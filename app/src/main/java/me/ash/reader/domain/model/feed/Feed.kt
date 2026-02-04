package me.ash.reader.domain.model.feed

import androidx.room.*
import me.ash.reader.domain.model.group.Group

/**
 * TODO: Add class description
 */
@Entity(
    tableName = "feed",
    foreignKeys = [ForeignKey(
        entity = Group::class,
        parentColumns = ["id"],
        childColumns = ["groupId"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE,
    )],
)
data class Feed(
    @PrimaryKey
    val id: String,
    @ColumnInfo
    val name: String,
    @ColumnInfo
    val icon: String? = null,
    @ColumnInfo
    val url: String,
    @ColumnInfo(index = true)
    var groupId: String,
    @ColumnInfo(index = true)
    val accountId: Int,
    @ColumnInfo
    val isNotification: Boolean = false,
    @ColumnInfo
    val isFullContent: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val isBrowser: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val isAutoTranslate: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val isAutoTranslateTitle: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val isImageFilterEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val isDisableReferer: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val isDisableJavaScript: Boolean = false,
    @ColumnInfo(defaultValue = "")
    val imageFilterResolution: String = "",
    @ColumnInfo(defaultValue = "")
    val imageFilterFileName: String = "",
    @ColumnInfo(defaultValue = "")
    val imageFilterDomain: String = "",
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,
    @Ignore val important: Int = 0
) {
    constructor(
        id: String,
        name: String,
        icon: String?,
        url: String,
        groupId: String,
        accountId: Int,
        isNotification: Boolean,
        isFullContent: Boolean,
        isBrowser: Boolean,
        isAutoTranslate: Boolean = false,
        isAutoTranslateTitle: Boolean = false,
        isImageFilterEnabled: Boolean = false,
        isDisableReferer: Boolean = false,
        isDisableJavaScript: Boolean = false,
        imageFilterResolution: String = "",
        imageFilterFileName: String = "",
        imageFilterDomain: String = "",
        sortOrder: Int = 0
    ) : this(
        id = id,
        name = name,
        icon = icon,
        url = url,
        groupId = groupId,
        accountId = accountId,
        isNotification = isNotification,
        isFullContent = isFullContent,
        isBrowser = isBrowser,
        isAutoTranslate = isAutoTranslate,
        isAutoTranslateTitle = isAutoTranslateTitle,
        isImageFilterEnabled = isImageFilterEnabled,
        isDisableReferer = isDisableReferer,
        isDisableJavaScript = isDisableJavaScript,
        imageFilterResolution = imageFilterResolution,
        imageFilterFileName = imageFilterFileName,
        imageFilterDomain = imageFilterDomain,
        sortOrder = sortOrder,
        important = 0
    )
}
