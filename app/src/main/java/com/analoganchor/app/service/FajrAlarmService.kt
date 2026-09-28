package com.analoganchor.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.analoganchor.app.MainActivity
import com.analoganchor.app.R

class FajrAlarmService : Service() {

    // Instance variables — NOT static. They live as long as the service lives.
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var toneGenerator: ToneGenerator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val safetyHandler = Handler(Looper.getMainLooper())
    private val safetyStopRunnable = Runnable { stopAudio() }
    private val reRingRunnable = Runnable {
        val prefs = getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        val isFajrRinging = prefs.getBoolean("fajr_alarm_ringing", false)
        val isJumuahRinging = prefs.getBoolean("jumuah_alarm_ringing", false)
        val isSessionEndRinging = prefs.getBoolean("session_end_alarm_ringing", false)

        if (!isFajrRinging && !isJumuahRinging && !isSessionEndRinging) {
            Log.d(TAG, "Re-ring aborted: No alarm is currently active in prefs. Terminating service.")
            stopSelf()
            return@Runnable
        }

        // Extra guard for Fajr: if only Fajr is ringing, ensure it is not past sunrise
        if (isFajrRinging && !isJumuahRinging && !isSessionEndRinging) {
            val tzOffset = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0
            val savedLat = prefs.getFloat("user_lat", Float.NaN).toDouble()
            val savedLng = prefs.getFloat("user_lng", Float.NaN).toDouble()
            val lat = if (savedLat.isNaN() || savedLat == 0.0) 21.4225 else savedLat
            val lng = if (savedLng.isNaN() || savedLng == 0.0) 39.8262 else savedLng
            val times = com.analoganchor.app.data.PrayerTimesCalculator(lat, lng, tzOffset).calculateTimes(java.util.Date())
            val fajrCutoffMs = times.sunrise.timeInMillis.coerceAtLeast(times.fajr.timeInMillis + (30 * 60 * 1000L))
            if (System.currentTimeMillis() > fajrCutoffMs) {
                Log.w(TAG, "Re-ring aborted: Current time is past sunrise Fajr cutoff. Terminating service.")
                prefs.edit().putBoolean("fajr_alarm_ringing", false).apply()
                stopSelf()
                return@Runnable
            }
        }

        Log.d(TAG, "Re-ringing alarm after 2-minute silence period")
        startAudioAndVibration()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action: $action")

        when (action) {
            ACTION_START_FAJR -> {
                val prefs = getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
                val isEnabled = prefs.getBoolean("fajr_alarm_enabled", false)
                val isNfcLocked = prefs.getBoolean("fajr_alarm_nfc_locked", false)
                val repeatUntilMs = prefs.getLong("fajr_nfc_repeat_until_ms", 0L)
                val isWeeklyRepeatActive = repeatUntilMs > 0L && System.currentTimeMillis() < repeatUntilMs

                if (!isEnabled && !isNfcLocked && !isWeeklyRepeatActive) {
                    Log.w(TAG, "ACTION_START_FAJR received but alarm is disabled and not locked. Terminating service.")
                    stopSelf()
                    return START_NOT_STICKY
                }

                startForegroundWithNotification(FAJR_NOTIFICATION_ID, "fajr")
                startAudioAndVibration()
                FajrAlarmScheduler.schedulePenaltyTimeout(this)
            }
            ACTION_START_JUMUAH -> {
                startForegroundWithNotification(JUMUAH_NOTIFICATION_ID, "jumuah")
                startAudioAndVibration()
                FajrAlarmScheduler.schedulePenaltyTimeout(this)
            }
            ACTION_START_SESSION_END -> {
                startForegroundWithNotification(SESSION_END_NOTIFICATION_ID, "session_end")
                startAudioAndVibration()
                // No penalty for session end
            }
            ACTION_SILENCE -> {
                // Stop audio/vibration only — service stays alive, penalty timer continues
                stopAudio()
                safetyHandler.removeCallbacks(reRingRunnable)
                safetyHandler.postDelayed(reRingRunnable, 120_000L) // 2 minutes re-ring timer
            }
            ACTION_STOP -> {
                // Full stop — kill everything, terminate service
                safetyHandler.removeCallbacksAndMessages(null)
                stopAudio()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification(notificationId: Int, type: String) {
        val prefs: SharedPreferences = getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        val isEnglish = prefs.getBoolean("is_english", true)

        val title: String
        val content: String

        when (type) {
            "fajr" -> {
                title = if (isEnglish) "Fajr alarm calling" else "منبه الفجر يدعو"
                content = if (isEnglish) "Wake up. 30 minutes until Fajr adhan." else "استيقظ. 30 دقيقة حتى أذان الفجر."
            }
            "jumuah" -> {
                title = if (isEnglish) "Friday Jumu'ah Alarm" else "منبه صلاة الجمعة"
                content = if (isEnglish) "Time to prepare for Friday Jumu'ah before 1st Azan." else "حان وقت الاستعداد لصلاة الجمعة قبل الأذان الأول."
            }
            "session_end" -> {
                title = if (isEnglish) "Session End Alarm" else "منبه انتهاء الجلسة"
                content = if (isEnglish) "Your session time has ended." else "انتهى وقت جلستك."
            }
            else -> {
                title = "Alarm"
                content = "Alarm is ringing"
            }
        }

        createNotificationChannel()

        val fullScreenIntent = Intent(this, MainActivity::class.java)
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Fajr Alarm Channel", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for Fajr Alarms"
                setBypassDnd(true)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun startAudioAndVibration() {
        safetyHandler.removeCallbacks(reRingRunnable)
        // Acquire WakeLock (15 min max)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AnalogAnchor:FajrAlarmServiceWakeLock")
        wakeLock?.acquire(15 * 60 * 1000L)

        // Safety auto-stop after 15 minutes
        safetyHandler.postDelayed(safetyStopRunnable, 15 * 60 * 1000L)

        // Ensure DND mode allows alarms before ringing
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm.isNotificationPolicyAccessGranted) {
                if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update DND interruption filter: ${e.message}")
        }

        // Force alarm volume to max
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm volume: ${e.message}")
        }

        val prefs = getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        val customUriStr = prefs.getString("fajr_alarm_ringtone_uri", null)

        var played = false
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
            .build()

        // 1. Try custom ringtone
        if (!customUriStr.isNullOrEmpty() && customUriStr != "default") {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@FajrAlarmService, Uri.parse(customUriStr))
                    setAudioAttributes(audioAttributes)
                    isLooping = true
                    prepare()
                    start()
                }
                played = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play custom ringtone: ${e.message}")
            }
        }

