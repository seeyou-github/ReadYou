package me.ash.reader.domain.model.article

import androidx.room.*
import me.ash.reader.domain.model.feed.Feed
import java.util.*

/**
 * TODO: Add class description
 */
@Entity(
    tableName = "article",
    foreignKeys = [ForeignKey(
        entity = Feed::class,
        parentColumns = ["id"],
        childColumns = ["feedId"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE
    )]
)
data class Article(
    @PrimaryKey
    var id: String,
    @ColumnInfo
    var date: Date,
    @ColumnInfo
    var sourceTime: String? = null,
    @ColumnInfo
    var title: String,
    @ColumnInfo
    var author: String? = null,
    @ColumnInfo
    var rawDescription: String,
    @ColumnInfo
    var shortDescription: String,
    @ColumnInfo
    @Deprecated("fullContent is the same as rawDescription")
    var fullContent: String? = null,
    @ColumnInfo
    var img: String? = null,
    @ColumnInfo
    var link: String,
    @ColumnInfo(index = true)
    var feedId: String,
    @ColumnInfo(index = true)
    var accountId: Int,
    @ColumnInfo
    var isUnread: Boolean = true,
    @ColumnInfo
    var isStarred: Boolean = false,
    @ColumnInfo
    var isReadLater: Boolean = false,
    @ColumnInfo
    var isTranslated: Boolean? = null,  // 是否已翻译
    @ColumnInfo
    var updateAt: Date? = null,
    @ColumnInfo
    var translatedTitle: String? = null,  // 翻译后的标题
) {

    @Ignore
    var dateString: String? = null
}
