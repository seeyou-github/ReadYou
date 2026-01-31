package me.ash.reader.infrastructure.translate.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 文章翻译缓存
 * 
 * 存储已翻译文章的完整DOM和翻译状态
 */
@Entity(tableName = "article_translation_cache")
data class ArticleTranslationCache(
    @PrimaryKey
    val articleId: String,
    val feedId: String,
    val isTranslated: Boolean = false,  // 是否已翻译
    val translatedTitle: String? = null,  // 译文标题
    val fullHtmlContent: String? = null,  // 完整DOM（包含译文和原文）
    val translateProvider: String? = null,  // 使用的翻译提供商
    val translateModel: String? = null,  // 使用的翻译模型
    val translatedAt: Long = System.currentTimeMillis(),  // 翻译时间
)
