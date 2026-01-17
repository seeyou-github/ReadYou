package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.flowArticleListTitleLineHeight
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

// 2026-01-18: 新增文章列表标题行距Preference
val LocalFlowArticleListTitleLineHeight =
    compositionLocalOf { FlowArticleListTitleLineHeightPreference.default }

object FlowArticleListTitleLineHeightPreference {

    // 默认值：1.2倍行距
    const val default = 1.4f

    fun put(context: Context, scope: CoroutineScope, value: Float) {
        scope.launch {
            context.dataStore.put(DataStoreKey.flowArticleListTitleLineHeight, value)
        }
    }

    fun fromPreferences(preferences: Preferences): Float =
        preferences[DataStoreKey.keys[flowArticleListTitleLineHeight]?.key as Preferences.Key<Float>] ?: default
}
