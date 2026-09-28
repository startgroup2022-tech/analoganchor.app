package com.analoganchor.app.data.preferences

import android.content.Context
import android.content.SharedPreferences

data class ActivePartnerSession(
    val partnerName: String,
    val startTimeMs: Long,
    val durationMinutes: Int = 60,
    val sessionId: String,
    val isActivated: Boolean = false
) {
    val totalDurationMs: Long get() = durationMinutes * 60 * 1000L
    val endTimeMs: Long get() = startTimeMs + totalDurationMs

    fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
        // If not activated yet, pending window expires after 5 minutes
        if (!isActivated) {
            return (now - startTimeMs) > PartnerSessionPrefs.PENDING_GRACE_WINDOW_MS
        }
        return now >= endTimeMs
    }

    fun remainingPendingSeconds(now: Long = System.currentTimeMillis()): Long {
        val remMs = (startTimeMs + PartnerSessionPrefs.PENDING_GRACE_WINDOW_MS) - now
        return if (remMs > 0) remMs / 1000L else 0L
    }

    fun remainingSeconds(now: Long = System.currentTimeMillis()): Long {
        if (!isActivated) return remainingPendingSeconds(now)
        val remMs = endTimeMs - now
        return if (remMs > 0) remMs / 1000L else 0L
    }
}

object PartnerSessionPrefs {
    const val PENDING_GRACE_WINDOW_MS = 5 * 60 * 1000L
    private const val PREFS_NAME = "analog_anchor_partner_session"
    private const val KEY_HAS_SESSION = "key_partner_session_has"
    private const val KEY_IS_ACTIVATED = "key_partner_session_activated"
    private const val KEY_PARTNER_NAME = "key_partner_name"
    private const val KEY_START_TIME_MS = "key_start_time_ms"
    private const val KEY_DURATION_MINUTES = "key_duration_minutes"
    private const val KEY_SESSION_ID = "key_session_id"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Records a session launched from Analog Anchor, awaiting user activation in Offline Challenge.
     */
    fun startPendingSession(
        context: Context,
        partnerName: String,
        durationMinutes: Int = 60,
        sessionId: String = java.util.UUID.randomUUID().toString()
    ) {
        val now = System.currentTimeMillis()
        getPrefs(context).edit()
            .putBoolean(KEY_HAS_SESSION, true)
            .putBoolean(KEY_IS_ACTIVATED, false)
            .putString(KEY_PARTNER_NAME, partnerName)
            .putLong(KEY_START_TIME_MS, now)
            .putInt(KEY_DURATION_MINUTES, durationMinutes)
            .putString(KEY_SESSION_ID, sessionId)
            .apply()
    }

    /**
     * Legacy helper or direct start
     */
    fun startSession(
        context: Context,
        partnerName: String,
        sanctuaryName: String = "",
        durationMinutes: Int = 60,
        sessionId: String = java.util.UUID.randomUUID().toString()
    ) {
        startPendingSession(context, partnerName, durationMinutes, sessionId)
    }

    /**
     * Confirms that the session is officially activated via explicit broadcast from Offline Challenge.
     */
    fun confirmActivation(
        context: Context,
        partnerName: String? = null,
        durationMinutes: Int = 60,
        startTimeMs: Long = System.currentTimeMillis(),
        sessionId: String? = null
    ) {
        val editor = getPrefs(context).edit()
            .putBoolean(KEY_HAS_SESSION, true)
            .putBoolean(KEY_IS_ACTIVATED, true)
            .putLong(KEY_START_TIME_MS, startTimeMs)
            .putInt(KEY_DURATION_MINUTES, durationMinutes)

        if (!partnerName.isNullOrBlank()) {
            editor.putString(KEY_PARTNER_NAME, partnerName)
        }
        if (!sessionId.isNullOrBlank()) {
            editor.putString(KEY_SESSION_ID, sessionId)
        }
        editor.apply()
    }

    fun getActiveSession(context: Context): ActivePartnerSession? {
        val prefs = getPrefs(context)
        val hasSession = prefs.getBoolean(KEY_HAS_SESSION, false)
        if (!hasSession) return null

        val isActivated = prefs.getBoolean(KEY_IS_ACTIVATED, false)
        val startTimeMs = prefs.getLong(KEY_START_TIME_MS, 0L)
        val durationMinutes = prefs.getInt(KEY_DURATION_MINUTES, 60)
        val partnerName = prefs.getString(KEY_PARTNER_NAME, "Partner") ?: "Partner"
        val sessionId = prefs.getString(KEY_SESSION_ID, "") ?: ""

        val session = ActivePartnerSession(
            partnerName = partnerName,
            startTimeMs = startTimeMs,
            durationMinutes = durationMinutes,
            sessionId = sessionId,
            isActivated = isActivated
        )

        // Clear if an already activated 60-minute session has completed its time
        if (isActivated && session.isExpired()) {
            clearSession(context)
            return null
        }

        return session
    }

    fun isSessionActive(context: Context): Boolean {
        return getActiveSession(context)?.isActivated == true
    }

    fun clearSession(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
