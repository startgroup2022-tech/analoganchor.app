package com.analoganchor.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.analoganchor.app.data.PrayerTimesCalculator
import java.util.Calendar
import java.util.TimeZone

/**
 * Schedules exact background alarms for Zuhr and Asr prayer buffer windows:
 * - 15 minutes before Adhan: triggers soft sound & vibration alert so user can enter DND mode.
 * - At buffer end: triggers gentle vibration alerting user that buffer period closed.
 */
object PrayerBufferScheduler {
    private const val TAG = "PrayerBufferScheduler"
    private const val ALERT_DHUHR_REQ = 2101
    private const val BUFFER_END_DHUHR_REQ = 2102
    private const val ADHAN_DHUHR_REQ = 2109
    private const val ALERT_ASR_REQ = 2103
    private const val BUFFER_END_ASR_REQ = 2104
    private const val ADHAN_ASR_REQ = 2110
    private const val ALERT_MAGHRIB_REQ = 2105
    private const val BUFFER_END_MAGHRIB_REQ = 2106
    private const val ADHAN_MAGHRIB_REQ = 2111
    private const val ALERT_ISHA_REQ = 2107
    private const val BUFFER_END_ISHA_REQ = 2108

    fun schedulePrayerBufferAlerts(context: Context, lat: Double, lng: Double) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val tzOffset = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0
            val calculator = PrayerTimesCalculator(lat, lng, tzOffset)
            val nowCal = Calendar.getInstance()
            val nowMs = nowCal.timeInMillis
            val isFriday = nowCal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY

            // Calculate for today (and tomorrow if today's window is already past)
            for (dayOffset in 0..1) {
                val targetCal = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
                val times = calculator.calculateTimes(targetCal.time)

                // 1. Zuhr / Jumu'ah buffer (5 minutes before Adhan only)
                val dhuhrAdhanMs = times.dhuhr.timeInMillis
                val dhuhrAlertMs = dhuhrAdhanMs - (5 * 60 * 1000L) // 5 mins before Azan
                val dhuhrBufferEndMs = dhuhrAdhanMs + (if (isFriday && dayOffset == 0) 25 else 20) * 60 * 1000L // 5 mins after prayer starts

                val dhuhrNameEn = if (isFriday && dayOffset == 0) "Jumu'ah" else "Zuhr"
                val dhuhrNameAr = if (isFriday && dayOffset == 0) "الجمعة" else "الظهر"

                if (dhuhrAlertMs > nowMs) {
                    scheduleExactAlarm(
                        context, alarmManager, dhuhrAlertMs,
                        ALERT_DHUHR_REQ,
                        PrayerDndRestoreReceiver.ACTION_PRAYER_BUFFER_START,
                        "dhuhr", dhuhrNameEn, dhuhrNameAr,
                        isExactAdhan = false
                    )
                }
                // Pre-Adhan notification lifetime ends at exact Adhan time
                if (dhuhrAdhanMs > nowMs) {
                    scheduleExactAlarm(
                        context, alarmManager, dhuhrAdhanMs,
                        ADHAN_DHUHR_REQ,
                        PrayerDndRestoreReceiver.ACTION_PRAYER_ADHAN_ARRIVED,
                        "dhuhr", dhuhrNameEn, dhuhrNameAr,
                        isExactAdhan = true
                    )
                }
                if (dhuhrBufferEndMs > nowMs) {
                    scheduleExactAlarm(
                        context, alarmManager, dhuhrBufferEndMs,
                        BUFFER_END_DHUHR_REQ,
                        PrayerDndRestoreReceiver.ACTION_PRAYER_BUFFER_END,
                        "dhuhr", dhuhrNameEn, dhuhrNameAr,
                        isExactAdhan = false
                    )
                }

                // 2. Asr buffer (5 minutes before Adhan only) - extended by +10 min (total 35 mins from Adhan)
                val asrAdhanMs = times.asr.timeInMillis
                val asrAlertMs = asrAdhanMs - (5 * 60 * 1000L) // 5 mins before Azan
                val asrBufferEndMs = asrAdhanMs + (35 * 60 * 1000L) // 15 mins after prayer starts (total 35 mins)

                if (asrAlertMs > nowMs) {
                    scheduleExactAlarm(
                        context, alarmManager, asrAlertMs,
                        ALERT_ASR_REQ,
                        PrayerDndRestoreReceiver.ACTION_PRAYER_BUFFER_START,
                        "asr", "Asr", "العصر",
                        isExactAdhan = false
                    )
                }
                // Pre-Adhan notification lifetime ends at exact Adhan time
                if (asrAdhanMs > nowMs) {
                    scheduleExactAlarm(
                        context, alarmManager, asrAdhanMs,
                        ADHAN_ASR_REQ,
                        PrayerDndRestoreReceiver.ACTION_PRAYER_ADHAN_ARRIVED,
                        "asr", "Asr", "العصر",
                        isExactAdhan = true
                    )
                }
                if (asrBufferEndMs > nowMs) {
                    scheduleExactAlarm(
                        context, alarmManager, asrBufferEndMs,
                        BUFFER_END_ASR_REQ,
                        PrayerDndRestoreReceiver.ACTION_PRAYER_BUFFER_END,
                        "asr", "Asr", "العصر",
                        isExactAdhan = false
                    )
                }

                // 3. Maghrib buffer (5 minutes before Adhan only)
                val maghribAdhanMs = times.maghrib.timeInMillis
                val maghribAlertMs = maghribAdhanMs - (5 * 60 * 1000L) // 5 mins before Azan
                val maghribBufferEndMs = maghribAdhanMs + (20 * 60 * 1000L) // 5 mins after prayer starts

                if (maghribAlertMs > nowMs) {
                    scheduleExactAlarm(
                        context, alarmManager, maghribAlertMs,
                        ALERT_MAGHRIB_REQ,
                        PrayerDndRestoreReceiver.ACTION_PRAYER_BUFFER_START,
                        "maghrib", "Maghrib", "المغرب",
                        isExactAdhan = false
                    )
                }
                // Pre-Adhan notification lifetime ends at exact Adhan time
                if (maghribAdhanMs > nowMs) {
                    scheduleExactAlarm(
                        context, alarmManager, maghribAdhanMs,
                        ADHAN_MAGHRIB_REQ,
                        PrayerDndRestoreReceiver.ACTION_PRAYER_ADHAN_ARRIVED,
                        "maghrib", "Maghrib", "المغرب",
                        isExactAdhan = true
                    )
                }
                if (maghribBufferEndMs > nowMs) {
                    scheduleExactAlarm(
                        context, alarmManager, maghribBufferEndMs,
                        BUFFER_END_MAGHRIB_REQ,
                        PrayerDndRestoreReceiver.ACTION_PRAYER_BUFFER_END,
                        "maghrib", "Maghrib", "المغرب",
                        isExactAdhan = false
                    )
                }

                // 4. Note: Isha is intentionally excluded from Soft Landing (DND) per design.
                // After Maghrib, duty finishes and evening is relaxed; Sacred Retreat covers Maghrib-Isha.

                // Only schedule for day 0 if in future, else continue to tomorrow
                if (dhuhrBufferEndMs > nowMs || asrBufferEndMs > nowMs || maghribBufferEndMs > nowMs) {
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule prayer buffer alerts: ${e.message}", e)
        }
    }

