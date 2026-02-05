package me.ash.reader.ui.page.home.flow

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import me.ash.reader.domain.data.Diff
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.ArticleWithFeed
import timber.log.Timber

@Suppress("FunctionName")
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.ArticleList(
    pagingItems: LazyPagingItems<ArticleFlowItem>,
    diffMap: Map<String, Diff>,
    isShowFeedIcon: Boolean,
    isShowStickyHeader: Boolean,
    articleListTonalElevation: Int,
    itemSpacing: Int = 0,
    isSwipeEnabled: () -> Boolean = { false },
    isMenuEnabled: Boolean = true,
    colorTheme: me.ash.reader.domain.model.theme.ColorTheme? = null,
    translatedTitleProvider: (ArticleWithFeed) -> String? = { it.article.translatedTitle }, // 2026-02-03: ??????
    onClick: (ArticleWithFeed, Int) -> Unit = { _, _ -> },
    onToggleStarred: (ArticleWithFeed) -> Unit = {},
    onToggleRead: (ArticleWithFeed) -> Unit = {},
    onMarkAboveAsRead: ((ArticleWithFeed) -> Unit)? = null,
    onMarkBelowAsRead: ((ArticleWithFeed) -> Unit)? = null,
    onShare: ((ArticleWithFeed) -> Unit)? = null,
    onSaveToLocal: ((ArticleWithFeed) -> Unit)? = null,
    isFirstItemLargeImageEnabled: Boolean = false, // 2026-01-27: 新增首行大图模式参数
    forceShowFeedName: Boolean = false, // 2026-01-29: 新增强制显示订阅源名称参数
) {
    val firstArticleIndex =
        pagingItems.itemSnapshotList.items.indexOfFirst { it is ArticleFlowItem.Article }
    // https://issuetracker.google.com/issues/193785330
    // FIXME: Using sticky header with paging-compose need to iterate through the entire list
    //  to figure out where to add sticky headers, which significantly impacts the performance
    if (!isShowStickyHeader) {
        items(
            count = pagingItems.itemCount,
            key = pagingItems.itemKey(::key),
            contentType = pagingItems.itemContentType(::contentType),
        ) { index ->
            when (val item = pagingItems[index]) {
                is ArticleFlowItem.Article -> {
                    val article = item.articleWithFeed.article
                    val hasImage = article.img != null
                    val translatedTitle = translatedTitleProvider(item.articleWithFeed)

                    if (index < 6) {
                        Timber.tag("TitleTranslate").d(
                            "ArticleList(item): idx=$index, articleId=${article.id}, feedId=${item.articleWithFeed.feed.id}, " +
                                "feedAuto=${item.articleWithFeed.feed.isAutoTranslateTitle}, hasTranslatedTitle=${article.translatedTitle != null}, " +
                                "showTranslated=${translatedTitle != null}"
                        )
                    }

                    // 2026-01-27: 判断是否应该显示大图模式
                    // 第一篇有图片的文章（index == 1，因为 index == 0 是 ArticleFlowItem.Date）
                    val shouldShowLargeImage =
                        isFirstItemLargeImageEnabled &&
                            firstArticleIndex >= 0 &&
                            index == firstArticleIndex &&
                            hasImage

                    if (shouldShowLargeImage) {
                        // 大图模式
                        LargeImageArticleItem(
                            modifier = Modifier.padding(horizontal = 1.dp, vertical = 1.dp),
                            articleWithFeed = item.articleWithFeed,
                            translatedTitle = translatedTitle,
                            onClick = { onClick(it, index) }
                        )
                    } else {
                        // 普通模式
                        SwipeableArticleItem(
                            articleWithFeed = item.articleWithFeed,
                            isUnread = diffMap[article.id]?.isUnread ?: article.isUnread,
                            translatedTitle = translatedTitle,
                            articleListTonalElevation = articleListTonalElevation,
                            colorTheme = colorTheme,
                            onClick = { onClick(it, index) },
                            isSwipeEnabled = isSwipeEnabled,
                            isMenuEnabled = isMenuEnabled,
                            onToggleStarred = onToggleStarred,
                            onToggleRead = onToggleRead,
                            onMarkAboveAsRead =
                                if (index == 1) null
                                else onMarkAboveAsRead, // index == 0 -> ArticleFlowItem.Date
                            onMarkBelowAsRead =
                                if (index == pagingItems.itemCount - 1) null else onMarkBelowAsRead,
                            onShare = onShare,
                            onSaveToLocal = onSaveToLocal,
                            forceShowFeedName = forceShowFeedName,
                        )
                    }
                    // 添加项间距
                    if (itemSpacing > 0 && index < pagingItems.itemCount - 1) {
                        val nextItem = pagingItems[index + 1]
                        if (nextItem !is ArticleFlowItem.Date) {
                            Spacer(modifier = Modifier.height(itemSpacing.dp))
                        }
                    }
                }

                is ArticleFlowItem.Date -> {
                    if (item.showSpacer) {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                    StickyHeader(item.date, isShowFeedIcon, articleListTonalElevation, colorTheme)
                }

                else -> {}
            }
        }
    } else {
        for (index in 0 until pagingItems.itemCount) {
            when (val item = pagingItems.peek(index)) {
                is ArticleFlowItem.Article -> {
                    item(key = key(item), contentType = contentType(item)) {
                        val article = item.articleWithFeed.article
                        val hasImage = article.img != null
                    val translatedTitle = translatedTitleProvider(item.articleWithFeed)

                        // 2026-01-27: 判断是否应该显示大图模式
                        // 第一篇有图片的文章（index == 1，因为 index == 0 是 ArticleFlowItem.Date）
                        val shouldShowLargeImage =
                            isFirstItemLargeImageEnabled &&
                                firstArticleIndex >= 0 &&
                                index == firstArticleIndex &&
                                hasImage

                        if (shouldShowLargeImage) {
                            // 大图模式
                            LargeImageArticleItem(
                                modifier = Modifier.padding(horizontal = 1.dp, vertical = 1.dp),
                                articleWithFeed = item.articleWithFeed,
                                onClick = { onClick(it, index) }
                            )
                        } else {
                            // 普通模式
                            SwipeableArticleItem(
                                articleWithFeed = item.articleWithFeed,
                                isUnread = diffMap[article.id]?.isUnread ?: article.isUnread,
                                articleListTonalElevation = articleListTonalElevation,
                                colorTheme = colorTheme,
                                onClick = { onClick(it, index) },
                                isSwipeEnabled = isSwipeEnabled,
                                isMenuEnabled = isMenuEnabled,
                                onToggleStarred = onToggleStarred,
                                onToggleRead = onToggleRead,
                                onMarkAboveAsRead =
                                    if (index == 1) null
                                    else onMarkAboveAsRead, // index == 0 -> ArticleFlowItem.Date
                                onMarkBelowAsRead =
                                    if (index == pagingItems.itemCount - 1) null else onMarkBelowAsRead,
                                onShare = onShare,
                                onSaveToLocal = onSaveToLocal,
                                forceShowFeedName = forceShowFeedName,
                            )
                        }
                        // 添加项间距
                        if (itemSpacing > 0 && index < pagingItems.itemCount - 1) {
                            val nextItem = pagingItems.peek(index + 1)
                            if (nextItem !is ArticleFlowItem.Date) {
                                Spacer(modifier = Modifier.height(itemSpacing.dp))
                            }
                        }
                    }
                }

                is ArticleFlowItem.Date -> {
                    if (item.showSpacer) {
                        item { Spacer(modifier = Modifier.height(32.dp)) }
                    }
                    stickyHeader(key = key(item), contentType = contentType(item)) {
                        StickyHeader(item.date, isShowFeedIcon, articleListTonalElevation, colorTheme)
                    }
                }

                else -> {}
            }
        }
    }
}

private fun key(item: ArticleFlowItem): String {
    return when (item) {
        is ArticleFlowItem.Article -> item.articleWithFeed.article.id
        is ArticleFlowItem.Date -> item.date
    }
}

private fun contentType(item: ArticleFlowItem): Int {
    return when (item) {
        is ArticleFlowItem.Article -> CONTENT_TYPE_ARTICLE
        is ArticleFlowItem.Date -> CONTENT_TYPE_DATE_HEADER
    }
}

const val CONTENT_TYPE_ARTICLE = 1
const val CONTENT_TYPE_DATE_HEADER = 2
