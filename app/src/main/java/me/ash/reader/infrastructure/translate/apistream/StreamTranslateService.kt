package me.ash.reader.infrastructure.translate.apistream

import me.ash.reader.infrastructure.translate.cache.TranslateCache
import me.ash.reader.infrastructure.translate.model.TranslateModelConfig

/**
 * 流式翻译服务接口
 *
 * 支持SSE（Server-Sent Events）流式翻译
 * - 单次请求包含标题和所有文本节点
 * - 每个节点翻译完成时回调（节点级别的流式，不是字符级别）
 * - 支持缓存、中断、RPM限制
 *
 * 创建日期：2026-01-31
 */
interface StreamTranslateService {

    /**
     * 流式翻译文本列表（单次请求）
     *
     * @param title 文章标题（可为null）
     * @param texts 待翻译的文本节点列表
     * @param config 翻译模型配置
     * @param onNodeCompleted 每个节点翻译完成时的回调 (nodeId -> translatedText)
     * @param onProgress 进度回调 (completed / total)
     * @param onError 错误回调
     * @return 所有翻译结果的完整列表
     * @throws Exception 翻译失败时抛出异常
     */
    suspend fun translateStream(
        title: String?,
        texts: List<String>,
        config: TranslateModelConfig,
        onNodeCompleted: (nodeId: Int, translatedText: String) -> Unit,
        onProgress: (completed: Int, total: Int) -> Unit,
        onError: (error: Throwable) -> Unit
    ): List<String>

    /**
     * 获取服务名称
     *
     * @return 服务名称（用于UI显示）
     */
    fun getServiceName(): String

    /**
     * 获取服务ID
     *
     * @return 服务ID（用于配置存储）
     */
    fun getServiceId(): String

    /**
     * 获取缓存统计
     *
     * @return 缓存统计信息
     */
    fun getCacheStats(): TranslateCache.CacheStats

    /**
     * 清空缓存
     */
    fun clearCache()

    /**
     * 取消当前翻译
     */
    fun cancel()
}