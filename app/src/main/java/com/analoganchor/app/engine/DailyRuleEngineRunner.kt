package com.analoganchor.app.engine

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import com.analoganchor.app.data.AppDatabase
import com.analoganchor.app.util.UsageStatsCollector

object DailyRuleEngineRunner {
    private const val PREF_LAST_RULE_ENGINE_RUN = "last_rule_engine_run"
    private const val DAY_MS = 86_400_000L
    private const val WEEK_MS = 7 * DAY_MS

    suspend fun runIfDue(context: Context) {
        val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastRun = prefs.getLong(PREF_LAST_RULE_ENGINE_RUN, 0L)
        if (now - lastRun < DAY_MS) return

        prefs.edit().putLong(PREF_LAST_RULE_ENGINE_RUN, now).apply()
        if (!hasUsageAccess(context)) return

        val db = AppDatabase.getInstance(context)
        val hasRecentStreakReward = db.ruleFlagDao().countFlagsSince(
            flagType = "FLAG_STREAK_REWARD",
            since = now - WEEK_MS
        ) > 0

        val engine = BehaviorRuleEngine(UsageStatsCollector(context))
        val flags = engine.analyzeAll(
            packageNames = BehaviorRuleEngine.TRACKED_PACKAGES,
            currentStreak = prefs.getInt("anchor_streak", 0),
            hasRecentStreakReward = hasRecentStreakReward
        )

        if (flags.isNotEmpty()) {
            db.ruleFlagDao().insertAll(flags)
        }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
