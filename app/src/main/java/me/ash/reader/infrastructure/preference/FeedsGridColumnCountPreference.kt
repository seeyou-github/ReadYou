package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalFeedsGridColumnCount = compositionLocalOf { FeedsGridColumnCountPreference.default }

object FeedsGridColumnCountPreference {
    const val default = 4

    fun put(context: Context, scope: CoroutineScope, value: Int) {
        scope.launch {
            context.dataStore.put("feedsGridColumnCount", value)
        }
    }

    fun fromPreferences(preferences: Preferences): Int =
        preferences[DataStoreKey.keys["feedsGridColumnCount"]?.key as Preferences.Key<Int>] ?: default
}