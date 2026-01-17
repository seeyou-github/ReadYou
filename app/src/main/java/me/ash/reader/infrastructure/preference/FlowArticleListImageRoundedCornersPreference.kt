package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.flowArticleListImageRoundedCorners
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

// 2026-01-18: 新增文章列表图片圆角Preference
val LocalFlowArticleListImageRoundedCorners =
    compositionLocalOf { FlowArticleListImageRoundedCornersPreference.default }

object FlowArticleListImageRoundedCornersPreference {

    // 默认值：20dp
    const val default = 10

    fun put(context: Context, scope: CoroutineScope, value: Int) {
        scope.launch {
            context.dataStore.put(DataStoreKey.flowArticleListImageRoundedCorners, value)
        }
    }

    fun fromPreferences(preferences: Preferences): Int =
        preferences[DataStoreKey.keys[flowArticleListImageRoundedCorners]?.key as Preferences.Key<Int>] ?: default
}
