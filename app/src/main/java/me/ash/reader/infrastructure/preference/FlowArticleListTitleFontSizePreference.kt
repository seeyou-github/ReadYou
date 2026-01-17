package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.flowArticleListTitleFontSize
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

// 2026-01-18: 新增文章列表标题文字大小Preference
val LocalFlowArticleListTitleFontSize =
    compositionLocalOf { FlowArticleListTitleFontSizePreference.default }

object FlowArticleListTitleFontSizePreference {

    // 默认值：16sp
    const val default = 21

    fun put(context: Context, scope: CoroutineScope, value: Int) {
        scope.launch {
            context.dataStore.put(DataStoreKey.flowArticleListTitleFontSize, value)
        }
    }

    fun fromPreferences(preferences: Preferences): Int =
        preferences[DataStoreKey.keys[flowArticleListTitleFontSize]?.key as Preferences.Key<Int>] ?: default
}
