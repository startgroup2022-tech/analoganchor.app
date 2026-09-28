package com.analoganchor.app.engine

import android.app.usage.UsageStats
import com.analoganchor.app.data.RuleFlag
import com.analoganchor.app.util.UsageStatsCollector
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToLong

data class AppBehaviorSnapshot(
    val packageName: String,
    val openCountLast24h: Int,
    val totalMinutesLast7d: Long,
    val lateNightOpens: Int,
    val compulsiveCheckCount: Int
)

class BehaviorRuleEngine(
    private val collector: UsageStatsCollector
) {
    fun analyzeAll(
        packageNames: List<String>,
        currentStreak: Int,
        hasRecentStreakReward: Boolean
    ): List<RuleFlag> {
        val now = System.currentTimeMillis()
        val usage = collector.getAggregatedUsage(7)
        val flags = mutableListOf<RuleFlag>()

        packageNames.forEach { packageName ->
            val snapshot = buildSnapshot(packageName, usage[packageName], now)
            flags += evaluatePackageRules(snapshot)
        }

        if (currentStreak >= 7 && !hasRecentStreakReward) {
            flags += RuleFlag(
                flagType = "FLAG_STREAK_REWARD",
                packageName = null,
                severity = "INFO",
                message = "7-day proof streak. The trusted contact can consider this positive context during reviews.",
                createdAt = now
            )
        }

        return flags
    }

    fun buildSnapshot(
        packageName: String,
        usageStats: UsageStats?,
        now: Long = System.currentTimeMillis()
    ): AppBehaviorSnapshot {
        val last24hEvents = collector.getOpenEvents(packageName, now - DAY_MS, now)
        val last3dEvents = collector.getOpenEvents(packageName, now - (3 * DAY_MS), now)
        val totalMinutesLast7d = ((usageStats?.totalTimeInForeground ?: 0L) / 60_000L)

        return AppBehaviorSnapshot(
            packageName = packageName,
            openCountLast24h = last24hEvents.size,
            totalMinutesLast7d = totalMinutesLast7d,
            lateNightOpens = last3dEvents.count(::isLateNight),
            compulsiveCheckCount = last24hEvents.zipWithNext().count { (first, second) ->
                second - first in 1 until 30_000L
            }
        )
    }

    private fun evaluatePackageRules(snapshot: AppBehaviorSnapshot): List<RuleFlag> {
        val flags = mutableListOf<RuleFlag>()
        val appName = friendlyName(snapshot.packageName)
        val now = System.currentTimeMillis()

        if (snapshot.openCountLast24h >= 8 && snapshot.compulsiveCheckCount >= 3) {
            flags += RuleFlag(
                flagType = "FLAG_COMPULSIVE_CHECKER",
                packageName = snapshot.packageName,
                severity = "WARN",
                message = "$appName was opened ${snapshot.openCountLast24h} times today, including ${snapshot.compulsiveCheckCount} rapid repeat checks. Add this context to unlock reviews.",
                createdAt = now
            )
        }

        if (snapshot.lateNightOpens >= 3) {
            flags += RuleFlag(
                flagType = "FLAG_LATE_NIGHT_PATTERN",
                packageName = snapshot.packageName,
                severity = "WARN",
                message = "Late-night $appName use was detected ${snapshot.lateNightOpens} times in the last 3 days.",
                createdAt = now
            )
        }

        val dailyAverage = (snapshot.totalMinutesLast7d / 7.0).roundToLong()
        if (dailyAverage > 90) {
            flags += RuleFlag(
                flagType = "FLAG_HEAVY_USAGE",
                packageName = snapshot.packageName,
                severity = "INFO",
                message = "$appName averaged $dailyAverage minutes per day last week.",
                createdAt = now
            )
        }

        return flags
    }

    private fun isLateNight(timestamp: Long): Boolean {
        val hour = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY)
        return hour >= 22 || hour < 2
    }

    private fun friendlyName(packageName: String): String {
        return when (packageName) {
            "com.instagram.android" -> "Instagram"
            "com.zhiliaoapp.musically", "com.tiktok.android" -> "TikTok"
            "com.google.android.youtube" -> "YouTube"
            "com.facebook.katana" -> "Facebook"
            "com.twitter.android" -> "X"
            "com.whatsapp" -> "WhatsApp"
            else -> packageName.substringAfterLast('.').replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
            }
        }
    }

    companion object {
        private const val DAY_MS = 86_400_000L

        val TRACKED_PACKAGES = listOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.tiktok.android",
            "com.google.android.youtube",
            "com.facebook.katana",
            "com.twitter.android",
            "com.whatsapp"
        )
    }
}
