package com.analoganchor.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rule_flags")
data class RuleFlag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val flagType: String,
    val packageName: String?,
    val severity: String,
    val message: String,
    val createdAt: Long = System.currentTimeMillis(),
    val acknowledged: Boolean = false
)
