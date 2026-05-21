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
 * Anthropic Claude Messages 流式翻译服务。
 *
 * POST {baseUrl}/messages
 * Headers: x-api-key, anthropic-version
 */
@Singleton
class StreamClaudeTranslate @Inject constructor(
    private val translateCache: TranslateCache,
) : StreamTranslateService {

    companion object {
        private const val TAG = "StreamClaude"
        private const val ANTHROPIC_VERSION = "2023-06-01"
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

    override fun getServiceName(): String = "Claude"
    override fun getServiceId(): String = "claude"

    private fun buildEndpoint(config: TranslateModelConfig): String {
        val base = config.baseUrl.trimEnd('/').ifBlank { "https://api.anthropic.com/v1" }
        return "$base/messages"
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
        if (config.apiKey.isBlank()) throw IllegalArgumentException("Claude API Key 未配置")
        if (config.model.isBlank()) throw IllegalArgumentException("Claude 模型未配置")

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

        val body = JsonObject().apply {
            addProperty("model", config.model)
            addProperty("max_tokens", 8000)
            addProperty("stream", true)
            addProperty("system", TranslatePrompt.SYSTEM)
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", mergedText)
                })
            })
        }

        val request = Request.Builder()
            .url(buildEndpoint(config))
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
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
                    if (type == "message_stop" || data.trim() == "[DONE]") {
                        parser.finish()
                        es.cancel()
                        if (!resumed) { resumed = true; continuation.resume(Unit) }
                        return
                    }
                    // 关心 content_block_delta 事件
                    if (type != null && type != "content_block_delta") return
                    try {
                        val json = gson.fromJson(data, JsonObject::class.java) ?: return
                        // 若 type 字段在 data 内，过滤同样适用
                        val deltaType = json.get("type")?.asString
                        if (deltaType != null && deltaType != "content_block_delta") return
                        val delta = json.getAsJsonObject("delta") ?: return
                        val deltaKind = delta.get("type")?.asString
                        if (deltaKind != null && deltaKind != "text_delta") return
                        val text = delta.get("text")?.asString
                        if (!text.isNullOrEmpty()) parser.feed(text)
                    } catch (e: Exception) {
                        Timber.w("[$TAG] chunk parse failed: ${e.message}")
                    }
                }

                override fun onFailure(es: EventSource, t: Throwable?, response: okhttp3.Response?) {
                    if (!isCancelled && !resumed) {
                        val code = response?.code ?: -1
                        val errBody = try { response?.body?.string() } catch (_: Exception) { null }
                        val msg = "Claude SSE 失败 (HTTP $code): ${t?.message ?: response?.message}"
                        Timber.e("[$TAG] $msg body=$errBody")
                        resumed = true
                        continuation.resumeWithException(Exception(msg))
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
            if (!hasEnglishChars(text)) results.add(text)
            else results.add(parser.results[index] ?: text)
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
