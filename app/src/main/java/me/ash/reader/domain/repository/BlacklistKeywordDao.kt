package me.ash.reader.domain.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.ash.reader.domain.model.blacklist.BlacklistKeyword

/**
 * 2026-01-24: 关键词黑名单数据访问接口
 * 与账户无关，仅和订阅源相关
 */
@Dao
interface BlacklistKeywordDao {

    /**
     * 获取所有关键词列表（按创建时间倒序）
     */
    @Query("SELECT * FROM blacklist_keyword ORDER BY createdAt DESC")
    fun getAll(): Flow<List<BlacklistKeyword>>

    /**
     * 获取所有关键词列表（同步方式，用于过滤时调用）
     */
    @Query("SELECT * FROM blacklist_keyword ORDER BY createdAt DESC")
    suspend fun getAllSync(): List<BlacklistKeyword>

    /**
     * 添加新关键词
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(keyword: BlacklistKeyword): Long

    /**
     * 删除关键词
     */
    @Delete
    suspend fun delete(keyword: BlacklistKeyword)

    /**
     * 根据ID删除关键词
     */
    @Query("DELETE FROM blacklist_keyword WHERE id = :id")
    suspend fun deleteById(id: Int)

    /**
     * 清空所有关键词
     */
    @Query("DELETE FROM blacklist_keyword")
    suspend fun deleteAll()

    /**
     * 统计关键词数量
     */
    @Query("SELECT COUNT(*) FROM blacklist_keyword")
    suspend fun count(): Int

    /**
     * 切换关键词的启用状态
     */
    @Query("UPDATE blacklist_keyword SET enabled = NOT enabled WHERE id = :id")
    suspend fun toggleEnabled(id: Int)

    /**
     * 设置关键词的启用状态
     */
    @Query("UPDATE blacklist_keyword SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Int, enabled: Boolean)
}