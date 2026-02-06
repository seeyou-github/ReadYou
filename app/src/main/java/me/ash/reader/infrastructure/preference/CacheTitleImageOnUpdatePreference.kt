package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.cacheTitleImageOnUpdate
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalCacheTitleImageOnUpdate =
    compositionLocalOf<CacheTitleImageOnUpdatePreference> { CacheTitleImageOnUpdatePreference.default }

sealed class CacheTitleImageOnUpdatePreference(val value: Boolean) : Preference() {
    data object On : CacheTitleImageOnUpdatePreference(true)
    data object Off : CacheTitleImageOnUpdatePreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(cacheTitleImageOnUpdate, value)
        }
    }

    fun toggle(context: Context, scope: CoroutineScope) = scope.launch {
        context.dataStore.put(cacheTitleImageOnUpdate, !value)
    }

    companion object {
        val default = Off
        val values = listOf(On, Off)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKey.keys[cacheTitleImageOnUpdate]?.key as Preferences.Key<Boolean>]) {
                true -> On
                false -> Off
                else -> default
            }
    }
}
