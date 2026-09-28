package com.analoganchor.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_logs")
data class SessionLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,
    val pointsChange: Int,
    val status: String, // complete, missed, bonus, redemption
    val startTime: Long? = null,
    val endTime: Long? = null,
    val duration: Int? = null,
    val nfcTagId: String? = null,
    val photoStatus: String? = null, // privately_saved, archived, skipped
    val userNote: String? = null,
    val imagePath: String? = null
)
