package me.ash.reader.infrastructure.translate.webbiew

import android.webkit.WebView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 翻译 JS 注入器
 *
 * 功能：
 * - 封装 JS 注入逻辑
 * - 提供类型安全的 API
 * - 处理 JSON 序列化/反序列化
 * - 使用 suspend 函数确保异步 操作正确完成
 */
class TranslateJsInjector(
    private val webView: WebView,
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "TranslateJsInjector"
    }

    /**
     * 标记所有文本节点
     *
     * @return 标记的节点总数
     */
    suspend fun markTextNodes(): Int = suspendCancellableCoroutine { continuation ->
        Timber.d("[$TAG] ========== 开始标记 DOM 文本节点 ==========")
        Timber.d("[$TAG] 执行 JavaScript 脚本: MARK_TEXT_NODES")
        
        webView.evaluateJavascript(TranslateScripts.MARK_TEXT_NODES) { result ->
            val count = result?.toIntOrNull() ?: 0
            Timber.d("[$TAG] DOM 标记完成: $count 个节点")
            Timber.d("[$TAG] JavaScript 返回值: $result")
            continuation.resume(count)
        }
    }

    /**
     * 提取所有文本节点
     *
     * @return 文本节点列表
     */
    suspend fun extractTextNodes(): List<TextNodeInfo> = suspendCancellableCoroutine { continuation ->
        Timber.d("[$TAG] ========== 开始提取文本节点 ==========")
        Timber.d("[$TAG] 执行 JavaScript 脚本: EXTRACT_TEXT_NODES")
        
        webView.evaluateJavascript(TranslateScripts.EXTRACT_TEXT_NODES) { result ->
            try {
                Timber.d("[$TAG] JavaScript 返回值: ${result?.take(500)}...")
                
                // WebView returns a JSON string that's double-encoded
                // First, we get a JSON string like: "[{\"id\":0,...}]"
                // We need to parse it as a JSON string first, then parse the result as JSON array
                val cleanedResult = result?.let { jsonStr ->
                    if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                        // It's a JSON string, parse it to get the actual content
                        org.json.JSONTokener(jsonStr).nextValue().toString()
                    } else {
                        jsonStr
                    }
                }
                
                Timber.d("[$TAG] 清理后的返回值: ${cleanedResult?.take(500)}...")
                
                val nodes = gson.fromJson<List<TextNodeInfo>>(
                    cleanedResult,
                    object : com.google.gson.reflect.TypeToken<List<TextNodeInfo>>() {}.type
                )
                
                Timber.d("[$TAG] 提取文本节点: ${nodes.size} 个")
                
                // 打印每个节点的详细信息
                nodes.forEach { node ->
                    Timber.d("[$TAG]   - 节点 #${node.id}: 文本长度=${node.text.length}, 优先级=${node.priority}, 文本预览=\"${node.text.take(100)}...\"")
                }
                
                continuation.resume(nodes)
            } catch (e: Exception) {
                Timber.e(e, "[$TAG] 解析文本节点失败")
                Timber.e(e, "[$TAG] 返回值: $result")
                continuation.resume(emptyList())
            }
        }
    }

    /**
     * 更新翻译结果
     *
     * @param translations 翻译结果列表
     * @return 更新的节点数量
     */
    suspend fun updateTranslations(translations: List<TranslationResult>): Int = suspendCancellableCoroutine { continuation ->
        Timber.d("[$TAG] ========== 开始更新翻译结果 ==========")
        Timber.d("[$TAG] 接收到 ${translations.size} 条翻译结果")

        translations.forEachIndexed { index, item ->
            Timber.d("[$TAG]   - #$index: id=${item.id}, text=\"${item.translatedText.take(50)}...\"")
        }

        val json = gson.toJson(translations)
        Timber.d("[$TAG] JSON 序列化完成，长度: ${json.length}")

        // 直接在 JavaScript 中内联 JSON，避免 WebView 字符串参数编码问题
        val execScript = """
            (function() {
                const translations = $json;
                let count = 0;
                translations.forEach(function(item) {
                    if (item.id === -1) return;
                    const el = document.querySelector('[data-tid="' + item.id + '"]');
                    if (el) {
                        if (!el.dataset.originalText) {
                            el.dataset.originalText = el.textContent.trim();
                        }
                        el.dataset.translatedText = item.text;
                        el.textContent = item.text;
                        count++;
                    }
                });
                return count;
            })();
        """.trimIndent()

        Timber.d("[$TAG] ========== 执行 JavaScript 更新 WebView ==========")

        webView.evaluateJavascript(execScript) { result ->
            val count = result?.toIntOrNull() ?: 0
            Timber.d("[$TAG] ========== WebView 更新完成 ==========")
            Timber.d("[$TAG] 更新了 $count 个 DOM 节点")
            Timber.d("[$TAG] JavaScript 返回值: $result")
            continuation.resume(count)
        }
    }

    /**
     * 切换显示原文/译文【lhp:可以切换】
     *
     * @param showTranslation true 显示译文，false 显示原文
     * @return 更新的节点数量
     */
    suspend fun toggleTranslationDisplay(showTranslation: Boolean): Int = suspendCancellableCoroutine { continuation ->
        Timber.d("[$TAG] ========== 切换翻译显示状态：${if (showTranslation) "原文" else "译文"} ---> ${if (showTranslation) "译文" else "原文"}")

        val script = "${TranslateScripts.TOGGLE_TRANSLATION_DISPLAY}($showTranslation);"

        webView.evaluateJavascript(script) { result ->
            val count = result?.toIntOrNull() ?: 0
            Timber.d("[$TAG] 切换翻译显示: $count 个节点已更新")
            Timber.d("[$TAG] JavaScript 返回值: $result")
            continuation.resume(count)
        }
    }

    /**
     * 获取完整 HTML 内容（用于缓存）
     *
     * @return 包含译文和原文的完整 HTML 字符串
     */
    suspend fun getFullHtmlContent(): String = suspendCancellableCoroutine { continuation ->
        Timber.d("[$TAG] ========== 获取完整 HTML 内容 ==========")

        webView.evaluateJavascript(TranslateScripts.GET_FULL_HTML_CONTENT) { result ->
            val html = if (result.isNullOrBlank()) {
                Timber.d("[$TAG] HTML 为空")
                ""
            } else {
                try {
                    // 使用 JSONTokener 正确解析 JSON 字符串，自动解码转义字符（\" → "）
                    val decoded = org.json.JSONTokener(result).nextValue().toString()
                    Timber.d("[$TAG] HTML 解码完成，原始长度: ${result.length}, 解码后长度: ${decoded.length}")
                    decoded
                } catch (e: Exception) {
                    Timber.e(e, "[$TAG] 解析 HTML JSON 失败")
                    ""
                }
            }
            Timber.d("[$TAG] ========== HTML 获取完成 ==========")
            Timber.d("[$TAG] HTML 总长度: ${html.length}")
            continuation.resume(html)
        }
    }

    /**
     * 检查页面是否包含译文
     *
     * @return true 如果页面已经翻译过
     */
    suspend fun checkHasTranslation(): Boolean = suspendCancellableCoroutine { continuation ->
        Timber.d("[$TAG] ========== 检查页面是否包含译文 ==========")

        webView.evaluateJavascript(TranslateScripts.CHECK_HAS_TRANSLATION) { result ->
            try {
                val response = gson.fromJson<Map<String, Any>>(
                    result,
                    object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                )
                val hasTranslation = response["hasTranslation"] as? Boolean ?: false
                val translatedCount = (response["translatedCount"] as? Double)?.toInt() ?: 0


                Timber.d("[$TAG]  打开页面会重置翻译结果与状态，所以即使加载了翻译缓存也会被清除，翻译时重新加载")
                Timber.d("[$TAG] 检查结果: hasTranslation=$hasTranslation, translatedCount=$translatedCount")
                continuation.resume(hasTranslation)
            } catch (e: Exception) {
                Timber.e(e, "[$TAG] 解析检查结果失败")
                continuation.resume(false)
            }
        }
    }





    /**
     * 从缓存恢复 DOM（加载缓存时使用）
     *
     * @return true 如果当前正在显示译文
     */
    suspend fun restoreFromCache(): Boolean = suspendCancellableCoroutine { continuation ->
        Timber.d("[$TAG] ========== 从缓存恢复 DOM ==========")

        webView.evaluateJavascript(TranslateScripts.RESTORE_FROM_CACHE) { result ->
            val showingTranslation = result?.toBooleanStrictOrNull() ?: false
            Timber.d("[$TAG] 恢复完成，当前显示译文: $showingTranslation")
            continuation.resume(showingTranslation)
        }
    }

    /**
     * 清除所有翻译（恢复原始 DOM）
     *
     * 用于翻译错误或取消时，移除所有翻译相关的标记和内容
     *
     * @return 恢复的节点数量
     */
    suspend fun clearAllTranslations(): Int = suspendCancellableCoroutine { continuation ->
        Timber.d("[$TAG] ========== 清除所有翻译 ==========")

        webView.evaluateJavascript(TranslateScripts.CLEAR_ALL_TRANSLATIONS) { result ->
            val count = result?.toIntOrNull() ?: 0
            Timber.d("[$TAG] 清除翻译完成: $count 个节点已恢复")
            Timber.d("[$TAG] JavaScript 返回值: $result")
            continuation.resume(count)
        }
    }

    /**
     * 获取滚动位置
     *
     * @return 滚动位置（像素）
     */
    suspend fun getScrollPosition(): Int = suspendCancellableCoroutine { continuation ->
        Timber.d("[$TAG] 获取滚动位置")
        webView.evaluateJavascript(TranslateScripts.GET_SCROLL_POSITION) { result ->
            val position = result?.toIntOrNull() ?: 0
            Timber.d("[$TAG] 滚动位置: $position")
            continuation.resume(position)
        }
    }

    /**
     * 设置滚动位置
     *
     * @param scrollY 滚动位置（像素）
     */
    fun setScrollPosition(scrollY: Int) {
        Timber.d("[$TAG] 设置滚动位置: $scrollY")
        val script = "${TranslateScripts.SET_SCROLL_POSITION}($scrollY);"
        webView.evaluateJavascript(script, null)
    }

    /**
     * 清空翻译并恢复原文（旧方法，使用 CLEAR_ALL_TRANSLATIONS）
     *
     * @return 恢复的节点数量
     */
    suspend fun clearTranslations(): Int = suspendCancellableCoroutine { continuation ->
        Timber.d("[$TAG] ========== 清空翻译并恢复原文 ==========")
        webView.evaluateJavascript(TranslateScripts.CLEAR_ALL_TRANSLATIONS) { result ->
            val count = result?.toIntOrNull() ?: 0
            Timber.d("[$TAG] 恢复原文完成: $count 个节点")
            Timber.d("[$TAG] JavaScript 返回值: $result")
            continuation.resume(count)
        }
    }

    /**
     * 获取翻译统计
     *
     * @return 统计信息
     */
    suspend fun getTranslationStats(): TranslationStats = suspendCancellableCoroutine { continuation ->
        Timber.d("[$TAG] 获取翻译统计")
        webView.evaluateJavascript(TranslateScripts.GET_TRANSLATION_STATS) { result ->
            try {
                // WebView returns a JSON string that's double-encoded
                val cleanedResult = result?.let { jsonStr ->
                    if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                        org.json.JSONTokener(jsonStr).nextValue().toString()
                    } else {
                        jsonStr
                    }
                }
                
                val stats = gson.fromJson<TranslationStats>(
                    cleanedResult,
                    TranslationStats::class.java
                )
                Timber.d("[$TAG] 翻译统计: total=${stats.total}, translated=${stats.translated}, untranslated=${stats.untranslated}")
                continuation.resume(stats)
            } catch (e: Exception) {
                Timber.e(e, "[$TAG] 解析翻译统计失败")
                continuation.resume(TranslationStats(0, 0, 0))
            }
        }
    }

    /**
     * 批量更新翻译结果（优化版）
     *
     * @param translations 翻译结果列表
     * @return 更新的节点数量
     */
    suspend fun updateTranslationsBatch(translations: List<TranslationResult>): Int = suspendCancellableCoroutine { continuation ->
        Timber.d("[$TAG] ========== 开始批量更新翻译结果 ==========")
        Timber.d("[$TAG] 接收到 ${translations.size} 条翻译结果")
        
        val json = gson.toJson(translations)
        Timber.d("[$TAG] JSON 序列化完成，长度: ${json.length}")
        
        val script = "${TranslateScripts.UPDATE_TRANSLATIONS_BATCH}($json);"
        Timber.d("[$TAG] 执行 JavaScript 脚本: UPDATE_TRANSLATIONS_BATCH")
        
        webView.evaluateJavascript(script) { result ->
            val count = result?.toIntOrNull() ?: 0
            Timber.d("[$TAG] 批量更新翻译结果: $count 个节点")
            continuation.resume(count)
        }
    }
}

/**
 * 文本节点信息
 */
data class TextNodeInfo(
    val id: Int,
    val text: String,
    val priority: String = "normal"
)

/**
 * 翻译结果
 * 用于 JavaScript 交互，字段名需要与 JS 脚本匹配
 */
data class TranslationResult(
    val id: Int,
    @com.google.gson.annotations.SerializedName("text")
    val translatedText: String
)

/**
 * 翻译统计
 */
data class TranslationStats(
    val total: Int,
    val translated: Int,
    val untranslated: Int
)