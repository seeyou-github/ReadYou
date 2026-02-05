package me.ash.reader.infrastructure.rss

import android.content.Context
import android.util.Log
import com.rometools.modules.mediarss.MediaEntryModule
import com.rometools.modules.mediarss.MediaModule
import com.rometools.modules.mediarss.types.UrlReference
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.feed.synd.SyndImageImpl
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.Charset
import java.util.*
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.html.Readability
import me.ash.reader.ui.ext.currentAccountId
import me.ash.reader.ui.ext.decodeHTML
import me.ash.reader.ui.ext.extractDomain
import me.ash.reader.ui.ext.isFuture
import me.ash.reader.ui.ext.spacerDollar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.executeAsync
import okhttp3.internal.commonIsSuccessful
import okio.IOException
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream

val enclosureRegex = """<enclosure\s+url="([^"]+)"\s+type=".*"\s*/>""".toRegex()
val imgRegex = """img.*?src=(["'])((?!data).*?)\1""".toRegex(RegexOption.DOT_MATCHES_ALL)
private val imgTagRegex = """<img[^>]*>""".toRegex(RegexOption.IGNORE_CASE)
private val imgSrcRegex = """\bsrc=(["'])(.*?)\1""".toRegex(RegexOption.IGNORE_CASE)
private val imgWidthRegex = """\bwidth=(["']?)(\d+)\1""".toRegex(RegexOption.IGNORE_CASE)
private val imgHeightRegex = """\bheight=(["']?)(\d+)\1""".toRegex(RegexOption.IGNORE_CASE)

