package com.analoganchor.app.ui.screens.settings

import androidx.compose.ui.res.stringResource
import com.analoganchor.app.R
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.analoganchor.app.data.preferences.UserPreferences
import com.analoganchor.app.data.preferences.UserPreferencesRepository
import com.analoganchor.app.data.preferences.CompactnessOption
import com.analoganchor.app.ui.navigation.Screen
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import com.analoganchor.app.viewmodel.AnchorViewModel
import com.analoganchor.app.ui.screens.ReserveLedgerBalanceCard
import com.analoganchor.app.ui.screens.HardwareUpgradeShowcaseCard

@Composable
fun SettingsScreen(
    isEnglish: Boolean,
    viewModel: AnchorViewModel,
    preferencesRepository: UserPreferencesRepository,
    innerPadding: PaddingValues,
    navController: NavController,
    onOpenPermissions: () -> Unit
) {
    val preferences by preferencesRepository.userPreferencesFlow.collectAsState(initial = UserPreferences())
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val isEditWindowActive = preferences.policyEditWindowEndTimestamp > System.currentTimeMillis()
    var showWhitelistSheet by remember { mutableStateOf(false) }
    var showProtectedAppsSheet by remember { mutableStateOf(false) }

    val reservePoints by viewModel.reservePoints.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val protocolComplianceStreak by viewModel.protocolComplianceStreak.collectAsState(0)
    val mosqueComplianceStreak by viewModel.mosqueComplianceStreak.collectAsState(0)
    val habitScore by viewModel.habitScore.collectAsState()
    val hasRecentPenalty by viewModel.hasRecentPenalty.collectAsState()
    val weeklyMosquePrayers by viewModel.weeklyMosqueCheckInCount.collectAsState()
    val maghribRetreatStreak by viewModel.maghribRetreatStreak.collectAsState()
    val covenantDayNumber by viewModel.covenantDayNumber.collectAsState()
    val covenantScore by viewModel.covenantScore.collectAsState()
    val covenantPercentage by viewModel.covenantPercentage.collectAsState()
    val covenantBonusClaimed by viewModel.covenantBonusClaimed.collectAsState()
    val recentTransactionType by viewModel.recentTransactionType.collectAsState()
    val stakeWeek by viewModel.stakeWeek.collectAsState()
    val cumulativeStakePercentage by viewModel.cumulativeStakePercentage.collectAsState()
    val todayPrayersCompleted by viewModel.todayPrayersCompleted.collectAsState()
    val outdoorSoloCountThisWeek by viewModel.outdoorSoloCountThisWeek.collectAsState()
    val outdoorPartnerCountThisWeek by viewModel.outdoorPartnerCountThisWeek.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = 6.dp,
                bottom = innerPadding.calculateBottomPadding() + 100.dp
            )
        ) {
            // Section 1: Manage Protected Apps
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showProtectedAppsSheet = true },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = if (isEnglish) "Protected Apps" else "التطبيقات المحمية",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = if (isEnglish) "Protected Apps" else "التطبيقات المحمية",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(text = if (isEnglish) "Choose which apps require the Day Guard mindful gateway." else "اختر التطبيقات التي تتطلب البوابة الواعية لحارس اليوم.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.ArrowForwardIos,
                            contentDescription = "Navigate",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Section 2: Anchor NFC Authority
            item {
                NfcAuthoritySection(
                    preferences = preferences,
                    isEnglish = isEnglish,
                    viewModel = viewModel,
                    reservePoints = reservePoints,
                    onUnlockPolicy = {
                        coroutineScope.launch {
                            preferencesRepository.setPolicyEditWindowEndTimestamp(System.currentTimeMillis() + 15 * 60 * 1000L)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Reserve Balance Box (repositioned below Soft Unlock demo test mode and above Bedtime & Accountability Cutoff)
            item {
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
                    hasRecentPenalty = hasRecentPenalty,
                    recentTransactionType = recentTransactionType,
                    stakeWeek = stakeWeek,
                    cumulativeStakePercentage = cumulativeStakePercentage,
                    todayPrayersCompleted = todayPrayersCompleted,
                    outdoorSoloCountThisWeek = outdoorSoloCountThisWeek,
                    outdoorPartnerCountThisWeek = outdoorPartnerCountThisWeek,
                    isEnglish = isEnglish
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Section 2.5: Bedtime & Accountability Cutoff
            item {
                BedtimeScheduleSection(
                    preferences = preferences,
                    isEnglish = isEnglish,
                    isEditWindowActive = isEditWindowActive,
                    onSaveBedtime = { timeStr ->
                        coroutineScope.launch {
                            preferencesRepository.setBedtimeStartTime(timeStr)
                            preferencesRepository.setBedtimeEndTime("05:00")
                            preferencesRepository.setBedtimeEnabled(true)
                            viewModel.saveEveningProtocolTime(timeStr)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        if (showWhitelistSheet) {
            WhitelistBottomSheet(
                initialWhitelisted = preferences.whitelistedPackages,
                onDismiss = { showWhitelistSheet = false },
                onSave = { newList ->
                    coroutineScope.launch {
                        preferencesRepository.setWhitelistedPackages(newList)
                        showWhitelistSheet = false
                    }
                }
            )
        }

        if (showProtectedAppsSheet) {
            ProtectedAppsBottomSheet(
                initialProtected = preferences.protectedPackages,
                preferences = preferences,
                reservePoints = reservePoints,
                onDismiss = { showProtectedAppsSheet = false },
                onSave = { updated ->
                    coroutineScope.launch {
                        preferencesRepository.setProtectedPackages(updated)
                        showProtectedAppsSheet = false
                    }
                },
                onSetLimit = { packageName, limit ->
                    coroutineScope.launch {
                        preferencesRepository.setProtectedAppLimit(packageName, limit)
                    }
                },
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun NfcAuthoritySection(
    preferences: UserPreferences,
    isEnglish: Boolean,
    viewModel: AnchorViewModel,
    reservePoints: Int,
    onUnlockPolicy: () -> Unit
) {
    val context = LocalContext.current
    val isWindowActive = preferences.policyEditWindowEndTimestamp > System.currentTimeMillis()
    var timeRemainingText by remember { mutableStateOf("") }
    var isListeningForNfc by remember { mutableStateOf(false) }
    var showPointsUnlockConfirmDialog by remember { mutableStateOf(false) }
    val registeredTagId by viewModel.nfcTagId.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.nfcScannedEvent.collect { scannedId ->
            if (isListeningForNfc) {
                viewModel.registerNfcTag(scannedId)
                isListeningForNfc = false
                Toast.makeText(context, if (isEnglish) "NFC Tag Registered: $scannedId" else "تم تسجيل NFC بنجاح", Toast.LENGTH_SHORT).show()
            } else if (registeredTagId.isNotEmpty() && scannedId == registeredTagId) {
                if (!isWindowActive) {
                    onUnlockPolicy()
                    Toast.makeText(context, if (isEnglish) "Policy Window Unlocked!" else "تم فتح نافذة السياسة!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, if (isEnglish) "Policy Window is already unlocked." else "نافذة السياسة مفتوحة بالفعل.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, if (isEnglish) "Unrecognized NFC Tag." else "علامة NFC غير معروفة.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (isWindowActive) {
        LaunchedEffect(preferences.policyEditWindowEndTimestamp) {
            while (true) {
                val diff = preferences.policyEditWindowEndTimestamp - System.currentTimeMillis()
                if (diff <= 0) {
                    timeRemainingText = context.getString(com.analoganchor.app.R.string.expired)
                    break
                }
                val min = diff / 60000
                val sec = (diff % 60000) / 1000
                timeRemainingText = String.format(Locale.getDefault(), "%dm %02ds", min, sec)
                delay(1000)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.TapAndPlay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = if (isEnglish) "Settings Override Authorization" else "تفويض تجاوز الإعدادات",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isEnglish) "Tap your registered NFC tag or use reserve points to authorize setting overrides." else "انقر بعلامة NFC المسجلة أو استخدم نقاط الاحتياط لتفويض تجاوز الإعدادات.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isListeningForNfc) {
                        Text(
                            text = if (isEnglish) "Listening for physical tag tap..." else "في انتظار النقر بالعلامة الفعلية...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = if (isWindowActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isWindowActive) if (isEnglish) "Settings Override Authorization: Active" else "تفويض تجاوز الإعدادات: نشط" else if (isEnglish) "Settings Override Authorization: Locked" else "تفويض تجاوز الإعدادات: مقفل",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isWindowActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error
                    )
                    if (isWindowActive) {
                        Text(
                            text = timeRemainingText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "Locked",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (!isWindowActive) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Button 1: Tap NFC Tag
                    OutlinedButton(
                        onClick = { isListeningForNfc = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.TapAndPlay,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) "NFC Tag" else "علامة NFC",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    // Button 2: Use 380 Reserve Points
                    OutlinedButton(
                        onClick = {
                            if (reservePoints >= 380) {
                                showPointsUnlockConfirmDialog = true
                            } else {
                                Toast.makeText(
                                    context,
                                    if (isEnglish) "You need at least 380 reserve points to unlock (currently: $reservePoints)"
                                    else "تحتاج إلى 380 نقطة احتياط على الأقل لإلغاء القفل (حالياً: $reservePoints)",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (reservePoints >= 380) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Stars,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (reservePoints >= 380) MaterialTheme.colorScheme.tertiary
                                       else MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) "380 Pts" else "380 نقطة",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (reservePoints >= 380) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        onUnlockPolicy()
                        Toast.makeText(
                            context,
                            if (isEnglish) "⚡ Policy Window Unlocked (Demo / Soft Mode) for 15 minutes!"
                            else "⚡ تم فتح نافذة السياسة (الوضع الافتراضي) لمدة 15 دقيقة!",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEnglish) "⚡ Soft Unlock (Demo / Test Mode)" else "⚡ فتح تجريبي لاختبار السياسة",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (showPointsUnlockConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showPointsUnlockConfirmDialog = false },
                    title = {
                        Text(
                            text = if (isEnglish) "Spend Reserve Points?" else "إنفاق نقاط الاحتياط؟",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = if (isEnglish)
                                "Do you want to spend 380 points to unlock settings override for 15 minutes? Your remaining points will be ${reservePoints - 380}."
                                else "هل تريد إنفاق 380 نقطة لفتح تجاوز الإعدادات لمدة 15 دقيقة؟ ستكون نقاطك المتبقية ${reservePoints - 380}."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showPointsUnlockConfirmDialog = false
                                viewModel.deductPoints(380, "Settings Override Point Unlock", "UNLOCKED")
                                onUnlockPolicy()
                                Toast.makeText(context, if (isEnglish) "Settings override unlocked for 15 minutes!" else "تم فتح تجاوز الإعدادات لمدة 15 دقيقة!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(if (isEnglish) "Yes, Unlock" else "نعم، افتح")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPointsUnlockConfirmDialog = false }) {
                            Text(if (isEnglish) "Cancel" else "إلغاء")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun BedtimeScheduleSection(
    preferences: UserPreferences,
    isEnglish: Boolean,
    isEditWindowActive: Boolean,
    onSaveBedtime: (String) -> Unit
) {
    val context = LocalContext.current
    val parts = preferences.bedtimeStartTime.split(":")
    var hour by remember(preferences.bedtimeStartTime) { mutableIntStateOf(parts.getOrNull(0)?.toIntOrNull() ?: 22) }
    var minute by remember(preferences.bedtimeStartTime) { mutableIntStateOf(parts.getOrNull(1)?.toIntOrNull() ?: 0) }

    val currentCal = java.util.Calendar.getInstance()
    val currentHour = currentCal.get(java.util.Calendar.HOUR_OF_DAY)
    // Pre-commitment rule: Locked after 5:00 PM (17:00) until morning (5:00 AM)
    val isPreCommitmentLocked = currentHour >= 17 || currentHour < 5

    // Allowed bedtime hours capped between 20:00 (8:00 PM) and 01:00 (1:00 AM maximum)
    val allowedHours = listOf(20, 21, 22, 23, 0, 1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEnglish) "Bedtime & Accountability Cutoff" else "موعد النوم والمساءلة اليومي",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isEnglish) "Pre-commitment decision: Modifiable daily until 5:00 PM (Capped at 1:00 AM maximum). Protected apps lock strictly at this cutoff."
                               else "قرار التزام مسبق: يمكن التعديل يومياً حتى 5:00 م (بحد أقصى 1:00 ص). تقفل التطبيقات المحمية تماماً عند هذا الموعد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isPreCommitmentLocked) 0.3f else 0.5f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isEnglish) "Current Bedtime Cutoff" else "موعد النوم الحالي",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val formattedDisplay = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                        Text(
                            text = formattedDisplay,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = if (isPreCommitmentLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.alpha(if (isPreCommitmentLocked) 0.4f else 1.0f)
                    ) {
                        // Hour controls
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isEnglish) "Hour" else "ساعة",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalIconButton(
                                    onClick = {
                                        if (isPreCommitmentLocked) {
                                            Toast.makeText(context, if (isEnglish) "Bedtime is locked after 5:00 PM as a pre-commitment. Unlocks tomorrow morning." else "موعد النوم مقفل بعد 5:00 مساءً للالتزام المسبق. يفتح صباح الغد.", Toast.LENGTH_SHORT).show()
                                        } else if (!isEditWindowActive) {
                                            Toast.makeText(context, if (isEnglish) "Unlock settings override first." else "قم بفك قفل تجاوز الإعدادات أولاً.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val currentIndex = allowedHours.indexOf(hour).takeIf { it != -1 } ?: 2
                                            val newIndex = if (currentIndex > 0) currentIndex - 1 else allowedHours.size - 1
                                            hour = allowedHours[newIndex]
                                            onSaveBedtime(String.format(Locale.US, "%02d:%02d", hour, minute))
                                        }
                                    },
                                    modifier = Modifier.size(36.dp),
                                    enabled = !isPreCommitmentLocked
                                ) {
                                    Text("◀", style = MaterialTheme.typography.labelSmall)
                                }
                                Text(
                                    text = String.format(Locale.US, "%02d", hour),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                                FilledTonalIconButton(
                                    onClick = {
                                        if (isPreCommitmentLocked) {
                                            Toast.makeText(context, if (isEnglish) "Bedtime is locked after 5:00 PM as a pre-commitment. Unlocks tomorrow morning." else "موعد النوم مقفل بعد 5:00 مساءً للالتزام المسبق. يفتح صباح الغد.", Toast.LENGTH_SHORT).show()
                                        } else if (!isEditWindowActive) {
                                            Toast.makeText(context, if (isEnglish) "Unlock settings override first." else "قم بفك قفل تجاوز الإعدادات أولاً.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val currentIndex = allowedHours.indexOf(hour).takeIf { it != -1 } ?: 2
                                            val newIndex = if (currentIndex < allowedHours.size - 1) currentIndex + 1 else 0
                                            hour = allowedHours[newIndex]
                                            onSaveBedtime(String.format(Locale.US, "%02d:%02d", hour, minute))
                                        }
                                    },
                                    modifier = Modifier.size(36.dp),
                                    enabled = !isPreCommitmentLocked
                                ) {
                                    Text("▶", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        // Minute controls
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isEnglish) "Min" else "دقيقة",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalIconButton(
                                    onClick = {
                                        if (isPreCommitmentLocked) {
                                            Toast.makeText(context, if (isEnglish) "Bedtime is locked after 5:00 PM as a pre-commitment. Unlocks tomorrow morning." else "موعد النوم مقفل بعد 5:00 مساءً للالتزام المسبق. يفتح صباح الغد.", Toast.LENGTH_SHORT).show()
                                        } else if (!isEditWindowActive) {
                                            Toast.makeText(context, if (isEnglish) "Unlock settings override first." else "قم بفك قفل تجاوز الإعدادات أولاً.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            minute = if (minute >= 15) minute - 15 else 45
                                            onSaveBedtime(String.format(Locale.US, "%02d:%02d", hour, minute))
                                        }
                                    },
                                    modifier = Modifier.size(36.dp),
                                    enabled = !isPreCommitmentLocked
                                ) {
                                    Text("◀", style = MaterialTheme.typography.labelSmall)
                                }
                                Text(
                                    text = String.format(Locale.US, "%02d", minute),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                                FilledTonalIconButton(
                                    onClick = {
                                        if (isPreCommitmentLocked) {
                                            Toast.makeText(context, if (isEnglish) "Bedtime is locked after 5:00 PM as a pre-commitment. Unlocks tomorrow morning." else "موعد النوم مقفل بعد 5:00 مساءً للالتزام المسبق. يفتح صباح الغد.", Toast.LENGTH_SHORT).show()
                                        } else if (!isEditWindowActive) {
                                            Toast.makeText(context, if (isEnglish) "Unlock settings override first." else "قم بفك قفل تجاوز الإعدادات أولاً.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            minute = if (minute <= 30) minute + 15 else 0
                                            onSaveBedtime(String.format(Locale.US, "%02d:%02d", hour, minute))
                                        }
                                    },
                                    modifier = Modifier.size(36.dp),
                                    enabled = !isPreCommitmentLocked
                                ) {
                                    Text("▶", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            // Pre-commitment status indicator
            if (isPreCommitmentLocked) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEnglish)
                                "Pre-commitment active: Bedtime locks daily at 5:00 PM (Max 1:00 AM). Unlocks tomorrow morning."
                            else
                                "الالتزام المسبق نشط: يقفل موعد النوم يومياً عند 5:00 م (بحد أقصى 1:00 ص). يفتح صباح الغد.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}




