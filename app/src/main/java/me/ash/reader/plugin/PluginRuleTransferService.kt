package me.ash.reader.plugin

import android.util.Log
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.ui.ext.spacerDollar

/**
 * 本地规则导入/导出服务
 */
class PluginRuleTransferService @Inject constructor(
    private val pluginRuleDao: PluginRuleDao,
    private val pluginFeedManager: PluginFeedManager,
    private val feedDao: FeedDao,
    private val accountService: AccountService,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun exportRule(rule: PluginRule, feed: Feed): String {
        return json.encodeToString(LocalRuleExport.serializer(), buildExport(rule, feed))
    }

    private fun buildExport(rule: PluginRule, feed: Feed): LocalRuleExport {
        val contentSelectors =
            rule.detailContentSelectors
                .split("||")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .ifEmpty { listOf(rule.detailContentSelector).filter { it.isNotBlank() } }
        return LocalRuleExport(
            version = 1,
            name = rule.name,
            subscribeUrl = rule.subscribeUrl,
            icon = rule.icon,
            listTitleSelector = rule.listTitleSelector,
            listUrlSelector = rule.listUrlSelector,
            listImageSelector = rule.listImageSelector,
            listTimeSelector = rule.listTimeSelector,
            detailTitleSelector = rule.detailTitleSelector,
            detailAuthorSelector = rule.detailAuthorSelector,
            detailTimeSelector = rule.detailTimeSelector,
            detailContentSelectors = contentSelectors,
            detailExcludeSelector = rule.detailExcludeSelector,
            detailImageSelector = rule.detailImageSelector,
            detailVideoSelector = rule.detailVideoSelector,
            detailAudioSelector = rule.detailAudioSelector,
            feedSettings =
                FeedSettingsExport(
                    isNotification = feed.isNotification,
                    isFullContent = feed.isFullContent,
                    isBrowser = feed.isBrowser,
                    isAutoTranslate = feed.isAutoTranslate,
                    isAutoTranslateTitle = feed.isAutoTranslateTitle,
                    isDisableReferer = feed.isDisableReferer,
                    isDisableJavaScript = feed.isDisableJavaScript,
                    isImageFilterEnabled = feed.isImageFilterEnabled,
                    imageFilterResolution = feed.imageFilterResolution,
                    imageFilterFileName = feed.imageFilterFileName,
                    imageFilterDomain = feed.imageFilterDomain,
                ),
        )
    }

    suspend fun importRule(jsonString: String): Result<PluginRule> {
        return runCatching {
            val parsed = json.decodeFromString(LocalRuleExport.serializer(), jsonString)
            val accountId = accountService.getCurrentAccountId()
            val ruleId = accountId.spacerDollar(UUID.randomUUID().toString())
            val selectors = parsed.detailContentSelectors.filter { it.isNotBlank() }
            val rule =
                PluginRule(
                    id = ruleId,
                    accountId = accountId,
                    name = parsed.name,
                    subscribeUrl = parsed.subscribeUrl,
                    icon = parsed.icon,
                    listHtmlCache = "",
                    listTitleSelector = parsed.listTitleSelector,
                    listUrlSelector = parsed.listUrlSelector,
                    listImageSelector = parsed.listImageSelector,
                    listTimeSelector = parsed.listTimeSelector,
                    detailTitleSelector = parsed.detailTitleSelector,
                    detailAuthorSelector = parsed.detailAuthorSelector,
                    detailTimeSelector = parsed.detailTimeSelector,
                    detailContentSelector = selectors.firstOrNull().orEmpty(),
                    detailContentSelectors = selectors.joinToString("||"),
                    detailExcludeSelector = parsed.detailExcludeSelector,
                    detailImageSelector = parsed.detailImageSelector,
                    detailVideoSelector = parsed.detailVideoSelector,
                    detailAudioSelector = parsed.detailAudioSelector,
                    isEnabled = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            pluginRuleDao.insert(rule)
            pluginFeedManager.ensureFeed(rule)

            val pluginUrl = pluginFeedManager.buildPluginUrl(rule.id)
            val feed = feedDao.queryByLink(accountId, pluginUrl).firstOrNull()
            if (feed != null) {
                val settings = parsed.feedSettings
                val updated =
                    feed.copy(
                        icon = rule.icon.ifBlank { feed.icon },
                        isNotification = settings.isNotification,
                        isFullContent = settings.isFullContent,
                        isBrowser = settings.isBrowser,
                        isAutoTranslate = settings.isAutoTranslate,
                        isAutoTranslateTitle = settings.isAutoTranslateTitle,
                        isDisableReferer = settings.isDisableReferer,
                        isDisableJavaScript = settings.isDisableJavaScript,
                        isImageFilterEnabled = settings.isImageFilterEnabled,
                        imageFilterResolution = settings.imageFilterResolution,
                        imageFilterFileName = settings.imageFilterFileName,
                        imageFilterDomain = settings.imageFilterDomain,
                    )
                feedDao.update(updated)
            }
            rule
        }.onFailure {
            Log.e(TAG, "import local rule failed: ${it.message}")
        }
    }

    companion object {
        private const val TAG = "PluginRuleTransfer"
    }
}

@Serializable
data class LocalRuleExport(
    val version: Int,
    val name: String,
    val subscribeUrl: String,
    val icon: String = "",
    val listTitleSelector: String,
    val listUrlSelector: String,
    val listImageSelector: String = "",
    val listTimeSelector: String = "",
    val detailTitleSelector: String = "",
    val detailAuthorSelector: String = "",
    val detailTimeSelector: String = "",
    val detailContentSelectors: List<String> = emptyList(),
    val detailExcludeSelector: String = "",
    val detailImageSelector: String = "",
    val detailVideoSelector: String = "",
    val detailAudioSelector: String = "",
    val feedSettings: FeedSettingsExport = FeedSettingsExport(),
)

@Serializable
data class FeedSettingsExport(
    val isNotification: Boolean = false,
    val isFullContent: Boolean = false,
    val isBrowser: Boolean = false,
    val isAutoTranslate: Boolean = false,
    val isAutoTranslateTitle: Boolean = false,
    val isDisableReferer: Boolean = false,
    val isDisableJavaScript: Boolean = false,
    val isImageFilterEnabled: Boolean = false,
    val imageFilterResolution: String = "",
    val imageFilterFileName: String = "",
    val imageFilterDomain: String = "",
)
