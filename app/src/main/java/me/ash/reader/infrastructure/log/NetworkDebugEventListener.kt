package me.ash.reader.infrastructure.log

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Call
import okhttp3.EventListener

class NetworkDebugEventListener(
    private val debugLogger: ImageDownloadDebugLogger,
) : EventListener() {
    private val calls = ConcurrentHashMap<Call, CallInfo>()

    override fun callStart(call: Call) {
        val request = call.request()
        calls[call] =
            CallInfo(
                startedAtNanos = System.nanoTime(),
                url = request.url.toString(),
                method = request.method,
            )
        debugLogger.logAlways {
            "NETWORK START method=${request.method} host=${request.url.host} url=${request.url}"
        }
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        val info = calls[call] ?: return
        info.responseBytes = byteCount
        debugLogger.logAlways {
            "NETWORK BODY_END method=${info.method} host=${call.request().url.host} bytes=$byteCount speedKBps=${formatSpeed(byteCount, info.startedAtNanos)} url=${info.url}"
        }
    }

    override fun callEnd(call: Call) {
        val info = calls.remove(call)
        val elapsedMs = elapsedMillis(info?.startedAtNanos)
        val bytes = info?.responseBytes ?: -1L
        debugLogger.logAlways {
            "NETWORK END method=${call.request().method} host=${call.request().url.host} elapsedMs=$elapsedMs bytes=$bytes speedKBps=${formatSpeed(bytes, info?.startedAtNanos)} url=${call.request().url}"
        }
    }

    override fun callFailed(call: Call, ioe: IOException) {
        val info = calls.remove(call)
        val elapsedMs = elapsedMillis(info?.startedAtNanos)
        val canceled = call.isCanceled() || ioe.message.equals("Canceled", ignoreCase = true)
        debugLogger.logAlways {
            "NETWORK ${if (canceled) "CANCEL" else "FAIL"} method=${call.request().method} host=${call.request().url.host} elapsedMs=$elapsedMs throwable=${ioe::class.java.simpleName}:${ioe.message} url=${call.request().url}"
        }
    }

    private fun elapsedMillis(startedAtNanos: Long?): Long =
        startedAtNanos?.let { (System.nanoTime() - it) / 1_000_000L } ?: -1L

    private fun formatSpeed(bytes: Long, startedAtNanos: Long?): String {
        if (bytes < 0L || startedAtNanos == null) return "unknown"
        val elapsedSeconds = ((System.nanoTime() - startedAtNanos).coerceAtLeast(1L)) / 1_000_000_000.0
        return "%.1f".format(bytes / 1024.0 / elapsedSeconds)
    }

    private data class CallInfo(
        val startedAtNanos: Long,
        val url: String,
        val method: String,
        var responseBytes: Long = -1L,
    )

    class Factory(
        private val debugLogger: ImageDownloadDebugLogger,
    ) : EventListener.Factory {
        override fun create(call: Call): EventListener = NetworkDebugEventListener(debugLogger)
    }
}
