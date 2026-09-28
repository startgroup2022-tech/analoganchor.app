package com.analoganchor.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proof_missions")
data class ProofMission(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val createdAt: Long = System.currentTimeMillis(),
    val proofType: String,
    val imagePath: String?,
    val note: String?,
    val status: String = "COMPLETED"
)
