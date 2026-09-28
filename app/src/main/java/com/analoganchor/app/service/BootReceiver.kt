package com.analoganchor.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device booted. Rescheduling Fajr alarm if enabled.")
            
            val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
            val fajrAlarmEnabled = prefs.getBoolean("fajr_alarm_enabled", false)
            val isNfcLocked = prefs.getBoolean("fajr_alarm_nfc_locked", false)
            val repeatUntilMs = prefs.getLong("fajr_nfc_repeat_until_ms", 0L)
            val isWeeklyRepeatActive = repeatUntilMs > 0L && System.currentTimeMillis() < repeatUntilMs

            if (fajrAlarmEnabled || isNfcLocked || isWeeklyRepeatActive) {
                // Use default lat/lng for simplicity here. AnchorViewModel updates these when location changes.
                FajrAlarmScheduler.scheduleFajrAlarm(context)
                FajrAlarmScheduler.scheduleNightlyReminder(context)
            } else {
                FajrAlarmScheduler.cancelFajrAlarm(context)
            }
            
            // Check if Fajr alarm was left ringing before device power-off
            val wasFajrRinging = prefs.getBoolean("fajr_alarm_ringing", false)
            if (wasFajrRinging) {
                val savedLat = prefs.getFloat("user_lat", Float.NaN).toDouble()
                val savedLng = prefs.getFloat("user_lng", Float.NaN).toDouble()
                val (effLat, effLng) = if (savedLat.isNaN() || savedLng.isNaN()) Pair(26.2285, 50.5860) else Pair(savedLat, savedLng)
                val tzOffset = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0
                val calculator = com.analoganchor.app.data.PrayerTimesCalculator(effLat, effLng, tzOffset)
                val nowCal = java.util.Calendar.getInstance()
                val times = calculator.calculateTimes(nowCal.time)
                val fajrCutoffMs = times.sunrise.timeInMillis.coerceAtLeast(times.fajr.timeInMillis + (30 * 60 * 1000L))

                if (System.currentTimeMillis() > fajrCutoffMs) {
                    Log.w("BootReceiver", "Fajr sunrise cutoff passed while device was powered off. Clearing ringing state and applying penalty.")
                    prefs.edit().putBoolean("fajr_alarm_ringing", false).apply()
                    FajrAlarmReceiver.stopAlarmAudio(context)

                    val dateKey = String.format(java.util.Locale.US, "%04d-%02d-%02d",
                        nowCal.get(java.util.Calendar.YEAR),
                        nowCal.get(java.util.Calendar.MONTH) + 1,
                        nowCal.get(java.util.Calendar.DAY_OF_MONTH)
                    )
                    val isVerified = prefs.getBoolean("fajr_verified_$dateKey", false) || prefs.getBoolean("fajr_wake_verified_$dateKey", false)
                    val isPenalized = prefs.getBoolean("fajr_penalized_$dateKey", false)

                    if (!isVerified && !isPenalized) {
                        val currentPoints = prefs.getInt("reserve_points", 0)
                        val now = System.currentTimeMillis()
                        prefs.edit()
                            .putBoolean("fajr_penalized_$dateKey", true)
                            .putBoolean("fajr_alarm_missed", true)
                            .putLong("fajr_alarm_missed_time", now)
                            .putInt("reserve_points", currentPoints - 10)
                            .putInt("mosque_streak", 0)
                            .putLong("last_penalty_timestamp", now)
                            .putLong("last_ledger_transaction_timestamp", now)
                            .putString("last_ledger_transaction_type", "PENALTY")
                            .apply()
                        FajrAlarmReceiver.showMissedFajrNotification(context, 10)
                    }
                }
            }


            // Check active reflection window on device boot
            val emergencyState = prefs.getString("emergency_state", "LOCKED")
            if (emergencyState == "COOLING") {
                val cooldownStartEpoch = prefs.getLong("emergency_cooldown_start_epoch", 0L)
                val targetEpoch = cooldownStartEpoch + com.analoganchor.app.viewmodel.AnchorViewModel.COOLDOWN_DURATION_SEC * 1000L
                val now = System.currentTimeMillis()
                if (cooldownStartEpoch > 0L && now >= targetEpoch) {
                    Log.d("BootReceiver", "Reflection window elapsed while device was powered off. Triggering completion.")
                    val completeIntent = Intent(context, FajrAlarmReceiver::class.java).apply {
                        action = FajrAlarmReceiver.ACTION_REFLECTION_COMPLETE
                    }
                    context.sendBroadcast(completeIntent)
                } else if (cooldownStartEpoch > 0L) {
                    Log.d("BootReceiver", "Rescheduling reflection window alarm for remaining time.")
                    FajrAlarmScheduler.scheduleReflectionWindowAlarm(context, targetEpoch)
                }
            }

            // Check active prayer DND buffer on device boot
            val isPrayerBufferActive = prefs.getBoolean("prayer_buffer_active", false)
            if (isPrayerBufferActive) {
                val endTimeMs = prefs.getLong("prayer_buffer_end_time_ms", 0L)
                val now = System.currentTimeMillis()
                if (endTimeMs in 1..now) {
                    Log.d("BootReceiver", "Prayer buffer elapsed while device was powered off. Restoring audio now.")
                    PrayerDndRestoreReceiver.restorePrayerDndState(context, triggerHaptic = true, showNotification = true)
                } else if (endTimeMs > now) {
                    Log.d("BootReceiver", "Rescheduling prayer buffer restore alarm for remaining time.")
                    val prayerKey = prefs.getString("prayer_buffer_key", "prayer") ?: "prayer"
                    val nameEn = prefs.getString("prayer_buffer_name_en", "Prayer") ?: "Prayer"
                    val nameAr = prefs.getString("prayer_buffer_name_ar", "الصلاة") ?: "الصلاة"
                    PrayerDndRestoreReceiver.scheduleRestoreAlarm(context, endTimeMs, prayerKey, nameEn, nameAr)
                }
            }

            // Reschedule Zuhr & Asr prayer buffer alerts
            val savedLat = prefs.getFloat("user_lat", Float.NaN).toDouble()
            val savedLng = prefs.getFloat("user_lng", Float.NaN).toDouble()
            val lat = if (savedLat.isNaN() || savedLat == 0.0) 26.2285 else savedLat
            val lng = if (savedLng.isNaN() || savedLng == 0.0) 50.5860 else savedLng
            PrayerBufferScheduler.schedulePrayerBufferAlerts(context, lat, lng)
        }
    }
}
