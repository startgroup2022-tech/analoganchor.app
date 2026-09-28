package com.analoganchor.app.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.analoganchor.app.AnalogAnchorApplication
import com.analoganchor.app.data.manager.GitHubUpdateManager
import com.analoganchor.app.data.preferences.UserPreferences
import com.analoganchor.app.data.preferences.UserPreferencesRepository
import com.analoganchor.app.data.remote.model.GitHubRelease
import com.analoganchor.app.ui.navigation.Screen

import com.analoganchor.app.util.BackupUtils
import com.analoganchor.app.worker.BackupManager
import kotlinx.coroutines.launch

@Composable
fun SettingsCategoryScreen(
    category: String,
    preferencesRepository: UserPreferencesRepository,
    navController: NavController,
    innerPadding: PaddingValues,
    onOpenPermissions: () -> Unit,
    onTriggerOnboardingStats: () -> Unit
) {
    val preferences by preferencesRepository.userPreferencesFlow.collectAsState(initial = UserPreferences())
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val app = context.applicationContext as AnalogAnchorApplication

    val backupManager = remember { BackupManager(context) }
    val updateManager = remember { GitHubUpdateManager(context) }

    var showGoalTestSheet by remember { mutableStateOf(false) }
    var showUpdateSheet by remember { mutableStateOf(false) }
    var showRestoreSheet by remember { mutableStateOf(false) }
    var latestRelease by remember { mutableStateOf<GitHubRelease?>(null) }
    var backupMetadata by remember { mutableStateOf<BackupUtils.BackupMetadata?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            uri?.let {
                coroutineScope.launch {
                    try {
                        // Removed UsageSyncManager dependency since Night Guard does not track usage stats
                    } catch (_: Exception) {}

                    BackupUtils.backupDatabase(context, it).onSuccess {
                        preferencesRepository.setLastBackupTimestamp(System.currentTimeMillis())
                        Toast.makeText(context, "Backup successful!", Toast.LENGTH_SHORT).show()
                    }.onFailure { e ->
                        Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                coroutineScope.launch {
                    preferencesRepository.setBackupDirectoryUri(it.toString())
                    if (preferences.autoBackupEnabled) {
                        backupManager.scheduleBackup(preferences.backupIntervalHours, it.toString())
                    }
                }
            }
        }
    )

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                coroutineScope.launch {
                    val metadata = BackupUtils.getBackupMetadata(context, it)
                    if (metadata != null) {
                        backupMetadata = metadata
                        pendingRestoreUri = it
                        showRestoreSheet = true
                    } else {
                        Toast.makeText(context, "Invalid backup file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 100.dp
            )
        ) {
            item {
                when (category.lowercase()) {
                    "features" -> FeaturesSettings(
                        preferences = preferences,
                        onTotalUsagePillEnabledChange = { enabled -> coroutineScope.launch { preferencesRepository.setTotalUsagePillEnabled(enabled) } },
                        onSessionUsageOverlayEnabledChange = { enabled -> coroutineScope.launch { preferencesRepository.setSessionUsageOverlayEnabled(enabled) } },
                        onSessionUsageOverlaySizeChange = { size -> coroutineScope.launch { preferencesRepository.setSessionUsageOverlaySize(size) } },
                        onSessionUsageOverlayOpacityChange = { opacity -> coroutineScope.launch { preferencesRepository.setSessionUsageOverlayOpacity(opacity) } },
                        onMindfulGatewayEnabledChange = { enabled -> coroutineScope.launch { preferencesRepository.setMindfulGatewayEnabled(enabled) } },
                        onEarlyKickEnabledChange = { enabled -> coroutineScope.launch { preferencesRepository.setEarlyKickEnabled(enabled) } },
                        onInterceptAudioFocusEnabledChange = { enabled -> coroutineScope.launch { preferencesRepository.setInterceptAudioFocusEnabled(enabled) } }
                    )
                    "general" -> GeneralSettings(
                        preferences = preferences,
                        onAccessibilityDisabledChange = { coroutineScope.launch { preferencesRepository.setAccessibilityDisabled(it) } },
                        onShowWhitelistSheetChange = { /* handled via sheet? actually unused here */ },
                        onOpenPermissions = onOpenPermissions
                    )
                    "appearance" -> AppearanceSettings(
                        preferences = preferences,
                        onThemeChange = { theme -> coroutineScope.launch { preferencesRepository.setThemeConfig(theme) } },
                        onFontChange = { font -> coroutineScope.launch { preferencesRepository.setFontOption(font) } },
                        onCompactnessChange = { compactness -> coroutineScope.launch { preferencesRepository.setCompactnessOption(compactness) } },
                        onDynamicColorChange = { enabled -> coroutineScope.launch { preferencesRepository.setDynamicColor(enabled) } },
                        onExpressiveColorsChange = { enabled -> coroutineScope.launch { preferencesRepository.setExpressiveColors(enabled) } },
                        onFloatingTabBarEnabledChange = { enabled -> coroutineScope.launch { preferencesRepository.setFloatingTabBarEnabled(enabled) } },
                        onNavigateToGSFlexCustomizer = { navController.navigate(Screen.GSFlexCustomizer.route) }
                    )
                    "data management" -> DataManagementSettings(
                        preferences = preferences,
                        onAutoBackupEnabledChange = { enabled ->
                            coroutineScope.launch {
                                preferencesRepository.setAutoBackupEnabled(enabled)
                                if (enabled && preferences.backupDirectoryUri.isNotEmpty()) {
                                    backupManager.scheduleBackup(preferences.backupIntervalHours, preferences.backupDirectoryUri)
                                } else {
                                    backupManager.cancelBackup()
                                }
                            }
                        },
                        onPickBackupDirectory = { directoryPickerLauncher.launch(null) },
                        onSetBackupInterval = { hours ->
                            coroutineScope.launch {
                                preferencesRepository.setBackupIntervalHours(hours)
                                if (preferences.autoBackupEnabled && preferences.backupDirectoryUri.isNotEmpty()) {
                                    backupManager.scheduleBackup(hours, preferences.backupDirectoryUri)
                                }
                            }
                        },
                        onBackupNow = {
                            if (preferences.backupDirectoryUri.isNotEmpty()) {
                                coroutineScope.launch {
                                    backupManager.runBackupNow(preferences.backupDirectoryUri)
                                }
                                Toast.makeText(context, "Backup started...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onBackup = { backupLauncher.launch("zenith_backup_${System.currentTimeMillis()}.db") },
                        onRefreshOnOpenUsageStatsChange = { enabled -> coroutineScope.launch { preferencesRepository.setRefreshOnOpenUsageStats(enabled) } },
                        onRestore = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) }
                    )
                    "developer" -> DeveloperSettings(
                        preferences = preferences,
                        onShowDatabaseIndicatorChange = { enabled -> coroutineScope.launch { preferencesRepository.setShowDatabaseIndicator(enabled) } },
                        onSmartRepairOnRefreshChange = { enabled -> coroutineScope.launch { preferencesRepository.setSmartRepairOnRefresh(enabled) } },
                        onNavigateToFontTest = { navController.navigate(Screen.FontTest.route) },
                        onCustomDelayEnabledChange = { enabled -> coroutineScope.launch { preferencesRepository.setCustomDelayEnabled(enabled) } },
                        onSetDelayPowerSave = { delay -> coroutineScope.launch { preferencesRepository.setDelayPowerSave(delay) } },
                        onSetDelayOverlayShowing = { delay -> coroutineScope.launch { preferencesRepository.setDelayOverlayShowing(delay) } },
                        onSetDelayGoalNear = { delay -> coroutineScope.launch { preferencesRepository.setDelayGoalNear(delay) } },
                        onSetDelayGoalMid = { delay -> coroutineScope.launch { preferencesRepository.setDelayGoalMid(delay) } },
                        onSetDelayGoalFar = { delay -> coroutineScope.launch { preferencesRepository.setDelayGoalFar(delay) } },
                        onSetDelayShieldVeryFar = { delay -> coroutineScope.launch { preferencesRepository.setDelayShieldVeryFar(delay) } },
                        onSetDelayShieldFar = { delay -> coroutineScope.launch { preferencesRepository.setDelayShieldFar(delay) } },
                        onSetDelayShieldMid = { delay -> coroutineScope.launch { preferencesRepository.setDelayShieldMid(delay) } },
                        onSetDelayShieldNear = { delay -> coroutineScope.launch { preferencesRepository.setDelayShieldNear(delay) } },
                        onSetDelayDefault = { delay -> coroutineScope.launch { preferencesRepository.setDelayDefault(delay) } },
                        onResetCustomDelays = { coroutineScope.launch { preferencesRepository.resetCustomDelays() } },
                        onTriggerOnboardingPermissions = { onOpenPermissions() },
                        onTriggerOnboardingStats = {
                            coroutineScope.launch {
                                preferencesRepository.setOnboardingStatsCompleted(false)
                                onTriggerOnboardingStats()
                                Toast.makeText(context, "Stats onboarding triggered", Toast.LENGTH_SHORT).show()
                                navController.popBackStack(Screen.Settings.route, false)
                            }
                        },
                        onResetBedtimeStreak = { coroutineScope.launch { preferencesRepository.resetBedtimeStreak() } },
                        onResetStreakRecovery = { coroutineScope.launch { preferencesRepository.setLastStreakCheckDate("") } },
                        onResetFajrNfcLock = {
                            val prefs = context.getSharedPreferences("analog_anchor_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit()
                                .putBoolean("fajr_alarm_nfc_locked", false)
                                .remove("fajr_alarm_target_time_ms")
                                .remove("fajr_nfc_repeat_until_ms")
                                .putInt("fajr_alarm_offset_minutes", 0)
                                .apply()
                            val isEnabled = prefs.getBoolean("fajr_alarm_enabled", false)
                            if (isEnabled) {
                                com.analoganchor.app.service.FajrAlarmScheduler.scheduleFajrAlarm(context)
                            } else {
                                com.analoganchor.app.service.FajrAlarmScheduler.cancelFajrAlarm(context)
                            }
                            Toast.makeText(context, "🔓 Fajr NFC Lockdown released.", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }



        if (showUpdateSheet && latestRelease != null) {
            val isDark = when (preferences.themeConfig) {
                com.analoganchor.app.data.preferences.ThemeConfig.FOLLOW_SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                com.analoganchor.app.data.preferences.ThemeConfig.LIGHT -> false
                com.analoganchor.app.data.preferences.ThemeConfig.DARK -> true
            }
            com.analoganchor.app.ui.components.UpdateBottomSheet(
                release = latestRelease!!,
                useExpressiveColors = preferences.expressiveColors,
                isDark = isDark,
                onDismiss = { showUpdateSheet = false },
                onUpdate = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, latestRelease!!.htmlUrl.toUri())
                    context.startActivity(intent)
                    showUpdateSheet = false
                }
            )
        }

        if (showRestoreSheet && backupMetadata != null && pendingRestoreUri != null) {
            com.analoganchor.app.ui.components.RestoreConfirmationBottomSheet(
                preferences = preferences,
                metadata = backupMetadata!!,
                onDismiss = {
                    showRestoreSheet = false
                    backupMetadata = null
                    pendingRestoreUri = null
                },
                onConfirm = {
                    coroutineScope.launch {
                        val currentBackupUri = preferences.backupDirectoryUri
                        val currentBackupInterval = preferences.backupIntervalHours
                        val currentAutoBackupEnabled = preferences.autoBackupEnabled

                        BackupUtils.restoreDatabase(context, pendingRestoreUri!!).onSuccess {
                            preferencesRepository.setBackupDirectoryUri(currentBackupUri)
                            preferencesRepository.setBackupIntervalHours(currentBackupInterval)
                            preferencesRepository.setAutoBackupEnabled(currentAutoBackupEnabled)

                            if (currentAutoBackupEnabled && currentBackupUri.isNotEmpty()) {
                                backupManager.scheduleBackup(currentBackupInterval, currentBackupUri)
                            }

                            Toast.makeText(context, "Restore successful! Restarting app...", Toast.LENGTH_LONG).show()
                            BackupUtils.restartApp(context)
                        }.onFailure { e ->
                            Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    showRestoreSheet = false
                }
            )
        }
    }
}
