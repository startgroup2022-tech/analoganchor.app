package com.analoganchor.app.ui.components

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analoganchor.app.data.preferences.PartnerSessionPrefs
import com.analoganchor.app.ui.theme.*
import kotlinx.coroutines.launch
import com.analoganchor.app.util.DiscoveredPartner
import com.analoganchor.app.util.PartnerPairingManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerPairingBottomSheet(
    onDismiss: () -> Unit,
    isEnglish: Boolean,
    sanctuaryName: String = "Outdoor Sanctuary",
    localUserName: String = "Partner"
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val discoveredPartners by PartnerPairingManager.discoveredPartners.collectAsState()
    val isScanning by PartnerPairingManager.isScanning.collectAsState()
    val pairedPartner by PartnerPairingManager.pairedPartner.collectAsState()
    var hasBlePerms by remember { mutableStateOf(PartnerPairingManager.hasBluetoothPermissions(context)) }
    val isBleOn = remember { PartnerPairingManager.isBluetoothEnabled() }
    val isOfflineAppInstalled = remember { PartnerPairingManager.isOfflineChallengeInstalled(context) }

    var isRefreshing by remember { mutableStateOf(false) }
    var showConfirmShieldDialog by remember { mutableStateOf(false) }

    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (PartnerPairingManager.isBluetoothEnabled()) {
            PartnerPairingManager.startAdvertising(context)
            PartnerPairingManager.startScanning()
        }
    }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val granted = PartnerPairingManager.hasBluetoothPermissions(context)
        hasBlePerms = granted
        if (granted) {
            if (PartnerPairingManager.isBluetoothEnabled()) {
                PartnerPairingManager.startAdvertising(context)
                PartnerPairingManager.startScanning()
            } else {
                try {
                    val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    enableBtLauncher.launch(enableIntent)
                } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(Unit) {
        PartnerPairingManager.init(context)
        PartnerPairingManager.resetPairing()

        // 1. Bluetooth permissions check
        if (!hasBlePerms) {
            val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
            blePermissionLauncher.launch(permissionsToRequest)
        } else if (!isBleOn) {
            // 2. Bluetooth is off -> immediate redirect / prompt
            try {
                val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBtLauncher.launch(enableIntent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        } else {
            // 3. Ready -> start advertising own device model & scan nearby
            PartnerPairingManager.startAdvertising(context)
            PartnerPairingManager.startScanning()
        }
    }

    // Gentle in-memory pruning loop every 8s (avoids BLE radio thrashing & Android scan quota throttling)
    LaunchedEffect(hasBlePerms, isBleOn) {
        if (hasBlePerms && PartnerPairingManager.isBluetoothEnabled()) {
            while (true) {
                kotlinx.coroutines.delay(8000L)
                if (pairedPartner == null) {
                    PartnerPairingManager.pruneStaleDevices()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Stop scanning and clear pairing state, but preserve background advertising grace period
            PartnerPairingManager.stopScanning()
            PartnerPairingManager.resetPairing()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MutedSilver) }
    ) {
        val sheetDirection = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
        CompositionLocalProvider(LocalLayoutDirection provides sheetDirection) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    text = if (isEnglish) "🤝 Link Anchor Circle" else "🤝 جلسة الرفاق",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Outfit,
                    color = WarmOffWhite
                )

                Text(
                    text = if (isEnglish) {
                        "⚡ Earn 40 PTS with your group.\nMust be within 1 meter."
                    } else {
                        "⚡ اكسب 40 نقطة مع رفاقك\nضمن نطاق 1 متر"
                    },
                    fontSize = 13.sp,
                    color = GlowGreen,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    fontFamily = Outfit
                )

                // App readiness card
                GlassCard(borderColor = if (isOfflineAppInstalled) GlowGreen.copy(alpha = 0.5f) else SignalAmber) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isEnglish) "1. Analog Anchor App" else "1. تطبيق أنالوج أنكور",
                                fontSize = 12.sp,
                                color = WarmOffWhite,
                                fontFamily = Outfit,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GlowGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isEnglish) "2. Offline Challenge App" else "2. تطبيق تحدي الأوفلاين",
                                fontSize = 12.sp,
                                color = WarmOffWhite,
                                fontFamily = Outfit,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (isOfflineAppInstalled) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isOfflineAppInstalled) GlowGreen else SignalAmber,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                    if (!isOfflineAppInstalled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isEnglish) {
                                "All friends need the Offline Challenge app installed to enter the focus shield together."
                            } else {
                                "يجب تثبيت تطبيق تحدي الأوفلاين على أجهزة الرفاق للدخول في درع التركيز معاً."
                            },
                            fontSize = 11.sp,
                            color = SignalAmber,
                            fontFamily = Outfit
                        )
                        OutlinedButton(
                            onClick = {
                                context.startActivity(PartnerPairingManager.getOfflineChallengeInstallIntent())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, SignalAmber),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "Get Offline Challenge App" else "تثبيت تطبيق الأوفلاين",
                                color = SignalAmber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Paired state
            if (pairedPartner != null) {
                val partner = pairedPartner!!
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GlowGreen.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .border(1.dp, GlowGreen, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "🎉", fontSize = 32.sp)
                        Text(
                            text = if (isEnglish) "Partner Linked: ${partner.name}" else "تم الربط بالشريك: ${partner.name}",
                            color = GlowGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Outfit
                        )
                        Text(
                            text = if (isEnglish) {
                                "Physical presence verified! 1 Partner session fulfills your entire weekly routine."
                            } else {
                                "تم توثيق الحضور الفعلي! جلسة واحدة مع شريك تحقق شرط الأسبوع كاملاً."
                            },
                            color = WarmOffWhite,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = Outfit
                        )
                    }
                }

                PremiumButton(
                    text = if (isEnglish) "🚀 Launch Offline Shield (1 Hour)" else "🚀 بدء درع الأوفلاين المشترك (ساعة واحدة)",
                    onClick = {
                        if (isOfflineAppInstalled) {
                            triggerHighVibration(context)
                            showConfirmShieldDialog = true
                        } else {
                            Toast.makeText(
                                context,
                                if (isEnglish) "Please install Offline Challenge first." else "يرجى تثبيت تطبيق الأوفلاين أولاً",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    gradientColors = listOf(RefinedGreen, GlowGreen),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        PartnerPairingManager.resetPairing()
                        if (PartnerPairingManager.isBluetoothEnabled()) {
                            PartnerPairingManager.startAdvertising(context)
                            PartnerPairingManager.startScanning()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isEnglish) "🔄 Unlink & Search for Another Partner" else "🔄 إلغاء الربط والبحث عن شريك آخر",
                        color = MutedSilver,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Outfit
                    )
                }
            } else {
                // BLE Radar Mode with Animated Pulse
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianBg, RoundedCornerShape(14.dp))
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Animated Concentric Pulse Sweep
                        RadarPulseAnimation(isScanning = isScanning)

                        Text(
                            text = if (isScanning) {
                                if (isEnglish) "Searching for nearby friends..." else "جارٍ البحث عن رفاق بالقرب منك..."
                            } else {
                                if (isEnglish) "Bluetooth ready" else "البلوتوث جاهز"
                            },
                            color = if (isScanning) GlowGreen else MutedSilver,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )

                        if (discoveredPartners.isEmpty()) {
                            Text(
                                text = if (isEnglish) {
                                    "Ask your friends to open Analog Anchor and tap JOIN GROUP.\nBring devices within 1 meter."
                                } else {
                                    "اطلب من رفاقك فتح التطبيق والضغط على \"انضمام للرفاق\"\nضعوا الأجهزة متقاربة ضمن نطاق متر واحد"
                                },
                                fontSize = 11.sp,
                                color = MutedSilver,
                                textAlign = TextAlign.Center,
                                fontFamily = Outfit
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                discoveredPartners.forEach { partner ->
                                    val isCatchUp = partner.hasActiveSession && partner.remainingMinutes < 60
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(ObsidianSurface, RoundedCornerShape(10.dp))
                                            .border(1.2.dp, if (isCatchUp) SignalAmber.copy(alpha = 0.8f) else GlowGreen.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                            .clickable {
                                                // Zero-friction: immediate vibration + confirmation pop-up!
                                                triggerHighVibration(context)
                                                PartnerPairingManager.confirmPairing(partner)
                                                showConfirmShieldDialog = true
                                            }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "🤝 ${partner.name}",
                                                color = WarmOffWhite,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = Outfit
                                            )
                                            Text(
                                                text = if (isCatchUp) {
                                                    if (isEnglish) "⚡ In progress (${partner.remainingMinutes}m remaining)"
                                                    else "⚡ الجلسة بدأت (متبقي ${partner.remainingMinutes} دقيقة)"
                                                } else {
                                                    if (isEnglish) "Nearby in sanctuary" else "قريب في الملاذ"
                                                },
                                                color = if (isCatchUp) SignalAmber else MutedSilver,
                                                fontSize = 11.sp,
                                                fontFamily = Outfit,
                                                fontWeight = if (isCatchUp) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                        Text(
                                            text = if (isCatchUp) {
                                                if (isEnglish) "Join (${partner.remainingMinutes}m)" else "انضمام (${partner.remainingMinutes}د)"
                                            } else {
                                                if (isEnglish) "Tap to Link" else "اضغط للربط"
                                            },
                                            color = if (isCatchUp) SignalAmber else GlowGreen,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Direct Instant Catch-Up Option (Solves one device already started offline challenge)
                        val activePartner = discoveredPartners.firstOrNull { it.hasActiveSession && it.remainingMinutes < 60 }
                        val catchUpDuration = activePartner?.remainingMinutes ?: 60
                        val catchUpPartnerName = activePartner?.name ?: (discoveredPartners.firstOrNull()?.name ?: "Partner")

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianSurface, RoundedCornerShape(10.dp))
                                .border(1.dp, if (activePartner != null) SignalAmber.copy(alpha = 0.5f) else GlowGreen.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .clickable {
                                    triggerHighVibration(context)
                                    PartnerSessionPrefs.startPendingSession(
                                        context = context,
                                        partnerName = catchUpPartnerName,
                                        durationMinutes = catchUpDuration
                                    )
                                    PartnerPairingManager.launchOfflineChallenge(
                                        context = context,
                                        partnerName = catchUpPartnerName,
                                        sanctuaryName = "",
                                        durationMinutes = catchUpDuration
                                    )
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("⚡", fontSize = 16.sp)
                                Column {
                                    Text(
                                        text = if (activePartner != null) {
                                            if (isEnglish) "⚡ Circle Active (${catchUpDuration}m left): Tap to Catch Up"
                                            else "⚡ بدأت الجلسة (متبقي ${catchUpDuration}د): اضغط للالتحاق"
                                        } else {
                                            if (isEnglish) "⚡ Group Already Started? Tap to Join"
                                            else "⚡ بدأت الجلسة؟ اضغط للانضمام فوراً"
                                        },
                                        color = if (activePartner != null) SignalAmber else GlowGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = Outfit
                                    )
                                    Text(
                                        text = if (activePartner != null) {
                                            if (isEnglish) "Synchronizes timer to count down from ${catchUpDuration}m in lockstep"
                                            else "يزامن العداد ليعد تنازلياً من ${catchUpDuration} دقيقة مع الرفاق"
                                        } else {
                                            if (isEnglish) "Instantly joins the 1-hour offline focus shield together"
                                            else "ينضم لدرع التركيز المشترك لساعة واحدة معاً فوراً"
                                        },
                                        color = MutedSilver,
                                        fontSize = 10.sp,
                                        fontFamily = Outfit
                                    )
                                }
                            }
                        }

                        // Refresh button with instant tactile animation and robust radio cycling
                        OutlinedButton(
                            onClick = {
                                if (!isRefreshing) {
                                    isRefreshing = true
                                    PartnerPairingManager.refreshScan(context)
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(1200L)
                                        isRefreshing = false
                                    }
                                }
                            },
                            enabled = !isRefreshing,
                            border = BorderStroke(1.dp, if (isRefreshing) GlowGreen.copy(alpha = 0.5f) else ObsidianBorder),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.8.dp,
                                    color = GlowGreen
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEnglish) "Scanning..." else "جارِ البحث...",
                                    color = GlowGreen,
                                    fontSize = 12.sp,
                                    fontFamily = Outfit,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = if (isEnglish) "🔄 Refresh" else "🔄 تحديث",
                                    color = GlowGreen,
                                    fontSize = 12.sp,
                                    fontFamily = Outfit,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

    // 1-Hour Confirmation Pop-up: Clean, Concise & Perfectly Aligned
    if (showConfirmShieldDialog && pairedPartner != null) {
        val partner = pairedPartner!!
        val direction = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            AlertDialog(
                onDismissRequest = { showConfirmShieldDialog = false },
                containerColor = ObsidianSurface,
                titleContentColor = WarmOffWhite,
                textContentColor = MutedSilver,
                title = {
                    val isCatchUp = partner.hasActiveSession && partner.remainingMinutes < 60
                    Text(
                        text = if (isCatchUp) {
                            if (isEnglish) "🛡️ Catch Up with Group" else "🛡️ الالتحاق بجلسة الرفاق"
                        } else {
                            if (isEnglish) "🛡️ Confirm Group Offline Session" else "🛡️ تأكيد جلسة الأوفلاين مع الرفاق"
                        },
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    val sessionDuration = if (partner.hasActiveSession && partner.remainingMinutes < 60) partner.remainingMinutes else 60
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (isEnglish) {
                                if (sessionDuration < 60) {
                                    "Group: ${partner.name}\n\nThis circle started earlier. You are catching up with ${sessionDuration} minutes remaining to complete together."
                                } else {
                                    "Group: ${partner.name}\n\nStarting duration is 1 hour. We will notify you after 1 hour if you want to extend."
                                }
                            } else {
                                if (sessionDuration < 60) {
                                    "الرفاق: ${partner.name}\n\nبدأت هذه الجلسة مسبقاً. ستلتحق بالرفاق مع متبقي ${sessionDuration} دقيقة لتنتهوا معاً."
                                } else {
                                    "الرفاق: ${partner.name}\n\nالمدة المبدئية ساعة واحدة. سنرسل لك إشعاراً بعد انتهاء الساعة للتمديد إن أردت."
                                }
                            },
                            fontFamily = Outfit,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = WarmOffWhite
                        )
                    }
                },
                confirmButton = {
                    val sessionDuration = if (partner.hasActiveSession && partner.remainingMinutes < 60) partner.remainingMinutes else 60
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                triggerHighVibration(context)
                                showConfirmShieldDialog = false
                                // 1. Save pending session in Analog Anchor awaiting shield activation
                                PartnerSessionPrefs.startPendingSession(
                                    context = context,
                                    partnerName = partner.name,
                                    durationMinutes = sessionDuration
                                )
                                // 2. Launch Offline Challenge directly to shield_permission
                                PartnerPairingManager.launchOfflineChallenge(
                                    context = context,
                                    partnerName = partner.name,
                                    sanctuaryName = "",
                                    durationMinutes = sessionDuration
                                )
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (sessionDuration < 60) SignalAmber else GlowGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = if (sessionDuration < 60) {
                                    if (isEnglish) "🛡️ Catch Up & Start (${sessionDuration}m)" else "🛡️ الالتحاق والبدء (${sessionDuration} دقيقة)"
                                } else {
                                    if (isEnglish) "🛡️ Confirm & Start (1 Hour)" else "🛡️ تأكيد والبدء (ساعة واحدة)"
                                },
                                color = ObsidianBg,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Outfit,
                                fontSize = 14.sp
                            )
                        }

                        TextButton(
                            onClick = { showConfirmShieldDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isEnglish) "Cancel" else "إلغاء",
                                color = MutedSilver,
                                fontFamily = Outfit,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                dismissButton = null
            )
        }
    }
}

@Composable
private fun RadarPulseAnimation(isScanning: Boolean, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseAnim1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )
    val pulseAnim2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2"
    )

    Box(modifier = modifier.size(130.dp), contentAlignment = Alignment.Center) {
        if (isScanning) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxRadius = size.minDimension / 2f
                val centerPt = this.center

                // Wave 1
                val r1 = maxRadius * pulseAnim1
                val alpha1 = ((1f - pulseAnim1) * 0.4f).coerceIn(0f, 1f)
                drawCircle(
                    color = GlowGreen.copy(alpha = alpha1),
                    radius = r1,
                    center = centerPt
                )
                drawCircle(
                    color = GlowGreen.copy(alpha = (alpha1 * 1.6f).coerceIn(0f, 1f)),
                    radius = r1,
                    center = centerPt,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Wave 2
                val r2 = maxRadius * pulseAnim2
                val alpha2 = ((1f - pulseAnim2) * 0.4f).coerceIn(0f, 1f)
                drawCircle(
                    color = GlowGreen.copy(alpha = alpha2),
                    radius = r2,
                    center = centerPt
                )
                drawCircle(
                    color = GlowGreen.copy(alpha = (alpha2 * 1.6f).coerceIn(0f, 1f)),
                    radius = r2,
                    center = centerPt,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Central beacon hub
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(GlowGreen.copy(alpha = 0.16f), CircleShape)
                .border(1.5.dp, GlowGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint = GlowGreen,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

internal fun triggerHighVibration(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val vibrator = vibratorManager?.defaultVibrator
            if (vibrator?.hasVibrator() == true) {
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 250, 120, 350),
                    intArrayOf(0, 255, 0, 255),
                    -1
                )
                vibrator.vibrate(effect)
            }
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(
                        longArrayOf(0, 250, 120, 350),
                        -1
                    )
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 250, 120, 350), -1)
                }
            }
        }
    } catch (_: Exception) {
    }
}
