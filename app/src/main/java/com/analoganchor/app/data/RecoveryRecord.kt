package com.analoganchor.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recovery_records")
data class RecoveryRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long?,
    val reason: String,
    val proofImagePath: String?,
    val status: String = STATUS_OPEN
) {
    companion object {
        const val STATUS_OPEN = "OPEN"
        const val STATUS_RESOLVED = "RESOLVED"
    }
}
