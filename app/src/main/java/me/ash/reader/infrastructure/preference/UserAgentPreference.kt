package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.BuildConfig
import me.ash.reader.ui.ext.dataStore

object UserAgentPreference {

    const val default: String = BuildConfig.USER_AGENT_STRING
    private val key = stringPreferencesKey("userAgent")

    fun put(context: Context, scope: CoroutineScope, value: String) {
        val normalized = value.trim().ifBlank { default }
        scope.launch {
            context.dataStore.edit { it[key] = normalized }
        }
    }

    fun fromPreferences(preferences: Preferences): String =
        preferences[key] ?: default
}
