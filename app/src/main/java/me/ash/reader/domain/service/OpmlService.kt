package me.ash.reader.domain.service

import android.content.Context
import be.ceau.opml.OpmlWriter
import be.ceau.opml.entity.Body
import be.ceau.opml.entity.Head
import be.ceau.opml.entity.Opml
import be.ceau.opml.entity.Outline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.rss.OPMLDataSource
import me.ash.reader.ui.ext.currentAccountId
import me.ash.reader.ui.ext.getDefaultGroupId
import java.io.InputStream
import java.util.*
import javax.inject.Inject
import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import coil.ImageLoader
import coil.request.ImageRequest
import java.io.ByteArrayInputStream
import java.net.URLConnection
import okio.FileSystem
import okio.buffer

/**
 * 鏀寔 OPML 鏂囦欢鐨勫鍏ュ拰瀵煎嚭銆?
 *
 * 璇ユ湇鍔℃彁渚涗互涓嬪姛鑳斤細
 * - 浠?OPML 鏂囦欢瀵煎叆璁㈤槄婧?
 * - 灏嗚闃呮簮瀵煎嚭涓?OPML 鏍煎紡
 * - 鏀寔瀵煎嚭鍗曚釜璁㈤槄婧愩€佸垎缁勬垨鏁翠釜璐︽埛
 */
