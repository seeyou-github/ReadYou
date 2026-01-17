package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.ash.reader.domain.model.theme.ColorTheme
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

// 2026-01-18: 新增文章列表颜色主题Preference
val LocalFlowArticleListColorThemes =
    compositionLocalOf { FlowArticleListColorThemesPreference.default }

object FlowArticleListColorThemesPreference {

    val KEY = stringPreferencesKey("flowArticleListColorThemes")

    // 默认值：
    val default = listOf(
        ColorTheme(
            name = "暗黑",
            textColor = androidx.compose.ui.graphics.Color(0xFFC9C9C9),
            backgroundColor = androidx.compose.ui.graphics.Color(0xFF2D2D2D),
            primaryColor = androidx.compose.ui.graphics.Color(0xFFFF9800),
            isDefault = true,
            isDarkTheme = true
        ),
        ColorTheme(
            name = "浅色",
            textColor = androidx.compose.ui.graphics.Color(0xFF000000),
            backgroundColor = androidx.compose.ui.graphics.Color(0xFFA1948B),
            primaryColor = androidx.compose.ui.graphics.Color(0xFF4CAF50),
            isDefault = true,
            isDarkTheme = false
        )
    )

    fun put(context: Context, scope: CoroutineScope, themes: List<ColorTheme>) {
        scope.launch {
            val jsonString = Json.encodeToString(themes)
            context.dataStore.put(KEY.name, jsonString)
        }
    }

    fun fromPreferences(preferences: Preferences): List<ColorTheme> {
        val jsonString = preferences[me.ash.reader.ui.ext.DataStoreKey.keys[me.ash.reader.ui.ext.DataStoreKey.flowArticleListColorThemes]?.key as Preferences.Key<String>]
        return if (jsonString.isNullOrEmpty()) {
            default
        } else {
            try {
                Json.decodeFromString(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
                default
            }
        }
    }
}
