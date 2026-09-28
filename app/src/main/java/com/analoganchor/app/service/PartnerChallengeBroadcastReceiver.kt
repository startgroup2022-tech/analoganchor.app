package com.analoganchor.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.analoganchor.app.MainActivity
import com.analoganchor.app.data.AppDatabase
import com.analoganchor.app.data.SessionLog
import com.analoganchor.app.data.preferences.PartnerSessionPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PartnerChallengeBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PARTNER_CHALLENGE_STARTED -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: System.currentTimeMillis().toString()
                val durationMinutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, 60)
                val partnerName = intent.getStringExtra(EXTRA_PARTNER_NAME) ?: "Partner"
                val startTimeMs = intent.getLongExtra(EXTRA_START_TIME_MS, System.currentTimeMillis())

                Log.d(TAG, "Received partner challenge start: partner=$partnerName, duration=$durationMinutes min")
                PartnerSessionPrefs.confirmActivation(
                    context = context,
                    partnerName = partnerName,
                    durationMinutes = durationMinutes,
                    startTimeMs = startTimeMs,
                    sessionId = sessionId
                )
            }

            ACTION_PARTNER_CHALLENGE_CANCELLED -> {
                Log.d(TAG, "Received partner challenge cancelled. Clearing session.")
                PartnerSessionPrefs.clearSession(context)
            }

            ACTION_PARTNER_CHALLENGE_COMPLETED -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: System.currentTimeMillis().toString()
                val durationMinutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, 60)
                val partnerName = intent.getStringExtra(EXTRA_PARTNER_NAME) ?: "Anchor Partner"
                val isSuccess = intent.getBooleanExtra(EXTRA_SUCCESS, true)
                val isGroup = intent.getBooleanExtra(EXTRA_IS_GROUP, false)
                val points = intent.getIntExtra(EXTRA_POINTS, if (isGroup) 70 else 40)

                Log.d(TAG, "Received partner challenge result: session=$sessionId, duration=$durationMinutes min, partner=$partnerName, success=$isSuccess, points=$points, isGroup=$isGroup")

                // Always clear active session preference when finished
                PartnerSessionPrefs.clearSession(context)

                val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
                val isEnglish = prefs.getBoolean("is_english", true)

                if (isSuccess) {
                    val currentPoints = prefs.getInt("reserve_points", 100)
                    val updatedPoints = currentPoints + points
                    val currentPartnerCount = prefs.getInt("outdoor_partner_count_this_week", 0)
                    val updatedPartnerCount = currentPartnerCount + 1
                    val now = System.currentTimeMillis()

                    prefs.edit()
                        .putInt("reserve_points", updatedPoints)
                        .putInt("outdoor_partner_count_this_week", updatedPartnerCount)
                        .putLong("last_ledger_transaction_timestamp", now)
                        .putString("last_ledger_transaction_type", "EARN")
                        .apply()

                    // Record in Room Database
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getDatabase(context)
                            val actionLabel = if (isGroup) {
                                "Completed Group Sanctuary Circle with $partnerName ($durationMinutes min, +$points PTS)"
                            } else {
                                "Completed Partner Meshwar Al-Sakeenah with $partnerName ($durationMinutes min, +$points PTS)"
                            }
                            val log = SessionLog(
                                id = 0,
                                timestamp = System.currentTimeMillis(),
                                action = actionLabel,
                                pointsChange = points,
                                status = "outdoor_partner",
                                duration = durationMinutes
                            )
                            db.sessionLogDao().insertLog(log)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error recording session log: ${e.message}", e)
                        }
                    }

                    postSuccessNotification(context, isEnglish, partnerName, durationMinutes, points, isGroup)
                } else {
                    // Penalty for broken/emergency exit early: -25 PTS
                    val penaltyPoints = 25
                    val currentPoints = prefs.getInt("reserve_points", 100)
                    val updatedPoints = (currentPoints - penaltyPoints).coerceAtLeast(0)
                    val now = System.currentTimeMillis()

                    prefs.edit()
                        .putInt("reserve_points", updatedPoints)
                        .putLong("last_penalty_timestamp", now)
                        .putLong("last_ledger_transaction_timestamp", now)
                        .putString("last_ledger_transaction_type", "PENALTY")
                        .apply()

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getDatabase(context)
                            val log = SessionLog(
                                id = 0,
                                timestamp = System.currentTimeMillis(),
                                action = "Broken Sanctuary Outing Early (-$penaltyPoints PTS)",
                                pointsChange = -penaltyPoints,
                                status = "penalty",
                                duration = durationMinutes
                            )
                            db.sessionLogDao().insertLog(log)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error recording penalty log: ${e.message}", e)
                        }
                    }

                    postPenaltyNotification(context, isEnglish, partnerName, penaltyPoints)
                }
            }
        }
    }

    private fun postSuccessNotification(
        context: Context,
        isEnglish: Boolean,
        partnerName: String,
        durationMinutes: Int,
        points: Int,
        isGroup: Boolean
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                if (isEnglish) "Outdoor Sanctuary Quotas" else "مشوار السكينة والعافية",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for completed companion outdoor challenges"
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val title = if (isEnglish) {
            if (isGroup) "🎉 Group Circle Complete (+$points PTS)" else "🎉 Partner Routine Complete (+$points PTS)"
        } else {
            if (isGroup) "🎉 اكتملت حلقة السكينة الجماعية (+$points نقطة)" else "🎉 اكتمل مشوار السكينة مع الشريك (+$points نقطة)"
        }

        val body = if (isEnglish) {
            "Verified $durationMinutes min offline with $partnerName! Weekly social accountability quota satisfied."
        } else {
            "تم توثيق $durationMinutes دقيقة بدون شاشات مع $partnerName! اكتملت حصتك الأسبوعية من مشوار السكينة (+$points نقطة في رصيدك الاحتياطي)."
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun postPenaltyNotification(
        context: Context,
        isEnglish: Boolean,
        partnerName: String,
        penalty: Int
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                if (isEnglish) "Outdoor Sanctuary Quotas" else "مشوار السكينة والعافية",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for companion outdoor challenges"
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val title = if (isEnglish) "⚠️ Broken Sanctuary Outing (-$penalty PTS)" else "⚠️ كسر عهد مشوار السكينة (-$penalty نقطة)"
        val body = if (isEnglish) {
            "Emergency exit used during outing with $partnerName. A $penalty-point covenant penalty has been recorded in your ledger."
        } else {
            "تم استخدام رمز الطوارئ أثناء المشوار مع $partnerName. تم خصم $penalty نقطة كجزاء في سجلك الاحتياطي."
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID + 1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(NOTIFICATION_ID + 1, notification)
    }

    companion object {
        const val TAG = "PartnerChallengeReceiver"
        const val ACTION_PARTNER_CHALLENGE_STARTED = "com.analoganchor.app.ACTION_PARTNER_CHALLENGE_STARTED"
        const val ACTION_PARTNER_CHALLENGE_COMPLETED = "com.analoganchor.app.ACTION_PARTNER_CHALLENGE_COMPLETED"
        const val ACTION_PARTNER_CHALLENGE_CANCELLED = "com.analoganchor.app.ACTION_PARTNER_CHALLENGE_CANCELLED"
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
        const val EXTRA_DURATION_MINUTES = "EXTRA_DURATION_MINUTES"
        const val EXTRA_PARTNER_NAME = "EXTRA_PARTNER_NAME"
        const val EXTRA_START_TIME_MS = "EXTRA_START_TIME_MS"
        const val EXTRA_SUCCESS = "EXTRA_SUCCESS"
        const val EXTRA_POINTS = "EXTRA_POINTS"
        const val EXTRA_IS_GROUP = "EXTRA_IS_GROUP"

        private const val CHANNEL_ID = "partner_challenge_channel"
        private const val NOTIFICATION_ID = 5002
    }
}
