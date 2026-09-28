package com.analoganchor.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecoveryRecordDao {
    @Query("SELECT * FROM recovery_records WHERE status = 'OPEN' ORDER BY createdAt DESC LIMIT 1")
    fun observeOpenRecovery(): Flow<RecoveryRecord?>

    @Query("SELECT * FROM recovery_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RecoveryRecord?

    @Insert
    suspend fun insert(recoveryRecord: RecoveryRecord): Long

    @Update
    suspend fun update(recoveryRecord: RecoveryRecord)
}
