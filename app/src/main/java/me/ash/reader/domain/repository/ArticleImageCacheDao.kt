package me.ash.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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
        WHERE articleId IN (:articleIds)
        """
    )
    suspend fun queryByArticleIds(articleIds: List<String>): List<ArticleImageCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: ArticleImageCache)

    @Query("DELETE FROM article_image_cache WHERE articleId = :articleId")
    suspend fun deleteByArticleId(articleId: String)

    @Query("DELETE FROM article_image_cache WHERE articleId IN (:articleIds)")
    suspend fun deleteByArticleIds(articleIds: List<String>)

    @Query("DELETE FROM article_image_cache WHERE accountId = :accountId")
    suspend fun deleteByAccountId(accountId: Int)
}
