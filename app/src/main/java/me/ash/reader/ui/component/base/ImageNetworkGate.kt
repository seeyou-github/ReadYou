package me.ash.reader.ui.component.base

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ImageNetworkGate {
    var remoteImagesPaused by mutableStateOf(false)
        private set

    fun pauseRemoteImages() {
        remoteImagesPaused = true
    }

    fun resumeRemoteImages() {
        remoteImagesPaused = false
    }

    fun isRemoteImage(data: Any?): Boolean =
        data is String && (data.startsWith("http://") || data.startsWith("https://"))
}
