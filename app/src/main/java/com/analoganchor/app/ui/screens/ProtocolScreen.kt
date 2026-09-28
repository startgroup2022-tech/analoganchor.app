package com.analoganchor.app.ui.screens

import android.media.RingtoneManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analoganchor.app.ui.components.GlassCard
import com.analoganchor.app.ui.components.TactileToken
import com.analoganchor.app.ui.components.PremiumButton
import com.analoganchor.app.ui.theme.*
import com.analoganchor.app.viewmodel.AnchorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProtocolScreen(
    viewModel: AnchorViewModel,
    onNavigateToDashboard: () -> Unit
) {
    val context = LocalContext.current
    val savedTagId by viewModel.nfcTagId.collectAsState()
    val isSessionActive by viewModel.isSessionActive.collectAsState()
    val timeLeftSeconds by viewModel.sessionTimeLeftSeconds.collectAsState()
    val totalDurationMinutes by viewModel.sessionTotalDurationMinutes.collectAsState()
    val sessionVoidedMessage by viewModel.sessionVoidedMessage.collectAsState()

    var stageState by remember { mutableStateOf("TAP_1") } // TAP_1, ACTIVE_COUNTDOWN, CLOSURE_SCREEN, CAMERA_VIEWPORT, PHOTO_PREVIEW, COMPLETED
    var selectedDuration by remember { mutableStateOf(5) }
    var userNote by remember { mutableStateOf("") }
    var selectedPrivacyOption by remember { mutableStateOf("A") } // A = Save privately, C = Session archive

    // Simulated camera capture state
    var isShutterFlashing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Immersive warning pulse animation for countdown session
    val infiniteTransition = rememberInfiniteTransition(label = "countdown_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // If session voided externally (e.g., Gyro sensor motion detected), automatically react and alert user
    LaunchedEffect(sessionVoidedMessage) {
        if (sessionVoidedMessage != null) {
            Toast.makeText(context, sessionVoidedMessage, Toast.LENGTH_LONG).show()
            onNavigateToDashboard()
        }
    }

    LaunchedEffect(stageState) {
        if (stageState == "TAP_1") {
            viewModel.nfcScannedEvent.collect { scannedId ->
                if (savedTagId.isEmpty()) {
                    Toast.makeText(context, "⚠️ No Eraser registered. Link your Whiteboard Eraser in Settings first!", Toast.LENGTH_LONG).show()
                } else if (scannedId == savedTagId) {
                    viewModel.startFocusSession(selectedDuration)
                    stageState = "ACTIVE_COUNTDOWN"
                    Toast.makeText(context, "Eraser NFC Validated. Session started.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "❌ Unrecognized NFC Tag. Use the registered Eraser.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Sync screen changes to VM states
    LaunchedEffect(isSessionActive, timeLeftSeconds) {
        if (isSessionActive) {
            stageState = "ACTIVE_COUNTDOWN"
        } else if (stageState == "ACTIVE_COUNTDOWN" && timeLeftSeconds == 0) {
            stageState = "CLOSURE_SCREEN"
            try {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context, alarmUri)
                ringtone.play()
            } catch (e: Exception) {
                android.util.Log.e("ProtocolScreen", "Could not play alarm: ${e.message}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        
        // EXIT / HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ERASER PROTOCOL",
                color = MutedSilver,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )

            // Let client abort before session starts
            if (stageState == "TAP_1" || stageState == "COMPLETED") {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(ObsidianSurface, RoundedCornerShape(17.dp))
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(17.dp))
                        .clickable { onNavigateToDashboard() }
                        .testTag("exit_protocol_setup"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        color = MutedSilver,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (stageState == "ACTIVE_COUNTDOWN") {
                // ACTIVE STOP
                PremiumButton(
                    text = "ABORT",
                    onClick = {
                        viewModel.failFocusSession("Session aborted by user request.")
                        Toast.makeText(context, "Protocol aborted. Penalty applied.", Toast.LENGTH_LONG).show()
                        onNavigateToDashboard()
                    },
                    gradientColors = listOf(Color.Transparent, Color.Transparent),
                    contentColor = SignalAmber,
                    disabledColor = Color.Transparent,
                    height = 36.dp,
                    modifier = Modifier
                        .width(76.dp)
                        .border(1.dp, SignalAmber.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                )
            } else {
                Spacer(modifier = Modifier.width(34.dp))
            }
        }

        // PRINCIPAL SESSION PANEL
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when (stageState) {
                "TAP_1" -> {
                    // STAGE 1: CHOOSING DURATION & TAP 1 COMMIT
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Whiteboard Desk Anchored Link",
                            color = WarmOffWhite,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Outfit
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Set the phone at the NFC zone on the Glass Organizer. Touch phone to the NFC-enabled physical eraser to start.",
                            color = MutedSilver,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = Outfit,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Segmented control choice (5, 10, 15 minutes)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianSurface, RoundedCornerShape(12.dp))
                                .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                                .padding(5.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf(5, 10, 15).forEach { min ->
                                val active = selectedDuration == min
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = if (active) GlowGreen.copy(alpha = 0.15f) else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (active) GlowGreen else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedDuration = min }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$min MIN",
                                        color = if (active) GlowGreen else MutedSilver,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        GlassCard(borderColor = GlowGreen) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                TactileToken(
                                    emoji = "📡", 
                                    size = 80.dp, 
                                    emojiSize = 44.sp, 
                                    borderColor = GlowGreen,
                                    glowColor = GlowGreen.copy(alpha = 0.25f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "ERASER LINK - ENGAGE RITUAL",
                                    color = WarmOffWhite,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Touch phone to the physical Whiteboard Eraser to link the session and start the clock.",
                                    color = MutedSilver,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    fontFamily = Outfit
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                PremiumButton(
                                    text = "⚡ SIMULATE ERASER TAP (SOFT MODE)",
                                    onClick = {
                                        viewModel.startFocusSession(selectedDuration)
                                        stageState = "ACTIVE_COUNTDOWN"
                                        Toast.makeText(context, "Eraser NFC Simulated. Session started.", Toast.LENGTH_SHORT).show()
                                    },
                                    gradientColors = listOf(RefinedGreen, GlowGreen),
                                    contentColor = ObsidianBg,
                                    height = 46.dp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                "ACTIVE_COUNTDOWN" -> {
                    // STAGE 2: COUNTDOWN IN PROGRESS (PHONE MUST REMAIN FLAT AND STILL)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TactileToken(
                            emoji = "🌀", 
                            size = 84.dp, 
                            emojiSize = 44.sp, 
                            borderColor = GlowGreen,
                            glowColor = GlowGreen.copy(alpha = 0.3f),
                            modifier = Modifier.scale(pulseScale)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "ERASER SESSION ENGAGED",
                            color = GlowGreen,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "🚭 CONTRACT IN PROGRESS. FOCUS ON THE WORKPLACE UNTIL COMPLETE.",
                            color = MutedSilver,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = Outfit
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        val minutesLeft = timeLeftSeconds / 60
                        val secondsLeft = timeLeftSeconds % 60
                        val timerDisplay = String.format("%02d:%02d", minutesLeft, secondsLeft)

                        Box(
                            modifier = Modifier
                                .background(ObsidianBg, RoundedCornerShape(16.dp))
                                .border(1.5.dp, GlowGreen.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 40.dp, vertical = 20.dp)
                        ) {
                            Text(
                                text = timerDisplay,
                                color = WarmOffWhite,
                                fontSize = 68.sp,
                                fontWeight = FontWeight.Light,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SignalAmber.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .border(1.dp, SignalAmber.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "🚨 SENSOR ACTIVE: Rest the phone flat on your desk. Any physical orientation change or movement void-locks the session.",
                                color = SignalAmber,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                fontFamily = Outfit
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PremiumButton(
                                text = "ACCELERATE TIME",
                                onClick = {
                                    viewModel.startFocusSession(0)
                                    try {
                                        val field = viewModel::class.java.getDeclaredField("_sessionTimeLeftSeconds")
                                        field.isAccessible = true
                                        val flow = field.get(viewModel) as MutableStateFlow<Int>
                                        flow.value = 3
                                    } catch (e: Exception) {}
                                    Toast.makeText(context, "Timer accelerated (3s remaining).", Toast.LENGTH_SHORT).show()
                                },
                                gradientColors = listOf(DeepPanel, ObsidianSurface),
                                contentColor = WarmOffWhite,
                                height = 46.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                                    .testTag("accelerate_timer_btn")
                            )

                            PremiumButton(
                                text = "SIMULATE TILT",
                                onClick = {
                                    viewModel.failFocusSession("Session voided. Physical movement occurred")
                                },
                                gradientColors = listOf(DeepPanel, ObsidianBg),
                                contentColor = SignalAmber,
                                height = 46.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, SignalAmber.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .testTag("trigger_gyro_movement_btn")
                            )
                        }
                    }
                }

                "CLOSURE_SCREEN" -> {
                    // STAGE 3: TIMER ENDED. INSTRUCTION AND USER NOTE TAKING
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TactileToken(
                            emoji = "📝", 
                            size = 72.dp, 
                            emojiSize = 36.sp, 
                            borderColor = GlowGreen,
                            glowColor = GlowGreen.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Session Complete",
                            color = GlowGreen,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Outfit
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "“Erase the board. Leave one unfinished sentence or one next action. Then take a photo.”",
                            color = WarmOffWhite,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = Outfit,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // User Note Text Input
                        OutlinedTextField(
                            value = userNote,
                            onValueChange = { userNote = it },
                            placeholder = { Text("Optional: Enter your re-entry sentence or next action here...", color = MutedSilver, fontSize = 13.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = WarmOffWhite,
                                unfocusedTextColor = WarmOffWhite,
                                focusedContainerColor = ObsidianSurface,
                                unfocusedContainerColor = ObsidianSurface,
                                focusedBorderColor = GlowGreen,
                                unfocusedBorderColor = ObsidianBorder
                            )
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PremiumButton(
                                text = "📷 TAKE PHOTO",
                                onClick = {
                                    stageState = "CAMERA_VIEWPORT"
                                },
                                gradientColors = listOf(RefinedGreen, GlowGreen),
                                height = 50.dp,
                                modifier = Modifier
                                    .weight(1.2f)
                            )

                            PremiumButton(
                                text = "SKIP PHOTO",
                                onClick = {
                                    viewModel.completeFocusSession(photoStatus = "skipped", userNote = userNote.trim().ifEmpty { null })
                                    stageState = "COMPLETED"
                                    val pts = when (selectedDuration) { 10 -> 35; 15 -> 45; else -> 25 }
                                    Toast.makeText(context, "Completed! +$pts Reserve Points.", Toast.LENGTH_LONG).show()
                                },
                                gradientColors = listOf(DeepPanel, ObsidianSurface),
                                contentColor = WarmOffWhite,
                                height = 50.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }

                "CAMERA_VIEWPORT" -> {
                    // STAGE 4: HIGH-FIDELITY IMMERSIVE MOCK CAMERA VIEWPORT
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black, RoundedCornerShape(16.dp))
                            .border(1.5.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        // Drawing Grid Lines & Brackets
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeColor = Color.White.copy(alpha = 0.15f)
                            val bracketColor = GlowGreen.copy(alpha = 0.7f)
                            
                            // Vertical Grid Lines
                            drawLine(strokeColor, start = androidx.compose.ui.geometry.Offset(size.width / 3f, 0f), end = androidx.compose.ui.geometry.Offset(size.width / 3f, size.height), strokeWidth = 1.dp.toPx())
                            drawLine(strokeColor, start = androidx.compose.ui.geometry.Offset(size.width * 2f / 3f, 0f), end = androidx.compose.ui.geometry.Offset(size.width * 2f / 3f, size.height), strokeWidth = 1.dp.toPx())
                            
                            // Horizontal Grid Lines
                            drawLine(strokeColor, start = androidx.compose.ui.geometry.Offset(0f, size.height / 3f), end = androidx.compose.ui.geometry.Offset(size.width, size.height / 3f), strokeWidth = 1.dp.toPx())
                            drawLine(strokeColor, start = androidx.compose.ui.geometry.Offset(0f, size.height * 2f / 3f), end = androidx.compose.ui.geometry.Offset(size.width, size.height * 2f / 3f), strokeWidth = 1.dp.toPx())

                            // Focusing brackets in the center
                            val cx = size.width / 2
                            val cy = size.height / 2
                            val sizeBox = 40.dp.toPx()
                            val len = 10.dp.toPx()

                            // Top Left Corner
                            drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(cx - sizeBox, cy - sizeBox), end = androidx.compose.ui.geometry.Offset(cx - sizeBox + len, cy - sizeBox), strokeWidth = 2.dp.toPx())
                            drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(cx - sizeBox, cy - sizeBox), end = androidx.compose.ui.geometry.Offset(cx - sizeBox, cy - sizeBox + len), strokeWidth = 2.dp.toPx())
                            
                            // Top Right Corner
                            drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(cx + sizeBox, cy - sizeBox), end = androidx.compose.ui.geometry.Offset(cx + sizeBox - len, cy - sizeBox), strokeWidth = 2.dp.toPx())
                            drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(cx + sizeBox, cy - sizeBox), end = androidx.compose.ui.geometry.Offset(cx + sizeBox, cy - sizeBox + len), strokeWidth = 2.dp.toPx())
                            
                            // Bottom Left Corner
                            drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(cx - sizeBox, cy + sizeBox), end = androidx.compose.ui.geometry.Offset(cx - sizeBox + len, cy + sizeBox), strokeWidth = 2.dp.toPx())
                            drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(cx - sizeBox, cy + sizeBox), end = androidx.compose.ui.geometry.Offset(cx - sizeBox, cy + sizeBox - len), strokeWidth = 2.dp.toPx())
                            
                            // Bottom Right Corner
                            drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(cx + sizeBox, cy + sizeBox), end = androidx.compose.ui.geometry.Offset(cx + sizeBox - len, cy + sizeBox), strokeWidth = 2.dp.toPx())
                            drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(cx + sizeBox, cy + sizeBox), end = androidx.compose.ui.geometry.Offset(cx + sizeBox, cy + sizeBox - len), strokeWidth = 2.dp.toPx())
                        }

                        // Text Overlay in Viewport (representing physical whiteboard)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "WHITEBOARD",
                                color = Color.White.copy(alpha = 0.25f),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            // Unfinished Sentence mockup representation on the glassboard
                            Box(
                                modifier = Modifier
                                    .border(BorderStroke(1.2.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.03f))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = if (userNote.trim().isNotEmpty()) "“${userNote}...”" else "“Next action is to implement the API...”",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Viewport Top HUD Settings
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("HDR", color = GlowGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text("1x", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text("GRID ON", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }

                        // Shutter controls bottom HUD
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(bottom = 20.dp, top = 20.dp, start = 24.dp, end = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cancel/Exit camera Button
                            Text(
                                text = "CANCEL",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .clickable { stageState = "CLOSURE_SCREEN" }
                                    .padding(8.dp)
                            )

                            // Circular Shutter Trigger Button
                            Box(
                                modifier = Modifier
                                    .size(66.dp)
                                    .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                                    .border(4.dp, Color.Black, androidx.compose.foundation.shape.CircleShape)
                                    .border(5.5.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                                    .clickable {
                                        isShutterFlashing = true
                                        coroutineScope.launch {
                                            delay(150)
                                            isShutterFlashing = false
                                            stageState = "PHOTO_PREVIEW"
                                        }
                                    }
                            )

                            // Zoom mock selector
                            Text(
                                text = "PHOTO",
                                color = GlowGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        // Camera flash simulated overlay
                        if (isShutterFlashing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White)
                            )
                        }
                    }
                }

                "PHOTO_PREVIEW" -> {
                    // STAGE 5: PHOTO PREVIEW AND PRIVACY ACTION CHOOSER
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Whiteboard Image captured",
                            color = GlowGreen,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Outfit
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Set privacy preferences before sealing.",
                            color = MutedSilver,
                            fontSize = 13.sp,
                            fontFamily = Outfit
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Polaroid image mock frame
                        Box(
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                                .width(220.dp)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .background(Color(0xFF1B2321), RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
                                        Text("🖊️", fontSize = 28.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = if (userNote.trim().isNotEmpty()) "“${userNote}...”" else "“Leaving board next action...”",
                                            color = WarmOffWhite,
                                            fontSize = 13.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "LINKED ERASER PHOTO",
                                    color = Color(0xFF1A1A1A),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Immersive Privacy Option choice selectors
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Option A
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (selectedPrivacyOption == "A") GlowGreen.copy(alpha = 0.1f) else ObsidianSurface, RoundedCornerShape(10.dp))
                                    .border(1.dp, if (selectedPrivacyOption == "A") GlowGreen else ObsidianBorder, RoundedCornerShape(10.dp))
                                    .clickable { selectedPrivacyOption = "A" }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text("🛡️", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Save privately on this device", color = WarmOffWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = Outfit)
                                        Text("Photo kept locally on device, bypassing remote syncs.", color = MutedSilver, fontSize = 13.sp, fontFamily = Outfit)
                                    }
                                }
                                if (selectedPrivacyOption == "A") {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Active", tint = GlowGreen, modifier = Modifier.size(20.dp))
                                }
                            }

                            // Option B
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (selectedPrivacyOption == "B") GlowGreen.copy(alpha = 0.1f) else ObsidianSurface, RoundedCornerShape(10.dp))
                                    .border(1.dp, if (selectedPrivacyOption == "B") GlowGreen else ObsidianBorder, RoundedCornerShape(10.dp))
                                    .clickable { selectedPrivacyOption = "B" }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text("❌", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Continue without saving photo", color = WarmOffWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = Outfit)
                                        Text("Proceed directly, skipping any file storage.", color = MutedSilver, fontSize = 13.sp, fontFamily = Outfit)
                                    }
                                }
                                if (selectedPrivacyOption == "B") {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Active", tint = GlowGreen, modifier = Modifier.size(20.dp))
                                }
                            }

                            // Option C
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (selectedPrivacyOption == "C") GlowGreen.copy(alpha = 0.1f) else ObsidianSurface, RoundedCornerShape(10.dp))
                                    .border(1.dp, if (selectedPrivacyOption == "C") GlowGreen else ObsidianBorder, RoundedCornerShape(10.dp))
                                    .clickable { selectedPrivacyOption = "C" }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text("🗄️", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Save to session archive", color = WarmOffWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = Outfit)
                                        Text("Records to chronological log history ledger.", color = MutedSilver, fontSize = 13.sp, fontFamily = Outfit)
                                    }
                                }
                                if (selectedPrivacyOption == "C") {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Active", tint = GlowGreen, modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PremiumButton(
                                text = "🔄 RETAKE",
                                onClick = { stageState = "CAMERA_VIEWPORT" },
                                gradientColors = listOf(DeepPanel, ObsidianSurface),
                                contentColor = WarmOffWhite,
                                height = 48.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                            )

                            PremiumButton(
                                text = "COMPLETE & SEAL",
                                onClick = {
                                    val status = when (selectedPrivacyOption) {
                                        "A" -> "privately_saved"
                                        "B" -> "skipped"
                                        else -> "archived"
                                    }
                                    viewModel.completeFocusSession(photoStatus = status, userNote = userNote.trim().ifEmpty { null })
                                    stageState = "COMPLETED"
                                    val pts = when (selectedDuration) { 10 -> 35; 15 -> 45; else -> 25 }
                                    Toast.makeText(context, "Completed! +$pts Reserve Points.", Toast.LENGTH_LONG).show()
                                },
                                gradientColors = listOf(GlowGreen, RefinedGreen),
                                height = 48.dp,
                                modifier = Modifier
                                    .weight(1.3f)
                            )
                        }
                    }
                }

                "COMPLETED" -> {
                    // STAGE 6: COMPLETED RECEIPT SCREEN SHOWING STORAGE METADATA
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TactileToken(
                            emoji = "📜", 
                            size = 76.dp, 
                            emojiSize = 38.sp, 
                            borderColor = GlowGreen,
                            glowColor = GlowGreen.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Session Log Sealed",
                            color = GlowGreen,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Outfit
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Daily anchor contract written successfully.",
                            color = WarmSilverText,
                            fontSize = 13.sp,
                            fontFamily = Outfit
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // High-tech premium receipt card displaying complete metadata!
                        GlassCard(borderColor = GlowGreen.copy(alpha = 0.5f)) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "RITUAL METADATA RECEIPTS",
                                    color = GlowGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )

                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ObsidianBorder))

                                // Start Time
                                val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                val startDateStr = sdfTime.format(Date(viewModel.sessionStartTime))
                                ReceiptRow("Start Time", startDateStr)

                                // End Time
                                val endDateStr = sdfTime.format(Date())
                                ReceiptRow("End Time", endDateStr)

                                // Duration
                                ReceiptRow("Duration", "${totalDurationMinutes} minutes")

                                // NFC Eraser Tag ID
                                ReceiptRow("Eraser NFC", savedTagId.ifEmpty { "ANCHOR_NFC_09X88" })

                                // Photo State
                                val photoLabel = when (selectedPrivacyOption) {
                                    "A" -> "Saved Privately"
                                    "C" -> "Archived to Ledger"
                                    else -> "Skipped"
                                }
                                ReceiptRow("Photo Ledger", photoLabel)

                                // Optional user note
                                if (userNote.trim().isNotEmpty()) {
                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ObsidianBorder))
                                    Text(
                                        text = "RE-ENTRY TASK/NOTE:",
                                        color = MutedSilver,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "“${userNote.trim()}”",
                                        color = WarmOffWhite,
                                        fontSize = 13.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        lineHeight = 18.sp,
                                        fontFamily = Outfit
                                    )
                                }

                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ObsidianBorder))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("CREDITS REWARD", color = WarmOffWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = Outfit)
                                    val pts = when (selectedDuration) { 10 -> 35; 15 -> 45; else -> 25 }
                                    Text("+$pts PTS", color = GlowGreen, fontWeight = FontWeight.Black, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        PremiumButton(
                            text = "RETURN TO DASHBOARD",
                            onClick = {
                                onNavigateToDashboard()
                            },
                            gradientColors = listOf(RefinedGreen, GlowGreen),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("simulate_tap2_btn")
                        )
                    }
                }
            }
        }

        // FOOTER FOOTNOTE
        Text(
            text = "Digital discipline made absolute. Powering focus in Bahrain.",
            color = MutedSilver,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = MutedSilver, fontSize = 12.sp, fontFamily = Outfit)
        Text(text = value, color = WarmOffWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}
