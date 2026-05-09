package me.ash.reader.ui.page.settings.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import me.ash.reader.domain.repository.BlacklistKeywordDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.plugin.PluginRuleDao
import me.ash.reader.ui.ext.fromDataStoreToJSONString

@Singleton
class OneClickBackupService
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val accountService: AccountService,
    private val blacklistKeywordDao: BlacklistKeywordDao,
    private val groupDao: GroupDao,
    private val feedDao: FeedDao,
    private val pluginRuleDao: PluginRuleDao,
) {

    suspend fun createBackupJson(): String {
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
        return Gson().toJson(payload)
    }

    suspend fun writeBackup(outputUri: Uri) {
        writeBytes(outputUri, createBackupJson().toByteArray())
    }

    suspend fun writeAutoBackup(treeUri: Uri) {
        val fileUri = createOrReplaceAutoBackupFile(treeUri)
        writeBytes(fileUri, createBackupJson().toByteArray())
    }

    fun testDirectoryReadWrite(treeUri: Uri) {
        val testFileUri =
            DocumentsContract.createDocument(
                context.contentResolver,
                treeUri.asTreeDocumentUri(),
                "text/plain",
                ".read_you_auto_backup_test",
            ) ?: error("Failed to create test file")
        try {
            writeBytes(testFileUri, "ok".toByteArray())
            val value =
                context.contentResolver.openInputStream(testFileUri)?.use {
                    it.readBytes().decodeToString()
                }
            check(value == "ok") { "Failed to read test file" }
        } finally {
            runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, testFileUri)
            }
        }
    }

    private fun createOrReplaceAutoBackupFile(treeUri: Uri): Uri {
        val directoryUri = treeUri.asTreeDocumentUri()
        val existing = findAutoBackupFile(directoryUri)
        if (existing != null) {
            return existing
        }
        return DocumentsContract.createDocument(
            context.contentResolver,
            directoryUri,
            "application/json",
            AUTO_BACKUP_FILE_NAME,
        ) ?: error("Failed to create auto backup file")
    }

    private fun findAutoBackupFile(directoryUri: Uri): Uri? {
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                directoryUri,
                DocumentsContract.getDocumentId(directoryUri),
            )
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn =
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == AUTO_BACKUP_FILE_NAME) {
                    return DocumentsContract.buildDocumentUriUsingTree(
                        directoryUri,
                        cursor.getString(idColumn),
                    )
                }
            }
        }
        return null
    }

    private fun writeBytes(uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
            outputStream.write(bytes)
        } ?: error("Failed to write backup")
    }

    private fun Uri.asTreeDocumentUri(): Uri =
        DocumentsContract.buildDocumentUriUsingTree(
            this,
            DocumentsContract.getTreeDocumentId(this),
        )

    companion object {
        const val AUTO_BACKUP_FILE_NAME = "Auto_backup_ReadYou_Rss.json"
    }
}
