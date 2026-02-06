package me.ash.reader.plugin

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 插件规则：用于解析非 RSS 的网页列表与正文。
 * 说明：
 * 1. 所有选择器均为 Jsoup CSS 选择器（暂不支持 JS 执行）
 * 2. 必填项：订阅 URL、列表标题选择器、列表文章 URL 选择器、正文内容选择器
 */
@Entity(tableName = "plugin_rule")
data class PluginRule(
    @PrimaryKey
    val id: String,
    @ColumnInfo(index = true)
    val accountId: Int,
    @ColumnInfo
    val name: String,
    @ColumnInfo
    val subscribeUrl: String,
    @ColumnInfo(defaultValue = "")
    val groupId: String = "",
    @ColumnInfo
    val icon: String = "",
    @ColumnInfo
    val listHtmlCache: String = "",
    // 列表解析
    @ColumnInfo
    val listTitleSelector: String,
    @ColumnInfo
    val listUrlSelector: String,
    @ColumnInfo
    val listImageSelector: String = "",
    @ColumnInfo
    val listTimeSelector: String = "",
    @ColumnInfo
    val listJsonArraySelector: String = "",
    @ColumnInfo
    val listJsonTitleSelector: String = "",
    @ColumnInfo
    val listJsonUrlSelector: String = "",
    @ColumnInfo
    val listJsonImageSelector: String = "",
    @ColumnInfo
    val listJsonTimeSelector: String = "",
    // 正文解析
    @ColumnInfo
    val detailTitleSelector: String = "",
    @ColumnInfo
    val detailAuthorSelector: String = "",
    @ColumnInfo
    val detailTimeSelector: String = "",
    @ColumnInfo
    val detailContentSelector: String,
    @ColumnInfo
    val detailContentSelectors: String = "",
    @ColumnInfo
    val detailExcludeSelector: String = "",
    @ColumnInfo
    val detailImageSelector: String = "",
    @ColumnInfo
    val detailVideoSelector: String = "",
    @ColumnInfo
    val detailAudioSelector: String = "",
    @ColumnInfo(defaultValue = "0")
    val cacheContentOnUpdate: Boolean = false,
    // 是否显示在主页（启用则创建 Feed）
    @ColumnInfo(defaultValue = "0")
    val isEnabled: Boolean = false,
    @ColumnInfo
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo
    val updatedAt: Long = System.currentTimeMillis(),
)
