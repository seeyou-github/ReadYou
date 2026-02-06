package me.ash.reader.ui.page.home.feeds.subscribe

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.feed.synd.SyndFeedImpl
import com.rometools.rome.feed.synd.SyndImageImpl
import android.content.Context
import android.util.Base64
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.R
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.OpmlService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.android.AndroidStringsHelper
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.MainDispatcher
import me.ash.reader.infrastructure.rss.RssHelper
import me.ash.reader.plugin.PluginRuleTransferService
import me.ash.reader.ui.ext.formatUrl
import android.net.Uri
import kotlinx.coroutines.Dispatchers

@HiltViewModel
class SubscribeViewModel
@Inject
constructor(
    private val opmlService: OpmlService,
    val rssService: RssService,
    private val androidStringsHelper: AndroidStringsHelper,
    private val pluginRuleTransferService: PluginRuleTransferService,
    private val rssHelper: RssHelper,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @ApplicationScope private val applicationScope: CoroutineScope,
    accountService: AccountService,
) : ViewModel() {

    private val _subscribeUiState = MutableStateFlow(SubscribeUiState())
    val subscribeUiState: StateFlow<SubscribeUiState> = _subscribeUiState.asStateFlow()

    private val _subscribeState: MutableStateFlow<SubscribeState> =
        MutableStateFlow(SubscribeState.Hidden)
    val subscribeState = _subscribeState.asStateFlow()

    val groupsFlow = MutableStateFlow<List<Group>>(emptyList())

    init {
        viewModelScope.launch {
            accountService.currentAccountFlow.collectLatest {
                rssService.get().pullGroups().collect { groupsFlow.value = it }
            }
        }
        viewModelScope.launch {
            groupsFlow.collect { groups ->
                _subscribeState.update {
                    when (it) {
                        is SubscribeState.Configure -> it.copy(groups = groups)
                        else -> it
                    }
                }
            }
        }
    }

    fun reset() {
        cancelSearch()
    }

    fun importFromInputStream(inputStream: InputStream) {
        applicationScope.launch {
            opmlService.saveToDatabase(inputStream)
        }
    }

    fun importLocalRule(inputStream: InputStream, onResult: (String) -> Unit) {
        applicationScope.launch {
            val content = inputStream.readBytes().decodeToString()
            val trimmed = content.trimStart()
            if (trimmed.startsWith("<")) {
                // 识别为 OPML/XML
                opmlService.saveToDatabase(content.byteInputStream())
                withContext(mainDispatcher) { onResult("OPML导入成功") }
                return@launch
            }
            pluginRuleTransferService.importRule(content)
                .onSuccess {
                    withContext(mainDispatcher) { onResult("本地规则导入成功") }
                }
                .onFailure { th ->
                    withContext(mainDispatcher) { onResult("本地规则导入失败：${th.message}") }
                }
        }
    }

    fun selectedGroup(groupId: String) {
        _subscribeState.update {
            when (it) {
                is SubscribeState.Configure -> it.copy(selectedGroupId = groupId)
                else -> it
            }
        }
    }

    fun addNewGroup() {
        if (_subscribeUiState.value.newGroupContent.isNotBlank()) {
            applicationScope.launch {
                // TODO: How to add a single group without no feeds via Google Reader API?
                selectedGroup(
                    rssService.get().addGroup(null, _subscribeUiState.value.newGroupContent)
                )
                hideNewGroupDialog()
                _subscribeUiState.update { it.copy(newGroupContent = "") }
            }
        }
    }

    fun toggleParseFullContentPreset() {
        _subscribeState.update { state ->
            when (state) {
                is SubscribeState.Configure ->
                    state.copy(fullContent = !state.fullContent, browser = false)

                else -> state
            }
        }
    }

    fun toggleOpenInBrowserPreset() {
        _subscribeState.update { state ->
            when (state) {
                is SubscribeState.Configure ->
                    state.copy(browser = !state.browser, fullContent = false)

                else -> state
            }
        }
    }

    fun toggleAllowNotificationPreset() {
        _subscribeState.update { state ->
            when (state) {
                is SubscribeState.Configure -> state.copy(notification = !state.notification)
                else -> state
            }
        }
    }

    fun toggleAutoTranslatePreset() {
        _subscribeState.update { state ->
            when (state) {
                is SubscribeState.Configure -> state.copy(autoTranslate = !state.autoTranslate)
                else -> state
            }
        }
    }

    fun addFeed() {
        val currentState = _subscribeState.value
        if (currentState !is SubscribeState.Idle) return
        viewModelScope.launch {
            val feedLink = currentState.linkState.text.trim().toString().formatUrl()
            currentState.linkState.edit { this.replace(0, length, feedLink) }

            if (rssService.get().isFeedExist(feedLink)) {
                _subscribeState.value =
                    currentState.copy(
                        errorMessage = androidStringsHelper.getString(R.string.already_subscribed)
                    )
                return@launch
            }
            val groups = groupsFlow.value
            val firstGroupId = groups.firstOrNull()?.id ?: return@launch
            val title = currentState.titleState.text.trim().toString()
            val iconUrl = currentState.iconUrlState.text.trim().toString()
            addFeedDirectly(feedLink, firstGroupId, title, iconUrl)
        }
    }

    fun parseIconFromFeedUrl() {
        val currentState = _subscribeState.value as? SubscribeState.Input ?: return
        val feedLink = currentState.linkState.text.trim().toString().formatUrl()
        if (feedLink.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val iconUrl = runCatching { rssHelper.queryRssIconLink(feedLink) }.getOrNull()
            if (!iconUrl.isNullOrBlank()) {
                currentState.iconUrlState.edit {
                    replace(0, length, iconUrl)
                }
            }
        }
    }

    fun parseTitleFromFeedUrl() {
        val currentState = _subscribeState.value as? SubscribeState.Input ?: return
        val feedLink = currentState.linkState.text.trim().toString().formatUrl()
        if (feedLink.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val title =
                runCatching { rssHelper.searchFeed(feedLink).title }
                    .getOrNull()
                    ?.trim()
                    .orEmpty()
            if (title.isNotBlank()) {
                currentState.titleState.edit {
                    replace(0, length, title)
                }
            }
        }
    }

    fun resolveIconFromInputUrl() {
        val currentState = _subscribeState.value as? SubscribeState.Input ?: return
        val input = currentState.iconUrlState.text.trim().toString()
        if (input.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val resolved =
                if (isLikelyImageUrl(input)) {
                    input
                } else {
                    runCatching { rssHelper.queryRssIconLink(input) }.getOrNull() ?: input
                }
            currentState.iconUrlState.edit {
                replace(0, length, resolved)
            }
        }
    }

    fun importIconFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null || bytes.isEmpty()) return@launch
            val mime = context.contentResolver.getType(uri) ?: "image/*"
            val dataUri = "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
            val currentState = _subscribeState.value as? SubscribeState.Input ?: return@launch
            currentState.iconUrlState.edit { replace(0, length, dataUri) }
        }
    }

    fun cancelSearch() {
        _subscribeState.value.let {
            if (it is SubscribeState.Fetching && it.job.isActive) {
                it.job.cancel()
            }
        }
    }

    fun subscribe() {
        val state = _subscribeState.value
        if (state !is SubscribeState.Configure) return

        applicationScope.launch {
            val searchedFeed = state.searchedFeed
            rssService
                .get()
                .subscribe(
                    searchedFeed = searchedFeed,
                    feedLink = state.feedLink,
                    groupId = state.selectedGroupId,
                    isNotification = state.notification,
                    isFullContent = state.fullContent,
                    isBrowser = state.browser,
                    isAutoTranslate = state.autoTranslate,
                )
            hideDrawer()
        }
    }

    fun inputNewGroup(content: String) {
        _subscribeUiState.update { it.copy(newGroupContent = content) }
    }

    fun handleSharedUrlFromIntent(url: String) {
        viewModelScope
            .launch {
                _subscribeState.update {
                    SubscribeState.Idle(
                        linkState = TextFieldState(url),
                        iconUrlState = TextFieldState(),
                        titleState = TextFieldState(),
                    )
                }
                delay(50)
            }
            .invokeOnCompletion { addFeed() }
    }

    private fun addFeedDirectly(
        feedLink: String,
        groupId: String,
        titleInput: String,
        iconUrlInput: String,
    ) {
        applicationScope.launch {
            val searchedFeed: SyndFeed = SyndFeedImpl().apply {
                title = titleInput.ifBlank { feedLink }
                link = feedLink
                val iconUrl = iconUrlInput.ifBlank { "" }
                if (iconUrl.isNotBlank()) {
                    icon = SyndImageImpl().apply {
                        link = iconUrl
                        url = iconUrl
                    }
                }
            }
            rssService
                .get()
                .subscribe(
                    searchedFeed = searchedFeed,
                    feedLink = feedLink,
                    groupId = groupId,
                    isNotification = false,
                    isFullContent = false,
                    isBrowser = false,
                    isAutoTranslate = false,
                )
            hideDrawer()
        }
    }

    fun showDrawer() {
        _subscribeState.value =
            SubscribeState.Idle(
                importFromOpmlEnabled = rssService.get().importSubscription,
                iconUrlState = TextFieldState(),
                titleState = TextFieldState(),
            )
    }

    fun hideDrawer() {
        cancelSearch()
        _subscribeState.value = SubscribeState.Hidden
    }

    fun showNewGroupDialog() {
        _subscribeUiState.update { it.copy(newGroupDialogVisible = true) }
    }

    fun hideNewGroupDialog() {
        _subscribeUiState.update { it.copy(newGroupDialogVisible = false) }
    }

    fun showRenameDialog() {
        _subscribeUiState.update { it.copy(renameDialogVisible = true) }
        _subscribeUiState.update { uiState ->
            (_subscribeState.value as? SubscribeState.Configure)?.searchedFeed?.title?.let { title
                ->
                uiState.copy(newName = title)
            } ?: uiState
        }
    }

    fun hideRenameDialog() {
        _subscribeUiState.update { it.copy(renameDialogVisible = false, newName = "") }
    }

    fun inputNewName(content: String) {
        _subscribeUiState.update { it.copy(newName = content) }
    }

    fun renameFeed() {
        _subscribeState.update { state ->
            when (state) {
                is SubscribeState.Configure ->
                    state.copy(
                        searchedFeed =
                            state.searchedFeed.apply { title = _subscribeUiState.value.newName }
                    )

                else -> state
            }
        }
    }

    private fun isLikelyImageUrl(value: String): Boolean {
        if (value.startsWith("data:", ignoreCase = true)) return true
        val lower = value.substringBefore("?").lowercase()
        return lower.endsWith(".png") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".gif") ||
            lower.endsWith(".webp") ||
            lower.endsWith(".svg") ||
            lower.endsWith(".ico")
    }
}

