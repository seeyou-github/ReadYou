package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.flowFilterBarAutoHide
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

// 2026-01-21: 新增过滤栏自动隐藏功能 Preference
val LocalFlowFilterBarAutoHide =
    compositionLocalOf<FlowFilterBarAutoHidePreference> { FlowFilterBarAutoHidePreference.default }

sealed class FlowFilterBarAutoHidePreference(val value: Boolean) : Preference() {
    object ON : FlowFilterBarAutoHidePreference(true)
    object OFF : FlowFilterBarAutoHidePreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKey.flowFilterBarAutoHide,
                value
            )
        }
    }

    companion object {
        //默认值：上划隐藏过滤栏
        val default = ON
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKey.keys[flowFilterBarAutoHide]?.key as Preferences.Key<Boolean>]) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun FlowFilterBarAutoHidePreference.not(): FlowFilterBarAutoHidePreference =
    when (value) {
        true -> FlowFilterBarAutoHidePreference.OFF
        false -> FlowFilterBarAutoHidePreference.ON
    }