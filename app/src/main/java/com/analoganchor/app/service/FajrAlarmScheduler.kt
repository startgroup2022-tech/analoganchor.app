package com.analoganchor.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.analoganchor.app.data.PrayerTimesCalculator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object FajrAlarmScheduler {
    const val FAJR_ALARM_ACTION = "com.analoganchor.app.action.FAJR_ALARM"
    const val FAJR_5_HOUR_WARNING_ACTION = "com.analoganchor.app.action.FAJR_5HOUR_WARNING"
    const val FAJR_REMINDER_ACTION = "com.analoganchor.app.action.FAJR_REMINDER"
    const val JUMUAH_ALARM_ACTION = "com.analoganchor.app.action.JUMUAH_ALARM"
    const val PENALTY_TIMEOUT_ACTION = "com.analoganchor.app.action.ALARM_PENALTY_TIMEOUT"
    const val REFLECTION_COMPLETE_ACTION = "com.analoganchor.app.action.REFLECTION_COMPLETE"
    const val ACTION_ACKNOWLEDGE_ISHA = "com.analoganchor.app.action.ACKNOWLEDGE_ISHA"
    const val ACTION_CHECK_ISHA_DORMANCY = "com.analoganchor.app.action.CHECK_ISHA_DORMANCY"
    const val ACTION_THURSDAY_PRE_BEDTIME_PROMPT = "com.analoganchor.app.action.THURSDAY_PRE_BEDTIME_PROMPT"
    const val TADABBUR_ALARM_ACTION = "com.analoganchor.app.action.TADABBUR_ALARM"
    const val EXTRA_IS_SECONDARY_FAJR = "is_secondary_fajr"

    fun scheduleJumuahAlarm(context: Context, lat: Double = 21.4225, lng: Double = 39.8262) {
        try {
            val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("fajr_alarm_enabled", false)
            val isNfcLocked = prefs.getBoolean("fajr_alarm_nfc_locked", false)
            val repeatUntilMs = prefs.getLong("fajr_nfc_repeat_until_ms", 0L)
            val isWeeklyRepeatActive = repeatUntilMs > 0L && System.currentTimeMillis() < repeatUntilMs
            val isArmed = enabled || isNfcLocked || isWeeklyRepeatActive

            if (!isArmed) {
                Log.d("FajrAlarmScheduler", "Fajr alarm is disabled. Cancelling Jumu'ah alarm.")
                cancelJumuahAlarm(context)
                return
            }

            val tzOffset = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0
            val calculator = PrayerTimesCalculator(lat, lng, tzOffset)
            val nowCal = Calendar.getInstance()
            
            val targetCal = (nowCal.clone() as Calendar)
            while (targetCal.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
                targetCal.add(Calendar.DAY_OF_YEAR, 1)
            }
            
            val fridayTimes = calculator.calculateTimes(targetCal.time)
            val jumuahDhuhrCal = fridayTimes.dhuhr
            val alarmTimeMillis = jumuahDhuhrCal.timeInMillis - (45 * 60 * 1000L)
            
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val sdf = SimpleDateFormat("EEE MM-dd hh:mm a", Locale.getDefault())

            val finalTriggerMillis = if (alarmTimeMillis > System.currentTimeMillis()) {
                alarmTimeMillis
            } else {
                targetCal.add(Calendar.DAY_OF_YEAR, 7)
                val nextFridayTimes = calculator.calculateTimes(targetCal.time)
                nextFridayTimes.dhuhr.timeInMillis - (45 * 60 * 1000L)
            }

            scheduleReceiverAlarm(
                context, alarmManager, finalTriggerMillis, 1004, JUMUAH_ALARM_ACTION
            )
            Log.d("FajrAlarmScheduler", "Scheduled Jumu'ah alarm for: ${sdf.format(Date(finalTriggerMillis))}")
        } catch (e: Exception) {
            Log.e("FajrAlarmScheduler", "Failed to schedule Jumu'ah alarm: ${e.message}")
        }
    }

    fun scheduleFajrAlarm(context: Context, lat: Double = 21.4225, lng: Double = 39.8262) {
        try {
            val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("fajr_alarm_enabled", false)
            val isNfcLocked = prefs.getBoolean("fajr_alarm_nfc_locked", false)
            val repeatUntilMs = prefs.getLong("fajr_nfc_repeat_until_ms", 0L)
            val isWeeklyRepeatActive = repeatUntilMs > 0L && System.currentTimeMillis() < repeatUntilMs

            if (!enabled && !isNfcLocked && !isWeeklyRepeatActive) {
                Log.d("FajrAlarmScheduler", "Fajr alarm is disabled and not locked. Ensuring cancelled in AlarmManager.")
                cancelFajrAlarm(context)
                return
            }

            val now = Date()
            val calendar = Calendar.getInstance()
            calendar.time = now
            val tzOffset = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0
            
            val calculator = PrayerTimesCalculator(lat, lng, tzOffset)
            val timesToday = calculator.calculateTimes(now)
            
            val tomorrow = Date(now.time + 86400000)
            val timesTomorrow = calculator.calculateTimes(tomorrow)
            
            val nowCal = Calendar.getInstance()
            val nextFajr = if (nowCal.before(timesToday.fajr)) {
                timesToday.fajr
            } else {
                timesTomorrow.fajr
            }

            val offsetMinutes = prefs.getInt("fajr_alarm_offset_minutes", 0)

            val warningTimeMillis = nextFajr.timeInMillis - (5 * 60 * 60 * 1000)
            val alarmTimeMillis = nextFajr.timeInMillis + (offsetMinutes * 60 * 1000L)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())

            if (warningTimeMillis > System.currentTimeMillis()) {
                scheduleReceiverAlarm(
                    context, alarmManager, warningTimeMillis, 1002, FAJR_5_HOUR_WARNING_ACTION
                )
                Log.d("FajrAlarmScheduler", "Scheduled Fajr 5-hour warning for: ${sdf.format(Date(warningTimeMillis))}")
            }
            if (alarmTimeMillis > System.currentTimeMillis()) {
                scheduleReceiverAlarm(
                    context, alarmManager, alarmTimeMillis, 1001, FAJR_ALARM_ACTION
                )
                Log.d("FajrAlarmScheduler", "Scheduled Fajr alarm (offset: ${offsetMinutes}m) for: ${sdf.format(Date(alarmTimeMillis))}")
            }
        } catch (e: Exception) {
            Log.e("FajrAlarmScheduler", "Failed to schedule Fajr alarm: ${e.message}")
        }
    }

    fun scheduleNightlyReminder(context: Context) {
        try {
            val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
            val bedtimeStr = prefs.getString("evening_protocol_time", "22:00") ?: "22:00"
            val parts = bedtimeStr.split(":")
            val bedHour = parts.getOrNull(0)?.toIntOrNull() ?: 22
            val bedMin = parts.getOrNull(1)?.toIntOrNull() ?: 0

            val now = Calendar.getInstance()
            val targetTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, bedHour)
                set(Calendar.MINUTE, bedMin)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (now.after(targetTime)) {
                targetTime.add(Calendar.DAY_OF_YEAR, 1)
            }
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            val intent = Intent(context, FajrAlarmReceiver::class.java).apply {
                this.action = FAJR_REMINDER_ACTION
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1003,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            safeScheduleAlarm(context, alarmManager, targetTime.timeInMillis, pendingIntent)
            val sdf = SimpleDateFormat("MM-dd hh:mm a", Locale.getDefault())
            Log.d("FajrAlarmScheduler", "Scheduled Nightly Bedtime Reminder for: ${sdf.format(targetTime.time)}")
        } catch (e: Exception) {
            Log.e("FajrAlarmScheduler", "Failed to schedule nightly reminder: ${e.message}")
        }
    }

    fun scheduleIshaDormancyCheck(context: Context, triggerAtMs: Long) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, FajrAlarmReceiver::class.java).apply {
                this.action = ACTION_CHECK_ISHA_DORMANCY
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1007,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
            Log.d("FajrAlarmScheduler", "Scheduled Isha dormancy check for ms: $triggerAtMs")
        } catch (e: Exception) {
            Log.e("FajrAlarmScheduler", "Failed to schedule Isha dormancy check: ${e.message}")
        }
    }

    private fun scheduleReceiverAlarm(
        context: Context,
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        requestCode: Int,
        action: String
    ) {
        val intent = Intent(context, FajrAlarmReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        safeScheduleAlarm(context, alarmManager, triggerAtMillis, pendingIntent)
    }

    private fun safeScheduleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        operationIntent: PendingIntent
    ) {
        val showIntent = Intent(context, com.analoganchor.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            0,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)

        try {
            alarmManager.setAlarmClock(clockInfo, operationIntent)
            Log.d("FajrAlarmScheduler", "✅ setAlarmClock() registered — status bar alarm clock icon active.")
        } catch (e: SecurityException) {
            Log.w("FajrAlarmScheduler", "SecurityException on setAlarmClock: ${e.message} — trying setExactAndAllowWhileIdle")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        operationIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        operationIntent
                    )
                }
            } catch (se2: SecurityException) {
                Log.w("FajrAlarmScheduler", "SecurityException on setExactAndAllowWhileIdle: ${se2.message} — falling back to setAndAllowWhileIdle")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        operationIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        operationIntent
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("FajrAlarmScheduler", "Failed to schedule alarm: ${e.message}")
        }
    }

    fun cancelFajrAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            cancelReceiverAlarm(context, alarmManager, 1001, FAJR_ALARM_ACTION)
            cancelReceiverAlarm(context, alarmManager, 1002, FAJR_5_HOUR_WARNING_ACTION)
            cancelReceiverAlarm(context, alarmManager, 1003, FAJR_REMINDER_ACTION)
            cancelJumuahAlarm(context)
            Log.d("FajrAlarmScheduler", "Cancelled Fajr alarm (1001, 1002, 1003) & Jumu'ah alarm (1004)")
        } catch (e: Exception) {
            Log.e("FajrAlarmScheduler", "Failed to cancel Fajr alarm: ${e.message}")
        }
    }

    fun cancelJumuahAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            cancelReceiverAlarm(context, alarmManager, 1004, JUMUAH_ALARM_ACTION)
            Log.d("FajrAlarmScheduler", "Cancelled Jumu'ah alarm (1004)")
        } catch (e: Exception) {
            Log.e("FajrAlarmScheduler", "Failed to cancel Jumu'ah alarm: ${e.message}")
        }
    }

    fun scheduleThursdayPreBedtimePrompt(context: Context) {
        try {
            val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
            val bedtimeStr = prefs.getString("evening_protocol_time", "22:00") ?: "22:00"
            val parts = bedtimeStr.split(":")
            val bedHour = parts.getOrNull(0)?.toIntOrNull() ?: 22
            val bedMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0

            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, bedHour)
                set(Calendar.MINUTE, bedMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.HOUR_OF_DAY, -1) // 1 hour before bedtime
            }

            // Target the upcoming Thursday
            while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.THURSDAY || cal.timeInMillis <= now) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val sdf = SimpleDateFormat("EEE MM-dd hh:mm a", Locale.getDefault())
            scheduleReceiverAlarm(
                context, alarmManager, cal.timeInMillis, 1008, ACTION_THURSDAY_PRE_BEDTIME_PROMPT
            )
            Log.d("FajrAlarmScheduler", "Scheduled Thursday pre-bedtime prompt for: ${sdf.format(cal.time)}")
        } catch (e: Exception) {
            Log.e("FajrAlarmScheduler", "Failed to schedule Thursday pre-bedtime prompt: ${e.message}")
        }
    }

    fun cancelThursdayPreBedtimePrompt(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            cancelReceiverAlarm(context, alarmManager, 1008, ACTION_THURSDAY_PRE_BEDTIME_PROMPT)
            Log.d("FajrAlarmScheduler", "Cancelled Thursday pre-bedtime prompt (1008)")
        } catch (e: Exception) {
            Log.e("FajrAlarmScheduler", "Failed to cancel Thursday pre-bedtime prompt: ${e.message}")
        }
    }

    private fun cancelReceiverAlarm(context: Context, alarmManager: AlarmManager, requestCode: Int, action: String) {
        val intent = Intent(context, FajrAlarmReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun schedulePenaltyTimeout(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = System.currentTimeMillis() + (15 * 60 * 1000L)
            val intent = Intent(context, FajrAlarmReceiver::class.java).apply {
                action = PENALTY_TIMEOUT_ACTION
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 1005, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            safeScheduleAlarm(context, alarmManager, triggerAt, pendingIntent)
            val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
            Log.d("FajrAlarmScheduler", "Scheduled penalty timeout for: ${sdf.format(Date(triggerAt))}")
        } catch (e: Exception) {
            Log.e("FajrAlarmScheduler", "Failed to schedule penalty timeout: ${e.message}")
        }
    }

    fun cancelPenaltyTimeout(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, FajrAlarmReceiver::class.java).apply {
                action = PENALTY_TIMEOUT_ACTION
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 1005, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d("FajrAlarmScheduler", "Cancelled penalty timeout")
        } catch (e: Exception) {
            Log.e("FajrAlarmScheduler", "Failed to cancel penalty timeout: ${e.message}")
        }
    }

    fun scheduleReflectionWindowAlarm(context: Context, triggerAtMillis: Long) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, FajrAlarmReceiver::class.java).apply {
                action = REFLECTION_COMPLETE_ACTION
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 1006, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            safeScheduleAlarm(context, alarmManager, triggerAtMillis, pendingIntent)
            val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
            Log.d("FajrAlarmScheduler", "Scheduled reflection window completion alarm for: ${sdf.format(Date(triggerAtMillis))}")
        } catch (e: Exception) {
            Log.e("FajrAlarmScheduler", "Failed to schedule reflection window completion alarm: ${e.message}")
        }
    }

    fun cancelReflectionWindowAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, FajrAlarmReceiver::class.java).apply {
                action = REFLECTION_COMPLETE_ACTION
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 1006, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d("FajrAlarmScheduler", "Cancelled reflection window completion alarm")
        } catch (e: Exception) {
            Log.e("FajrAlarmScheduler", "Failed to cancel reflection window completion alarm: ${e.message}")
        }
    }

    fun scheduleTadabburAlarm(context: Context, isSecondaryFajr: Boolean = false) {
        try {
            val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
            val isActive = prefs.getBoolean("tadabbur_commitment_active", false)
            val untilMs = prefs.getLong("tadabbur_commitment_until_ms", 0L)
            val nowMs = System.currentTimeMillis()

            if (!isSecondaryFajr && (!isActive || nowMs > untilMs)) {
                Log.d("FajrAlarmScheduler", "Tadabbur commitment is inactive or expired. Cancelling alarm.")
                cancelTadabburAlarm(context)
                return
            }

            val savedLat = prefs.getFloat("user_lat", Float.NaN).toDouble()
            val savedLng = prefs.getFloat("user_lng", Float.NaN).toDouble()
            val lat = if (savedLat.isNaN() || savedLat == 0.0) 21.4225 else savedLat
            val lng = if (savedLng.isNaN() || savedLng == 0.0) 39.8262 else savedLng
            val tzOffset = TimeZone.getDefault().getOffset(nowMs) / 3600000.0

            val calculator = PrayerTimesCalculator(lat, lng, tzOffset)
            val now = Date(nowMs)
            val timesToday = calculator.calculateTimes(now)
            val targetToday = timesToday.sunrise.timeInMillis - (7 * 60 * 1000L)

            val triggerMs = if (targetToday > nowMs) {
                targetToday
            } else {
                if (isSecondaryFajr) {
                    Log.d("FajrAlarmScheduler", "Secondary Fajr opportunity target today has already passed.")
                    return
                }
                val timesTomorrow = calculator.calculateTimes(Date(nowMs + 86400000L))
                timesTomorrow.sunrise.timeInMillis - (7 * 60 * 1000L)
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, FajrAlarmReceiver::class.java).apply {
                action = TADABBUR_ALARM_ACTION
                putExtra(EXTRA_IS_SECONDARY_FAJR, isSecondaryFajr)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 1010, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            safeScheduleAlarm(context, alarmManager, triggerMs, pendingIntent)
            val sdf = SimpleDateFormat("EEE MM-dd hh:mm a", Locale.getDefault())
            Log.d("FajrAlarmScheduler", "Scheduled Tadabbur alarm (secondary=$isSecondaryFajr) for: ${sdf.format(Date(triggerMs))}")
        } catch (e: Exception) {
            Log.e("FajrAlarmScheduler", "Failed to schedule Tadabbur alarm: ${e.message}")
        }
    }

    fun cancelTadabburAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            cancelReceiverAlarm(context, alarmManager, 1010, TADABBUR_ALARM_ACTION)
            Log.d("FajrAlarmScheduler", "Cancelled Tadabbur alarm (1010)")
        } catch (e: Exception) {
            Log.e("FajrAlarmScheduler", "Failed to cancel Tadabbur alarm: ${e.message}")
        }
    }
}
