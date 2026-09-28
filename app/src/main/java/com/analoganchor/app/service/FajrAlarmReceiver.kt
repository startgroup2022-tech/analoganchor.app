package com.analoganchor.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.analoganchor.app.MainActivity
import com.analoganchor.app.R
import com.analoganchor.app.data.AppDatabase
import com.analoganchor.app.data.SessionLog
import com.analoganchor.app.viewmodel.AnchorViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FajrAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("FajrAlarmReceiver", "Broadcast Received! Action: ${intent.action}")
        acquireWakeLock(context)

        when (intent.action) {
            ACTION_REFLECTION_COMPLETE -> handleReflectionComplete(context)
            ACTION_FAJR_5_HOUR_WARNING -> showFiveHourWarning(context)
            ACTION_FAJR_REMINDER, FajrAlarmScheduler.FAJR_REMINDER_ACTION -> showNightlyReminder(context)
            ACTION_ACKNOWLEDGE_ISHA, FajrAlarmScheduler.ACTION_ACKNOWLEDGE_ISHA -> handleAcknowledgeIsha(context)
            ACTION_CHECK_ISHA_DORMANCY, FajrAlarmScheduler.ACTION_CHECK_ISHA_DORMANCY -> handleCheckIshaDormancy(context)
            ACTION_THURSDAY_PRE_BEDTIME_PROMPT, FajrAlarmScheduler.ACTION_THURSDAY_PRE_BEDTIME_PROMPT -> handleThursdayPreBedtimePrompt(context)
            ACTION_JUMUAH_ALARM -> triggerJumuahAlarm(context)
            ACTION_TADABBUR_ALARM, FajrAlarmScheduler.TADABBUR_ALARM_ACTION -> handleTadabburAlarm(context, intent.getBooleanExtra(FajrAlarmScheduler.EXTRA_IS_SECONDARY_FAJR, false))
            ACTION_STOP_TADABBUR_ALARM -> handleStopTadabburAlarm(context)
            FajrAlarmScheduler.PENALTY_TIMEOUT_ACTION -> handlePenaltyTimeout(context)
            ACTION_FAJR_ALARM, null -> triggerFajrAlarm(context)
            else -> Log.w("FajrAlarmReceiver", "Unknown action received: ${intent.action}. Ignoring.")
        }
    }

    private fun showNightlyReminder(context: Context) {
        val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        Log.d("FajrAlarmReceiver", "Bedtime & Accountability reminder triggered.")
        ensureChannel(context)
        vibrateOnce(context)

        val isEnglish = prefs.getBoolean("is_english", true)
        val title = if (isEnglish) "Bedtime & Accountability" else "موعد النوم والمساءلة"
        val quote = "Never stay up late for something you wouldn't wake UP early for"

        // Lock distracting apps (social media) immediately at bedtime cutoff
        ZenithAccessibilityService.enforceBedtimeImmediately()

        // Tapping the notification body or action button acknowledges Isha prayer & sleep
        val ackIntent = Intent(context, FajrAlarmReceiver::class.java).apply {
            action = ACTION_ACKNOWLEDGE_ISHA
        }
        val ackPendingIntent = PendingIntent.getBroadcast(
            context,
            10031,
            ackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actionBtnText = if (isEnglish) "✓ Prayed Isha & Ready to Sleep" else "✓ صليت العشاء ومستعد للنوم"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(quote)
            .setStyle(NotificationCompat.BigTextStyle().bigText(quote))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(ackPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.checkbox_on_background, actionBtnText, ackPendingIntent)
            .build()

        notificationManager(context).notify(1003, notification)

        val nowMs = System.currentTimeMillis()
        prefs.edit().putLong("bedtime_reminder_last_trigger_ms", nowMs).apply()

        // Check Thursday Covenant penalty: if Thursday night and Fajr alarm is still disabled
        val cal = Calendar.getInstance()
        val isThursday = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.THURSDAY)
        val enabled = prefs.getBoolean("fajr_alarm_enabled", false)
        val isNfcLocked = prefs.getBoolean("fajr_alarm_nfc_locked", false)
        val repeatUntilMs = prefs.getLong("fajr_nfc_repeat_until_ms", 0L)
        val isWeeklyRepeatActive = repeatUntilMs > 0L && System.currentTimeMillis() < repeatUntilMs
        val isArmed = enabled || isNfcLocked || isWeeklyRepeatActive

        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(nowMs))
        val alreadyPenalizedThursday = prefs.getBoolean("thursday_unarmed_penalized_$todayKey", false)

        if (isThursday && !isArmed && !alreadyPenalizedThursday) {
            prefs.edit().putBoolean("thursday_unarmed_penalized_$todayKey", true).apply()
            val currentPoints = prefs.getInt("reserve_points", 0)
            val updatedPoints = currentPoints - 50
            prefs.edit()
                .putInt("reserve_points", updatedPoints)
                .putLong("last_penalty_timestamp", nowMs)
                .putLong("last_ledger_transaction_timestamp", nowMs)
                .putString("last_ledger_transaction_type", "PENALTY")
                .apply()

            AnchorViewModel.instance?.let { vm ->
                vm.recalculateTodayPrayerStatus()
                vm.recalculateCovenantStats()
            }

            try {
                CoroutineScope(Dispatchers.IO).launch {
                    AppDatabase.getDatabase(context).sessionLogDao().insertLog(
                        SessionLog(
                            action = "Unarmed Thursday Covenant: Fajr & Jumu'ah Penalty (-50 PTS)",
                            pointsChange = -50,
                            status = "penalty"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("FajrAlarmReceiver", "Failed to log Thursday penalty: ${e.message}")
            }

            val penTitle = if (isEnglish) "Friday Covenant Missed (-50 PTS)" else "فاتك عهد الجمعة (-50 نقطة)"
            val penMsg = if (isEnglish) {
                "Fajr alarm was not set before Thursday bedtime cutoff. Applied Fajr (-10) & Jumu'ah (-40) penalties."
            } else {
                "لم يتم ضبط منبه الفجر قبل وقت النوم المحدد ليوم الخميس. تم تطبيق عقوبة الفجر (-10) والجمعة (-40)."
            }

            val penNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(penTitle)
                .setContentText(penMsg)
                .setStyle(NotificationCompat.BigTextStyle().bigText(penMsg))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            notificationManager(context).notify(NOTIFICATION_THURSDAY_PENALTY_ID, penNotification)
        }

        // Reschedule nightly reminder and Thursday pre-bedtime prompt
        FajrAlarmScheduler.scheduleNightlyReminder(context)
        FajrAlarmScheduler.scheduleThursdayPreBedtimePrompt(context)

        // Schedule passive safety net check 45 min later
        FajrAlarmScheduler.scheduleIshaDormancyCheck(context, nowMs + (45 * 60 * 1000L))
    }

    private fun handleThursdayPreBedtimePrompt(context: Context) {
        val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("fajr_alarm_enabled", false)
        val isNfcLocked = prefs.getBoolean("fajr_alarm_nfc_locked", false)
        val repeatUntilMs = prefs.getLong("fajr_nfc_repeat_until_ms", 0L)
        val isWeeklyRepeatActive = repeatUntilMs > 0L && System.currentTimeMillis() < repeatUntilMs
        val isArmed = enabled || isNfcLocked || isWeeklyRepeatActive

        if (!isArmed) {
            Log.d("FajrAlarmReceiver", "Thursday pre-bedtime prompt: Fajr alarm is unarmed. Showing notification.")
            ensureChannel(context)
            val isEnglish = prefs.getBoolean("is_english", true)
            val title = if (isEnglish) "Friday Covenant: Set Fajr Alarm" else "عهد الجمعة: اضبط منبه الفجر"
            val content = if (isEnglish) {
                "Set your Friday Fajr alarm before bedtime. A non-reversible Jumu'ah alarm will also be bound."
            } else {
                "اضبط منبه فجر الجمعة قبل موعد نومك لربط منبه صلاة الجمعة التلقائي."
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                10081,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build()

            notificationManager(context).notify(NOTIFICATION_THURSDAY_PROMPT_ID, notification)
        }

        // Reschedule for next Thursday
        FajrAlarmScheduler.scheduleThursdayPreBedtimePrompt(context)
    }

    private fun handleAcknowledgeIsha(context: Context) {
        val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        prefs.edit()
            .putBoolean("prayer_verified_isha_$todayKey", true)
            .apply()

        notificationManager(context).cancel(1003)

        AnchorViewModel.instance?.let { vm ->
            vm.recalculateTodayPrayerStatus()
            vm.recalculateCovenantStats()
        }

        val isEnglish = prefs.getBoolean("is_english", true)
        val toastMsg = if (isEnglish) {
            "✓ Isha prayer verified (+40 PTS). Sleep well!"
        } else {
            "✓ تم توثيق صلاة العشاء (+40 نقطة). نوم هنيء!"
        }
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
        }

        try {
            CoroutineScope(Dispatchers.IO).launch {
                AppDatabase.getDatabase(context).sessionLogDao().insertLog(
                    SessionLog(action = "Isha Prayer Acknowledged (+40 PTS)", pointsChange = 0, status = "completed")
                )
            }
        } catch (_: Exception) {}
    }

    private fun handleCheckIshaDormancy(context: Context) {
        val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val alreadyVerified = prefs.getBoolean("prayer_verified_isha_$todayKey", false)
        if (alreadyVerified) {
            Log.d("FajrAlarmReceiver", "Isha already verified today. Dormancy check complete.")
            return
        }

        val bedtimeTriggerMs = prefs.getLong("bedtime_reminder_last_trigger_ms", 0L)
        val now = System.currentTimeMillis()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isScreenInteractive = powerManager?.isInteractive ?: false

        val lastScreenOn = prefs.getLong("last_screen_on_timestamp", 0L)
        val lastAccessibilityEvent = ZenithAccessibilityService.lastEventTime

        // Allow 5 minutes grace window right after reminder to put phone down
        val graceEndMs = bedtimeTriggerMs + (5 * 60 * 1000L)
        val wasActiveAfterGrace = (lastScreenOn > graceEndMs) ||
                (lastAccessibilityEvent > graceEndMs && (now - lastAccessibilityEvent) < 20 * 60 * 1000L)

        if (!isScreenInteractive && !wasActiveAfterGrace) {
            prefs.edit()
                .putBoolean("prayer_verified_isha_$todayKey", true)
                .putBoolean("prayer_verified_isha_passive_$todayKey", true)
                .apply()
            Log.d("FajrAlarmReceiver", "✓ Isha verified via passive phone dormancy safety net (+40 PTS).")
            notificationManager(context).cancel(1003)

            AnchorViewModel.instance?.let { vm ->
                vm.recalculateTodayPrayerStatus()
                vm.recalculateCovenantStats()
            }

            try {
                CoroutineScope(Dispatchers.IO).launch {
                    AppDatabase.getDatabase(context).sessionLogDao().insertLog(
                        SessionLog(action = "Isha Prayer Verified (Bedtime Dormancy +40 PTS)", pointsChange = 0, status = "completed")
                    )
                }
            } catch (_: Exception) {}
        } else {
            Log.d("FajrAlarmReceiver", "User was active past bedtime cutoff. Passive Isha verification withheld.")
        }
    }

    private fun showFiveHourWarning(context: Context) {
        val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("fajr_alarm_enabled", false)
        val isNfcLocked = prefs.getBoolean("fajr_alarm_nfc_locked", false)
        val repeatUntilMs = prefs.getLong("fajr_nfc_repeat_until_ms", 0L)
        val isWeeklyRepeatActive = repeatUntilMs > 0L && System.currentTimeMillis() < repeatUntilMs

        if (!isEnabled && !isNfcLocked && !isWeeklyRepeatActive) {
            Log.d("FajrAlarmReceiver", "Fajr 5-hour warning suppressed because Fajr alarm is disabled.")
            return
        }

        Log.d("FajrAlarmReceiver", "Fajr 5-hour warning triggered.")
        ensureChannel(context)
        vibrateOnce(context)

        val isEnglish = prefs.getBoolean("is_english", true)

        val title = if (isEnglish) "Fajr sleep threshold" else "عتبة نوم الفجر"
        val content = if (isEnglish) "Fajr is 5 hours away. Protect the minimum sleep window now." else "الفجر على بعد 5 ساعات. احمِ الحد الأدنى من نافذة النوم الآن."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .build()

        notificationManager(context).notify(NOTIFICATION_WARNING_ID, notification)
    }

    /**
     * Delegates Fajr alarm to FajrAlarmService (Foreground Service), with bulletproof fail-safe fallback.
     */
    private fun triggerFajrAlarm(context: Context) {
        val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("fajr_alarm_enabled", false)
        val isNfcLocked = prefs.getBoolean("fajr_alarm_nfc_locked", false)
        val repeatUntilMs = prefs.getLong("fajr_nfc_repeat_until_ms", 0L)
        val isWeeklyRepeatActive = repeatUntilMs > 0L && System.currentTimeMillis() < repeatUntilMs

        if (!isEnabled && !isNfcLocked && !isWeeklyRepeatActive) {
            Log.w("FajrAlarmReceiver", "Fajr alarm broadcast received but alarm is disabled. Suppressing alarm.")
            FajrAlarmScheduler.cancelFajrAlarm(context)
            return
        }

        Log.d("FajrAlarmReceiver", "Triggering Fajr alarm...")

        ensureChannel(context)
        prefs.edit()
            .putBoolean("fajr_alarm_ringing", true)
            .apply()

        val serviceIntent = Intent(context, FajrAlarmService::class.java).apply {
            action = FajrAlarmService.ACTION_START_FAJR
        }
        var serviceStarted = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            serviceStarted = true
            Log.d("FajrAlarmReceiver", "✅ Successfully started FajrAlarmService for Fajr alarm.")
        } catch (e: Exception) {
            Log.e("FajrAlarmReceiver", "⚠️ Failed to start FajrAlarmService: ${e.message} — launching fail-safe direct alarm", e)
        }

        if (!serviceStarted) {
            triggerFailSafeAlarm(context, isJumuah = false)
        }

        val isEnglish = prefs.getBoolean("is_english", true)
        val toastText = if (isEnglish) "Wake up. 30 mins until Fajr adhan." else "استيقظ. 30 دقيقة حتى أذان الفجر."
        Toast.makeText(context, toastText, Toast.LENGTH_LONG).show()
    }

    /**
     * Delegates Jumu'ah alarm to FajrAlarmService (Foreground Service), with bulletproof fail-safe fallback.
     */
    private fun triggerJumuahAlarm(context: Context) {
        Log.d("FajrAlarmReceiver", "Triggering Jumu'ah alarm...")

        ensureChannel(context)
        context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("jumuah_alarm_ringing", true)
            .apply()

        val serviceIntent = Intent(context, FajrAlarmService::class.java).apply {
            action = FajrAlarmService.ACTION_START_JUMUAH
        }
        var serviceStarted = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            serviceStarted = true
            Log.d("FajrAlarmReceiver", "✅ Successfully started FajrAlarmService for Jumu'ah alarm.")
        } catch (e: Exception) {
            Log.e("FajrAlarmReceiver", "⚠️ Failed to start FajrAlarmService for Jumu'ah: ${e.message} — launching fail-safe direct alarm", e)
        }

        if (!serviceStarted) {
            triggerFailSafeAlarm(context, isJumuah = true)
        }

        val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        val isEnglish = prefs.getBoolean("is_english", true)
        val toastText = if (isEnglish) "Friday Jumu'ah alarm ringing!" else "منبه الجمعة يرن الآن!"
        Toast.makeText(context, toastText, Toast.LENGTH_LONG).show()
    }

    private fun triggerFailSafeAlarm(context: Context, isJumuah: Boolean) {
        Log.w("FajrAlarmReceiver", "🚨 [FAIL_SAFE_TRIGGERED] Activating direct receiver alarm playback & full screen wake up.")
        startFailSafeAudioAndVibration(context)
        showFailSafeFullScreenNotification(context, isJumuah)
        FajrAlarmScheduler.schedulePenaltyTimeout(context)

        try {
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            Log.e("FajrAlarmReceiver", "Failed direct activity start from receiver: ${e.message}")
        }
    }

    /**
     * Handles the 15-minute penalty timeout fired by AlarmManager.
     * If alarm ringing flag is STILL TRUE -> user never dismissed -> DEDUCT POINTS.
     * If flag is FALSE -> user dismissed legitimately -> no penalty.
     */
    private fun handlePenaltyTimeout(context: Context) {
        Log.d("FajrAlarmReceiver", "Penalty timeout broadcast received.")
        val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)

        val isJumuahRinging = prefs.getBoolean("jumuah_alarm_ringing", false)
        val isFajrRinging = prefs.getBoolean("fajr_alarm_ringing", false)

        if (!isJumuahRinging && !isFajrRinging) {
            Log.d("FajrAlarmReceiver", "No alarm ringing at 15-min mark — legitimate dismiss. No penalty.")
            return
        }

        val isJumuah = isJumuahRinging
        Log.w("FajrAlarmReceiver", "15-minute timeout — applying ${if (isJumuah) "Jumu'ah (-40)" else "Fajr (-10)"} penalty.")

        // Stop the foreground service
        try {
            context.startService(Intent(context, FajrAlarmService::class.java).apply {
                action = FajrAlarmService.ACTION_STOP
            })
        } catch (e: Exception) {
            Log.e("FajrAlarmReceiver", "Failed to stop service during penalty: ${e.message}")
        }

        val pointsToDeduct = if (isJumuah) 40 else 10
        val actionLabel = if (isJumuah) "Missed Jumu'ah Alarm" else "Missed Fajr Alarm"
        val currentPoints = prefs.getInt("reserve_points", 0)
        val updatedPoints = currentPoints - pointsToDeduct

        val now = System.currentTimeMillis()
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))
        prefs.edit()
            .putBoolean("fajr_alarm_ringing", false)
            .putBoolean("jumuah_alarm_ringing", false)
            .putBoolean(if (isJumuah) "jumuah_alarm_missed" else "fajr_alarm_missed", true)
            .putBoolean(if (isJumuah) "jumuah_alarm_missed_$todayKey" else "fajr_alarm_missed_$todayKey", true)
            .putLong(if (isJumuah) "jumuah_alarm_missed_time" else "fajr_alarm_missed_time", now)
            .putInt("reserve_points", updatedPoints)
            .putLong("last_penalty_timestamp", now)
            .putLong("last_ledger_transaction_timestamp", now)
            .putString("last_ledger_transaction_type", "PENALTY")
            .apply()

        AnchorViewModel.instance?.let { vm ->
            vm.recalculateTodayPrayerStatus()
            vm.recalculateCovenantStats()
        }

        if (!isJumuah) {
            prefs.edit().putInt("mosque_streak", 0).apply()
        }

        // Log to Room DB
        try {
            CoroutineScope(Dispatchers.IO).launch {
                AppDatabase.getDatabase(context).sessionLogDao().insertLog(
                    SessionLog(action = actionLabel, pointsChange = -pointsToDeduct, status = "penalty")
                )
            }
        } catch (e: Exception) {
            Log.e("FajrAlarmReceiver", "Failed to insert penalty log: ${e.message}")
        }

        // Cancel alarm notifications
        val nm = notificationManager(context)
        nm.cancel(NOTIFICATION_ALARM_ID)
        nm.cancel(NOTIFICATION_JUMUAH_ALARM_ID)

        // Post sticky missed notification
        val isEnglish = prefs.getBoolean("is_english", true)
        val title = if (isJumuah) {
            if (isEnglish) "⚠️ Missed Jumu'ah Alarm (-40 pts)" else "⚠️ تم تفويت منبه الجمعة (-40 نقطة)"
        } else {
            if (isEnglish) "⚠️ Missed Fajr Alarm (-10 pts)" else "⚠️ تم تفويت منبه الفجر (-10 نقاط)"
        }
        val content = if (isJumuah) {
            if (isEnglish) "The Jumu'ah alarm rang for 15 minutes without response. 40 points deducted from reserve balance."
            else "رن منبه الجمعة لمدة 15 دقيقة دون استجابة. تم خصم 40 نقطة من النقاط الاحتياطية."
        } else {
            if (isEnglish) "The Fajr alarm rang for 15 minutes without response. 10 points deducted from reserve balance."
            else "رن منبه الفجر لمدة 15 دقيقة دون استجابة. تم خصم 10 نقاط من النقاط الاحتياطية."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(PendingIntent.getActivity(context, 0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()

        nm.notify(NOTIFICATION_MISSED_ALARM_ID, notification)

        if (isJumuah) {
            FajrAlarmScheduler.scheduleJumuahAlarm(context)
        } else {
            // Automatically trigger the Tadabbur reminder as a secondary opportunity to catch Fajr Prayer
            FajrAlarmScheduler.scheduleTadabburAlarm(context, isSecondaryFajr = true)

            // Guarantee Day 2 Fajr alarm is rescheduled only if enabled or locked!
            val fajrEnabled = prefs.getBoolean("fajr_alarm_enabled", false)
            val isNfcLocked = prefs.getBoolean("fajr_alarm_nfc_locked", false)
            val repeatUntilMs = prefs.getLong("fajr_nfc_repeat_until_ms", 0L)
            val isWeeklyRepeatActive = repeatUntilMs > 0L && System.currentTimeMillis() < repeatUntilMs

            if (fajrEnabled || isNfcLocked || isWeeklyRepeatActive) {
                val savedLat = prefs.getFloat("user_lat", Float.NaN).toDouble()
                val savedLng = prefs.getFloat("user_lng", Float.NaN).toDouble()
                val lat = if (savedLat.isNaN() || savedLat == 0.0) 21.4225 else savedLat
                val lng = if (savedLng.isNaN() || savedLng == 0.0) 39.8262 else savedLng
                FajrAlarmScheduler.scheduleFajrAlarm(context, lat, lng)
                Log.d("FajrAlarmReceiver", "✅ Rescheduled Day 2 Fajr alarm after penalty timeout.")
            } else {
                FajrAlarmScheduler.cancelFajrAlarm(context)
                Log.d("FajrAlarmReceiver", "⏹️ Fajr alarm disabled; suppressed Day 2 rescheduling.")
            }
        }
    }

    private fun mainActivityPendingIntent(context: Context): PendingIntent {
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun handleReflectionComplete(context: Context) {
        Log.d("FajrAlarmReceiver", "Reflection window complete alarm received.")
        val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        val currentState = prefs.getString("emergency_state", "LOCKED")
        if (currentState == "COOLING") {
            prefs.edit()
                .putString("emergency_state", "READY_TO_REQUEST")
                .remove("emergency_cooldown_start_epoch")
                .apply()
        }

        vibrateReflectionComplete(context)
        showReflectionCompleteNotification(context)
    }

    private fun vibrateReflectionComplete(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            val pattern = longArrayOf(0, 400, 200, 400, 200, 600, 200, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.e("FajrAlarmReceiver", "Failed to vibrate for reflection complete: ${e.message}")
        }
    }

    private fun showReflectionCompleteNotification(context: Context) {
        try {
            ensureChannel(context)
            val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
            val isEnglish = prefs.getBoolean("is_english", true)

            val title = if (isEnglish) "Reflection Window Complete" else "انتهت فترة التفكير"
            val content = if (isEnglish) "Your reflection window has ended. Tap to enter your access code."
                else "انتهت فترة التفكير. انقر لإدخال رمز الدخول."

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(mainActivityPendingIntent(context))
                .build()

            notificationManager(context).notify(NOTIFICATION_REFLECTION_COMPLETE_ID, notification)
        } catch (e: Exception) {
            Log.e("FajrAlarmReceiver", "Failed to show reflection complete notification: ${e.message}")
        }
    }

    private fun vibrateOnce(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } catch (e: Exception) {
            Log.e("FajrAlarmReceiver", "Failed to start single vibration: ${e.message}")
        }
    }

    private fun handleTadabburAlarm(context: Context, isSecondaryFajr: Boolean) {
        try {
            ensureChannel(context)
            val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
            val isEnglish = prefs.getBoolean("is_english", true)

            prefs.edit().putBoolean("tadabbur_alarm_ringing", true).apply()
            AnchorViewModel.instance?.setTadabburAlarmRinging(true)

            startFailSafeAudioAndVibration(context)

            val title = if (isSecondaryFajr) {
                if (isEnglish) "Secondary Fajr Window & Tadabbur" else "فرصة الفجر الثانية والتدبّر"
            } else {
                if (isEnglish) "Tadabbur Ritual: Meet yourself at the Anchor" else "جلسة التدبّر: التقِ بنفسك عند المرسى"
            }

            val content = if (isSecondaryFajr) {
                if (isEnglish) "7 min before sunrise. Catch Fajr prayer and return to your White Glass."
                else "قبل الشروق بـ ٧ دقائق. أدرك صلاة الفجر والتقِ بنفسك عند اللوحة البيضاء."
            } else {
                if (isEnglish) "7 min before sunrise. Head to the White Glass and tap the Anchor."
                else "قبل الشروق بـ ٧ دقائق. توجّه للوحة البيضاء وانقر على المرسى."
            }

            val openAnchorIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("target_tab", "ANCHOR")
                putExtra("tadabbur_wake", true)
            }
            val openPendingIntent = PendingIntent.getActivity(
                context, 10101, openAnchorIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val simTapIntent = Intent(context, FajrAlarmReceiver::class.java).apply {
                action = ACTION_STOP_TADABBUR_ALARM
            }
            val simTapPendingIntent = PendingIntent.getBroadcast(
                context, 10102, simTapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val simActionLabel = if (isEnglish) "⚡ SIMULATE ANCHOR NFC TAP" else "⚡ محاكاة مسح NFC للمرساة"

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(openPendingIntent)
                .addAction(android.R.drawable.ic_menu_compass, simActionLabel, simTapPendingIntent)
                .setAutoCancel(true)
                .setOngoing(true)
                .build()

            notificationManager(context).notify(NOTIFICATION_TADABBUR_ALARM_ID, notification)

            val isActive = prefs.getBoolean("tadabbur_commitment_active", false)
            val untilMs = prefs.getLong("tadabbur_commitment_until_ms", 0L)
            if (isActive && System.currentTimeMillis() < untilMs) {
                FajrAlarmScheduler.scheduleTadabburAlarm(context, isSecondaryFajr = false)
            }
        } catch (e: Exception) {
            Log.e("FajrAlarmReceiver", "Failed to handle Tadabbur alarm: ${e.message}")
        }
    }

    private fun handleStopTadabburAlarm(context: Context) {
        try {
            stopTadabburAlarm(context)
            AnchorViewModel.instance?.simulateTadabburAnchorTap()
        } catch (e: Exception) {
            Log.e("FajrAlarmReceiver", "Failed to handle stop Tadabbur alarm: ${e.message}")
        }
    }

    companion object {
        const val ACTION_TADABBUR_ALARM = "com.analoganchor.app.action.TADABBUR_ALARM"
        const val ACTION_STOP_TADABBUR_ALARM = "com.analoganchor.app.action.STOP_TADABBUR_ALARM"
        const val NOTIFICATION_TADABBUR_ALARM_ID = 2010
        const val ACTION_REFLECTION_COMPLETE = "com.analoganchor.app.action.REFLECTION_COMPLETE"
        const val ACTION_FAJR_ALARM = "com.analoganchor.app.action.FAJR_ALARM"
        const val ACTION_FAJR_5_HOUR_WARNING = "com.analoganchor.app.action.FAJR_5HOUR_WARNING"
        const val ACTION_FAJR_REMINDER = "com.analoganchor.app.action.FAJR_REMINDER"
        const val ACTION_ACKNOWLEDGE_ISHA = "com.analoganchor.app.action.ACKNOWLEDGE_ISHA"
        const val ACTION_CHECK_ISHA_DORMANCY = "com.analoganchor.app.action.CHECK_ISHA_DORMANCY"
        const val ACTION_THURSDAY_PRE_BEDTIME_PROMPT = "com.analoganchor.app.action.THURSDAY_PRE_BEDTIME_PROMPT"
        const val ACTION_JUMUAH_ALARM = "com.analoganchor.app.action.JUMUAH_ALARM"
        private const val CHANNEL_ID = "fajr_alarm_channel_v2"
        const val NOTIFICATION_ALARM_ID = 2002
        const val NOTIFICATION_WARNING_ID = 2003
        const val NOTIFICATION_MISSED_ALARM_ID = 2004
        const val NOTIFICATION_JUMUAH_ALARM_ID = 2005
        const val NOTIFICATION_MISSED_JUMUAH_ID = 2006
        const val NOTIFICATION_REFLECTION_COMPLETE_ID = 2007
        const val NOTIFICATION_THURSDAY_PROMPT_ID = 2008
        const val NOTIFICATION_THURSDAY_PENALTY_ID = 2009

        private var failSafeMediaPlayer: MediaPlayer? = null
        private var failSafeVibrator: Vibrator? = null
        private var failSafeWakeLock: PowerManager.WakeLock? = null

        fun acquireWakeLock(context: Context) {
            try {
                if (failSafeWakeLock?.isHeld != true) {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    failSafeWakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "AnalogAnchor:FajrReceiverWakeLock"
                    )
                    failSafeWakeLock?.acquire(15 * 60 * 1000L)
                }
            } catch (e: Exception) {
                Log.e("FajrAlarmReceiver", "Failed acquireWakeLock: ${e.message}")
            }
        }

        fun startFailSafeAudioAndVibration(context: Context) {
            try {
                stopFailSafeAudioAndVibration()
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build()

                val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
                val customUriStr = prefs.getString("fajr_alarm_ringtone_uri", null)

                var player: MediaPlayer? = null
                if (!customUriStr.isNullOrEmpty() && customUriStr != "default") {
                    try {
                        player = MediaPlayer().apply {
                            setDataSource(context, Uri.parse(customUriStr))
                            setAudioAttributes(audioAttributes)
                            isLooping = true
                            prepare()
                            start()
                        }
                    } catch (_: Exception) {}
                }
                if (player == null) {
                    try {
                        val rawUri = Uri.parse("android.resource://${context.packageName}/${R.raw.fajr_default_ringtone}")
                        player = MediaPlayer().apply {
                            setDataSource(context, rawUri)
                            setAudioAttributes(audioAttributes)
                            isLooping = true
                            prepare()
                            start()
                        }
                    } catch (_: Exception) {}
                }
                if (player == null) {
                    try {
                        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        player = MediaPlayer().apply {
                            setDataSource(context, defaultUri)
                            setAudioAttributes(audioAttributes)
                            isLooping = true
                            prepare()
                            start()
                        }
                    } catch (_: Exception) {}
                }
                failSafeMediaPlayer = player

                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                failSafeVibrator = vibrator
                val timings = longArrayOf(0, 800, 400)
                val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(timings, amplitudes, 0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        vibrator.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build())
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    }
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(timings, 0)
                }
            } catch (e: Exception) {
                Log.e("FajrAlarmReceiver", "Failed startFailSafeAudioAndVibration: ${e.message}")
            }
        }

        fun stopFailSafeAudioAndVibration() {
            try {
                failSafeMediaPlayer?.apply {
                    if (isPlaying) stop()
                    release()
                }
                failSafeMediaPlayer = null
            } catch (_: Exception) {}
            try {
                failSafeVibrator?.cancel()
                failSafeVibrator = null
            } catch (_: Exception) {}
            try {
                if (failSafeWakeLock?.isHeld == true) {
                    failSafeWakeLock?.release()
                }
                failSafeWakeLock = null
            } catch (_: Exception) {}
        }

        fun showFailSafeFullScreenNotification(context: Context, isJumuah: Boolean) {
            try {
                ensureChannel(context)
                val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
                val isEnglish = prefs.getBoolean("is_english", true)

                val title = if (isJumuah) {
                    if (isEnglish) "Friday Jumu'ah Alarm" else "منبه صلاة الجمعة"
                } else {
                    if (isEnglish) "Fajr alarm calling" else "منبه الفجر يدعو"
                }
                val content = if (isJumuah) {
                    if (isEnglish) "Time to prepare for Friday Jumu'ah before 1st Azan." else "حان وقت الاستعداد لصلاة الجمعة قبل الأذان الأول."
                } else {
                    if (isEnglish) "Wake up. 30 minutes until Fajr adhan." else "استيقظ. 30 دقيقة حتى أذان الفجر."
                }

                val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val fullScreenPendingIntent = PendingIntent.getActivity(
                    context, 0, fullScreenIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setOngoing(true)
                    .build()

                val notifId = if (isJumuah) NOTIFICATION_JUMUAH_ALARM_ID else NOTIFICATION_ALARM_ID
                notificationManager(context).notify(notifId, notification)
            } catch (e: Exception) {
                Log.e("FajrAlarmReceiver", "Failed showFailSafeFullScreenNotification: ${e.message}")
            }
        }

        fun notificationManager(context: Context): NotificationManager {
            return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Fajr Alarm Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Sleep discipline warnings and Fajr alarms"
                setSound(null, null)
                enableLights(true)
                enableVibration(true)
                setBypassDnd(true)
            }
            notificationManager(context).createNotificationChannel(channel)
        }

        /**
         * Sends SILENCE to FajrAlarmService — stops audio & vibration only.
         * The service stays alive. The penalty timeout keeps ticking.
         */
        fun stopAlarmAudio(context: Context) {
            stopFailSafeAudioAndVibration()
            try {
                context.startService(Intent(context, FajrAlarmService::class.java).apply {
                    action = FajrAlarmService.ACTION_SILENCE
                })
            } catch (e: Exception) {
                Log.e("FajrAlarmReceiver", "Failed to send SILENCE to service: ${e.message}")
            }
        }

        /** Alias for backward compatibility */
        fun stopRingtone(context: Context) = stopAlarmAudio(context)

        /**
         * Full STOP — stops service AND cancels penalty timeout.
         * Use this for LEGITIMATE dismissals only (NFC scan, step walk, Ayah, etc.)
         */
        fun stopServiceAndCancelPenalty(context: Context) {
            stopFailSafeAudioAndVibration()
            try {
                context.startService(Intent(context, FajrAlarmService::class.java).apply {
                    action = FajrAlarmService.ACTION_STOP
                })
            } catch (e: Exception) {
                Log.e("FajrAlarmReceiver", "Failed to send STOP to service: ${e.message}")
            }
            FajrAlarmScheduler.cancelPenaltyTimeout(context)
        }

        /** Triggers session-end alarm via the foreground service */
        fun triggerSessionEndAlarm(context: Context) {
            try {
                val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("session_end_alarm_ringing", true).apply()
                val intent = Intent(context, FajrAlarmService::class.java).apply {
                    action = FajrAlarmService.ACTION_START_SESSION_END
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("FajrAlarmReceiver", "Failed to trigger session end alarm: ${e.message}")
            }
        }

        /** Stops session-end alarm via the foreground service */
        fun stopSessionEndAlarm(context: Context) {
            try {
                val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("session_end_alarm_ringing", false).apply()
                context.startService(Intent(context, FajrAlarmService::class.java).apply {
                    action = FajrAlarmService.ACTION_STOP
                })
            } catch (e: Exception) {
                Log.e("FajrAlarmReceiver", "Failed to stop session end alarm: ${e.message}")
            }
        }

        fun stopTadabburAlarm(context: Context) {
            try {
                val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("tadabbur_alarm_ringing", false).apply()
                stopFailSafeAudioAndVibration()
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_TADABBUR_ALARM_ID)
                context.startService(Intent(context, FajrAlarmService::class.java).apply {
                    action = FajrAlarmService.ACTION_STOP
                })
            } catch (e: Exception) {
                Log.e("FajrAlarmReceiver", "Failed to stop Tadabbur alarm: ${e.message}")
            }
        }

        fun cancelMissedNotification(context: Context) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_MISSED_ALARM_ID)
            } catch (e: Exception) {
                Log.e("FajrAlarmReceiver", "Failed to cancel missed alarm notification: ${e.message}")
            }
        }

        fun showMissedFajrNotification(context: Context, missedPoints: Int = 10) {
            try {
                ensureChannel(context)
                val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
                val isEnglish = prefs.getBoolean("is_english", true)
                val title = if (isEnglish) "⚠️ Missed Fajr Alarm (-$missedPoints pts)" else "⚠️ تم تفويت منبه الفجر (-$missedPoints نقطة)"
                val content = if (isEnglish) {
                    "Fajr prayer was missed without attendance or confirmation. $missedPoints points deducted from reserve balance."
                } else {
                    "تم تفويت صلاة الفجر دون تسجيل حضور أو تأكيد الاستيقاظ. تم خصم $missedPoints نقطة من النقاط الاحتياطية."
                }

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setContentIntent(PendingIntent.getActivity(context, 0,
                        Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    .build()

                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_MISSED_ALARM_ID, notification)
            } catch (e: Exception) {
                Log.e("FajrAlarmReceiver", "Failed to show missed Fajr notification: ${e.message}")
            }
        }

        fun showMissedJumuahNotification(context: Context) {
            try {
                ensureChannel(context)
                val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
                val isEnglish = prefs.getBoolean("is_english", true)
                val title = if (isEnglish) "⚠️ Missed Friday Jumu'ah (-40 pts)" else "⚠️ تم تفويت صلاة الجمعة (-40 نقطة)"
                val content = if (isEnglish) "Friday Jumu'ah prayer was missed without attendance. 40 points deducted."
                else "تم تفويت صلاة الجمعة دون تسجيل الحضور. تم خصم 40 نقطة من النقاط الاحتياطية."

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setContentIntent(PendingIntent.getActivity(context, 0,
                        Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    .build()

                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_MISSED_JUMUAH_ID, notification)
            } catch (e: Exception) {
                Log.e("FajrAlarmReceiver", "Failed to show missed Jumu'ah notification: ${e.message}")
            }
        }

        fun cancelMissedJumuahNotification(context: Context) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_MISSED_JUMUAH_ID)
            } catch (e: Exception) {
                Log.e("FajrAlarmReceiver", "Failed to cancel missed Jumu'ah notification: ${e.message}")
            }
        }

        fun cancelReflectionNotification(context: Context) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_REFLECTION_COMPLETE_ID)
            } catch (e: Exception) {
                Log.e("FajrAlarmReceiver", "Failed to cancel reflection notification: ${e.message}")
            }
        }
    }
}
