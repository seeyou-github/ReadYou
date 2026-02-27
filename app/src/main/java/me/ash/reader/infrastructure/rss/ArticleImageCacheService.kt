package me.ash.reader.infrastructure.rss

import android.content.Context
import android.webkit.URLUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.article.ArticleImageCache
import me.ash.reader.domain.model.article.ArticleImageCacheType
import me.ash.reader.domain.repository.ArticleImageCacheDao
import me.ash.reader.infrastructure.di.IODispatcher
import okhttp3.OkHttpClient
import okhttp3.Request

class ArticleImageCacheService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val articleImageCacheDao: ArticleImageCacheDao,
    private val okHttpClient: OkHttpClient,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private companion object {
        const val SQL_IN_CHUNK_SIZE = 500
    }

    private val cacheDir = context.cacheDir.resolve("article_images")
    private val md = MessageDigest.getInstance("SHA-256")

    @OptIn(ExperimentalStdlibApi::class)
    private fun fileFor(url: String, accountId: Int): File {
        val digest = md.digest(url.toByteArray())
        val ext = URLUtil.guessFileName(url, null, null).substringAfterLast('.', "img")
        val fileName = digest.toHexString() + "." + ext
        val dir = cacheDir.resolve(accountId.toString())
        return dir.resolve(fileName)
    }

    suspend fun cacheTitleImages(articles: List<Article>) {
        articles.forEach { article ->
            val url = article.img?.trim().orEmpty()
            if (url.isBlank()) return@forEach
            cacheImage(article, url, ArticleImageCacheType.TITLE)
        }
    }

    suspend fun cacheContentImages(articles: List<Article>, urlExtractor: (String) -> List<String>) {
        articles.forEach { article ->
            val content = article.rawDescription
            if (content.isBlank()) return@forEach
            val urls = urlExtractor(content)
            if (urls.isEmpty()) return@forEach
            urls.distinct().forEach { url ->
                cacheImage(article, url, ArticleImageCacheType.CONTENT)
            }
        }
    }

    suspend fun deleteByArticleIds(articleIds: List<String>) {
        if (articleIds.isEmpty()) return

        articleIds.chunked(SQL_IN_CHUNK_SIZE).forEach { chunk ->
            val caches = articleImageCacheDao.queryByArticleIds(chunk)
            caches.forEach { cache ->
                runCatching {
                    val file = File(cache.localPath)
                    if (file.exists()) file.delete()
                }
            }
            articleImageCacheDao.deleteByArticleIds(chunk)
        }
    }

    suspend fun deleteByAccountId(accountId: Int) {
        val dir = cacheDir.resolve(accountId.toString())
        runCatching { if (dir.exists()) dir.deleteRecursively() }
        articleImageCacheDao.deleteByAccountId(accountId)
    }

    private suspend fun cacheImage(article: Article, url: String, type: String) {
        val accountId = article.accountId
        val file = fileFor(url, accountId)
        val fileExists = file.exists()
        val existing = articleImageCacheDao.queryByArticleIdAndUrl(article.id, url, type)

        if (fileExists && existing != null) return

        if (!fileExists) {
            downloadToFile(url, file)
        }
        val path = file.absolutePath
        if (existing == null || existing.localPath != path) {
            articleImageCacheDao.insert(
                ArticleImageCache(
                    articleId = article.id,
                    accountId = accountId,
                    url = url,
                    type = type,
                    localPath = path,
                )
            )
        }
    }

    private suspend fun downloadToFile(url: String, file: File) {
        withContext(ioDispatcher) {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext
                file.parentFile?.mkdirs()
                response.body?.byteStream()?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}
