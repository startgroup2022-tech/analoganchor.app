package com.analoganchor.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleFlagDao {
    @Query("SELECT COUNT(*) FROM rule_flags WHERE acknowledged = 0")
    fun observeUnacknowledgedCount(): Flow<Int>

    @Query("SELECT * FROM rule_flags WHERE acknowledged = 0 ORDER BY createdAt DESC")
    fun observeUnacknowledged(): Flow<List<RuleFlag>>

    @Query("SELECT COUNT(*) FROM rule_flags WHERE flagType = :flagType AND createdAt >= :since")
    suspend fun countFlagsSince(flagType: String, since: Long): Int

    @Insert
    suspend fun insert(ruleFlag: RuleFlag): Long

    @Insert
    suspend fun insertAll(ruleFlags: List<RuleFlag>)

    @Update
    suspend fun update(ruleFlag: RuleFlag)

    @Query("UPDATE rule_flags SET acknowledged = 1 WHERE id = :id")
    suspend fun acknowledge(id: Long)
}
