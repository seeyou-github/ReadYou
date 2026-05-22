package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.translateDebugLog
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

sealed class TranslateDebugLogPreference(val value: Boolean) : Preference() {
    data object On : TranslateDebugLogPreference(true)
    data object Off : TranslateDebugLogPreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(translateDebugLog, value)
        }
    }

    fun toggle(context: Context, scope: CoroutineScope) = scope.launch {
        context.dataStore.put(translateDebugLog, !value)
    }

    companion object {
        val default = Off

        @Suppress("UNCHECKED_CAST")
        fun fromPreferences(preferences: Preferences) =
            when (
                preferences[
                    DataStoreKey.keys[translateDebugLog]?.key as Preferences.Key<Boolean>
                ]
            ) {
                true -> On
                false -> Off
                else -> default
            }
    }
}
