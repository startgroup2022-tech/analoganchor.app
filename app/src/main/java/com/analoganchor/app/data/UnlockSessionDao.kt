package com.analoganchor.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UnlockSessionDao {
    @Query("SELECT COUNT(*) FROM unlock_sessions WHERE date = :date AND outcome != 'DRAFT'")
    fun observeRequestCountForDate(date: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM unlock_sessions WHERE requestedAt >= :weekStartMillis AND outcome != 'DRAFT'")
    fun observeRequestCountSince(weekStartMillis: Long): Flow<Int>

    @Query("SELECT * FROM unlock_sessions WHERE outcome = 'AWAITING_RETURN' ORDER BY requestedAt DESC LIMIT 1")
    fun observePendingReturnSession(): Flow<UnlockSession?>

    @Query("SELECT * FROM unlock_sessions WHERE outcome IN ('BREACH', 'RECOVERY_REQUIRED') ORDER BY requestedAt DESC LIMIT 1")
    fun observeLatestBreachSession(): Flow<UnlockSession?>

    @Query("SELECT * FROM unlock_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): UnlockSession?

    @Query("SELECT * FROM unlock_sessions WHERE outcome = 'AWAITING_RETURN' AND returnDueAt IS NOT NULL AND returnDueAt < :now")
    suspend fun getExpiredPendingSessions(now: Long): List<UnlockSession>

    @Insert
    suspend fun insert(unlockSession: UnlockSession): Long

    @Update
    suspend fun update(unlockSession: UnlockSession)
}
