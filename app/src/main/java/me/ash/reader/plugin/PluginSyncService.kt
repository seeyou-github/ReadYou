package me.ash.reader.plugin

import android.util.Log
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.feed.FeedWithArticle
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.ui.ext.decodeHTML
import me.ash.reader.ui.ext.formatUrl
import me.ash.reader.ui.ext.spacerDollar
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Plugin rule sync service.
 * - Parse list page to get title/url/time/image
 * - Parse detail page to get content/media
 */
class PluginSyncService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val articleDao: ArticleDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun previewList(rule: PluginRule): Result<PreviewResult> {
        return withContext(ioDispatcher) {
            runCatching {
                Log.d(TAG, "preview list: url=${rule.subscribeUrl}")
                val listHtml = rule.listHtmlCache.takeIf { it.isNotBlank() } ?: fetchHtml(rule.subscribeUrl)
                if (listHtml.isBlank()) {
                    throw IOException("List HTML is empty")
                }
                val listDoc = Jsoup.parse(listHtml, rule.subscribeUrl)
                val items = parseListItems(listDoc, rule)
                val titles = items.map { it.title }.filter { it.isNotBlank() }
                PreviewResult(
                    total = items.size,
                    sampleTitles = titles.take(5),
                )
            }.onFailure {
                Log.e(TAG, "preview list failed: ${it.message}")
            }
        }
    }

    suspend fun previewListItems(rule: PluginRule): Result<List<ListItem>> {
        return withContext(ioDispatcher) {
            runCatching {
                Log.d(TAG, "preview list items: url=${rule.subscribeUrl}")
                val listHtml = rule.listHtmlCache.takeIf { it.isNotBlank() } ?: fetchHtml(rule.subscribeUrl)
                if (listHtml.isBlank()) {
                    throw IOException("List HTML is empty")
                }
                val listDoc = Jsoup.parse(listHtml, rule.subscribeUrl)
                val items = parseListItems(listDoc, rule)
                items.forEachIndexed { index, item ->
                    Log.d(TAG, "list item[$index]: title=${item.title} time=${item.time} image=${item.image}")
                }
                if (rule.listImageSelector.isNotBlank()) return@runCatching items
                if (rule.detailImageSelector.isBlank()) return@runCatching items

                // When list image selector is empty, fallback to first detail image (preview only).
                items.take(10).map { item ->
                    if (!item.image.isNullOrBlank()) return@map item
                    val detail = parseDetail(item, rule)
                    item.copy(image = detail.coverImage)
                }
            }.onFailure {
                Log.e(TAG, "preview list items failed: ${it.message}")
            }
        }
    }

    suspend fun previewDetail(rule: PluginRule): Result<DetailResult> {
        return withContext(ioDispatcher) {
            runCatching {
                Log.d(TAG, "preview detail: url=${rule.subscribeUrl}")
                val listHtml = rule.listHtmlCache.takeIf { it.isNotBlank() } ?: fetchHtml(rule.subscribeUrl)
                if (listHtml.isBlank()) {
                    throw IOException("List HTML is empty")
                }
                val listDoc = Jsoup.parse(listHtml, rule.subscribeUrl)
                val items = parseListItems(listDoc, rule)
                val first = items.firstOrNull() ?: return@runCatching DetailResult()
                val detail = parseDetail(first, rule)
                Log.d(TAG, "preview detail result: title=${detail.title} time=${detail.time} contentLen=${detail.contentHtml.length}")
                Log.d(TAG, "preview detail content sample=${detail.contentHtml.take(200)}")
                detail
            }.onFailure {
                Log.e(TAG, "preview detail failed: ${it.message}")
            }
        }
    }

    suspend fun downloadListHtml(url: String): Result<String> {
        return withContext(ioDispatcher) {
            runCatching { fetchHtml(url) }
        }
    }

    suspend fun debugSelectors(rule: PluginRule): Result<List<SelectorDebugItem>> {
        return withContext(ioDispatcher) {
            runCatching {
                val debugItems = mutableListOf<SelectorDebugItem>()
                val listHtml = rule.listHtmlCache.takeIf { it.isNotBlank() } ?: fetchHtml(rule.subscribeUrl)
                if (listHtml.isBlank()) {
                    return@runCatching listOf(
                        SelectorDebugItem(
                            label = "List page",
                            selector = "Subscribe URL",
                            count = 0,
                            samples = listOf("List HTML is empty"),
                        )
                    )
                }
                val listDoc = Jsoup.parse(listHtml, rule.subscribeUrl)

                debugItems += buildDebugItem(listDoc, "List title selector", rule.listTitleSelector, sampleAttr = null)
                debugItems += buildDebugItem(listDoc, "List url selector", rule.listUrlSelector, sampleAttr = "href")
                debugItems += buildDebugItem(listDoc, "List image selector", rule.listImageSelector, sampleAttr = "src")
                debugItems += buildDebugItem(listDoc, "List time selector", rule.listTimeSelector, sampleAttr = null)

                val firstItem = parseListItems(listDoc, rule).firstOrNull()
                if (firstItem == null) {
                    debugItems += SelectorDebugItem(
                        label = "Detail page",
                        selector = "Article URL",
                        count = 0,
                        samples = listOf("No article URL parsed from list"),
                    )
                    return@runCatching debugItems
                }
                val detailHtml = fetchHtml(firstItem.link)
                if (detailHtml.isBlank()) {
                    debugItems += SelectorDebugItem(
                        label = "Detail page",
                        selector = "Article URL",
                        count = 0,
                        samples = listOf("Detail HTML is empty"),
                    )
                    return@runCatching debugItems
                }
                val detailDoc = Jsoup.parse(detailHtml, firstItem.link)

                debugItems += buildDebugItem(detailDoc, "Detail title selector", rule.detailTitleSelector, sampleAttr = null)
                debugItems += buildDebugItem(detailDoc, "Detail author selector", rule.detailAuthorSelector, sampleAttr = null)
                debugItems += buildDebugItem(detailDoc, "Detail time selector", rule.detailTimeSelector, sampleAttr = null)

                val contentSelectors =
                    rule.detailContentSelectors
                        .split("||")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .ifEmpty { listOf(rule.detailContentSelector).filter { it.isNotBlank() } }
                if (contentSelectors.isEmpty()) {
                    debugItems += SelectorDebugItem(
                        label = "Detail content selector",
                        selector = "Not set",
                        count = 0,
                        samples = listOf("Detail content selector is empty"),
                    )
                } else {
                    contentSelectors.forEachIndexed { index, selector ->
                        debugItems += buildDebugItem(
                            detailDoc,
                            "Detail content selector ${index + 1}",
                            selector,
                            sampleAttr = null,
                            useOuterHtml = true,
                        )
                    }
                }

                debugItems += buildDebugItem(detailDoc, "Detail image selector", rule.detailImageSelector, sampleAttr = "src")
                debugItems += buildDebugItem(detailDoc, "Detail exclude selector", rule.detailExcludeSelector, sampleAttr = null)
                debugItems += buildDebugItem(detailDoc, "Detail video selector", rule.detailVideoSelector, sampleAttr = "src")
                debugItems += buildDebugItem(detailDoc, "Detail audio selector", rule.detailAudioSelector, sampleAttr = "src")

                debugItems.also { list ->
                    list.forEach {
                        Log.d(TAG, "debug ${it.label}: selector='${it.selector}', count=${it.count}, samples=${it.samples}")
                    }
                }
            }.onFailure {
                Log.e(TAG, "debug selectors failed: ${it.message}")
            }
        }
    }

    suspend fun syncByRule(feed: Feed, rule: PluginRule, preDate: Date = Date()): FeedWithArticle {
        return withContext(ioDispatcher) {
            Log.d(TAG, "sync start: rule=${rule.id} url=${rule.subscribeUrl}")
            val listHtml = fetchHtml(rule.subscribeUrl)
            if (listHtml.isBlank()) {
                Log.e(TAG, "list html empty: ${rule.subscribeUrl}")
                return@withContext FeedWithArticle(feed = feed, articles = emptyList())
            }
            val listDoc = Jsoup.parse(listHtml, rule.subscribeUrl)
            val items = parseListItems(listDoc, rule)
            if (items.isEmpty()) {
                Log.w(TAG, "list items empty: ${rule.subscribeUrl}")
                return@withContext FeedWithArticle(feed = feed, articles = emptyList())
            }

            // Dedupe by link
            val existingLinks =
                articleDao.queryArticlesByLinks(
                    linkList = items.map { it.link },
                    feedId = feed.id,
                    accountId = feed.accountId,
                ).map { it.link }.toSet()
            val newItems = items.filterNot { existingLinks.contains(it.link) }
            Log.d(TAG, "list items=${items.size}, newItems=${newItems.size}")

            val articles =
                newItems.mapIndexedNotNull { index, item ->
                    runCatching {
                        buildArticle(feed, rule, item, preDate, index)
                    }.onFailure {
                        Log.e(TAG, "build article failed: ${item.link} ${it.message}")
                    }.getOrNull()
                }
            Log.d(TAG, "sync end: newArticles=${articles.size}")
            FeedWithArticle(feed = feed, articles = articles)
        }
    }

    private fun parseListItems(doc: Document, rule: PluginRule): List<ListItem> {
        if (rule.listTitleSelector.isBlank() || rule.listUrlSelector.isBlank()) {
            Log.w(TAG, "list selector missing: title='${rule.listTitleSelector}' url='${rule.listUrlSelector}'")
            return emptyList()
        }
        val titleElements = doc.select(rule.listTitleSelector)
        val urlElements = doc.select(rule.listUrlSelector)
        val imageElements = rule.listImageSelector.takeIf { it.isNotBlank() }?.let { doc.select(it) }
        val timeElements = rule.listTimeSelector.takeIf { it.isNotBlank() }?.let { doc.select(it) }

        Log.d(TAG, "list title count=${titleElements.size}, url count=${urlElements.size}")
        val count = minOf(titleElements.size, urlElements.size)
        if (count == 0) return emptyList()

        return (0 until count).mapNotNull { index ->
            val title = titleElements.getOrNull(index)?.let { pickText(it) } ?: ""
            val link = urlElements.getOrNull(index)?.let { pickUrl(it, "href") } ?: ""
            if (link.isBlank()) {
                Log.w(TAG, "list item link empty at $index")
                return@mapNotNull null
            }
            val titleElement = titleElements.getOrNull(index)
            val urlElement = urlElements.getOrNull(index)
            val image =
                if (rule.listImageSelector.isNotBlank()) {
                    val container = findContainer(titleElement, urlElement, rule.listImageSelector)
                    val fromContainer = container?.select(rule.listImageSelector)?.firstOrNull()
                    val picked = fromContainer?.let { pickUrl(it, "src") }.orEmpty()
                    if (picked.isNotBlank()) picked else ""
                } else {
                    imageElements?.getOrNull(index)?.let { pickUrl(it, "src") }.orEmpty()
                }.takeIf { it.isNotBlank() }
            val time =
                if (rule.listTimeSelector.isNotBlank()) {
                    val container = findContainer(titleElement, urlElement, rule.listTimeSelector)
                    val fromContainer = container?.select(rule.listTimeSelector)?.firstOrNull()
                    val picked = fromContainer?.let { pickText(it) }.orEmpty()
                    if (picked.isNotBlank()) picked
                    else timeElements?.getOrNull(index)?.let { pickText(it) }.orEmpty()
                } else {
                    timeElements?.getOrNull(index)?.let { pickText(it) }.orEmpty()
                }

            ListItem(
                title = title.ifBlank { link },
                link = link,
                image = image,
                time = time,
            )
        }.distinctBy { it.link }
    }

    private fun parseDetail(item: ListItem, rule: PluginRule): DetailResult {
        if (rule.detailContentSelector.isBlank() && rule.detailContentSelectors.isBlank()) {
            Log.w(TAG, "detail content selector missing")
            return DetailResult()
        }
        val html = fetchHtml(item.link)
        if (html.isBlank()) {
            Log.w(TAG, "detail html empty: ${item.link}")
            return DetailResult()
        }
        val doc = Jsoup.parse(html, item.link)

        val title = selectFirstText(doc, rule.detailTitleSelector)
        val author = selectFirstText(doc, rule.detailAuthorSelector)
        val time = selectFirstText(doc, rule.detailTimeSelector)
        val contentHtml = selectContentHtml(doc, rule)

        val images = selectMediaHtml(doc, rule.detailImageSelector, "img", "src")
        val videos = selectMediaHtml(doc, rule.detailVideoSelector, "video", "src")
        val audios = selectMediaHtml(doc, rule.detailAudioSelector, "audio", "src")

        return DetailResult(
            title = title,
            author = author,
            time = time,
            contentHtml = mergeContent(contentHtml, images, videos, audios),
            coverImage = images.firstOrNull(),
        )
    }

    private fun buildArticle(
        feed: Feed,
        rule: PluginRule,
        item: ListItem,
        preDate: Date,
        orderIndex: Int,
    ): Article {
        val contentHtml = ""
        val plainText = ""
        val sortDate = Date(preDate.time - orderIndex.toLong())

        return Article(
            id = feed.accountId.spacerDollar(UUID.randomUUID().toString()),
            accountId = feed.accountId,
            feedId = feed.id,
            date = sortDate,
            sourceTime = item.time,
            title = item.title.decodeHTML() ?: item.title,
            author = null,
            rawDescription = contentHtml,
            shortDescription = plainText.take(280),
            img = item.image,
            link = item.link,
            updateAt = preDate,
        )
    }

    suspend fun fetchDetail(rule: PluginRule, link: String): Result<DetailResult> {
        return withContext(ioDispatcher) {
            runCatching {
                parseDetail(ListItem(title = "", link = link, image = null, time = null), rule)
            }.onFailure {
                Log.e(TAG, "fetch detail failed: ${it.message}")
            }
        }
    }

    private fun fetchHtml(url: String): String {
        return runCatching {
            val request = Request.Builder().url(url.formatUrl()).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("http ${response.code}")
                response.body?.string().orEmpty()
            }
        }.onFailure {
            Log.e(TAG, "fetch html failed: $url ${it.message}")
        }.getOrDefault("")
    }

    private fun pickText(element: Element): String {
        return element.text()
            .ifBlank { element.attr("title") }
            .ifBlank { element.attr("datetime") }
            .trim()
    }

    private fun pickUrl(element: Element, attr: String): String {
        val raw =
            element.attr(attr).ifBlank {
                element.attr("data-src")
                    .ifBlank { element.attr("data-original") }
                    .ifBlank { element.attr("srcset").substringBefore(" ").trim() }
            }.trim()
        val url =
            when {
                raw.isNotBlank() -> {
                    if (raw.startsWith("http://") || raw.startsWith("https://")) raw
                    else if (raw.startsWith("//")) "https:$raw"
                    else element.absUrl(attr).ifBlank { raw }
                }
                else -> element.absUrl(attr)
            }
        return url.trim()
    }

    private fun selectFirstText(doc: Document, selector: String): String? {
        if (selector.isBlank()) return null
        return doc.selectFirst(selector)?.let { pickText(it) }?.takeIf { it.isNotBlank() }
    }

    private fun selectContentHtml(doc: Document, rule: PluginRule): String {
        val selectors =
            rule.detailContentSelectors
                .split("||")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .ifEmpty { listOf(rule.detailContentSelector).filter { it.isNotBlank() } }
        if (selectors.isEmpty()) return ""
        return selectors.joinToString(separator = "") { selector ->
            val element = doc.selectFirst(selector) ?: return@joinToString ""
            if (rule.detailExcludeSelector.isNotBlank()) {
                element.select(rule.detailExcludeSelector).forEach { it.remove() }
            }
            element.outerHtml()
        }
    }

    private fun selectMediaHtml(
        doc: Document,
        selector: String,
        tagName: String,
        attr: String,
    ): List<String> {
        if (selector.isBlank()) return emptyList()
        return doc.select(selector).mapNotNull { element ->
            val src = pickUrl(element, attr)
            if (src.isBlank()) return@mapNotNull null
            val controls = if (tagName == "video" || tagName == "audio") " controls" else ""
            "<$tagName$controls src=\"$src\"></$tagName>"
        }
    }

    private fun mergeContent(content: String, images: List<String>, videos: List<String>, audios: List<String>): String {
        val builder = StringBuilder(content)
        if (images.isNotEmpty()) {
            builder.append("<div>").append(images.joinToString("")).append("</div>")
        }
        if (videos.isNotEmpty()) {
            builder.append("<div>").append(videos.joinToString("")).append("</div>")
        }
        if (audios.isNotEmpty()) {
            builder.append("<div>").append(audios.joinToString("")).append("</div>")
        }
        return builder.toString()
    }

    private fun parseDateOrNull(text: String?, base: Date): Date? {
        if (text.isNullOrBlank()) return null
        val patterns = arrayOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd",
            "yyyy.MM.dd HH:mm:ss",
            "yyyy.MM.dd",
            "yyyy年MM月dd日 HH:mm",
            "yyyy年MM月dd日",
            "MM月dd日 HH:mm",
            "MM月dd日",
            "MM-dd HH:mm",
        )
        val df = SimpleDateFormat()
        for (pattern in patterns) {
            df.applyPattern(pattern)
            df.isLenient = true
            val date = df.parse(text.trim(), ParsePosition(0))
            if (date != null) return date
        }
        // Relative time
        val trimmed = text.trim()
        val now = base.time
        if (trimmed.contains("刚刚")) return Date(now)
        val minuteMatch = Regex("(\\d+)\\s*分钟").find(trimmed)
        if (minuteMatch != null) {
            val minutes = minuteMatch.groupValues[1].toLongOrNull() ?: return null
            return Date(now - minutes * 60_000)
        }
        val hourMatch = Regex("(\\d+)\\s*小时").find(trimmed)
        if (hourMatch != null) {
            val hours = hourMatch.groupValues[1].toLongOrNull() ?: return null
            return Date(now - hours * 3_600_000)
        }
        val dayMatch = Regex("(\\d+)\\s*天").find(trimmed)
        if (dayMatch != null) {
            val days = dayMatch.groupValues[1].toLongOrNull() ?: return null
            return Date(now - days * 86_400_000)
        }
        if (trimmed.contains("昨天")) {
            return Date(now - 86_400_000)
        }
        if (trimmed.contains("前天")) {
            return Date(now - 2 * 86_400_000)
        }
        Log.w(TAG, "parse date failed: $text")
        return null
    }

    private fun findContainer(titleElement: Element?, urlElement: Element?, selector: String): Element? {
        if (selector.isBlank()) return null
        return titleElement?.parents()?.firstOrNull { it.select(selector).isNotEmpty() }
            ?: urlElement?.parents()?.firstOrNull { it.select(selector).isNotEmpty() }
    }

    data class ListItem(
        val title: String,
        val link: String,
        val image: String?,
        val time: String?,
    )

    data class DetailResult(
        val title: String? = null,
        val author: String? = null,
        val time: String? = null,
        val contentHtml: String = "",
        val coverImage: String? = null,
    )

    data class PreviewResult(
        val total: Int,
        val sampleTitles: List<String>,
    )

    data class SelectorDebugItem(
        val label: String,
        val selector: String,
        val count: Int,
        val samples: List<String>,
    )

    private fun buildDebugItem(
        doc: Document,
        label: String,
        selector: String,
        sampleAttr: String?,
        useOuterHtml: Boolean = false,
    ): SelectorDebugItem {
        if (selector.isBlank()) {
            return SelectorDebugItem(label, selector, 0, listOf("Selector is empty"))
        }
        val elements = doc.select(selector)
        val samples = elements.take(3).map { element ->
            val raw =
                when {
                    !sampleAttr.isNullOrBlank() -> pickUrl(element, sampleAttr)
                    useOuterHtml -> element.outerHtml()
                    else -> element.text()
                }
            raw.take(200)
        }
        return SelectorDebugItem(
            label = label,
            selector = selector,
            count = elements.size,
            samples = if (samples.isEmpty()) listOf("No match") else samples,
        )
    }

    companion object {
        private const val TAG = "PluginSync"
    }
}
