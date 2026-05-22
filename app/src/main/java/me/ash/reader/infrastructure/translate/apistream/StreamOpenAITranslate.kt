package me.ash.reader.infrastructure.translate.apistream

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.ash.reader.infrastructure.di.UserAgentInterceptor
import me.ash.reader.infrastructure.translate.apistream.TranslatePrompt.buildMarked
import me.ash.reader.infrastructure.translate.apistream.TranslatePrompt.hasEnglishChars
import me.ash.reader.infrastructure.translate.cache.TranslateCache
import me.ash.reader.infrastructure.translate.model.TranslateModelConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 通用 OpenAI 兼容协议流式翻译服务。
 *
 * 适用于 OpenAI、SiliconFlow、Cerebras 以及任意提供 `POST {baseUrl}{chatPath}` 的供应商。
 */
@Singleton
class StreamOpenAITranslate @Inject constructor(
    private val translateCache: TranslateCache,
) : StreamTranslateService {

    companion object {
        private const val TAG = "StreamOpenAI"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val gson = Gson()

        private val client by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addNetworkInterceptor(UserAgentInterceptor)
                .build()
        }
        private val eventSourceFactory by lazy { EventSources.createFactory(client) }
    }

    private var eventSource: EventSource? = null
    @Volatile private var isCancelled = false

    override fun getServiceName(): String = "OpenAI"
    override fun getServiceId(): String = "openai"

    private fun requireKey(config: TranslateModelConfig): String {
        val apiKey = config.apiKey
        if (apiKey.isBlank()) throw IllegalArgumentException("API Key 未配置")
        return apiKey
    }

    private fun requireModel(config: TranslateModelConfig): String {
        val model = config.model
        if (model.isBlank()) throw IllegalArgumentException("翻译模型未配置")
        return model
    }

    private fun buildEndpoint(config: TranslateModelConfig): String {
        val baseRaw = config.baseUrl.trimEnd('/')
        val base = if (baseRaw.isNotBlank()) baseRaw else when (config.provider.lowercase()) {
            "siliconflow" -> "https://api.siliconflow.cn/v1"
            "cerebras" -> "https://api.cerebras.ai/v1"
            else -> "https://api.openai.com/v1"
        }
        val path = config.chatPath.ifBlank { "/chat/completions" }
            .let { if (it.startsWith("/")) it else "/$it" }
        return base + path
    }

    override suspend fun translateStream(
        title: String?,
        texts: List<String>,
        config: TranslateModelConfig,
        onNodeCompleted: (nodeId: Int, translatedText: String) -> Unit,
        onProgress: (completed: Int, total: Int) -> Unit,
        onError: (error: Throwable) -> Unit,
    ): List<String> = withContext(Dispatchers.IO) {
        isCancelled = false
        if (texts.isEmpty()) return@withContext emptyList()

        val validTexts = texts.filter { it.isNotBlank() && hasEnglishChars(it) }
        if (validTexts.isEmpty()) return@withContext texts.map { it }

        val (markedTexts, mergedText, _) = buildMarked(title, texts)
        if (markedTexts.isEmpty()) return@withContext texts.map { it }

        val markedTextsIndexMap = mutableMapOf<Int, Int>()
        markedTexts.forEachIndexed { index, text ->
            Regex("""##\[(-?\d+)\]##""").find(text)?.let {
                markedTextsIndexMap[it.groupValues[1].toInt()] = index
            }
        }

        var completedCount = 0
        val parser = NodeStreamParser { id, translated ->
            val markedIdx = markedTextsIndexMap[id] ?: -1
            val cacheKey = if (markedIdx >= 0) markedTexts[markedIdx].take(100) else "node_$id"
            translateCache.put(cacheKey, translated)
            completedCount++
            onNodeCompleted(id, translated)
            onProgress(completedCount, markedTexts.size)
        }

        val messages = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("role", "system"); addProperty("content", TranslatePrompt.SYSTEM)
            })
            add(JsonObject().apply {
                addProperty("role", "user"); addProperty("content", mergedText)
            })
        }
        val body = JsonObject().apply {
            addProperty("model", requireModel(config))
            add("messages", messages)
            addProperty("max_tokens", 8000)
            addProperty("stream", true)
        }

        val request = Request.Builder()
            .url(buildEndpoint(config))
            .addHeader("Authorization", "Bearer ${requireKey(config)}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        Timber.d("[$TAG] POST ${buildEndpoint(config)} model=${config.model}")

        var resumed = false
        suspendCancellableCoroutine<Unit> { continuation ->
            val listener = object : EventSourceListener() {
                override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                    if (isCancelled) { es.cancel(); return }
                    if (data.trim() == "[DONE]") {
                        parser.finish()
                        es.cancel()
                        if (!resumed) { resumed = true; continuation.resume(Unit) }
                        return
                    }
                    try {
                        val json = gson.fromJson(data, JsonObject::class.java) ?: return
                        if (json.has("error")) {
                            val error = TranslationApiException(
                                provider = "OpenAI-compatible SSE",
                                response = null,
                                cause = IllegalStateException("SSE error event"),
                                responseBody = data,
                            )
                            es.cancel()
                            if (!resumed) { resumed = true; continuation.resumeWithException(error) }
                            return
                        }
                        val choices = json.getAsJsonArray("choices") ?: return
                        if (choices.size() == 0) return
                        val delta = choices.get(0).asJsonObject.getAsJsonObject("delta")
                        val content = delta?.get("content")?.asString
                        if (!content.isNullOrEmpty()) parser.feed(content)
                    } catch (e: Exception) {
                        Timber.w("[$TAG] chunk parse failed: ${e.message}")
                    }
                }

                override fun onFailure(es: EventSource, t: Throwable?, response: okhttp3.Response?) {
                    if (!isCancelled && !resumed) {
                        val errBody = try { response?.body?.string() } catch (_: Exception) { null }
                        val error = TranslationApiException(
                            provider = "OpenAI-compatible",
                            response = response,
                            cause = t,
                            responseBody = errBody,
                        )
                        Timber.e(error, "[$TAG] Translation API failure\n${error.message}")
                        resumed = true
                        continuation.resumeWithException(error)
                    } else if (!resumed) {
                        resumed = true
                        continuation.resume(Unit)
                    }
                }

                override fun onClosed(es: EventSource) {
                    parser.finish()
                    if (!resumed) { resumed = true; continuation.resume(Unit) }
                }
            }
            eventSource = eventSourceFactory.newEventSource(request, listener)
            continuation.invokeOnCancellation {
                isCancelled = true
                eventSource?.cancel()
            }
        }

        val results = mutableListOf<String>()
        texts.forEachIndexed { index, text ->
            if (!hasEnglishChars(text)) {
                results.add(text)
            } else {
                results.add(parser.results[index] ?: text)
            }
        }
        results
    }

    override fun getCacheStats(): TranslateCache.CacheStats = translateCache.getStats()
    override fun clearCache() { translateCache.clear() }
    override fun cancel() {
        isCancelled = true
        eventSource?.cancel()
    }
}
