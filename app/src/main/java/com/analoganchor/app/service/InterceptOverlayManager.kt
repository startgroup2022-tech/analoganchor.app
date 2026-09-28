package com.analoganchor.app.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.analoganchor.app.ui.components.overlay.BedtimeOverlayContent
import com.analoganchor.app.ui.components.overlay.InterceptOverlayContent
import com.analoganchor.app.ui.components.overlay.ScheduleOverlayContent
import com.analoganchor.app.ui.components.overlay.WindDownOverlayContent
import com.analoganchor.app.ui.theme.GSFlexSettings
import com.analoganchor.app.ui.theme.ZenithTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class InterceptOverlayManager(
    private val context: Context,
    private val preferencesRepository: com.analoganchor.app.data.preferences.UserPreferencesRepository
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var overlayUsageState: androidx.compose.runtime.MutableState<Pair<Long, Long>>? = null
    private val shieldRepository by lazy {
        (context.applicationContext as com.analoganchor.app.AnalogAnchorApplication).shieldRepository
    }
    private var waterPauseBackupRunnable: Runnable? = null
    private var pendingWaterPauseCallback: (() -> Unit)? = null
    private val graceRunnables = java.util.concurrent.ConcurrentHashMap<String, Runnable>()
    private var pendingHideRunnable: Runnable? = null
    // Tracks how many times the duration picker has been shown per-package in the current lifecycle
    private val sessionShowCount = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun resetSessionCount(packageName: String) {
        sessionShowCount.remove(packageName)
    }

    companion object {
        private const val TAG = "InterceptOverlayManager"
        private const val WATER_PAUSE_DURATION_MS = 5_500L
        private const val POST_WATER_PAUSE_GRACE_MS = 25_000L
        private const val DURATION_PICKER_PROTECT_MS = 60_000L
        @Volatile
        var isShowing = false
        private var overlayView: ComposeView? = null
        private var lifecycleOwner: MyLifecycleOwner? = null
        private var viewModelStore: ViewModelStore? = null
        @Volatile
        var currentPackage: String? = null
        private var audioFocusRequest: AudioFocusRequest? = null
        private var focusRequestJob: kotlinx.coroutines.Job? = null
        private val afChangeListener = AudioManager.OnAudioFocusChangeListener { }

        private val SYSTEM_UI_PACKAGES = setOf(
            "com.android.systemui",
            "android",
            "com.android.settings",
            "com.google.android.permissioncontroller",
            "com.google.android.packageinstaller"
        )
    }

    private fun sendToHomeScreen() {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(homeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching home intent", e)
        }
    }

    private fun updateOverlayContent(
        packageName: String,
        appName: String,
        shield: com.analoganchor.app.data.local.entity.ShieldEntity?,
        totalUsageToday: Long,
        totalGlobalUsageToday: Long,
        delayDurationSeconds: Int = 0,
        currentPreferences: com.analoganchor.app.data.preferences.UserPreferences,
        skipWaterPause: Boolean = false,
        isLastChance: Boolean = false,
        onWaterPauseCompleted: () -> Unit = {},
        onAllowUse: (Int, Boolean) -> Unit,
        onCloseApp: () -> Unit,
        onGoalDismiss: () -> Unit
    ) {
        overlayUsageState?.value = Pair(totalUsageToday, totalGlobalUsageToday)
        overlayView?.setContent {
            val userPrefs by preferencesRepository.userPreferencesFlow.collectAsState(initial = currentPreferences)
            val prefs = userPrefs
            val darkTheme = when (prefs?.themeConfig) {
                com.analoganchor.app.data.preferences.ThemeConfig.LIGHT -> false
                com.analoganchor.app.data.preferences.ThemeConfig.DARK -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            ZenithTheme(
                darkTheme = darkTheme,
                fontOption = prefs?.fontOption ?: com.analoganchor.app.data.preferences.FontOption.SYSTEM,
                dynamicColor = prefs?.dynamicColor ?: true,
                expressiveColors = prefs?.expressiveColors ?: false,
                gsFlexSettings = prefs?.gsFlexSettings ?: GSFlexSettings(),
                compactnessOption = prefs?.compactnessOption ?: com.analoganchor.app.data.preferences.CompactnessOption.DEFAULT
            ) {
                if (prefs != null) {
                    val usageState = overlayUsageState?.value ?: Pair(totalUsageToday, totalGlobalUsageToday)
                    Box(modifier = Modifier.fillMaxSize()) {
                        InterceptOverlayContent(
                            packageName = packageName,
                            appName = appName,
                            shield = shield,
                            totalUsageToday = usageState.first,
                            totalGlobalUsageToday = usageState.second,
                            delayDurationSeconds = delayDurationSeconds,
                            userPreferences = prefs,
                            skipWaterPause = skipWaterPause,
                            isLastChance = isLastChance,
                            onWaterPauseCompleted = onWaterPauseCompleted,
                            onAllowUse = { minutes, isEmergency ->
                                onAllowUse(minutes, isEmergency)
                                hideOverlay()
                            },
                            onCloseApp = {
                                sendToHomeScreen()
                                onCloseApp()
                                hideOverlay()
                            },
                            onGoalDismiss = {
                                onGoalDismiss()
                                hideOverlay()
                            },
                            onReserveUnlock = {
                                val sharedPrefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
                                val currentPoints = sharedPrefs.getInt("reserve_points", 0)
                                if (currentPoints >= 380) {
                                    val now = System.currentTimeMillis()
                                    sharedPrefs.edit()
                                        .putInt("reserve_points", currentPoints - 380)
                                        .putBoolean("is_override_active", true)
                                        .putBoolean("is_locked", false)
                                        .putLong("last_penalty_timestamp", now)
                                        .putLong("last_ledger_transaction_timestamp", now)
                                        .putString("last_ledger_transaction_type", "PENALTY")
                                        .apply()
                                    
                                    // Refresh the widget if necessary, though we don't have access to the ViewModel directly
                                    // The ViewModel collects from SharedPreferences on its own usually
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        android.widget.Toast.makeText(context, "Unlocked using Reserve Balance (-380 Pts)", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    hideOverlay()
                                } else {
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        android.widget.Toast.makeText(context, "Not enough Reserve Balance. Need 380 Pts.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    var lastShowTime: Long = 0L
        private set

    fun isInPostWaterPauseGrace(packageName: String): Boolean =
        shieldRepository.isInPostWaterPauseGrace(packageName)

    fun cancelGraceForPackage(packageName: String) {
        cancelGraceTimer(packageName)
        shieldRepository.clearPostWaterPauseGrace(packageName)
    }

    private fun cancelWaterPauseBackup() {
        waterPauseBackupRunnable?.let { mainHandler.removeCallbacks(it) }
        waterPauseBackupRunnable = null
        pendingWaterPauseCallback = null
    }

    private fun cancelGraceTimer(packageName: String) {
        graceRunnables.remove(packageName)?.let { mainHandler.removeCallbacks(it) }
    }

    /**
     * Called when water pause animation finishes. Hides overlay and starts the 25s grace period,
     * then invokes [onGraceExpired] to re-show the duration picker.
     */
    fun completeWaterPauseAndBeginGrace(packageName: String, onGraceExpired: () -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { completeWaterPauseAndBeginGrace(packageName, onGraceExpired) }
            return
        }
        if (graceRunnables.containsKey(packageName) || shieldRepository.isInPostWaterPauseGrace(packageName)) return
        cancelWaterPauseBackup()
        shieldRepository.beginPostWaterPauseGrace(packageName, POST_WATER_PAUSE_GRACE_MS)
        hideOverlay()
        cancelGraceTimer(packageName)
        val runnable = Runnable {
            graceRunnables.remove(packageName)
            shieldRepository.clearPostWaterPauseGrace(packageName)
            onGraceExpired()
        }
        graceRunnables[packageName] = runnable
        mainHandler.postDelayed(runnable, POST_WATER_PAUSE_GRACE_MS)
    }

    private fun armWaterPauseBackup(packageName: String, skipWaterPause: Boolean, waterPauseEnabled: Boolean, onWaterPauseCompleted: () -> Unit) {
        cancelWaterPauseBackup()
        if (skipWaterPause || !waterPauseEnabled) {
            return
        }
        pendingWaterPauseCallback = onWaterPauseCompleted
        val backup = Runnable {
            if (currentPackage == packageName && pendingWaterPauseCallback != null) {
                val cb = pendingWaterPauseCallback!!
                pendingWaterPauseCallback = null
                waterPauseBackupRunnable = null
                cb()
            }
        }
        waterPauseBackupRunnable = backup
        mainHandler.postDelayed(backup, WATER_PAUSE_DURATION_MS)
    }

    fun showOverlay(
        packageName: String,
        appName: String,
        shield: com.analoganchor.app.data.local.entity.ShieldEntity?,
        totalUsageToday: Long,
        totalGlobalUsageToday: Long,
        delayDurationSeconds: Int = 0,
        currentPreferences: com.analoganchor.app.data.preferences.UserPreferences,
        skipWaterPause: Boolean = false,
        onWaterPauseCompleted: () -> Unit = {},
        onAllowUse: (Int, Boolean) -> Unit,
        onCloseApp: () -> Unit,
        onGoalDismiss: () -> Unit
    ) {
        // Must run on Main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                showOverlay(packageName, appName, shield, totalUsageToday, totalGlobalUsageToday, delayDurationSeconds, currentPreferences, skipWaterPause, onWaterPauseCompleted, onAllowUse, onCloseApp, onGoalDismiss)
            }
            return
        }

        if (isShowing && currentPackage == packageName) return

        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        if (km?.isKeyguardLocked == true) {
            Log.d(TAG, "showOverlay: Device is locked, refusing to show overlay")
            return
        }

        // Cancel any pending hide — we're about to show/re-show
        pendingHideRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingHideRunnable = null

        // Clean up stale view if present (e.g. from a previous hide that removed state but view wasn't destroyed yet)
        if (overlayView != null && !isShowing) {
            destroyOverlayView()
        }

        // Track session count for last-chance warnings
        var isLastChance = false
        if (skipWaterPause) {
            val nextCount = (sessionShowCount[packageName] ?: 0) + 1
            sessionShowCount[packageName] = nextCount
            isLastChance = nextCount >= 2
        } else {
            // First show with water pause — reset count
            sessionShowCount[packageName] = 0
        }

        if (overlayView != null) {
            updateOverlayContent(packageName, appName, shield, totalUsageToday, totalGlobalUsageToday, delayDurationSeconds, currentPreferences, skipWaterPause, isLastChance, onWaterPauseCompleted, onAllowUse, onCloseApp, onGoalDismiss)
            armWaterPauseBackup(packageName, skipWaterPause, currentPreferences.waterPauseEnabled, onWaterPauseCompleted)
            isShowing = true
            currentPackage = packageName
            lastShowTime = System.currentTimeMillis()
            return
        }

        armWaterPauseBackup(packageName, skipWaterPause, currentPreferences.waterPauseEnabled, onWaterPauseCompleted)
        isShowing = true
        currentPackage = packageName
        lastShowTime = System.currentTimeMillis()

        val usageState = androidx.compose.runtime.mutableStateOf(Pair(totalUsageToday, totalGlobalUsageToday))
        overlayUsageState = usageState

        val vStore = ViewModelStore()
        viewModelStore = vStore
        
        val lOwner = MyLifecycleOwner()
        lOwner.performRestore(null)
        lOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner = lOwner

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lOwner)
            setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = vStore
            })
            setViewTreeSavedStateRegistryOwner(lOwner)
            
            setContent {
                val userPrefs by preferencesRepository.userPreferencesFlow.collectAsState(initial = currentPreferences)
                val prefs = userPrefs
                val darkTheme = when (prefs?.themeConfig) {
                    com.analoganchor.app.data.preferences.ThemeConfig.LIGHT -> false
                    com.analoganchor.app.data.preferences.ThemeConfig.DARK -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }

                ZenithTheme(
                    darkTheme = darkTheme,
                    fontOption = prefs?.fontOption ?: com.analoganchor.app.data.preferences.FontOption.SYSTEM,
                    dynamicColor = prefs?.dynamicColor ?: true,
                    expressiveColors = prefs?.expressiveColors ?: false,
                    gsFlexSettings = prefs?.gsFlexSettings ?: GSFlexSettings(),
                    compactnessOption = prefs?.compactnessOption ?: com.analoganchor.app.data.preferences.CompactnessOption.DEFAULT
                ) {
                    if (prefs != null) {
                        val (usage, globalUsage) = usageState.value
                        Box(modifier = Modifier.fillMaxSize()) {
                            InterceptOverlayContent(
                                packageName = packageName,
                                appName = appName,
                                shield = shield,
                                totalUsageToday = usage,
                                totalGlobalUsageToday = globalUsage,
                                delayDurationSeconds = delayDurationSeconds,
                                userPreferences = prefs,
                                skipWaterPause = skipWaterPause,
                                isLastChance = isLastChance,
                                onWaterPauseCompleted = onWaterPauseCompleted,
                                onAllowUse = { minutes, isEmergency ->
                                    onAllowUse(minutes, isEmergency)
                                    hideOverlay()
                                },
                                onCloseApp = {
                                    sendToHomeScreen()
                                    onCloseApp()
                                    hideOverlay()
                                },
                                onGoalDismiss = {
                                    onGoalDismiss()
                                    hideOverlay()
                                }
                            )
                        }
                    }
                }
            }
        }
        setupAndAddView(composeView, lOwner)
    }

    fun showScheduleOverlay(
        packageName: String,
        appName: String,
        schedule: com.analoganchor.app.data.local.entity.ScheduleEntity,
        totalGlobalUsageToday: Long,
        onAllowUse: (Int, Boolean) -> Unit,
        onCloseApp: () -> Unit
    ) {
        synchronized(this) {
            if (isShowing && currentPackage == packageName) return
            
            if (Looper.myLooper() != Looper.getMainLooper()) {
                mainHandler.post {
                    showScheduleOverlay(packageName, appName, schedule, totalGlobalUsageToday, onAllowUse, onCloseApp)
                }
                return
            }
            
            if (isShowing || overlayView != null) hideOverlay()
            
            isShowing = true
            currentPackage = packageName
        }

        val usageState = androidx.compose.runtime.mutableStateOf(Pair(0L, totalGlobalUsageToday))
        overlayUsageState = usageState

        val vStore = ViewModelStore()
        viewModelStore = vStore
        
        val lOwner = MyLifecycleOwner()
        lOwner.performRestore(null)
        lOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner = lOwner

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lOwner)
            setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = vStore
            })
            setViewTreeSavedStateRegistryOwner(lOwner)

            setContent {
                val userPrefs by preferencesRepository.userPreferencesFlow.collectAsState(initial = null)
                val darkTheme = when (userPrefs?.themeConfig) {
                    com.analoganchor.app.data.preferences.ThemeConfig.LIGHT -> false
                    com.analoganchor.app.data.preferences.ThemeConfig.DARK -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }

                ZenithTheme(
                    darkTheme = darkTheme,
                    fontOption = userPrefs?.fontOption ?: com.analoganchor.app.data.preferences.FontOption.SYSTEM,
                    dynamicColor = userPrefs?.dynamicColor ?: true,
                    expressiveColors = userPrefs?.expressiveColors ?: false,
                    gsFlexSettings = userPrefs?.gsFlexSettings ?: GSFlexSettings(),
                    compactnessOption = userPrefs?.compactnessOption ?: com.analoganchor.app.data.preferences.CompactnessOption.DEFAULT
                ) {
                    val (_, globalUsage) = usageState.value
                    Box(modifier = Modifier.fillMaxSize()) {
                        ScheduleOverlayContent(
                            packageName = packageName,
                            appName = appName,
                            schedule = schedule,
                            totalGlobalUsageToday = globalUsage,
                            onAllowUse = { minutes, isEmergency ->
                                onAllowUse(minutes, isEmergency)
                                hideOverlay()
                            },
                            onCloseApp = {
                                sendToHomeScreen()
                                onCloseApp()
                                hideOverlay()
                            }
                        )
                    }
                }
            }
        }
        setupAndAddView(composeView, lOwner)
    }

    fun showBedtimeOverlay(
        packageName: String,
        appName: String,
        onCloseApp: () -> Unit
    ) {
        synchronized(this) {
            if (isShowing && currentPackage == packageName) return

            if (Looper.myLooper() != Looper.getMainLooper()) {
                mainHandler.post {
                    showBedtimeOverlay(packageName, appName, onCloseApp)
                }
                return
            }
            
            if (isShowing || overlayView != null) hideOverlay()
            
            isShowing = true
            currentPackage = packageName
        }

        val vStore = ViewModelStore()
        viewModelStore = vStore
        
        val lOwner = MyLifecycleOwner()
        lOwner.performRestore(null)
        lOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner = lOwner

        val composeView = ComposeView(context).apply {
            setContent {
                val userPrefs by preferencesRepository.userPreferencesFlow.collectAsState(initial = null)
                val darkTheme = when (userPrefs?.themeConfig) {
                    com.analoganchor.app.data.preferences.ThemeConfig.LIGHT -> false
                    com.analoganchor.app.data.preferences.ThemeConfig.DARK -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }

                ZenithTheme(
                    darkTheme = darkTheme,
                    fontOption = userPrefs?.fontOption ?: com.analoganchor.app.data.preferences.FontOption.SYSTEM,
                    dynamicColor = userPrefs?.dynamicColor ?: true,
                    expressiveColors = userPrefs?.expressiveColors ?: false,
                    gsFlexSettings = userPrefs?.gsFlexSettings ?: GSFlexSettings(),
                    compactnessOption = userPrefs?.compactnessOption ?: com.analoganchor.app.data.preferences.CompactnessOption.DEFAULT
                ) {
                    val prefs = userPrefs
                    if (prefs != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            BedtimeOverlayContent(
                                packageName = packageName,
                                appName = appName,
                                userPreferences = prefs,
                                onCloseApp = {
                                    sendToHomeScreen()
                                    onCloseApp()
                                    hideOverlay()
                                }
                            )
                        }
                    }
                }
            }
        }
        setupAndAddView(composeView, lOwner)
    }

    fun showWindDownOverlay(
        packageName: String,
        appName: String,
        sessionUsed: Boolean,
        onAllowUse: (Int) -> Unit,
        onCloseApp: () -> Unit
    ) {
        synchronized(this) {
            if (isShowing && currentPackage == packageName) return

            if (Looper.myLooper() != Looper.getMainLooper()) {
                mainHandler.post {
                    showWindDownOverlay(packageName, appName, sessionUsed, onAllowUse, onCloseApp)
                }
                return
            }

            if (isShowing || overlayView != null) hideOverlay()

            isShowing = true
            currentPackage = packageName
        }

        val vStore = ViewModelStore()
        viewModelStore = vStore
        
        val lOwner = MyLifecycleOwner()
        lOwner.performRestore(null)
        lOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner = lOwner

        val composeView = ComposeView(context).apply {
            setContent {
                val userPrefs by preferencesRepository.userPreferencesFlow.collectAsState(initial = null)
                val darkTheme = when (userPrefs?.themeConfig) {
                    com.analoganchor.app.data.preferences.ThemeConfig.LIGHT -> false
                    com.analoganchor.app.data.preferences.ThemeConfig.DARK -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }

                ZenithTheme(
                    darkTheme = darkTheme,
                    fontOption = userPrefs?.fontOption ?: com.analoganchor.app.data.preferences.FontOption.SYSTEM,
                    dynamicColor = userPrefs?.dynamicColor ?: true,
                    expressiveColors = userPrefs?.expressiveColors ?: false,
                    gsFlexSettings = userPrefs?.gsFlexSettings ?: GSFlexSettings(),
                    compactnessOption = userPrefs?.compactnessOption ?: com.analoganchor.app.data.preferences.CompactnessOption.DEFAULT
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WindDownOverlayContent(
                            packageName = packageName,
                            appName = appName,
                            sessionUsed = sessionUsed,
                            userPreferences = userPrefs ?: com.analoganchor.app.data.preferences.UserPreferences(),
                            onAllowUse = { minutes ->
                                onAllowUse(minutes)
                                hideOverlay()
                            },
                            onCloseApp = {
                                sendToHomeScreen()
                                onCloseApp()
                                hideOverlay()
                            }
                        )
                    }
                }
            }
        }
        setupAndAddView(composeView, lOwner)
    }

    private var lastOverlayError: String? = null
    private var lastOverlayErrorTime = 0L

    private fun setupAndAddView(composeView: ComposeView, lOwner: MyLifecycleOwner) {
        Log.d(TAG, "setupAndAddView called for $currentPackage")
        val vStore = viewModelStore ?: run {
            Log.e(TAG, "setupAndAddView: viewModelStore is null, aborting")
            return
        }

        @Suppress("DEPRECATION")
        composeView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

        composeView.setViewTreeLifecycleOwner(lOwner)
        composeView.setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = vStore
        })
        composeView.setViewTreeSavedStateRegistryOwner(lOwner)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.FILL
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            overlayView?.let { existing ->
                try {
                    windowManager.removeViewImmediate(existing)
                } catch (_: Exception) {}
                overlayView = null
            }
            requestMediaPause()
            windowManager.addView(composeView, params)
            overlayView = composeView

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                composeView.post {
                    try {
                        composeView.windowInsetsController?.setSystemBarsAppearance(
                            0,
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        )
                    } catch (_: Exception) {}
                }
            }

            lOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (e: Exception) {
            val currentTime = System.currentTimeMillis()
            if (e.message != lastOverlayError || currentTime - lastOverlayErrorTime > 30000) {
                Log.e(TAG, "Error adding overlay for $currentPackage: ${e.message}", e)
                lastOverlayError = e.message
                lastOverlayErrorTime = currentTime
            }
            isShowing = false
            currentPackage = null
        }
    }

    fun checkAndHide(newPackage: String) {
        if (!isShowing) return
        // System UI packages (status bar, permission dialogs) should never trigger a hide
        if (SYSTEM_UI_PACKAGES.contains(newPackage)) return
        // Same package or self — no hide
        val target = currentPackage ?: return
        if (newPackage == target || newPackage == context.packageName) return

        // User navigated to a different real app/launcher — hide immediately
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { checkAndHide(newPackage) }
            return
        }
        // Re-check after posting (state may have changed)
        if (!isShowing) return
        if (currentPackage?.let { newPackage == it } == true) return

        hideOverlay()
    }

    /**
     * Removes the overlay view from the WindowManager and cleans up Compose/lifecycle resources.
     * Must be called on the Main thread.
     */
    private fun destroyOverlayView() {
        val view = overlayView ?: return
        try {
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            view.disposeComposition()
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Exception) {}
            viewModelStore?.clear()
        } catch (e: Exception) {
            val currentTime = System.currentTimeMillis()
            if (e.message != lastOverlayError || currentTime - lastOverlayErrorTime > 30000) {
                Log.e(TAG, "Error removing overlay: ${e.message}", e)
                lastOverlayError = e.message
                lastOverlayErrorTime = currentTime
            }
        } finally {
            overlayView = null
            lifecycleOwner = null
            viewModelStore = null
            overlayUsageState = null
        }
    }

    fun hideOverlay() {
        // If not on Main thread, post the entire operation atomically to Main
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideOverlay() }
            return
        }

        // All state changes + view cleanup happen atomically on Main thread
        cancelWaterPauseBackup()
        pendingHideRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingHideRunnable = null
        isShowing = false
        currentPackage = null
        resumeMedia()

        destroyOverlayView()
    }

    private fun requestMediaPause() {
        val pkg = currentPackage ?: return
        if (!shouldRequestMediaFocus(pkg)) return

        focusRequestJob?.cancel()
        focusRequestJob = managerScope.launch {
            val enabled = preferencesRepository.userPreferencesFlow.first().interceptAudioFocusEnabled
            if (!enabled) return@launch

            if (!isShowing || currentPackage != pkg) return@launch

            val focusType = if (isVideoApp(pkg)) {
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            } else {
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val focusRequest = AudioFocusRequest.Builder(focusType)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(afChangeListener)
                    .build()
                audioFocusRequest = focusRequest
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    afChangeListener,
                    AudioManager.STREAM_MUSIC,
                    focusType
                )
            }
        }
    }

    private fun isVideoApp(packageName: String): Boolean {
        val strictVideoApps = setOf(
            "com.google.android.youtube",
            "com.netflix.mediaclient",
            "com.disney.disneyplus",
            "tv.twitch.android.app",
            "com.ss.android.ugc.aweme",
            "com.zhiliaoapp.musically",
            "com.instagram.android",
            "com.facebook.katana"
        )
        if (strictVideoApps.contains(packageName)) return true

        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_VIDEO
            } else false
        } catch (_: Exception) {
            false
        }
    }

    private fun shouldRequestMediaFocus(packageName: String): Boolean {
        val mediaApps = setOf(
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.aweme",
            "com.instagram.android",
            "com.facebook.katana",
            "com.netflix.mediaclient",
            "com.disney.disneyplus",
            "tv.twitch.android.app",
            "com.twitter.android",
            "com.snapchat.android",
            "com.google.android.apps.youtube.music",
            "com.spotify.music"
        )
        if (mediaApps.contains(packageName)) return true

        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_VIDEO ||
                appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_GAME ||
                appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_SOCIAL ||
                appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_AUDIO
            } else false
        } catch (_: Exception) {
            false
        }
    }

    private fun resumeMedia() {
        focusRequestJob?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
                audioFocusRequest = null
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(afChangeListener)
        }
    }


    private class MyLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            lifecycleRegistry.handleLifecycleEvent(event)
        }

        fun performRestore(savedState: Bundle?) {
            savedStateRegistryController.performRestore(savedState)
        }
    }
}
