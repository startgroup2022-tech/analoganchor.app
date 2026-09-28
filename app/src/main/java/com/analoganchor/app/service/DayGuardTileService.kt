package com.analoganchor.app.service

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.analoganchor.app.util.isAccessibilityServiceEnabled

class DayGuardTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val isEnabled = isAccessibilityServiceEnabled(this)
        
        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Day Guard"
        tile.subtitle = if (isEnabled) "Active" else "Inactive"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        
        val zenithComponent = android.content.ComponentName(
            this,
            "com.analoganchor.app.service.ZenithAccessibilityService"
        )
        val accessibilitySettingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val bundle = android.os.Bundle()
            bundle.putString(":settings:fragment_args_key", zenithComponent.flattenToString())
            putExtra(":settings:show_fragment_args", bundle)
            putExtra("extra_fragment_arg_key", zenithComponent.flattenToString())
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = android.app.PendingIntent.getActivity(
                    this,
                    0,
                    accessibilitySettingsIntent,
                    android.app.PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(accessibilitySettingsIntent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to open Accessibility settings.", e)
        }
    }

    private companion object {
        const val TAG = "DayGuardTileService"
    }
}
