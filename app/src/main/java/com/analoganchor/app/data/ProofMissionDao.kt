package com.analoganchor.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProofMissionDao {
    @Query("SELECT * FROM proof_missions WHERE date = :date AND status = 'COMPLETED' ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestCompletedForDate(date: String): Flow<ProofMission?>

    @Query("SELECT * FROM proof_missions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProofMission?

    @Insert
    suspend fun insert(proofMission: ProofMission): Long
}
