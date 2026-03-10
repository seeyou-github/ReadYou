package me.ash.reader.infrastructure.net

import me.ash.reader.BuildConfig

object UserAgentHolder {

    @Volatile
    private var current: String = BuildConfig.USER_AGENT_STRING

    fun get(): String = current

    fun update(value: String) {
        current = value.ifBlank { BuildConfig.USER_AGENT_STRING }
    }
}
