package me.ash.reader.infrastructure.translate.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.ash.reader.infrastructure.translate.model.ArticleTranslationCache

/**
 * 文章翻译缓存 DAO
 */
@Dao
interface ArticleTranslationCacheDao {

    @Query("SELECT * FROM article_translation_cache WHERE articleId = :articleId")
    suspend fun getByArticleId(articleId: String): ArticleTranslationCache?

    @Query("SELECT * FROM article_translation_cache WHERE articleId = :articleId")
    fun getByArticleIdFlow(articleId: String): Flow<ArticleTranslationCache?>

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insert(cache: ArticleTranslationCache)

    @Query("DELETE FROM article_translation_cache WHERE articleId = :articleId")
    suspend fun deleteByArticleId(articleId: String)

    @Query("DELETE FROM article_translation_cache WHERE translatedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("SELECT COUNT(*) FROM article_translation_cache")
    suspend fun getCount(): Int
}