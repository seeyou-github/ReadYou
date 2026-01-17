package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.flowArticleListHorizontalPadding
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

// 2026-01-18: 新增文章列表左右边距Preference
val LocalFlowArticleListHorizontalPadding =
    compositionLocalOf { FlowArticleListHorizontalPaddingPreference.default }

object FlowArticleListHorizontalPaddingPreference {

    // 默认值：12dp
    const val default = 5

    fun put(context: Context, scope: CoroutineScope, value: Int) {
        scope.launch {
            context.dataStore.put(DataStoreKey.flowArticleListHorizontalPadding, value)
        }
    }

    fun fromPreferences(preferences: Preferences): Int =
        preferences[DataStoreKey.keys[flowArticleListHorizontalPadding]?.key as Preferences.Key<Int>] ?: default
}
