package com.analoganchor.app.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.analoganchor.app.data.RuleFlag
import com.analoganchor.app.data.SessionLog
import com.analoganchor.app.data.UnlockSession
import com.analoganchor.app.engine.DailyRuleEngineRunner
import com.analoganchor.app.ui.components.GlassCard
import com.analoganchor.app.ui.components.PremiumButton
import com.analoganchor.app.ui.theme.DeepPanel
import com.analoganchor.app.ui.theme.GlowGreen
import com.analoganchor.app.ui.theme.MutedSilver
import com.analoganchor.app.ui.theme.ObsidianBg
import com.analoganchor.app.ui.theme.ObsidianBorder
import com.analoganchor.app.ui.theme.ObsidianSurface
import com.analoganchor.app.ui.theme.Outfit
import com.analoganchor.app.ui.theme.RefinedGreen
import com.analoganchor.app.ui.theme.SignalAmber
import com.analoganchor.app.ui.theme.SignalRed
import com.analoganchor.app.ui.theme.SignalRedMuted
import com.analoganchor.app.ui.theme.WarmOffWhite
import com.analoganchor.app.ui.theme.WarmSilverText
import com.analoganchor.app.util.AppBlockIntegration
import com.analoganchor.app.util.ProofFileStore
import com.analoganchor.app.util.ReviewCardData
import com.analoganchor.app.util.ReviewCardRenderer
import com.analoganchor.app.viewmodel.AnchorViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccountabilityDashboardScreen(
    viewModel: AnchorViewModel,
    isEnglish: Boolean = true,
    onNavigateToReturn: () -> Unit,
    onNavigateToRecovery: () -> Unit
) {
    val context = LocalContext.current
    var usageAccessGranted by remember { mutableStateOf(DailyRuleEngineRunner.hasUsageAccess(context)) }
    val streak by viewModel.anchorStreak.collectAsState()
    val pendingReturn by viewModel.pendingReturnSession.collectAsState()
    val recovery by viewModel.openRecoveryRecord.collectAsState()
    val flags by viewModel.activeRuleFlagCount.collectAsState()
    val activeFlags by viewModel.activeRuleFlags.collectAsState()

    // Real state flows from viewmodel
    val reservePoints by viewModel.reservePoints.collectAsState()
    val hasRecentPenalty by viewModel.hasRecentPenalty.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val emergencyRequestsTotal by viewModel.emergencyUnlockRequestCount.collectAsState()
    val sessionLogs by viewModel.sessionLogs.collectAsState()
    val stepsCount by viewModel.stepsCount.collectAsState()
    val fajrVerified by viewModel.fajrVerified.collectAsState()
    val fajrWakeVerified by viewModel.fajrWakeVerified.collectAsState()
    val fajrWakeStepVerificationActive by viewModel.fajrWakeStepVerificationActive.collectAsState()
    val fajrWakeStepsAfterDismiss by viewModel.fajrWakeStepsAfterDismiss.collectAsState()
    val maghribVerified by viewModel.maghribVerified.collectAsState()
    val workoutMode by viewModel.workoutMode.collectAsState()
    val mosqueComplianceStreak by viewModel.mosqueComplianceStreak.collectAsState()
    val protocolComplianceStreak by viewModel.protocolComplianceStreak.collectAsState()
    val weeklyMosquePrayers by viewModel.weeklyMosqueCheckInCount.collectAsState()
    val maghribRetreatStreak by viewModel.maghribRetreatStreak.collectAsState()
    val covenantDayNumber by viewModel.covenantDayNumber.collectAsState()
    val covenantScore by viewModel.covenantScore.collectAsState()
    val covenantPercentage by viewModel.covenantPercentage.collectAsState()
    val covenantBonusClaimed by viewModel.covenantBonusClaimed.collectAsState()
    val habitScore by viewModel.habitScore.collectAsState()
    val recentTransactionType by viewModel.recentTransactionType.collectAsState()
    val stakeWeek by viewModel.stakeWeek.collectAsState()
    val cumulativeStakePercentage by viewModel.cumulativeStakePercentage.collectAsState()
    val todayPrayersCompleted by viewModel.todayPrayersCompleted.collectAsState()
    val outdoorSoloCountThisWeek by viewModel.outdoorSoloCountThisWeek.collectAsState()
    val outdoorPartnerCountThisWeek by viewModel.outdoorPartnerCountThisWeek.collectAsState()

    // Determine complete status of today's pillars
    val todayDateStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val isAnchorSessionDone = remember(sessionLogs, todayDateStr) {
        sessionLogs.any { log ->
            val logDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(log.timestamp))
            logDate == todayDateStr && log.status == "complete"
        }
    }

    // 2. Mosque: completed if prayer presence is verified. Legacy workout state remains a fallback.
    val stepGoal = if (workoutMode == "walk") 4000 else 2000
    val isMosqueRunwayDone = fajrVerified || maghribVerified || (stepsCount >= stepGoal)

    // 3. Wake Protocol: completed when post-dismissal movement is verified.
    val isSleepProtocolDone = fajrWakeVerified
    val wakeStepGoal = AnchorViewModel.FAJR_WAKE_STEP_GOAL
    val wakeProtocolDescription = when {
        isSleepProtocolDone -> "Wake movement verified after Fajr alarm (+25 pts)"
        fajrWakeStepVerificationActive -> "Carry the phone now: ${fajrWakeStepsAfterDismiss.coerceIn(0, wakeStepGoal)}/$wakeStepGoal real steps detected."
        else -> "Dismiss the Fajr alarm, then carry the phone for $wakeStepGoal real steps."
    }

    LaunchedEffect(Unit) {
        viewModel.refreshExpiredUnlockSessions()
        usageAccessGranted = DailyRuleEngineRunner.hasUsageAccess(context)
    }

    // Hidden emergency access button state
    var hiddenAccessClicks by remember { mutableStateOf(0) }
    var showHiddenButton by remember { mutableStateOf(false) }

    val isFemaleUser by viewModel.isFemaleUserFlow.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DashboardHeader()

        ReserveLedgerBalanceCard(
            reservePoints = reservePoints,
            isLocked = isLocked,
            protocolComplianceStreak = protocolComplianceStreak,
            mosqueComplianceStreak = mosqueComplianceStreak,
            retreatStreak = maghribRetreatStreak,
            covenantDayNumber = covenantDayNumber,
            covenantScore = covenantScore,
            covenantPercentage = covenantPercentage,
            covenantBonusClaimed = covenantBonusClaimed,
            onClaimCovenantBonus = { viewModel.claimCovenantMilestoneBonus() },
            weeklyMosquePrayers = weeklyMosquePrayers,
            habitScore = habitScore,
            isFemaleUser = isFemaleUser,
            hasRecentPenalty = hasRecentPenalty,
            recentTransactionType = recentTransactionType,
            stakeWeek = stakeWeek,
            cumulativeStakePercentage = cumulativeStakePercentage,
            todayPrayersCompleted = todayPrayersCompleted,
            outdoorSoloCountThisWeek = outdoorSoloCountThisWeek,
            outdoorPartnerCountThisWeek = outdoorPartnerCountThisWeek,
            isEnglish = isEnglish
        )

        ReserveLedgerHistorySection(sessionLogs = sessionLogs)

        BehaviorAlertsCard(
            flags = activeFlags,
            onDismiss = viewModel::dismissRuleFlag
        )

        // TODAY'S ANCHORS CARD
        GlassCard(borderColor = GlowGreen.copy(alpha = 0.45f)) {
            Text(
                text = "TODAY'S ANCHORS",
                color = GlowGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            
            // Pillar 1: Anchor Session
            PillarRow(
                title = "⚓ ANCHOR SESSION",
                desc = if (isAnchorSessionDone) "Session completed today (+25 pts)" else "Verify physical Anchor presence today",
                isCompleted = isAnchorSessionDone
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Pillar 2: Mosque
            PillarRow(
                title = "MOSQUE",
                desc = if (isMosqueRunwayDone) "Mosque presence verified" else "Perform mosque check-in",
                isCompleted = isMosqueRunwayDone
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Pillar 3: Wake Protocol
            PillarRow(
                title = "🌅 WAKE PROTOCOL",
                desc = wakeProtocolDescription,
                isCompleted = isSleepProtocolDone
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Reserve Points Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RESERVE BALANCE: $reservePoints PTS",
                    color = WarmOffWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Outfit
                )
                Text(
                    text = "${(reservePoints.coerceIn(0, 380) * 100 / 380)}%",
                    color = if (reservePoints >= 380) Color(0xFF00FF9D) else GlowGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            // Beautiful progress bar
            val progressPercent = (reservePoints.coerceIn(0, 380).toFloat() / 380f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(ObsidianBorder, RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressPercent)
                        .height(8.dp)
                        .background(if (reservePoints >= 380) Color(0xFF00FF9D) else GlowGreen, RoundedCornerShape(4.dp))
                )
            }
        }

        // WEEK CALENDAR STRIP
        GlassCard(borderColor = ObsidianBorder) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WEEK CALENDAR",
                    color = MutedSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "$streak-DAY STREAK 🔥",
                    color = SignalAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Outfit
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            
            // 7 Days list
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val daysOfWeek = listOf("M", "T", "W", "T", "F", "S", "S")
                daysOfWeek.forEachIndexed { index, day ->
                    val isDayActive = index < (streak % 7).coerceAtLeast(1)
                    val isToday = index == 4
                    
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = if (isDayActive) GlowGreen else ObsidianBg,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.2.dp,
                                color = if (isToday) GlowGreen else if (isDayActive) GlowGreen else ObsidianBorder,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = day,
                                color = if (isDayActive) ObsidianBg else MutedSilver,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            if (isDayActive) {
                                Text(
                                    text = "✓",
                                    color = ObsidianBg,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.offset(y = (-2).dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // STATS CARD
        GlassCard(borderColor = ObsidianBorder) {
            Text(
                text = "STATS & INSIGHTS",
                color = MutedSilver,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Emergency Requests", color = MutedSilver, fontSize = 11.sp, fontFamily = Outfit)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$emergencyRequestsTotal", color = if (emergencyRequestsTotal > 2) SignalAmber else WarmOffWhite, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = Outfit)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Streak Record", color = MutedSilver, fontSize = 11.sp, fontFamily = Outfit)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${streak + 5} days", color = GlowGreen, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = Outfit)
                }
            }
        }

        if (!usageAccessGranted) {
            GlassCard(borderColor = ObsidianBorder) {
                StatusTitle("Behavior context offline", MutedSilver)
                Text(
                    text = "Grant Usage Access only if you want Analog Anchor to add passive behavior context for the user.",
                    color = WarmSilverText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontFamily = Outfit
                )
                Spacer(modifier = Modifier.height(14.dp))
                PremiumButton(
                    text = "OPEN USAGE ACCESS",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    gradientColors = listOf(DeepPanel, ObsidianSurface),
                    contentColor = WarmOffWhite
                )
            }
        }

        StatusGrid(
            anchorDone = isAnchorSessionDone,
            mosqueDone = isMosqueRunwayDone,
            reservePoints = reservePoints,
            recoveryOpen = recovery != null
        )

        if (pendingReturn != null) {
            GlassCard(borderColor = SignalAmber) {
                StatusTitle("Return checkpoint waiting", SignalAmber)
                Text(
                    text = "Finish the re-entry checkpoint before ${formatTime(pendingReturn!!.returnDueAt)}.",
                    color = WarmSilverText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontFamily = Outfit
                )
                Spacer(modifier = Modifier.height(14.dp))
                PremiumButton(
                    text = "COMPLETE RETURN CHECKPOINT",
                    onClick = onNavigateToReturn,
                    gradientColors = listOf(SignalAmber, Color(0xFFE69A30))
                )
            }
        }

        if (recovery != null) {
            GlassCard(borderColor = SignalAmber) {
                StatusTitle("Recovery required", SignalAmber)
                Text(
                    text = recovery!!.reason,
                    color = WarmSilverText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontFamily = Outfit
                )
                Spacer(modifier = Modifier.height(14.dp))
                PremiumButton(
                    text = "COMPLETE RECOVERY CHECKPOINT",
                    onClick = onNavigateToRecovery,
                    gradientColors = listOf(SignalAmber, Color(0xFFE69A30))
                )
            }
        }

        if (flags > 0) {
            Text(
                text = "$flags behavior context flags are available for the private record.",
                color = MutedSilver,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // TRIPLE TAP HIDDEN PORTAL ACCESS LINK
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .background(ObsidianSurface, RoundedCornerShape(8.dp))
                    .border(BorderStroke(1.dp, ObsidianBorder), RoundedCornerShape(8.dp))
                    .clickable {
                        hiddenAccessClicks += 1
                        if (hiddenAccessClicks >= 3) {
                            showHiddenButton = true
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (hiddenAccessClicks > 0 && !showHiddenButton) "Need emergency access? (${3 - hiddenAccessClicks} more tap${if (3 - hiddenAccessClicks > 1) "s" else ""})" else "Need emergency access?",
                    color = if (hiddenAccessClicks > 0) SignalAmber else MutedSilver,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Outfit,
                    textAlign = TextAlign.Center
                )
            }

            androidx.compose.animation.AnimatedVisibility(visible = showHiddenButton) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PremiumButton(
                        text = "ACCESS EMERGENCY PORTAL",
                        onClick = {
                            hiddenAccessClicks = 0
                            showHiddenButton = false
                            viewModel.setShowDecoyShortcutScreen(true)
                        },
                        gradientColors = listOf(DeepPanel, ObsidianSurface),
                        contentColor = WarmOffWhite,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun ReserveLedgerBalanceCard(
    reservePoints: Int,
    isLocked: Boolean,
    protocolComplianceStreak: Int,
    mosqueComplianceStreak: Int,
    retreatStreak: Int = 0,
    covenantDayNumber: Int = 1,
    covenantScore: Int = 0,
    covenantPercentage: Int = 0,
    covenantBonusClaimed: Boolean = false,
    onClaimCovenantBonus: () -> Unit = {},
    weeklyMosquePrayers: Int = 0,
    habitScore: Int = 85,
    isFemaleUser: Boolean = false,
    hasRecentPenalty: Boolean = false,
    recentTransactionType: AnchorViewModel.LedgerTransactionType? = null,
    stakeWeek: Int = 1,
    cumulativeStakePercentage: Float = 1.2f,
    todayPrayersCompleted: List<Boolean> = listOf(false, false, false, false, false),
    outdoorSoloCountThisWeek: Int = 0,
    outdoorPartnerCountThisWeek: Int = 0,
    isEnglish: Boolean = true
) {
    val isDeficit = reservePoints < 0
    val isAllSevenDays = protocolComplianceStreak >= 7 && retreatStreak >= 7 && mosqueComplianceStreak >= 7
    val goldColor = Color(0xFFFFD700)
    val emeraldGreen = Color(0xFF00FF9D)
    val cyanColor = Color(0xFF7DEEFF)
    val isPenaltyActive = isDeficit || hasRecentPenalty || recentTransactionType == AnchorViewModel.LedgerTransactionType.PENALTY
    val isEarnActive = !isPenaltyActive && recentTransactionType == AnchorViewModel.LedgerTransactionType.EARN
    val isTargetMet = reservePoints >= 380

    val pointsColor = when {
        isPenaltyActive -> SignalRed
        isEarnActive -> emeraldGreen
        isTargetMet -> emeraldGreen
        isAllSevenDays -> goldColor
        else -> WarmOffWhite
    }
    val accentColor = when {
        isPenaltyActive -> SignalRed
        isTargetMet -> emeraldGreen
        isAllSevenDays -> goldColor
        else -> cyanColor
    }
    val cardBorderColor = when {
        isPenaltyActive -> SignalRed.copy(alpha = 0.75f)
        isEarnActive -> emeraldGreen.copy(alpha = 0.85f)
        isTargetMet -> emeraldGreen.copy(alpha = 0.65f)
        isAllSevenDays -> goldColor.copy(alpha = 0.85f)
        else -> cyanColor.copy(alpha = 0.35f)
    }
    val titleColor = when {
        isPenaltyActive -> SignalRed
        isEarnActive -> emeraldGreen
        isTargetMet -> emeraldGreen
        isAllSevenDays -> goldColor
        else -> cyanColor
    }
    val layoutDirection = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        GlassCard(borderColor = cardBorderColor) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 1. Header: Left Title, Right 90D Stake
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEnglish) "RESERVE BALANCE" else "الرصيد الاحتياطي",
                        color = titleColor,
                        fontSize = 15.sp,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Bold
                    )
                    if (isDeficit) {
                        Box(
                            modifier = Modifier
                                .background(SignalRed.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                                .border(1.dp, SignalRed.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "⚠️ PENALTY DEFICIT" else "⚠️ عجز بالرصيد",
                                color = SignalRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Outfit
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(cyanColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                .border(1.dp, cyanColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "90D STAKE • WK $stakeWeek/12" else "عهد الـ 90 يوماً • أسبوع $stakeWeek من 12",
                                color = cyanColor,
                                fontSize = 11.sp,
                                fontFamily = Outfit,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 2. Points & Stake Progress above 500
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = if (reservePoints < 0) "-${kotlin.math.abs(reservePoints)}" else "$reservePoints",
                                color = pointsColor,
                                fontSize = 54.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = Outfit
                            )
                        }
                        Text(
                            text = if (isEnglish) "PTS" else "نقطة",
                            color = accentColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = Outfit,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    Column(
                        horizontalAlignment = if (isEnglish) Alignment.End else Alignment.Start,
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        val stakePctFormatted = String.format(Locale.US, "%.1f%% / 6,000", cumulativeStakePercentage)
                        Text(
                            text = if (isEnglish) "TOTAL STAKE: $stakePctFormatted" else "إجمالي العهد: $stakePctFormatted",
                            color = if (isTargetMet) emeraldGreen else cyanColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isEnglish) "minimum: 380" else "الحد الأدنى: 380",
                            color = WarmOffWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Outfit
                        )
                    }
                }

                // 3. Minimalist 16-Segment Horizontal Battery Bar (Turns Green >= 380 PTS)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (isDeficit) {
                            val penaltySegments = ((Math.abs(reservePoints) / 31.25).toInt()).coerceIn(1, 16)
                            for (i in 1..16) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(12.dp)
                                        .background(
                                            if (i <= penaltySegments) SignalRed else ObsidianBorder,
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                        } else {
                            val activeSegments = (reservePoints / 31.25).toInt().coerceIn(0, 16)
                            val barColor = when {
                                isAllSevenDays -> goldColor
                                isTargetMet -> emeraldGreen
                                else -> cyanColor
                            }
                            for (i in 1..16) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(12.dp)
                                        .background(
                                            if (i <= activeSegments) barColor else ObsidianBorder,
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (isEnglish) "Empty" else "فارغ", color = MutedSilver, fontSize = 11.sp, fontFamily = Outfit, fontWeight = FontWeight.Bold)
                        val percentageText = if (isDeficit) {
                            "${((reservePoints / 500f) * 100f).toInt()}%"
                        } else {
                            "${((reservePoints / 500f) * 100f).toInt().coerceIn(0, 100)}%"
                        }
                        Text(
                            text = percentageText,
                            color = if (isTargetMet) emeraldGreen else cyanColor,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black
                        )
                        Text(if (isEnglish) "Full" else "مكتمل", color = MutedSilver, fontSize = 11.sp, fontFamily = Outfit, fontWeight = FontWeight.Bold)
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ObsidianBorder))

                // 4. The 4 Stacked Routine Columns
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    // 1. PAPER RITUAL
                    val paperColor = when {
                        protocolComplianceStreak >= 7 -> goldColor
                        protocolComplianceStreak >= 3 -> SignalAmber
                        protocolComplianceStreak > 0 -> WarmOffWhite
                        else -> MutedSilver
                    }
                    StackedReserveMetric(
                        topWord = if (isEnglish) "PAPER" else "التدبر",
                        bottomWord = if (isEnglish) "RITUAL" else "الورقي",
                        value = if (isEnglish) "${protocolComplianceStreak}d" else "$protocolComplianceStreak يوم",
                        color = paperColor,
                        modifier = Modifier.weight(1f)
                    )

                    // 2. EVENING RETREAT
                    val retreatColor = when {
                        retreatStreak >= 7 -> goldColor
                        retreatStreak >= 3 -> cyanColor
                        retreatStreak > 0 -> WarmOffWhite
                        else -> MutedSilver
                    }
                    StackedReserveMetric(
                        topWord = if (isEnglish) "EVENING" else "خلوة",
                        bottomWord = if (isEnglish) "RETREAT" else "المغرب",
                        value = if (isEnglish) "${retreatStreak}d" else "$retreatStreak يوم",
                        color = retreatColor,
                        modifier = Modifier.weight(1f)
                    )

                    // 3. SOLO ROUTINE
                    val soloColor = when {
                        outdoorSoloCountThisWeek >= 7 -> goldColor
                        outdoorSoloCountThisWeek >= 3 -> cyanColor
                        outdoorSoloCountThisWeek > 0 -> WarmOffWhite
                        else -> MutedSilver
                    }
                    StackedReserveMetric(
                        topWord = if (isEnglish) "SOLO" else "الروتين",
                        bottomWord = if (isEnglish) "ROUTINE" else "الفردي",
                        value = if (isEnglish) "${outdoorSoloCountThisWeek}d" else "$outdoorSoloCountThisWeek يوم",
                        color = soloColor,
                        modifier = Modifier.weight(1f)
                    )

                    // 4. JOIN GROUP (جلسة الرفاق / Outdoor Group & Partner Session)
                    val groupColor = when {
                        outdoorPartnerCountThisWeek >= 7 -> goldColor
                        outdoorPartnerCountThisWeek >= 1 -> emeraldGreen
                        outdoorPartnerCountThisWeek > 0 -> WarmOffWhite
                        else -> MutedSilver
                    }
                    StackedReserveMetric(
                        topWord = if (isEnglish) "JOIN" else "جلسة",
                        bottomWord = if (isEnglish) "GROUP" else "الرفاق",
                        value = if (isEnglish) "${outdoorPartnerCountThisWeek}d" else "$outdoorPartnerCountThisWeek يوم",
                        color = groupColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ObsidianBorder))

                // 5. 40-Day Takbir Covenant (5 dedicated daily prayer slots + 8,000 PTS scale)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val covenantTitle = if (isEnglish) "40-DAY TAKBIR COVENANT" else "عهد الـ 40 يوماً (تكبيرة الإحرام)"
                        val covenantDayProgress = if (isEnglish) "DAY $covenantDayNumber / 40" else "اليوم $covenantDayNumber من 40"
                        Text(
                            text = covenantTitle,
                            color = cyanColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = covenantDayProgress,
                            color = WarmOffWhite,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Clean 5-Slot Daily Prayer Bar (Option 1: 20.dp height, 11.sp font, 4.dp radius)
                        val prayerLetters = if (isEnglish) listOf("F", "D", "A", "M", "I") else listOf("ف", "ظ", "ع", "م", "ش")
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (idx in 0..4) {
                                val isCompleted = todayPrayersCompleted.getOrElse(idx) { false }
                                val slotBg = if (isCompleted) cyanColor else ObsidianBorder
                                val slotTextColor = if (isCompleted) ObsidianBg else MutedSilver
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(slotBg, RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = prayerLetters[idx],
                                        color = slotTextColor,
                                        fontSize = 11.sp,
                                        fontWeight = if (isCompleted) FontWeight.Black else FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        val scoreColor = if (covenantScore >= 6000) emeraldGreen else if (covenantScore >= 2000) cyanColor else WarmOffWhite
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = if (isEnglish) "$covenantScore / 8000 PTS ($covenantPercentage%)" else "$covenantScore / 8000 نقطة ($covenantPercentage%)",
                                color = scoreColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    if (covenantDayNumber >= 40 && covenantScore >= 6000 && !covenantBonusClaimed) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Button(
                            onClick = onClaimCovenantBonus,
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = emeraldGreen, contentColor = ObsidianBg),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "🎉 CLAIM 500 PTS COVENANT BONUS" else "🎉 استلام مكافأة العهد (+500 نقطة)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StackedReserveMetric(
    topWord: String,
    bottomWord: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val labelColor = if (color != MutedSilver && color != SignalRed) color else MutedSilver
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            fontFamily = Outfit
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = topWord,
            color = labelColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = bottomWord,
            color = labelColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ReserveMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = Outfit)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = MutedSilver, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReserveLedgerHistorySection(sessionLogs: List<SessionLog>) {
    var isExpanded by remember { mutableStateOf(false) }
    val sortedLogs = remember(sessionLogs) { sessionLogs.sortedByDescending { it.timestamp } }
    val recentLog = sortedLogs.firstOrNull()
    val olderLogs = sortedLogs.drop(1)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = olderLogs.isNotEmpty()) { isExpanded = !isExpanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LEDGER TAPE HISTORY",
                color = MutedSilver,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp
            )
            if (olderLogs.isNotEmpty()) {
                Text(
                    text = if (isExpanded) "HIDE" else "VIEW ${olderLogs.size}",
                    color = GlowGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (recentLog == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(ObsidianSurface, RoundedCornerShape(8.dp))
                    .border(BorderStroke(1.dp, ObsidianBorder.copy(alpha = 0.42f)), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("No events logged on hardware yet.", color = MutedSilver, fontSize = 14.sp, fontFamily = Outfit)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LedgerHistoryRow(log = recentLog)
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 3 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 3 })
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        olderLogs.forEach { log ->
                            LedgerHistoryRow(log = log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerHistoryRow(log: SessionLog) {
    val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(log.timestamp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface, RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, ObsidianBorder.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
            .padding(vertical = 12.dp, horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(log.action, color = WarmOffWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Outfit)
            Text(dateStr, color = MutedSilver, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        val positive = log.pointsChange >= 0
        Text(
            text = (if (positive) "+" else "") + log.pointsChange,
            color = if (positive) GlowGreen else SignalAmber,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}

// Pillar Row Component
@Composable
private fun PillarRow(
    title: String,
    desc: String,
    isCompleted: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface, RoundedCornerShape(8.dp))
            .border(1.dp, if (isCompleted) GlowGreen.copy(alpha = 0.45f) else ObsidianBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isCompleted) GlowGreen else WarmOffWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Outfit
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                color = MutedSilver,
                fontSize = 11.sp,
                fontFamily = Outfit
            )
        }
        Text(
            text = if (isCompleted) "✅" else "❌",
            fontSize = 18.sp
        )
    }
}

@Composable
private fun BehaviorAlertsCard(
    flags: List<RuleFlag>,
    onDismiss: (Long) -> Unit
) {
    if (flags.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }

    GlassCard(borderColor = SignalAmber) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .padding(bottom = if (expanded) 12.dp else 0.dp)
                .border(0.dp, Color.Transparent, RoundedCornerShape(0.dp))
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BEHAVIOR ALERTS",
                    color = SignalAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${flags.size} context item${if (flags.size == 1) "" else "s"} for custody review",
                    color = WarmSilverText,
                    fontSize = 13.sp,
                    fontFamily = Outfit
                )
            }
            Box(
                modifier = Modifier
                    .background(SignalAmber.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                    .border(1.dp, SignalAmber.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = flags.size.toString(),
                    color = SignalAmber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                flags.forEach { flag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianBg, RoundedCornerShape(8.dp))
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = when (flag.severity) {
                                "WARN" -> "!"
                                "BLOCK" -> "?"
                                else -> "i"
                            },
                            color = when (flag.severity) {
                                "WARN" -> SignalAmber
                                "BLOCK" -> SignalAmber
                                else -> GlowGreen
                            },
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = flag.message,
                                color = WarmSilverText,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontFamily = Outfit
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Dismiss",
                                color = GlowGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.clickable { onDismiss(flag.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// (Removed legacy unused composables ProofMissionScreen and UnlockReviewScreen as per implementation plan)


@Composable
fun ReturnProofScreen(
    viewModel: AnchorViewModel,
    onDone: () -> Unit,
    onRecovery: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pending by viewModel.pendingReturnSession.collectAsState()
    var proofPath by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }

    ActionScaffold(title = "Return Checkpoint", subtitle = "Upload the strict-mode AFTER screenshot.") {
        val session = pending
        if (session == null) {
            StatusNotice("No active review is waiting for return proof.", MutedSilver)
            PremiumButton(
                text = "BACK TO DASHBOARD",
                onClick = onDone,
                gradientColors = listOf(DeepPanel, ObsidianSurface),
                contentColor = WarmOffWhite
            )
            return@ActionScaffold
        }

        StatusNotice("Return window closes at ${formatTime(session.returnDueAt)}.", SignalAmber)
        Spacer(modifier = Modifier.height(12.dp))
        ProofCaptureControls(
            selectedPath = proofPath,
            onPathSelected = { proofPath = it },
            prefix = "appblock_after",
            cameraEnabled = false
        )
        Spacer(modifier = Modifier.height(14.dp))
        AnalogTextField(
            value = note,
            onValueChange = { if (it.length <= 180) note = it },
            placeholder = "Optional: what happened during the unlock window?"
        )
        Spacer(modifier = Modifier.height(18.dp))
        PremiumButton(
            text = "SUBMIT RETURN PROOF",
            enabled = proofPath != null,
            onClick = {
                val path = proofPath ?: return@PremiumButton
                scope.launch {
                    val outcome = viewModel.submitReturnProof(session.id, path, note)
                    if (outcome == UnlockSession.OUTCOME_COMPLIANT) {
                        onDone()
                    } else {
                        onRecovery()
                    }
                }
            },
            gradientColors = listOf(RefinedGreen, GlowGreen)
        )
    }
}

@Composable
fun RecoveryScreen(
    viewModel: AnchorViewModel,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val recovery by viewModel.openRecoveryRecord.collectAsState()
    var proofPath by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }

    ActionScaffold(title = "Recovery", subtitle = "Restore eligibility with a concrete proof action.") {
        StatusNotice(
            text = recovery?.reason ?: "No recovery is currently open.",
            color = if (recovery == null) GlowGreen else SignalAmber
        )
        Spacer(modifier = Modifier.height(12.dp))
        ProofCaptureControls(
            selectedPath = proofPath,
            onPathSelected = { proofPath = it },
            prefix = "recovery_proof",
            cameraEnabled = false
        )
        Spacer(modifier = Modifier.height(14.dp))
        AnalogTextField(
            value = note,
            onValueChange = { if (it.length <= 180) note = it },
            placeholder = "Optional recovery note"
        )
        Spacer(modifier = Modifier.height(18.dp))
        PremiumButton(
            text = "COMPLETE RECOVERY",
            enabled = proofPath != null,
            onClick = {
                val path = proofPath ?: return@PremiumButton
                scope.launch {
                    viewModel.resolveRecoveryWithProof(path, note)
                    onDone()
                }
            },
            gradientColors = listOf(RefinedGreen, GlowGreen)
        )
    }
}

@Composable
private fun DashboardHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "ANALOG ANCHOR",
            color = WarmOffWhite,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            fontFamily = Outfit
        )
        Text(
            text = "Private accountability. Emergency friction.",
            color = GlowGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun StatusGrid(
    anchorDone: Boolean,
    mosqueDone: Boolean,
    reservePoints: Int,
    recoveryOpen: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatusTile("Anchor", if (anchorDone) "Done" else "Open", anchorDone, Modifier.weight(1f))
            StatusTile("Mosque", if (mosqueDone) "Done" else "Open", mosqueDone, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatusTile("Reserve", "$reservePoints pts", reservePoints > 0, Modifier.weight(1f))
            StatusTile("Recovery", if (recoveryOpen) "Required" else "Clear", !recoveryOpen, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatusTile(label: String, value: String, positive: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(ObsidianSurface, RoundedCornerShape(8.dp))
            .border(1.dp, if (positive) GlowGreen.copy(alpha = 0.55f) else SignalAmber.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = label.uppercase(Locale.US),
                color = MutedSilver,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = if (positive) GlowGreen else SignalAmber,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                fontFamily = Outfit,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun ActionScaffold(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title.uppercase(Locale.US), color = WarmOffWhite, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = Outfit)
        Text(subtitle, color = MutedSilver, fontSize = 14.sp, lineHeight = 20.sp, fontFamily = Outfit)
        GlassCard(content = content)
    }
}

@Composable
private fun ProofCaptureControls(
    selectedPath: String?,
    onPathSelected: (String) -> Unit,
    prefix: String,
    cameraEnabled: Boolean = true
) {
    val context = LocalContext.current
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingCameraPath?.let(onPathSelected)
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pendingCameraUri?.let { takePictureLauncher.launch(it) }
        } else {
            Toast.makeText(context, "Camera permission is required for capture.", Toast.LENGTH_SHORT).show()
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val path = ProofFileStore.copyUriToProofFile(context, uri, prefix)
            if (path != null) {
                onPathSelected(path)
            } else {
                Toast.makeText(context, "Could not import image.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ImagePreview(selectedPath)
    Spacer(modifier = Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        if (cameraEnabled) {
            PremiumButton(
                text = "TAKE PHOTO",
                onClick = {
                    val file = ProofFileStore.createProofImageFile(context, prefix)
                    pendingCameraPath = file.absolutePath
                    pendingCameraUri = ProofFileStore.uriForFile(context, file)
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        pendingCameraUri?.let { takePictureLauncher.launch(it) }
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                gradientColors = listOf(DeepPanel, ObsidianSurface),
                contentColor = WarmOffWhite,
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = WarmOffWhite, modifier = Modifier.size(18.dp)) }
            )
        }
        PremiumButton(
            text = "UPLOAD",
            onClick = { galleryLauncher.launch("image/*") },
            gradientColors = listOf(DeepPanel, ObsidianSurface),
            contentColor = WarmOffWhite,
            modifier = Modifier.weight(1f),
            icon = { Icon(Icons.Default.CloudUpload, contentDescription = null, tint = WarmOffWhite, modifier = Modifier.size(18.dp)) }
        )
    }
}

@Composable
private fun ImagePreview(path: String?) {
    val bitmap = remember(path) {
        path?.let { BitmapFactory.decodeFile(it) }?.asImageBitmap()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .background(ObsidianBg, RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, ObsidianBorder), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Selected proof",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MutedSilver, modifier = Modifier.size(34.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("No image selected", color = MutedSilver, fontSize = 13.sp, fontFamily = Outfit)
            }
        }
    }
}

@Composable
private fun ReviewCardPreview(cardPath: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusNotice("Review card generated. Share it, then return here after strict-mode approval.", GlowGreen)
        ImagePreview(cardPath)
    }
}

@Composable
private fun AnalogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = MutedSilver, fontFamily = Outfit) },
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp),
        colors = TextFieldDefaults.colors(
            focusedTextColor = WarmOffWhite,
            unfocusedTextColor = WarmOffWhite,
            focusedContainerColor = ObsidianBg,
            unfocusedContainerColor = ObsidianBg,
            focusedIndicatorColor = GlowGreen,
            unfocusedIndicatorColor = ObsidianBorder,
            cursorColor = GlowGreen
        )
    )
}

@Composable
private fun StatusNotice(text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (color == GlowGreen) Icons.Default.CheckCircle else if (color == SignalAmber) Icons.Default.Warning else Icons.Default.LockClock,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Text(text, color = WarmSilverText, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = Outfit)
    }
}

@Composable
private fun StatusTitle(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 17.sp,
        fontWeight = FontWeight.Black,
        fontFamily = Outfit
    )
    Spacer(modifier = Modifier.height(8.dp))
}

private fun shareReviewCard(
    context: Context,
    cardPath: String,
    viaWhatsApp: Boolean,
    founderEmail: String
) {
    val cardFile = File(cardPath)
    val uri = ProofFileStore.uriForFile(context, cardFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (viaWhatsApp) "image/jpeg" else "message/rfc822"
        putExtra(Intent.EXTRA_SUBJECT, "Analog Anchor Review")
        putExtra(Intent.EXTRA_TEXT, "Analog Anchor review request - ${formatDate(System.currentTimeMillis())}")
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (viaWhatsApp) {
            setPackage("com.whatsapp")
        } else if (founderEmail.isNotBlank()) {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(founderEmail))
        }
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Share Analog Anchor review"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No compatible app found for sharing.", Toast.LENGTH_LONG).show()
    }
}

private fun openAppBlockStore(context: Context) {
    try {
        context.startActivity(AppBlockIntegration.playStoreIntent())
    } catch (_: ActivityNotFoundException) {
        context.startActivity(AppBlockIntegration.webPlayStoreIntent())
    }
}

private fun formatTime(timestamp: Long?): String {
    if (timestamp == null) return "the return deadline"
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(timestamp))
}
