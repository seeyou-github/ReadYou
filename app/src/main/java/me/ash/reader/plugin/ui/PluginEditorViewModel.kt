package me.ash.reader.plugin.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.ash.reader.domain.service.AccountService
import me.ash.reader.plugin.PluginFeedManager
import me.ash.reader.plugin.PluginRule
import me.ash.reader.plugin.PluginRuleDao
import me.ash.reader.ui.ext.spacerDollar
import java.util.UUID

@HiltViewModel
class PluginEditorViewModel @Inject constructor(
    private val pluginRuleDao: PluginRuleDao,
    private val accountService: AccountService,
    private val pluginFeedManager: PluginFeedManager,
    private val pluginSyncService: me.ash.reader.plugin.PluginSyncService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PluginEditorState())
    val uiState: StateFlow<PluginEditorState> = _uiState.asStateFlow()

    fun load(pluginId: String?) {
        viewModelScope.launch {
            if (pluginId.isNullOrBlank()) {
                _uiState.update { PluginEditorState() }
                return@launch
            }
            val rule = pluginRuleDao.queryById(pluginId)
            if (rule == null) {
                Log.w(TAG, "rule not found: $pluginId")
                _uiState.update { PluginEditorState() }
                return@launch
            }
            _uiState.update { PluginEditorState.fromRule(rule) }
        }
    }

    fun updateState(update: PluginEditorState.() -> PluginEditorState) {
        _uiState.update { it.update() }
    }

    fun save(onInvalid: (String) -> Unit, onSaved: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            val error = state.validate()
            if (error != null) {
                Log.w(TAG, "validate failed: $error")
                onInvalid(error)
                return@launch
            }

            val accountId = accountService.getCurrentAccountId()
            val now = System.currentTimeMillis()
            val id = state.id ?: accountId.spacerDollar(UUID.randomUUID().toString())
            val contentSelectors = state.detailContentSelectors.filter { it.isNotBlank() }
            val firstContentSelector = contentSelectors.firstOrNull().orEmpty()
            val rule =
                PluginRule(
                    id = id,
                    accountId = accountId,
                    name = state.name.trim(),
                    subscribeUrl = state.subscribeUrl.trim(),
                    icon = state.icon.trim(),
                    listHtmlCache = state.listHtmlCache,
                    listTitleSelector = state.listTitleSelector.trim(),
                    listUrlSelector = state.listUrlSelector.trim(),
                    listImageSelector = state.listImageSelector.trim(),
                    listTimeSelector = state.listTimeSelector.trim(),
                    detailTitleSelector = state.detailTitleSelector.trim(),
                    detailAuthorSelector = state.detailAuthorSelector.trim(),
                    detailTimeSelector = state.detailTimeSelector.trim(),
                    detailContentSelector = firstContentSelector,
                    detailContentSelectors = contentSelectors.joinToString("||"),
                    detailExcludeSelector = state.detailExcludeSelector.trim(),
                    detailImageSelector = state.detailImageSelector.trim(),
                    detailVideoSelector = state.detailVideoSelector.trim(),
                    detailAudioSelector = state.detailAudioSelector.trim(),
                    cacheContentOnUpdate = state.cacheContentOnUpdate,
                    isEnabled = true,
                    createdAt = state.createdAt ?: now,
                    updatedAt = now,
                )
            pluginRuleDao.insert(rule)
            pluginFeedManager.ensureFeed(rule)
            _uiState.update { PluginEditorState.fromRule(rule) }
            onSaved()
        }
    }

    fun testListPreview() {
        viewModelScope.launch {
            val state = _uiState.value
            val error = state.validateListOnly()
            if (error != null) {
                Log.w(TAG, "test parse validate failed: $error")
                _uiState.update { it.copy(testResult = error) }
                return@launch
            }
            val accountId = accountService.getCurrentAccountId()
            val contentSelectors = state.detailContentSelectors.filter { it.isNotBlank() }
            val tempRule =
                PluginRule(
                    id = "preview",
                    accountId = accountId,
                    name = state.name.ifBlank { "preview" },
                    subscribeUrl = state.subscribeUrl.trim(),
                    icon = state.icon.trim(),
                    listHtmlCache = state.listHtmlCache,
                    listTitleSelector = state.listTitleSelector.trim(),
                    listUrlSelector = state.listUrlSelector.trim(),
                    listImageSelector = state.listImageSelector.trim(),
                    listTimeSelector = state.listTimeSelector.trim(),
                    detailTitleSelector = state.detailTitleSelector.trim(),
                    detailAuthorSelector = state.detailAuthorSelector.trim(),
                    detailTimeSelector = state.detailTimeSelector.trim(),
                    detailContentSelector = contentSelectors.firstOrNull().orEmpty(),
                    detailContentSelectors = contentSelectors.joinToString("||"),
                    detailExcludeSelector = state.detailExcludeSelector.trim(),
                    detailImageSelector = state.detailImageSelector.trim(),
                    detailVideoSelector = state.detailVideoSelector.trim(),
                    detailAudioSelector = state.detailAudioSelector.trim(),
                    cacheContentOnUpdate = state.cacheContentOnUpdate,
                    isEnabled = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            pluginSyncService.previewListItems(tempRule)
                .onSuccess { items ->
                    _uiState.update { s ->
                        s.copy(
                            testResult = "",
                            listPreviewItems = items.map { item ->
                                PluginPreviewItem(
                                    title = item.title,
                                    link = item.link,
                                    image = item.image,
                                    time = item.time,
                                )
                            },
                            listPreviewVisible = true,
                        )
                    }
                }
                .onFailure { th ->
                    _uiState.update { s -> s.copy(testResult = "解析失败：${th.message}") }
                }
        }
    }

    fun testDetailPreview() {
        viewModelScope.launch {
            val state = _uiState.value
            val error = state.validate()
            if (error != null) {
                Log.w(TAG, "test detail validate failed: $error")
                _uiState.update { it.copy(testResult = error) }
                return@launch
            }
            val accountId = accountService.getCurrentAccountId()
            val contentSelectors = state.detailContentSelectors.filter { it.isNotBlank() }
            val tempRule =
                PluginRule(
                    id = "preview",
                    accountId = accountId,
                    name = state.name.ifBlank { "preview" },
                    subscribeUrl = state.subscribeUrl.trim(),
                    icon = state.icon.trim(),
                    listHtmlCache = state.listHtmlCache,
                    listTitleSelector = state.listTitleSelector.trim(),
                    listUrlSelector = state.listUrlSelector.trim(),
                    listImageSelector = state.listImageSelector.trim(),
                    listTimeSelector = state.listTimeSelector.trim(),
                    detailTitleSelector = state.detailTitleSelector.trim(),
                    detailAuthorSelector = state.detailAuthorSelector.trim(),
                    detailTimeSelector = state.detailTimeSelector.trim(),
                    detailContentSelector = contentSelectors.firstOrNull().orEmpty(),
                    detailContentSelectors = contentSelectors.joinToString("||"),
                    detailExcludeSelector = state.detailExcludeSelector.trim(),
                    detailImageSelector = state.detailImageSelector.trim(),
                    detailVideoSelector = state.detailVideoSelector.trim(),
                    detailAudioSelector = state.detailAudioSelector.trim(),
                    cacheContentOnUpdate = state.cacheContentOnUpdate,
                    isEnabled = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            pluginSyncService.previewDetail(tempRule)
                .onSuccess { detail ->
                    _uiState.update { s ->
                        s.copy(
                            testResult = "",
                            detailPreviewHtml = detail.contentHtml,
                            detailPreviewVisible = true,
                        )
                    }
                }
                .onFailure { th ->
                    _uiState.update { s -> s.copy(testResult = "解析失败：${th.message}") }
                }
        }
    }

    fun downloadListHtml(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.subscribeUrl.isBlank()) {
                onResult("订阅URL不能为空")
                return@launch
            }
            pluginSyncService.downloadListHtml(state.subscribeUrl.trim())
                .onSuccess { html ->
                    if (html.isBlank()) {
                        onResult("下载失败：HTML为空")
                        return@onSuccess
                    }
                    _uiState.update { it.copy(listHtmlCache = html) }
                    val ruleId = state.id
                    if (!ruleId.isNullOrBlank()) {
                        val rule = pluginRuleDao.queryById(ruleId)
                        if (rule != null) {
                            pluginRuleDao.update(rule.copy(listHtmlCache = html, updatedAt = System.currentTimeMillis()))
                        }
                    }
                    onResult("下载完成，已缓存HTML")
                }
                .onFailure { th ->
                    onResult("下载失败：${th.message}")
                }
        }
    }

    fun hideListPreview() {
        _uiState.update { it.copy(listPreviewVisible = false) }
    }

    fun hideDetailPreview() {
        _uiState.update { it.copy(detailPreviewVisible = false) }
    }

    fun debugSelectors() {
        viewModelScope.launch {
            val state = _uiState.value
            val error = state.validateListOnly()
            if (error != null) {
                _uiState.update { it.copy(testResult = error) }
                return@launch
            }
            val accountId = accountService.getCurrentAccountId()
            val contentSelectors = state.detailContentSelectors.filter { it.isNotBlank() }
            val tempRule =
                PluginRule(
                    id = "preview",
                    accountId = accountId,
                    name = state.name.ifBlank { "preview" },
                    subscribeUrl = state.subscribeUrl.trim(),
                    icon = state.icon.trim(),
                    listHtmlCache = state.listHtmlCache,
                    listTitleSelector = state.listTitleSelector.trim(),
                    listUrlSelector = state.listUrlSelector.trim(),
                    listImageSelector = state.listImageSelector.trim(),
                    listTimeSelector = state.listTimeSelector.trim(),
                    detailTitleSelector = state.detailTitleSelector.trim(),
                    detailAuthorSelector = state.detailAuthorSelector.trim(),
                    detailTimeSelector = state.detailTimeSelector.trim(),
                    detailContentSelector = contentSelectors.firstOrNull().orEmpty(),
                    detailContentSelectors = contentSelectors.joinToString("||"),
                    detailExcludeSelector = state.detailExcludeSelector.trim(),
                    detailImageSelector = state.detailImageSelector.trim(),
                    detailVideoSelector = state.detailVideoSelector.trim(),
                    detailAudioSelector = state.detailAudioSelector.trim(),
                    cacheContentOnUpdate = state.cacheContentOnUpdate,
                    isEnabled = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            pluginSyncService.debugSelectors(tempRule)
                .onSuccess { items ->
                    _uiState.update { s ->
                        s.copy(
                            debugItems = items.map {
                                PluginSelectorDebugItem(
                                    label = it.label,
                                    selector = it.selector,
                                    count = it.count,
                                    samples = it.samples,
                                )
                            },
                            debugVisible = true,
                        )
                    }
                }
                .onFailure { th ->
                    _uiState.update { s -> s.copy(testResult = "调试失败：${th.message}") }
                }
        }
    }

    fun hideDebug() {
        _uiState.update { it.copy(debugVisible = false) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            val id = state.id ?: return@launch
            val rule = pluginRuleDao.queryById(id) ?: return@launch
            pluginRuleDao.delete(rule)
            pluginFeedManager.removeFeed(rule)
            onDeleted()
        }
    }

    companion object {
        private const val TAG = "PluginEditorVM"
    }
}