class OpmlService @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val groupDao: GroupDao,
    private val feedDao: FeedDao,
    private val accountService: AccountService,
    private val rssService: RssService,
    private val OPMLDataSource: OPMLDataSource,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
    private val okHttpClient: OkHttpClient,
    private val imageLoader: ImageLoader,
) {

    /**
     * 瀵煎叆 OPML 鏂囦欢銆?
     *
     * 灏?OPML 鏂囦欢涓殑璁㈤槄婧愬鍏ュ埌鏁版嵁搴撱€?
     * 澶勭悊閫昏緫锛?
     * 1. 鑾峰彇褰撳墠璐︽埛鐨勯粯璁ゅ垎缁?
     * 2. 瑙ｆ瀽 OPML 鏂囦欢锛岃幏鍙栨墍鏈夊垎缁勫拰璁㈤槄婧?
     * 3. 閬嶅巻瑙ｆ瀽缁撴灉锛屽皢鏂板垎缁勬彃鍏ユ暟鎹簱
     * 4. 妫€鏌ラ噸澶嶈闃呮簮锛岄伩鍏嶉噸澶嶅鍏?
     * 5. 灏嗕笉閲嶅鐨勮闃呮簮鎻掑叆鏁版嵁搴?
     *
     * @param [inputStream] OPML 鏂囦欢鐨勮緭鍏ユ祦
     * @throws Exception 瑙ｆ瀽鎴栧鍏ヨ繃绋嬩腑鍙兘鎶涘嚭寮傚父
     */
    @Throws(Exception::class)
    suspend fun saveToDatabase(inputStream: InputStream) {
        withContext(ioDispatcher) {
            runCatching {
                val defaultGroup = groupDao.queryById(getDefaultGroupId(context.currentAccountId))!!
                val groupWithFeedList =
                    OPMLDataSource.parseFileInputStream(inputStream, defaultGroup, context.currentAccountId)
                groupWithFeedList.forEach { groupWithFeed ->
                    if (groupWithFeed.group != defaultGroup) {
                        val existingGroup = groupDao.queryByName(context.currentAccountId, groupWithFeed.group.name)
                        if (existingGroup == null) {
                            groupDao.insert(groupWithFeed.group)
                            groupWithFeed.feeds.forEach { feed ->
                                feed.groupId = groupWithFeed.group.id
                            }
                        } else {
                            groupWithFeed.feeds.forEach { feed ->
                                feed.groupId = existingGroup.id
                            }
                        }
                    }
                    val repeatList = mutableListOf<Feed>()
                    groupWithFeed.feeds.forEach {
                        if (rssService.get().isFeedExist(it.url)) {
                            repeatList.add(it)
                        }
                    }
                    feedDao.insertList((groupWithFeed.feeds subtract repeatList.toSet()).toList())
                }
            }.onFailure { th ->
                Log.e("OpmlService", "import OPML failed: ${th.message}")
            }
        }
    }

    /**
     * 瀵煎嚭 OPML 鏂囦欢銆?
     *
     * 灏嗗綋鍓嶈处鎴风殑鎵€鏈夎闃呮簮瀵煎嚭涓?OPML 鏍煎紡瀛楃涓层€?
     *
     * @param [accountId] 璐︽埛 ID
     * @param [attachInfo] 鏄惁闄勫姞棰濆淇℃伅锛堥€氱煡寮€鍏炽€佸叏鏂囬槄璇汇€佹祻瑙堝櫒鎵撳紑璁剧疆锛?
     * @return OPML 鏍煎紡鐨勫瓧绗︿覆
     * @throws Exception 瀵煎嚭杩囩▼涓彲鑳芥姏鍑哄紓甯?
     */
    @Throws(Exception::class)
    suspend fun saveToString(accountId: Int, attachInfo: Boolean): String {
        return withContext(ioDispatcher) {
            // ??????
            val defaultGroup = groupDao.queryById(getDefaultGroupId(accountId))
            // ?? OPML ????????
            OpmlWriter().write(
                Opml(
                    "2.0",
                    // ??????????????????
                    Head(
                        accountService.getCurrentAccount().name,
                        Date().toString(), null, null, null,
                        null, null, null, null,
                        null, null, null, null,
                    ),
                    // ?????????????????
                    Body(groupDao.queryAllGroupWithFeed(accountId).map {
                        // ??????
                        Outline(
                            mutableMapOf(
                                "text" to it.group.name,
                                "title" to it.group.name,
                            ).apply {
                                // ??????????????????
                                if (attachInfo) {
                                    put("isDefault", (it.group.id == defaultGroup?.id).toString())
                                }
                            },
                            // ??????????????
                            it.feeds.map { feed ->
                                buildFeedOutline(feed, attachInfo)
                            }
                        )
                    })
                )
            )!!
        }
    }

    /**
     * 鑾峰彇榛樿鍒嗙粍鐨?ID銆?
     *
     * @param [accountId] 璐︽埛 ID
     * @return 榛樿鍒嗙粍鐨?ID 瀛楃涓?
     */
    private fun getDefaultGroupId(accountId: Int): String = accountId.getDefaultGroupId()

    /**
     * 瀵煎嚭鍗曚釜璁㈤槄婧愪负 OPML銆?
     *
     * 灏嗘寚瀹氳闃呮簮鍙婂叾鎵€灞炲垎缁勫鍑轰负 OPML 鏍煎紡瀛楃涓层€?
     *
     * @param [feedId] 璁㈤槄婧?ID
     * @param [attachInfo] 鏄惁闄勫姞棰濆淇℃伅
     * @return OPML 鏍煎紡鐨勫瓧绗︿覆
     * @throws Exception 璁㈤槄婧愭垨鍒嗙粍涓嶅瓨鍦ㄦ椂鎶涘嚭寮傚父
     */
    @Throws(Exception::class)
    suspend fun saveSingleFeedToString(feedId: String, attachInfo: Boolean): String {
        return withContext(ioDispatcher) {
            // ?????ID?????
            val feed = feedDao.queryById(feedId) ?: throw Exception("??????")
            // ???????? ID ????
            val group = groupDao.queryById(feed.groupId) ?: throw Exception("?????")
            // ?? OPML ????????
            OpmlWriter().write(
                Opml(
                    "2.0",
                    Head(
                        accountService.getCurrentAccount().name,
                        Date().toString(), null, null, null,
                        null, null, null, null,
                        null, null, null, null,
                    ),
                    Body(listOf(
                        // ??????
                        Outline(
                            mutableMapOf(
                                "text" to group.name,
                                "title" to group.name,
                            ),
                            listOf(
                                // ???????
                                buildFeedOutline(feed, attachInfo)
                            )
                        )
                    ))
                )
            )!!
        }
    }

    /**
     * 瀵煎嚭鍒嗙粍鍐呮墍鏈夎闃呮簮涓?OPML銆?
     *
     * 灏嗘寚瀹氬垎缁勭殑鎵€鏈夎闃呮簮瀵煎嚭涓?OPML 鏍煎紡瀛楃涓层€?
     *
     * @param [groupId] 鍒嗙粍 ID
     * @param [attachInfo] 鏄惁闄勫姞棰濆淇℃伅
     * @return OPML 鏍煎紡鐨勫瓧绗︿覆
     * @throws Exception 鍒嗙粍涓嶅瓨鍦ㄦ椂鎶涘嚭寮傚父
     */
    @Throws(Exception::class)
    suspend fun saveGroupFeedsToString(groupId: String, attachInfo: Boolean): String {
        return withContext(ioDispatcher) {
            // ???? ID ????
            val group = groupDao.queryById(groupId) ?: throw Exception("?????")
            // ????????????
            val feeds = feedDao.queryByGroupId(group.accountId, groupId)
            // ?? OPML ????????
            OpmlWriter().write(
                Opml(
                    "2.0",
                    Head(
                        accountService.getCurrentAccount().name,
                        Date().toString(), null, null, null,
                        null, null, null, null,
                        null, null, null, null,
                    ),
                    Body(listOf(
                        // ??????
                        Outline(
                            mutableMapOf(
                                "text" to group.name,
                                "title" to group.name,
                            ),
                            // ????????????????
                            feeds.map { feed ->
                                buildFeedOutline(feed, attachInfo)
                            }
                        )
                    ))
                )
            )!!
        }
    }
    private fun buildFeedOutline(feed: Feed, attachInfo: Boolean): Outline {
        val iconUrl = feed.icon ?: ""
        val iconBase64 = fetchIconBase64(iconUrl)
        return Outline(
            mutableMapOf(
                "text" to feed.name,
                "title" to feed.name,
                "xmlUrl" to feed.url,
                "htmlUrl" to feed.url,
                "icon" to iconUrl,
            ).apply {
                if (!iconBase64.isNullOrBlank()) {
                    put("iconBase64", iconBase64)
                }
                if (attachInfo) {
                    put("isNotification", feed.isNotification.toString())
                    put("isFullContent", feed.isFullContent.toString())
                    put("isBrowser", feed.isBrowser.toString())
                    put("isAutoTranslate", feed.isAutoTranslate.toString())
                    put("isAutoTranslateTitle", feed.isAutoTranslateTitle.toString())
                    put("isImageFilterEnabled", feed.isImageFilterEnabled.toString())
                    put("isDisableReferer", feed.isDisableReferer.toString())
                    put("isDisableJavaScript", feed.isDisableJavaScript.toString())
                    put("imageFilterResolution", feed.imageFilterResolution)
                    put("imageFilterFileName", feed.imageFilterFileName)
                    put("imageFilterDomain", feed.imageFilterDomain)
                }
            },
            listOf()
        )
    }

    private fun fetchIconBase64(iconUrl: String?): String? {
        if (iconUrl.isNullOrBlank()) return null
        if (iconUrl.startsWith("data:", ignoreCase = true)) {
            return iconUrl
        }
        loadFromDiskCache(iconUrl)?.let { return it }
        return downloadAndEncode(iconUrl)
    }

    private fun loadFromDiskCache(iconUrl: String): String? {
        val request = ImageRequest.Builder(context).data(iconUrl).build()
        val key = request.diskCacheKey ?: return null
        val snapshot = imageLoader.diskCache?.openSnapshot(key) ?: return null
        snapshot.use {
            val path = it.data
            if (!FileSystem.SYSTEM.exists(path)) return null
            val bytes = FileSystem.SYSTEM.source(path).buffer().use { source ->
                source.readByteArray()
            }
            if (bytes.isEmpty()) return null
            return toDataUri(bytes)
        }
    }

    private fun downloadAndEncode(iconUrl: String): String? {
        return runCatching {
            val request = Request.Builder().url(iconUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                val bytes = body.bytes()
                if (bytes.isEmpty()) return@use null
                val mime = body.contentType()?.toString()?.substringBefore(";")
                toDataUri(bytes, mime)
            }
        }.getOrNull()
    }

    private fun toDataUri(bytes: ByteArray, mimeFromHeader: String? = null): String? {
        val mime = mimeFromHeader
            ?: runCatching { URLConnection.guessContentTypeFromStream(ByteArrayInputStream(bytes)) }
                .getOrNull()
            ?: "image/*"
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:$mime;base64,$base64"
    }
}


