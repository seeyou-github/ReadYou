package me.ash.reader.ui.page.home.feeds.drawer.feed

import androidx.compose.material.ExperimentalMaterialApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLConnection
import android.util.Base64
import android.content.Context
import android.net.Uri
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import timber.log.Timber
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.service.OpmlService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.di.MainDispatcher
import me.ash.reader.infrastructure.rss.RssHelper
import me.ash.reader.plugin.PluginConstants
import me.ash.reader.plugin.PluginRuleDao
import me.ash.reader.plugin.PluginRuleTransferService

@OptIn(ExperimentalMaterialApi::class)
@HiltViewModel
class FeedOptionViewModel
@Inject
constructor(
    val rssService: RssService,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val rssHelper: RssHelper,
    private val feedDao: FeedDao,
    private val opmlService: OpmlService,
    private val pluginRuleDao: PluginRuleDao,
    private val pluginRuleTransferService: PluginRuleTransferService,
) : ViewModel() {

    private val _feedOptionUiState = MutableStateFlow(FeedOptionUiState())
    val feedOptionUiState: StateFlow<FeedOptionUiState> = _feedOptionUiState.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            rssService.flow().collectLatest {
                it.pullGroups().collectLatest { groups ->
                    _feedOptionUiState.update { it.copy(groups = groups) }
                }
            }
        }
    }

    suspend fun fetchFeed(feedId: String) {
        val feed = rssService.get().findFeedById(feedId)
        _feedOptionUiState.update {
            it.copy(
                feed = feed,
                selectedGroupId = feed?.groupId ?: "",
                imageFilterEnabled = feed?.isImageFilterEnabled ?: false,
                imageFilterResolution = feed?.imageFilterResolution ?: "",
                imageFilterFileName = feed?.imageFilterFileName ?: "",
                imageFilterDomain = feed?.imageFilterDomain ?: "",
                disableRefererEnabled = feed?.isDisableReferer ?: false,
                disableJavaScriptEnabled = feed?.isDisableJavaScript ?: false,
            )
        }
    }

    fun showNewGroupDialog() {
        _feedOptionUiState.update { it.copy(newGroupDialogVisible = true, newGroupContent = "") }
    }

    fun hideNewGroupDialog() {
        _feedOptionUiState.update { it.copy(newGroupDialogVisible = false, newGroupContent = "") }
    }

    fun inputNewGroup(content: String) {
        _feedOptionUiState.update { it.copy(newGroupContent = content) }
    }

    fun addNewGroup() {
        if (_feedOptionUiState.value.newGroupContent.isNotBlank()) {
            applicationScope.launch {
                selectedGroup(
                    rssService
                        .get()
                        .addGroup(
                            destFeed = _feedOptionUiState.value.feed,
                            newGroupName = _feedOptionUiState.value.newGroupContent,
                        )
                )
                hideNewGroupDialog()
            }
        }
    }

    fun selectedGroup(groupId: String) {
        applicationScope.launch(ioDispatcher) {
            _feedOptionUiState.value.feed?.let {
                val updated = it.copy(groupId = groupId)
                rssService
                    .get()
                    .moveFeed(originGroupId = it.groupId, feed = updated)
                syncLocalRuleIfNeeded(updated) { rule ->
                    rule.copy(groupId = groupId, updatedAt = System.currentTimeMillis())
                }
                fetchFeed(it.id)
            }
        }
    }

    fun changeParseFullContentPreset() {
        viewModelScope.launch(ioDispatcher) {
            _feedOptionUiState.value.feed?.let {
                val isFullContent = !it.isFullContent
                val isBrowser = if (isFullContent) false else it.isBrowser
                rssService
                    .get()
                    .updateFeed(it.copy(isFullContent = isFullContent, isBrowser = isBrowser))
                fetchFeed(it.id)
            }
        }
    }

    fun changeOpenInBrowserPreset() {
        viewModelScope.launch(ioDispatcher) {
            _feedOptionUiState.value.feed?.let {
                val isBrowser = !it.isBrowser
                val isFullContent = if (isBrowser) false else it.isFullContent
                rssService
                    .get()
                    .updateFeed(it.copy(isBrowser = isBrowser, isFullContent = isFullContent))
                fetchFeed(it.id)
            }
        }
    }

    fun changeAllowNotificationPreset() {
        viewModelScope.launch(ioDispatcher) {
            _feedOptionUiState.value.feed?.let {
                rssService.get().updateFeed(it.copy(isNotification = !it.isNotification))
                fetchFeed(it.id)
            }
        }
    }

    fun setDisableReferer(enabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            _feedOptionUiState.value.feed?.let {
                rssService.get().updateFeed(it.copy(isDisableReferer = enabled))
                fetchFeed(it.id)
            }
        }
    }

    fun setDisableJavaScript(enabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            _feedOptionUiState.value.feed?.let {
                rssService.get().updateFeed(it.copy(isDisableJavaScript = enabled))
                fetchFeed(it.id)
            }
        }
    }

    fun changeAutoTranslatePreset() {
        viewModelScope.launch(ioDispatcher) {
            _feedOptionUiState.value.feed?.let { feed ->
                Timber.tag("AutoTranslate").d("changeAutoTranslatePreset: Feed ID = ${feed.id}, Name = ${feed.name}, Current isAutoTranslate = ${feed.isAutoTranslate}")
                val isAutoTranslate = !feed.isAutoTranslate
                Timber.tag("AutoTranslate").d("changeAutoTranslatePreset: New isAutoTranslate = $isAutoTranslate")
                val updatedFeed = feed.copy(isAutoTranslate = isAutoTranslate)
                rssService.get().updateFeed(updatedFeed)
                Timber.tag("AutoTranslate").d("changeAutoTranslatePreset: Database update completed for feed ${feed.id}")
                fetchFeed(feed.id)
                Timber.tag("AutoTranslate").d("changeAutoTranslatePreset: FetchFeed completed for feed ${feed.id}")
            } ?: Timber.tag("AutoTranslate").e("changeAutoTranslatePreset: Feed is null")
        }
    }

    // 2026-02-02: �����޸��Զ��������±������õķ�?
    fun changeAutoTranslateTitlePreset() {
        viewModelScope.launch(ioDispatcher) {
            _feedOptionUiState.value.feed?.let { feed ->
                Timber.tag("AutoTranslateTitle").d("changeAutoTranslateTitlePreset: Feed ID = ${feed.id}, Name = ${feed.name}, Current isAutoTranslateTitle = ${feed.isAutoTranslateTitle}")
                val isAutoTranslateTitle = !feed.isAutoTranslateTitle
                Timber.tag("AutoTranslateTitle").d("changeAutoTranslateTitlePreset: New isAutoTranslateTitle = $isAutoTranslateTitle")
                val updatedFeed = feed.copy(isAutoTranslateTitle = isAutoTranslateTitle)
                rssService.get().updateFeed(updatedFeed)
                Timber.tag("AutoTranslateTitle").d("changeAutoTranslateTitlePreset: Database update completed for feed ${feed.id}")
                fetchFeed(feed.id)
                Timber.tag("AutoTranslateTitle").d("changeAutoTranslateTitlePreset: FetchFeed completed for feed ${feed.id}")
            } ?: Timber.tag("AutoTranslateTitle").e("changeAutoTranslateTitlePreset: Feed is null")
        }
    }

    fun delete(callback: () -> Unit = {}) {
        _feedOptionUiState.value.feed?.let {
            applicationScope.launch(ioDispatcher) {
                syncLocalRuleIfNeeded(it) { rule ->
                    rule.copy(isEnabled = false, updatedAt = System.currentTimeMillis())
                }
                rssService.get().deleteFeed(it)
                withContext(mainDispatcher) { callback() }
            }
        }
    }

    fun hideDeleteDialog() {
        _feedOptionUiState.update { it.copy(deleteDialogVisible = false) }
    }

    fun showDeleteDialog() {
        _feedOptionUiState.update { it.copy(deleteDialogVisible = true) }
    }

    fun showClearDialog() {
        _feedOptionUiState.update { it.copy(clearDialogVisible = true) }
    }

    fun hideClearDialog() {
        _feedOptionUiState.update { it.copy(clearDialogVisible = false) }
    }

    fun clearFeed(callback: () -> Unit = {}) {
        _feedOptionUiState.value.feed?.let {
            viewModelScope.launch(ioDispatcher) {
                rssService.get().deleteArticles(feed = it)
                withContext(mainDispatcher) { callback() }
            }
        }
    }

    fun renameFeed() {
        _feedOptionUiState.value.feed?.let {
            applicationScope.launch {
                val updated = it.copy(name = _feedOptionUiState.value.newName)
                rssService.get().renameFeed(updated)
                syncLocalRuleIfNeeded(updated) { rule ->
                    rule.copy(name = updated.name, updatedAt = System.currentTimeMillis())
                }
                _feedOptionUiState.update { it.copy(renameDialogVisible = false) }
            }
        }
    }

    fun showRenameDialog() {
        _feedOptionUiState.update {
            it.copy(renameDialogVisible = true, newName = _feedOptionUiState.value.feed?.name ?: "")
        }
    }

    fun hideRenameDialog() {
        _feedOptionUiState.update { it.copy(renameDialogVisible = false, newName = "") }
    }

    fun inputNewName(content: String) {
        _feedOptionUiState.update { it.copy(newName = content) }
    }

    fun showFeedUrlDialog() {
        _feedOptionUiState.update {
            it.copy(
                changeUrlDialogVisible = true,
                newUrl = _feedOptionUiState.value.feed?.url ?: "",
            )
        }
    }

    fun hideFeedUrlDialog() {
        _feedOptionUiState.update { it.copy(changeUrlDialogVisible = false, newUrl = "") }
    }

    fun inputNewUrl(content: String) {
        _feedOptionUiState.update { it.copy(newUrl = content) }
    }

    fun changeFeedUrl() {
        _feedOptionUiState.value.feed?.let {
            applicationScope.launch {
                rssService.get().changeFeedUrl(it.copy(url = _feedOptionUiState.value.newUrl))
                _feedOptionUiState.update { it.copy(changeUrlDialogVisible = false) }
            }
        }
    }

    fun showChangeIconDialog() {
        _feedOptionUiState.update {
            it.copy(
                changeIconDialogVisible = true,
                newIcon = _feedOptionUiState.value.feed?.icon ?: "",
            )
        }
    }

    fun hideChangeIconDialog() {
        _feedOptionUiState.update { it.copy(changeIconDialogVisible = false, newIcon = "") }
    }

    fun inputNewIcon(content: String) {
        _feedOptionUiState.update { it.copy(newIcon = content) }
    }

    fun changeIconUrl() {
        _feedOptionUiState.value.feed?.let {
            applicationScope.launch {
                val updated = it.copy(icon = _feedOptionUiState.value.newIcon)
                rssService.get().updateFeed(updated)
                syncLocalRuleIfNeeded(updated) { rule ->
                    rule.copy(icon = updated.icon ?: rule.icon, updatedAt = System.currentTimeMillis())
                }
                _feedOptionUiState.update { it.copy(changeIconDialogVisible = false) }
            }
        }
    }


    fun importIconFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null || bytes.isEmpty()) return@launch
            val mime =
                context.contentResolver.getType(uri)
                    ?: runCatching { URLConnection.guessContentTypeFromStream(bytes.inputStream()) }
                        .getOrNull()
                    ?: "image/*"
            val dataUri = "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
            _feedOptionUiState.value.feed?.let { feed ->
                val updated = feed.copy(icon = dataUri)
                rssService.get().updateFeed(updated)
                syncLocalRuleIfNeeded(updated) { rule ->
                    rule.copy(icon = updated.icon ?: rule.icon, updatedAt = System.currentTimeMillis())
                }
                fetchFeed(feed.id)
                _feedOptionUiState.update { it.copy(newIcon = dataUri) }
            }
        }
    }

    fun exportIconToUri(context: Context, uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            val icon = _feedOptionUiState.value.feed?.icon ?: return@launch
            val bytes =
                when {
                    icon.startsWith("data:", ignoreCase = true) -> {
                        val base64 = icon.substringAfter("base64,", "")
                        if (base64.isBlank()) return@launch
                        runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
                    }
                    icon.startsWith("http://", ignoreCase = true) ||
                        icon.startsWith("https://", ignoreCase = true) -> {
                        runCatching { URL(icon).openStream().use { it.readBytes() } }.getOrNull()
                    }
                    else -> null
                }
            if (bytes == null || bytes.isEmpty()) return@launch
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        }
    }

    fun consumeIconSearchResult() {
        _feedOptionUiState.update { it.copy(iconSearchResult = null) }
    }

    fun reloadIcon() {
        val feed = _feedOptionUiState.value.feed ?: return
        if (_feedOptionUiState.value.isIconSearching) return
        _feedOptionUiState.update { it.copy(isIconSearching = true, iconSearchResult = null) }
        viewModelScope.launch(ioDispatcher) {
            val icon = runCatching { rssHelper.queryRssIconLink(feed.url) }.getOrNull()
            if (icon.isNullOrBlank()) {
                _feedOptionUiState.update {
                    it.copy(isIconSearching = false, iconSearchResult = IconSearchResult.NotFound)
                }
                return@launch
            }
            runCatching { feedDao.update(feed.copy(icon = icon)) }
                .onFailure {
                    _feedOptionUiState.update {
                        it.copy(isIconSearching = false, iconSearchResult = IconSearchResult.Failed)
                    }
                }
                .onSuccess {
                    fetchFeed(feed.id)
                    _feedOptionUiState.update { it.copy(isIconSearching = false) }
                }
        }
    }

    fun showImageFilterDialog() {
        val feed = _feedOptionUiState.value.feed
        _feedOptionUiState.update {
            it.copy(
                imageFilterDialogVisible = true,
                imageFilterEnabled = feed?.isImageFilterEnabled ?: false,
                imageFilterResolution = feed?.imageFilterResolution ?: "",
                imageFilterFileName = feed?.imageFilterFileName ?: "",
                imageFilterDomain = feed?.imageFilterDomain ?: "",
            )
        }
    }

    fun hideImageFilterDialog() {
        _feedOptionUiState.update { it.copy(imageFilterDialogVisible = false) }
    }

    fun inputImageFilterEnabled(enabled: Boolean) {
        _feedOptionUiState.update { it.copy(imageFilterEnabled = enabled) }
    }

    fun inputImageFilterResolution(value: String) {
        _feedOptionUiState.update { it.copy(imageFilterResolution = value) }
    }

    fun inputImageFilterFileName(value: String) {
        _feedOptionUiState.update { it.copy(imageFilterFileName = value) }
    }

    fun inputImageFilterDomain(value: String) {
        _feedOptionUiState.update { it.copy(imageFilterDomain = value) }
    }

    fun saveImageFilterSettings() {
        viewModelScope.launch(ioDispatcher) {
            val state = _feedOptionUiState.value
            val feed = state.feed ?: return@launch
            val updated =
                feed.copy(
                    isImageFilterEnabled = state.imageFilterEnabled,
                    imageFilterResolution = state.imageFilterResolution.trim(),
                    imageFilterFileName = state.imageFilterFileName.trim(),
                    imageFilterDomain = state.imageFilterDomain.trim(),
                )
            rssService.get().updateFeed(updated)
            fetchFeed(feed.id)
            _feedOptionUiState.update { it.copy(imageFilterDialogVisible = false) }
        }
    }

    private suspend fun syncLocalRuleIfNeeded(
        feed: Feed,
        update: (me.ash.reader.plugin.PluginRule) -> me.ash.reader.plugin.PluginRule,
    ) {
        if (!feed.url.startsWith(PluginConstants.PLUGIN_URL_PREFIX)) return
        val ruleId = feed.url.removePrefix(PluginConstants.PLUGIN_URL_PREFIX)
        val rule = pluginRuleDao.queryById(ruleId) ?: return
        pluginRuleDao.insert(update(rule))
    }

    suspend fun buildExportPayload(feedId: String): ExportPayload {
        return withContext(ioDispatcher) {
            val feed = rssService.get().findFeedById(feedId)
                ?: return@withContext ExportPayload(
                    fileName = "feed.xml",
                    mime = "text/xml",
                    content = "",
                )
            val baseName = (feed.name ?: "feed").ifBlank { "feed" }
            if (feed.url.startsWith(PluginConstants.PLUGIN_URL_PREFIX)) {
                val ruleId = feed.url.removePrefix(PluginConstants.PLUGIN_URL_PREFIX)
                val rule = pluginRuleDao.queryById(ruleId)
                if (rule != null) {
                    val json = pluginRuleTransferService.exportRule(rule, feed)
                    return@withContext ExportPayload(
                        fileName = "${baseName}.json",
                        mime = "application/json",
                        content = json,
                    )
                }
            }
            val opml = opmlService.saveSingleFeedToString(feedId, attachInfo = true)
            ExportPayload(
                fileName = "${baseName}.xml",
                mime = "text/xml",
                content = opml,
            )
        }
    }
}



enum class IconSearchResult {
    NotFound,
    Failed,
}

data class ExportPayload(
    val fileName: String,
    val mime: String,
    val content: String,
)

data class FeedOptionUiState(
    val feed: Feed? = null,
    val selectedGroupId: String = "",
    val newGroupContent: String = "",
    val newGroupDialogVisible: Boolean = false,
    val groups: List<Group> = emptyList(),
    val deleteDialogVisible: Boolean = false,
    val clearDialogVisible: Boolean = false,
    val newName: String = "",
    val renameDialogVisible: Boolean = false,
    val newUrl: String = "",
    val changeUrlDialogVisible: Boolean = false,
    val newIcon: String = "",
    val changeIconDialogVisible: Boolean = false,
    val imageFilterDialogVisible: Boolean = false,
    val imageFilterEnabled: Boolean = false,
    val imageFilterResolution: String = "",
    val imageFilterFileName: String = "",
    val imageFilterDomain: String = "",
    val disableRefererEnabled: Boolean = false,
    val disableJavaScriptEnabled: Boolean = false,
    val isIconSearching: Boolean = false,
    val iconSearchResult: IconSearchResult? = null,
)






