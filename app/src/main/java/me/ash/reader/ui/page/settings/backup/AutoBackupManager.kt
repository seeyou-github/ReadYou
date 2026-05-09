package me.ash.reader.ui.page.settings.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.InvalidationTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.db.AndroidDatabase
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.dataStore
import timber.log.Timber

@OptIn(FlowPreview::class)
@Singleton
class AutoBackupManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: AndroidDatabase,
    private val oneClickBackupService: OneClickBackupService,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IODispatcher private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher,
) {
    private var startJob: Job? = null
    private val databaseChanges =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    fun start() {
        if (startJob != null) return
        startJob =
            applicationScope.launch(ioDispatcher) {
                database.invalidationTracker.addObserver(tableObserver)
                merge(
                    context.dataStore.data
                        .map { preferences ->
                            preferences.asMap()
                                .filterKeys { key ->
                                    key.name != DataStoreKey.autoBackupDirectoryUri
                                }
                        }
                        .distinctUntilChanged()
                        .drop(1)
                        .map { },
                    databaseChanges,
                )
                    .debounce(AUTO_BACKUP_DEBOUNCE_MS)
                    .collect {
                        runAutoBackup()
                    }
            }
    }

    private suspend fun runAutoBackup() {
        val uriString =
            context.dataStore.data.first()[stringPreferencesKey(DataStoreKey.autoBackupDirectoryUri)]
                ?: return
        try {
            oneClickBackupService.writeAutoBackup(Uri.parse(uriString))
        } catch (e: Exception) {
            Timber.w(e, "Auto backup failed")
        }
    }

    private val tableObserver =
        object : InvalidationTracker.Observer(
            "account",
            "feed",
            "group",
            "blacklist_keyword",
            "plugin_rule",
        ) {
            override fun onInvalidated(tables: Set<String>) {
                databaseChanges.tryEmit(Unit)
            }
        }

    companion object {
        private const val AUTO_BACKUP_DEBOUNCE_MS = 1_500L
    }
}
