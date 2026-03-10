package me.ash.reader.domain.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SyncErrorReport(
    val feedId: String,
    val feedName: String,
    val feedUrl: String,
    val message: String,
    val stackTrace: String? = null,
    val rawContent: String? = null,
    val contentType: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
) {
    fun toDisplayString(): String {
        val sb = StringBuilder()
        val time =
            runCatching {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
            }.getOrDefault(timestamp.toString())

        sb.append("Time: ").append(time).append('\n')
        sb.append("Feed: ").append(feedName).append('\n')
        sb.append("Feed ID: ").append(feedId).append('\n')
        sb.append("URL: ").append(feedUrl).append('\n')
        if (!contentType.isNullOrBlank()) {
            sb.append("Content-Type: ").append(contentType).append('\n')
        }
        sb.append("Error: ").append(message).append('\n')
        if (!stackTrace.isNullOrBlank()) {
            sb.append("\nStacktrace:\n").append(stackTrace).append('\n')
        }
        if (!rawContent.isNullOrBlank()) {
            sb.append("\nRaw content:\n").append(rawContent)
        }
        return sb.toString()
    }
}
