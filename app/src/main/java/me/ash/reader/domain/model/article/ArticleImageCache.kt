package me.ash.reader.domain.model.article

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "article_image_cache",
    foreignKeys = [
        ForeignKey(
            entity = Article::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["articleId"]),
        Index(value = ["accountId"]),
        Index(value = ["articleId", "url", "type"], unique = true),
    ],
)
data class ArticleImageCache(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo
    val articleId: String,
    @ColumnInfo
    val accountId: Int,
    @ColumnInfo
    val url: String,
    @ColumnInfo
    val type: String,
    @ColumnInfo
    val localPath: String,
    @ColumnInfo
    val createdAt: Long = System.currentTimeMillis(),
)

object ArticleImageCacheType {
    const val TITLE = "title"
    const val CONTENT = "content"
}
