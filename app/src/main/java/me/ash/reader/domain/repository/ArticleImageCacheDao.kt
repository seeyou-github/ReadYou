package me.ash.reader.domain.repository

import androidx.room.Dao
import androidx.room.Query
import me.ash.reader.domain.model.article.ArticleImageCache

@Dao
interface ArticleImageCacheDao {

    @Query(
        """
        SELECT * FROM article_image_cache
        WHERE articleId = :articleId
        AND url = :url
        AND type = :type
        LIMIT 1
        """
    )
    suspend fun queryByArticleIdAndUrl(
        articleId: String,
        url: String,
        type: String,
    ): ArticleImageCache?

    @Query(
        """
        SELECT * FROM article_image_cache
        WHERE articleId = :articleId
        AND url = :url
        AND type = :type
        LIMIT 1
        """
    )
    fun queryByArticleIdAndUrlSync(
        articleId: String,
        url: String,
        type: String,
    ): ArticleImageCache?

    @Query(
        """
        SELECT * FROM article_image_cache
        WHERE articleId IN (:articleIds)
        """
    )
    suspend fun queryByArticleIds(articleIds: List<String>): List<ArticleImageCache>

    @Query(
        """
        INSERT OR REPLACE INTO article_image_cache (
            articleId,
            accountId,
            url,
            type,
            localPath,
            createdAt
        )
        SELECT
            :articleId,
            :accountId,
            :url,
            :type,
            :localPath,
            :createdAt
        WHERE EXISTS (
            SELECT 1 FROM article
            WHERE id = :articleId
            AND accountId = :accountId
        )
        """
    )
    suspend fun insertIfArticleExists(
        articleId: String,
        accountId: Int,
        url: String,
        type: String,
        localPath: String,
        createdAt: Long,
    )

    suspend fun insertIfArticleExists(cache: ArticleImageCache) {
        insertIfArticleExists(
            articleId = cache.articleId,
            accountId = cache.accountId,
            url = cache.url,
            type = cache.type,
            localPath = cache.localPath,
            createdAt = cache.createdAt,
        )
    }

    @Query("DELETE FROM article_image_cache WHERE articleId = :articleId")
    suspend fun deleteByArticleId(articleId: String)

    @Query("DELETE FROM article_image_cache WHERE articleId IN (:articleIds)")
    suspend fun deleteByArticleIds(articleIds: List<String>)

    @Query("DELETE FROM article_image_cache WHERE accountId = :accountId")
    suspend fun deleteByAccountId(accountId: Int)
}
