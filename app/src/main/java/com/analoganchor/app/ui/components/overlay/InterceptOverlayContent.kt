package com.analoganchor.app.ui.components.overlay

import androidx.compose.runtime.Composable
import com.analoganchor.app.data.local.entity.ScheduleEntity
import com.analoganchor.app.data.local.entity.ShieldEntity

import com.analoganchor.app.data.preferences.UserPreferences

@Composable
fun InterceptOverlayContent(
    packageName: String,
    appName: String,
    shield: ShieldEntity?,
    totalUsageToday: Long,
    totalGlobalUsageToday: Long,
    delayDurationSeconds: Int = 0,
    userPreferences: UserPreferences,
    skipWaterPause: Boolean = false,
    isLastChance: Boolean = false,
    onWaterPauseCompleted: () -> Unit = {},
    onAllowUse: (Int, Boolean) -> Unit,
    onCloseApp: () -> Unit,
    onGoalDismiss: () -> Unit = {},
    onReserveUnlock: () -> Unit = {}
) {
    ShieldOverlay(
        packageName = packageName,
        appName = appName,
        shield = shield,
        totalUsageToday = totalUsageToday,
        totalGlobalUsageToday = totalGlobalUsageToday,
        delayDurationSeconds = delayDurationSeconds,
        userPrefs = userPreferences,
        skipWaterPause = skipWaterPause,
        isLastChance = isLastChance,
        onWaterPauseCompleted = onWaterPauseCompleted,
        onAllowUse = onAllowUse,
        onCloseApp = onCloseApp,
        onGoalDismiss = onGoalDismiss,
        onReserveUnlock = onReserveUnlock
    )
}

@Composable
fun ScheduleOverlayContent(
    packageName: String,
    appName: String,
    schedule: ScheduleEntity,
    totalGlobalUsageToday: Long,
    onAllowUse: (Int, Boolean) -> Unit,
    onCloseApp: () -> Unit
) {
    ScheduleOverlay(
        packageName = packageName,
        appName = appName,
        schedule = schedule,
        totalGlobalUsageToday = totalGlobalUsageToday,
        onAllowUse = onAllowUse,
        onCloseApp = onCloseApp
    )
}
