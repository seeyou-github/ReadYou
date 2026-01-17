package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.flowArticleListItemSpacing
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

// 2026-01-19: 新增文章列表项间距Preference
val LocalFlowArticleListItemSpacing =
    compositionLocalOf { FlowArticleListItemSpacingPreference.default }

object FlowArticleListItemSpacingPreference {

    // 默认值：0dp
    const val default = 0

    fun put(context: Context, scope: CoroutineScope, value: Int) {
        scope.launch {
            context.dataStore.put(DataStoreKey.flowArticleListItemSpacing, value)
        }
    }

    fun fromPreferences(preferences: Preferences): Int =
        preferences[DataStoreKey.keys[flowArticleListItemSpacing]?.key as Preferences.Key<Int>] ?: default
}