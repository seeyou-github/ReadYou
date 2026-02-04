package me.ash.reader.infrastructure.rss

import android.content.Context
import be.ceau.opml.OpmlParser
import be.ceau.opml.entity.Outline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.model.group.GroupWithFeed
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.ui.ext.extractDomain
import me.ash.reader.ui.ext.spacerDollar
import java.io.InputStream
import java.util.*
import javax.inject.Inject

class OPMLDataSource @Inject constructor(
    @ApplicationContext
    private val context: Context,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Throws(Exception::class)
    suspend fun parseFileInputStream(
        inputStream: InputStream,
        defaultGroup: Group,
        targetAccountId: Int,
    ): List<GroupWithFeed> {
        val opml = OpmlParser().parse(inputStream)
        val groupWithFeedList = mutableListOf<GroupWithFeed>().also {
            it.addGroup(defaultGroup)
        }

        for (outline in opml.body.outlines) {
            // Only feeds
            if (outline.subElements.isEmpty()) {
                // It's a empty group
                if (!outline.attributes.containsKey("xmlUrl")) {
                    if (!outline.isDefaultGroup()) {
                        groupWithFeedList.addGroup(
                            Group(
                                id = targetAccountId.spacerDollar(UUID.randomUUID().toString()),
                                name = outline.extractName(),
                                accountId = targetAccountId,
                            )
                        )
                    }
                } else {
                    groupWithFeedList.addFeedToDefault(
                        Feed(
                            id = targetAccountId.spacerDollar(UUID.randomUUID().toString()),
                            name = outline.extractName(),
                            url = outline.extractUrl() ?: continue,
                            icon = outline.extractIconBase64() ?: outline.extractIcon(),
                            groupId = defaultGroup.id,
                            accountId = targetAccountId,
                            isNotification = outline.extractPresetNotification(),
                            isFullContent = outline.extractPresetFullContent(),
                            isBrowser = outline.extractPresetBrowser(),
                            isAutoTranslate = outline.extractPresetAutoTranslate(),
                            isAutoTranslateTitle = outline.extractPresetAutoTranslateTitle(),
                            isImageFilterEnabled = outline.extractPresetImageFilterEnabled(),
                            isDisableReferer = outline.extractPresetDisableReferer(),
                            isDisableJavaScript = outline.extractPresetDisableJavaScript(),
                            imageFilterResolution = outline.extractPresetImageFilterResolution(),
                            imageFilterFileName = outline.extractPresetImageFilterFileName(),
                            imageFilterDomain = outline.extractPresetImageFilterDomain(),
                        )
                    )
                }
            } else {
                var groupId = defaultGroup.id
                if (!outline.isDefaultGroup()) {
                    groupId = targetAccountId.spacerDollar(UUID.randomUUID().toString())
                    groupWithFeedList.addGroup(
                        Group(
                            id = groupId,
                            name = outline.extractName(),
                            accountId = targetAccountId,
                        )
                    )
                }
                for (subOutline in outline.subElements) {
                    if (subOutline != null && subOutline.attributes != null) {
                        groupWithFeedList.addFeed(
                            Feed(
                                id = targetAccountId.spacerDollar(UUID.randomUUID().toString()),
                                name = subOutline.extractName(),
                                url = subOutline.extractUrl() ?: continue,
                                icon = subOutline.extractIconBase64() ?: subOutline.extractIcon(),
                                groupId = groupId,
                                accountId = targetAccountId,
                                isNotification = subOutline.extractPresetNotification(),
                                isFullContent = subOutline.extractPresetFullContent(),
                                isBrowser = subOutline.extractPresetBrowser(),
                                isAutoTranslate = subOutline.extractPresetAutoTranslate(),
                                isAutoTranslateTitle = subOutline.extractPresetAutoTranslateTitle(),
                                isImageFilterEnabled = subOutline.extractPresetImageFilterEnabled(),
                                isDisableReferer = subOutline.extractPresetDisableReferer(),
                                isDisableJavaScript = subOutline.extractPresetDisableJavaScript(),
                                imageFilterResolution = subOutline.extractPresetImageFilterResolution(),
                                imageFilterFileName = subOutline.extractPresetImageFilterFileName(),
                                imageFilterDomain = subOutline.extractPresetImageFilterDomain(),
                            )
                        )
                    }
                }
            }
        }
        return groupWithFeedList
    }

    private fun MutableList<GroupWithFeed>.addGroup(group: Group) {
        add(GroupWithFeed(group = group, feeds = mutableListOf()))
    }

    private fun MutableList<GroupWithFeed>.addFeed(feed: Feed) {
        last().feeds.add(feed)
    }

    private fun MutableList<GroupWithFeed>.addFeedToDefault(feed: Feed) {
        first().feeds.add(feed)
    }

    private fun Outline?.extractName(): String {
        if (this == null) return ""
        return attributes.getOrDefault("title", null)
            ?: text
            ?: attributes.getOrDefault("xmlUrl", null).extractDomain()
            ?: attributes.getOrDefault("htmlUrl", null).extractDomain()
            ?: attributes.getOrDefault("url", null).extractDomain()
            ?: ""
    }

    private fun Outline?.extractUrl(): String? {
        if (this == null) return null
        val url = attributes.getOrDefault("xmlUrl", null)
            ?: attributes.getOrDefault("url", null)
        return if (url.isNullOrBlank()) null else url
    }

    // 2026-01-21: 新增从 OPML 提取图标 URL 的方法
    // 修改目的：支持从 OPML 的 icon 属性获取 Feed 图标
    private fun Outline?.extractIcon(): String? {
        if (this == null) return null
        val icon = attributes.getOrDefault("icon", null)
        return if (icon.isNullOrBlank()) null else icon
    }

    // 2026-02-04: 优先从 OPML 的 iconBase64 属性获取 Feed 图标（data URI 或 base64）
    private fun Outline?.extractIconBase64(): String? {
        if (this == null) return null
        val iconBase64 = attributes.getOrDefault("iconBase64", null)
        return if (iconBase64.isNullOrBlank()) null else iconBase64
    }

    private fun Outline?.extractPresetNotification(): Boolean =
        this?.attributes?.getOrDefault("isNotification", null).toBoolean()

    private fun Outline?.extractPresetFullContent(): Boolean =
        this?.attributes?.getOrDefault("isFullContent", null).toBoolean()

    private fun Outline?.extractPresetBrowser(): Boolean =
        this?.attributes?.getOrDefault("isBrowser", null).toBoolean()

    private fun Outline?.extractPresetAutoTranslate(): Boolean =
        this?.attributes?.getOrDefault("isAutoTranslate", null).toBoolean()

    private fun Outline?.extractPresetAutoTranslateTitle(): Boolean =
        this?.attributes?.getOrDefault("isAutoTranslateTitle", null).toBoolean()

    private fun Outline?.extractPresetImageFilterEnabled(): Boolean =
        this?.attributes?.getOrDefault("isImageFilterEnabled", null).toBoolean()

    private fun Outline?.extractPresetDisableReferer(): Boolean =
        this?.attributes?.getOrDefault("isDisableReferer", null).toBoolean()

    private fun Outline?.extractPresetDisableJavaScript(): Boolean =
        this?.attributes?.getOrDefault("isDisableJavaScript", null).toBoolean()

    private fun Outline?.extractPresetImageFilterResolution(): String =
        this?.attributes?.getOrDefault("imageFilterResolution", null) ?: ""

    private fun Outline?.extractPresetImageFilterFileName(): String =
        this?.attributes?.getOrDefault("imageFilterFileName", null) ?: ""

    private fun Outline?.extractPresetImageFilterDomain(): String =
        this?.attributes?.getOrDefault("imageFilterDomain", null) ?: ""

    private fun Outline?.isDefaultGroup(): Boolean =
        this?.attributes?.getOrDefault("isDefault", null).toBoolean()
}
