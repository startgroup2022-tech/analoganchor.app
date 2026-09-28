package com.analoganchor.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unlock_sessions")
data class UnlockSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val requestedAt: Long = System.currentTimeMillis(),
    val proofMissionId: Long?,
    val beforeInsightsPath: String?,
    val reviewCardPath: String?,
    val sharedAt: Long?,
    val returnDueAt: Long?,
    val returnProofPath: String?,
    val afterNote: String?,
    val outcome: String = OUTCOME_DRAFT,
    val completedAt: Long? = null
) {
    companion object {
        const val OUTCOME_DRAFT = "DRAFT"
        const val OUTCOME_AWAITING_RETURN = "AWAITING_RETURN"
        const val OUTCOME_COMPLIANT = "COMPLIANT"
        const val OUTCOME_BREACH = "BREACH"
        const val OUTCOME_RECOVERY_REQUIRED = "RECOVERY_REQUIRED"
    }
}
