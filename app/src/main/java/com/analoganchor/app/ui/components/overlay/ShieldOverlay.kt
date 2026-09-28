package com.analoganchor.app.ui.components.overlay

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.graphicsLayer
import com.analoganchor.app.data.local.entity.FocusType
import com.analoganchor.app.data.local.entity.ShieldEntity
import com.analoganchor.app.data.preferences.UserPreferences
import com.analoganchor.app.data.preferences.UserPreferencesRepository
import com.analoganchor.app.ui.components.ZenithButton
import com.analoganchor.app.ui.components.ZenithButtonSize
import com.analoganchor.app.ui.components.ZenithButtonType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShieldOverlay(
    packageName: String,
    appName: String,
    shield: ShieldEntity?,
    totalUsageToday: Long,
    totalGlobalUsageToday: Long,
    delayDurationSeconds: Int = 0,
    userPrefs: UserPreferences,
    skipWaterPause: Boolean = false,
    isLastChance: Boolean = false,
    onWaterPauseCompleted: () -> Unit = {},
    onAllowUse: (Int, Boolean) -> Unit,
    onCloseApp: () -> Unit,
    onGoalDismiss: () -> Unit = {},
    onReserveUnlock: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.analoganchor.app.AnalogAnchorApplication
    val shieldRepository = app.shieldRepository
    val scope = rememberCoroutineScope()

    val isEnglish = remember {
        context.getSharedPreferences("analog_anchor_prefs", android.content.Context.MODE_PRIVATE)
            .getBoolean("is_english", true)
    }

    val localizedContext = remember(isEnglish) {
        if (!isEnglish) {
            val locale = java.util.Locale("ar")
            java.util.Locale.setDefault(locale)
            val config = android.content.res.Configuration(context.resources.configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
            context.createConfigurationContext(config)
        } else {
            val locale = java.util.Locale("en")
            java.util.Locale.setDefault(locale)
            val config = android.content.res.Configuration(context.resources.configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
            context.createConfigurationContext(config)
        }
    }


        var appIconBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    DisposableEffect(packageName) {
        val job = scope.launch(Dispatchers.IO) {
            val bmp = try {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(120, 120).asImageBitmap()
            } catch (_: Exception) { null }
            withContext(Dispatchers.Main) { appIconBitmap = bmp }
        }
        onDispose {
            job.cancel()
            appIconBitmap = null
        }
    }

    val combinedStateState = produceState<Triple<ShieldEntity?, Long, Long>>(
        initialValue = Triple(shield, totalUsageToday, totalGlobalUsageToday),
        packageName,
        shield
    ) {
        val usm = context.getSystemService(android.content.Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        
        while (true) {
            val now = System.currentTimeMillis()
            val calendar = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis
            val timeSinceMidnight = (now - startOfDay).coerceAtLeast(0L)

            val detailedUsage = withContext(Dispatchers.IO) {
                com.analoganchor.app.util.ScreenUsageHelper.fetchDetailedUsageToday(usm)
            }
            
            val s = shieldRepository.getShieldByPackageName(packageName)
            val liveAppUsage = detailedUsage.appUsageMap[packageName] ?: 0L

            value = Triple(
                s ?: shield, 
                liveAppUsage.coerceAtMost(timeSinceMidnight), 
                totalGlobalUsageToday.coerceAtMost(timeSinceMidnight)
            )
            delay(10000)
        }
    }

    val currentShield = combinedStateState.value.first
    val currentTotalUsageToday = combinedStateState.value.second
    val currentTotalGlobalUsageToday = combinedStateState.value.third
    
    var showContent by remember(packageName, skipWaterPause) { mutableStateOf(skipWaterPause) }
    var isEmergencyUnlocked by remember { mutableStateOf(false) }

    val initialWaterPause = userPrefs.waterPauseEnabled && !skipWaterPause
    var isWaterPauseActive by remember(packageName, initialWaterPause) { mutableStateOf(initialWaterPause) }
    var isWaterPauseCompleted by remember(packageName, initialWaterPause) { mutableStateOf(!initialWaterPause) }
    var hasNotifiedWaterPauseCompleted by remember(packageName) { mutableStateOf(false) }

    var waterPauseProgress by remember(packageName) { mutableStateOf(0f) }
    var secondsLeft by remember(packageName) { mutableStateOf(5) }
    LaunchedEffect(isWaterPauseActive) {
        if (isWaterPauseActive) {
            val totalDurationMs = 5000L
            val startTime = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= totalDurationMs) {
                    waterPauseProgress = 0f
                    secondsLeft = 0
                    break
                }
                waterPauseProgress = if (elapsed < 2500L) {
                    elapsed.toFloat() / 2500f
                } else {
                    1f - (elapsed - 2500L).toFloat() / 2500f
                }
                secondsLeft = (((totalDurationMs - elapsed) + 999) / 1000).toInt().coerceIn(1, 5)
                delay(16)
            }
            isWaterPauseActive = false
            isWaterPauseCompleted = true
        }
    }

    val isDelayEnabled = currentShield != null && currentShield.isDelayAppEnabled && currentShield.type == FocusType.SHIELD
    
    val initialProgress = remember(packageName, delayDurationSeconds) {
        if (isDelayEnabled && currentShield != null && currentShield.lastDelayStartTimestamp > 0 && delayDurationSeconds > 0) {
            val elapsed = System.currentTimeMillis() - currentShield.lastDelayStartTimestamp
            (elapsed.toFloat() / (delayDurationSeconds * 1000f)).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    val delayProgressAnimatable = remember(packageName) { Animatable(initialProgress) }
    var isDelaying by remember(packageName) { 
        mutableStateOf(false)
    }

    val motivationalMessages = remember(context) {
        context.resources.getStringArray(com.analoganchor.app.R.array.motivational_messages).toList()
    }
    val randomMessage = remember(isDelaying) {
        if (isDelaying) motivationalMessages.random() else ""
    }

    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showContent) 0.6f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "backgroundAlpha"
    )

    LaunchedEffect(packageName, isWaterPauseCompleted, skipWaterPause) {
        if (isWaterPauseCompleted) {
            if (initialWaterPause) {
                if (!hasNotifiedWaterPauseCompleted) {
                    hasNotifiedWaterPauseCompleted = true
                    onWaterPauseCompleted()
                }
            } else {
                showContent = true
            }
        }
    }

    LaunchedEffect(packageName, isWaterPauseCompleted, skipWaterPause) {
        // After the 25s grace period (skipWaterPause=true), go straight to duration selection
        if (isWaterPauseCompleted && isDelayEnabled && delayDurationSeconds > 0 && !skipWaterPause) {
            val startProgress = if (initialWaterPause) 0f else initialProgress
            if (startProgress < 1f) {
                isDelaying = true
                delayProgressAnimatable.snapTo(startProgress)
                val remainingProgress = 1f - startProgress
                val remainingDuration = (remainingProgress * delayDurationSeconds * 1000).toInt()
                
                if (remainingDuration > 0) {
                    delayProgressAnimatable.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = remainingDuration,
                            easing = LinearEasing
                        )
                    )
                }
                isDelaying = false
            } else {
                isDelaying = false
            }
        } else {
            isDelaying = false
        }
    }

    val isPeriodExpired = remember(currentShield) {
        currentShield != null && (System.currentTimeMillis() - currentShield.lastPeriodResetTimestamp > currentShield.refreshPeriodMinutes * 60 * 1000L)
    }
    
    val currentUses = remember(currentShield, isPeriodExpired) {
        if (isPeriodExpired) 0 else (currentShield?.currentPeriodUses ?: 0)
    }
    val maxUses = currentShield?.maxUsesPerPeriod ?: 5
    val isUsesExceeded = remember(currentUses, maxUses) { currentUses >= maxUses }
    val isTimeLimitReached = remember(currentTotalUsageToday, currentShield) {
        currentShield != null && currentShield.timeLimitMinutes > 0 && currentTotalUsageToday >= (currentShield.timeLimitMinutes * 60 * 1000L)
    }

    val remainingMinutes = remember(currentTotalUsageToday, currentShield) {
        currentShield?.let {
            if (it.timeLimitMinutes <= 0) return@let null
            val limitMillis = it.timeLimitMinutes * 60 * 1000L
            ((limitMillis - currentTotalUsageToday) / (60 * 1000L)).toInt().coerceAtLeast(0)
        }
    }

    val isBlocked by remember(isUsesExceeded, isTimeLimitReached, remainingMinutes, isEmergencyUnlocked, currentShield?.type) {
        derivedStateOf {
            val effectivelyTimeReached = isTimeLimitReached || (remainingMinutes != null && remainingMinutes <= 0)
            (isUsesExceeded || effectivelyTimeReached) && !isEmergencyUnlocked && currentShield?.type == FocusType.SHIELD
        }
    }

    val autoKickProgress = remember(packageName) { Animatable(0f) }
    var isEmergencyHolding by remember(packageName) { mutableStateOf(false) }

    data class ShieldOverlayState(
        val isBlocked: Boolean,
        val isEmergencyHolding: Boolean,
        val isDelaying: Boolean,
        val isEmergencyUnlocked: Boolean,
        val isWaterPauseActive: Boolean,
        val showContent: Boolean
    )

    LaunchedEffect(showContent) {
        if (!showContent) {
            autoKickProgress.stop()
            delayProgressAnimatable.stop()
        }
    }

    LaunchedEffect(packageName) {
        snapshotFlow { 
            ShieldOverlayState(isBlocked, isEmergencyHolding, isDelaying, isEmergencyUnlocked, isWaterPauseActive, showContent)
        }
            .collectLatest { state ->
                if (state.isWaterPauseActive) {
                    autoKickProgress.stop()
                    autoKickProgress.snapTo(0f)
                } else if (state.isBlocked) {
                    if (!state.isEmergencyHolding) {
                        autoKickProgress.snapTo(0f)
                        autoKickProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 4000, easing = LinearEasing)
                        )
                        if (showContent) {
                            showContent = false
                            delay(300)
                        }
                        onCloseApp()
                    } else {
                        autoKickProgress.stop()
                        autoKickProgress.snapTo(0f)
                    }
                } else if (state.showContent && !state.isDelaying && !state.isBlocked) {
                    // Mindful Pause active: stays visible calmly waiting for user choice.
                    autoKickProgress.stop()
                    autoKickProgress.snapTo(0f)
                } else {
                    autoKickProgress.stop()
                    autoKickProgress.snapTo(0f)
                }
            }
    }
    
    val refreshTimeLeftMillis = remember(currentShield) {
        if (currentShield != null) {
            val nextRefresh = currentShield.lastPeriodResetTimestamp + (currentShield.refreshPeriodMinutes * 60 * 1000L)
            (nextRefresh - System.currentTimeMillis()).coerceAtLeast(0L)
        } else 0L
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val currentOnAllowUse by rememberUpdatedState(onAllowUse)
    val currentOnCloseApp by rememberUpdatedState(onCloseApp)
    val currentOnGoalDismiss by rememberUpdatedState(onGoalDismiss)

    val layoutDirection = if (isEnglish) androidx.compose.ui.unit.LayoutDirection.Ltr else androidx.compose.ui.unit.LayoutDirection.Rtl

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        androidx.compose.ui.platform.LocalLayoutDirection provides layoutDirection
    ) {
    if (isWaterPauseActive) {
        WaterPauseScreen(
            progress = waterPauseProgress,
            secondsLeft = secondsLeft,
            onCloseApp = currentOnCloseApp
        )
    } else if (currentShield?.type == FocusType.GOAL) {
        GoalOverlay(
            packageName = packageName,
            appName = appName,
            shield = currentShield,
            totalUsageToday = currentTotalUsageToday,
            totalGlobalUsageToday = currentTotalGlobalUsageToday,
            userPrefs = userPrefs,
            onGoalDismiss = {
                scope.launch {
                    showContent = false
                    delay(400)
                    currentOnGoalDismiss()
                }
            }
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (showContent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = backgroundAlpha))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                // Strictly consume background taps while dialog is visibly open
                            }
                        }
                )
            }

            AnimatedVisibility(
                visible = showContent,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
                ) + fadeOut(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .let {
                            if (isLandscape) it.widthIn(max = 640.dp).wrapContentHeight()
                            else it.fillMaxWidth().wrapContentHeight()
                        }
                        .align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding(),
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        if (isLandscape) {
                            LandscapeInterceptLayout(
                                modifier = Modifier.displayCutoutPadding(),
                                appName = appName,
                                appIcon = appIconBitmap,
                                shield = currentShield,
                                totalUsageToday = currentTotalUsageToday,
                                totalGlobalUsageToday = currentTotalGlobalUsageToday,
                                userPrefs = userPrefs,
                                remainingMinutes = remainingMinutes,
                                isEmergencyUnlocked = isEmergencyUnlocked,
                                isDelaying = isDelaying,
                                randomMessage = randomMessage,
                                delayProgressAnimatable = delayProgressAnimatable,
                                delayDurationSeconds = delayDurationSeconds,
                                isUsesExceeded = isUsesExceeded,
                                isTimeLimitReached = isTimeLimitReached,
                                refreshTimeLeftMillis = refreshTimeLeftMillis,
                                currentUses = currentUses,
                                maxUses = maxUses,
                                autoKickProgress = { autoKickProgress.value },
                                isLastChance = isLastChance,
                                onEmergencyHoldingChange = { isEmergencyHolding = it },
                                onEmergencyClick = { isEmergencyUnlocked = true },
                                onAllowUse = { minutes ->
                                    scope.launch {
                                        showContent = false
                                        delay(400)
                                        currentOnAllowUse(minutes, isEmergencyUnlocked)
                                    }
                                },
                                onCloseApp = {
                                    scope.launch {
                                        showContent = false
                                        delay(400)
                                        currentOnCloseApp()
                                    }
                                }
                            )
                        } else {
                            PortraitInterceptLayout(
                                appName = appName,
                                appIcon = appIconBitmap,
                                shield = currentShield,
                                totalUsageToday = currentTotalUsageToday,
                                totalGlobalUsageToday = currentTotalGlobalUsageToday,
                                userPrefs = userPrefs,
                                remainingMinutes = remainingMinutes,
                                isEmergencyUnlocked = isEmergencyUnlocked,
                                isDelaying = isDelaying,
                                randomMessage = randomMessage,
                                delayProgressAnimatable = delayProgressAnimatable,
                                delayDurationSeconds = delayDurationSeconds,
                                isUsesExceeded = isUsesExceeded,
                                isTimeLimitReached = isTimeLimitReached,
                                refreshTimeLeftMillis = refreshTimeLeftMillis,
                                currentUses = currentUses,
                                maxUses = maxUses,
                                autoKickProgress = { autoKickProgress.value },
                                isLastChance = isLastChance,
                                onEmergencyHoldingChange = { isEmergencyHolding = it },
                                onEmergencyClick = { isEmergencyUnlocked = true },
                                onAllowUse = { minutes ->
                                    scope.launch {
                                        showContent = false
                                        delay(400)
                                        currentOnAllowUse(minutes, isEmergencyUnlocked)
                                    }
                                },
                                onCloseApp = {
                                    scope.launch {
                                        showContent = false
                                        delay(400)
                                        currentOnCloseApp()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PortraitInterceptLayout(
    appName: String,
    appIcon: androidx.compose.ui.graphics.ImageBitmap?,
    shield: ShieldEntity?,
    totalUsageToday: Long,
    totalGlobalUsageToday: Long,
    userPrefs: UserPreferences,
    remainingMinutes: Int?,
    isEmergencyUnlocked: Boolean,
    isDelaying: Boolean,
    randomMessage: String,
    delayProgressAnimatable: Animatable<Float, AnimationVector1D>,
    delayDurationSeconds: Int,
    isUsesExceeded: Boolean,
    isTimeLimitReached: Boolean,
    refreshTimeLeftMillis: Long,
    currentUses: Int,
    maxUses: Int,
    autoKickProgress: () -> Float,
    isLastChance: Boolean = false,
    onEmergencyClick: () -> Unit,
    onEmergencyHoldingChange: (Boolean) -> Unit = {},
    onAllowUse: (Int) -> Unit,
    onCloseApp: () -> Unit,
    onReserveUnlock: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OverlayDragHandleWithIndicators(
            currentUses = if (shield?.type == FocusType.SHIELD) currentUses else null,
            maxUses = if (shield?.type == FocusType.SHIELD) maxUses else null,
            emergencyCount = if (shield?.type == FocusType.SHIELD) shield.emergencyUseCount else null
        )

        Column(
            modifier = Modifier
                .padding(bottom = 24.dp, start = 24.dp, end = 24.dp, top = 24.dp)
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Outlined.Block,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.mindful_pause),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = appName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (shield != null) androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.day_guard_active) else androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.mindful_gateway_guarding),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            ShieldSection(
                shield = shield,
                remainingMinutes = remainingMinutes,
                totalUsageToday = totalUsageToday,
                totalGlobalUsageToday = totalGlobalUsageToday,
                userPrefs = userPrefs,
                isEmergencyUnlocked = isEmergencyUnlocked,
                isDelaying = isDelaying,
                randomMessage = randomMessage,
                delayProgressAnimatable = delayProgressAnimatable,
                delayDurationSeconds = delayDurationSeconds,
                isUsesExceeded = isUsesExceeded,
                isTimeLimitReached = isTimeLimitReached,
                refreshTimeLeftMillis = refreshTimeLeftMillis,
                autoKickProgress = autoKickProgress,
                isLastChance = isLastChance,
                onEmergencyClick = onEmergencyClick,
                onEmergencyHoldingChange = onEmergencyHoldingChange,
                onAllowUse = onAllowUse,
                onCloseApp = onCloseApp,
                onReserveUnlock = onReserveUnlock
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LandscapeInterceptLayout(
    modifier: Modifier = Modifier,
    appName: String,
    appIcon: androidx.compose.ui.graphics.ImageBitmap?,
    shield: ShieldEntity?,
    totalUsageToday: Long,
    totalGlobalUsageToday: Long,
    userPrefs: UserPreferences,
    remainingMinutes: Int?,
    isEmergencyUnlocked: Boolean,
    isDelaying: Boolean,
    randomMessage: String,
    delayProgressAnimatable: Animatable<Float, AnimationVector1D>,
    delayDurationSeconds: Int,
    isUsesExceeded: Boolean,
    isTimeLimitReached: Boolean,
    refreshTimeLeftMillis: Long,
    currentUses: Int,
    maxUses: Int,
    autoKickProgress: () -> Float,
    isLastChance: Boolean = false,
    onEmergencyClick: () -> Unit,
    onEmergencyHoldingChange: (Boolean) -> Unit = {},
    onAllowUse: (Int) -> Unit,
    onCloseApp: () -> Unit,
    onReserveUnlock: () -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(bottom = 24.dp, start = 24.dp, end = 24.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.mindful_pause),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (shield != null) androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.day_guard_active) else androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.mindful_gateway_guarding),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

            }

            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ShieldLandscapeContent(
                    shield = shield,
                    remainingMinutes = remainingMinutes,
                    totalUsageToday = totalUsageToday,
                    isEmergencyUnlocked = isEmergencyUnlocked,
                    isDelaying = isDelaying,
                    randomMessage = randomMessage,
                    delayProgressAnimatable = delayProgressAnimatable,
                    delayDurationSeconds = delayDurationSeconds,
                    isUsesExceeded = isUsesExceeded,
                    isTimeLimitReached = isTimeLimitReached,
                    refreshTimeLeftMillis = refreshTimeLeftMillis,
                    autoKickProgress = autoKickProgress,
                    isLastChance = isLastChance,
                    onEmergencyClick = onEmergencyClick,
                    onEmergencyHoldingChange = onEmergencyHoldingChange,
                    onAllowUse = onAllowUse,
                    onCloseApp = onCloseApp,
                    onReserveUnlock = onReserveUnlock
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShieldSection(
    shield: ShieldEntity?,
    remainingMinutes: Int?,
    totalUsageToday: Long,
    totalGlobalUsageToday: Long,
    userPrefs: UserPreferences,
    isEmergencyUnlocked: Boolean,
    isDelaying: Boolean,
    randomMessage: String,
    delayProgressAnimatable: Animatable<Float, AnimationVector1D>,
    delayDurationSeconds: Int,
    isUsesExceeded: Boolean,
    isTimeLimitReached: Boolean,
    refreshTimeLeftMillis: Long,
    autoKickProgress: () -> Float,
    isLastChance: Boolean = false,
    onEmergencyClick: () -> Unit,
    onEmergencyHoldingChange: (Boolean) -> Unit = {},
    onAllowUse: (Int) -> Unit,
    onCloseApp: () -> Unit,
    onReserveUnlock: () -> Unit = {}
) {
    val currentRemainingMinutes = remember(shield, totalUsageToday, remainingMinutes) {
        shield?.let {
            if (it.timeLimitMinutes <= 0) return@let null
            val totalLimitMillis = it.timeLimitMinutes * 60 * 1000L
            val remainingMillis = (totalLimitMillis - totalUsageToday).coerceAtLeast(0L)
            (remainingMillis / 60000).toInt()
        } ?: remainingMinutes
    }

    val effectivelyTimeReached = isTimeLimitReached || (currentRemainingMinutes != null && currentRemainingMinutes <= 0)
    val isBlocked = (isUsesExceeded || effectivelyTimeReached) && !isEmergencyUnlocked

    Spacer(modifier = Modifier.height(16.dp))
    val totalLimitMillis = shield?.let { it.timeLimitMinutes * 60 * 1000L } ?: 0L
    val remainingMillis = if (totalLimitMillis > 0) (totalLimitMillis - totalUsageToday).coerceAtLeast(0L) else 0L
    val progress = if (totalLimitMillis > 0) remainingMillis.toFloat() / totalLimitMillis else 0f



    if (isBlocked) {
        LimitReachedSection(
            isUsesExceeded = isUsesExceeded,
            isTimeLimitReached = effectivelyTimeReached,
            refreshTimeLeftMillis = refreshTimeLeftMillis,
            shield = shield,
            onEmergencyClick = onEmergencyClick,
            onEmergencyHoldingChange = onEmergencyHoldingChange,
            onReserveUnlock = onReserveUnlock
        )
    } else {
        val minutesToDisplay = if (isEmergencyUnlocked) null else currentRemainingMinutes
        Spacer(modifier = Modifier.height(24.dp))

        AnimatedContent(
            targetState = isDelaying,
            transitionSpec = {
                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                 scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)))
                    .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)))
            },
            label = "delayContent"
        ) { delaying ->
            if (delaying) {
                DelayInProgressSection(randomMessage, delayProgressAnimatable, delayDurationSeconds)
            } else {
                DurationSelectionSection(minutesToDisplay, isEmergencyUnlocked, isLastChance, onAllowUse)
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    CloseAppTextButton(onCloseApp, autoKickProgress, size = ZenithButtonSize.ExtraLarge)
}




@Composable
fun ShieldLandscapeContent(
    shield: ShieldEntity?,
    remainingMinutes: Int?,
    totalUsageToday: Long = 0,
    isEmergencyUnlocked: Boolean,
    isDelaying: Boolean,
    randomMessage: String,
    delayProgressAnimatable: Animatable<Float, AnimationVector1D>,
    delayDurationSeconds: Int,
    isUsesExceeded: Boolean,
    isTimeLimitReached: Boolean,
    refreshTimeLeftMillis: Long,
    autoKickProgress: () -> Float,
    isLastChance: Boolean = false,
    onEmergencyClick: () -> Unit,
    onEmergencyHoldingChange: (Boolean) -> Unit = {},
    onAllowUse: (Int) -> Unit,
    onCloseApp: () -> Unit,
    onReserveUnlock: () -> Unit = {}
) {
    val displayRemainingMinutes = if (shield != null && shield.timeLimitMinutes > 0) {
        val totalLimitMillis = shield.timeLimitMinutes * 60 * 1000L
        ((totalLimitMillis - totalUsageToday).coerceAtLeast(0L) / 60000).toInt()
    } else remainingMinutes

    if ((isUsesExceeded || isTimeLimitReached) && !isEmergencyUnlocked) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))
            LimitReachedContent(isUsesExceeded, isTimeLimitReached, refreshTimeLeftMillis)
            if (shield != null && shield.emergencyUseCount > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                EmergencyButton(onEmergencyUse = onEmergencyClick, onHoldingChange = onEmergencyHoldingChange)
            }
            Spacer(modifier = Modifier.height(8.dp))
            ZenithButton(
                onClick = onReserveUnlock,
                text = "Reserve Unlock (200 Pts)",
                type = ZenithButtonType.Outlined,
                size = ZenithButtonSize.Large
            )
            Spacer(modifier = Modifier.weight(1f))
            CloseAppTextButton(onCloseApp, autoKickProgress, size = ZenithButtonSize.Large)
        }
    } else {
        if (isDelaying) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))
                DelayInProgressSection(randomMessage, delayProgressAnimatable, delayDurationSeconds)
                Spacer(modifier = Modifier.weight(1f))
                CloseAppTextButton(onCloseApp, autoKickProgress, size = ZenithButtonSize.Large)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))
                if (isLastChance) {
                    LastChanceWarningBanner()
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(
                    text = if (isEmergencyUnlocked) androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.emergency_use_select_duration) else androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.how_long_to_use),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                DurationButtonsGrid(if (isEmergencyUnlocked) null else displayRemainingMinutes, onAllowUse)
                Spacer(modifier = Modifier.weight(1f))
                CloseAppTextButton(onCloseApp, autoKickProgress, size = ZenithButtonSize.Large)
            }
        }
    }
}

