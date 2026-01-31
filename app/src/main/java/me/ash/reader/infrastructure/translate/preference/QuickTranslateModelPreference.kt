package me.ash.reader.infrastructure.translate.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.translate.model.TranslateModelConfig
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

/**
 * 快速翻译模型偏好设置
 */
val LocalQuickTranslateModel = compositionLocalOf<TranslateModelConfig?> { null }

object QuickTranslateModelPreference {

    val default: TranslateModelConfig? = null

    fun put(context: Context, scope: CoroutineScope, config: TranslateModelConfig?) {
        scope.launch {
            val json = config?.let { TranslateModelConfig.toJson(it) } ?: ""
            context.dataStore.put(DataStoreKey.quickTranslateModel, json)
        }
    }

    fun fromPreferences(preferences: Preferences): TranslateModelConfig? {
        val json = preferences[DataStoreKey.keys[DataStoreKey.quickTranslateModel]?.key as Preferences.Key<String>]
        return if (json.isNullOrBlank()) {
            null
        } else {
            TranslateModelConfig.fromJson(json)
        }
    }
}
