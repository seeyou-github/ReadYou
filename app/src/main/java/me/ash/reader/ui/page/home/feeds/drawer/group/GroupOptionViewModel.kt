package me.ash.reader.ui.page.home.feeds.drawer.group

import android.content.Context
import android.content.Intent
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.service.OpmlService
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.plugin.PluginConstants
import me.ash.reader.plugin.PluginRuleDao
import me.ash.reader.R
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.di.MainDispatcher
import javax.inject.Inject

@OptIn(ExperimentalMaterialApi::class)
@HiltViewModel
class GroupOptionViewModel @Inject constructor(
    val rssService: RssService,
    @MainDispatcher
    private val mainDispatcher: CoroutineDispatcher,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope
    private val applicationScope: CoroutineScope,
    private val opmlService: OpmlService,
    private val feedDao: FeedDao,
    private val pluginRuleDao: PluginRuleDao,
    private val accountService: AccountService,
) : ViewModel() {

    private val _groupOptionUiState = MutableStateFlow(GroupOptionUiState())
    val groupOptionUiState: StateFlow<GroupOptionUiState> = _groupOptionUiState.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            rssService.get().pullGroups().collect { groups ->
                _groupOptionUiState.update { it.copy(groups = groups) }
            }
        }
    }

    fun fetchGroup(groupId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val group = rssService.get().findGroupById(groupId)
            withContext(Dispatchers.Main) {
                _groupOptionUiState.update { it.copy(group = group) }
            }
        }
    }


    fun allAllowNotification(isNotification: Boolean, callback: () -> Unit = {}) {
        _groupOptionUiState.value.group?.let {
            viewModelScope.launch(ioDispatcher) {
                rssService.get().groupAllowNotification(it, isNotification)
                withContext(mainDispatcher) {
                    callback()
                }
            }
        }
    }

    fun showAllAllowNotificationDialog() {
        _groupOptionUiState.update { it.copy(allAllowNotificationDialogVisible = true) }
    }

    fun hideAllAllowNotificationDialog() {
        _groupOptionUiState.update { it.copy(allAllowNotificationDialogVisible = false) }
    }

    fun allParseFullContent(isFullContent: Boolean, callback: () -> Unit = {}) {
        _groupOptionUiState.value.group?.let {
            viewModelScope.launch(ioDispatcher) {
                rssService.get().groupParseFullContent(it, isFullContent)
                withContext(mainDispatcher) {
                    callback()
                }
            }
        }
    }

    fun showAllParseFullContentDialog() {
        _groupOptionUiState.update { it.copy(allParseFullContentDialogVisible = true) }
    }
        
    fun hideAllParseFullContentDialog() {
        _groupOptionUiState.update { it.copy(allParseFullContentDialogVisible = false) }
    }

    fun allOpenInBrowser(isBrowser: Boolean, callback: () -> Unit = {}) {
        _groupOptionUiState.value.group?.let {
            viewModelScope.launch(ioDispatcher) {
                rssService.get().groupOpenInBrowser(it, isBrowser)
                withContext(mainDispatcher) {
                    callback()
                }
            }
        }
    }

    fun showAllOpenInBrowserDialog() {
        _groupOptionUiState.update { it.copy(allOpenInBrowserDialogVisible = true) }
    }

    fun hideAllOpenInBrowserDialog() {
        _groupOptionUiState.update { it.copy(allOpenInBrowserDialogVisible = false) }
    }

    fun delete(callback: () -> Unit = {}) {
        _groupOptionUiState.value.group?.let {
            applicationScope.launch(ioDispatcher) {
                rssService.get().deleteGroup(it)
                withContext(mainDispatcher) {
                    callback()
                }
            }
        }
    }

    fun showDeleteDialog() {
        _groupOptionUiState.update { it.copy(deleteDialogVisible = true) }
    }

    fun hideDeleteDialog() {
        _groupOptionUiState.update { it.copy(deleteDialogVisible = false) }
    }

    fun showClearDialog() {
        _groupOptionUiState.update { it.copy(clearDialogVisible = true) }
    }

    fun hideClearDialog() {
        _groupOptionUiState.update { it.copy(clearDialogVisible = false) }
    }

    fun clear(callback: () -> Unit = {}) {
        _groupOptionUiState.value.group?.let {
            viewModelScope.launch(ioDispatcher) {
                rssService.get().deleteArticles(group = it)
                withContext(mainDispatcher) {
                    callback()
                }
            }
        }
    }

    fun allMoveToGroup(callback: () -> Unit) {
        _groupOptionUiState.value.group?.let { group ->
            _groupOptionUiState.value.targetGroup?.let { targetGroup ->
                viewModelScope.launch(ioDispatcher) {
                    val accountId = accountService.getCurrentAccountId()
                    val pluginRuleIds =
                        feedDao.queryByGroupId(accountId, group.id)
                            .filter { it.url.startsWith(PluginConstants.PLUGIN_URL_PREFIX) }
                            .map { it.url.removePrefix(PluginConstants.PLUGIN_URL_PREFIX) }
                            .toSet()
                    rssService.get().groupMoveToTargetGroup(group, targetGroup)
                    if (pluginRuleIds.isNotEmpty()) {
                        pluginRuleIds.forEach { ruleId ->
                            val rule = pluginRuleDao.queryById(ruleId) ?: return@forEach
                            pluginRuleDao.insert(
                                rule.copy(
                                    groupId = targetGroup.id,
                                    updatedAt = System.currentTimeMillis(),
                                )
                            )
                        }
                    }
                    withContext(mainDispatcher) {
                        callback()
                    }
                }
            }
        }
    }

    fun showAllMoveToGroupDialog(targetGroup: Group) {
        _groupOptionUiState.update {
            it.copy(
                targetGroup = targetGroup,
                allMoveToGroupDialogVisible = true,
            )
        }
    }

    fun hideAllMoveToGroupDialog() {
        _groupOptionUiState.update {
            it.copy(
                targetGroup = null,
                allMoveToGroupDialogVisible = false,
            )
        }
    }

    fun rename() {
        _groupOptionUiState.value.group?.let {
            applicationScope.launch {
                rssService.get().renameGroup(it.copy(name = _groupOptionUiState.value.newName))
                _groupOptionUiState.update { it.copy(renameDialogVisible = false) }
            }
        }
    }

    fun showRenameDialog() {
        _groupOptionUiState.update {
            it.copy(
                renameDialogVisible = true,
                newName = _groupOptionUiState.value.group?.name ?: "",
            )
        }
    }

    fun hideRenameDialog() {
        _groupOptionUiState.update {
            it.copy(
                renameDialogVisible = false,
                newName = "",
            )
        }
    }

    fun inputNewName(content: String) {
        _groupOptionUiState.update { it.copy(newName = content) }
    }

    suspend fun exportGroupAsOpml(groupId: String): String {
        return opmlService.saveGroupFeedsToString(groupId, attachInfo = true)
    }
}

@OptIn(ExperimentalMaterialApi::class)
data class GroupOptionUiState(
    val group: Group? = null,
    val targetGroup: Group? = null,
    val groups: List<Group> = emptyList(),
    val allAllowNotificationDialogVisible: Boolean = false,
    val allParseFullContentDialogVisible: Boolean = false,
    val allOpenInBrowserDialogVisible: Boolean = false,
    val allMoveToGroupDialogVisible: Boolean = false,
    val deleteDialogVisible: Boolean = false,
    val clearDialogVisible: Boolean = false,
    val newName: String = "",
    val renameDialogVisible: Boolean = false,
)
