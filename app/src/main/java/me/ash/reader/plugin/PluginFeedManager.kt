package me.ash.reader.plugin

import android.util.Log
import javax.inject.Inject
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.ui.ext.extractDomain
import me.ash.reader.ui.ext.getDefaultGroupId

/**
 * 插件规则与 Feed 的桥接管理。
 * 说明：插件规则开启时创建 Feed，关闭时删除 Feed 与文章。
 */
class PluginFeedManager @Inject constructor(
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val groupDao: GroupDao,
) {
    fun buildPluginUrl(ruleId: String): String = "${PluginConstants.PLUGIN_URL_PREFIX}$ruleId"

    suspend fun ensureFeed(rule: PluginRule) {
        val pluginUrl = buildPluginUrl(rule.id)
        val existing = feedDao.queryByLink(rule.accountId, pluginUrl).firstOrNull()
        val displayName =
            rule.name.ifBlank { rule.subscribeUrl.extractDomain() ?: "Plugin" }

        if (existing != null) {
            // 更新名称/图标（避免规则改名后不同步）
            val updated =
                existing.copy(
                    name = displayName,
                    icon = rule.icon.ifBlank { existing.icon },
                )
            if (updated != existing) {
                feedDao.update(updated)
            }
            return
        }
        val resolvedGroupId =
            rule.groupId.takeIf { it.isNotBlank() && groupDao.queryById(it) != null }
                ?: rule.accountId.getDefaultGroupId()
        val feed =
            Feed(
                id = rule.id,
                name = displayName,
                icon = rule.icon.ifBlank { null },
                url = pluginUrl,
                groupId = resolvedGroupId,
                accountId = rule.accountId,
                isNotification = false,
                isFullContent = false,
                isBrowser = false,
            )
        Log.d("PluginFeed", "create feed: id=${feed.id} url=$pluginUrl name=$displayName")
        feedDao.insert(feed)
    }

    suspend fun removeFeed(rule: PluginRule) {
        val pluginUrl = buildPluginUrl(rule.id)
        val feed = feedDao.queryByLink(rule.accountId, pluginUrl).firstOrNull()
        if (feed == null) return
        Log.d("PluginFeed", "remove feed: id=${feed.id} url=$pluginUrl")
        articleDao.deleteByFeedId(rule.accountId, feed.id, includeStarred = true)
        feedDao.delete(feed)
    }
}
