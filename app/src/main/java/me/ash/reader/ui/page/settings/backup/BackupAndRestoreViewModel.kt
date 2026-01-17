package me.ash.reader.ui.page.settings.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import me.ash.reader.domain.model.blacklist.BlacklistKeyword
import me.ash.reader.domain.repository.BlacklistKeywordDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.OpmlService
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.PreferencesKey
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.fromDataStoreToJSONString
import me.ash.reader.ui.ext.fromJSONStringToDataStore

/**
 * 将 SAF URI 转换为可读的路径字符串
 */
fun Uri.toReadablePath(context: Context): String {
    return try {
        val treeId = DocumentsContract.getTreeDocumentId(this)
        if (DocumentsContract.isTreeUri(this)) {
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(this, treeId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID
            )
            context.contentResolver.query(documentUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayName = cursor.getString(0)
                    val documentId = cursor.getString(1)
                    // 构建路径，例如：/storage/emulated/0/XXX
                    val storagePath = when {
                        documentId.startsWith("primary:") -> "/storage/emulated/0"
                        documentId.startsWith("home:") -> "/storage/emulated/0"
                        else -> {
                            // 尝试从 documentId 提取路径
                            val colonIndex = documentId.indexOf(':')
                            if (colonIndex > 0) {
                                val volumeName = documentId.substring(0, colonIndex)
                                "/storage/$volumeName"
                            } else {
                                "/storage"
                            }
                        }
                    }
                    val subPath = documentId.substringAfter(':').replace(':', '/')
                    "$storagePath/$subPath".replace("//", "/")
                } else {
                    this.toString()
                }
            } ?: this.toString()
        } else {
            this.toString()
        }
    } catch (e: Exception) {
        this.toString()
    }
}
@HiltViewModel
class BackupAndRestoreViewModel
@Inject
constructor(
    private val accountService: AccountService,
    private val opmlService: OpmlService,
    private val blacklistKeywordDao: BlacklistKeywordDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val applicationScope: CoroutineScope,
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
            val exportData = mapOf(
                "exportedAt" to System.currentTimeMillis(),
                "keywords" to keywords
            )
            val jsonString = Gson().toJson(exportData)
            callback(jsonString.toByteArray())
        }
    }

    fun tryImportKeywords(context: Context, byteArray: ByteArray) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val jsonString = String(byteArray)
                val gson = Gson()
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val importData: Map<String, Any> = gson.fromJson(jsonString, type)

                val keywordsList = importData["keywords"] as? List<*>
                val count = keywordsList?.size ?: 0

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
                val gson = Gson()
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val importData: Map<String, Any> = gson.fromJson(jsonString, type)

                val keywordsList = importData["keywords"] as? List<*>
                if (keywordsList != null) {
                    val existingKeywords = blacklistKeywordDao.getAllSync()
                    val existingKeywordsSet = existingKeywords.map { it.keyword }.toSet()

                    keywordsList.forEach { item ->
                        try {
                            val keywordMap = item as? Map<*, *>
                            if (keywordMap != null) {
                                val keyword = keywordMap["keyword"] as? String
                                if (keyword != null && keyword !in existingKeywordsSet) {
                                    val newKeyword = BlacklistKeyword(
                                        keyword = keyword,
                                        enabled = keywordMap["enabled"] as? Boolean ?: true,
                                        feedUrls = keywordMap["feedUrls"] as? String,
                                        feedNames = keywordMap["feedNames"] as? String,
                                        createdAt = keywordMap["createdAt"] as? Long ?: System.currentTimeMillis()
                                    )
                                    blacklistKeywordDao.insert(newKeyword)
                                }
                            }
                        } catch (e: Exception) {
                            // Skip invalid keyword
                        }
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

    fun loadBackupFolder(context: Context) {
        viewModelScope.launch(ioDispatcher) {
            val folder = PreferencesKey.keys[PreferencesKey.backupFolder]?.key?.let {
                context.dataStore.data.first()[it] as? String
            }
            _backupUiState.update { it.copy(backupFolder = folder) }
        }
    }

    fun saveBackupFolder(context: Context, folderUri: String) {
        viewModelScope.launch(ioDispatcher) {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKey.keys[PreferencesKey.backupFolder]!!.key as Preferences.Key<String>] = folderUri
            }
            _backupUiState.update { it.copy(backupFolder = folderUri) }
        }
    }

    fun performOneClickBackup(context: Context) {
        viewModelScope.launch(ioDispatcher) {
            _backupUiState.update { it.copy(isBackingUp = true, backupSuccess = false, backupError = null) }
            try {
                val backupFolder = _backupUiState.value.backupFolder
                if (backupFolder.isNullOrBlank()) {
                    _backupUiState.update { it.copy(isBackingUp = false, backupError = "Backup folder not set") }
                    return@launch
                }

                val uri = Uri.parse(backupFolder)
                val calendar = java.util.Calendar.getInstance()
                val dateSuffix = "${calendar.get(java.util.Calendar.YEAR)}.${calendar.get(java.util.Calendar.MONTH) + 1}.${calendar.get(java.util.Calendar.DAY_OF_MONTH)}"

                // Helper function to check if file exists and delete it
                suspend fun deleteFileIfExists(fileName: String): Boolean {
                    return try {
                        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                            uri,
                            DocumentsContract.getTreeDocumentId(uri)
                        )
                        
                        // Query for existing files with the same name
                        val projection = arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME
                        )
                        val selection = "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} = ?"
                        val selectionArgs = arrayOf(fileName)
                        
                        val queryResult = context.contentResolver.query(
                            documentUri,
                            projection,
                            selection,
                            selectionArgs,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val documentId = cursor.getString(0)
                                val fileUri = DocumentsContract.buildDocumentUriUsingTree(
                                    uri,
                                    documentId
                                )
                                // Try to delete the existing file
                                try {
                                    context.contentResolver.delete(fileUri, null, null) > 0
                                } catch (deleteError: UnsupportedOperationException) {
                                    // Some SAF providers don't support delete operations
                                    // In this case, we'll proceed anyway as createDocument will overwrite
//                                    Timber.w("Delete not supported for this provider, will overwrite instead")
                                    true
                                }
                            } else {
                                true // File doesn't exist, can proceed
                            }
                        } ?: true // Query failed, assume file doesn't exist
                        
                        queryResult
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }

                // Helper function to create file URI in the selected folder
                fun createFileUri(fileName: String, mimeType: String): Uri? {
                    return try {
                        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                            uri,
                            DocumentsContract.getTreeDocumentId(uri)
                        )
                        val fileUri = DocumentsContract.createDocument(
                            context.contentResolver,
                            documentUri,
                            mimeType,
                            fileName
                        )
                        DocumentsContract.buildDocumentUriUsingTree(
                            uri,
                            DocumentsContract.getDocumentId(fileUri)
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                // Backup subscriptions (OPML with info)
                val opmlContent = opmlService.saveToString(
                    accountService.getCurrentAccountId(),
                    true
                )
                val opmlFileName = "subscriptions_${dateSuffix}.opml"
                
                // Delete existing file if it exists and wait for completion
                if (!deleteFileIfExists(opmlFileName)) {
                    _backupUiState.update { it.copy(isBackingUp = false, backupError = "Failed to delete $opmlFileName") }
                    return@launch
                }
                
                val opmlFileUri = createFileUri(opmlFileName, "text/x-opml")
                if (opmlFileUri != null) {
                    context.contentResolver.openOutputStream(opmlFileUri)?.use { it.write(opmlContent.toByteArray()) }
                        ?: run {
                            _backupUiState.update { it.copy(isBackingUp = false, backupError = "Failed to write $opmlFileName") }
                            return@launch
                        }
                } else {
                    _backupUiState.update { it.copy(isBackingUp = false, backupError = "Failed to create $opmlFileName") }
                    return@launch
                }

                // Backup keywords
                val keywords = blacklistKeywordDao.getAllSync()
                val keywordsExportData = mapOf(
                    "exportedAt" to System.currentTimeMillis(),
                    "keywords" to keywords
                )
                val keywordsJson = Gson().toJson(keywordsExportData)
                val keywordsFileName = "keywords_${dateSuffix}.json"
                
                // Delete existing file if it exists and wait for completion
                if (!deleteFileIfExists(keywordsFileName)) {
                    _backupUiState.update { it.copy(isBackingUp = false, backupError = "Failed to delete $keywordsFileName") }
                    return@launch
                }
                
                val keywordsFileUri = createFileUri(keywordsFileName, "application/json")
                if (keywordsFileUri != null) {
                    context.contentResolver.openOutputStream(keywordsFileUri)?.use { it.write(keywordsJson.toByteArray()) }
                        ?: run {
                            _backupUiState.update { it.copy(isBackingUp = false, backupError = "Failed to write $keywordsFileName") }
                            return@launch
                        }
                } else {
                    _backupUiState.update { it.copy(isBackingUp = false, backupError = "Failed to create $keywordsFileName") }
                    return@launch
                }

                // Backup app preferences
                val preferencesJson = context.fromDataStoreToJSONString()
                val preferencesFileName = "preferences_${dateSuffix}.json"
                
                // Delete existing file if it exists and wait for completion
                if (!deleteFileIfExists(preferencesFileName)) {
                    _backupUiState.update { it.copy(isBackingUp = false, backupError = "Failed to delete $preferencesFileName") }
                    return@launch
                }
                
                val preferencesFileUri = createFileUri(preferencesFileName, "application/json")
                if (preferencesFileUri != null) {
                    context.contentResolver.openOutputStream(preferencesFileUri)?.use { it.write(preferencesJson.toByteArray()) }
                        ?: run {
                            _backupUiState.update { it.copy(isBackingUp = false, backupError = "Failed to write $preferencesFileName") }
                            return@launch
                        }
                } else {
                    _backupUiState.update { it.copy(isBackingUp = false, backupError = "Failed to create $preferencesFileName") }
                    return@launch
                }

                _backupUiState.update { it.copy(isBackingUp = false, backupSuccess = true) }
            } catch (e: Exception) {
                e.printStackTrace()
                _backupUiState.update { it.copy(isBackingUp = false, backupError = e.message ?: "Backup failed") }
            }
        }
    }
}

data class BackupUiState(
    val warningDialogVisible: Boolean = false,
    val exportOPMLModeDialogVisible: Boolean = false,
    val exportOPMLMode: ExportOPMLMode = ExportOPMLMode.ATTACH_INFO,
    val byteArray: ByteArray = ByteArray(0),
    val keywordsWarningDialogVisible: Boolean = false,
    val keywordsByteArray: ByteArray = ByteArray(0),
    val keywordsImportCount: Int = 0,
    val backupFolder: String? = null,
    val isBackingUp: Boolean = false,
    val backupSuccess: Boolean = false,
    val backupError: String? = null,
)

sealed class ExportOPMLMode {
    object ATTACH_INFO : ExportOPMLMode()
    object NO_ATTACH : ExportOPMLMode()
}