package me.ash.reader.ui.page.settings.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.blacklist.BlacklistKeyword
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.repository.BlacklistKeywordDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.OpmlService
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.AutoMarkAsReadPreference
import me.ash.reader.infrastructure.preference.KeepArchivedPreference
import me.ash.reader.infrastructure.preference.SyncBlockListPreference
import me.ash.reader.infrastructure.preference.SyncIntervalPreference
import me.ash.reader.infrastructure.preference.SyncOnStartPreference
import me.ash.reader.infrastructure.preference.SyncOnlyOnWiFiPreference
import me.ash.reader.infrastructure.preference.SyncOnlyWhenChargingPreference
import me.ash.reader.ui.ext.fromDataStoreToJSONString
import me.ash.reader.ui.ext.fromJSONStringToDataStore
import me.ash.reader.ui.ext.getDefaultGroupId
import me.ash.reader.plugin.PluginRule
import me.ash.reader.plugin.PluginRuleDao

@HiltViewModel
class BackupAndRestoreViewModel
@Inject
constructor(
    private val accountService: AccountService,
    private val opmlService: OpmlService,
    private val blacklistKeywordDao: BlacklistKeywordDao,
    private val groupDao: GroupDao,
    private val feedDao: FeedDao,
    private val pluginRuleDao: PluginRuleDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _backupUiState = MutableStateFlow(BackupUiState())
    val backupUiState: StateFlow<BackupUiState> = _backupUiState.asStateFlow()

    fun tryImport(context: Context, byteArray: ByteArray) {
        _backupUiState.update { it.copy(byteArray = byteArray, warningDialogVisible = true) }
    }

    fun importPreferencesFromJSON(context: Context, byteArray: ByteArray) {
        viewModelScope.launch(ioDispatcher) {
            String(byteArray).fromJSONStringToDataStore(context)
            _backupUiState.update { it.copy(warningDialogVisible = false) }
        }
    }

    fun exportPreferencesAsJSON(context: Context, callback: (ByteArray) -> Unit = {}) {
        viewModelScope.launch(ioDispatcher) {
            callback(context.fromDataStoreToJSONString().toByteArray())
        }
    }

    fun exportAsOPML(context: Context, callback: (String) -> Unit = {}) {
        viewModelScope.launch {
            val currentAccountId = accountService.getCurrentAccountId()
            callback(opmlService.saveToString(
                currentAccountId,
                _backupUiState.value.exportOPMLMode == ExportOPMLMode.ATTACH_INFO
            ))
        }
    }

    fun showWarningDialog() {
        _backupUiState.update { it.copy(warningDialogVisible = true) }
    }

    fun hideWarningDialog() {
        _backupUiState.update { it.copy(warningDialogVisible = false) }
    }

    fun showExportOPMLModeDialog() {
        _backupUiState.update { it.copy(exportOPMLModeDialogVisible = true) }
    }

    fun hideExportOPMLModeDialog() {
        _backupUiState.update { it.copy(exportOPMLModeDialogVisible = false) }
    }

    fun changeExportOPMLMode(mode: ExportOPMLMode) {
        _backupUiState.update { it.copy(exportOPMLMode = mode) }
    }

    fun exportKeywordsAsJSON(context: Context, callback: (ByteArray) -> Unit = {}) {
        viewModelScope.launch(ioDispatcher) {
            val keywords = blacklistKeywordDao.getAllSync()
            val jsonString = Gson().toJson(KeywordsBackupPayload(keywords = keywords))
            callback(jsonString.toByteArray())
        }
    }

    fun tryImportKeywords(context: Context, byteArray: ByteArray) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val jsonString = String(byteArray)
                val payload = Gson().fromJson(jsonString, KeywordsBackupPayload::class.java)
                val count = payload.keywords.size

                _backupUiState.update {
                    it.copy(
                        keywordsByteArray = byteArray,
                        keywordsImportCount = count,
                        keywordsWarningDialogVisible = true
                    )
                }
            } catch (e: Exception) {
                _backupUiState.update {
                    it.copy(
                        keywordsByteArray = byteArray,
                        keywordsImportCount = -1,
                        keywordsWarningDialogVisible = true
                    )
                }
            }
        }
    }

    fun importKeywordsFromJSON(context: Context, byteArray: ByteArray) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val jsonString = String(byteArray)
                val payload = Gson().fromJson(jsonString, KeywordsBackupPayload::class.java)
                if (payload.keywords.isNotEmpty()) {
                    val existingKeywords = blacklistKeywordDao.getAllSync()
                    val existingKeywordsSet = existingKeywords.map { it.keyword }.toSet()
                    payload.keywords
                        .filter { it.keyword !in existingKeywordsSet }
                        .forEach { keyword ->
                            blacklistKeywordDao.insert(keyword.copy(id = 0))
                        }
                }
            } catch (e: Exception) {
                // Handle import error
            } finally {
                _backupUiState.update { it.copy(keywordsWarningDialogVisible = false) }
            }
        }
    }

    fun hideKeywordsWarningDialog() {
        _backupUiState.update { it.copy(keywordsWarningDialogVisible = false) }
    }

        fun tryRestoreOneClick(context: Context, byteArray: ByteArray) {
        _backupUiState.update {
            it.copy(oneClickRestoreByteArray = byteArray, oneClickRestoreDialogVisible = true)
        }
    }

    fun hideOneClickRestoreDialog() {
        _backupUiState.update { it.copy(oneClickRestoreDialogVisible = false) }
    }

    fun performOneClickBackup(context: Context, outputUri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            _backupUiState.update { it.copy(isBackingUp = true, backupSuccess = false, backupError = null) }
            try {
                val accountId = accountService.getCurrentAccountId()
                val account = accountService.getAccountById(accountId)
                val payload =
                    OneClickBackupPayload(
                        version = 2,
                        exportedAt = System.currentTimeMillis(),
                        accountId = accountId,
                        autoMarkAsReadMs = account?.autoMarkAsRead?.value,
                        accountSettings =
                            account?.let {
                                AccountSettingsBackupPayload(
                                    syncIntervalMinutes = it.syncInterval.value,
                                    syncOnStart = it.syncOnStart.value,
                                    syncOnlyOnWiFi = it.syncOnlyOnWiFi.value,
                                    syncOnlyWhenCharging = it.syncOnlyWhenCharging.value,
                                    keepArchivedMs = it.keepArchived.value,
                                    autoMarkAsReadMs = it.autoMarkAsRead.value,
                                    syncBlockList = it.syncBlockList.joinToString("\n"),
                                )
                            },
                        preferencesJson = context.fromDataStoreToJSONString(),
                        groups = groupDao.queryAll(accountId),
                        feeds = feedDao.queryAll(accountId),
                        keywords = blacklistKeywordDao.getAllSync(),
                        pluginRules = pluginRuleDao.queryAll(accountId),
                    )
                val json = Gson().toJson(payload)
                context.contentResolver.openOutputStream(outputUri)?.use {
                    it.write(json.toByteArray())
                } ?: run {
                    _backupUiState.update { it.copy(isBackingUp = false, backupError = "Failed to write backup") }
                    return@launch
                }
                _backupUiState.update { it.copy(isBackingUp = false, backupSuccess = true) }
            } catch (e: Exception) {
                e.printStackTrace()
                _backupUiState.update { it.copy(isBackingUp = false, backupError = e.message ?: "Backup failed") }
            }
        }
    }

    fun performOneClickRestore(context: Context, byteArray: ByteArray) {
        viewModelScope.launch(ioDispatcher) {
            _backupUiState.update {
                it.copy(
                    isRestoring = true,
                    restoreSuccess = false,
                    restoreError = null,
                    oneClickRestoreDialogVisible = false,
                )
            }
            try {
                val jsonString = String(byteArray)
                val payload = Gson().fromJson(jsonString, OneClickBackupPayload::class.java)
                val accountId =
                    payload.accountId.takeIf { accountService.getAccountById(it) != null }
                        ?: accountService.getCurrentAccountId()
                val settings = payload.accountSettings
                if (settings != null || payload.autoMarkAsReadMs != null) {
                    accountService.update(accountId) {
                        val syncInterval =
                            settings?.syncIntervalMinutes?.let { value ->
                                SyncIntervalPreference.values.find { it.value == value }
                            } ?: this.syncInterval
                        val syncOnStart =
                            settings?.syncOnStart?.let { value ->
                                SyncOnStartPreference.values.find { it.value == value }
                            } ?: this.syncOnStart
                        val syncOnlyOnWiFi =
                            settings?.syncOnlyOnWiFi?.let { value ->
                                SyncOnlyOnWiFiPreference.values.find { it.value == value }
                            } ?: this.syncOnlyOnWiFi
                        val syncOnlyWhenCharging =
                            settings?.syncOnlyWhenCharging?.let { value ->
                                SyncOnlyWhenChargingPreference.values.find { it.value == value }
                            } ?: this.syncOnlyWhenCharging
                        val keepArchived =
                            settings?.keepArchivedMs?.let { value ->
                                KeepArchivedPreference.values.find { it.value == value }
                            } ?: this.keepArchived
                        val autoMarkAsRead =
                            settings?.autoMarkAsReadMs?.let { value ->
                                AutoMarkAsReadPreference.values.find { it.value == value }
                            } ?: payload.autoMarkAsReadMs?.let { value ->
                                AutoMarkAsReadPreference.values.find { it.value == value }
                            } ?: this.autoMarkAsRead

                        copy(
                            syncInterval = syncInterval,
                            syncOnStart = syncOnStart,
                            syncOnlyOnWiFi = syncOnlyOnWiFi,
                            syncOnlyWhenCharging = syncOnlyWhenCharging,
                            keepArchived = keepArchived,
                            autoMarkAsRead = autoMarkAsRead,
                            syncBlockList =
                                settings?.syncBlockList?.let { value ->
                                    if (value.isBlank()) {
                                        this.syncBlockList
                                    } else {
                                        SyncBlockListPreference.of(value)
                                    }
                                } ?: this.syncBlockList,
                        )
                    }
                }

                payload.preferencesJson?.fromJSONStringToDataStore(context)

                blacklistKeywordDao.deleteAll()
                pluginRuleDao.queryAll(accountId).forEach { pluginRuleDao.delete(it) }
                feedDao.deleteByAccountId(accountId)
                groupDao.deleteByAccountId(accountId)

                val groups = payload.groups.orEmpty().map { it.copy(accountId = accountId) }
                if (groups.isNotEmpty()) {
                    groupDao.insertAll(groups)
                }
                val defaultGroupId = accountId.getDefaultGroupId()
                if (groups.none { it.id == defaultGroupId }) {
                    val defaultGroup = accountService.getDefaultGroup().copy(accountId = accountId)
                    groupDao.insert(defaultGroup)
                }

                val feeds = payload.feeds.orEmpty().map { it.copy(accountId = accountId) }
                if (feeds.isNotEmpty()) {
                    feedDao.insertAll(feeds)
                }

                payload.pluginRules.orEmpty().forEach { rule ->
                    val safeGroupId =
                        rule.groupId?.takeIf { it.isNotBlank() } ?: defaultGroupId
                    pluginRuleDao.insert(
                        rule.copy(
                            accountId = accountId,
                            groupId = safeGroupId,
                        )
                    )
                }
                payload.keywords.orEmpty().forEach { keyword ->
                    blacklistKeywordDao.insert(keyword.copy(id = 0))
                }

                _backupUiState.update { it.copy(isRestoring = false, restoreSuccess = true) }
            } catch (e: Exception) {
                e.printStackTrace()
                _backupUiState.update {
                    it.copy(
                        isRestoring = false,
                        restoreError = e.message ?: "Restore failed",
                    )
                }
            }
        }
    }}

