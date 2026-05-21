package me.ash.reader.infrastructure.rss

import android.graphics.BitmapFactory
import android.content.Context
import android.webkit.URLUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.domain.model.article.ArticleImageCache
import me.ash.reader.domain.model.article.ArticleImageCacheType
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.repository.ArticleImageCacheDao
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.log.ImageDownloadDebugLogger
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.helper.StringUtil

@Singleton
class ArticleImagePreloadQueue @Inject constructor(
    @ApplicationContext context: Context,
    private val articleImageCacheDao: ArticleImageCacheDao,
    private val okHttpClient: OkHttpClient,
    private val debugLogger: ImageDownloadDebugLogger,
    @ApplicationScope applicationScope: CoroutineScope,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val cacheDir = context.cacheDir.resolve("article_images")
    private val signal = Channel<Unit>(capacity = Channel.UNLIMITED)
    private val imageOkHttpClient =
        okHttpClient
            .newBuilder()
            .readTimeout(IMAGE_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
    private val sequence = AtomicLong(0L)
    private val lock = Any()
    private val pendingTasks = LinkedHashMap<TaskKey, Task>()
    private val activeCalls = mutableMapOf<TaskKey, Call>()
    private val activeTasks = mutableMapOf<TaskKey, Task>()
    private val interruptedTasks = mutableSetOf<TaskKey>()
    private val workers: List<Job>

    private val _cachedImagePaths = MutableStateFlow<Map<String, String>>(emptyMap())
    val cachedImagePaths: StateFlow<Map<String, String>> = _cachedImagePaths.asStateFlow()

    private inline fun log(crossinline message: () -> String) =
        debugLogger.log { "ArticleImagePreloadQueue ${message()}" }

    init {
        workers = List(MAX_CONCURRENT_DOWNLOADS) {
            applicationScope.launch(ioDispatcher) { workerLoop() }
        }
    }

    fun enqueueTitleImages(
        articles: List<ArticleWithFeed>,
        priorityArticleIds: Set<String> = emptySet(),
    ) {
        if (articles.isEmpty()) return
        var changed = false
        synchronized(lock) {
            log {
                "enqueueTitleImages size=${articles.size} priority=${priorityArticleIds.size} pending=${pendingTasks.size} active=${activeTasks.size}"
            }
            articles
                .asSequence()
                .sortedWith(
                    compareByDescending<ArticleWithFeed> {
                        it.article.id in priorityArticleIds
                    }.thenByDescending { it.article.date.time }
                )
                .forEach { articleWithFeed ->
                    val article = articleWithFeed.article
                    val url = article.img?.trim().orEmpty()
                    if (url.isBlank()) {
                        log { "skip title image articleId=${article.id} reason=blank_url" }
                        return@forEach
                    }
                    val key = TaskKey(article.id, url, ArticleImageCacheType.TITLE)
                    if (pendingTasks.containsKey(key) || activeTasks.containsKey(key)) {
                        log { "skip title image articleId=${article.id} reason=duplicate url=$url" }
                        return@forEach
                    }
                    pendingTasks[key] =
                        Task(
                            key = key,
                            accountId = article.accountId,
                            refererUrl = article.link.takeIf { it.isNotBlank() },
                            priority =
                                if (article.id in priorityArticleIds) {
                                    VISIBLE_TITLE_PRIORITY + article.date.time
                                } else {
                                    article.date.time
                                },
                            sequence = sequence.incrementAndGet(),
                            source = TaskSource.LIST,
                        )
                    log {
                        "enqueue title image articleId=${article.id} priority=${pendingTasks[key]?.priority} visible=${article.id in priorityArticleIds} url=$url"
                    }
                    changed = true
                }
        }
        if (changed) {
            log { "signal workers for title images" }
            signalWorkers()
        }
    }

    fun enqueueReadingImages(articleWithFeed: ArticleWithFeed, html: String) {
        val article = articleWithFeed.article
        val urls = extractImageUrls(baseUrl = article.link, html = html)
        if (urls.isEmpty()) {
            log { "enqueueReadingImages articleId=${article.id} urls=0" }
            return
        }
        var changed = false
        val shouldPreempt =
            synchronized(lock) {
                log { "enqueueReadingImages articleId=${article.id} urls=${urls.size} pending=${pendingTasks.size} active=${activeTasks.size}" }
                urls.asReversed().forEach { url ->
                    val key = TaskKey(article.id, url, ArticleImageCacheType.CONTENT)
                    pendingTasks.remove(key)
                    if (activeTasks.containsKey(key)) {
                        log { "skip reading image articleId=${article.id} reason=already_active url=$url" }
                        return@forEach
                    }
                    pendingTasks[key] =
                        Task(
                            key = key,
                            accountId = article.accountId,
                            refererUrl = article.link.takeIf { it.isNotBlank() },
                            priority = READING_PRIORITY,
                            sequence = sequence.incrementAndGet(),
                            source = TaskSource.READING,
                        )
                    log { "enqueue reading image articleId=${article.id} url=$url" }
                    changed = true
                }
                changed
            }
        if (shouldPreempt) {
            log { "preempt active downloads for reading articleId=${article.id}" }
            preemptActiveDownloads()
            signalWorkers()
        }
    }

    fun clear() {
        val callsToCancel: List<Call>
        synchronized(lock) {
            log { "clear queue pending=${pendingTasks.size} active=${activeTasks.size}" }
            pendingTasks.clear()
            interruptedTasks.addAll(activeTasks.keys)
            callsToCancel = activeCalls.values.toList()
        }
        callsToCancel.forEach { it.cancel() }
    }

    private fun signalWorkers() {
        repeat(MAX_CONCURRENT_DOWNLOADS) { signal.trySend(Unit) }
    }

    fun removeReadingImagesForArticle(articleId: String) {
        val callsToCancel: List<Call>
        synchronized(lock) {
            log { "removeReadingImagesForArticle articleId=$articleId pending=${pendingTasks.size} active=${activeTasks.size}" }
            pendingTasks.entries.removeAll {
                it.key.articleId == articleId && it.key.type == ArticleImageCacheType.CONTENT
            }
            val activeKeys =
                activeTasks.keys.filter {
                    it.articleId == articleId && it.type == ArticleImageCacheType.CONTENT
                }
            interruptedTasks.addAll(activeKeys)
            callsToCancel = activeKeys.mapNotNull { activeCalls[it] }
        }
        callsToCancel.forEach { it.cancel() }
    }

    private suspend fun workerLoop() {
        while (true) {
            val task = takeNextTask()
            if (task == null) {
                signal.receive()
                continue
            }
            try {
                runTask(task)
            } finally {
                synchronized(lock) {
                    activeTasks.remove(task.key)
                    activeCalls.remove(task.key)
                }
            }
        }
    }

    private fun takeNextTask(): Task? =
        synchronized(lock) {
            val next =
                pendingTasks.values.maxWithOrNull(
                    compareBy<Task> { it.priority }.thenBy { it.sequence }
                ) ?: return@synchronized null
            pendingTasks.remove(next.key)
            activeTasks[next.key] = next
            log {
                "takeNextTask articleId=${next.key.articleId} type=${next.key.type} source=${next.source} priority=${next.priority} remaining=${pendingTasks.size}"
            }
            next
        }

    private fun preemptActiveDownloads() {
        val callsToCancel: List<Call>
        synchronized(lock) {
            activeTasks.values.forEach { task ->
                pendingTasks.putIfAbsent(task.key, task)
                interruptedTasks.add(task.key)
            }
            callsToCancel = activeCalls.values.toList()
        }
        log { "preemptActiveDownloads cancel=${callsToCancel.size}" }
        callsToCancel.forEach { it.cancel() }
    }

    private suspend fun runTask(task: Task) {
        val existing =
            articleImageCacheDao.queryByArticleIdAndUrl(
                articleId = task.key.articleId,
                url = task.key.url,
                type = task.key.type,
            )
        if (existing != null && File(existing.localPath).exists()) {
            log { "cache hit articleId=${task.key.articleId} type=${task.key.type} path=${existing.localPath}" }
            publishCache(task.key, existing.localPath)
            return
        }

        val file = fileFor(task.key.url, task.accountId)
        log {
            "runTask articleId=${task.key.articleId} type=${task.key.type} source=${task.source} url=${task.key.url} target=${file.absolutePath} exists=${file.exists()}"
        }
        if (!file.exists()) {
            downloadWithSingleRetry(task, file)
        }
        if (!file.exists()) return

        val path = file.absolutePath
        articleImageCacheDao.insert(
            ArticleImageCache(
                articleId = task.key.articleId,
                accountId = task.accountId,
                url = task.key.url,
                type = task.key.type,
                localPath = path,
            )
        )
        log { "db insert articleId=${task.key.articleId} type=${task.key.type} path=$path" }
        publishCache(task.key, path)
    }

    private suspend fun downloadWithSingleRetry(task: Task, file: File) {
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                log { "download attempt=${attempt + 1} articleId=${task.key.articleId} url=${task.key.url}" }
                downloadToFile(task, file)
                log { "download success attempt=${attempt + 1} articleId=${task.key.articleId} url=${task.key.url} size=${file.length()}" }
                return
            } catch (throwable: Throwable) {
                if (file.exists()) file.delete()
                if (consumeInterruption(task.key)) {
                    log { "download interrupted articleId=${task.key.articleId} url=${task.key.url} throwable=${throwable::class.java.simpleName}" }
                    return
                }
                if (attempt == MAX_ATTEMPTS - 1) {
                    log {
                        "download failed articleId=${task.key.articleId} url=${task.key.url} throwable=${throwable::class.java.simpleName}:${throwable.message}"
                    }
                }
            }
        }
    }

    private suspend fun downloadToFile(task: Task, file: File) {
        withContext(ioDispatcher) {
            val tempFile = tempFileFor(file)
            val existingBytes = tempFile.takeIf { it.exists() }?.length() ?: 0L
            val requestBuilder = Request.Builder().url(task.key.url)
            if (existingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }
            task.refererUrl?.let { requestBuilder.header("Referer", it) }
            val call = imageOkHttpClient.newCall(requestBuilder.build())
            synchronized(lock) { activeCalls[task.key] = call }
            log {
                "request start articleId=${task.key.articleId} url=${task.key.url} referer=${task.refererUrl.orEmpty()} rangeStart=$existingBytes tempExists=${tempFile.exists()} tempBytes=$existingBytes"
            }

            call.execute().use { response ->
                val body = response.body ?: throw IOException("Empty image body")
                val append = shouldAppendResponse(response, existingBytes)
                if (existingBytes > 0L && !append) {
                    tempFile.delete()
                    log {
                        "range unsupported restart articleId=${task.key.articleId} url=${task.key.url} code=${response.code}"
                    }
                }
                val startBytes = if (append) existingBytes else 0L
                val expectedBytes = responseExpectedBytes(response, startBytes)
                log {
                    "response articleId=${task.key.articleId} url=${task.key.url} code=${response.code} success=${response.isSuccessful} contentType=${body.contentType()} contentLength=${body.contentLength()} expectedBytes=$expectedBytes append=$append contentRange=${response.header("Content-Range").orEmpty()} ext=${file.extension}"
                }
                if (!response.isSuccessful && response.code != HTTP_PARTIAL) {
                    throw IOException("HTTP ${response.code}")
                }
                file.parentFile?.mkdirs()
                var bytesWritten: Long
                body.byteStream().use { input ->
                    FileOutputStream(tempFile, append).use { output ->
                        bytesWritten =
                            copyWithProgress(
                                task = task,
                                input = input,
                                output = output,
                                startBytes = startBytes,
                                expectedBytes = expectedBytes,
                            )
                    }
                }
                log { "write temp file articleId=${task.key.articleId} url=${task.key.url} temp=${tempFile.absolutePath} bytes=$bytesWritten" }
                if (file.exists()) file.delete()
                if (!tempFile.renameTo(file)) {
                    log { "rename failed fallback copy articleId=${task.key.articleId} url=${task.key.url}" }
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }
                logImageInfo(task, file)
            }
        }
    }

    private fun consumeInterruption(key: TaskKey): Boolean =
        synchronized(lock) { interruptedTasks.remove(key) }

    private fun tempFileFor(file: File): File = File(file.parentFile, "${file.name}.tmp")

    @OptIn(ExperimentalStdlibApi::class)
    private fun fileFor(url: String, accountId: Int): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        val ext = URLUtil.guessFileName(url, null, null).substringAfterLast('.', "img")
        return cacheDir.resolve(accountId.toString()).resolve("${digest.toHexString()}.$ext")
    }

    private fun publishCache(key: TaskKey, path: String) {
        val cacheKey = cacheKey(key.articleId, key.url, key.type)
        _cachedImagePaths.update { it + (cacheKey to path) }
        log { "publish cache articleId=${key.articleId} type=${key.type} path=$path cacheKey=$cacheKey" }
    }

    private fun shouldAppendResponse(response: Response, existingBytes: Long): Boolean =
        existingBytes > 0L && response.code == HTTP_PARTIAL

    private fun responseExpectedBytes(response: Response, startBytes: Long): Long {
        val contentRange = response.header("Content-Range").orEmpty()
        val totalFromRange = contentRange.substringAfterLast("/", "").toLongOrNull()
        if (totalFromRange != null) return totalFromRange
        val contentLength = response.body?.contentLength() ?: -1L
        return if (contentLength >= 0L) startBytes + contentLength else -1L
    }

    private fun copyWithProgress(
        task: Task,
        input: java.io.InputStream,
        output: OutputStream,
        startBytes: Long,
        expectedBytes: Long,
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val startedAt = System.nanoTime()
        var total = startBytes
        var lastLogAt = startedAt
        var lastLogBytes = startBytes
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            total += read
            val now = System.nanoTime()
            if (
                now - lastLogAt >= PROGRESS_LOG_INTERVAL_NANOS ||
                    total - lastLogBytes >= PROGRESS_LOG_BYTES
            ) {
                logProgress(task, total, expectedBytes, startedAt, now)
                lastLogAt = now
                lastLogBytes = total
            }
        }
        output.flush()
        logProgress(task, total, expectedBytes, startedAt, System.nanoTime())
        return total - startBytes
    }

    private fun logProgress(
        task: Task,
        bytes: Long,
        expectedBytes: Long,
        startedAt: Long,
        now: Long,
    ) {
        val elapsedSeconds = ((now - startedAt).coerceAtLeast(1L)) / 1_000_000_000.0
        val speedKbps = bytes / 1024.0 / elapsedSeconds
        val percent =
            if (expectedBytes > 0L) {
                "%.1f".format((bytes * 100.0) / expectedBytes)
            } else {
                "unknown"
            }
        log {
            "progress articleId=${task.key.articleId} type=${task.key.type} bytes=$bytes expected=$expectedBytes percent=$percent speedKBps=${"%.1f".format(speedKbps)} url=${task.key.url}"
        }
    }

    private fun logImageInfo(task: Task, file: File) {
        val options =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        BitmapFactory.decodeFile(file.absolutePath, options)
        log {
            "image info articleId=${task.key.articleId} type=${task.key.type} path=${file.absolutePath} bytes=${file.length()} width=${options.outWidth} height=${options.outHeight} mime=${options.outMimeType.orEmpty()} ext=${file.extension}"
        }
    }

    private fun extractImageUrls(baseUrl: String, html: String): List<String> =
        Jsoup.parse(html, baseUrl)
            .select("img")
            .mapNotNull { element ->
                val srcSet = element.attr("srcset").takeIf { it.isNotBlank() }
                val src = element.attr("abs:src").takeIf { it.isNotBlank() }
                when {
                    srcSet != null -> {
                        srcSet.split(",")
                            .asSequence()
                            .map { it.trim().substringBefore(" ").trim() }
                            .firstOrNull { it.isNotBlank() }
                            ?.let { StringUtil.resolve(baseUrl, it) }
                    }
                    src != null -> src
                    else -> null
                }
            }
            .distinct()

    private data class TaskKey(
        val articleId: String,
        val url: String,
        val type: String,
    )

    private data class Task(
        val key: TaskKey,
        val accountId: Int,
        val refererUrl: String?,
        val priority: Long,
        val sequence: Long,
        val source: TaskSource,
    )

    private enum class TaskSource {
        LIST,
        READING,
    }

    companion object {
        private const val MAX_CONCURRENT_DOWNLOADS = 2
        private const val MAX_ATTEMPTS = 2
        private const val READING_PRIORITY = Long.MAX_VALUE / 2
        private const val VISIBLE_TITLE_PRIORITY = Long.MAX_VALUE / 4
        private const val HTTP_PARTIAL = 206
        private const val IMAGE_READ_TIMEOUT_SECONDS = 180L
        private const val PROGRESS_LOG_BYTES = 512L * 1024L
        private const val PROGRESS_LOG_INTERVAL_NANOS = 1_000_000_000L

        fun cacheKey(articleId: String, url: String, type: String): String =
            "$articleId|$type|$url"
    }
}
