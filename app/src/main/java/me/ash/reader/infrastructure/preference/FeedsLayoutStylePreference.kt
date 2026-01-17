package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.feedsLayoutStyle
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalFeedsLayoutStyle =
    compositionLocalOf<FeedsLayoutStylePreference> { FeedsLayoutStylePreference.default }

sealed class FeedsLayoutStylePreference(val value: Int) : Preference() {
    object List : FeedsLayoutStylePreference(0)
    object Grid : FeedsLayoutStylePreference(1)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKey.feedsLayoutStyle,
                value
            )
        }
    }

    fun toDesc(context: Context): String =
        when (this) {
            List -> context.getString(R.string.list)
            Grid -> context.getString(R.string.grid)
        }

    companion object {
        val default = List
        val values = listOf(List, Grid)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKey.keys[feedsLayoutStyle]?.key as Preferences.Key<Int>]) {
                0 -> List
                1 -> Grid
                else -> default
            }
    }
}