package com.analoganchor.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object DayGuardIntegration {
    const val DAY_GUARD_PACKAGE = "com.etrisad.zenith"
    val DAY_GUARD_PACKAGE_CANDIDATES = listOf(
        DAY_GUARD_PACKAGE,
        "com.analoganchor.dayguard",
        "com.analoganchor.day_guard"
    )

    fun isDayGuardInstalled(context: Context): Boolean {
        return DAY_GUARD_PACKAGE_CANDIDATES.any { packageName ->
            isPackageInstalled(context, packageName)
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun dayGuardLaunchIntent(context: Context): Intent? {
        for (packageName in DAY_GUARD_PACKAGE_CANDIDATES) {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                return intent.apply {
                    action = Intent.ACTION_MAIN
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
        return null
    }
}
