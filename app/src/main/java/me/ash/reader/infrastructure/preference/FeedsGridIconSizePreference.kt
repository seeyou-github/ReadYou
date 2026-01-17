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

val LocalFeedsGridIconSize = compositionLocalOf { FeedsGridIconSizePreference.default }

object FeedsGridIconSizePreference {
    const val default = 56

    fun put(context: Context, scope: CoroutineScope, value: Int) {
        scope.launch {
            context.dataStore.put("feedsGridIconSize", value)
        }
    }

    fun fromPreferences(preferences: Preferences): Int =
        preferences[DataStoreKey.keys["feedsGridIconSize"]?.key as Preferences.Key<Int>] ?: default
}