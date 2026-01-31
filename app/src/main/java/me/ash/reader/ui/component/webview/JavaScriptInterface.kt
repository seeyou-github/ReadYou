package me.ash.reader.ui.component.webview

import android.webkit.JavascriptInterface

/**
 * WebView JavaScript 接口
 *
 * 功能：
 * - 图片点击回调
 * - 翻译相关回调（DOM 标记、文本提取、翻译更新等）
 */
interface JavaScriptInterface {

    /**
     * 图片点击回调
     */
    @JavascriptInterface
    fun onImgTagClick(imgUrl: String?, alt: String?)

    // ========== 翻译相关回调 ==========

    /**
     * DOM 标记完成回调
     * @param totalNodes 标记的文本节点总数
     */
    @JavascriptInterface
    fun onDomMarked(totalNodes: Int)

    /**
     * 文本节点提取完成回调
     * @param nodesJson 文本节点 JSON 数组 [{"id": 0, "text": "..."}]
     */
    @JavascriptInterface
    fun onTextNodesExtracted(nodesJson: String)

    /**
     * 可视区域节点提取完成回调
     * @param nodesJson 可视区域节点 JSON 数组
     */
    @JavascriptInterface
    fun onVisibleNodesExtracted(nodesJson: String)

    /**
     * 翻译节点更新完成回调
     * @param updatedCount 更新的节点数量
     */
    @JavascriptInterface
    fun onTranslationsUpdated(updatedCount: Int)

    /**
     * 日志回调（用于调试）
     * @param message 日志消息
     */
    @JavascriptInterface
    fun onLog(message: String)

    companion object {

        const val NAME = "JavaScriptInterface"
    }
}
