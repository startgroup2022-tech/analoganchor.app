package com.analoganchor.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.analoganchor.app.data.local.database.ZenithDatabase
import com.analoganchor.app.data.local.entity.FocusType
import com.analoganchor.app.data.local.entity.ScheduleEntity
import com.analoganchor.app.data.local.entity.ScheduleMode
import com.analoganchor.app.data.local.entity.ShieldEntity
import com.analoganchor.app.data.preferences.UserPreferences
import com.analoganchor.app.data.preferences.UserPreferencesRepository
import com.analoganchor.app.data.repository.ShieldRepository
import com.analoganchor.app.ui.components.overlay.SessionUsageOverlayManager
import com.analoganchor.app.util.AppBlockIntegration
import com.analoganchor.app.util.PendingAutofillPinStore
import java.util.Calendar
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ZenithAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val packageChangeFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private lateinit var shieldRepository: ShieldRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var overlayManager: InterceptOverlayManager
    private lateinit var sessionUsageOverlayManager: SessionUsageOverlayManager
    private val usageStatsManager by lazy { getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager }
    private val keyguardManager by lazy { getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager }
    fun isDeviceLocked(): Boolean = try { keyguardManager.isKeyguardLocked } catch (_: Exception) { false }

    private var isScreenReceiverRegistered = false
    @Volatile
    private var isScreenOffRecorded = false

    private val screenStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Option B: Screen-off immediately locks/terminates active app sessions
                    isScreenOffRecorded = true
                    try {
                        context?.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
                            ?.edit()
                            ?.putLong("last_screen_off_timestamp", System.currentTimeMillis())
                            ?.apply()
                    } catch (_: Exception) {}
                    allowedApps.clear()
                    waterPauseCompletedSessions.clear()
                    lastForegroundApp?.let { pkg ->
                        shieldRepository.clearPostWaterPauseGrace(pkg)
                        if (::overlayManager.isInitialized) {
                            overlayManager.cancelGraceForPackage(pkg)
                            overlayManager.resetSessionCount(pkg)
                        }
                    }
                    if (::sessionUsageOverlayManager.isInitialized) {
                        serviceScope.launch(Dispatchers.Main) {
                            sessionUsageOverlayManager.destroyAllHUDs()
                        }
                    }
                    if (::overlayManager.isInitialized) {
                        serviceScope.launch(Dispatchers.Main) {
                            overlayManager.hideOverlay()
                        }
                    }
                }
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    try {
                        context?.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
                            ?.edit()
                            ?.putLong("last_screen_on_timestamp", System.currentTimeMillis())
                            ?.apply()
                    } catch (_: Exception) {}
                    if (isScreenOffRecorded) {
                        isScreenOffRecorded = false
                        lastForegroundApp?.let { pkg ->
                            serviceScope.launch {
                                checkIfAppIsShielded(pkg)
                            }
                        }
                    }
                }
                "com.analoganchor.app.action.DISMISS_ALL_OVERLAYS" -> {
                    if (::overlayManager.isInitialized) {
                        serviceScope.launch(Dispatchers.Main) {
                            overlayManager.hideOverlay()
                        }
                    }
                }
            }
        }
    }
    private val reusableCalendar = Calendar.getInstance()

    private var lastForegroundApp: String? = null
    private var currentSessionStartTimestamp = 0L
    @Volatile
    private var currentShieldCache: ShieldEntity? = null
    private var lastUsageFetchTime = 0L
    private var cachedTotalUsage = 0L
    private val notifiedGoals = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val allowedApps get() = shieldRepository.allowedApps
    @Volatile
    private var activeSchedules = listOf<ScheduleEntity>()
    @Volatile
    private var whitelistedPackages = emptySet<String>()
    @Volatile
    private var currentPreferences: UserPreferences? = null
    @Volatile
    private var allShieldsCache = listOf<ShieldEntity>()
    @Volatile
    private var restrictedPackages = emptySet<String>()
    @Volatile
    private var hasGlobalAllowSchedule = false
    private val dailyUsageCache = mutableMapOf<String, Long>()
    @Volatile
    private var usageStatsCache: List<UsageStats>? = null
    private var lastUsageCacheTime = 0L

    private val waterPauseCompletedSessions get() = shieldRepository.waterPauseCompletedSessions

    private var lastMonitoringError: String? = null
    private var lastMonitoringErrorTime = 0L

    private var lastKickTime = 0L
    private var lastKickedPackage: String? = null

    private var isBedtimeActive = false
    private var isWindDownActive = false
    private var cachedBedtimeStartMinutes = -1
    private var cachedBedtimeEndMinutes = -1
    private val windDownUsedPackages = ConcurrentHashMap<String, Boolean>()
    private var bedtimeWhitelistedPackages = emptySet<String>()
    private val systemAppCache = ConcurrentHashMap<String, Boolean>()

    private class ParsedSchedule(
        val id: Long,
        val startMinutes: Int,
        val endMinutes: Int,
        val mode: ScheduleMode,
        val packageNames: Set<String>
    )
    @Volatile
    private var parsedSchedulesCache = listOf<ParsedSchedule>()

    companion object {
        // --- Primary Zenith state ---
        var isServiceRunning = false
            private set
        @Volatile
        var lastEventTime = 0L
        @Volatile
        var instance: ZenithAccessibilityService? = null
            private set

        fun enforceBedtimeImmediately() {
            instance?.let { service ->
                service.allowedApps.clear()
                val pkg = service.lastForegroundApp ?: return@let
                service.serviceScope.launch {
                    val prefs = service.currentPreferences ?: service.preferencesRepository.userPreferencesFlow.first()
                    val isProtected = prefs.protectedPackages.contains(pkg) || service.currentShieldCache != null
                    val currentTime = System.currentTimeMillis()
                    val isBedtimeNow = service.isBedtimeActiveNow(prefs, currentTime)
                    if (isBedtimeNow && isProtected && pkg !in service.bedtimeWhitelistedPackages) {
                        service.showBedtimeOverlay(pkg)
                    }
                }
            }
        }

        // --- Merged Anchor (Auto-Unlocker) state ---
        /** True while this service is connected (replaces AnchorAccessibilityService.isServiceActive). */
        val isServiceActive get() = isServiceRunning

        private var pendingPin: String? = null
        private var hasTyped = false
        private var isTyping = false

        /**
         * Called by [AnchorViewModel] to arm the PIN that should be gesture-typed
         * when an AppBlock PIN screen is detected.
         */
        fun setPendingPin(pin: String?) {
            pendingPin = pin?.trim()?.takeIf { candidate ->
                candidate.length in 4..8 && candidate.all { it.isDigit() }
            }
            hasTyped = false
            isTyping = false
            Log.d(TAG_ANCHOR, "Pending secure AppBlock unlock armed: ${pendingPin != null}")
        }

        private const val TAG_ANCHOR = "AnchorAccessibility"
        private const val TAP_DURATION_MS = 100L
        private const val DIGIT_SPACING_MS = 260L
        private const val CONFIRM_DELAY_AFTER_DIGITS_MS = 360L
        private const val ENTRY_SETTLE_DELAY_MS = 260L
        private const val TREE_LOG_THROTTLE_MS = 2_000L
        private const val LOG_CHUNK_SIZE = 3_500
        private val exactConfirmLabels = setOf(
            "ok", "done", "enter", "confirm", "submit",
            "verify", "verify code", "unlock", "continue", "next", "go"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "com.analoganchor.app.action.REFRESH_DATA") {
            refreshService()
        }
        return START_STICKY
    }

    private fun refreshService() {
        serviceScope.launch {
            try {
                val prefs = preferencesRepository.userPreferencesFlow.first()
                currentPreferences = prefs
                whitelistedPackages = prefs.whitelistedPackages
                bedtimeWhitelistedPackages = prefs.bedtimeWhitelistedPackages

                shieldRepository.isShieldsLoaded.first { it }
                allShieldsCache = shieldRepository.allShields.first()

                val schedules = shieldRepository.allSchedules.first()
                activeSchedules = schedules.filter { it.isActive }
                parsedSchedulesCache = activeSchedules.map { s ->
                    val startParts = s.startTime.split(":")
                    val endParts = s.endTime.split(":")
                    ParsedSchedule(
                        id = s.id,
                        startMinutes = (startParts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (startParts.getOrNull(1)?.toIntOrNull() ?: 0),
                        endMinutes = (endParts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (endParts.getOrNull(1)?.toIntOrNull() ?: 0),
                        mode = s.mode,
                        packageNames = s.packageNames.toSet()
                    )
                }

                updateRestrictedPackages()
                updateBedtimeStatus(prefs)

                usageStatsCache = null
                lastUsageCacheTime = 0L
                lastUsageFetchTime = 0L
                dailyUsageCache.clear()

                lastForegroundApp?.let { pkg ->
                    packageChangeFlow.tryEmit(pkg)
                }

                Log.d("ZenithAS", "Service refreshed successfully via REFRESH_DATA")
            } catch (e: Exception) {
                Log.e("ZenithAS", "Error refreshing service: ${e.message}")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceRunning = true
        lastEventTime = System.currentTimeMillis()

        // Start Day Guard persistent Foreground Service to prevent background process kills
        DayGuardForegroundService.start(applicationContext)

        // Sync widget & QS Tile state
        try {
            DayGuardWidgetProvider.updateAllWidgets(applicationContext)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.service.quicksettings.TileService.requestListeningState(
                    applicationContext,
                    android.content.ComponentName(applicationContext, DayGuardTileService::class.java)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        serviceScope.launch(Dispatchers.IO) {
            refreshLauncherCache()
            while (true) {
                delay(60000)
                refreshLauncherCache()
            }
        }

        val app = application as com.analoganchor.app.AnalogAnchorApplication
        shieldRepository = app.shieldRepository
        preferencesRepository = app.preferencesRepository
        overlayManager = InterceptOverlayManager(this, preferencesRepository)
        sessionUsageOverlayManager = SessionUsageOverlayManager(this)

        try {
            if (!isScreenReceiverRegistered) {
                val filter = android.content.IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction("com.analoganchor.app.action.DISMISS_ALL_OVERLAYS")
                }
                androidx.core.content.ContextCompat.registerReceiver(
                    this,
                    screenStateReceiver,
                    filter,
                    androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
                )
                isScreenReceiverRegistered = true
                Log.d("ZenithAS", "Registered screenStateReceiver with RECEIVER_NOT_EXPORTED")
            }
        } catch (e: Exception) {
            Log.e("ZenithAS", "Error registering screenStateReceiver: ${e.message}")
        }

        serviceScope.launch(Dispatchers.Main) {
            overlayManager.hideOverlay()
        }

        serviceScope.launch {
            shieldRepository.isShieldsLoaded.first { it }
            shieldRepository.allShields.collect { shields ->
                allShieldsCache = shields
                lastForegroundApp?.let { currentPkg ->
                    currentShieldCache = shields.find { it.packageName == currentPkg }
                }
                updateRestrictedPackages()
            }
        }

        serviceScope.launch {
            shieldRepository.isShieldsLoaded.first { it }
            shieldRepository.allSchedules.collect { schedules: List<ScheduleEntity> ->
                activeSchedules = schedules.filter { it.isActive }
                parsedSchedulesCache = activeSchedules.map { s ->
                    val startParts = s.startTime.split(":")
                    val endParts = s.endTime.split(":")
                    ParsedSchedule(
                        id = s.id,
                        startMinutes = (startParts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (startParts.getOrNull(1)?.toIntOrNull() ?: 0),
                        endMinutes = (endParts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (endParts.getOrNull(1)?.toIntOrNull() ?: 0),
                        mode = s.mode,
                        packageNames = s.packageNames.toSet()
                    )
                }
                updateRestrictedPackages()
            }
        }

        serviceScope.launch {
            preferencesRepository.userPreferencesFlow.collect { preferences ->
                currentPreferences = preferences
                whitelistedPackages = preferences.whitelistedPackages
                bedtimeWhitelistedPackages = preferences.bedtimeWhitelistedPackages

                val startParts = preferences.bedtimeStartTime.split(":")
                val endParts = preferences.bedtimeEndTime.split(":")
                cachedBedtimeStartMinutes = (startParts.getOrNull(0)?.toIntOrNull() ?: 22) * 60 + (startParts.getOrNull(1)?.toIntOrNull() ?: 0)
                cachedBedtimeEndMinutes = (endParts.getOrNull(0)?.toIntOrNull() ?: 7) * 60 + (endParts.getOrNull(1)?.toIntOrNull() ?: 0)

                updateBedtimeStatus(preferences)
            }
        }

        serviceScope.launch {
            packageChangeFlow.collectLatest { packageName ->
                try {
                    handlePackageChange(packageName)
                } catch (e: Exception) {
                    val currentTime = System.currentTimeMillis()
                    if (e.message != lastMonitoringError || currentTime - lastMonitoringErrorTime > 30000) {
                        Log.e("ZenithAS", "Error in handlePackageChange: ${e.message}", e)
                        lastMonitoringError = e.message
                        lastMonitoringErrorTime = currentTime
                    }
                }
            }
        }

        mainHandler.postDelayed({
            if (isDeviceLocked()) return@postDelayed
            rootInActiveWindow?.packageName?.toString()?.let { pkg ->
                if (pkg != packageName) {
                    packageChangeFlow.tryEmit(pkg)
                }
            }
        }, 1000)

        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        serviceScope.launch {
            while (true) {
                val currentTime = System.currentTimeMillis()
                val currentPkg = lastForegroundApp
                val isScreenInteractive = powerManager.isInteractive

                // Hardware-level Option B: Screen-Off Immediate Session Locking
                if (!isScreenInteractive) {
                    if (!isScreenOffRecorded || allowedApps.isNotEmpty() || waterPauseCompletedSessions.isNotEmpty()) {
                        isScreenOffRecorded = true
                        allowedApps.clear()
                        waterPauseCompletedSessions.clear()
                        currentPkg?.let { pkg ->
                            shieldRepository.clearPostWaterPauseGrace(pkg)
                        }
                        if (::sessionUsageOverlayManager.isInitialized) {
                            serviceScope.launch(Dispatchers.Main) {
                                sessionUsageOverlayManager.destroyAllHUDs()
                            }
                        }
                        if (::overlayManager.isInitialized) {
                            serviceScope.launch(Dispatchers.Main) {
                                overlayManager.hideOverlay()
                            }
                        }
                    }
                } else if (isScreenOffRecorded) {
                    // Screen woke back up!
                    isScreenOffRecorded = false
                    if (currentPkg != null && !shouldBypassBlocking(currentPkg)) {
                        checkIfAppIsShielded(currentPkg)
                    }
                }

                if (isScreenInteractive && !isDeviceLocked() && currentPkg != null && !shouldBypassBlocking(currentPkg)) {
                    val prefs = currentPreferences ?: preferencesRepository.userPreferencesFlow.first()
                    val shield = currentShieldCache ?: allShieldsCache.find { it.packageName == currentPkg } ?: shieldRepository.getShieldByPackageName(currentPkg)
                    val isProtected = (prefs.protectedPackages.contains(currentPkg)) || shield != null

                    // 1. Bedtime & Wind-Down Real-Time Enforcement (Takes priority over any active session)
                    val isBedtimeNow = isBedtimeActiveNow(prefs, currentTime)
                    val isWindDownNow = isWindDownActiveNow(prefs, currentTime) && prefs.bedtimeWindDownEnabled
                    val isBedtimeRestricted = isProtected && currentPkg !in bedtimeWhitelistedPackages

                    if ((isBedtimeNow || isWindDownNow) && isBedtimeRestricted) {
                        allowedApps.remove(currentPkg)
                        serviceScope.launch(Dispatchers.Main) {
                            sessionUsageOverlayManager.destroyAllHUDs()
                        }
                        if (!InterceptOverlayManager.isShowing) {
                            if (isBedtimeNow) {
                                showBedtimeOverlay(currentPkg)
                            } else {
                                showWindDownOverlay(currentPkg)
                            }
                        }
                    } else {
                        // 2. Post-Water-Pause Grace Expiry Check
                        val graceUntil = shieldRepository.postWaterPauseGraceUntil[currentPkg]
                        if (graceUntil != null && currentTime >= graceUntil) {
                            shieldRepository.clearPostWaterPauseGrace(currentPkg)
                            if (!InterceptOverlayManager.isShowing) {
                                checkIfAppIsShielded(currentPkg)
                            }
                        }

                        // 3. Daily Usage Limit Check
                        val rawLimit = if (isProtected) (prefs.protectedAppLimits[currentPkg] ?: 60) else -1
                        val limitMinutes = if (rawLimit <= 0 && isProtected) 60 else rawLimit
                        if (limitMinutes > 0) {
                            val limitMillis = limitMinutes * 60 * 1000L
                            val totalUsageToday = getTotalUsageToday(currentPkg)
                            val allowedUntilVal = allowedApps[currentPkg] ?: 0L
                            val isAllowedActive = currentTime < allowedUntilVal

                            if (totalUsageToday >= limitMillis && !isAllowedActive) {
                                val timeSinceLastKick = currentTime - lastKickTime
                                if (lastKickedPackage != currentPkg || timeSinceLastKick > 5000) {
                                    Log.d("ZenithAS", "Daily limit exceeded for $currentPkg: ${totalUsageToday}ms >= ${limitMillis}ms — showing limit overlay")
                                    lastKickTime = currentTime
                                    lastKickedPackage = currentPkg
                                    allowedApps.remove(currentPkg)
                                    checkIfAppIsShielded(currentPkg)
                                }
                            }
                        }

                        // 4. Continuous Shield / Session Expiration Enforcement (Loophole Eradication)
                        val isShieldedOrProtected = (shield != null) || (prefs.mindfulGatewayEnabled && prefs.protectedPackages.contains(currentPkg))
                        val isAppPaused = shield != null && isPaused(shield)

                        if (isShieldedOrProtected && !isAppPaused) {
                            val allowedUntil = allowedApps[currentPkg] ?: 0L
                            val isInGrace = shieldRepository.isInPostWaterPauseGrace(currentPkg)

                            if (currentTime >= allowedUntil && !isInGrace) {
                                allowedApps.remove(currentPkg)
                                if (!InterceptOverlayManager.isShowing) {
                                    if (shield?.isAutoQuitEnabled == true) {
                                        lastKickTime = currentTime
                                        lastKickedPackage = currentPkg
                                        goToHomeScreen()
                                    }
                                    checkIfAppIsShielded(currentPkg)
                                }
                            }
                        }
                    }
                }
                delay(1500)
            }
        }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Throttle for Anchor tree logging
    private var lastAnchorTreeLogAtMs = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            lastEventTime = System.currentTimeMillis()

            if (isDeviceLocked()) {
                if (InterceptOverlayManager.isShowing && ::overlayManager.isInitialized) {
                    serviceScope.launch(Dispatchers.Main) {
                        overlayManager.hideOverlay()
                    }
                }
                return
            }

            val packageName = event.packageName?.toString() ?: return
            if (packageName == this.packageName) return

            val className = event.className?.toString() ?: ""
            if (className.contains("Toast") || className.contains("Notification") || className.contains("Tooltip")) {
                return
            }

            // ── Merged Anchor: handle AppBlock PIN-entry ──────────────────────────
            if (AppBlockIntegration.isKnownAppBlockPackage(packageName)) {
                val rootNode = rootInActiveWindow
                if (rootNode != null && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    anchorLogTree(rootNode, "AppBlock window detected: $packageName")
                }

                val pin = pendingPin ?: anchorLoadPersistedPendingPin()
                if (pin != null && !hasTyped && !isTyping) {
                    val eventIsRelevant =
                        event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                        event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    if (eventIsRelevant) {
                        isTyping = true
                        serviceScope.launch {
                            delay(ENTRY_SETTLE_DELAY_MS)
                            val latestRoot = rootInActiveWindow
                            if (latestRoot == null) {
                                Log.w(TAG_ANCHOR, "AppBlock root disappeared before secure gesture unlock could start.")
                                isTyping = false
                                return@launch
                            }
                            if (!anchorLooksLikePinEntryScreen(latestRoot, pin)) {
                                Log.d(TAG_ANCHOR, "AppBlock visible but not a PIN keypad yet.")
                                isTyping = false
                                return@launch
                            }
                            val entryStarted = anchorEnterPin(latestRoot, pin)
                            if (!entryStarted) isTyping = false
                        }
                    }
                }
                // AppBlock events should not trigger the Zenith blocking flow.
                return
            }
            // ─────────────────────────────────────────────────────────────────────

            if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

            // --- UNINSTALL PREVENTION (TEMPORARILY BYPASSED FOR TESTING) ---
            // NOTE: Re-enable this block after testing by removing the 'if (false)' wrapper
            if (false && (packageName == "com.android.settings" || packageName.contains("packageinstaller") || packageName.contains("security") || packageName.contains("settings"))) {
                serviceScope.launch(Dispatchers.Main) {
                    val prefs = preferencesRepository.userPreferencesFlow.first()
                    if (System.currentTimeMillis() > prefs.policyEditWindowEndTimestamp) {
                        val rootNode = rootInActiveWindow
                        if (rootNode != null) {
                            val text = getRecursiveText(rootNode).lowercase()
                            val isZenith = text.contains("zenith") || text.contains("day guard") || text.contains("analog anchor")
                            val isUninstall = text.contains("uninstall") || text.contains("force stop") || text.contains("remove") || text.contains("deactivate") || text.contains("accessibility") ||
                                              text.contains("إلغاء التثبيت") || text.contains("إلغاء تثبيت") || text.contains("فرض الإيقاف") || text.contains("إيقاف إجباري") || text.contains("إزالة") || text.contains("تعطيل") || text.contains("إمكانية الوصول")
                            if (isZenith && isUninstall) {
                                goToHomeScreen()
                                val isArabic = java.util.Locale.getDefault().language == "ar"
                                val toastMsg = if (isArabic) "استخدم Anchor NFC لتعديل إعدادات Zenith" else "Use Anchor NFC to modify Zenith settings"
                                android.widget.Toast.makeText(this@ZenithAccessibilityService, toastMsg, android.widget.Toast.LENGTH_LONG).show()
                                return@launch
                            }
                        }
                    }
                }
            }
            // --------------------------------------------

            serviceScope.launch(Dispatchers.Main) {
                val activePackage = rootInActiveWindow?.packageName?.toString()
                val realPackage = when {
                    activePackage == this@ZenithAccessibilityService.packageName -> {
                        InterceptOverlayManager.currentPackage ?: packageName
                    }
                    activePackage != null && activePackage != "android" && activePackage != "com.android.systemui" -> {
                        activePackage
                    }
                    else -> {
                        packageName
                    }
                }
                overlayManager.checkAndHide(realPackage)
                packageChangeFlow.tryEmit(realPackage)
            }
        } catch (t: Throwable) {
            Log.e("ZenithAS", "Error handling accessibility event", t)
        }
    }



    private suspend fun handlePackageChange(currentApp: String) {
        if (isDeviceLocked()) {
            serviceScope.launch(Dispatchers.Main) {
                if (::overlayManager.isInitialized) overlayManager.hideOverlay()
            }
            return
        }
        if (currentApp == "com.android.systemui" || currentApp == "android" || currentApp == "com.android.keyguard" || currentApp == "com.miui.securitycenter" || currentApp == "com.xiaomi.finddevice") {
            return
        }
        if (currentApp == lastForegroundApp && currentShieldCache != null) {
            Log.d("ZenithAS", "handlePackageChange: SKIP - same app $currentApp with cached shield")
            return
        }

        // Detect if this is likely a late launcher event right after we showed an overlay
        val isLikelyLateLauncherEvent = (launcherAppsCache.isEmpty() || 
                                         launcherAppsCache.contains(currentApp) || 
                                         currentApp == defaultLauncherPackage ||
                                         currentApp.contains("launcher", ignoreCase = true) ||
                                         currentApp.contains("home", ignoreCase = true)) && 
                                        InterceptOverlayManager.isShowing && 
                                        (System.currentTimeMillis() - overlayManager.lastShowTime < 5000)

        if (isLikelyLateLauncherEvent) {
            Log.d("ZenithAS", "handlePackageChange: SKIP - late launcher event for $currentApp")
            return
        }

        val isRealApp = launcherAppsCache.isEmpty() || launcherAppsCache.contains(currentApp) || 
                        currentApp == packageName || 
                        currentApp == defaultLauncherPackage ||
                        currentApp.contains("launcher", ignoreCase = true) ||
                        currentApp.contains("home", ignoreCase = true) ||
                        mindfulGatewayStates.containsKey(currentApp) || 
                        allShieldsCache.any { it.packageName == currentApp }

        if (isRealApp) {
            val previousApp = lastForegroundApp
            lastForegroundApp = currentApp

            if (previousApp != currentApp) {
                currentSessionStartTimestamp = System.currentTimeMillis()
                previousApp?.let { prevPkg ->
                    waterPauseCompletedSessions.remove(prevPkg)
                    overlayManager.cancelGraceForPackage(prevPkg)
                    overlayManager.resetSessionCount(prevPkg)
                }
            }
            serviceScope.launch(Dispatchers.Main) {
                sessionUsageOverlayManager.updateForegroundApp(currentApp)
            }
        }

        if (shouldBypassBlocking(currentApp)) {
            Log.d("ZenithAS", "handlePackageChange: BYPASS for $currentApp")
            lastForegroundApp?.let { prevPkg ->
                allShieldsCache.find { it.packageName == prevPkg }?.let { shield ->
                    if (shield.isDelayAppEnabled) {
                        serviceScope.launch {
                            shieldRepository.updateShield(shield.copy(lastDelayStartTimestamp = 0L))
                        }
                    }
                }
            }
            currentShieldCache = null
            serviceScope.launch(Dispatchers.Main) {
                overlayManager.checkAndHide(currentApp)
                sessionUsageOverlayManager.updateForegroundApp(currentApp)
            }
            return
        }

        val shield = allShieldsCache.find { it.packageName == currentApp }
        currentShieldCache = shield
        Log.d("ZenithAS", "handlePackageChange: app=$currentApp shield=${shield != null} shieldPkg=${shield?.packageName} isRealApp=$isRealApp allShieldsCount=${allShieldsCache.size}")

        checkBlockingInstant(currentApp, shield)
    }

    private suspend fun checkBlockingInstant(currentApp: String, shield: ShieldEntity?) {
        if (isDeviceLocked()) return
        if (shouldBypassBlocking(currentApp)) return

        val currentTime = System.currentTimeMillis()
        val isAppPaused = shield != null && isPaused(shield)

        if (!isAppPaused) {
            val allowedUntil = allowedApps[currentApp] ?: 0L
            val prefs = currentPreferences ?: preferencesRepository.userPreferencesFlow.first()
            val isProtected = prefs.protectedPackages.contains(currentApp) || shield != null
            val isBedtimeNow = isBedtimeActiveNow(prefs, currentTime)
            val isWindDownNow = isWindDownActiveNow(prefs, currentTime)
            val isBedtimeBlocking = (isBedtimeNow || isWindDownNow) && isProtected
            val shouldCheckSchedules = (isBedtimeBlocking && currentApp !in bedtimeWhitelistedPackages) || currentTime > allowedUntil
            val isInGrace = shieldRepository.isInPostWaterPauseGrace(currentApp)

            Log.d("ZenithAS", "checkBlockingInstant: app=$currentApp shield=${shield != null} isPaused=$isAppPaused shouldCheck=$shouldCheckSchedules isShowing=${InterceptOverlayManager.isShowing} isInGrace=$isInGrace allowedUntil=$allowedUntil isBedtimeBlocking=$isBedtimeBlocking")

            if (shouldCheckSchedules && !InterceptOverlayManager.isShowing && !isInGrace) {
                val isScheduled = checkSchedules(currentApp)
                Log.d("ZenithAS", "checkBlockingInstant: isScheduled=$isScheduled mindfulGateway=${prefs.mindfulGatewayEnabled} currentTime=$currentTime > allowedUntil=$allowedUntil = ${currentTime > allowedUntil}")
                if (!isScheduled && (shield != null || (prefs.mindfulGatewayEnabled && prefs.protectedPackages.contains(currentApp) && !shouldBypassBlocking(currentApp))) && currentTime > allowedUntil) {
                    Log.d("ZenithAS", "checkBlockingInstant: CALLING checkIfAppIsShielded for $currentApp")
                    checkIfAppIsShielded(currentApp)
                }
            }
        } else {
            Log.d("ZenithAS", "checkBlockingInstant: app=$currentApp is PAUSED, skipping")
        }
    }

    private fun updateUsageTime(packageName: String, startTime: Long, baseUsage: Long, shield: ShieldEntity?) {
        if (shield == null) return
        val currentTime = System.currentTimeMillis()

        val sessionElapsed = currentTime - startTime
        val currentTotalUsage = baseUsage + sessionElapsed
        cachedTotalUsage = currentTotalUsage

        if (shield.type == FocusType.GOAL) {
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
            val remaining = (limitMillis - currentTotalUsage).coerceAtLeast(0L)

            val uiUpdateInterval = when {
                remaining < 60000 -> 5000L
                remaining < 300000 -> 10000L
                remaining < 900000 -> 20000L
                else -> 30000L
            }

            if (currentTime - lastUsageFetchTime >= uiUpdateInterval) {
                val usageToReport = currentTotalUsage
                serviceScope.launch(Dispatchers.Main) {
                    sessionUsageOverlayManager.updateHUDUsage(packageName, usageToReport)
                }
                lastUsageFetchTime = currentTime
            }
        } else {
            if (currentTime - lastUsageFetchTime > 5000) {
                cachedTotalUsage = getTotalUsageToday(packageName)
                lastUsageFetchTime = currentTime
            }
        }

        val limitMillis = shield.timeLimitMinutes * 60 * 1000L
        val remainingMillis = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)

        val timeSinceLastUsed = currentTime - shield.lastUsedTimestamp
        val isNearLimit = remainingMillis < 60000
        val shouldUpdateDB = timeSinceLastUsed > 15000 || (isNearLimit && timeSinceLastUsed > 5000)

        if (shouldUpdateDB) {
            val updatedShield = shield.copy(
                remainingTimeMillis = remainingMillis,
                lastUsedTimestamp = currentTime,
                lastGoalReminderTimestamp = if (shield.type == FocusType.GOAL) currentTime else shield.lastGoalReminderTimestamp
            )
            serviceScope.launch {
                try {
                    val exists = shieldRepository.getShieldByPackageName(updatedShield.packageName)
                    if (exists != null) {
                        shieldRepository.updateShield(updatedShield)
                    } else {
                        if (lastForegroundApp == updatedShield.packageName) {
                            currentShieldCache = null
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ZenithAS", "Failed background DB update: ${e.message}")
                }
            }
            if (currentShieldCache?.packageName == packageName) {
                currentShieldCache = updatedShield
            }

            if (shield.type == FocusType.GOAL && !isPaused(shield)) {
                if (currentTotalUsage >= limitMillis && !notifiedGoals.contains(packageName)) {
                    sendGoalReachedNotification(shield.appName, packageName)
                    notifiedGoals.add(packageName)
                }
            }
        }
    }

    private fun sendGoalReachedNotification(appName: String, packageName: String) {
        val channelId = "zenith_goal_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId, "Goal Reminders", NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Goal Achieved!")
            .setContentText("You've reached your target usage for $appName. Keep it up!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(packageName.hashCode(), notification)
        } catch (_: Exception) {}
    }

    private val mindfulGatewayStates get() = shieldRepository.mindfulGatewayStates

    private fun getMindfulShield(packageName: String, appName: String): ShieldEntity {
        val existing = mindfulGatewayStates[packageName]
        val currentTime = System.currentTimeMillis()
        val rawLimit = currentPreferences?.protectedAppLimits?.get(packageName) ?: 60
        val configuredLimit = if (rawLimit <= 0) 60 else rawLimit

        if (existing == null) {
            val newShield = ShieldEntity(
                packageName = packageName,
                appName = appName,
                timeLimitMinutes = configuredLimit,
                maxUsesPerPeriod = 5,
                refreshPeriodMinutes = 60,
                maxEmergencyUses = 3,
                emergencyUseCount = 3,
                lastPeriodResetTimestamp = currentTime,
                lastEmergencyRechargeTimestamp = currentTime,
                lastUsedTimestamp = currentTime,
                isDelayAppEnabled = true
            )
            mindfulGatewayStates[packageName] = newShield
            return newShield
        }

        var updated = existing
        if (updated.timeLimitMinutes != configuredLimit) {
            updated = updated.copy(timeLimitMinutes = configuredLimit)
        }
        if (!updated.isDelayAppEnabled) {
            updated = updated.copy(isDelayAppEnabled = true)
        }
        val rechargeDurationMillis = if (com.analoganchor.app.viewmodel.AnchorViewModel.ANCHOR_TAP_ONE_TEST_MODE) {
            25 * 1000L
        } else {
            (currentPreferences?.emergencyRechargeDurationMinutes ?: 60) * 60 * 1000L
        }
        if (updated.emergencyUseCount < updated.maxEmergencyUses && rechargeDurationMillis > 0) {
            val timeSinceLastRecharge = currentTime - updated.lastEmergencyRechargeTimestamp
            if (timeSinceLastRecharge >= rechargeDurationMillis) {
                val chargesToAdd = (timeSinceLastRecharge / rechargeDurationMillis).toInt()
                updated = updated.copy(
                    emergencyUseCount = (updated.emergencyUseCount + chargesToAdd).coerceAtMost(updated.maxEmergencyUses),
                    lastEmergencyRechargeTimestamp = updated.lastEmergencyRechargeTimestamp + (chargesToAdd * rechargeDurationMillis)
                )
            }
        }

        if (currentTime - updated.lastPeriodResetTimestamp > updated.refreshPeriodMinutes * 60 * 1000L) {
            updated = updated.copy(
                currentPeriodUses = 0,
                lastPeriodResetTimestamp = currentTime
            )
        }

        mindfulGatewayStates[packageName] = updated
        return updated
    }

    private suspend fun checkIfAppIsShielded(targetPackageName: String) {
        if (isDeviceLocked()) return
        Log.d("ZenithAS", "checkIfAppIsShielded: pkg=$targetPackageName lastForeground=$lastForegroundApp")
        if (targetPackageName != lastForegroundApp) {
            Log.d("ZenithAS", "checkIfAppIsShielded: SKIP - not foreground")
            return
        }

        if (shieldRepository.isInPostWaterPauseGrace(targetPackageName)) {
            Log.d("ZenithAS", "checkIfAppIsShielded: SKIP - in grace")
            return
        }

        val allowedUntil = allowedApps[targetPackageName] ?: 0L
        if (System.currentTimeMillis() < allowedUntil) {
            Log.d("ZenithAS", "checkIfAppIsShielded: SKIP - still allowed until $allowedUntil")
            return
        }

        val shield = currentShieldCache ?: allShieldsCache.find { it.packageName == targetPackageName }
        val prefs = currentPreferences ?: preferencesRepository.userPreferencesFlow.first()
        val isMindfulGateway = shield == null && prefs.mindfulGatewayEnabled && prefs.protectedPackages.contains(targetPackageName) && !shouldBypassBlocking(targetPackageName)

        val appName = shield?.appName ?: try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(targetPackageName, 0)).toString()
        } catch (_: Exception) { targetPackageName }

        val effectiveShield = if (isMindfulGateway) getMindfulShield(targetPackageName, appName) else shield
        Log.d("ZenithAS", "checkIfAppIsShielded: effectiveShield=${effectiveShield != null} isMindfulGateway=$isMindfulGateway isShowing=${InterceptOverlayManager.isShowing}")

        if (effectiveShield != null && !InterceptOverlayManager.isShowing) {
            Log.d("ZenithAS", "checkIfAppIsShielded: SHOWING OVERLAY for $targetPackageName")
            val totalUsageToday = getTotalUsageToday(targetPackageName)
            val totalGlobalUsageToday = getTotalGlobalUsageToday()
            val delayDurationSeconds = prefs.delayAppDurationSeconds

            val currentTime = System.currentTimeMillis()
            val lastAction = effectiveShield.lastDelayStartTimestamp

            val lastSessionEnd = effectiveShield.lastSessionEndTimestamp
            val gracePeriodMillis = if (effectiveShield.isDelayAppEnabled) 15 * 1000L else 5 * 60 * 1000L
            val isGracePeriodActive = lastSessionEnd != 0L && (currentTime - lastSessionEnd < gracePeriodMillis)

            val shieldWithTimestamp = if (effectiveShield.isDelayAppEnabled) {
                if (isGracePeriodActive) {
                    val updated = effectiveShield.copy(lastDelayStartTimestamp = currentTime - (delayDurationSeconds * 1000L) - 1000)
                    if (!isMindfulGateway) serviceScope.launch { shieldRepository.updateShield(updated) }
                    else mindfulGatewayStates[targetPackageName] = updated
                    updated
                } else if (lastAction == 0L) {
                    val updated = effectiveShield.copy(lastDelayStartTimestamp = currentTime)
                    if (!isMindfulGateway) serviceScope.launch { shieldRepository.updateShield(updated) }
                    else mindfulGatewayStates[targetPackageName] = updated
                    updated
                } else {
                    effectiveShield
                }
            } else {
                effectiveShield
            }

            serviceScope.launch(Dispatchers.Main) {
                val skipWP = waterPauseCompletedSessions.contains(targetPackageName)
                val overlayShield = if (skipWP && shieldWithTimestamp.isDelayAppEnabled && delayDurationSeconds > 0) {
                    shieldWithTimestamp.copy(
                        lastDelayStartTimestamp = currentTime - (delayDurationSeconds * 1000L) - 1000
                    )
                } else {
                    shieldWithTimestamp
                }
                overlayManager.showOverlay(
                    packageName = targetPackageName,
                    appName = appName,
                    shield = overlayShield,
                    totalUsageToday = totalUsageToday,
                    totalGlobalUsageToday = totalGlobalUsageToday,
                    delayDurationSeconds = delayDurationSeconds,
                    currentPreferences = prefs,
                    skipWaterPause = skipWP,
                    onWaterPauseCompleted = {
                        waterPauseCompletedSessions.add(targetPackageName)
                        overlayManager.completeWaterPauseAndBeginGrace(targetPackageName) {
                            serviceScope.launch {
                                if (lastForegroundApp == targetPackageName) {
                                    checkIfAppIsShielded(targetPackageName)
                                }
                            }
                        }
                    },
                    onAllowUse = { minutes, isEmergency ->
                        val currentTimeOnUnlock = System.currentTimeMillis()
                        overlayManager.cancelGraceForPackage(targetPackageName)
                        val rawLimit = currentPreferences?.protectedAppLimits?.get(targetPackageName) ?: 60
                        val dailyLimitMinutes = if (rawLimit <= 0) 60 else rawLimit
                        val currentUsageMillis = getTotalUsageToday(targetPackageName)
                        val remainingDailyMillis = (dailyLimitMinutes * 60 * 1000L - currentUsageMillis).coerceAtLeast(0L)
                        val sessionMillis = if (isEmergency) {
                            minutes * 60 * 1000L
                        } else {
                            (minutes * 60 * 1000L).coerceAtMost(remainingDailyMillis)
                        }
                        allowedApps[targetPackageName] = currentTimeOnUnlock + sessionMillis

                        serviceScope.launch {
                            if (isMindfulGateway) {
                                val currentMindful = mindfulGatewayStates[targetPackageName] ?: shieldWithTimestamp
                                val updatedMindful = if (isEmergency) {
                                    currentMindful.copy(
                                        emergencyUseCount = (currentMindful.emergencyUseCount - 1).coerceAtLeast(0),
                                        lastSessionEndTimestamp = currentTimeOnUnlock
                                    )
                                } else {
                                    val periodExpired = currentTimeOnUnlock - currentMindful.lastPeriodResetTimestamp > currentMindful.refreshPeriodMinutes * 60 * 1000L
                                    currentMindful.copy(
                                        currentPeriodUses = if (periodExpired) 1 else currentMindful.currentPeriodUses + 1,
                                        lastPeriodResetTimestamp = if (periodExpired) currentTimeOnUnlock else currentMindful.lastPeriodResetTimestamp,
                                        lastSessionEndTimestamp = currentTimeOnUnlock
                                    )
                                }
                                mindfulGatewayStates[targetPackageName] = updatedMindful
                            } else {
                                val currentShield = shieldRepository.getShieldByPackageName(targetPackageName)
                                val updatedShield = if (currentShield != null) {
                                    if (isEmergency) {
                                        currentShield.copy(
                                            emergencyUseCount = (currentShield.emergencyUseCount - 1).coerceAtLeast(0),
                                            lastDelayStartTimestamp = 0L,
                                            lastSessionEndTimestamp = currentTimeOnUnlock
                                        )
                                    } else {
                                        val periodExpired = currentTimeOnUnlock - currentShield.lastPeriodResetTimestamp > currentShield.refreshPeriodMinutes * 60 * 1000L
                                        currentShield.copy(
                                            currentPeriodUses = if (periodExpired) 1 else currentShield.currentPeriodUses + 1,
                                            lastPeriodResetTimestamp = if (periodExpired) currentTimeOnUnlock else currentShield.lastPeriodResetTimestamp,
                                            lastDelayStartTimestamp = 0L,
                                            lastSessionEndTimestamp = currentTimeOnUnlock
                                        )
                                    }
                                } else null

                                if (updatedShield != null) {
                                    shieldRepository.updateShield(updatedShield)
                                    currentShieldCache = updatedShield
                                }
                            }

                            val currentPrefs = preferencesRepository.userPreferencesFlow.first()
                            if (currentPrefs.sessionUsageOverlayEnabled) {
                                val isGoal = shieldWithTimestamp.type == FocusType.GOAL
                                val limitMillis = (shieldWithTimestamp.timeLimitMinutes) * 60 * 1000L
                                val currentUsage = getTotalUsageToday(targetPackageName)

                                if (!(isGoal && (currentUsage >= limitMillis || notifiedGoals.contains(targetPackageName)) && limitMillis > 0)) {
                                    serviceScope.launch(Dispatchers.Main) {
                                        sessionUsageOverlayManager.showHUD(
                                            targetPackageName,
                                            if (isGoal) shieldWithTimestamp.timeLimitMinutes else minutes,
                                            currentPrefs.sessionUsageOverlaySize,
                                            currentPrefs.sessionUsageOverlayOpacity,
                                            isGoal = isGoal,
                                            initialSeconds = if (isGoal) (currentUsage / 1000).toInt() else 0,
                                            onSessionEnd = {
                                                allowedApps[targetPackageName] = 0L
                                                serviceScope.launch {
                                                    val s = currentShieldCache ?: mindfulGatewayStates[targetPackageName] ?: shieldRepository.getShieldByPackageName(targetPackageName)
                                                    if (s?.isAutoQuitEnabled == true) {
                                                        if (lastForegroundApp == targetPackageName) {
                                                            goToHomeScreen()
                                                        }
                                                    } else {
                                                        checkIfAppIsShielded(targetPackageName)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    onCloseApp = {
                        serviceScope.launch {
                            val s = currentShieldCache ?: allShieldsCache.find { it.packageName == targetPackageName } ?: shieldRepository.getShieldByPackageName(targetPackageName)
                            if (s != null && s.isDelayAppEnabled) {
                                val updated = s.copy(lastDelayStartTimestamp = 0L)
                                shieldRepository.updateShield(updated)
                                currentShieldCache = updated
                            }
                        }
                        lastKickTime = System.currentTimeMillis()
                        lastKickedPackage = targetPackageName
                        goToHomeScreen()
                    },
                    onGoalDismiss = {
                        allowedApps[targetPackageName] = System.currentTimeMillis() + (60 * 60 * 1000L)
                    }
                )
            }
        }
    }

    private var cachedTotalGlobalUsage: Long = 0L
    private var lastGlobalUsageCacheTime: Long = 0L
    private var launcherAppsCache = emptySet<String>()
    private var defaultLauncherPackage: String? = null
    private var lastLauncherAppsRefreshTime = 0L

    private fun refreshLauncherCache() {
        try {
            val pm = packageManager
            launcherAppsCache = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            ).map { it.activityInfo.packageName }.toSet()

            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            defaultLauncherPackage = pm.resolveActivity(launcherIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName

            lastLauncherAppsRefreshTime = System.currentTimeMillis()
        } catch (_: Exception) {}
    }

    private fun getTotalGlobalUsageToday(): Long {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastGlobalUsageCacheTime < 5000) {
            return cachedTotalGlobalUsage
        }

        if (currentTime - lastLauncherAppsRefreshTime > 3600000 || launcherAppsCache.isEmpty()) {
            refreshLauncherCache()
        }

        val excludePackages = setOfNotNull(packageName, defaultLauncherPackage)

        val detailedUsage = com.analoganchor.app.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager)
        val accurateUsageMap = detailedUsage.appUsageMap

        var totalToday = 0L
        accurateUsageMap.forEach { (pkg, time) ->
            if (pkg !in excludePackages && pkg in launcherAppsCache) {
                if (time > 0) {
                    totalToday += time
                }
            }
        }

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        cachedTotalGlobalUsage = totalToday.coerceAtMost(currentTime - todayStart)
        lastGlobalUsageCacheTime = currentTime
        return cachedTotalGlobalUsage
    }

    private fun getTotalUsageToday(packageName: String): Long {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastUsageCacheTime > 10000) {
            val detailedUsage = com.analoganchor.app.util.ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager)
            val tempMap = detailedUsage.appUsageMap

            dailyUsageCache.clear()
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val timeSinceMidnight = currentTime - todayStart

            tempMap.forEach { (pkg, time) ->
                val cappedTime = if (time > timeSinceMidnight) timeSinceMidnight else time
                if (cappedTime > 0) dailyUsageCache[pkg] = cappedTime
            }
            lastUsageCacheTime = currentTime
        }

        val baseUsage = dailyUsageCache[packageName] ?: 0L
        return if (packageName == lastForegroundApp && currentSessionStartTimestamp > 0L) {
            baseUsage + (currentTime - currentSessionStartTimestamp)
        } else {
            baseUsage
        }
    }

    private fun goToHomeScreen() {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(homeIntent)
        } catch (e: Exception) {
            android.util.Log.e("ZenithAccessibility", "Failed to launch home intent directly", e)
        }
        if (!isDeviceLocked()) {
            try {
                performGlobalAction(GLOBAL_ACTION_HOME)
            } catch (e: Exception) {
                android.util.Log.e("ZenithAccessibility", "Failed to perform GLOBAL_ACTION_HOME", e)
            }
        }
    }

    private fun isBedtimeActiveNow(prefs: UserPreferences, now: Long = System.currentTimeMillis()): Boolean {
        if (!prefs.bedtimeEnabled) return false

        val startParts = prefs.bedtimeStartTime.split(":")
        val endParts = prefs.bedtimeEndTime.split(":")
        val startMinutes = (startParts.getOrNull(0)?.toIntOrNull() ?: 22) * 60 + (startParts.getOrNull(1)?.toIntOrNull() ?: 0)
        val endMinutes = (endParts.getOrNull(0)?.toIntOrNull() ?: 5) * 60 + (endParts.getOrNull(1)?.toIntOrNull() ?: 0)

        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val yesterdayCal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterdayDay = yesterdayCal.get(Calendar.DAY_OF_WEEK)

        return if (startMinutes <= endMinutes) {
            currentDay in prefs.bedtimeDays && currentMinutes in startMinutes until endMinutes
        } else {
            (currentDay in prefs.bedtimeDays && currentMinutes >= startMinutes) ||
            (yesterdayDay in prefs.bedtimeDays && currentMinutes < endMinutes)
        }
    }

    private fun isWindDownActiveNow(prefs: UserPreferences, now: Long = System.currentTimeMillis()): Boolean {
        if (!prefs.bedtimeEnabled || !prefs.bedtimeWindDownEnabled) return false
        if (isBedtimeActiveNow(prefs, now)) return false

        val startParts = prefs.bedtimeStartTime.split(":")
        val startMinutes = (startParts.getOrNull(0)?.toIntOrNull() ?: 22) * 60 + (startParts.getOrNull(1)?.toIntOrNull() ?: 0)
        val windDownStartMinutes = (startMinutes - 30 + 1440) % 1440

        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val yesterdayCal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterdayDay = yesterdayCal.get(Calendar.DAY_OF_WEEK)

        return if (windDownStartMinutes < startMinutes) {
            currentDay in prefs.bedtimeDays && currentMinutes in windDownStartMinutes until startMinutes
        } else {
            (currentDay in prefs.bedtimeDays && currentMinutes >= windDownStartMinutes) ||
            (yesterdayDay in prefs.bedtimeDays && currentMinutes < startMinutes)
        }
    }

    private fun updateBedtimeStatus(prefs: UserPreferences) {
        val currentTime = System.currentTimeMillis()
        val active = isBedtimeActiveNow(prefs, currentTime)
        val windDownActive = isWindDownActiveNow(prefs, currentTime)

        val wasWindDownActive = isWindDownActive
        isBedtimeActive = active
        isWindDownActive = windDownActive

        if (isWindDownActive && !wasWindDownActive) {
            windDownUsedPackages.clear()
        }
    }

    private fun checkSchedules(packageName: String): Boolean {
        if (shouldBypassBlocking(packageName)) return false

        val prefs = currentPreferences ?: return false
        val isProtected = prefs.protectedPackages.contains(packageName) || allShieldsCache.any { it.packageName == packageName }

        val now = System.currentTimeMillis()
        val isBedtime = isBedtimeActiveNow(prefs, now)
        if (isBedtime) {
            // Bedtime ONLY blocks user-protected distraction apps
            // Blocks them strictly regardless of daily limit or session time remaining
            if (isProtected && packageName !in bedtimeWhitelistedPackages) {
                showBedtimeOverlay(packageName)
                return true
            }
            return false
        }

        val isWindDown = isWindDownActiveNow(prefs, now)
        if (isWindDown && prefs.bedtimeWindDownEnabled) {
            if (isProtected && packageName !in bedtimeWhitelistedPackages) {
                showWindDownOverlay(packageName)
                return true
            }
            return false
        }

        val allowedUntil = allowedApps[packageName] ?: 0L
        if (System.currentTimeMillis() < allowedUntil) return false

        val currentTotalMinutes = synchronized(reusableCalendar) {
            reusableCalendar.timeInMillis = now
            reusableCalendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + reusableCalendar.get(java.util.Calendar.MINUTE)
        }

        for (ps in parsedSchedulesCache) {
            val isInInterval = if (ps.startMinutes <= ps.endMinutes) {
                currentTotalMinutes in ps.startMinutes..ps.endMinutes
            } else {
                currentTotalMinutes >= ps.startMinutes || currentTotalMinutes <= ps.endMinutes
            }

            if (isInInterval) {
                when (ps.mode) {
                    com.analoganchor.app.data.local.entity.ScheduleMode.BLOCK -> {
                        if (packageName in ps.packageNames) {
                            showScheduleOverlayFromParsed(packageName, ps)
                            return true
                        }
                    }
                    com.analoganchor.app.data.local.entity.ScheduleMode.ALLOW -> {
                        if (packageName !in ps.packageNames) {
                            showScheduleOverlayFromParsed(packageName, ps)
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun showBedtimeOverlay(packageName: String) {
        serviceScope.launch(Dispatchers.Main) {
            val appName = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            } catch (_: Exception) { packageName }

            overlayManager.showBedtimeOverlay(
                packageName = packageName,
                appName = appName,
                onCloseApp = {
                    lastKickTime = System.currentTimeMillis()
                    lastKickedPackage = packageName
                    goToHomeScreen()
                }
            )
        }
    }

    private fun showWindDownOverlay(packageName: String) {
        val sessionUsed = windDownUsedPackages[packageName] ?: false
        serviceScope.launch(Dispatchers.Main) {
            val appName = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            } catch (_: Exception) { packageName }

            overlayManager.showWindDownOverlay(
                packageName = packageName,
                appName = appName,
                sessionUsed = sessionUsed,
                onAllowUse = { minutes ->
                    allowedApps[packageName] = System.currentTimeMillis() + (minutes * 60 * 1000L)
                    windDownUsedPackages[packageName] = true
                },
                onCloseApp = {
                    lastKickTime = System.currentTimeMillis()
                    lastKickedPackage = packageName
                    goToHomeScreen()
                }
            )
        }
    }


    private fun showScheduleOverlayFromParsed(packageName: String, ps: ParsedSchedule) {
        val originalSchedule = activeSchedules.find { it.id == ps.id } ?: return
        showScheduleOverlay(packageName, originalSchedule)
    }

    private fun isPaused(shield: ShieldEntity): Boolean {
        if (!shield.isPaused) return false
        if (shield.pauseEndTimestamp == 0L) return true
        return System.currentTimeMillis() < shield.pauseEndTimestamp
    }

    private fun shouldBypassBlocking(packageName: String): Boolean {
        if (packageName == this.packageName) return true

        val prefs = currentPreferences
        val isProtected = (prefs?.protectedPackages?.contains(packageName) == true) || allShieldsCache.any { it.packageName == packageName }
        val isBedtimeOrWindDown = prefs != null && (isBedtimeActiveNow(prefs) || isWindDownActiveNow(prefs))

        if (isBedtimeOrWindDown) {
            // Non-protected apps bypass bedtime & wind-down completely so they stay open
            if (!isProtected) return true

            if (packageName in bedtimeWhitelistedPackages && packageName !in restrictedPackages) {
                if (prefs?.mindfulGatewayEnabled != true) return true
            }
        } else {
            if (packageName in whitelistedPackages) return true
        }

        if (packageName in CRITICAL_SYSTEM_PACKAGES) return true
        if (packageName.contains("launcher", ignoreCase = true) || packageName.contains("home", ignoreCase = true)) return true

        val isSystem = systemAppCache.getOrPut(packageName) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                (appInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            } catch (_: Exception) { false }
        }

        if (isSystem) {
            if (packageName.contains("car.mode", ignoreCase = true)) return true
            if (isBedtimeOrWindDown) return !isProtected
            val isMindfulActive = prefs?.mindfulGatewayEnabled == true
            return !(packageName in restrictedPackages || hasGlobalAllowSchedule || isMindfulActive)
        }

        return false
    }

    private fun updateRestrictedPackages() {
        val shieldPkgs = allShieldsCache.map { it.packageName }.toSet()
        val schedulePkgs = activeSchedules.filter { it.mode == ScheduleMode.BLOCK }
            .flatMap { it.packageNames }.toSet()
        hasGlobalAllowSchedule = activeSchedules.any { it.mode == ScheduleMode.ALLOW }
        restrictedPackages = shieldPkgs + schedulePkgs + BLOCKABLE_SYSTEM_APPS
    }

    private val CRITICAL_SYSTEM_PACKAGES = setOf(
        "android", "com.android.systemui", "com.android.keyguard", "com.android.settings",
        "com.android.phone", "com.android.server.telecom", "com.google.android.packageinstaller",
        "com.android.packageinstaller", "com.google.android.permissioncontroller",
        "com.samsung.android.app.cocktailbarservice", "com.samsung.android.honeyboard",
        "com.samsung.android.biometrics.app.setting", "com.miui.securitycenter",
        "com.miui.home", "com.miui.guardprovider", "com.google.android.inputmethod.latin",
        "com.touchtype.swiftkey"
    )
    private val BLOCKABLE_SYSTEM_APPS = setOf(
        "com.google.android.youtube", "com.android.chrome",
        "com.google.android.apps.youtube.music", "com.android.vending"
    )

    private fun showScheduleOverlay(packageName: String, schedule: ScheduleEntity) {
        val totalGlobalUsageToday = getTotalGlobalUsageToday()
        serviceScope.launch(Dispatchers.Main) {
            overlayManager.showScheduleOverlay(
                packageName = packageName,
                appName = packageName,
                schedule = schedule,
                totalGlobalUsageToday = totalGlobalUsageToday,
                onAllowUse = { minutes, isEmergency ->
                    if (isEmergency) {
                        allowedApps[packageName] = System.currentTimeMillis() + (minutes * 60 * 1000L)
                        serviceScope.launch {
                            val currentSchedule = shieldRepository.getScheduleById(schedule.id) ?: return@launch
                            shieldRepository.updateSchedule(currentSchedule.copy(emergencyUseCount = currentSchedule.emergencyUseCount - 1))

                            val prefs = preferencesRepository.userPreferencesFlow.first()
                            if (prefs.sessionUsageOverlayEnabled) {
                                serviceScope.launch(Dispatchers.Main) {
                                    sessionUsageOverlayManager.showHUD(
                                        packageName,
                                        minutes,
                                        prefs.sessionUsageOverlaySize,
                                        prefs.sessionUsageOverlayOpacity,
                                        initialSeconds = 0,
                                        onSessionEnd = {
                                            allowedApps[packageName] = 0L
                                            serviceScope.launch {
                                                val shield = shieldRepository.getShieldByPackageName(packageName)
                                                if (shield != null) {
                                                    val updated = shield.copy(lastSessionEndTimestamp = System.currentTimeMillis())
                                                    shieldRepository.updateShield(updated)
                                                    currentShieldCache = updated

                                                    if (updated.isAutoQuitEnabled) {
                                                        if (lastForegroundApp == packageName) {
                                                            goToHomeScreen()
                                                        }
                                                    } else {
                                                        checkIfAppIsShielded(packageName)
                                                    }
                                                } else {
                                                    checkIfAppIsShielded(packageName)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                onCloseApp = {
                    lastKickTime = System.currentTimeMillis()
                    lastKickedPackage = packageName
                    goToHomeScreen()
                }
            )
        }
    }

    private fun getRecursiveText(node: android.view.accessibility.AccessibilityNodeInfo): String {
        val sb = java.lang.StringBuilder()
        if (node.text != null) {
            sb.append(node.text).append(" ")
        }
        if (node.contentDescription != null) {
            sb.append(node.contentDescription).append(" ")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                sb.append(getRecursiveText(child))
                child.recycle()
            }
        }
        return sb.toString()
    }

    // =========================================================================
    // Merged Anchor (Auto-Unlocker) — PIN gesture-entry helpers
    // =========================================================================

    private fun anchorLoadPersistedPendingPin(): String? {
        val stored = PendingAutofillPinStore.getPendingPin(applicationContext)
        if (stored != null) {
            pendingPin = stored
            hasTyped = false
            isTyping = false
        }
        return stored
    }

    private fun anchorClearPendingPin() {
        pendingPin = null
        PendingAutofillPinStore.clear(applicationContext)
    }

    private fun anchorEnterPin(root: AccessibilityNodeInfo, pin: String): Boolean {
        val editTextNode = anchorFindStandardEditTextNode(root)
        if (editTextNode != null) {
            val filled = anchorTrySetEditText(editTextNode, pin)
            Log.d(TAG_ANCHOR, "Standard EditText fallback attempted; success=$filled")
            if (filled) {
                val confirmNode = anchorFindConfirmNode(root)
                if (confirmNode == null) {
                    anchorMarkCompleted("standard EditText accepted PIN and no confirm button was exposed")
                    return true
                }
                val confirmTap = anchorTapFromNode(confirmNode, "confirm button") ?: run {
                    Log.w(TAG_ANCHOR, "Confirm node had no usable screen bounds after EditText fill.")
                    return false
                }
                return anchorDispatchTapSequence(
                    strokes = listOf(confirmTap.copy(startOffsetMs = 0L)),
                    reason = "standard EditText confirm"
                )
            }
        }

        val strokes = anchorBuildDigitGesturePlan(root, pin) ?: return false
        return anchorDispatchTapSequence(strokes, "dynamic AppBlock keypad")
    }

    private fun anchorTrySetEditText(node: AccessibilityNodeInfo, pin: String): Boolean {
        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, pin)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (e: Exception) {
            Log.e(TAG_ANCHOR, "EditText ACTION_SET_TEXT failed: ${e.message}")
            false
        }
    }

    private fun anchorBuildDigitGesturePlan(root: AccessibilityNodeInfo, pin: String): List<AnchorTapStroke>? {
        val digitTargets = anchorFindDigitTargets(root)
        val missingDigits = pin.toSet().filterNot { digitTargets.containsKey(it) }
        if (missingDigits.isNotEmpty()) {
            Log.w(TAG_ANCHOR, "Aborting unlock. Missing digit nodes: ${missingDigits.joinToString("")}")
            anchorLogTree(root, "Missing AppBlock digit nodes", force = true)
            return null
        }

        val strokes = mutableListOf<AnchorTapStroke>()
        pin.forEachIndexed { index, digit ->
            val target = digitTargets[digit]
            val stroke = target?.let { anchorTapFromNode(it.node, "digit $digit") }
            if (stroke == null) {
                Log.w(TAG_ANCHOR, "Aborting unlock. Digit $digit has no usable bounds.")
                anchorLogTree(root, "Digit node without usable bounds", force = true)
                return null
            }
            strokes += stroke.copy(startOffsetMs = index * DIGIT_SPACING_MS)
        }

        val confirmNode = anchorFindConfirmNode(root, digitTargets)
        if (confirmNode != null) {
            val confirmStart = ((pin.length - 1) * DIGIT_SPACING_MS) +
                TAP_DURATION_MS + CONFIRM_DELAY_AFTER_DIGITS_MS
            val confirmStroke = anchorTapFromNode(confirmNode, "confirm button")?.copy(startOffsetMs = confirmStart)
            if (confirmStroke != null) strokes += confirmStroke
        } else {
            Log.d(TAG_ANCHOR, "No confirm button exposed; assuming AppBlock auto-submits after PIN length.")
        }

        return strokes
    }

    private fun anchorDispatchTapSequence(strokes: List<AnchorTapStroke>, reason: String): Boolean {
        if (strokes.isEmpty()) return false
        Log.d(TAG_ANCHOR, "Dispatching $reason as ${strokes.size} sequential tap gestures.")
        return anchorDispatchNextTap(strokes, reason, index = 0)
    }

    private fun anchorDispatchNextTap(strokes: List<AnchorTapStroke>, reason: String, index: Int): Boolean {
        val stroke = strokes.getOrNull(index) ?: run {
            anchorMarkCompleted("$reason sequential gesture completed")
            return true
        }
        val stepReason = "$reason step ${index + 1}/${strokes.size}: ${stroke.label}"
        return anchorDispatchSingleTap(stroke.copy(startOffsetMs = 0L), stepReason) { completed ->
            if (!completed) {
                hasTyped = false
                isTyping = false
                Log.w(TAG_ANCHOR, "$stepReason failed; PIN remains armed for retry.")
                return@anchorDispatchSingleTap
            }
            if (index >= strokes.lastIndex) {
                anchorMarkCompleted("$reason sequential gesture completed")
            } else {
                val nextDelay = if (stroke.label.contains("confirm", ignoreCase = true))
                    CONFIRM_DELAY_AFTER_DIGITS_MS else DIGIT_SPACING_MS
                serviceScope.launch {
                    delay(nextDelay)
                    anchorDispatchNextTap(strokes, reason, index + 1)
                }
            }
        }
    }

    private fun anchorDispatchSingleTap(
        stroke: AnchorTapStroke,
        reason: String,
        onResult: (Boolean) -> Unit
    ): Boolean {
        val gesture = try {
            val builder = GestureDescription.Builder()
            val path = Path().apply {
                moveTo(stroke.x, stroke.y)
                lineTo(stroke.x, stroke.y)
            }
            builder.addStroke(
                GestureDescription.StrokeDescription(path, stroke.startOffsetMs, TAP_DURATION_MS)
            )
            builder.build()
        } catch (e: Exception) {
            Log.e(TAG_ANCHOR, "Could not build $reason gesture: ${e.message}")
            return false
        }

        Log.d(TAG_ANCHOR, "Dispatching $reason tap at ${stroke.x},${stroke.y}.")
        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    super.onCompleted(gestureDescription)
                    onResult(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription) {
                    super.onCancelled(gestureDescription)
                    onResult(false)
                }
            },
            null
        )
        if (!accepted) {
            Log.w(TAG_ANCHOR, "Android rejected the $reason gesture dispatch.")
            onResult(false)
        }
        return accepted
    }

    private fun anchorMarkCompleted(reason: String) {
        hasTyped = true
        isTyping = false
        anchorClearPendingPin()
        Log.d(TAG_ANCHOR, "Secure AppBlock unlock complete: $reason.")
    }

    private fun anchorLooksLikePinEntryScreen(root: AccessibilityNodeInfo, pin: String): Boolean {
        val hasEditTextPath = anchorFindStandardEditTextNode(root) != null && anchorHasCodeEntryHint(root)
        if (hasEditTextPath) return true
        val digitTargets = anchorFindDigitTargets(root)
        val hasNeededDigits = pin.toSet().all { digitTargets.containsKey(it) }
        val looksLikeKeypad = digitTargets.size >= 8
        return hasNeededDigits && (looksLikeKeypad || anchorHasCodeEntryHint(root))
    }

    private fun anchorFindStandardEditTextNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return anchorFindNode(root) { node ->
            node.isEditable || node.className?.toString() == "android.widget.EditText"
        }
    }

    private fun anchorFindDigitTargets(root: AccessibilityNodeInfo): Map<Char, AnchorDigitTarget> {
        val targets = mutableMapOf<Char, AnchorDigitTarget>()
        anchorTraverse(root) { node ->
            val digit = anchorMatchedDigit(node) ?: return@anchorTraverse
            val tapNode = anchorTappableSelfOrParent(node)
            val current = targets[digit]
            val candidate = AnchorDigitTarget(tapNode, anchorScoreDigitTarget(node, tapNode))
            if (current == null || candidate.score > current.score) targets[digit] = candidate
        }
        return targets
    }

    private fun anchorMatchedDigit(node: AccessibilityNodeInfo): Char? {
        val labels = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        for (label in labels) {
            val trimmed = label.trim()
            if (trimmed.length == 1 && trimmed[0].isDigit()) return trimmed[0]
            val digits = trimmed.mapNotNull { char ->
                val numeric = Character.getNumericValue(char)
                if (numeric in 0..9) numeric.digitToChar() else null
            }
            val lower = trimmed.lowercase()
            val keypadHint = lower.contains("digit") || lower.contains("number") ||
                lower.contains("key") || lower.contains("button")
            if (digits.size == 1 && keypadHint) return digits.first()
        }
        return null
    }

    private fun anchorScoreDigitTarget(
        labelNode: AccessibilityNodeInfo,
        tapNode: AccessibilityNodeInfo
    ): Int {
        var score = 0
        if (tapNode.isClickable) score += 8
        if (tapNode.isEnabled) score += 4
        if (tapNode.className?.toString()?.contains("Button", ignoreCase = true) == true) score += 4
        if (labelNode.className?.toString()?.contains("TextView", ignoreCase = true) == true) score += 1
        val rect = Rect()
        tapNode.getBoundsInScreen(rect)
        if (!rect.isEmpty) score += 2
        if (rect.width() in 40..500 && rect.height() in 40..500) score += 1
        return score
    }

    private fun anchorFindConfirmNode(
        root: AccessibilityNodeInfo,
        digitTargets: Map<Char, AnchorDigitTarget> = anchorFindDigitTargets(root)
    ): AccessibilityNodeInfo? {
        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = 0
        anchorTraverse(root) { node ->
            val labels = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
            val matchingLabel = labels.firstOrNull { anchorLabelMatchesConfirm(it) } ?: return@anchorTraverse
            val tapNode = anchorTappableSelfOrParent(node)
            val score = anchorScoreConfirmTarget(matchingLabel, node, tapNode)
            if (score > bestScore) { bestScore = score; bestNode = tapNode }
        }
        return bestNode.takeIf { bestScore >= 4 } ?: anchorFindFallbackConfirmNode(root, digitTargets)
    }

    private fun anchorScoreConfirmTarget(
        label: String,
        labelNode: AccessibilityNodeInfo,
        tapNode: AccessibilityNodeInfo
    ): Int {
        val lower = label.trim().lowercase()
        var score = 0
        if (tapNode.isClickable) score += 8
        if (tapNode.className?.toString()?.contains("Button", ignoreCase = true) == true) score += 4
        if (labelNode.className?.toString()?.contains("Button", ignoreCase = true) == true) score += 4
        if (lower in exactConfirmLabels) score += 3
        if (lower.contains("confirm") || lower.contains("submit") || lower.contains("verify")) score += 2
        val rect = Rect()
        tapNode.getBoundsInScreen(rect)
        if (!rect.isEmpty) score += 1
        return score
    }

    private fun anchorLabelMatchesConfirm(label: String): Boolean {
        val lower = label.trim().lowercase()
        if (lower.isBlank()) return false
        return lower in exactConfirmLabels ||
            lower.contains("confirm") || lower.contains("submit") ||
            lower.contains("verify") || lower.contains("check") ||
            lower.contains("next") || lower.contains("go")
    }

    private fun anchorFindFallbackConfirmNode(
        root: AccessibilityNodeInfo,
        digitTargets: Map<Char, AnchorDigitTarget>
    ): AccessibilityNodeInfo? {
        if (digitTargets.size < 8) return null
        val digitRects = digitTargets.values.mapNotNull { it.node.anchorBoundsOrNull() }
        if (digitRects.isEmpty()) return null
        val keypadLeft = digitRects.minOf { it.left }
        val keypadTop = digitRects.minOf { it.top }
        val keypadRight = digitRects.maxOf { it.right }
        val keypadBottom = digitRects.maxOf { it.bottom }
        val keypadCenterX = keypadLeft + ((keypadRight - keypadLeft) / 2f)
        val keypadCenterY = keypadTop + ((keypadBottom - keypadTop) / 2f)
        val digitBounds = digitRects.map { it.toShortString() }.toSet()
        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = 0
        anchorTraverse(root) { node ->
            val tapNode = anchorTappableSelfOrParent(node)
            val rect = tapNode.anchorBoundsOrNull() ?: return@anchorTraverse
            if (rect.toShortString() in digitBounds) return@anchorTraverse
            if (anchorMatchedDigit(node) != null || anchorMatchedDigit(tapNode) != null) return@anchorTraverse
            if (!tapNode.isClickable && tapNode.className?.toString()?.contains("Button", ignoreCase = true) != true) return@anchorTraverse
            if (anchorLabelLooksLikeDelete(node) || anchorLabelLooksLikeDelete(tapNode)) return@anchorTraverse
            val centerX = rect.left + rect.width() / 2f
            val centerY = rect.top + rect.height() / 2f
            var score = 0
            if (centerX >= keypadCenterX) score += 8
            if (centerY >= keypadCenterY) score += 8
            if (rect.bottom >= keypadBottom - rect.height()) score += 5
            if (tapNode.isClickable) score += 4
            if (tapNode.isEnabled) score += 3
            if (rect.left >= keypadLeft && rect.right <= keypadRight + rect.width()) score += 2
            if (score > bestScore) { bestScore = score; bestNode = tapNode }
        }
        return bestNode.takeIf { bestScore >= 14 }?.also {
            Log.d(TAG_ANCHOR, "Using lower-right keypad fallback as confirm button.")
        }
    }

    private fun anchorLabelLooksLikeDelete(node: AccessibilityNodeInfo): Boolean {
        val labels = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        return labels.any { label ->
            val lower = label.trim().lowercase()
            lower.contains("delete") || lower.contains("backspace") ||
                lower.contains("clear") || lower.contains("remove") || lower.contains("erase")
        }
    }

    private fun anchorHasCodeEntryHint(root: AccessibilityNodeInfo): Boolean {
        var found = false
        anchorTraverse(root) { node ->
            if (found) return@anchorTraverse
            val labels = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
            found = labels.any { label ->
                val lower = label.lowercase()
                lower.contains("enter code") || lower.contains("pin") ||
                    lower.contains("password") || lower.contains("unlock") || lower.contains("verify code")
            }
        }
        return found
    }

    private fun anchorTapFromNode(node: AccessibilityNodeInfo, label: String): AnchorTapStroke? {
        val rect = node.anchorBoundsOrNull() ?: return null
        return AnchorTapStroke(
            label = label,
            x = rect.left + rect.width() / 2f,
            y = rect.top + rect.height() / 2f,
            startOffsetMs = 0L
        )
    }

    private fun AccessibilityNodeInfo.anchorBoundsOrNull(): Rect? {
        val rect = Rect()
        getBoundsInScreen(rect)
        return rect.takeIf { !it.isEmpty && it.width() > 0 && it.height() > 0 }
    }

    private fun anchorTappableSelfOrParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current: AccessibilityNodeInfo? = node
        var fallback = node
        while (current != null) {
            val rect = Rect()
            current.getBoundsInScreen(rect)
            if (!rect.isEmpty) fallback = current
            if (current.isClickable && !rect.isEmpty) return current
            current = current.parent
        }
        return fallback
    }

    private fun anchorFindNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(root)) return root
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val found = anchorFindNode(child, predicate)
            if (found != null) return found
        }
        return null
    }

    private fun anchorTraverse(
        root: AccessibilityNodeInfo,
        visit: (AccessibilityNodeInfo) -> Unit
    ) {
        visit(root)
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            anchorTraverse(child, visit)
        }
    }

    private fun anchorLogTree(
        root: AccessibilityNodeInfo,
        reason: String,
        force: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        if (!force && now - lastAnchorTreeLogAtMs < TREE_LOG_THROTTLE_MS) return
        lastAnchorTreeLogAtMs = now
        val builder = StringBuilder()
        builder.appendLine("Accessibility tree dump: $reason")
        anchorAppendNode(root, depth = 0, builder = builder)
        builder.toString().chunked(LOG_CHUNK_SIZE).forEachIndexed { index, chunk ->
            Log.d(TAG_ANCHOR, "TREE[$index] $chunk")
        }
    }

    private fun anchorAppendNode(
        node: AccessibilityNodeInfo,
        depth: Int,
        builder: StringBuilder
    ) {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val indent = "  ".repeat(max(0, depth))
        builder.append(indent)
            .append("class=").append(node.className ?: "")
            .append(" text=").append(if (node.text == null) "\"\"" else "\"${node.text.toString().replace("\n", " ").take(80)}\"")
            .append(" desc=").append(if (node.contentDescription == null) "\"\"" else "\"${node.contentDescription.toString().replace("\n", " ").take(80)}\"")
            .append(" viewId=").append(node.viewIdResourceName ?: "")
            .append(" clickable=").append(node.isClickable)
            .append(" editable=").append(node.isEditable)
            .append(" enabled=").append(node.isEnabled)
            .append(" bounds=").append(rect.toShortString())
            .appendLine()
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            anchorAppendNode(child, depth + 1, builder)
        }
    }

    /** Internal data class for Anchor digit targets (replaces the deleted AnchorAccessibilityService inner class). */
    private data class AnchorDigitTarget(val node: AccessibilityNodeInfo, val score: Int)

    /** Internal data class for Anchor tap strokes. */
    private data class AnchorTapStroke(
        val label: String,
        val x: Float,
        val y: Float,
        val startOffsetMs: Long
    )

    // =========================================================================

    override fun onInterrupt() {}

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            currentShieldCache = null
            lastUsageFetchTime = 0L
            usageStatsCache = null
            lastUsageCacheTime = 0L
            systemAppCache.clear()
            try {
                ZenithDatabase.getDatabase(this).openHelper.writableDatabase.execSQL("PRAGMA shrink_memory")
            } catch (_: Exception) {}
            System.gc()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (isScreenReceiverRegistered) {
            try {
                unregisterReceiver(screenStateReceiver)
            } catch (_: Exception) {}
            isScreenReceiverRegistered = false
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isServiceRunning = false
        if (isScreenReceiverRegistered) {
            try {
                unregisterReceiver(screenStateReceiver)
            } catch (_: Exception) {}
            isScreenReceiverRegistered = false
        }
        try {
            overlayManager.hideOverlay()
            sessionUsageOverlayManager.destroyAllHUDs()
        } catch (_: Exception) {}
        serviceJob.cancel()
        
        // Stop the persistent Foreground Service as accessibility service is shut down
        DayGuardForegroundService.stop(applicationContext)

        // Sync widget & QS Tile state
        try {
            DayGuardWidgetProvider.updateAllWidgets(applicationContext)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.service.quicksettings.TileService.requestListeningState(
                    applicationContext,
                    android.content.ComponentName(applicationContext, DayGuardTileService::class.java)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