data class SubscribeUiState(
    val newGroupDialogVisible: Boolean = false,
    val newGroupContent: String = "",
    val newName: String = "",
    val renameDialogVisible: Boolean = false,
)

sealed interface SubscribeState {
    object Hidden : SubscribeState

    sealed interface Visible

    sealed interface Input : SubscribeState, Visible {
        val linkState: TextFieldState
        val iconUrlState: TextFieldState
        val titleState: TextFieldState
    }

    data class Idle(
        override val linkState: TextFieldState = TextFieldState(),
        override val iconUrlState: TextFieldState = TextFieldState(),
        override val titleState: TextFieldState = TextFieldState(),
        val importFromOpmlEnabled: Boolean = false,
        val errorMessage: String? = null,
    ) : SubscribeState, Input

    data class Fetching(
        override val linkState: TextFieldState,
        override val iconUrlState: TextFieldState,
        override val titleState: TextFieldState,
        val job: Job,
    ) :
        SubscribeState, Input

    data class Configure(
        val searchedFeed: SyndFeed,
        val feedLink: String,
        val groups: List<Group> = emptyList(),
        val notification: Boolean = false,
        val fullContent: Boolean = false,
        val browser: Boolean = false,
        val autoTranslate: Boolean = false,
        val selectedGroupId: String,
    ) : SubscribeState, Visible
}
