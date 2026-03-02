package me.ash.reader.infrastructure.translate.cache

import javax.inject.Inject
import javax.inject.Singleton
import me.ash.reader.infrastructure.translate.model.ArticleTranslationCache
import timber.log.Timber

@Singleton
class ArticleTranslationCacheService @Inject constructor(
    private val cacheDao: ArticleTranslationCacheDao,
) {
    companion object {
        private const val TAG = "ArticleTranslationCache"
    }

    suspend fun getCache(articleId: String): ArticleTranslationCache? {
        return cacheDao.getByArticleId(articleId)?.also {
            Timber.Forest.d("[$TAG] cache hit: articleId=$articleId")
        }
    }

    suspend fun saveCache(
        articleId: String,
        feedId: String,
        translatedTitle: String?,
        fullHtmlContent: String,
        provider: String,
        model: String,
    ) {
        val cache =
            ArticleTranslationCache(
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
        Timber.Forest.d("[$TAG] cache saved: articleId=$articleId")
    }

    suspend fun updateTitleOnly(
        articleId: String,
        translatedTitle: String,
        provider: String,
        model: String,
    ) {
        val existing = getCache(articleId) ?: run {
            Timber.Forest.d("[$TAG] skip title update, cache missing: articleId=$articleId")
            return
        }

        val cache =
            existing.copy(
                translatedTitle = translatedTitle,
                translateProvider = provider,
                translateModel = model,
                translatedAt = System.currentTimeMillis(),
            )
        cacheDao.insert(cache)
        Timber.Forest.d("[$TAG] title cache updated: articleId=$articleId")
    }

    suspend fun deleteCache(articleId: String) {
        cacheDao.deleteByArticleId(articleId)
        Timber.Forest.d("[$TAG] cache deleted: articleId=$articleId")
    }

    suspend fun deleteByArticleIds(articleIds: List<String>) {
        if (articleIds.isEmpty()) return
        cacheDao.deleteByArticleIds(articleIds)
        Timber.Forest.d("[$TAG] caches deleted by article ids: count=${articleIds.size}")
    }

    suspend fun deleteByAccountId(accountId: Int) {
        cacheDao.deleteByAccountId(accountId)
        Timber.Forest.d("[$TAG] caches deleted by account: accountId=$accountId")
    }
}
