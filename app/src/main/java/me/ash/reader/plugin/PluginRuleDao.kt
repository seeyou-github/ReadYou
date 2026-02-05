package me.ash.reader.plugin

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginRuleDao {

    @Query(
        """
        SELECT * FROM plugin_rule
        WHERE accountId = :accountId
        ORDER BY updatedAt DESC
        """
    )
    fun flowAll(accountId: Int): Flow<List<PluginRule>>

    @Query(
        """
        SELECT * FROM plugin_rule
        WHERE accountId = :accountId
        ORDER BY updatedAt DESC
        """
    )
    suspend fun queryAll(accountId: Int): List<PluginRule>

    @Query(
        """
        SELECT * FROM plugin_rule
        WHERE id = :id
        """
    )
    suspend fun queryById(id: String): PluginRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: PluginRule)

    @Update
    suspend fun update(rule: PluginRule)

    @Delete
    suspend fun delete(rule: PluginRule)
}
