package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.log.ImageDownloadDebugLogger
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.imageDownloadDebugLog
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

sealed class ImageDownloadDebugLogPreference(val value: Boolean) : Preference() {
    data object On : ImageDownloadDebugLogPreference(true)
    data object Off : ImageDownloadDebugLogPreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(imageDownloadDebugLog, value)
            if (!value) {
                ImageDownloadDebugLogger.deleteLogFile(context)
            }
        }
    }

    fun toggle(context: Context, scope: CoroutineScope) = scope.launch {
        val newValue = !value
        context.dataStore.put(imageDownloadDebugLog, newValue)
        if (!newValue) {
            ImageDownloadDebugLogger.deleteLogFile(context)
        }
    }

    companion object {
        val default = Off

        fun fromPreferences(preferences: Preferences) =
            when (
                preferences[
                    DataStoreKey.keys[imageDownloadDebugLog]?.key as Preferences.Key<Boolean>
                ]
            ) {
                true -> On
                false -> Off
                else -> default
            }
    }
}