/** Some operations on RSS. */
class RssHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val okHttpClient: OkHttpClient,
) {

    @Throws(Exception::class)
    suspend fun searchFeed(feedLink: String): SyndFeed {
        return withContext(ioDispatcher) {
            val response = response(okHttpClient, feedLink)
            val contentType = response.header("Content-Type")
            val httpContentType =
                contentType?.let {
                    if (it.contains("charset=", ignoreCase = true)) it
                    else "$it; charset=UTF-8"
                } ?: "text/xml; charset=UTF-8"


            response.body.byteStream().use { inputStream ->
                    SyndFeedInput().build(XmlReader(inputStream, httpContentType)).also {
                    // ??????????????????????
                    it.icon = SyndImageImpl()
                    // it.icon.link = queryRssIconLink(feedLink)
                    // it.icon.url = it.icon.link
                }
            }
        }
    }

    @Throws(Exception::class)
    suspend fun parseFullContent(link: String, title: String): String {
        return withContext(ioDispatcher) {
            val response = response(okHttpClient, link)
            if (response.commonIsSuccessful) {
                val responseBody = response.body
                val charset = responseBody.contentType()?.charset()
                val content =
                    responseBody.source().use {
                        if (charset != null) {
                            return@use it.readString(charset)
                        }

                        val peekContent = it.peek().readString(Charsets.UTF_8)

                        val charsetFromMeta =
                            runCatching {
                                    val element =
                                        Jsoup.parse(peekContent, link)
                                            .selectFirst("meta[http-equiv=content-type]")
                                    return@runCatching if (element == null) Charsets.UTF_8
                                    else {
                                        element
                                            .attr("content")
                                            .substringAfter("charset=")
                                            .removeSurrounding("\"")
                                            .lowercase()
                                            .let { Charset.forName(it) }
                                    }
                                }
                                .getOrDefault(Charsets.UTF_8)

                        if (charsetFromMeta == Charsets.UTF_8) {
                            peekContent
                        } else {
                            it.readString(charsetFromMeta)
                        }
                    }

                val articleContent = Readability.parseToElement(content, link)
                articleContent?.let {
                    val h1Element = articleContent.selectFirst("h1")
                    if (h1Element != null && h1Element.hasText() && h1Element.text() == title) {
                        h1Element.remove()
                    }
                    // 修复正文中相对路径图片，统一转为绝对地址
                    articleContent.select("img").forEach { img ->
                        val raw =
                            img.attr("src")
                                .ifBlank { img.attr("data-src") }
                                .ifBlank { img.attr("data-original") }
                                .ifBlank { img.attr("srcset").substringBefore(" ").trim() }
                        val resolved = resolveUrl(link, raw)
                        if (resolved.isNotBlank()) {
                            img.attr("src", resolved)
                        }
                    }
                    articleContent.toString()
                } ?: throw IOException("articleContent is null")
            } else throw IOException(response.message)
        }
    }

    suspend fun queryRssXml(
        feed: Feed,
        latestLink: String?,
        preDate: Date = Date(),
    ): List<Article> =
        try {
            val accountId = context.currentAccountId
            val response = response(okHttpClient, feed.url)
            val contentType = response.header("Content-Type")

            val httpContentType =
                contentType?.let {
                    if (it.contains("charset=", ignoreCase = true)) it
                    else "$it; charset=UTF-8"
                } ?: "text/xml; charset=UTF-8"

            response.body.byteStream().use { inputStream ->
                SyndFeedInput()
                    .apply { isPreserveWireFeed = true }
                    .build(XmlReader(inputStream, httpContentType))
                    .entries
                    .asSequence()
                    .takeWhile { latestLink == null || latestLink != it.link }
                    .map { buildArticleFromSyndEntry(feed, accountId, it, preDate) }
                    .toList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("RLog", "queryRssXml[${feed.name}]: ${e.message}")
            runCatching { debugFetchRssRaw(feed.url, feed.name) }
            listOf()
        }

    fun buildArticleFromSyndEntry(
        feed: Feed,
        accountId: Int,
        syndEntry: SyndEntry,
        preDate: Date = Date(),
    ): Article {
        val desc = syndEntry.description?.value
        val content =
            syndEntry.contents
                .takeIf { it.isNotEmpty() }
                ?.let { it.joinToString("\n") { it.value } }
        //        Log.i(
        //            "RLog",
        //            "request rss:\n" +
        //                    "name: ${feed.name}\n" +
        //                    "feedUrl: ${feed.url}\n" +
        //                    "url: ${syndEntry.link}\n" +
        //                    "title: ${syndEntry.title}\n" +
        //                    "desc: ${desc}\n" +
        //                    "content: ${content}\n"
        //        )
        val baseUrl = syndEntry.link ?: feed.url
        val normalizedContent = normalizeHtmlImageUrls(content, baseUrl)
        val normalizedDesc = normalizeHtmlImageUrls(desc, baseUrl)
        return Article(
            id = accountId.spacerDollar(UUID.randomUUID().toString()),
            accountId = accountId,
            feedId = feed.id,
            date =
                (syndEntry.publishedDate ?: syndEntry.updatedDate)?.takeIf { !it.isFuture(preDate) }
                    ?: preDate,
            title = syndEntry.title.decodeHTML() ?: feed.name,
            author = syndEntry.author,
            rawDescription = normalizedContent ?: normalizedDesc ?: "",
            shortDescription =
                Readability.parseToText(normalizedDesc ?: normalizedContent, syndEntry.link).take(280),
            //            fullContent = content,
            img = run {
                val textThumbnail =
                    findThumbnailWithFilter(
                        normalizedContent ?: normalizedDesc,
                        feed,
                        baseUrl,
                    )
                val syndThumbnail = findThumbnail(syndEntry, baseUrl)
                if (feed.isImageFilterEnabled && shouldApplyImageFilter(feed) && syndThumbnail != null) {
                    val candidate = ImageCandidate(src = syndThumbnail)
                    if (shouldFilterImage(feed, candidate)) textThumbnail else syndThumbnail
                } else {
                    syndThumbnail ?: textThumbnail
                }
            },
            link = syndEntry.link ?: "",
            updateAt = preDate,
        )
    }

    fun findThumbnail(syndEntry: SyndEntry, baseUrl: String? = null): String? {
        val enclosure = syndEntry.enclosures?.firstOrNull()
        if (enclosure?.url != null) {
            val contentType = enclosure.type ?: ""
            if (contentType.startsWith("image/", ignoreCase = true)) {
                return resolveUrl(baseUrl, enclosure.url)
            }
        }

        val mediaModule = syndEntry.getModule(MediaModule.URI) as? MediaEntryModule
        if (mediaModule != null) {
            return findThumbnail(mediaModule, baseUrl)
        }

        return null
    }

    private fun findThumbnail(mediaModule: MediaEntryModule, baseUrl: String? = null): String? {
        val candidates =
            buildList {
                    add(mediaModule.metadata)
                    addAll(mediaModule.mediaGroups.map { mediaGroup -> mediaGroup.metadata })
                    addAll(mediaModule.mediaContents.map { content -> content.metadata })
                }
                .flatMap { it.thumbnail.toList() }

        val thumbnail = candidates.firstOrNull()

        if (thumbnail != null) {
            return resolveUrl(baseUrl, thumbnail.url.toString())
        } else {
            val imageMedia = mediaModule.mediaContents.firstOrNull { it.medium == "image" }
            if (imageMedia != null) {
                return resolveUrl(baseUrl, (imageMedia.reference as? UrlReference)?.url.toString())
            }
        }
        return null
    }

    data class ImageCandidate(
        val src: String,
        val width: Int? = null,
        val height: Int? = null,
    )

    fun findThumbnail(text: String?): String? {
        text ?: return null
        val enclosure = enclosureRegex.find(text)?.groupValues?.get(1)
        if (enclosure?.isNotBlank() == true) {
            return enclosure
        }
        // From https://gitlab.com/spacecowboy/Feeder
        // Using negative lookahead to skip data: urls, being inline base64
        // And capturing original quote to use as ending quote
        // Base64 encoded images can be quite large - and crash database cursors
        return imgRegex.find(text)?.groupValues?.get(2)?.takeIf { !it.startsWith("data:") }
    }

    fun findThumbnailWithFilter(text: String?, feed: Feed, baseUrl: String? = null): String? {
        text ?: return null
        val candidates = extractImageCandidates(text, baseUrl)
        if (candidates.isEmpty()) return null
        if (!feed.isImageFilterEnabled || !shouldApplyImageFilter(feed)) {
            return candidates.firstOrNull()?.src
        }
        return candidates.firstOrNull { !shouldFilterImage(feed, it) }?.src
    }

    fun removeImageFromContent(content: String, imageUrl: String): String {
        val normalizedTarget = normalizeUrl(imageUrl)
        return imgTagRegex.replace(content) { match ->
            val tag = match.value
            val src = imgSrcRegex.find(tag)?.groupValues?.get(2) ?: return@replace tag
            val normalizedSrc = normalizeUrl(src)
            if (normalizedSrc == normalizedTarget) "" else tag
        }
    }

    fun removeFilteredImages(content: String, feed: Feed): String {
        if (!feed.isImageFilterEnabled || !shouldApplyImageFilter(feed)) return content
        return imgTagRegex.replace(content) { match ->
            val tag = match.value
            val src = imgSrcRegex.find(tag)?.groupValues?.get(2) ?: return@replace tag
            if (src.startsWith("data:")) return@replace tag
            val width = imgWidthRegex.find(tag)?.groupValues?.get(2)?.toIntOrNull()
            val height = imgHeightRegex.find(tag)?.groupValues?.get(2)?.toIntOrNull()
            val candidate = ImageCandidate(src = src, width = width, height = height)
            if (shouldFilterImage(feed, candidate)) "" else tag
        }
    }

    private fun extractImageCandidates(text: String, baseUrl: String? = null): List<ImageCandidate> {
        return imgTagRegex.findAll(text).mapNotNull { match ->
            val tag = match.value
            val src = imgSrcRegex.find(tag)?.groupValues?.get(2) ?: return@mapNotNull null
            if (src.startsWith("data:")) return@mapNotNull null
            val resolved = resolveUrl(baseUrl, src)
            val width = imgWidthRegex.find(tag)?.groupValues?.get(2)?.toIntOrNull()
            val height = imgHeightRegex.find(tag)?.groupValues?.get(2)?.toIntOrNull()
            ImageCandidate(src = resolved, width = width, height = height)
        }.toList()
    }

    fun shouldApplyImageFilter(feed: Feed): Boolean {
        return feed.imageFilterResolution.isNotBlank() ||
            feed.imageFilterFileName.isNotBlank() ||
            feed.imageFilterDomain.isNotBlank()
    }

    fun shouldFilterImage(feed: Feed, candidate: ImageCandidate): Boolean {
        if (!feed.isImageFilterEnabled) return false
        val matchesResolution = matchesResolutionRule(feed.imageFilterResolution, candidate)
        val matchesFileName = matchesFileNameRule(feed.imageFilterFileName, candidate.src)
        val matchesDomain = matchesDomainRule(feed.imageFilterDomain, candidate.src)
        return matchesResolution || matchesFileName || matchesDomain
    }

    private fun matchesResolutionRule(rule: String, candidate: ImageCandidate): Boolean {
        if (rule.isBlank()) return false
        val (minWidth, minHeight) = parseResolution(rule) ?: return false
        val width = candidate.width ?: return false
        val height = candidate.height ?: return false
        return width < minWidth || height < minHeight
    }

    private fun matchesFileNameRule(rule: String, src: String): Boolean {
        if (rule.isBlank()) return false
        val fileName = src.substringBefore("?").substringAfterLast("/")
        return fileName.contains(rule, ignoreCase = true)
    }

    private fun matchesDomainRule(rule: String, src: String): Boolean {
        if (rule.isBlank()) return false
        val domain = src.extractDomain() ?: ""
        return domain.contains(rule, ignoreCase = true)
    }

    private fun parseResolution(value: String): Pair<Int, Int>? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        val normalized = trimmed.replace("×", "x").replace("*", "x")
        return if (normalized.contains("x", ignoreCase = true)) {
            val parts = normalized.lowercase().split("x").map { it.trim() }
            val w = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val h = parts.getOrNull(1)?.toIntOrNull() ?: return null
            w to h
        } else {
            val size = normalized.toIntOrNull() ?: return null
            size to size
        }
    }

    private fun normalizeUrl(url: String): String {
        return url.substringBefore("#").substringBefore("?")
    }

    private fun resolveUrl(baseUrl: String?, url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        if (baseUrl.isNullOrBlank()) return url
        return runCatching { java.net.URI(baseUrl).resolve(url).toString() }.getOrDefault(url)
    }

    private fun normalizeHtmlImageUrls(html: String?, baseUrl: String?): String? {
        if (html.isNullOrBlank() || baseUrl.isNullOrBlank()) return html
        return runCatching {
            val doc = Jsoup.parse(html, baseUrl)
            doc.select("img").forEach { img ->
                val raw =
                    img.attr("src")
                        .ifBlank { img.attr("data-src") }
                        .ifBlank { img.attr("data-original") }
                        .ifBlank { img.attr("srcset").substringBefore(" ").trim() }
                val resolved = resolveUrl(baseUrl, raw)
                if (resolved.isNotBlank()) {
                    img.attr("src", resolved)
                }
            }
            doc.body().html()
        }.getOrDefault(html)
    }

    suspend fun queryRssIconLink(feedLink: String?): String? {
        if (feedLink.isNullOrEmpty()) return null
        val iconFinder = BestIconFinder(okHttpClient)
        val domain = feedLink.extractDomain()
        return iconFinder.findBestIcon(domain ?: feedLink).also {
            Log.i("RLog", "queryRssIconByLink: get $it from $domain")
        }
    }

    suspend fun saveRssIcon(feedDao: FeedDao, feed: Feed, iconLink: String) {
        feedDao.update(feed.copy(icon = iconLink))
    }

    suspend fun debugFetchRssRaw(
        url: String,
        label: String? = null,
    ): String? =
        withContext(ioDispatcher) {
            val response = response(okHttpClient, url)
            val contentType = response.header("Content-Type")
            val httpContentType =
                contentType?.let {
                    if (it.contains("charset=", ignoreCase = true)) it
                    else "$it; charset=UTF-8"
                } ?: "text/xml; charset=UTF-8"
            val bytes = response.body?.bytes() ?: return@withContext null
            val charsetName =
                contentType?.substringAfter("charset=", "UTF-8")?.trim()?.ifBlank { "UTF-8" } ?: "UTF-8"
            val bodyString = bytes.toString(Charset.forName(charsetName))
            logLong("RLog", "rss raw[${label ?: url}]: $bodyString")
            runCatching {
                ByteArrayInputStream(bytes).use { inputStream ->
                    SyndFeedInput().build(XmlReader(inputStream, httpContentType))
                }
            }.onFailure { Log.e("RLog", "rss debug parse failed: ${it.message}") }
            bodyString
        }

    private fun logLong(tag: String, message: String) {
        val chunkSize = 3500
        if (message.length <= chunkSize) {
            Log.d(tag, message)
            return
        }
        var start = 0
        var index = 1
        while (start < message.length) {
            val end = (start + chunkSize).coerceAtMost(message.length)
            Log.d(tag, "part $index: ${message.substring(start, end)}")
            start = end
            index++
        }
    }

    private suspend fun response(client: OkHttpClient, url: String): okhttp3.Response =
        client.newCall(Request.Builder().url(url).build()).executeAsync()
}
