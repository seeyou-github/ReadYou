package me.ash.reader.infrastructure.translate.apistream

import android.webkit.WebView
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter
import me.ash.reader.infrastructure.log.TranslateDebugLogger
import me.ash.reader.infrastructure.translate.TranslateTask
import me.ash.reader.infrastructure.translate.TranslatePriority
import me.ash.reader.infrastructure.translate.cache.TranslateCache
import me.ash.reader.infrastructure.translate.model.TranslateModelConfig
import me.ash.reader.infrastructure.translate.ui.TranslateState
import timber.log.Timber

/**
 * 流式翻译管理器
 *
 * 功能：
 * - 使用 SSE（Server-Sent Events）进行流式翻译
 * - 单次请求：标题 + 所有文本节点
 * - 节点级别的流式：每个节点翻译完成后立即更新 DOM
 * - 支持缓存、中断、RPM 限制
 * - 切换原文/译文显示
 *
 * 创建日期：2026-01-31
 */
class StreamTranslateManager(
    private val webView: WebView,
    private val streamService: StreamTranslateService,
    private val initialConfig: TranslateModelConfig
) {
    companion object {
        private const val TAG = "StreamTranslateManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val gson = Gson()

    private val injector = StreamTranslateJsInjector(webView, gson)
    private val tlog by lazy { TranslateDebugLogger.from(webView.context) }

    private var scheduler: StreamTranslateBatchScheduler? = null

    /** 是否已取消 */
    @Volatile
    private var isCancelled = false

    /** 翻译状态回调 */
    var onStateChanged: ((TranslateState) -> Unit)? = null

    /** 进度回调 */
    var onProgress: ((current: Int, total: Int) -> Unit)? = null

    /** 错误回调 */
    var onError: ((String) -> Unit)? = null

    /** 完成回调（返回完整HTML用于缓存） */
    var onComplete: ((fullHtml: String) -> Unit)? = null

    /** 标题翻译完成回调 */
    var onTitleTranslated: ((String) -> Unit)? = null

    /** 节点总数 */
    private var totalNodes: Int = 0

    /** 已完成节点数量 */
    private var completedNodes: Int = 0

    /**
     * 判断字符串是否包含英文字符
     */
    private fun hasEnglishChars(text: String): Boolean {
        return text.any { it in 'a'..'z' || it in 'A'..'Z' }
    }

    /**
     * 检查DOM是否已翻译（用于恢复缓存时）
     */
    suspend fun checkHasTranslation(): Boolean {
        return injector.checkHasTranslation()
    }

    /**
     * 从缓存恢复后调用，恢复事件监听等
     */
    suspend fun restoreFromCache(): Boolean {
        return injector.restoreFromCache()
    }

    /**
     * 切换显示原文/译文
     *
     * @param showTranslation true显示译文，false显示原文
     * @return 更新的节点数量
     */
    suspend fun toggleTranslationDisplay(showTranslation: Boolean): Int {
        Timber.d("[$TAG] 切换显示: ${if (showTranslation) "译文" else "原文"}")
        return injector.toggleTranslationDisplay(showTranslation)
    }

    /**
     * 获取完整HTML内容（用于缓存）
     */
    suspend fun getFullHtmlContent(): String {
        return injector.getFullHtmlContent()
    }

    /**
     * 开始流式翻译流程
     *
     * @param title 文章标题
     * @param config 翻译模型配置，为 null 时使用默认配置
     */
    fun startStreamTranslation(title: String?, config: TranslateModelConfig? = null) {
        Timber.d("[$TAG] ═══════════════════════════════════════════════════════════════")
        Timber.d("[$TAG] ========== 开始流式翻译流程 ==========")
        Timber.d("[$TAG] ═══════════════════════════════════════════════════════════════")
        Timber.d("[$TAG] 参数: title=${title?.take(50) ?: "null"}, config=${config?.provider ?: "null"}")
        tlog.log("Manager") {
            "startStreamTranslation BEGIN webView=${System.identityHashCode(webView)} " +
                "titleLen=${title?.length ?: 0} provider=${config?.provider ?: initialConfig.provider} " +
                "model=${config?.model ?: initialConfig.model}"
        }

        totalNodes = 0
        completedNodes = 0
        isCancelled = false

        val finalConfig = config ?: initialConfig

        scope.launch {
            try {
                // 步骤 1: 标记 DOM
                Timber.d("[$TAG] 步骤 1: 开始标记 DOM")
                tlog.log("Manager") { "step1 markTextNodes START" }
                onStateChanged?.invoke(TranslateState.MarkingDOM)

                var nodeCount = injector.markTextNodes()
                var markAttempt = 0
                while (nodeCount == 0 && markAttempt < 3 && !isCancelled) {
                    markAttempt++
                    val waitMillis = 150L * markAttempt
                    tlog.log("Manager") {
                        "step1 markTextNodes RETRY attempt=$markAttempt waitMs=$waitMillis"
                    }
                    delay(waitMillis)
                    nodeCount = injector.markTextNodes()
                }
                Timber.d("[$TAG] 步骤 1 完成: DOM标记完成，共 $nodeCount 个节点")
                tlog.log("Manager") { "step1 markTextNodes END count=$nodeCount" }

                if (nodeCount == 0) {
                    Timber.w("[$TAG] 没有找到可翻译的文本节点")
                    tlog.log("Manager") {
                        "ABORT reason=no_translatable_text nodeCount=0 " +
                            "(可能：DOM未就绪 / 内容全为非英文 / WebView刚 reload 还没渲染)"
                    }
                    onError?.invoke("没有找到可翻译的文本")
                    onStateChanged?.invoke(TranslateState.Idle)
                    return@launch
                }

                totalNodes = nodeCount

                // 步骤 2: 提取文本节点
                Timber.d("[$TAG] 步骤 2: 开始提取文本节点")
                tlog.log("Manager") { "step2 extractTextNodes START" }
                onStateChanged?.invoke(TranslateState.ExtractingText)

                var textNodes = injector.extractTextNodes()
                var extractAttempt = 0
                while (textNodes.isEmpty() && extractAttempt < 2 && !isCancelled) {
                    extractAttempt++
                    val waitMillis = 150L * extractAttempt
                    tlog.log("Manager") {
                        "step2 extractTextNodes RETRY attempt=$extractAttempt waitMs=$waitMillis"
                    }
                    delay(waitMillis)
                    textNodes = injector.extractTextNodes()
                }
                Timber.d("[$TAG] 步骤 2 完成: 提取了 ${textNodes.size} 个文本节点")
                tlog.log("Manager") {
                    "step2 extractTextNodes END count=${textNodes.size} " +
                        "totalChars=${textNodes.sumOf { it.text.length }}"
                }

                if (textNodes.isEmpty()) {
                    Timber.w("[$TAG] 提取的文本节点列表为空")
                    tlog.log("Manager") { "ABORT reason=empty_text_nodes nodeCount=$nodeCount" }
                    onError?.invoke("没有可翻译的文本内容")
                    onStateChanged?.invoke(TranslateState.Idle)
                    return@launch
                }

                // 步骤 3: 开始流式翻译
                Timber.d("[$TAG] 步骤 3: 开始流式翻译")
                tlog.log("Manager") {
                    "step3 startStreamTranslationInternal nodes=${textNodes.size} provider=${finalConfig.provider}"
                }
                onStateChanged?.invoke(TranslateState.Translating)

                startStreamTranslationInternal(textNodes, title, finalConfig)

            } catch (e: Exception) {
                Timber.e(e, "[$TAG] 流式翻译流程失败")
                tlog.error("Manager", e) { "startStreamTranslation FAILED cancelled=$isCancelled" }
                if (!isCancelled) {
                    onError?.invoke(e.toFullErrorMessage())
                    onStateChanged?.invoke(TranslateState.Idle)
                }
            }
        }
    }

    /**
     * 执行流式翻译
     */
    private fun startStreamTranslationInternal(
        textNodes: List<me.ash.reader.infrastructure.translate.webbiew.TextNodeInfo>,
        title: String?,
        config: TranslateModelConfig
    ) {
        // 提取文本列表
        val texts = textNodes.map { it.text }

        // 获取 RPM 值
        val rpm = config.rpm ?: 10
        Timber.d("[$TAG] RPM限制: $rpm")

        // 创建调度器
        scheduler = StreamTranslateBatchScheduler(streamService, rpm).apply {
            onProgress = { current, total ->
                if (!isCancelled) {
                    Timber.d("[$TAG] onProgress 进度更新: $current / $total")
                    this@StreamTranslateManager.onProgress?.invoke(current, total)
                }
            }
            onNodeCompleted = { nodeId, translatedText ->
                scope.launch {
                    if (!isCancelled) {
                        // 处理标题翻译（id = -1）
                        if (nodeId == -1 && title != null) {
                            Timber.d("[$TAG] 📌onNodeCompleted  标题翻译完成: \"$translatedText\"")
                            onTitleTranslated?.invoke(translatedText)
                        } else {
                            // 更新 DOM（id 从 0 开始）
                            val updated = injector.updateSingleNode(nodeId, translatedText)
                            Timber.d("[$TAG] [onNodeCompleted] 节点 #$nodeId DOM 更新: ${if (updated) "成功" else "失败"}")
                        }
                    }
                }
            }
            onError = { error ->
                if (!isCancelled) {
                    Timber.e(error, "[$TAG] onError 流式翻译错误")
                    tlog.error("Manager", error) { "scheduler onError full=${error.message.orEmpty()}" }
                    this@StreamTranslateManager.onError?.invoke(error.toFullErrorMessage())
                }
            }
            onComplete = { results ->
                if (!isCancelled) {
                    Timber.d("[$TAG] ═══════════════════════════════════════════════════════════════")
                    Timber.d("[$TAG] ✅ onComplete 流式翻译完成")
                    Timber.d("[$TAG] ═══════════════════════════════════════════════════════════════")
                    scope.launch {
                        val html = injector.getFullHtmlContent()
                        this@StreamTranslateManager.onComplete?.invoke(html)
                    }
                    this@StreamTranslateManager.onStateChanged?.invoke(TranslateState.Translated)
                }
            }
        }

        // 启动流式翻译
        scheduler?.startStreamTranslation(title, texts, config)
    }

    /**
     * 取消翻译任务
     */
    fun cancel() {
        Timber.d("[$TAG] ========== 取消流式翻译 ==========")
        isCancelled = true
        scheduler?.cancel()

        // 清除所有翻译（恢复原始DOM）
        scope.launch {
            try {
                injector.clearAllTranslations()
                Timber.d("[$TAG] 清除翻译完成，DOM已恢复")
            } catch (e: Exception) {
                Timber.e(e, "[$TAG] 清除翻译失败")
            }
        }
    }

    /**
     * 清除翻译（恢复原文）- 用于错误处理
     */
    suspend fun clearTranslations() {
        Timber.d("[$TAG] ========== 清除翻译并恢复原文 ==========")
        injector.clearAllTranslations()
    }

    /**
     * 清理资源
     */
    fun destroy() {
        Timber.d("[$TAG] 销毁 StreamTranslateManager")
        scheduler?.destroy()
        scheduler = null
        scope.cancel()
    }

    /**
     * 获取当前服务ID
     */
    fun getCurrentServiceId(): String = streamService.getServiceId()

    /**
     * 获取当前服务名称
     */
    fun getCurrentServiceName(): String {
        return streamService.getServiceName()
    }

    /**
     * 获取缓存统计
     */
    fun getCacheStats(): TranslateCache.CacheStats {
        return streamService.getCacheStats()
    }

    /**
     * 清空缓存
     */
    fun clearCache() {
        streamService.clearCache()
    }

    /**
     * 获取当前翻译配置
     *
     * @return 翻译模型配置
     */
    fun getConfig(): TranslateModelConfig {
        return initialConfig
    }



}

private fun Throwable.toFullErrorMessage(): String {
    val stackTrace = StringWriter().also { writer ->
        printStackTrace(PrintWriter(writer))
    }.toString()
    return buildString {
        appendLine(message ?: "翻译失败")
        appendLine()
        appendLine("Full Exception:")
        append(stackTrace)
    }.trim()
}
