package com.analoganchor.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionLogDao {
    @Query("SELECT * FROM session_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getLatestLogs(limit: Int): Flow<List<SessionLog>>

    @Query("SELECT * FROM session_logs WHERE action LIKE '%Eraser%' OR userNote IS NOT NULL ORDER BY timestamp DESC")
    fun getWhiteboardSessions(): Flow<List<SessionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SessionLog)

    @Query("DELETE FROM session_logs")
    suspend fun clearLogs()
}
