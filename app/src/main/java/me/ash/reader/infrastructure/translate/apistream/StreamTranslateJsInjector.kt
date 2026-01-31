package me.ash.reader.infrastructure.translate.apistream

import android.webkit.WebView
import com.google.gson.Gson
import me.ash.reader.infrastructure.translate.webbiew.TranslateJsInjector
import me.ash.reader.infrastructure.translate.webbiew.TranslationResult
import timber.log.Timber

/**
 * 流式翻译 JS 注入器
 *
 * 封装 WebView JavaScript 注入逻辑，用于流式翻译：
 * - 标记 DOM 文本节点
 * - 提取文本节点
 * - 逐个节点更新翻译结果（流式）
 * - 切换原文/译文显示
 * - 缓存管理
 *
 * 内部复用现有的 TranslateJsInjector，因为 DOM 操作逻辑是相同的
 *
 * 创建日期：2026-01-31
 */
class StreamTranslateJsInjector(
    private val webView: WebView,
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "StreamTranslateJsInjector"
    }

    private val baseInjector = TranslateJsInjector(webView, gson)

    /**
     * 标记所有文本节点
     *
     * @return 标记的节点总数
     */
    suspend fun markTextNodes(): Int {
        return baseInjector.markTextNodes()
    }

    /**
     * 提取所有文本节点
     *
     * @return 文本节点列表
     */
    suspend fun extractTextNodes(): List<me.ash.reader.infrastructure.translate.webbiew.TextNodeInfo> {
        return baseInjector.extractTextNodes()
    }

    /**
     * 更新单个节点的翻译结果（流式更新）
     *
     * @param nodeId 节点ID
     * @param translatedText 翻译文本
     * @return 是否更新成功
     */
    suspend fun updateSingleNode(nodeId: Int, translatedText: String): Boolean {
        Timber.d("[$TAG] ========== 更新单个节点翻译 ==========")
        Timber.d("[$TAG] 节点ID: $nodeId, 翻译文本: \"${translatedText.take(50)}...\"")

        val result = TranslationResult(
            id = nodeId,
            translatedText = translatedText
        )

        val count = baseInjector.updateTranslations(listOf(result))
        return count > 0
    }

    /**
     * 批量更新翻译结果
     *
     * @param translations 翻译结果列表
     * @return 更新的节点数量
     */
    suspend fun updateTranslations(translations: List<TranslationResult>): Int {
        return baseInjector.updateTranslations(translations)
    }

    /**
     * 切换显示原文/译文
     *
     * @param showTranslation true 显示译文，false 显示原文
     * @return 更新的节点数量
     */
    suspend fun toggleTranslationDisplay(showTranslation: Boolean): Int {
        return baseInjector.toggleTranslationDisplay(showTranslation)
    }

    /**
     * 获取完整 HTML 内容（用于缓存）
     *
     * @return 包含译文和原文的完整 HTML 字符串
     */
    suspend fun getFullHtmlContent(): String {
        return baseInjector.getFullHtmlContent()
    }

    /**
     * 检查页面是否包含译文
     *
     * @return true 如果页面已经翻译过
     */
    suspend fun checkHasTranslation(): Boolean {
        return baseInjector.checkHasTranslation()
    }




    /**
     * 从缓存恢复 DOM
     *
     * @return true 如果当前正在显示译文
     */
    suspend fun restoreFromCache(): Boolean {
        return baseInjector.restoreFromCache()
    }

    /**
     * 清除所有翻译（恢复原始 DOM）
     *
     * @return 恢复的节点数量
     */
    suspend fun clearAllTranslations(): Int {
        return baseInjector.clearAllTranslations()
    }

    /**
     * 获取滚动位置
     *
     * @return 滚动位置（像素）
     */
    suspend fun getScrollPosition(): Int {
        return baseInjector.getScrollPosition()
    }

    /**
     * 设置滚动位置
     *
     * @param scrollY 滚动位置（像素）
     */
    fun setScrollPosition(scrollY: Int) {
        baseInjector.setScrollPosition(scrollY)
    }

    /**
     * 获取翻译统计
     *
     * @return 统计信息
     */
    suspend fun getTranslationStats(): me.ash.reader.infrastructure.translate.webbiew.TranslationStats {
        return baseInjector.getTranslationStats()
    }
}