data class BackupUiState(
    val warningDialogVisible: Boolean = false,
    val exportOPMLModeDialogVisible: Boolean = false,
    val exportOPMLMode: ExportOPMLMode = ExportOPMLMode.ATTACH_INFO,
    val byteArray: ByteArray = ByteArray(0),
    val keywordsWarningDialogVisible: Boolean = false,
    val keywordsByteArray: ByteArray = ByteArray(0),
    val keywordsImportCount: Int = 0,
    val isBackingUp: Boolean = false,
    val backupSuccess: Boolean = false,
    val backupError: String? = null,
    val isRestoring: Boolean = false,
    val restoreSuccess: Boolean = false,
    val restoreError: String? = null,
    val oneClickRestoreDialogVisible: Boolean = false,
    val oneClickRestoreByteArray: ByteArray = ByteArray(0),
)

sealed class ExportOPMLMode {
    object ATTACH_INFO : ExportOPMLMode()
    object NO_ATTACH : ExportOPMLMode()
}

data class OneClickBackupPayload(
    val version: Int,
    val exportedAt: Long,
    val accountId: Int,
    val autoMarkAsReadMs: Long? = null,
    val accountSettings: AccountSettingsBackupPayload? = null,
    val preferencesJson: String? = null,
    val groups: List<Group>? = emptyList(),
    val feeds: List<Feed>? = emptyList(),
    val keywords: List<BlacklistKeyword>? = emptyList(),
    val pluginRules: List<PluginRule>? = emptyList(),
)

data class AccountSettingsBackupPayload(
    val syncIntervalMinutes: Long? = null,
    val syncOnStart: Boolean? = null,
    val syncOnlyOnWiFi: Boolean? = null,
    val syncOnlyWhenCharging: Boolean? = null,
    val keepArchivedMs: Long? = null,
    val autoMarkAsReadMs: Long? = null,
    val syncBlockList: String? = null,
)

data class KeywordsBackupPayload(
    val exportedAt: Long = System.currentTimeMillis(),
    val keywords: List<BlacklistKeyword> = emptyList(),
)


