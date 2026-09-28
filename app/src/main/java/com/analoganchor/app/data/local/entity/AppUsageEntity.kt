package com.analoganchor.app.data.local.entity

data class AppUsageEntity(
    val id: Int = 0,
    val packageName: String = "",
    val date: String = "",
    val usageTimeMillis: Long = 0L,
    val lastUpdated: Long = 0L
)
