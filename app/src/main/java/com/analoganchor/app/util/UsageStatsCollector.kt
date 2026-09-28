package com.analoganchor.app.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context

class UsageStatsCollector(private val context: Context) {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun getAggregatedUsage(days: Int): Map<String, UsageStats> {
        val end = System.currentTimeMillis()
        val start = end - (days * DAY_MS)
        return usageStatsManager.queryAndAggregateUsageStats(start, end)
    }

    fun getOpenEvents(
        packageName: String,
        startTime: Long,
        endTime: Long
    ): List<Long> {
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val timestamps = mutableListOf<Long>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName == packageName && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                timestamps.add(event.timeStamp)
            }
        }
        return timestamps
    }

    companion object {
        private const val DAY_MS = 86_400_000L
    }
}