    fun cancelPrayerBufferAlerts(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            cancelAlarm(context, alarmManager, ALERT_DHUHR_REQ, PrayerDndRestoreReceiver.ACTION_PRAYER_BUFFER_START)
            cancelAlarm(context, alarmManager, ADHAN_DHUHR_REQ, PrayerDndRestoreReceiver.ACTION_PRAYER_ADHAN_ARRIVED)
            cancelAlarm(context, alarmManager, BUFFER_END_DHUHR_REQ, PrayerDndRestoreReceiver.ACTION_PRAYER_BUFFER_END)
            cancelAlarm(context, alarmManager, ALERT_ASR_REQ, PrayerDndRestoreReceiver.ACTION_PRAYER_BUFFER_START)
            cancelAlarm(context, alarmManager, ADHAN_ASR_REQ, PrayerDndRestoreReceiver.ACTION_PRAYER_ADHAN_ARRIVED)
            cancelAlarm(context, alarmManager, BUFFER_END_ASR_REQ, PrayerDndRestoreReceiver.ACTION_PRAYER_BUFFER_END)
            cancelAlarm(context, alarmManager, ALERT_MAGHRIB_REQ, PrayerDndRestoreReceiver.ACTION_PRAYER_BUFFER_START)
            cancelAlarm(context, alarmManager, ADHAN_MAGHRIB_REQ, PrayerDndRestoreReceiver.ACTION_PRAYER_ADHAN_ARRIVED)
            cancelAlarm(context, alarmManager, BUFFER_END_MAGHRIB_REQ, PrayerDndRestoreReceiver.ACTION_PRAYER_BUFFER_END)
            cancelAlarm(context, alarmManager, ALERT_ISHA_REQ, PrayerDndRestoreReceiver.ACTION_PRAYER_BUFFER_START)
            cancelAlarm(context, alarmManager, BUFFER_END_ISHA_REQ, PrayerDndRestoreReceiver.ACTION_PRAYER_BUFFER_END)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel prayer buffer alerts: ${e.message}", e)
        }
    }

    private fun cancelAlarm(context: Context, alarmManager: AlarmManager, requestCode: Int, action: String) {
        val intent = Intent(context, PrayerDndRestoreReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun scheduleExactAlarm(
        context: Context,
        alarmManager: AlarmManager,
        triggerAtMs: Long,
        requestCode: Int,
        action: String,
        prayerKey: String,
        nameEn: String,
        nameAr: String,
        isExactAdhan: Boolean = false
    ) {
        val intent = Intent(context, PrayerDndRestoreReceiver::class.java).apply {
            this.action = action
            putExtra("prayer_key", prayerKey)
            putExtra("prayer_name_en", nameEn)
            putExtra("prayer_name_ar", nameAr)
            putExtra("is_exact_adhan", isExactAdhan)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
        }
        Log.d(TAG, "Scheduled $action ($prayerKey, exactAdhan=$isExactAdhan) at epoch $triggerAtMs")
    }
}