@Composable
fun LimitReachedSection(
    isUsesExceeded: Boolean,
    isTimeLimitReached: Boolean,
    refreshTimeLeftMillis: Long,
    shield: ShieldEntity?,
    onEmergencyClick: () -> Unit,
    onEmergencyHoldingChange: (Boolean) -> Unit = {},
    onReserveUnlock: () -> Unit = {}
) {
    Spacer(modifier = Modifier.height(24.dp))
    LimitReachedContent(isUsesExceeded, isTimeLimitReached, refreshTimeLeftMillis)
    if (shield != null && shield.emergencyUseCount > 0) {
        Spacer(modifier = Modifier.height(16.dp))
        EmergencyButton(onEmergencyUse = onEmergencyClick, onHoldingChange = onEmergencyHoldingChange)
    }
    Spacer(modifier = Modifier.height(8.dp))
    ZenithButton(
        onClick = onReserveUnlock,
        text = "Reserve Unlock (200 Pts)",
        type = ZenithButtonType.Outlined,
        size = ZenithButtonSize.Large
    )
}

@Composable
fun LimitReachedContent(
    isUsesExceeded: Boolean,
    isTimeLimitReached: Boolean,
    refreshTimeLeftMillis: Long
) {
    if (isUsesExceeded && !isTimeLimitReached) {
        var countdownText by remember { mutableStateOf(formatCountdown(refreshTimeLeftMillis)) }
        LaunchedEffect(refreshTimeLeftMillis) {
            var current = refreshTimeLeftMillis
            while (current > 0) {
                delay(1000)
                current -= 1000
                countdownText = formatCountdown(current)
            }
        }

        Text(
            text = androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.uses_limit_reached, countdownText),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    } else {
        Text(
            text = androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.daily_limit_reached),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DelayInProgressSection(
    randomMessage: String,
    delayProgressAnimatable: Animatable<Float, AnimationVector1D>,
    delayDurationSeconds: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = randomMessage,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Box(contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { delayProgressAnimatable.value },
                modifier = Modifier.size(100.dp),
                color = MaterialTheme.colorScheme.tertiary,
                amplitude = { 1f },
                wavelength = 30.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            val secondsLeft by remember(delayDurationSeconds) {
                derivedStateOf { kotlin.math.ceil((1f - delayProgressAnimatable.value) * delayDurationSeconds).toInt() }
            }
            Text(
                text = "${secondsLeft}s",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
fun DurationSelectionSection(remainingMinutes: Int?, isEmergencyUnlocked: Boolean, isLastChance: Boolean = false, onAllowUse: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLastChance) {
            LastChanceWarningBanner()
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
            text = if (isEmergencyUnlocked) androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.emergency_use_select_duration) else androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.how_long_to_use),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        DurationButtonsGrid(if (isEmergencyUnlocked) null else remainingMinutes, onAllowUse)
    }
}

@Composable
fun WaterPauseScreen(
    progress: Float,
    secondsLeft: Int,
    onCloseApp: () -> Unit,
    onReserveUnlock: () -> Unit = {}
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)), // Deep slate / dark blue
        contentAlignment = Alignment.Center
    ) {
        // Liquid progress filling from the bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(progress)
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF3B82F6).copy(alpha = 0.3f),
                            Color(0xFF1D4ED8).copy(alpha = 0.5f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFF60A5FA)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.take_a_deep_breath),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Light,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "$secondsLeft",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF60A5FA),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF3B82F6),
                    trackColor = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        try {
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                            }
                            context.startActivity(homeIntent)
                        } catch (_: Exception) {}
                        onCloseApp()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.close_app),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun LastChanceWarningBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.errorColorOrCustom(),
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.last_chance_warning),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun MaterialTheme.errorColorOrCustom(): Color =
    this.colorScheme.error


