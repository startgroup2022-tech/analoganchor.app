package com.analoganchor.app.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.widget.RemoteViews
import com.analoganchor.app.R
import com.analoganchor.app.util.isAccessibilityServiceEnabled

class DayGuardWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val isEnabled = isAccessibilityServiceEnabled(context)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_day_guard)

            // Setup broadcast intent to our provider first to launch spotlight
            val clickIntent = Intent(context, DayGuardWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_CLICK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            // Update UI components dynamically based on status
            if (isEnabled) {
                views.setTextViewText(R.id.widget_status, "Active")
                views.setTextColor(R.id.widget_status, Color.parseColor("#4CAF50"))
                views.setInt(R.id.widget_icon, "setColorFilter", Color.parseColor("#4CAF50"))
            } else {
                views.setTextViewText(R.id.widget_status, "Inactive")
                views.setTextColor(R.id.widget_status, Color.parseColor("#9E9E9E"))
                views.setInt(R.id.widget_icon, "setColorFilter", Color.parseColor("#9E9E9E"))
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, DayGuardWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        } else if (intent.action == ACTION_WIDGET_CLICK) {
            // Open settings
            val zenithComponent = android.content.ComponentName(
                context,
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
                context.startActivity(accessibilitySettingsIntent)
            } catch (e: Exception) {
                // Ignore fallback
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_CLICK = "com.analoganchor.app.action.WIDGET_CLICK"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, DayGuardWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            val intent = Intent(context, DayGuardWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            context.sendBroadcast(intent)
        }
    }
}
