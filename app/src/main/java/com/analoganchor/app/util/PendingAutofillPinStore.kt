package com.analoganchor.app.util

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object PendingAutofillPinStore {
    private const val TAG = "PendingPinStore"
    private const val SECURE_PREFS_NAME = "analog_anchor_secure_autofill"
    private const val LEGACY_PREFS_NAME = "analog_anchor_prefs"
    private const val KEY_PIN = "pending_autofill_pin"
    private const val KEY_EXPIRES_AT = "pending_autofill_expires_epoch"
    private const val KEY_REENTRY_PIN = "reentry_autofill_pin"
    private const val KEY_REENTRY_EXPIRES_AT = "reentry_autofill_expires_epoch"

    fun armPin(context: Context, pin: String, ttlMs: Long): Boolean {
        val cleanPin = cleanPinOrNull(pin)
        if (cleanPin == null) {
            Log.w(TAG, "Refusing to store invalid pending PIN shape.")
            return false
        }

        return try {
            val saved = securePrefs(context).edit()
                .putString(KEY_PIN, cleanPin)
                .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + ttlMs)
                .commit()
            clearLegacyPlainPendingPin(context)
            saved
        } catch (e: Exception) {
            Log.e(TAG, "Could not arm encrypted pending PIN: ${e.message}")
            false
        }
    }

    fun rememberReEntryPin(context: Context, pin: String, ttlMs: Long): Boolean {
        val cleanPin = cleanPinOrNull(pin)
        if (cleanPin == null) {
            Log.w(TAG, "Refusing to remember invalid re-entry PIN shape.")
            return false
        }

        return try {
            securePrefs(context).edit()
                .putString(KEY_REENTRY_PIN, cleanPin)
                .putLong(KEY_REENTRY_EXPIRES_AT, System.currentTimeMillis() + ttlMs)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Could not remember encrypted re-entry PIN: ${e.message}")
            false
        }
    }

    fun getReEntryPin(context: Context): String? {
        return try {
            val prefs = securePrefs(context)
            val pin = prefs.getString(KEY_REENTRY_PIN, null)
            val expiresAt = prefs.getLong(KEY_REENTRY_EXPIRES_AT, 0L)
            if (pin.isNullOrBlank() || System.currentTimeMillis() > expiresAt) {
                clearReEntryPin(context)
                null
            } else {
                pin
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not read encrypted re-entry PIN: ${e.message}")
            null
        }
    }

    fun getPendingPin(context: Context): String? {
        return try {
            clearLegacyPlainPendingPin(context)

            val prefs = securePrefs(context)
            val pin = prefs.getString(KEY_PIN, null)
            val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
            if (pin.isNullOrBlank() || System.currentTimeMillis() > expiresAt) {
                clear(context)
                null
            } else {
                pin
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not read encrypted pending PIN: ${e.message}")
            null
        }
    }

    fun clear(context: Context) {
        try {
            securePrefs(context).edit()
                .remove(KEY_PIN)
                .remove(KEY_EXPIRES_AT)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Could not clear encrypted pending PIN: ${e.message}")
        } finally {
            clearLegacyPlainPendingPin(context)
        }
    }

    fun clearReEntryPin(context: Context) {
        try {
            securePrefs(context).edit()
                .remove(KEY_REENTRY_PIN)
                .remove(KEY_REENTRY_EXPIRES_AT)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Could not clear encrypted re-entry PIN: ${e.message}")
        }
    }

    fun clearAll(context: Context) {
        try {
            securePrefs(context).edit()
                .remove(KEY_PIN)
                .remove(KEY_EXPIRES_AT)
                .remove(KEY_REENTRY_PIN)
                .remove(KEY_REENTRY_EXPIRES_AT)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Could not clear encrypted auto-fill data: ${e.message}")
        } finally {
            clearLegacyPlainPendingPin(context)
        }
    }

    private fun securePrefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    private fun cleanPinOrNull(pin: String): String? {
        val cleanPin = pin.trim()
        return cleanPin.takeIf { candidate ->
            candidate.length in 4..8 && candidate.all { it.isDigit() }
        }
    }

    private fun clearLegacyPlainPendingPin(context: Context) {
        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PIN)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }
}
