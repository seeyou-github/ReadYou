package me.ash.reader.infrastructure.preference

import me.ash.reader.domain.model.blacklist.BlacklistKeyword
import me.ash.reader.domain.repository.BlacklistKeywordDao

/**
 * 2026-01-24: 关键词黑名单偏好设置
 * 与账户无关，仅和订阅源相关
 * 支持多订阅源匹配（逗号分隔）
 */
object BlacklistPreference {

    /**
     * 添加新关键词
     * @param keyword 关键词
     * @param feedUrls 订阅源 URL 列表（逗号分隔），null 或空表示全部订阅源
     * @param feedNames 订阅源名称列表（逗号分隔），用于 UI 显示
     */
    suspend fun addKeyword(
        dao: BlacklistKeywordDao,
        keyword: String,
        feedUrls: List<String>? = null,
        feedNames: List<String>? = null,
    ) {
        val blacklistKeyword = BlacklistKeyword(
            keyword = keyword.trim(),
            feedUrls = feedUrls?.joinToString(",") { it.trim() }?.takeIf { it.isNotEmpty() },
            feedNames = feedNames?.joinToString(",") { it.trim() }?.takeIf { it.isNotEmpty() },
        )
        dao.insert(blacklistKeyword)
    }

    /**
     * 删除关键词
     */
    suspend fun deleteKeyword(dao: BlacklistKeywordDao, id: Int) {
        dao.deleteById(id)
    }

    /**
     * 清空所有关键词
     */
    suspend fun clearAll(dao: BlacklistKeywordDao) {
        dao.deleteAll()
    }

    /**
     * 获取所有关键词（同步，用于过滤时）
     */
    suspend fun getAllKeywordsSync(dao: BlacklistKeywordDao): List<BlacklistKeyword> {
        return dao.getAllSync()
    }

    /**
     * 切换关键词的启用状态
     */
    suspend fun toggleEnabled(dao: BlacklistKeywordDao, id: Int) {
        dao.toggleEnabled(id)
    }

    /**
     * 设置关键词的启用状态
     */
    suspend fun setEnabled(dao: BlacklistKeywordDao, id: Int, enabled: Boolean) {
        dao.setEnabled(id, enabled)
    }
}