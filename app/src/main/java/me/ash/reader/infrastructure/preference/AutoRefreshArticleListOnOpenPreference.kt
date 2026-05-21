package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.autoRefreshArticleListOnOpen
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalAutoRefreshArticleListOnOpen =
    compositionLocalOf<AutoRefreshArticleListOnOpenPreference> {
        AutoRefreshArticleListOnOpenPreference.default
    }

sealed class AutoRefreshArticleListOnOpenPreference(val value: Boolean) : Preference() {
    data object On : AutoRefreshArticleListOnOpenPreference(true)
    data object Off : AutoRefreshArticleListOnOpenPreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(autoRefreshArticleListOnOpen, value)
        }
    }

    fun toggle(context: Context, scope: CoroutineScope) = scope.launch {
        context.dataStore.put(autoRefreshArticleListOnOpen, !value)
    }

    companion object {
        val default = Off

        fun fromPreferences(preferences: Preferences) =
            when (
                preferences[
                    DataStoreKey.keys[autoRefreshArticleListOnOpen]?.key
                        as Preferences.Key<Boolean>
                ]
            ) {
                true -> On
                false -> Off
                else -> default
            }
    }
}
