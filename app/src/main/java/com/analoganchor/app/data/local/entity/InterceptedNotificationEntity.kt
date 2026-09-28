package com.analoganchor.app.data.local.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "intercepted_notifications")
data class InterceptedNotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val scheduleId: Long
)
