package me.ash.reader.infrastructure.translate.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.translate.model.TranslateProviderConfig
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

/**
 * Cerebras 提供商配置偏好设置
 */
val LocalCerebrasConfig = compositionLocalOf<TranslateProviderConfig?> { null }

object CerebrasConfigPreference {
    val default: TranslateProviderConfig? = null

    fun put(context: Context, scope: CoroutineScope, config: TranslateProviderConfig?) {
        scope.launch {
            val json = config?.let { TranslateProviderConfig.toJson(it) } ?: ""
            context.dataStore.put(DataStoreKey.cerebrasConfig, json)
        }
    }

    fun fromPreferences(preferences: Preferences): TranslateProviderConfig? {
        val json = preferences[DataStoreKey.keys[DataStoreKey.cerebrasConfig]?.key as Preferences.Key<String>]
        return if (json.isNullOrBlank()) {
            null
        } else {
            TranslateProviderConfig.fromJson(json)
        }
    }
}
