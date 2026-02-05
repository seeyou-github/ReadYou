package me.ash.reader.domain.model.article

import androidx.paging.PagingData
import androidx.paging.insertSeparators
import androidx.paging.map
import java.text.SimpleDateFormat
import java.util.Locale
import me.ash.reader.infrastructure.android.AndroidStringsHelper
import me.ash.reader.plugin.PluginConstants
import me.ash.reader.ui.ext.formatAsRelativeTime

/**
 * Provide paginated and inserted separator data types for article list view.
 *
 * @see me.ash.reader.ui.page.home.flow.ArticleList
 */
sealed class ArticleFlowItem {

    /**
     * The [Article] item.
     *
     * @see me.ash.reader.ui.page.home.flow.ArticleItem
     */
    class Article(val articleWithFeed: ArticleWithFeed) : ArticleFlowItem()

    /**
     * The feed publication date separator between [Article] items.
     *
     * @see me.ash.reader.ui.page.home.flow.StickyHeader
     */
    class Date(val date: String, val showSpacer: Boolean) : ArticleFlowItem()
}

/**
 * Mapping [ArticleWithFeed] list to [ArticleFlowItem] list.
 */
fun PagingData<ArticleWithFeed>.mapPagingFlowItem(androidStringsHelper: AndroidStringsHelper): PagingData<ArticleFlowItem> =
    map {
        ArticleFlowItem.Article(it.apply {
            val isLocalRule = feed.url.startsWith(PluginConstants.PLUGIN_URL_PREFIX)
            article.dateString =
                if (isLocalRule && !article.sourceTime.isNullOrBlank()) {
                    article.sourceTime
                } else {
                    article.date.formatAsRelativeTime()
                }
        })
    }.insertSeparators { before, after ->
        val dateFormat = SimpleDateFormat("M月d日", Locale.getDefault())
        val beforeDate =
            before?.articleWithFeed?.article?.date?.let { date ->
                if (before.articleWithFeed.feed.url.startsWith(PluginConstants.PLUGIN_URL_PREFIX)) {
                    dateFormat.format(date)
                } else {
                    androidStringsHelper.formatAsString(date)
                }
            }
        val afterDate =
            after?.articleWithFeed?.article?.date?.let { date ->
                if (after.articleWithFeed.feed.url.startsWith(PluginConstants.PLUGIN_URL_PREFIX)) {
                    dateFormat.format(date)
                } else {
                    androidStringsHelper.formatAsString(date)
                }
            }
        if (beforeDate != afterDate) {
            afterDate?.let { ArticleFlowItem.Date(it, beforeDate != null) }
        } else {
            null
        }
    }
