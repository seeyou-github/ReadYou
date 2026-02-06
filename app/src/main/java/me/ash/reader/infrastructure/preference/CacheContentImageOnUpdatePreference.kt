package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.cacheContentImageOnUpdate
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalCacheContentImageOnUpdate =
    compositionLocalOf<CacheContentImageOnUpdatePreference> { CacheContentImageOnUpdatePreference.default }

sealed class CacheContentImageOnUpdatePreference(val value: Boolean) : Preference() {
    data object On : CacheContentImageOnUpdatePreference(true)
    data object Off : CacheContentImageOnUpdatePreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(cacheContentImageOnUpdate, value)
        }
    }

    fun toggle(context: Context, scope: CoroutineScope) = scope.launch {
        context.dataStore.put(cacheContentImageOnUpdate, !value)
    }

    companion object {
        val default = Off
        val values = listOf(On, Off)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKey.keys[cacheContentImageOnUpdate]?.key as Preferences.Key<Boolean>]) {
                true -> On
                false -> Off
                else -> default
            }
    }
}