        // 2. Try embedded raw resource if present
        if (!played) {
            try {
                val rawUri = Uri.parse("android.resource://$packageName/${R.raw.fajr_default_ringtone}")
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@FajrAlarmService, rawUri)
                    setAudioAttributes(audioAttributes)
                    isLooping = true
                    prepare()
                    start()
                }
                played = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play raw ringtone: ${e.message}")
            }
        }

        // 3. Try system default alarm sound
        if (!played) {
            try {
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@FajrAlarmService, defaultUri)
                    setAudioAttributes(audioAttributes)
                    isLooping = true
                    prepare()
                    start()
                }
                played = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play default ringtone: ${e.message}")
            }
        }

        // 4. Fallback to ToneGenerator
        if (!played) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                startToneLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play ToneGenerator: ${e.message}")
            }
        }

        startVibration()
    }

    private val toneRunnable = object : Runnable {
        override fun run() {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 650)
            safetyHandler.postDelayed(this, 950)
        }
    }

    private fun startToneLoop() { safetyHandler.post(toneRunnable) }
    private fun stopToneLoop() { safetyHandler.removeCallbacks(toneRunnable) }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val timings = longArrayOf(0, 800, 400)
            val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(timings, amplitudes, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    vibrator?.vibrate(effect, VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM).build())
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(effect, AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(timings, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibration: ${e.message}")
        }
    }

    private fun stopAudio() {
        safetyHandler.removeCallbacks(safetyStopRunnable)
        safetyHandler.removeCallbacks(reRingRunnable)
        stopToneLoop()
        try { mediaPlayer?.apply { if (isPlaying) stop(); release() }; mediaPlayer = null } catch (_: Exception) {}
        try { toneGenerator?.release(); toneGenerator = null } catch (_: Exception) {}
        try { vibrator?.cancel() } catch (_: Exception) {}
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudio()
    }

    companion object {
        private const val TAG = "FajrAlarmService"
        const val ACTION_START_FAJR = "com.analoganchor.app.action.ALARM_SERVICE_START_FAJR"
        const val ACTION_START_JUMUAH = "com.analoganchor.app.action.ALARM_SERVICE_START_JUMUAH"
        const val ACTION_START_SESSION_END = "com.analoganchor.app.action.ALARM_SERVICE_START_SESSION_END"
        const val ACTION_SILENCE = "com.analoganchor.app.action.ALARM_SERVICE_SILENCE"
        const val ACTION_STOP = "com.analoganchor.app.action.ALARM_SERVICE_STOP"
        const val FAJR_NOTIFICATION_ID = 2002
        const val JUMUAH_NOTIFICATION_ID = 2005
        const val SESSION_END_NOTIFICATION_ID = 2006
        const val CHANNEL_ID = "fajr_alarm_channel_v2"
    }
}
