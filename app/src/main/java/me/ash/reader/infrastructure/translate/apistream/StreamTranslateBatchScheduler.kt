package me.ash.reader.infrastructure.translate.apistream

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.infrastructure.translate.RpmRateLimitManager
import timber.log.Timber

/**
 * 流式翻译调度器
 *
 * 用于单次流式翻译请求的调度管理：
 * - RPM限制检查
 * - 进度回调
 * - 错误处理
 *
 * 注意：流式翻译使用单次请求（标题+所有文本节点），不需要批量调度
 *
 * 创建日期：2026-01-31
 */
class StreamTranslateBatchScheduler(
    private val streamService: StreamTranslateService,
    private val rpm: Int
) {
    companion object {
        private const val TAG = "StreamTranslateScheduler"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isCancelled = false

    /** 进度回调 */
    var onProgress: ((completed: Int, total: Int) -> Unit)? = null

    /** 完成回调 */
    var onComplete: ((results: List<String>) -> Unit)? = null

    /** 错误回调 */
    var onError: ((Throwable) -> Unit)? = null

    /** 节点完成回调 (nodeId -> translatedText) */
    var onNodeCompleted: ((nodeId: Int, translatedText: String) -> Unit)? = null

    /**
     * 开始流式翻译
     *
     * @param title 文章标题
     * @param texts 文本节点列表
     * @param config 翻译配置
     */
    fun startStreamTranslation(
        title: String?,
        texts: List<String>,
        config: me.ash.reader.infrastructure.translate.model.TranslateModelConfig
    ) {
        Timber.d("[$TAG] ========== 开始流式翻译调度 ==========")
        Timber.d("[$TAG] 标题: ${title?.take(50) ?: "null"}")
        Timber.d("[$TAG] 文本节点数: ${texts.size}")
        Timber.d("[$TAG] RPM限制: $rpm")

        isCancelled = false

        scope.launch {
            try {
                // RPM限制检查
                val (needWait, waitTime) = RpmRateLimitManager.waitForPermission(
                    rpm = rpm,
                    showToast = true
                )

                if (needWait) {
                    Timber.d("[$TAG] 等待RPM限制 ${waitTime}ms")
                }

                // 设置RPM限制提示回调
                RpmRateLimitManager.onLimitReached = {
                    scope.launch(Dispatchers.Main) {
                        onError?.invoke(Exception("达到API请求限制，请稍等片刻..."))
                    }
                }

                // 调用流式翻译服务
                val results = streamService.translateStream(
                    title = title,
                    texts = texts,
                    config = config,
                    onNodeCompleted = { nodeId, translatedText ->
                        Timber.d("[$TAG] 节点 #$nodeId 翻译完成: ${translatedText.take(50)}...")
                        scope.launch(Dispatchers.Main) {
                            onNodeCompleted?.invoke(nodeId, translatedText)
                        }
                    },
                    onProgress = { completed, total ->
                        Timber.d("[$TAG] 进度: $completed / $total")
                        scope.launch(Dispatchers.Main) {
                            onProgress?.invoke(completed, total)
                        }
                    },
                    onError = { error ->
                        Timber.e(error, "[$TAG] 流式翻译错误")
                        if (!isCancelled) {
                            scope.launch(Dispatchers.Main) {
                                onError?.invoke(error)
                            }
                        }
                    }
                )

                // 翻译完成
                if (!isCancelled) {
                    Timber.d("[$TAG] ========== 流式翻译完成 ==========")
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(results)
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "[$TAG] 流式翻译异常")
                if (!isCancelled) {
                    withContext(Dispatchers.Main) {
                        onError?.invoke(e)
                    }
                }
            }
        }
    }

    /**
     * 取消翻译
     */
    fun cancel() {
        Timber.d("[$TAG] ========== 取消流式翻译 ==========")
        isCancelled = true
        streamService.cancel()
        scope.cancel()
    }

    /**
     * 清理资源
     */
    fun destroy() {
        cancel()
    }
}