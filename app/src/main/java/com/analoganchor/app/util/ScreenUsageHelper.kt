package com.analoganchor.app.util

import android.app.usage.UsageStatsManager
import java.util.Calendar

object ScreenUsageHelper {
    fun fetchDetailedUsageToday(usageStatsManager: UsageStatsManager): UsageResult {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        val appUsageMap = mutableMapOf<String, Long>()
        stats?.forEach { stat ->
            val pkg = stat.packageName
            val time = stat.totalTimeInForeground
            if (time > 0) {
                appUsageMap[pkg] = (appUsageMap[pkg] ?: 0L) + time
            }
        }

        return UsageResult(appUsageMap)
    }

    data class UsageResult(val appUsageMap: Map<String, Long>)
    data class AppUsageInfo(val totalTimeInForeground: Long)
}
