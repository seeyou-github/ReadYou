package me.ash.reader.domain.model.blacklist

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * 2026-01-24: 关键词黑名单实体
 * 用于文章标题关键词过滤
 * 与账户无关，仅和订阅源相关
 * 支持多订阅源匹配（逗号分隔）
 */
@Entity(tableName = "blacklist_keyword")
data class BlacklistKeyword(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** 屏蔽关键词 */
    @SerializedName("keyword")
    val keyword: String,

    /** 是否启用：true 生效，false 不生效 */
    @SerializedName("enabled")
    val enabled: Boolean = true,

    /** 作用范围：null 或空字符串 表示匹配所有订阅源，指定 URL（逗号分隔）表示只匹配这些订阅源 */
    @SerializedName("feedUrls")
    val feedUrls: String? = null,

    /** 订阅源名称（用于UI显示，非数据库字段，逗号分隔） */
    @SerializedName("feedNames")
    val feedNames: String? = null,

    /** 创建时间戳 */
    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),
) {
    /**
     * 获取解析后的订阅源 URL 列表
     */
    fun getFeedUrlList(): List<String> {
        return feedUrls?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    }

    /**
     * 获取解析后的订阅源名称列表
     */
    fun getFeedNameList(): List<String> {
        return feedNames?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    }

    /**
     * 检查是否匹配给定订阅源
     */
    fun matchesFeed(feedUrl: String): Boolean {
        val urlList = getFeedUrlList()
        return urlList.isEmpty() || urlList.contains(feedUrl)
    }
}