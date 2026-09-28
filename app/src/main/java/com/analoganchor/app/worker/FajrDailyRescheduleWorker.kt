package com.analoganchor.app.worker

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.analoganchor.app.data.AppDatabase
import com.analoganchor.app.data.PrayerTimesCalculator
import com.analoganchor.app.data.SessionLog
import com.analoganchor.app.service.FajrAlarmReceiver
import com.analoganchor.app.service.FajrAlarmScheduler
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Background WorkManager worker that runs periodically to guarantee the "Day 2" Fajr alarm
 * is always scheduled in the system AlarmManager, even if the app process was killed overnight,
 * and detects missed Fajr prayers if the phone was idle or sleeping.
 */
class FajrDailyRescheduleWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val prefs = applicationContext.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("fajr_alarm_enabled", false)
            val isNfcLocked = prefs.getBoolean("fajr_alarm_nfc_locked", false)
            val repeatUntilMs = prefs.getLong("fajr_nfc_repeat_until_ms", 0L)
            val isWeeklyRepeatActive = repeatUntilMs > 0L && System.currentTimeMillis() < repeatUntilMs
            val shouldScheduleFajr = enabled || isNfcLocked || isWeeklyRepeatActive

            Log.d(TAG, "🌙 FajrDailyRescheduleWorker executing self-healing check. enabled=$enabled, locked=$isNfcLocked, weekly=$isWeeklyRepeatActive")

            val savedLat = prefs.getFloat("user_lat", Float.NaN).toDouble()
            val savedLng = prefs.getFloat("user_lng", Float.NaN).toDouble()
            val lat = if (savedLat.isNaN() || savedLat == 0.0) 21.4225 else savedLat
            val lng = if (savedLng.isNaN() || savedLng == 0.0) 39.8262 else savedLng

            // 1. Guarantee Bedtime reminder, Thursday prompt, and Prayer Buffer alerts are scheduled
            FajrAlarmScheduler.scheduleNightlyReminder(applicationContext)
            FajrAlarmScheduler.scheduleThursdayPreBedtimePrompt(applicationContext)
            com.analoganchor.app.service.PrayerBufferScheduler.schedulePrayerBufferAlerts(applicationContext, lat, lng)

            // 2. Schedule or cancel Fajr and Jumu'ah alarms based on user preference
            if (shouldScheduleFajr) {
                FajrAlarmScheduler.scheduleFajrAlarm(applicationContext, lat, lng)
                FajrAlarmScheduler.scheduleJumuahAlarm(applicationContext, lat, lng)
                Log.d(TAG, "✅ Guaranteed and refreshed Fajr & Jumu'ah alarms in AlarmManager.")
            } else {
                FajrAlarmScheduler.cancelFajrAlarm(applicationContext)
                FajrAlarmScheduler.cancelJumuahAlarm(applicationContext)
                Log.d(TAG, "⏹️ Fajr alarm is disabled; cancelled any lingering alarms in AlarmManager.")
            }

            // 3. Catch up missed Fajr prayers in background ONLY if Fajr was actually active
            val isOnboarded = prefs.getBoolean("is_onboarded", false)
            if (shouldScheduleFajr && isOnboarded) {
                var installTimestamp = prefs.getLong("app_install_timestamp", 0L)
                if (installTimestamp == 0L) {
                    installTimestamp = System.currentTimeMillis()
                    prefs.edit().putLong("app_install_timestamp", installTimestamp).apply()
                }
                val tzOffset = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0
                val calculator = PrayerTimesCalculator(lat, lng, tzOffset)
                val nowCal = Calendar.getInstance()
                val nowMs = nowCal.timeInMillis
                var newlyMissedFajrCount = 0

                for (dayOffset in 6 downTo 0) {
                    val checkCal = (nowCal.clone() as Calendar).apply {
                        add(Calendar.DAY_OF_YEAR, -dayOffset)
                    }
                    val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(checkCal.time)
                    val times = calculator.calculateTimes(checkCal.time)
                    val fajrCutoffMs = times.sunrise.timeInMillis.coerceAtLeast(times.fajr.timeInMillis + (30 * 60 * 1000L))

                    // Never penalize for Fajr if Fajr occurred before the app was installed/onboarded
                    if (times.fajr.timeInMillis < installTimestamp && nowMs - installTimestamp < 24 * 3600 * 1000L) {
                        prefs.edit().putBoolean("fajr_penalized_$dateKey", true).apply()
                        continue
                    }

                    if (nowMs > fajrCutoffMs) {
                        val isAttendedOrVerified = prefs.getBoolean("fajr_verified_$dateKey", false) ||
                                prefs.getBoolean("fajr_wake_verified_$dateKey", false)
                        val isPenalized = prefs.getBoolean("fajr_penalized_$dateKey", false)

                        if (!isAttendedOrVerified && !isPenalized) {
                            prefs.edit().putBoolean("fajr_penalized_$dateKey", true).apply()
                            newlyMissedFajrCount++

                            val currentPoints = prefs.getInt("reserve_points", 0)
                            val updatedPoints = currentPoints - 10
                            val now = System.currentTimeMillis()
                            prefs.edit()
                                .putInt("reserve_points", updatedPoints)
                                .putLong("last_penalty_timestamp", now)
                                .putLong("last_ledger_transaction_timestamp", now)
                                .putString("last_ledger_transaction_type", "PENALTY")
                                .apply()

                            try {
                                AppDatabase.getDatabase(applicationContext).sessionLogDao().insertLog(
                                    SessionLog(
                                        action = "Missed Fajr Prayer ($dateKey)",
                                        pointsChange = -10,
                                        status = "penalty"
                                    )
                                )
                            } catch (dbEx: Exception) {
                                Log.e(TAG, "Failed to log missed Fajr: ${dbEx.message}")
                            }
                        }
                    }
                }

                if (newlyMissedFajrCount > 0) {
                    prefs.edit()
                        .putBoolean("fajr_alarm_missed", true)
                        .putLong("fajr_alarm_missed_time", System.currentTimeMillis())
                        .putInt("mosque_streak", 0)
                        .apply()
                    FajrAlarmReceiver.showMissedFajrNotification(applicationContext, newlyMissedFajrCount * 10)
                }
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during FajrDailyRescheduleWorker: ${e.message}", e)
            return Result.retry()
        }
    }

    companion object {
        const val TAG = "FajrRescheduleWorker"
        const val WORK_NAME = "fajr_daily_self_healing_reschedule"
    }
}
