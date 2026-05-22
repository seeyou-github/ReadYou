package me.ash.reader.infrastructure.translate.apistream

import java.io.PrintWriter
import java.io.StringWriter
import okhttp3.Response

class TranslationApiException(
    provider: String,
    response: Response?,
    cause: Throwable?,
    responseBody: String?,
) : Exception(buildMessage(provider, response, cause, responseBody), cause)

private fun buildMessage(
    provider: String,
    response: Response?,
    cause: Throwable?,
    responseBody: String?,
): String {
    val stackTrace =
        cause?.let {
            StringWriter().also { writer ->
                it.printStackTrace(PrintWriter(writer))
            }.toString()
        }.orEmpty()

    return buildString {
        appendLine("$provider Translation Error")
        appendLine("HTTP Code: ${response?.code ?: -1}")
        appendLine("HTTP Message: ${response?.message.orEmpty()}")
        appendLine("URL: ${response?.request?.url ?: "unknown"}")
        appendLine("Exception: ${cause?.javaClass?.name ?: "none"}")
        appendLine("Exception Message: ${cause?.message.orEmpty()}")
        appendLine("Response Body:")
        appendLine(responseBody ?: "<empty>")
        if (stackTrace.isNotBlank()) {
            appendLine("Stack Trace:")
            appendLine(stackTrace)
        }
    }.trim()
}
