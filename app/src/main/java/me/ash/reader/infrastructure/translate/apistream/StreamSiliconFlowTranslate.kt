package me.ash.reader.infrastructure.translate.apistream

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
 * SiliconFlow 流式翻译服务
 *
 * 使用 OkHttp SSE 实现真正的流式翻译：
 * - 单次请求：标题 + 所有文本节点
 * - SSE流式：按行读取，实时累积
 * - 节点级别更新：每个节点翻译完成后立即更新
 * - 支持缓存、中断、think过滤
 *
 * 创建日期：2026-01-31
 * 更新日期：2026-01-31 (升级为SSE流式，修复崩溃)
 */
@Singleton
class StreamSiliconFlowTranslate @Inject constructor(
    private val translateCache: TranslateCache
) : StreamTranslateService {
    companion object {
        private const val TAG = "StreamSiliconFlow"
        private const val API_URL = "https://api.siliconflow.cn/v1/chat/completions"
        private const val SYSTEM_PROMPT =
            """你是一个专业翻译助手，负责将英文翻译为中文。
重要规则：
1. 必须严格按照输入的段落分隔符（\n\n---\n\n）来划分翻译结果
2. 输入有多少个段落，输出就必须有多少个段落
3. 每个段落必须单独翻译，不能合并多个段落
4. 非英文字符不做处理，保持原样输出
5. 【重要】不要翻译或修改 ##[-1]##、##[0]##、##[1]##、##[2]## 这类节点标记，输出时必须保留此类标记
6. 只输出翻译结果，不要有任何解释或思考过程"""
        private const val SERVICE_ID = "siliconflow"
        private const val SERVICE_NAME = "SiliconFlow"

        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val gson = Gson()

        // SSE专用客户端：readTimeout设为0以支持长流式连接
        private val client by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)  // 流式必须设为0，防止超时断开
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }

        // 用于创建EventSource的工厂
        private val eventSourceFactory by lazy {
            EventSources.createFactory(client)
        }

        fun hasEnglishChars(text: String): Boolean {
            return text.any { it in 'a'..'z' || it in 'A'..'Z' }
        }

        // 节点标记解析状态机
        private enum class ParsingState {
            NONE,               // 初始状态/内容累积中
            EXPECT_HASH1,       // 期望第一个 #
            EXPECT_HASH2,       // 期望第二个 #
            PARSING_ID,         // 解析节点 ID
            EXPECT_HASH_END1,   // 期望第一个结束 #
            EXPECT_HASH_END2,   // 期望第二个结束 #
            PARSING_CONTENT     // 解析节点内容
        }
    }

    private var eventSource: EventSource? = null
    @Volatile
    private var isCancelled = false

    override fun getServiceName(): String = SERVICE_NAME

    override fun getServiceId(): String = SERVICE_ID

    private fun getApiKey(config: TranslateModelConfig?): String {
        val apiKey = config?.apiKey
        if (apiKey.isNullOrBlank()) {
            throw IllegalArgumentException(
                "API Key 未配置，请在设置中配置 SiliconFlow API Key"
            )
        }
        return apiKey
    }

    private fun getModel(config: TranslateModelConfig?): String {
        val model = config?.model
        if (model.isNullOrBlank()) {
            throw IllegalArgumentException(
                "翻译模型未配置，请在设置中选择或配置 SiliconFlow 模型"
            )
        }
        return model
    }

    override suspend fun translateStream(
        title: String?,
        texts: List<String>,
        config: TranslateModelConfig,
        onNodeCompleted: (nodeId: Int, translatedText: String) -> Unit,
        onProgress: (completed: Int, total: Int) -> Unit,
        onError: (error: Throwable) -> Unit
    ): List<String> = withContext(Dispatchers.IO) {
        isCancelled = false
        Timber.d("[$TAG] ========== 开始SSE流式翻译 ==========")
        Timber.d("[$TAG] 标题: ${title?.take(50) ?: "null"}")
        Timber.d("[$TAG] 文本节点数: ${texts.size}")


        if (texts.isEmpty()) {
            Timber.w("[$TAG] 输入文本列表为空")
            return@withContext emptyList()
        }
        Timber.d("[$TAG] 文本: ${texts}")
        val validTexts = texts.filter { it.isNotBlank() && hasEnglishChars(it) }
        if (validTexts.isEmpty()) {
            Timber.w("[$TAG] 所有文本都不包含英文字符")
            return@withContext texts.map { it }
        }

        Timber.d("[$TAG] 有效文本数: ${validTexts.size}")

        val markedTexts = mutableListOf<String>()

        if (title != null && hasEnglishChars(title)) {
            markedTexts.add("##[-1]## $title")
        }

        texts.forEachIndexed { index, text ->
            if (hasEnglishChars(text)) {
                markedTexts.add("##[$index]## $text")
            }
        }

        Timber.d("[$TAG] 标记文本数: ${markedTexts.size}")
        Timber.d("[$TAG] 标记文本数 内容: ${markedTexts}")

        // 创建映射表：节点ID -> markedTexts索引
        val markedTextsIndexMap = mutableMapOf<Int, Int>()
        markedTexts.forEachIndexed { index, text ->
            val nodeMatch = Regex("""##\[(-?\d+)\]##""").find(text)
            if (nodeMatch != null) {
                val nodeId = nodeMatch.groupValues[1].toInt()
                markedTextsIndexMap[nodeId] = index
//                Timber.d("[$TAG] 映射: 节点ID=$nodeId -> markedTexts索引=$index")
            }
        }

        // 打印提取的节点文本
        markedTexts.forEachIndexed { index, text ->
//            Timber.d("[$TAG] 提取节点 #$index: 原文=\"${text.take(50)}...\"")
        }

        val mergedText = markedTexts.joinToString("\n")
        Timber.d("[$TAG] 合并文本长度: ${mergedText.length}")
//        Timber.d("[$TAG] 合并文本预览: ${mergedText}")

        // 构建请求体，添加 stream: true
        val messagesArray = JsonArray()
        messagesArray.add(JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", SYSTEM_PROMPT)
        })
        messagesArray.add(JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", mergedText)
        })

        val requestBody = JsonObject().apply {
            addProperty("model", getModel(config))
            add("messages", messagesArray)
            addProperty("max_tokens", 8000)
            addProperty("stream", true)  // 开启流式传输
        }

        Timber.d("[$TAG] 请求体: ${requestBody.toString()}...")

        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer ${getApiKey(config)}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        Timber.d("[$TAG] ✓ 发送SSE流式请求到 API_URL")
        Timber.d("[$TAG] 等待 API 响应...")

        // 状态管理
        var currentNodeId: Int? = null
        val currentNodeBuffer = StringBuilder()
        val idBuffer = StringBuilder()  // 用于解析节点 ID
        var parsingState = ParsingState.NONE  // 节点标记解析状态
        val completedNodes = mutableSetOf<Int>()
        var completedCount = 0
        var isResumed = false  // 防止重复 resume
        val allSegments = mutableMapOf<Int, String>()  // 存储所有节点的翻译结果

        suspendCancellableCoroutine<Unit> { continuation ->
            val listener = object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                    Timber.d("[$TAG] SSE 连接已建立")
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
//                    Timber.d("[$TAG] =============onEvent==============")

                    if (isCancelled) {
                        Timber.d("[$TAG] 检测到取消标志")
                        eventSource.cancel()
                        return
                    }

                    // 检查是否结束
                    if (data.trim() == "[DONE]") {
                        Timber.d("[$TAG] ✓ SSE 流结束，所有节点翻译完成  data.trim() == [DONE]")

                        // 完成最后一个节点
                        val idToComplete = currentNodeId
                        if (idToComplete != null && !completedNodes.contains(idToComplete)) {
                            val completedText = currentNodeBuffer.toString().trim()
                            if (completedText.isNotBlank()) {
                                Timber.d("[$TAG] 完成最后的节点 #$idToComplete (流结束时)")
                                Timber.d("[$TAG] 节点[$idToComplete] 译文: \"$completedText\"")

                                // 缓存结果
                                val markedTextsIndex = markedTextsIndexMap[idToComplete] ?: -1
                                val cacheKey = if (markedTextsIndex >= 0) markedTexts[markedTextsIndex].take(100) else "node_${idToComplete}"
                                translateCache.put(cacheKey, completedText)

                                completedNodes.add(idToComplete)
                                completedCount++

                                // 存储到结果映射
                                allSegments[idToComplete] = completedText

                                // 实时更新
                                onNodeCompleted(idToComplete, completedText)
                                Timber.d("[$TAG] ========== 节点[$idToComplete] 最后节点 completedText = {$completedText}  已发送 onNodeCompleted ==========")

                                onProgress(completedCount, markedTexts.size)
                            }
                        }

                        eventSource.cancel()
                        try {
                            continuation.resume(Unit)
                            isResumed = true
                        } catch (e: IllegalStateException) {
                            Timber.d("[$TAG] 忽略重复 resume: ${e.message}")
                        }
                        return
                    }

                    // 记录接收到的数据
//                    if (data.isNotBlank()) {
//                        Timber.d("[$TAG] ← 接收数据: ${data}...")
//                    }

                    try {
                        val json = gson.fromJson(data, JsonObject::class.java)
                        val choices = json.getAsJsonArray("choices")
                        if (choices != null && choices.size() > 0) {
                            val delta = choices.get(0).asJsonObject.getAsJsonObject("delta")
                            val content = delta?.get("content")?.asString

                            if (content != null && content.isNotBlank()) {
                                // 字符级处理 - 使用状态机检测 ##[ 和 ]##
                                content.forEach { char ->
                                    when (parsingState) {
                                        ParsingState.NONE -> {
                                            when (char) {
                                                '#' -> parsingState = ParsingState.EXPECT_HASH1
                                                else -> currentNodeBuffer.append(char)
                                            }
                                        }
                                        ParsingState.EXPECT_HASH1 -> {
                                            when (char) {
                                                '#' -> parsingState = ParsingState.EXPECT_HASH2
                                                else -> {
                                                    // 不是 ##，回退到内容累积
                                                    parsingState = ParsingState.NONE
                                                    currentNodeBuffer.append('#')
                                                    currentNodeBuffer.append(char)
                                                }
                                            }
                                        }
                                        ParsingState.EXPECT_HASH2 -> {
                                            when (char) {
                                                '[' -> {
                                                    // 遇到 ##[，开始新节点
                                                    val idToComplete = currentNodeId
                                                    if (idToComplete != null && currentNodeBuffer.isNotEmpty()) {
                                                        // 处理上一个节点
                                                        val nodeContent = currentNodeBuffer.toString().trim()
                                                        if (nodeContent.isNotBlank() && !completedNodes.contains(idToComplete)) {
                                                            completedNodes.add(idToComplete)
                                                            completedCount++

                                                            Timber.d("[$TAG] ========== 节点[$idToComplete] 翻译完成 ==========")
                                                            Timber.d("[$TAG] 译文: \"$nodeContent\"")
                                                            Timber.d("[$TAG] 进度: $completedCount / ${markedTexts.size}")

                                                            // 缓存结果
                                                            val markedTextsIndex = markedTextsIndexMap[idToComplete] ?: -1
                                                            val cacheKey = if (markedTextsIndex >= 0) markedTexts[markedTextsIndex].take(100) else "node_${idToComplete}"
                                                            translateCache.put(cacheKey, nodeContent)

                                                            // 存储到结果映射
                                                            allSegments[idToComplete] = nodeContent

                                                            // 实时更新
                                                            onNodeCompleted(idToComplete, nodeContent)
                                                            Timber.d("[$TAG] ========== 节点[$idToComplete] nodeContent = {$nodeContent} 已发送 onNodeCompleted ==========")

                                                            onProgress(completedCount, markedTexts.size)
                                                        }
                                                    }
                                                    // 开始新节点
                                                    currentNodeBuffer.clear()
                                                    idBuffer.clear()
                                                    parsingState = ParsingState.PARSING_ID
                                                }
                                                else -> {
                                                    // 不是 ##[，回退到内容累积
                                                    parsingState = ParsingState.NONE
                                                    currentNodeBuffer.append("##")
                                                    currentNodeBuffer.append(char)
                                                }
                                            }
                                        }
                                        ParsingState.PARSING_ID -> {
                                            when (char) {
                                                ']' -> {
                                                    // ID 解析完成
                                                    currentNodeId = idBuffer.toString().toIntOrNull()
                                                    idBuffer.clear()
                                                    parsingState = ParsingState.EXPECT_HASH_END1
                                                    Timber.d("[$TAG] 开始节点 #$currentNodeId")
                                                }
                                                else -> {
                                                    if (char.isDigit() || char == '-') {
                                                        idBuffer.append(char)
                                                    } else {
                                                        // 非法字符，回退到内容累积
                                                        parsingState = ParsingState.NONE
                                                        currentNodeBuffer.append("##[")
                                                        currentNodeBuffer.append(idBuffer)
                                                        currentNodeBuffer.append(char)
                                                        idBuffer.clear()
                                                    }
                                                }
                                            }
                                        }
                                        ParsingState.EXPECT_HASH_END1 -> {
                                            when (char) {
                                                '#' -> parsingState = ParsingState.EXPECT_HASH_END2
                                                else -> {
                                                    // 不是 ]##，回退到内容累积
                                                    parsingState = ParsingState.NONE
                                                    currentNodeBuffer.append("##[${currentNodeId ?: ""}]")
                                                    currentNodeBuffer.append(char)
                                                }
                                            }
                                        }
                                        ParsingState.EXPECT_HASH_END2 -> {
                                            when (char) {
                                                '#' -> {
                                                    // 完成 ]##，开始累积内容
                                                    parsingState = ParsingState.PARSING_CONTENT
                                                }
                                                else -> {
                                                    // 不是 ]##，回退到内容累积
                                                    parsingState = ParsingState.NONE
                                                    currentNodeBuffer.append("##[${currentNodeId ?: ""}]#")
                                                    currentNodeBuffer.append(char)
                                                }
                                            }
                                        }
                                        ParsingState.PARSING_CONTENT -> {
                                            when (char) {
                                                '#' -> {
                                                    // 可能是新的 ##[ 开始标记
                                                    parsingState = ParsingState.EXPECT_HASH1
                                                }
                                                else -> currentNodeBuffer.append(char)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.w("[$TAG] 解析chunk失败: ${e.message}")
                    }
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: okhttp3.Response?
                ) {
                    Timber.d("[$TAG] =============onFailure==============")

                    if (!isCancelled && !isResumed) {
                        val statusCode = response?.code ?: -1
                        val errorBody = try {
                            response?.body?.string()
                        } catch (e: Exception) {
                            null
                        }
                        val errorMsg = "SSE连接失败 (HTTP $statusCode): ${t?.message ?: response?.message}"
                        Timber.e("[$TAG] ✗ $errorMsg")
                        if (errorBody != null) {
                            Timber.e("[$TAG] 错误响应体: $errorBody")
                        }
                        Timber.e("[$TAG] 异常详情: ${t?.stackTraceToString()}")
                        isResumed = true
                        continuation.resumeWithException(Exception(errorMsg))
                    } else {
                        Timber.d("[$TAG] SSE连接已取消或已恢复")
                        if (!isResumed) {
                            isResumed = true
                            try {
                                continuation.resume(Unit)
                            } catch (e: IllegalStateException) {
                                Timber.d("[$TAG] 忽略重复 resume: ${e.message}")
                            }
                        }
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    Timber.d("[$TAG] =============onClosed SSE 连接已关闭==============")

                    // 完成最后一个节点
                    val idToComplete = currentNodeId
                    if (idToComplete != null && !completedNodes.contains(idToComplete)) {
                        val completedText = currentNodeBuffer.toString().trim()
                        if (completedText.isNotBlank()) {
                            // 缓存结果
                            val markedTextsIndex = markedTextsIndexMap[idToComplete] ?: -1
                            val cacheKey = if (markedTextsIndex >= 0) markedTexts[markedTextsIndex].take(100) else "node_${idToComplete}"
                            translateCache.put(cacheKey, completedText)

                            completedNodes.add(idToComplete)
                            completedCount++

                            Timber.d("[$TAG] 节点[$idToComplete] 完成(关闭时): 译文=\"$completedText\"")

                            // 存储到结果映射
                            allSegments[idToComplete] = completedText

                            // 实时更新
                            onNodeCompleted(idToComplete, completedText)
                            Timber.d("[$TAG] 节点[$idToComplete] 最后节点 completedText = {$completedText}  已发送 onNodeCompleted ==========")

                            onProgress(completedCount, markedTexts.size)
                        }
                    }
                    try {
                        continuation.resume(Unit)
                        isResumed = true
                    } catch (e: IllegalStateException) {
                        Timber.d("[$TAG] 忽略重复 resume: ${e.message}")
                    }
                }
            }

            eventSource = eventSourceFactory.newEventSource(request, listener)

            // 支持协程取消
            continuation.invokeOnCancellation {
                Timber.d("[$TAG] 协程被取消")
                isCancelled = true
                eventSource?.cancel()
            }
        }

        // 构建最终结果
        val results = mutableListOf<String>()
        texts.forEachIndexed { index, text ->
            if (!hasEnglishChars(text)) {
                results.add(text)
            } else {
                val validIndex = validTexts.indexOf(text)
                if (validIndex != -1) {
                    val id = if (title != null && hasEnglishChars(title)) validIndex + 1 else validIndex
                    results.add(allSegments[id] ?: text)
                } else {
                    results.add(text)
                }
            }
        }

        Timber.d("[$TAG] ========== SSE流式翻译完成 最终结果 results ={$results}==========")
        return@withContext results
    }

    /**
     * 处理内容：过滤 think 标签和空行
     */
    private fun processContent(content: String, nodeRegex: Regex): String {
        // 过滤 think 标签之间的内容
        var result = content
        val thinkPattern = Regex(""".*?</think>""", RegexOption.DOT_MATCHES_ALL)
        result = thinkPattern.replace(result, "")

        // 去除空行（只保留非空行）
        val lines = result.lines()
        val nonEmptyLines = lines.filter { it.trim().isNotEmpty() }
        result = nonEmptyLines.joinToString("\n")

        return result
    }

    /**
     * 检查是否在 think 标签内
     */
    private fun isInThinkTag(content: String): Boolean {
        val thinkOpenTag = ""
        val thinkCloseTag = "</think>"
        val openIndex = content.indexOf(thinkOpenTag)
        val closeIndex = content.indexOf(thinkCloseTag)
        // 简单判断：有开启标签但还没有闭合标签
        return openIndex != -1 && (closeIndex == -1 || openIndex > closeIndex)
    }

    override fun getCacheStats(): TranslateCache.CacheStats {
        return translateCache.getStats()
    }

    override fun clearCache() {
        translateCache.clear()
    }

    override fun cancel() {
        Timber.d("[$TAG] ========== 取消翻译 ==========")
        Timber.d("[$TAG] 设置取消标志: isCancelled = true")
        isCancelled = true
        eventSource?.cancel()
        Timber.d("[$TAG] ✓ EventSource 已取消")
        Timber.d("[$TAG] ========== 翻译已取消 ==========")
    }
}