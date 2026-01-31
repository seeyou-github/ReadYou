package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.feedsFilterBarHeight
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalFeedsFilterBarHeight =
    compositionLocalOf { FeedsFilterBarHeightPreference.default }

object FeedsFilterBarHeightPreference {

    const val default = 50

    fun put(context: Context, scope: CoroutineScope, value: Int) {
        scope.launch {
            context.dataStore.put(DataStoreKey.feedsFilterBarHeight, value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        preferences[DataStoreKey.keys["feedsFilterBarHeight"]?.key as Preferences.Key<Int>] ?: default
}