package com.analoganchor.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import com.analoganchor.app.R

object AppBlockIntegration {
    const val APPBLOCK_PACKAGE = "cz.mobilesoft.appblock"
    private const val SHORTCUT_ID = "anchor_lock_shortcut"
    val APPBLOCK_PACKAGE_CANDIDATES = listOf(
        APPBLOCK_PACKAGE,
        "com.appblock.app",
        "cz.droidsonroids.appblock"
    )

    fun isKnownAppBlockPackage(packageName: String): Boolean {
        return APPBLOCK_PACKAGE_CANDIDATES.any { it.equals(packageName, ignoreCase = true) }
    }

    fun isAppBlockInstalled(context: Context): Boolean {
        return APPBLOCK_PACKAGE_CANDIDATES.any { packageName ->
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

    fun appBlockLaunchIntent(context: Context): Intent? {
        for (packageName in APPBLOCK_PACKAGE_CANDIDATES) {
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

    fun appBlockApplicationDetailsIntent(context: Context): Intent? {
        val installedPackage = APPBLOCK_PACKAGE_CANDIDATES.firstOrNull { packageName ->
            isPackageInstalled(context, packageName)
        } ?: return null

        return Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$installedPackage")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun playStoreIntent(): Intent {
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$APPBLOCK_PACKAGE")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun webPlayStoreIntent(): Intent {
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$APPBLOCK_PACKAGE")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun requestAnchorLockShortcut(context: Context): ShortcutResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return ShortcutResult.NotSupported
        }

        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            ?: return ShortcutResult.NotSupported

        if (!shortcutManager.isRequestPinShortcutSupported) {
            return ShortcutResult.NotSupported
        }

        if (!isAppBlockInstalled(context)) {
            return ShortcutResult.AppBlockMissing
        }

        val launchIntent = Intent(context, com.analoganchor.app.MainActivity::class.java).apply {
            action = Intent.ACTION_RUN
            putExtra("open_anchor_lock_decoy", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        val shortcutInfo = ShortcutInfo.Builder(context, SHORTCUT_ID)
            .setShortLabel("Anchor Lock")
            .setLongLabel("Anchor Lock - Strict Mode")
            .setIcon(Icon.createWithResource(context, R.drawable.ic_anchor_lock))
            .setIntent(launchIntent)
            .build()

        return if (shortcutManager.requestPinShortcut(shortcutInfo, null)) {
            ShortcutResult.Requested
        } else {
            ShortcutResult.NotSupported
        }
    }

    sealed class ShortcutResult {
        data object Requested : ShortcutResult()
        data object AppBlockMissing : ShortcutResult()
        data object NotSupported : ShortcutResult()
    }
}
