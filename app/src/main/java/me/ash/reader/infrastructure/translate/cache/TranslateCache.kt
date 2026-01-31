package me.ash.reader.infrastructure.translate.cache

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 翻译缓存
 *
 * 功能：
 * - 缓存翻译结果，避免重复翻译
 * - 提供缓存统计信息
 * - 支持清空缓存
 */
@Singleton
class TranslateCache @Inject constructor() {
    companion object {
        private const val TAG = "TranslateCache"
    }

    /** 缓存映射：原文 -> 译文 */
    private val cache = mutableMapOf<String, String>()

    /**
     * 获取缓存
     *
     * @param text 原文
     * @return 译文，如果未缓存则返回 null
     */
    fun get(text: String): String? {
        val result = cache[text]
        if (result != null) {
            Timber.Forest.d("[$TAG] 缓存命中: ${text.take(50)}...")
        }
        return result
    }

    /**
     * 设置缓存
     *
     * @param text 原文
     * @param translated 译文
     */
    fun put(text: String, translated: String) {
        cache[text] = translated
        Timber.Forest.d("[$TAG] 缓存保存: ${text.take(50)}... -> ${translated.take(50)}...")
    }



    /**
     * 清空缓存
     */
    fun clear() {
        val size = cache.size
        cache.clear()
        Timber.Forest.d("[$TAG] 缓存已清空: 清除了 $size 条记录")
    }

    /**
     * 获取缓存统计
     *
     * @return 缓存统计信息
     */
    fun getStats(): CacheStats {
        return CacheStats(
            size = cache.size,
            totalChars = cache.keys.sumOf { it.length },
            translatedChars = cache.values.sumOf { it.length }
        )
    }

    /**
     * 缓存统计信息
     */
    data class CacheStats(
        val size: Int,           // 缓存条目数量
        val totalChars: Int,     // 原文总字符数
        val translatedChars: Int // 译文总字符数
    )
}