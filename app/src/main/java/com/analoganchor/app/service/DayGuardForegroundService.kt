package com.analoganchor.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.analoganchor.app.R

class DayGuardForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "day_guard_foreground_channel"
        private const val NOTIFICATION_ID = 2607
        
        @Volatile
        var isServiceRunning = false
            private set

        fun start(context: Context) {
            try {
                val intent = Intent(context, DayGuardForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("DayGuardFGS", "Failed to start foreground service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, DayGuardForegroundService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                android.util.Log.e("DayGuardFGS", "Failed to stop foreground service: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()
        startForegroundSafe()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafe()
        return START_STICKY
    }

    private fun startForegroundSafe() {
        try {
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("DayGuardFGS", "Failed to start foreground service: ${e.message}", e)
            try {
                stopSelf()
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Analog Anchor Active",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Bringing Intentionality to Hardware & Services"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val iconRes = R.mipmap.ic_launcher_custom
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Analog Anchor Active")
            .setContentText("Bringing Intentionality to Hardware & Services")
            .setSmallIcon(iconRes)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }
}
