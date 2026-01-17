package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

/**
 * 阅读页面标题字体大小设置
 * 2026-01-24: 新增
 * 修改原因：支持用户自定义文章标题的字体大小
 */
val LocalReadingTitleFontSize = compositionLocalOf { ReadingTitleFontSizePreference.default }

object ReadingTitleFontSizePreference {

    const val default = 30  // 默认字体大小

    fun put(context: Context, scope: CoroutineScope, value: Int) {
        scope.launch {
            context.dataStore.put(DataStoreKey.readingTitleFontSize, value)
        }
    }

    fun fromPreferences(preferences: Preferences): Int {
        val key = DataStoreKey.keys[DataStoreKey.readingTitleFontSize]?.key
        return if (key != null) {
            preferences[key as Preferences.Key<Int>] ?: default
        } else {
            default
        }
    }
}