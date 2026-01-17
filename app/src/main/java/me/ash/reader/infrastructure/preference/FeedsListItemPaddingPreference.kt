package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

// 2026-01-23: 新增列表视图列表边距的 LocalPreference
// 修改原因：支持用户自定义列表视图的左右边距
val LocalFeedsListItemPadding =
    compositionLocalOf { FeedsListItemPaddingPreference.default }

// 2026-01-23: 新增列表视图列表边距的 Preference 类
// 修改原因：实现列表视图列表边距的设置功能
object FeedsListItemPaddingPreference {
    //默认值：列表布局列表边距
    const val default = 10

    fun put(context: Context, scope: CoroutineScope, value: Int) {
        scope.launch {
            context.dataStore.put("feedsListItemPadding", value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        preferences[me.ash.reader.ui.ext.DataStoreKey.keys["feedsListItemPadding"]?.key as Preferences.Key<Int>] ?: default
}

