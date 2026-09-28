package com.analoganchor.app.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.analoganchor.app.MainActivity
import com.analoganchor.app.data.AppDatabase
import com.analoganchor.app.data.SessionLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrayerDndRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            ACTION_PRAYER_BUFFER_START -> {
                val prayerKey = intent.getStringExtra("prayer_key") ?: "dhuhr"
                val prayerNameEn = intent.getStringExtra("prayer_name_en") ?: "Prayer"
                val prayerNameAr = intent.getStringExtra("prayer_name_ar") ?: "الصلاة"
                val isExactAdhan = intent.getBooleanExtra("is_exact_adhan", false)
                handlePrayerBufferStart(context, prayerKey, prayerNameEn, prayerNameAr, isExactAdhan)
            }
            ACTION_PRAYER_BUFFER_END -> {
                val prayerKey = intent.getStringExtra("prayer_key") ?: "dhuhr"
                val prayerNameEn = intent.getStringExtra("prayer_name_en") ?: "Prayer"
                val prayerNameAr = intent.getStringExtra("prayer_name_ar") ?: "الصلاة"
                handlePrayerBufferEnd(context, prayerKey, prayerNameEn, prayerNameAr)
            }
            ACTION_ACTIVATE_PRAYER_DND_DIRECT -> {
                val prayerKey = intent.getStringExtra("prayer_key") ?: "dhuhr"
                val prayerNameEn = intent.getStringExtra("prayer_name_en") ?: "Prayer"
                val prayerNameAr = intent.getStringExtra("prayer_name_ar") ?: "الصلاة"
                handleDirectDndActivation(context, prayerKey, prayerNameEn, prayerNameAr)
            }
            ACTION_PRAYER_ADHAN_ARRIVED -> {
                val prayerKey = intent.getStringExtra("prayer_key") ?: "prayer"
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(BUFFER_ALERT_NOTIFICATION_ID)
                Log.d(TAG, "Adhan arrived for $prayerKey: dismissed pre-Adhan notification as Soft Landing preparation window closed.")
            }
            else -> {
                Log.d(TAG, "PrayerDndRestoreReceiver triggered. Restoring audio and DND.")
                restorePrayerDndState(context, triggerHaptic = true, showNotification = true)
            }
        }
    }

    companion object {
        const val TAG = "PrayerDndRestore"
        const val ACTION_RESTORE_PRAYER_DND = "com.analoganchor.app.action.RESTORE_PRAYER_DND"
        const val ACTION_PRAYER_BUFFER_START = "com.analoganchor.app.action.PRAYER_BUFFER_START"
        const val ACTION_PRAYER_BUFFER_END = "com.analoganchor.app.action.PRAYER_BUFFER_END"
        const val ACTION_ACTIVATE_PRAYER_DND_DIRECT = "com.analoganchor.app.action.ACTIVATE_PRAYER_DND_DIRECT"
        const val ACTION_PRAYER_ADHAN_ARRIVED = "com.analoganchor.app.action.PRAYER_ADHAN_ARRIVED"

        const val RESTORE_ALARM_REQUEST_CODE = 2001
        const val DIRECT_DND_REQ_CODE = 2002

        const val CHANNEL_ID = "prayer_soft_landing_restore_v3"
        const val BUFFER_ALERT_CHANNEL_ID = "prayer_soft_landing_alert_v3"
        const val BUFFER_ACTIVE_CHANNEL_ID = "prayer_soft_landing_active_v3"
        const val BUFFER_PENALTY_CHANNEL_ID = "prayer_soft_landing_penalty_v3"

        const val NOTIFICATION_ID = 4001
        const val BUFFER_ALERT_NOTIFICATION_ID = 4002
        const val BUFFER_ACTIVE_NOTIFICATION_ID = 4003
        const val MISSED_BUFFER_NOTIFICATION_ID = 4004

        // Extended Harmonic Ascent vibration rhythm with deep grounding coda (~4.87s total, longer than 3.36s chime)
        val HARMONIC_ASCENT_VIBE_TIMINGS = longArrayOf(
            0, 380, 180, 380, 180, 380, 360, 380, 180, 380, 180, 380, 360, 450, 200, 500
        )
        val HARMONIC_ASCENT_VIBE_AMPLITUDES = intArrayOf(
            0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 220, 0, 180
        )

        fun scheduleRestoreAlarm(context: Context, triggerAtMs: Long, prayerKey: String, prayerNameEn: String, prayerNameAr: String) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, PrayerDndRestoreReceiver::class.java).apply {
                    action = ACTION_RESTORE_PRAYER_DND
                    putExtra("prayer_key", prayerKey)
                    putExtra("prayer_name_en", prayerNameEn)
                    putExtra("prayer_name_ar", prayerNameAr)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    RESTORE_ALARM_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
                }
                Log.d(TAG, "Scheduled Soft Landing DND restore alarm for epoch: $triggerAtMs")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule restore alarm: ${e.message}", e)
            }
        }

        fun cancelRestoreAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, PrayerDndRestoreReceiver::class.java).apply {
                    action = ACTION_RESTORE_PRAYER_DND
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    RESTORE_ALARM_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                    Log.d(TAG, "Cancelled Soft Landing DND restore alarm.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel restore alarm: ${e.message}", e)
            }
        }

        fun handleDirectDndActivation(
            context: Context,
            prayerKey: String,
            prayerNameEn: String,
            prayerNameAr: String
        ) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)

            // Cancel the Stage 1 pre-Adhan notification
            nm.cancel(BUFFER_ALERT_NOTIFICATION_ID)

            val originalRinger = am.ringerMode
            val originalFilter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try { nm.currentInterruptionFilter } catch (_: Exception) { NotificationManager.INTERRUPTION_FILTER_ALL }
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }

            // 1. Activate DND & mute ringer
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm.isNotificationPolicyAccessGranted) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                }
                am.ringerMode = AudioManager.RINGER_MODE_SILENT
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enable DND mode directly: ${e.message}")
            }

            val nowMs = System.currentTimeMillis()
            // Buffer window duration: 35 minutes for Asr (+10 min extension), 25 minutes for other prayers
            val bufferDurationMs = if (prayerKey == "asr") 35 * 60 * 1000L else 25 * 60 * 1000L
            val restoreTimeMs = nowMs + bufferDurationMs
            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val prefKey = "prayer_verified_${prayerKey}_$todayKey"

            // 2. Save active state in preferences
            prefs.edit()
                .putBoolean("prayer_buffer_active", true)
                .putLong("prayer_buffer_start_time_ms", nowMs)
                .putLong("prayer_buffer_end_time_ms", restoreTimeMs)
                .putString("prayer_buffer_key", prayerKey)
                .putString("prayer_buffer_name_en", prayerNameEn)
                .putString("prayer_buffer_name_ar", prayerNameAr)
                .putInt("prayer_buffer_initial_ringer", originalRinger)
                .putInt("prayer_buffer_initial_filter", originalFilter)
                .putBoolean(prefKey, true)
                .apply()

            // 3. Update streak (Points reward removed: prayer is sacred obligation, not gamified child's play)
            val currentStreak = prefs.getInt("mosque_streak", 0) + 1
            prefs.edit().putInt("mosque_streak", currentStreak).apply()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AppDatabase.getDatabase(context).sessionLogDao().insertLog(
                        SessionLog(
                            action = "$prayerNameEn Soft Landing Verified",
                            pointsChange = 0,
                            status = "mosque",
                            startTime = nowMs,
                            endTime = restoreTimeMs
                        )
                    )
                } catch (_: Exception) {}
            }

            // 4. Schedule Stage 3 restore alarm
            scheduleRestoreAlarm(context, restoreTimeMs, prayerKey, prayerNameEn, prayerNameAr)

            // 5. Post Stage 2 (In-Flight Sanctuary) Notification
            postStage2SanctuaryNotification(context, prefs.getBoolean("is_english", false), prayerNameEn, prayerNameAr)
        }

        fun postStage2SanctuaryNotification(
            context: Context,
            isEnglish: Boolean,
            prayerNameEn: String,
            prayerNameAr: String
        ) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    BUFFER_ACTIVE_CHANNEL_ID,
                    if (isEnglish) "Active Soft Landing State" else "حالة الهبوط الناعم النشط",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = if (isEnglish) "Active Soft Landing state" else "حالة الهبوط الناعم النشط"
                    enableVibration(false)
                    setSound(null, null)
                }
                nm.createNotificationChannel(channel)
            }

            val title = if (isEnglish) "🛡️ Soft Landing Active • Phone Silenced" else "🛡️ الهبوط الناعم نشط • الهاتف صامت"
            val text = if (isEnglish)
                "Your phone is in Do Not Disturb. Restores automatically after prayer."
            else
                "هاتفك في وضع عدم الإزعاج لحضور الصلاة. سيُستعاد الصوت تلقائياً بعد انقضاء الوقت."

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("target_tab", "MOSQUE")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                BUFFER_ACTIVE_NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, BUFFER_ACTIVE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .build()

            nm.notify(BUFFER_ACTIVE_NOTIFICATION_ID, notification)
        }

        fun restorePrayerDndState(
            context: Context,
            triggerHaptic: Boolean = false,
            showNotification: Boolean = true
        ) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(BUFFER_ALERT_NOTIFICATION_ID)
            nm.cancel(BUFFER_ACTIVE_NOTIFICATION_ID)

            val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
            val initialRinger = prefs.getInt("prayer_buffer_initial_ringer", AudioManager.RINGER_MODE_NORMAL)
            val initialFilter = prefs.getInt("prayer_buffer_initial_filter", NotificationManager.INTERRUPTION_FILTER_ALL)
            val prayerNameEn = prefs.getString("prayer_buffer_name_en", "Prayer") ?: "Prayer"
            val prayerNameAr = prefs.getString("prayer_buffer_name_ar", "الصلاة") ?: "الصلاة"
            val isEnglish = prefs.getBoolean("is_english", false)

            // 1. Restore Do Not Disturb Interruption Filter
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm.isNotificationPolicyAccessGranted) {
                    nm.setInterruptionFilter(initialFilter)
                    Log.d(TAG, "Restored DND interruption filter to: $initialFilter")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring DND filter: ${e.message}")
            }

            // 2. Restore Audio Ringer Mode
            try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.ringerMode = initialRinger
                Log.d(TAG, "Restored audio ringer mode to: $initialRinger")
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring audio mode: ${e.message}")
            }

            // 3. Clear Buffer state in preferences
            prefs.edit()
                .putBoolean("prayer_buffer_active", false)
                .remove("prayer_buffer_end_time_ms")
                .remove("prayer_buffer_start_time_ms")
                .remove("prayer_buffer_key")
                .remove("prayer_buffer_name_en")
                .remove("prayer_buffer_name_ar")
                .apply()

            // 4. Subtle Gentle Haptic Pulse
            if (triggerHaptic) {
                try {
                    vibrateGentle(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not trigger haptic: ${e.message}")
                }
            }

            // 5. Post Stage 3 Outcome 3A completion notification
            if (showNotification) {
                try {
                    postRestoreNotification(context, isEnglish, prayerNameEn, prayerNameAr)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not post restore notification: ${e.message}")
                }
            }
        }

        private fun vibrateGentle(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vm?.defaultVibrator ?: context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(200L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(200L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(200L)
                }
            }
        }

        // Extended 2X Cycle + Outro Coda: ~4.87s total vibration, explicitly longer than the 3.36s chime
        private fun vibrateHarmonicAscent(context: Context) {
            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    val vibrator = vm?.defaultVibrator ?: context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.let {
                        val effect = if (it.hasAmplitudeControl()) {
                            VibrationEffect.createWaveform(HARMONIC_ASCENT_VIBE_TIMINGS, HARMONIC_ASCENT_VIBE_AMPLITUDES, -1)
                        } else {
                            VibrationEffect.createWaveform(HARMONIC_ASCENT_VIBE_TIMINGS, -1)
                        }
                        it.vibrate(effect, audioAttributes)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.let {
                        val effect = if (it.hasAmplitudeControl()) {
                            VibrationEffect.createWaveform(HARMONIC_ASCENT_VIBE_TIMINGS, HARMONIC_ASCENT_VIBE_AMPLITUDES, -1)
                        } else {
                            VibrationEffect.createWaveform(HARMONIC_ASCENT_VIBE_TIMINGS, -1)
                        }
                        it.vibrate(effect, audioAttributes)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.vibrate(HARMONIC_ASCENT_VIBE_TIMINGS, -1)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not vibrate harmonic ascent pulses: ${e.message}")
            }
        }

        // Synthesizes the exact B. Harmonic Ascent (تصاعد نغمي) 6-note signature via AudioTrack (~3.3s)
        private fun playHarmonicAscentSound(context: Context) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
                if (ringerMode == AudioManager.RINGER_MODE_SILENT) return

                Thread {
                    try {
                        val sampleRate = 44100
                        val totalDurationMs = 3360
                        val numSamples = (sampleRate * (totalDurationMs / 1000.0)).toInt()
                        val buffer = ShortArray(numSamples)

                        // 6 notes: Harmonic Ascent
                        // Cycle 1: E5 (659.25), G#5 (830.61), B5 (987.77)
                        // Cycle 2: G#5 (830.61), B5 (987.77), E6 (1318.51)
                        val freqs = doubleArrayOf(659.25, 830.61, 987.77, 830.61, 987.77, 1318.51)
                        val pulseDelaysMs = intArrayOf(0, 560, 1120, 1860, 2420, 2980)
                        val noteDurationSamples = (sampleRate * 0.38).toInt()
                        val attackSamples = (sampleRate * 0.015).toInt()

                        for (i in freqs.indices) {
                            val startSample = (pulseDelaysMs[i] * sampleRate / 1000)
                            val freq = freqs[i]
                            val angularFreq = 2.0 * Math.PI * freq / sampleRate

                            for (s in 0 until noteDurationSamples) {
                                val bufferIdx = startSample + s
                                if (bufferIdx >= numSamples) break

                                val envelope = if (s < attackSamples) {
                                    s.toDouble() / attackSamples
                                } else {
                                    Math.exp(-4.5 * (s - attackSamples) / noteDurationSamples)
                                }

                                val sampleVal = (Math.sin(angularFreq * s) * envelope * 24000).toInt()
                                val combined = (buffer[bufferIdx] + sampleVal).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                buffer[bufferIdx] = combined.toShort()
                            }
                        }

                        val minBufferSize = AudioTrack.getMinBufferSize(
                            sampleRate,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                        )
                        val audioTrack = AudioTrack(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build(),
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build(),
                            maxOf(buffer.size * 2, minBufferSize),
                            AudioTrack.MODE_STATIC,
                            AudioManager.AUDIO_SESSION_ID_GENERATE
                        )
                        audioTrack.write(buffer, 0, buffer.size)
                        audioTrack.play()

                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                audioTrack.stop()
                                audioTrack.release()
                            } catch (_: Exception) {}
                        }, (totalDurationMs + 200).toLong())
                    } catch (e: Exception) {
                        Log.w(TAG, "AudioTrack synthesis error: ${e.message}")
                    }
                }.start()
            } catch (e: Exception) {
                Log.w(TAG, "Could not play Harmonic Ascent sound: ${e.message}")
            }
        }

        // Stage 3 Outcome 3A: Restoration Notification
        private fun postRestoreNotification(context: Context, isEnglish: Boolean, prayerNameEn: String, prayerNameAr: String) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    if (isEnglish) "Soft Landing" else "الهبوط الناعم",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = if (isEnglish) "Soft Landing audio restoration" else "إشعارات استعادة الصوت بعد انقضاء الصلاة"
                    enableVibration(false)
                    setSound(null, null)
                }
                nm.createNotificationChannel(channel)
            }

            val title = if (isEnglish) "May Allah accept your prayer." else "تقبل الله طاعتكم"
            val body = if (isEnglish) "Your phone is out of Do Not Disturb mode." else "خرج هاتفك من وضع عدم الإزعاج وعاد إلى وضعه المعتاد."

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("target_tab", "MOSQUE")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setSound(null)
                .setVibrate(null)
                .setContentIntent(pendingIntent)
                .build()

            nm.notify(NOTIFICATION_ID, notification)
        }

        // Stage 3 Outcome 3B: Missed DND (-7 PTS) Accountability Notification
        private fun postMissedBufferPenaltyNotification(
            context: Context,
            prayerKey: String,
            prayerNameEn: String,
            prayerNameAr: String
        ) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val isEnglish = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
                .getBoolean("is_english", false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    BUFFER_PENALTY_CHANNEL_ID,
                    if (isEnglish) "Soft Landing Accountability" else "تنبيهات انضباط الهبوط الناعم",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = if (isEnglish) "Soft Landing accountability alerts" else "تنبيهات انضباط الهبوط الناعم"
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }

            val prayerArClean = prayerNameAr.replace("صلاة", "").replace("الصلاة", "").trim()
            val title = if (isEnglish) {
                "⚠️ Soft Landing Bypassed (-7 PTS)"
            } else {
                "⚠️ تجاوزت وقت الاستعداد لل$prayerArClean (-7 نقاط)"
            }

            val text = if (isEnglish) {
                "Stepping away brings clarity; staying plugged in only compounds mental fatigue."
            } else {
                "الانقطاع للصلاة يجدد صفاء ذهنك، ومواصلة الاندماج تضاعف إرهاقك."
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("target_tab", "MOSQUE")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                MISSED_BUFFER_NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, BUFFER_PENALTY_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            nm.notify(MISSED_BUFFER_NOTIFICATION_ID, notification)
        }

        // Stage 1: Pre-Adhan Soft Landing Nudge (5 mins before Adhan)
        private fun handlePrayerBufferStart(
            context: Context,
            prayerKey: String,
            prayerNameEn: String,
            prayerNameAr: String,
            isExactAdhan: Boolean = false
        ) {
            val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
            val isBufferActive = prefs.getBoolean("prayer_buffer_active", false)
            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val isAlreadyVerified = prefs.getBoolean("prayer_verified_${prayerKey}_$todayKey", false)

            if (isBufferActive || isAlreadyVerified) {
                Log.d(TAG, "Soft landing or prayer already active/verified for $prayerKey. Skipping alert.")
                return
            }

            Log.d(TAG, "Soft Landing alert for $prayerKey ($prayerNameEn), isExactAdhan=$isExactAdhan")
            val isEnglish = prefs.getBoolean("is_english", false)

            // 1. Extended vibration pulses with deep grounding coda (~4.87s total, longer than the 3.36s chime)
            vibrateHarmonicAscent(context)

            // 2. Play 2X Harmonic Ascent signature chime (AudioTrack, ~3.3s)
            playHarmonicAscentSound(context)

            // 3. Post Stage 1 notification with 1-Tap Quick Action Button
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        nm.deleteNotificationChannel("prayer_buffer_channel")
                        nm.deleteNotificationChannel("prayer_buffer_alert_channel")
                        nm.deleteNotificationChannel("prayer_buffer_alert_channel_v2")
                        nm.deleteNotificationChannel("prayer_buffer_active_channel")
                        nm.deleteNotificationChannel("prayer_buffer_penalty_channel")
                    } catch (_: Exception) {}

                    val channel = NotificationChannel(
                        BUFFER_ALERT_CHANNEL_ID,
                        if (isEnglish) "Soft Landing Alerts" else "تنبيهات الهبوط الناعم",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = if (isEnglish) "Alerts 5 minutes before Adhan for Soft Landing" else "تنبيهات قبل 5 دقائق من الأذان لبدء الهبوط الناعم"
                        enableVibration(true)
                        vibrationPattern = HARMONIC_ASCENT_VIBE_TIMINGS
                        setSound(null, null)
                    }
                    nm.createNotificationChannel(channel)
                }

                val prayerArClean = prayerNameAr.replace("صلاة", "").replace("الصلاة", "").trim()
                val prayerEnClean = prayerNameEn.replace("Prayer", "").replace("prayer", "").trim()

                val title = if (isEnglish) {
                    "$prayerEnClean Adhan is in 5 mins"
                } else {
                    "5 دقائق على أذان $prayerArClean"
                }

                // Quick Action Intent (direct broadcast to activate DND without opening UI)
                val dndDirectIntent = Intent(context, PrayerDndRestoreReceiver::class.java).apply {
                    action = ACTION_ACTIVATE_PRAYER_DND_DIRECT
                    putExtra("prayer_key", prayerKey)
                    putExtra("prayer_name_en", prayerNameEn)
                    putExtra("prayer_name_ar", prayerNameAr)
                }
                val dndPendingIntent = PendingIntent.getBroadcast(
                    context,
                    DIRECT_DND_REQ_CODE,
                    dndDirectIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val actionTitle = if (isEnglish) {
                    "Activate Soft Landing"
                } else {
                    "تفعيل الهبوط الناعم"
                }

                val dndAction = NotificationCompat.Action.Builder(
                    android.R.drawable.ic_lock_idle_alarm,
                    actionTitle,
                    dndPendingIntent
                ).build()

                // Content intent opens app dialog if tapped on body
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("target_tab", "MOSQUE")
                    putExtra("show_prayer_buffer_dialog", true)
                    putExtra("prayer_key", prayerKey)
                    putExtra("prayer_name_en", prayerNameEn)
                    putExtra("prayer_name_ar", prayerNameAr)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    BUFFER_ALERT_NOTIFICATION_ID,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, BUFFER_ALERT_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(title)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setSound(null)
                    .setVibrate(HARMONIC_ASCENT_VIBE_TIMINGS)
                    .setContentIntent(pendingIntent)
                    .addAction(dndAction)
                    .build()

                nm.notify(BUFFER_ALERT_NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.e(TAG, "Could not post buffer start notification: ${e.message}")
            }
        }

        private fun handlePrayerBufferEnd(context: Context, prayerKey: String, prayerNameEn: String, prayerNameAr: String) {
            Log.d(TAG, "Soft Landing window ended for $prayerKey ($prayerNameEn)")
            val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
            val isBufferActive = prefs.getBoolean("prayer_buffer_active", false)
            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val isAlreadyVerified = prefs.getBoolean("prayer_verified_${prayerKey}_$todayKey", false)

            if (isBufferActive || isAlreadyVerified) {
                // Buffer was active -> restore sound quietly with Outcome 3A
                restorePrayerDndState(context, triggerHaptic = true, showNotification = true)
            } else {
                // Buffer ended without DND activation: Outcome 3B (-7 PTS penalty)
                val penaltyKey = "prayer_missed_penalty_${prayerKey}_$todayKey"
                val penaltyAlreadyApplied = prefs.getBoolean(penaltyKey, false)
                if (!penaltyAlreadyApplied) {
                    prefs.edit().putBoolean(penaltyKey, true).apply()
                    val currentPoints = prefs.getInt("reserve_points", 0)
                    val now = System.currentTimeMillis()
                    prefs.edit()
                        .putInt("reserve_points", currentPoints - 7)
                        .putLong("last_penalty_timestamp", now)
                        .putLong("last_ledger_transaction_timestamp", now)
                        .putString("last_ledger_transaction_type", "PENALTY")
                        .apply()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            AppDatabase.getDatabase(context).sessionLogDao().insertLog(
                                SessionLog(
                                    action = "$prayerNameEn Soft Landing Bypassed (-7 PTS)",
                                    pointsChange = -7,
                                    status = "prayer_buffer_penalty"
                                )
                            )
                        } catch (_: Exception) {}
                    }
                }
                postMissedBufferPenaltyNotification(context, prayerKey, prayerNameEn, prayerNameAr)
            }
        }
    }
}
