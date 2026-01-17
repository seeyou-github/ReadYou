package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.flowArticleListRoundedCorners
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

// 2026-01-19: 新增文章列表背景圆角Preference
val LocalFlowArticleListRoundedCorners =
    compositionLocalOf { FlowArticleListRoundedCornersPreference.default }

object FlowArticleListRoundedCornersPreference {

    // 默认值：20dp
    const val default = 0

    fun put(context: Context, scope: CoroutineScope, value: Int) {
        scope.launch {
            context.dataStore.put(DataStoreKey.flowArticleListRoundedCorners, value)
        }
    }

    fun fromPreferences(preferences: Preferences): Int =
        preferences[DataStoreKey.keys[flowArticleListRoundedCorners]?.key as Preferences.Key<Int>] ?: default
}