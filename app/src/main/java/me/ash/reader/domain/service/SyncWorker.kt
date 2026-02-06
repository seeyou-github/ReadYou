package me.ash.reader.domain.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import java.util.Date
import me.ash.reader.domain.model.account.Account
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.infrastructure.rss.ReaderCacheHelper
import me.ash.reader.infrastructure.rss.ArticleImageCacheService
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.plugin.PluginConstants
import me.ash.reader.plugin.PluginRuleDao
import me.ash.reader.plugin.PluginSyncService
import me.ash.reader.ui.ext.showToastSuspend
import org.jsoup.Jsoup

@HiltWorker
class SyncWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val rssService: RssService,
    private val readerCacheHelper: ReaderCacheHelper,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val pluginRuleDao: PluginRuleDao,
    private val pluginSyncService: PluginSyncService,
    private val settingsProvider: SettingsProvider,
    private val articleImageCacheService: ArticleImageCacheService,
    private val workManager: WorkManager,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val data = inputData
        val accountId = data.getInt("accountId", -1)
        require(accountId != -1)
        val feedId = data.getString("feedId")
        val groupId = data.getString("groupId")
        val syncStartAt = Date()

        val result =
            rssService
                .get()
                .sync(accountId = accountId, feedId = feedId, groupId = groupId)

        rssService.get().clearKeepArchivedArticles().forEach {
            readerCacheHelper.deleteCacheFor(articleId = it.id)
        }

        if (result is ListenableWorker.Result.Success) {
            runPostSyncCacheTasks(accountId, syncStartAt)
        }

        workManager
            .beginUniqueWork(
                uniqueWorkName = POST_SYNC_WORK_NAME,
                existingWorkPolicy = ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ReaderWorker>()
                    .addTag(READER_TAG)
                    .addTag(ONETIME_WORK_TAG)
                    .setBackoffCriteria(
                        backoffPolicy = BackoffPolicy.EXPONENTIAL,
                        backoffDelay = 30,
                        timeUnit = TimeUnit.SECONDS,
                    )
                    .build(),
            )
            .then(OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build())
            .enqueue()

        return result
    }

    private suspend fun runPostSyncCacheTasks(accountId: Int, syncStartAt: Date) {
        runStep(
            startMessage = "正在缓存本地规则正文内容",
            successMessage = "缓存本地规则正文内容完成",
            errorPrefix = "缓存本地规则正文内容出错，错误原因：",
        ) {
            cacheLocalRuleContent(accountId, syncStartAt)
        }
        runStep(
            startMessage = "正在缓存新增文章标题图片",
            successMessage = "缓存新增文章标题图片完成",
            errorPrefix = "缓存新增文章标题图片出错，错误原因：",
        ) {
            cacheTitleImages(accountId, syncStartAt)
        }
        runStep(
            startMessage = "正在缓存新增文章正文图片",
            successMessage = "缓存新增文章正文图片完成",
            errorPrefix = "缓存新增文章正文图片出错，错误原因：",
        ) {
            cacheContentImages(accountId, syncStartAt)
        }
    }

    private suspend fun runStep(
        startMessage: String,
        successMessage: String,
        errorPrefix: String,
        block: suspend () -> Unit,
    ) {
        applicationContext.showToastSuspend(startMessage)
        try {
            block()
            applicationContext.showToastSuspend(successMessage)
        } catch (e: Exception) {
            applicationContext.showToastSuspend(errorPrefix + (e.message ?: "unknown"))
        }
    }

    private suspend fun cacheLocalRuleContent(accountId: Int, syncStartAt: Date) {
        val newArticles = articleDao.queryByUpdateAtAfter(accountId, syncStartAt)
        if (newArticles.isEmpty()) return
        val feedsById = feedDao.queryAll(accountId).associateBy { it.id }
        val rulesById = pluginRuleDao.queryAll(accountId).associateBy { it.id }

        newArticles.forEach { article ->
            val feed = feedsById[article.feedId] ?: return@forEach
            if (!feed.url.startsWith(PluginConstants.PLUGIN_URL_PREFIX)) return@forEach
            val ruleId = feed.url.removePrefix(PluginConstants.PLUGIN_URL_PREFIX)
            val rule = rulesById[ruleId] ?: return@forEach
            if (!rule.cacheContentOnUpdate) return@forEach
            if (article.rawDescription.isNotBlank()) return@forEach
            val detail = pluginSyncService.fetchDetail(rule, article.link).getOrNull() ?: return@forEach
            if (detail.contentHtml.isBlank()) return@forEach

            val plainText = Jsoup.parse(detail.contentHtml).text()
            val updated =
                article.copy(
                    title = detail.title?.takeIf { it.isNotBlank() } ?: article.title,
                    author = detail.author ?: article.author,
                    rawDescription = detail.contentHtml,
                    shortDescription = plainText.take(280),
                    img = article.img ?: detail.coverImage,
                    sourceTime = detail.time ?: article.sourceTime,
                )
            articleDao.update(updated)
        }
    }

    private suspend fun cacheTitleImages(accountId: Int, syncStartAt: Date) {
        if (!settingsProvider.settings.cacheTitleImageOnUpdate.value) return
        val newArticles = articleDao.queryByUpdateAtAfter(accountId, syncStartAt)
        articleImageCacheService.cacheTitleImages(newArticles)
    }

    private suspend fun cacheContentImages(accountId: Int, syncStartAt: Date) {
        if (!settingsProvider.settings.cacheContentImageOnUpdate.value) return
        val newArticles = articleDao.queryByUpdateAtAfter(accountId, syncStartAt)
        articleImageCacheService.cacheContentImages(newArticles) { html ->
            extractImageUrlsFromHtml(html)
        }
    }

    private fun extractImageUrlsFromHtml(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html)
        return doc.select("img").mapNotNull { img ->
            val raw =
                img.attr("src")
                    .ifBlank { img.attr("data-src") }
                    .ifBlank { img.attr("data-original") }
                    .ifBlank { img.attr("srcset").substringBefore(" ").trim() }
                    .trim()
            raw.takeIf { it.isNotBlank() && !it.startsWith("data:") }
        }
    }

    companion object {
        private const val SYNC_WORK_NAME_PERIODIC = "ReadYou"
        @Deprecated("do not use")
        private const val READER_WORK_NAME_PERIODIC = "FETCH_FULL_CONTENT_PERIODIC"
        private const val POST_SYNC_WORK_NAME = "POST_SYNC_WORK"

        private const val SYNC_ONETIME_NAME = "SYNC_ONETIME"

        const val SYNC_TAG = "SYNC_TAG"
        const val READER_TAG = "READER_TAG"
        const val ONETIME_WORK_TAG = "ONETIME_WORK_TAG"
        const val PERIODIC_WORK_TAG = "PERIODIC_WORK_TAG"

        fun cancelOneTimeWork(workManager: WorkManager) {
            workManager.cancelUniqueWork(SYNC_ONETIME_NAME)
        }

        fun cancelPeriodicWork(workManager: WorkManager) {
            workManager.cancelUniqueWork(SYNC_WORK_NAME_PERIODIC)
            workManager.cancelUniqueWork(READER_WORK_NAME_PERIODIC)
        }

        fun enqueueOneTimeWork(workManager: WorkManager, inputData: Data = workDataOf()) {
            workManager
                .beginUniqueWork(
                    SYNC_ONETIME_NAME,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<SyncWorker>()
                        .addTag(SYNC_TAG)
                        .addTag(ONETIME_WORK_TAG)
                        .setInputData(inputData)
                        .build(),
                )
                .enqueue()
        }

        fun enqueuePeriodicWork(account: Account, workManager: WorkManager) {
            val syncInterval = account.syncInterval
            val syncOnlyWhenCharging = account.syncOnlyWhenCharging
            val syncOnlyOnWiFi = account.syncOnlyOnWiFi
            val workState =
                workManager
                    .getWorkInfosForUniqueWork(SYNC_WORK_NAME_PERIODIC)
                    .get()
                    .firstOrNull()
                    ?.state

            val policy =
                if (workState == WorkInfo.State.ENQUEUED || workState == WorkInfo.State.RUNNING)
                    ExistingPeriodicWorkPolicy.UPDATE
                else ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE

            workManager.enqueueUniquePeriodicWork(
                SYNC_WORK_NAME_PERIODIC,
                policy,
                PeriodicWorkRequestBuilder<SyncWorker>(syncInterval.value, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiresCharging(syncOnlyWhenCharging.value)
                            .setRequiredNetworkType(
                                if (syncOnlyOnWiFi.value) NetworkType.UNMETERED
                                else NetworkType.CONNECTED
                            )
                            .build()
                    )
                    .setBackoffCriteria(
                        backoffPolicy = BackoffPolicy.EXPONENTIAL,
                        backoffDelay = 30,
                        timeUnit = TimeUnit.SECONDS,
                    )
                    .setInputData(workDataOf("accountId" to account.id))
                    .addTag(SYNC_TAG)
                    .addTag(PERIODIC_WORK_TAG)
                    .setInitialDelay(syncInterval.value, TimeUnit.MINUTES)
                    .build(),
            )

            workManager.cancelUniqueWork(READER_WORK_NAME_PERIODIC)
        }
    }
}
