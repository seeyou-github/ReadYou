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

/**
 * 支持 OPML 文件的导入和导出。
 *
 * 该服务提供以下功能：
 * - 从 OPML 文件导入订阅源
 * - 将订阅源导出为 OPML 格式
 * - 支持导出单个订阅源、分组或整个账户
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
) {

    /**
     * 导入 OPML 文件。
     *
     * 将 OPML 文件中的订阅源导入到数据库。
     * 处理逻辑：
     * 1. 获取当前账户的默认分组
     * 2. 解析 OPML 文件，获取所有分组和订阅源
     * 3. 遍历解析结果，将新分组插入数据库
     * 4. 检查重复订阅源，避免重复导入
     * 5. 将不重复的订阅源插入数据库
     *
     * @param [inputStream] OPML 文件的输入流
     * @throws Exception 解析或导入过程中可能抛出异常
     */
    @Throws(Exception::class)
    suspend fun saveToDatabase(inputStream: InputStream) {
        withContext(ioDispatcher) {
            // 获取当前账户的默认分组
            val defaultGroup = groupDao.queryById(getDefaultGroupId(context.currentAccountId))!!
            // 解析 OPML 文件，返回分组和订阅源列表
            val groupWithFeedList =
                OPMLDataSource.parseFileInputStream(inputStream, defaultGroup, context.currentAccountId)
            // 遍历每个分组及其订阅源
            groupWithFeedList.forEach { groupWithFeed ->
                // 如果不是默认分组，检查是否已存在同名分组
                if (groupWithFeed.group != defaultGroup) {
                    // 查询数据库中是否已存在同名分组
                    val existingGroup = groupDao.queryByName(context.currentAccountId, groupWithFeed.group.name)
                    if (existingGroup == null) {
                        // 不存在，插入新分组
                        groupDao.insert(groupWithFeed.group)
                    } else {
                        // 存在，使用现有分组ID更新订阅源的分组
                        groupWithFeed.feeds.forEach { feed ->
                            feed.groupId = existingGroup.id
                        }
                    }
                }
                // 重新计算重复列表，因为可能有订阅源的分组ID已被更新
                val repeatList = mutableListOf<Feed>()
                // 遍历该分组下的所有订阅源
                groupWithFeed.feeds.forEach {
                    // 设置订阅源的分组 ID
                    it.groupId = groupWithFeed.group.id
                    // 判断该订阅源 URL 是否已存在
                    if (rssService.get().isFeedExist(it.url)) {
                        // 已存在，添加到重复列表
                        repeatList.add(it)
                    }
                }
                // 插入不重复的订阅源到数据库（排除重复项）
                feedDao.insertList((groupWithFeed.feeds subtract repeatList.toSet()).toList())
            }
        }
    }

    /**
     * 导出 OPML 文件。
     *
     * 将当前账户的所有订阅源导出为 OPML 格式字符串。
     *
     * @param [accountId] 账户 ID
     * @param [attachInfo] 是否附加额外信息（通知开关、全文阅读、浏览器打开设置）
     * @return OPML 格式的字符串
     * @throws Exception 导出过程中可能抛出异常
     */
    @Throws(Exception::class)
    suspend fun saveToString(accountId: Int, attachInfo: Boolean): String {
        // 获取默认分组
        val defaultGroup = groupDao.queryById(getDefaultGroupId(accountId))
        // 构建 OPML 文档并返回字符串
        return OpmlWriter().write(
            Opml(
                "2.0",
                // 构建头部信息，使用账户名称和当前日期
                Head(
                    accountService.getCurrentAccount().name,
                    Date().toString(), null, null, null,
                    null, null, null, null,
                    null, null, null, null,
                ),
                // 构建主体部分，包含所有分组和订阅源
                Body(groupDao.queryAllGroupWithFeed(accountId).map {
                    // 创建分组节点
                    Outline(
                        mutableMapOf(
                            "text" to it.group.name,
                            "title" to it.group.name,
                        ).apply {
                            // 如果需要附加信息，判断是否为默认分组
                            if (attachInfo) {
                                put("isDefault", (it.group.id == defaultGroup?.id).toString())
                            }
                        },
                        // 构建该分组下的所有订阅源节点
                        it.feeds.map { feed ->
                            Outline(
                                mutableMapOf(
                                    "text" to feed.name,
                                    "title" to feed.name,
                                    "xmlUrl" to feed.url,
                                    "htmlUrl" to feed.url,
                                    "icon" to (feed.icon ?: "")
                                ).apply {
                                    // 如果需要附加信息，添加订阅源设置
                                    if (attachInfo) {
                                        put("isNotification", feed.isNotification.toString())
                                        put("isFullContent", feed.isFullContent.toString())
                                        put("isBrowser", feed.isBrowser.toString())
                                    }
                                },
                                listOf()
                            )
                        }
                    )
                })
            )
        )!!
    }

    /**
     * 获取默认分组的 ID。
     *
     * @param [accountId] 账户 ID
     * @return 默认分组的 ID 字符串
     */
    private fun getDefaultGroupId(accountId: Int): String = accountId.getDefaultGroupId()

    /**
     * 导出单个订阅源为 OPML。
     *
     * 将指定订阅源及其所属分组导出为 OPML 格式字符串。
     *
     * @param [feedId] 订阅源 ID
     * @param [attachInfo] 是否附加额外信息
     * @return OPML 格式的字符串
     * @throws Exception 订阅源或分组不存在时抛出异常
     */
    @Throws(Exception::class)
    suspend fun saveSingleFeedToString(feedId: String, attachInfo: Boolean): String {
        // 根据订阅源 ID 查询订阅源
        val feed = feedDao.queryById(feedId) ?: throw Exception("订阅源不存在")
        // 根据订阅源的分组 ID 查询分组
        val group = groupDao.queryById(feed.groupId) ?: throw Exception("分组不存在")
        // 构建 OPML 文档并返回字符串
        return OpmlWriter().write(
            Opml(
                "2.0",
                Head(
                    accountService.getCurrentAccount().name,
                    Date().toString(), null, null, null,
                    null, null, null, null,
                    null, null, null, null,
                ),
                Body(listOf(
                    // 创建分组节点
                    Outline(
                        mutableMapOf(
                            "text" to group.name,
                            "title" to group.name,
                        ),
                        listOf(
                            // 创建订阅源节点
                            Outline(
                                mutableMapOf(
                                    "text" to feed.name,
                                    "title" to feed.name,
                                    "xmlUrl" to feed.url,
                                    "htmlUrl" to feed.url,
                                    "icon" to (feed.icon ?: "")
                                ).apply {
                                    // 如果需要附加信息，添加订阅源设置
                                    if (attachInfo) {
                                        put("isNotification", feed.isNotification.toString())
                                        put("isFullContent", feed.isFullContent.toString())
                                        put("isBrowser", feed.isBrowser.toString())
                                    }
                                },
                                listOf()
                            )
                        )
                    )
                ))
            )
        )!!
    }

    /**
     * 导出分组内所有订阅源为 OPML。
     *
     * 将指定分组的所有订阅源导出为 OPML 格式字符串。
     *
     * @param [groupId] 分组 ID
     * @param [attachInfo] 是否附加额外信息
     * @return OPML 格式的字符串
     * @throws Exception 分组不存在时抛出异常
     */
    @Throws(Exception::class)
    suspend fun saveGroupFeedsToString(groupId: String, attachInfo: Boolean): String {
        // 根据分组 ID 查询分组
        val group = groupDao.queryById(groupId) ?: throw Exception("分组不存在")
        // 查询该分组下的所有订阅源
        val feeds = feedDao.queryByGroupId(group.accountId, groupId)
        // 构建 OPML 文档并返回字符串
        return OpmlWriter().write(
            Opml(
                "2.0",
                Head(
                    accountService.getCurrentAccount().name,
                    Date().toString(), null, null, null,
                    null, null, null, null,
                    null, null, null, null,
                ),
                Body(listOf(
                    // 创建分组节点
                    Outline(
                        mutableMapOf(
                            "text" to group.name,
                            "title" to group.name,
                        ),
                        // 构建该分组下所有订阅源的节点列表
                        feeds.map { feed ->
                            Outline(
                                mutableMapOf(
                                    "text" to feed.name,
                                    "title" to feed.name,
                                    "xmlUrl" to feed.url,
                                    "htmlUrl" to feed.url,
                                    "icon" to (feed.icon ?: "")
                                ).apply {
                                    // 如果需要附加信息，添加订阅源设置
                                    if (attachInfo) {
                                        put("isNotification", feed.isNotification.toString())
                                        put("isFullContent", feed.isFullContent.toString())
                                        put("isBrowser", feed.isBrowser.toString())
                                    }
                                },
                                listOf()
                            )
                        }
                    )
                ))
            )
        )!!
    }
}