data class PluginEditorState(
    val id: String? = null,
    val name: String = "",
    val subscribeUrl: String = "",
    val icon: String = "",
    val listHtmlCache: String = "",
    val listTitleSelector: String = "",
    val listUrlSelector: String = "",
    val listImageSelector: String = "",
    val listTimeSelector: String = "",
    val detailTitleSelector: String = "",
    val detailAuthorSelector: String = "",
    val detailTimeSelector: String = "",
    val detailContentSelectors: List<String> = listOf(""),
    val detailExcludeSelector: String = "",
    val detailImageSelector: String = "",
    val detailVideoSelector: String = "",
    val detailAudioSelector: String = "",
    val cacheContentOnUpdate: Boolean = false,
    val createdAt: Long? = null,
    val testResult: String = "",
    val listPreviewItems: List<PluginPreviewItem> = emptyList(),
    val listPreviewVisible: Boolean = false,
    val detailPreviewHtml: String = "",
    val detailPreviewVisible: Boolean = false,
    val debugItems: List<PluginSelectorDebugItem> = emptyList(),
    val debugVisible: Boolean = false,
) {
    fun validate(): String? {
        if (subscribeUrl.isBlank()) return "订阅URL不能为空"
        if (listTitleSelector.isBlank()) return "标题列表选择器不能为空"
        if (listUrlSelector.isBlank()) return "文章URL选择器不能为空"
        if (detailContentSelectors.all { it.isBlank() }) return "正文选择器不能为空"
        return null
    }

    fun validateListOnly(): String? {
        if (subscribeUrl.isBlank()) return "订阅URL不能为空"
        if (listTitleSelector.isBlank()) return "标题列表选择器不能为空"
        if (listUrlSelector.isBlank()) return "文章URL选择器不能为空"
        return null
    }

    companion object {
        fun fromRule(rule: PluginRule): PluginEditorState {
            val contentSelectors =
                rule.detailContentSelectors
                    .split("||")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .ifEmpty { listOf(rule.detailContentSelector).filter { it.isNotBlank() } }
            return PluginEditorState(
                id = rule.id,
                name = rule.name,
                subscribeUrl = rule.subscribeUrl,
                icon = rule.icon,
                listHtmlCache = rule.listHtmlCache,
                listTitleSelector = rule.listTitleSelector,
                listUrlSelector = rule.listUrlSelector,
                listImageSelector = rule.listImageSelector,
                listTimeSelector = rule.listTimeSelector,
                detailTitleSelector = rule.detailTitleSelector,
                detailAuthorSelector = rule.detailAuthorSelector,
                detailTimeSelector = rule.detailTimeSelector,
                detailContentSelectors = if (contentSelectors.isEmpty()) listOf("") else contentSelectors,
                detailExcludeSelector = rule.detailExcludeSelector,
                detailImageSelector = rule.detailImageSelector,
                detailVideoSelector = rule.detailVideoSelector,
                detailAudioSelector = rule.detailAudioSelector,
                cacheContentOnUpdate = rule.cacheContentOnUpdate,
                createdAt = rule.createdAt,
            )
        }
    }
}

data class PluginPreviewItem(
    val title: String,
    val link: String,
    val image: String?,
    val time: String?,
)

data class PluginSelectorDebugItem(
    val label: String,
    val selector: String,
    val count: Int,
    val samples: List<String>,
)
