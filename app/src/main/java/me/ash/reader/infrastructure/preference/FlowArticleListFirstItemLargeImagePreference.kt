package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.flowArticleListFirstItemLargeImage
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalFlowArticleListFirstItemLargeImage =
    compositionLocalOf<FlowArticleListFirstItemLargeImagePreference> { FlowArticleListFirstItemLargeImagePreference.default }

sealed class FlowArticleListFirstItemLargeImagePreference(val value: Boolean) : Preference() {
    object ON : FlowArticleListFirstItemLargeImagePreference(true)
    object OFF : FlowArticleListFirstItemLargeImagePreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKey.flowArticleListFirstItemLargeImage,
                value
            )
        }
    }

    companion object {

        //默认值：首行大图模式开启
        val default = ON
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKey.keys[flowArticleListFirstItemLargeImage]?.key as Preferences.Key<Boolean>]) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun FlowArticleListFirstItemLargeImagePreference.not(): FlowArticleListFirstItemLargeImagePreference =
    when (value) {
        true -> FlowArticleListFirstItemLargeImagePreference.OFF
        false -> FlowArticleListFirstItemLargeImagePreference.ON
    }