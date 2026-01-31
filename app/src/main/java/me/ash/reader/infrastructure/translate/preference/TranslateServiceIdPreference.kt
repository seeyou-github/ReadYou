package me.ash.reader.infrastructure.translate.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.translate.TranslateProvider
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalTranslateServiceId = compositionLocalOf { TranslateServiceIdPreference.default }

object TranslateServiceIdPreference {

    val default = TranslateProvider.SILICONFLOW.serviceId

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.translateServiceId, value)
        }
    }

    fun fromPreferences(preferences: Preferences): String {
        return preferences[DataStoreKey.keys[DataStoreKey.translateServiceId]?.key as Preferences.Key<String>] ?: default
    }
}