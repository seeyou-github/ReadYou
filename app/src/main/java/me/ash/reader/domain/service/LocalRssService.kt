package me.ash.reader.domain.service

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.ash.reader.domain.data.SyncLogger
import me.ash.reader.domain.model.account.AccountType
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.feed.FeedWithArticle
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.BlacklistKeywordDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.infrastructure.android.NotificationHelper
import me.ash.reader.infrastructure.di.DefaultDispatcher
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.rss.RssHelper
import me.ash.reader.plugin.PluginConstants
import me.ash.reader.plugin.PluginRuleDao
import me.ash.reader.plugin.PluginSyncService
import timber.log.Timber

private const val TAG = "LocalRssService"

class LocalRssService
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val rssHelper: RssHelper,
    private val notificationHelper: NotificationHelper,
    private val groupDao: GroupDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val workManager: WorkManager,
    private val accountService: AccountService,
    private val syncLogger: SyncLogger,
    private val blacklistKeywordDao: BlacklistKeywordDao,
    private val pluginRuleDao: PluginRuleDao,
    private val pluginSyncService: PluginSyncService,
) :
    AbstractRssRepository(
        articleDao,
        groupDao,
        feedDao,
        workManager,
        rssHelper,
        notificationHelper,
        ioDispatcher,
        defaultDispatcher,
        accountService,
    ) {

    override suspend fun sync(
        accountId: Int,
        feedId: String?,
        groupId: String?
    ): ListenableWorker.Result = supervisorScope {
        return@supervisorScope runCatching {
            val preTime = System.currentTimeMillis()
            val preDate = Date(preTime)
            val currentAccount = accountService.getAccountById(accountId)!!
            require(currentAccount.type.id == AccountType.Local.id) {
                "Account type is invalid"
            }
            val semaphore = Semaphore(16)

            val feedsToSync =
                when {
                    feedId != null -> listOfNotNull(feedDao.queryById(feedId))
                    groupId != null -> feedDao.queryByGroupId(accountId, groupId)
                    else -> feedDao.queryAll(accountId)
                }

            // 2026-01-25: 设置同步进度总数量
            // 原因：用户反馈需要在更新订阅源时显示进度
            // 时间：2026-01-25
            val totalFeeds = feedsToSync.size
            Timber.tag("SyncProgress").d("准备同步，总数量: $totalFeeds")
            AbstractRssRepository.setSyncProgress(0, totalFeeds)

            val completedCount = AtomicInteger(0)
            feedsToSync
                .mapIndexed { index, currentFeed ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val archivedArticles =
                                feedDao
                                    .queryArchivedArticles(currentFeed.id)
                                    .map { it.link }
                                    .toSet()
                            val fetchedFeed = syncFeed(currentFeed, preDate)
                            var fetchedArticles =
                                fetchedFeed.articles.filterNot {
                                    archivedArticles.contains(it.link)
                                }

                            // 2026-01-24: 关键词过滤 - 过滤的文章不保存
                            val blacklistKeywords = blacklistKeywordDao.getAllSync()
                            if (blacklistKeywords.isNotEmpty()) {
                                fetchedArticles = fetchedArticles.filterNot { article ->
                                    blacklistKeywords.any { keyword ->
                                        keyword.enabled && article.title.contains(
                                            keyword.keyword,
                                            ignoreCase = true
                                        ) && (keyword.feedUrls.isNullOrBlank() || keyword.feedUrls.split(
                                            ","
                                        ).contains(currentFeed.url))
                                    }
                                }
                            }

                            val newArticles =
                                articleDao.insertListIfNotExist(
                                    articles = fetchedArticles,
                                    feed = currentFeed,
                                )
                            if (currentFeed.isNotification && newArticles.isNotEmpty()) {
                                notificationHelper.notify(
                                    fetchedFeed.copy(articles = newArticles, feed = currentFeed)
                                )
                            }

                            // 2026-01-25: 更新同步进度
                            // 原因：用户反馈需要在更新订阅源时显示进度
                            // 时间：2026-01-25
                            val current = completedCount.incrementAndGet()
                            Timber.tag("SyncProgress").d("更新进度: $current/$totalFeeds")
                            AbstractRssRepository.setSyncProgress(current, totalFeeds)
                        }
                    }
                }
                .awaitAll()

            // 2026-01-25: 清除同步进度
            clearSyncProgress()

            Timber.tag("RlOG").i("onCompletion: ${System.currentTimeMillis() - preTime}")
            accountService.update(currentAccount.copy(updateAt = Date()))
            ListenableWorker.Result.success()
        }
            .onFailure {
                // 2026-01-25: 同步失败时清除进度
                AbstractRssRepository.clearSyncProgress()
                syncLogger.log(it)
            }
            .getOrNull() ?: ListenableWorker.Result.retry()
    }

    private suspend fun syncFeed(feed: Feed, preDate: Date = Date()): FeedWithArticle {
        if (feed.url.startsWith(PluginConstants.PLUGIN_URL_PREFIX)) {
            val ruleId = feed.url.removePrefix(PluginConstants.PLUGIN_URL_PREFIX)
            val rule = pluginRuleDao.queryById(ruleId)
            if (rule == null) {
                Timber.tag(TAG).w("Plugin rule not found: $ruleId")
                return FeedWithArticle(feed = feed, articles = emptyList())
            }
            return pluginSyncService.syncByRule(feed, rule, preDate)
        }

        val articles = rssHelper.queryRssXml(feed, "", preDate)

        if (feed.icon == null) {
            val iconLink = rssHelper.queryRssIconLink(feed.url)
            if (iconLink != null) {
                rssHelper.saveRssIcon(feedDao, feed, iconLink)
            }
        }
        return FeedWithArticle(
            feed = feed.copy(isNotification = feed.isNotification && articles.isNotEmpty()),
            articles = articles,
        )
    }
}
