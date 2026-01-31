package me.ash.reader.infrastructure.translate.cache

import me.ash.reader.infrastructure.translate.model.ArticleTranslationCache
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 文章翻译缓存服务
 *
 * 管理已翻译文章的缓存存储和恢复
 */
@Singleton
class ArticleTranslationCacheService @Inject constructor(
    private val cacheDao: ArticleTranslationCacheDao
) {
    companion object {
        private const val TAG = "ArticleTranslationCache"
        private const val CACHE_EXPIRY_DAYS = 7 // 缓存有效期7天
    }

    /**
     * 获取文章的翻译缓存
     */
    suspend fun getCache(articleId: String): ArticleTranslationCache? {
        return cacheDao.getByArticleId(articleId)?.also { cache ->
            // 检查缓存是否过期
            val expiryTime = System.currentTimeMillis() - (CACHE_EXPIRY_DAYS * 24 * 60 * 60 * 1000)
            if (cache.translatedAt < expiryTime) {
                Timber.Forest.d("[$TAG] 缓存已过期，articleId=$articleId")
                deleteCache(articleId)
                return null
            }
            Timber.Forest.d("[$TAG] 找到有效缓存，articleId=$articleId")
        }
    }

    /**
     * 保存翻译缓存
     */
    suspend fun saveCache(
        articleId: String,
        feedId: String,
        translatedTitle: String?,
        fullHtmlContent: String,
        provider: String,
        model: String,
    ) {
        val cache = ArticleTranslationCache(
            articleId = articleId,
            feedId = feedId,
            isTranslated = true,
            translatedTitle = translatedTitle,
            fullHtmlContent = fullHtmlContent,
            translateProvider = provider,
            translateModel = model,
            translatedAt = System.currentTimeMillis(),
        )
        cacheDao.insert(cache)
        Timber.Forest.d("[$TAG] 保存缓存成功，articleId=$articleId")
    }

    /**
     * 仅更新标题译文，避免覆盖正文翻译缓存
     */
    suspend fun updateTitleOnly(
        articleId: String,
        translatedTitle: String,
        provider: String,
        model: String,
    ) {
        val existing = getCache(articleId) ?: run {
            Timber.Forest.d("[$TAG] 无正文翻译缓存，跳过标题缓存更新：articleId=$articleId")
            return
        }

        val cache = existing.copy(
            translatedTitle = translatedTitle,
            translateProvider = provider,
            translateModel = model,
            translatedAt = System.currentTimeMillis(),
        )
        cacheDao.insert(cache)
        Timber.Forest.d("[$TAG] 标题译文已更新，保留正文缓存：articleId=$articleId")
    }

    /**
     * 删除缓存
     */
    suspend fun deleteCache(articleId: String) {
        cacheDao.deleteByArticleId(articleId)
        Timber.Forest.d("[$TAG] 删除缓存，articleId=$articleId")
    }

    /**
     * 清理过期缓存
     */
    suspend fun cleanExpiredCache() {
        val expiryTime = System.currentTimeMillis() - (CACHE_EXPIRY_DAYS * 24 * 60 * 60 * 1000)
        cacheDao.deleteOlderThan(expiryTime)
        Timber.Forest.d("[$TAG] 清理过期缓存完成")
    }
}
