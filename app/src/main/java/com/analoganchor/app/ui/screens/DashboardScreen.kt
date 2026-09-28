package com.analoganchor.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.widget.Toast
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.analoganchor.app.data.preferences.CompactnessOption
import com.analoganchor.app.data.preferences.UserPreferences
import com.analoganchor.app.ui.screens.settings.CompactnessSelector
import com.analoganchor.app.ui.screens.settings.HUDAppearanceSettings
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import com.analoganchor.app.R
import com.analoganchor.app.data.SessionLog
import com.analoganchor.app.viewmodel.AnchorViewModel
import com.analoganchor.app.viewmodel.MapboxPlaceCandidate
import com.analoganchor.app.ui.theme.*
import com.analoganchor.app.ui.components.GlassCard
import com.analoganchor.app.ui.components.TactileToken
import com.analoganchor.app.ui.components.PremiumButton
import com.analoganchor.app.ui.components.PartnerPairingBottomSheet
import com.analoganchor.app.ui.components.triggerHighVibration
import com.analoganchor.app.data.preferences.PartnerSessionPrefs
import com.analoganchor.app.util.PartnerPairingManager
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
import com.analoganchor.app.util.ProofFileStore
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.ui.draw.scale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.analoganchor.app.util.AppBlockIntegration
import com.analoganchor.app.util.DayGuardIntegration
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import android.location.LocationManager
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val FAJR_WAKE_AYAH = "تَتَجَافَىٰ جُنُوبُهُمْ عَنِ الْمَضَاجِعِ"
private val FAJR_WAKE_AYAHS = listOf(
    "تَتَجَافَىٰ جُنُوبُهُمْ عَنِ الْمَضَاجِعِ",
    "وَقُرْآنَ الْفَجْرِ ۖ إِنَّ قُرْآنَ الْفَجْرِ كَانَ مَشْهُودًا"
)
private val FAJR_AYAH_MARKS_REGEX = Regex("\\p{Mn}+")
private val FAJR_AYAH_TATWEEL_REGEX = Regex("\\u0640+")
private val FAJR_AYAH_ALEF_REGEX = Regex("[\\u0622\\u0623\\u0625\\u0671]")
private val FAJR_AYAH_SPACING_REGEX = Regex("\\s+")

private fun hasUsableGps(lat: Double, lng: Double): Boolean {
    return kotlin.math.abs(lat) > 0.00001 || kotlin.math.abs(lng) > 0.00001
}

private var activeToastHandler: android.os.Handler? = null
private var activeToastRunnable: Runnable? = null

fun showExtendedToast(context: Context, message: String) {
    if (activeToastHandler == null) {
        activeToastHandler = android.os.Handler(android.os.Looper.getMainLooper())
    }
    activeToastRunnable?.let { activeToastHandler?.removeCallbacks(it) }

    Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    val nextToast = Runnable {
        try {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        } catch (_: Exception) { }
    }
    activeToastRunnable = nextToast
    activeToastHandler?.postDelayed(nextToast, 2700L)
}

@Composable
private fun FocusSessionSystemUi(active: Boolean) {
    val view = LocalView.current

    DisposableEffect(active) {
        val window = (view.context as? Activity)?.window
        if (window != null && active) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowInsetsControllerCompat(window, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
        }
    }
}

@Composable
private fun AnchorFocusSessionFullscreen(
    timeLeftSeconds: Int,
    chosenDuration: Int,
    pickupCount: Int,
    isFaceDown: Boolean,
    warning: String?,
    dndActive: Boolean,
    dndAccessGranted: Boolean,
    deviceAdminActive: Boolean,
    isEnglish: Boolean = true
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val totalSeconds = if (AnchorViewModel.ANCHOR_TAP_ONE_TEST_MODE) {
        AnchorViewModel.ANCHOR_TAP_ONE_TEST_DURATION_SEC
    } else {
        chosenDuration * 60
    }.coerceAtLeast(1)
    val progress = (1f - (timeLeftSeconds.toFloat() / totalSeconds.toFloat())).coerceIn(0f, 1f)
    val minutesLeft = ((timeLeftSeconds + 59) / 60).coerceAtLeast(0)
    val clock = String.format(java.util.Locale.US, "%02d:%02d", timeLeftSeconds / 60, timeLeftSeconds % 60)
    
    val statusText = when {
        pickupCount > AnchorViewModel.FOCUS_PICKUP_LIMIT -> "RESTART REQUIRED"
        !dndAccessGranted -> "DND ACCESS NEEDED"
        isFaceDown -> "NFC ZONE LOCKED"
        pickupCount > 0 -> "PICKUP ($pickupCount/${AnchorViewModel.FOCUS_PICKUP_LIMIT})"
        else -> "PLACE AT NFC ZONE"
    }
    val statusColor = when {
        pickupCount >= AnchorViewModel.FOCUS_PICKUP_LIMIT -> SignalAmber
        isFaceDown -> GlowGreen
        pickupCount > 0 -> SignalAmber
        else -> MutedSilver
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        ObsidianBg,
                        Color(0xFF000A12),
                        Color.Black
                    ),
                    radius = 1600f
                )
            )
            .padding(
                horizontal = if (isLandscape) 32.dp else 24.dp,
                vertical = if (isLandscape) 16.dp else 32.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLandscape) {
            // ---------------- HORIZONTAL / DESK STAND MODE ----------------
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 860.dp),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Stage: Circular Dial
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    val dialSize = 230.dp
                    Box(
                        modifier = Modifier.size(dialSize),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 7.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            val radius = size.minDimension / 2f - stroke.width * 2
                            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)

                            // Smooth background track
                            drawCircle(
                                color = ObsidianBorder,
                                radius = radius,
                                center = center,
                                style = stroke
                            )

                            // Animated wavy foreground track
                            if (progress > 0f) {
                                val path = androidx.compose.ui.graphics.Path()
                                val numWaves = 36
                                val waveAmplitude = 7.dp.toPx()
                                
                                val sweepAngle = 360f * progress
                                val startAngle = -90f
                                
                                val points = (sweepAngle).toInt() * 2
                                for (i in 0..points) {
                                    val currentAngleDeg = startAngle + (i.toFloat() / Math.max(1, points)) * sweepAngle
                                    val currentAngleRad = Math.toRadians(currentAngleDeg.toDouble())
                                    
                                    val waveOffset = kotlin.math.sin(currentAngleRad * numWaves) * waveAmplitude
                                    val currentRadius = radius + waveOffset
                                    
                                    val px = (center.x + currentRadius * kotlin.math.cos(currentAngleRad)).toFloat()
                                    val py = (center.y + currentRadius * kotlin.math.sin(currentAngleRad)).toFloat()
                                    
                                    if (i == 0) {
                                        path.moveTo(px, py)
                                    } else {
                                        path.lineTo(px, py)
                                    }
                                }
                                
                                drawPath(
                                    path = path,
                                    color = GlowGreen,
                                    style = stroke
                                )
                            }
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = clock,
                                color = WarmOffWhite,
                                fontSize = 52.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = Outfit,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${chosenDuration} MIN SESSION",
                                color = GlowGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // Right HUD Panel
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    // Header Badges Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .background(GlowGreen.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                .border(BorderStroke(1.dp, GlowGreen.copy(alpha = 0.40f)), RoundedCornerShape(16.dp))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(GlowGreen, CircleShape)
                            )
                            Text(
                                text = "ANCHOR ACTIVE",
                                color = GlowGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .background(statusColor.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                                .border(BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)), RoundedCornerShape(16.dp))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Text(
                        text = if (isEnglish) "Thinking on Paper Ritual" else "التدبر اليومي",
                        color = WarmOffWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Outfit
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // DND Shield Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, GlowGreen.copy(alpha = 0.28f)), RoundedCornerShape(12.dp))
                            .background(ObsidianSurface.copy(alpha = 0.70f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("🛡️", fontSize = 20.sp)
                            Column {
                                Text(
                                    text = if (dndActive) "DO NOT DISTURB ACTIVE" else "FOCUS SHIELD ENGAGED",
                                    color = GlowGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "All notifications & interruptions are silenced.",
                                    color = MutedSilver,
                                    fontSize = 11.sp,
                                    fontFamily = Outfit
                                )
                            }
                        }
                    }

                    if (warning != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠️ $warning",
                            color = SignalAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Externalize your thoughts on paper. Screen off, mind clear.",
                        color = MutedSilver,
                        fontSize = 12.sp,
                        fontFamily = Outfit,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            // ---------------- VERTICAL / PORTRAIT MODE ----------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                // Header Active Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .background(GlowGreen.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                        .border(BorderStroke(1.dp, GlowGreen.copy(alpha = 0.40f)), RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(GlowGreen, CircleShape)
                    )
                    Text(
                        text = "ANCHOR SESSION ACTIVE",
                        color = GlowGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp
                    )
                }

                Text(
                    text = if (isEnglish) "Thinking on Paper Ritual" else "التدبر اليومي",
                    color = WarmOffWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Outfit
                )

                Box(
                    modifier = Modifier.size(250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 8.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        val radius = size.minDimension / 2f - stroke.width * 2
                        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)

                        // Smooth background track
                        drawCircle(
                            color = ObsidianBorder,
                            radius = radius,
                            center = center,
                            style = stroke
                        )

                        // Wavy foreground track
                        if (progress > 0f) {
                            val path = androidx.compose.ui.graphics.Path()
                            val numWaves = 36
                            val waveAmplitude = 8.dp.toPx()
                            
                            val sweepAngle = 360f * progress
                            val startAngle = -90f
                            
                            val points = (sweepAngle).toInt() * 2
                            for (i in 0..points) {
                                val currentAngleDeg = startAngle + (i.toFloat() / Math.max(1, points)) * sweepAngle
                                val currentAngleRad = Math.toRadians(currentAngleDeg.toDouble())
                                
                                val waveOffset = kotlin.math.sin(currentAngleRad * numWaves) * waveAmplitude
                                val currentRadius = radius + waveOffset
                                
                                val px = (center.x + currentRadius * kotlin.math.cos(currentAngleRad)).toFloat()
                                val py = (center.y + currentRadius * kotlin.math.sin(currentAngleRad)).toFloat()
                                
                                if (i == 0) {
                                    path.moveTo(px, py)
                                } else {
                                    path.lineTo(px, py)
                                }
                            }
                            
                            drawPath(
                                path = path,
                                color = GlowGreen,
                                style = stroke
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = clock,
                            color = WarmOffWhite,
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = Outfit,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${chosenDuration} MIN REMAINING",
                            color = GlowGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Zone & Pickup Status Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FocusSessionStatusPill(
                        label = "ZONE STATUS",
                        value = if (isFaceDown) "LOCKED" else "READY",
                        active = isFaceDown,
                        modifier = Modifier.weight(1f)
                    )
                    FocusSessionStatusPill(
                        label = "PICKUPS",
                        value = "$pickupCount/${AnchorViewModel.FOCUS_PICKUP_LIMIT}",
                        active = pickupCount == 0,
                        modifier = Modifier.weight(1f)
                    )
                }

                // DND Active Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, GlowGreen.copy(alpha = 0.30f)), RoundedCornerShape(14.dp))
                        .background(ObsidianSurface.copy(alpha = 0.60f), RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("🛡️", fontSize = 24.sp)
                        Column {
                            Text(
                                text = "DO NOT DISTURB ACTIVE",
                                color = GlowGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "All calls, SMS, and app notifications are silenced during this session.",
                                color = MutedSilver,
                                fontSize = 12.sp,
                                fontFamily = Outfit,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                if (warning != null) {
                    Text(
                        text = "⚠️ $warning",
                        color = SignalAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    text = "Externalize your thoughts on paper. Screen off, mind clear.",
                    color = MutedSilver,
                    fontSize = 13.sp,
                    fontFamily = Outfit,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun MaghribIshaRetreatFullscreen(
    retreatSecondsRemaining: Int,
    totalDurationMinutes: Int,
    sanctuaryLabel: String,
    isEnglish: Boolean,
    onSimulateBreak: () -> Unit,
    onCompleteRetreat: () -> Unit
) {
    val totalSeconds = (totalDurationMinutes * 60).coerceAtLeast(1)
    val progress = (1f - (retreatSecondsRemaining.toFloat() / totalSeconds.toFloat())).coerceIn(0f, 1f)
    val h = retreatSecondsRemaining / 3600
    val m = (retreatSecondsRemaining % 3600) / 60
    val s = retreatSecondsRemaining % 60
    val clock = if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s) else String.format(java.util.Locale.US, "%02d:%02d", m, s)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF002A30), // Islamic deep turquoise
                        Color(0xFF001217),
                        Color.Black
                    ),
                    radius = 1600f
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .background(Color(0xFF00E5FF).copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                    .border(BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.40f)), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text("🌙", fontSize = 14.sp)
                Text(
                    text = if (isEnglish) "SACRED RETREAT ACTIVE" else "الخلوة الإيمانية قائمة",
                    color = Color(0xFF00E5FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isEnglish) "Maghrib-to-Isha Retreat" else "خلوة المغرب إلى العشاء",
                    color = WarmOffWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Outfit
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = sanctuaryLabel,
                    color = Color(0xFF00E5FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Outfit
                )
            }

            // Circular Dial
            Box(
                modifier = Modifier.size(250.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 8.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    val radius = size.minDimension / 2f - stroke.width * 2
                    val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)

                    // Smooth background track
                    drawCircle(
                        color = Color(0xFF003840),
                        radius = radius,
                        center = center,
                        style = stroke
                    )

                    // Wavy foreground track
                    if (progress > 0f) {
                        val path = androidx.compose.ui.graphics.Path()
                        val numWaves = 36
                        val waveAmplitude = 8.dp.toPx()

                        val sweepAngle = 360f * progress
                        val startAngle = -90f

                        val points = (sweepAngle).toInt() * 2
                        for (i in 0..points) {
                            val currentAngleDeg = startAngle + (i.toFloat() / Math.max(1, points)) * sweepAngle
                            val currentAngleRad = Math.toRadians(currentAngleDeg.toDouble())

                            val waveOffset = kotlin.math.sin(currentAngleRad * numWaves) * waveAmplitude
                            val currentRadius = radius + waveOffset

                            val px = (center.x + currentRadius * kotlin.math.cos(currentAngleRad)).toFloat()
                            val py = (center.y + currentRadius * kotlin.math.sin(currentAngleRad)).toFloat()

                            if (i == 0) {
                                path.moveTo(px, py)
                            } else {
                                path.lineTo(px, py)
                            }
                        }

                        drawPath(
                            path = path,
                            color = Color(0xFF00E5FF),
                            style = stroke
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = clock,
                        color = WarmOffWhite,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (isEnglish) "TIME UNTIL ISHA PRAYER" else "الوقت المتبقي لصلاة العشاء",
                        color = Color(0xFF00E5FF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Spiritual Reflection Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
                    .background(Color(0xFF001920).copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📖", fontSize = 18.sp)
                        Text(
                            text = if (isEnglish) "SANCTUARY PRACTICE" else "آداب الخلوة في بيت الله",
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = if (isEnglish)
                            "Stay anchored in the mosque sanctuary. Engage in Quran recitation, silent dhikr, and reflection between Maghrib and Isha."
                        else
                            "اعتكف في رحاب المسجد، واغتنم ما بين الصلاتين في تلاوة القرآن والذكر والاستغفار والتدبر.",
                        color = WarmSilverText,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontFamily = Outfit
                    )
                }
            }

            // Status Chips Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Pair("🔕 DND", if (isEnglish) "Active" else "مفعّل"),
                    Pair("📍 GEOFENCE", if (isEnglish) "Secured" else "مؤمّن"),
                    Pair("🏆 REWARD", "+40 PTS")
                ).forEach { (label, value) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF001920), RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.dp, Color(0xFF003840)), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(label, color = MutedSilver, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(value, color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = Outfit)
                    }
                }
            }

            // Bottom Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PremiumButton(
                    text = if (isEnglish) "SIMULATE BREAK" else "محاكاة المغادرة",
                    onClick = onSimulateBreak,
                    gradientColors = listOf(DeepPanel, ObsidianSurface),
                    contentColor = SignalAmber,
                    modifier = Modifier.weight(1f)
                )

                PremiumButton(
                    text = if (isEnglish) "COMPLETE RETREAT" else "إكمال الخلوة",
                    onClick = onCompleteRetreat,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FocusSessionStatusPill(
    label: String,
    value: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(if (active) GlowGreen.copy(alpha = 0.12f) else ObsidianSurface, RoundedCornerShape(8.dp))
            .border(
                BorderStroke(1.dp, if (active) GlowGreen.copy(alpha = 0.45f) else ObsidianBorder),
                RoundedCornerShape(8.dp)
            )
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = MutedSilver,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = if (active) GlowGreen else WarmOffWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun normalizeFajrAyahInput(value: String): String {
    return Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(FAJR_AYAH_MARKS_REGEX, "")
        .replace(FAJR_AYAH_TATWEEL_REGEX, "")
        .replace(FAJR_AYAH_ALEF_REGEX, "\u0627")
        .replace('\u0649', '\u064A')
        .replace(Regex("[\\u06D6-\\u06ED]"), "")
        .replace(FAJR_AYAH_SPACING_REGEX, " ")
        .trim()
}

@Composable
fun DashboardScreen(
    viewModel: AnchorViewModel,
    onNavigateToProtocol: () -> Unit,
    onNavigateToSurvival: () -> Unit,
    onNavigateToFounderReset: () -> Unit
) {
    val context = LocalContext.current
    val nfcTagId by viewModel.nfcTagId.collectAsState()
    val sanctuaryType by viewModel.sanctuaryType.collectAsState()
    val appPinEnabled by viewModel.appPinEnabled.collectAsState()
    val appPinSet by viewModel.appPinSet.collectAsState()
    val appPinUnlocked by viewModel.appPinUnlocked.collectAsState()
    val isFajrAlarmRinging by viewModel.isFajrAlarmRinging.collectAsState()
    val isJumuahAlarmRinging by viewModel.isJumuahAlarmRinging.collectAsState()
    val isSessionEndAlarmRinging by viewModel.isSessionEndAlarmRinging.collectAsState()
    val isTadabburAlarmRinging by viewModel.isTadabburAlarmRinging.collectAsState()
    val isFajrAlarmMissed by viewModel.isFajrAlarmMissed.collectAsState()
    val isJumuahAlarmMissed by viewModel.isJumuahAlarmMissed.collectAsState()
    val showJumuahBoundConfirmationDialog by viewModel.showJumuahBoundConfirmationDialog.collectAsState()
    val jumuahCountdownText by viewModel.jumuahCountdownText.collectAsState()
    val fajrAlarmEnabled by viewModel.fajrAlarmEnabled.collectAsState()
    val fajrAlarmAyahConstraint by viewModel.fajrAlarmAyahConstraint.collectAsState()
    val fajrAlarmAccountabilityPinEnabled by viewModel.fajrAlarmAccountabilityPinEnabled.collectAsState()
    val fajrAccountabilityPinSet by viewModel.fajrAccountabilityPinSet.collectAsState()
    val fajrNfcTagId by viewModel.fajrNfcTagId.collectAsState()
    val fajrAlarmNfcLocked by viewModel.fajrAlarmNfcLocked.collectAsState()

    // Interactive translation and language togglers as seen in Lovable pages!
    var isEnglish by remember { mutableStateOf(viewModel.isEnglish) }
    val dashboardAccent = GlowGreen
    
    // Bottom Tab States: first tab is now the outdoor sanctuary hub.
    var selectedTab by remember { mutableStateOf(if (sanctuaryType == "runaway") "RUNAWAY" else "MOSQUE") }
    var proofFlowScreen by remember { mutableStateOf("DASHBOARD") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPermissionBottomSheetFromSettings by remember { mutableStateOf(false) }
    var autoFocusFajrPinOnOpen by remember { mutableStateOf(false) }
    var showGpsVerificationDialog by remember { mutableStateOf(false) }
    var gpsVerificationType by remember { mutableStateOf("FAJR") } // "FAJR" or "MAGHRIB"
    var showFounderResetConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab) {
        viewModel.setActiveTab(selectedTab)
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.requestRealLocation()
            viewModel.refreshNearbyMosques()
        } else {
            Toast.makeText(context, "Location permission is needed for live mosque and runway GPS.", Toast.LENGTH_LONG).show()
        }
    }

    val syncRealLocation = {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            viewModel.requestRealLocation()
            viewModel.refreshNearbyMosques()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // REAL-TIME SYSTEM CLOCK
    var systemTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            systemTime = sdf.format(Date())
            delay(1000)
        }
    }

    // Sync active tab to ViewModel so background and NFC events route intelligently
    LaunchedEffect(selectedTab) {
        viewModel.setActiveTab(selectedTab)
    }

    // GLOBAL FAJR ERASER NFC HANDLER
    // Fires when the user taps the Fajr Eraser NFC tag from anywhere — even a cold app launch.
    // Directly locks Fajr for tonight only, triggers haptic feedback, and shows a brief confirmation toast.
    LaunchedEffect(Unit) {
        viewModel.fajrEraserNfcEvent.collect {
            selectedTab = "MOSQUE"
            viewModel.confirmFajrNfcLockdown(repeatDaily = false)
            try {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 350, 150, 450), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 350, 150, 450), -1)
                }
            } catch (_: Exception) {}
            val msg = if (isEnglish) "🔒 Fajr alarm is ACTIVATED & LOCKED for tonight" else "🔒 تم تفعيل وقفل منبه الفجر الليلة"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // GLOBAL ANCHOR TAG HANDLER
    // Fires when the main Anchor NFC tag is tapped from background or outside ANCHOR tab.
    // Navigates directly to Day Guard tab when not on the ANCHOR or MOSQUE tab.
    LaunchedEffect(Unit) {
        viewModel.navigateToTabEvent.collect { tab ->
            if (tab == "MOSQUE" || tab == "OUTDOOR" || tab == "RUNAWAY") {
                selectedTab = if (sanctuaryType == "runaway") "RUNAWAY" else "MOSQUE"
            } else if (tab == "DAY_GUARD") {
                selectedTab = "DAY_GUARD"
            } else if (tab == "ANCHOR") {
                selectedTab = "ANCHOR"
            } else {
                selectedTab = tab
            }
        }
    }

    // STATE CONTROLLERS FOR PROTOCOL FLOW (ANCHOR TAB)
    // Steps: "DURATION" -> "PRE_FLIGHT" -> "TAP_1" -> "SESSION" -> "TAP_2" -> "CLOSING" -> "VERIFIED"
    var anchorStep by remember { mutableStateOf("DURATION") }
    var chosenDuration by remember { mutableStateOf(5) }
    var checklistPhoneFaceDown by remember { mutableStateOf(false) }
    var checklistWhiteboardOpen by remember { mutableStateOf(false) }
    var checklistTimerSet by remember { mutableStateOf(false) }

    // Active session bindings from ViewModel
    val isSessionActive by viewModel.isSessionActive.collectAsState()
    val timeLeftSeconds by viewModel.sessionTimeLeftSeconds.collectAsState()
    val sessionVoidedMessage by viewModel.sessionVoidedMessage.collectAsState()
    val focusPickupCount by viewModel.focusPickupCount.collectAsState()
    val focusPhoneFaceDown by viewModel.focusPhoneFaceDown.collectAsState()
    val focusDisciplineWarning by viewModel.focusDisciplineWarning.collectAsState()
    val focusDndActive by viewModel.focusDndActive.collectAsState()
    val notificationPolicyAccessGranted by viewModel.notificationPolicyAccessGranted.collectAsState()
    val deviceAdminActive by viewModel.deviceAdminActive.collectAsState()
    val focusSessionFullscreen = selectedTab == "ANCHOR" && anchorStep == "SESSION" && isSessionActive
    val mosqueName by viewModel.mosqueName.collectAsState()
    val userLocationName by viewModel.userLocationName.collectAsState()
    val effectiveSanctuaryLabel = remember(sanctuaryType, mosqueName, userLocationName) {
        if (sanctuaryType == "mosque") {
            if (mosqueName.isNotEmpty()) mosqueName else "Mosque Sanctuary"
        } else {
            "Designated Sanctuary"
        }
    }

    // STATE CONTROLLERS FOR MOSQUE RETREAT (MOSQUE TAB)
    val prayerTimes by viewModel.prayerTimesMap.collectAsState()
    val maghribTime = prayerTimes["maghrib"]
    val ishaTime = prayerTimes["isha"]
    val durationMinutes = remember(maghribTime, ishaTime) {
        try {
            if (maghribTime != null && ishaTime != null) {
                val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                val maghribDate = sdf.parse(maghribTime)
                val ishaDate = sdf.parse(ishaTime)
                if (maghribDate != null && ishaDate != null) {
                    var diffMs = ishaDate.time - maghribDate.time
                    if (diffMs < 0) {
                        diffMs += 24 * 60 * 60 * 1000
                    }
                    (diffMs / (60 * 1000)).toInt()
                } else {
                    70
                }
            } else {
                70
            }
        } catch (e: Exception) {
            70
        }
    }

    var isRetreatActive by remember { mutableStateOf(false) }
    var retreatSecondsRemaining by remember { mutableStateOf(15) }

    val dndAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkHardwarePermissions()
    }

    val requestDndAccess = {
        try {
            dndAccessLauncher.launch(viewModel.notificationPolicyAccessIntent())
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open Do Not Disturb access settings.", Toast.LENGTH_SHORT).show()
        }
    }

    FocusSessionSystemUi(active = focusSessionFullscreen || isRetreatActive)

    LaunchedEffect(isRetreatActive) {
        val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        if (isRetreatActive) {
            prefs.edit().putBoolean("maghrib_retreat_in_progress", true).apply()
            retreatSecondsRemaining = if (com.analoganchor.app.viewmodel.AnchorViewModel.ANCHOR_TAP_ONE_TEST_MODE) 15 else durationMinutes * 60
            viewModel.enableDndMode()
        }
        try {
            while (isRetreatActive && retreatSecondsRemaining > 0) {
                delay(1000)
                retreatSecondsRemaining -= 1
                if (retreatSecondsRemaining == 0) {
                    isRetreatActive = false
                    viewModel.addPoints(40, "Completed Maghrib-Isha Retreat", "retreat")
                }
            }
        } finally {
            if (!isRetreatActive) {
                prefs.edit().putBoolean("maghrib_retreat_in_progress", false).apply()
                viewModel.disableDndMode()
            }
        }
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        val tipShown = prefs.getBoolean("compactness_tip_shown", false)
        if (!tipShown) {
            prefs.edit().putBoolean("compactness_tip_shown", true).apply()
            val msg = if (isEnglish)
                "🎨 Welcome! You can adjust your UI scale (Default, Smaller, Bigger) anytime in Day Guard ➔ Appearance & Display."
            else
                "🎨 مرحباً بك! يمكنك ضبط حجم الخط وواجهة التطبيق في أي وقت من علامة تبويب حارس اليوم ➔ المظهر والوضوح."
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    // Sync VM active session to ANCHOR Tab view states
    LaunchedEffect(isSessionActive, timeLeftSeconds, sessionVoidedMessage) {
        when {
            sessionVoidedMessage != null -> {
                // Voided externally
                anchorStep = "DURATION"
            }
            isSessionActive -> {
                anchorStep = "SESSION"
            }
            anchorStep == "SESSION" && timeLeftSeconds == 0 -> {
                anchorStep = "TAP_2"
            }
        }
    }

    // STATE CONTROLLERS FOR SETUP PAIRING (SETUP TAB)
    var setupStep by remember { mutableStateOf(1) } // 1, 2, 3, 4

    val showDecoyShortcutScreen by viewModel.showDecoyShortcutScreen.collectAsState()
    val showReEntryShield by viewModel.showReEntryShield.collectAsState()

    if (showReEntryShield) {
        ReEntryLockoutOverlayContent(viewModel = viewModel, isEnglish = isEnglish)
        return
    }

    if (showDecoyShortcutScreen) {
        AnchorLockDecoyContent(viewModel = viewModel, isEnglish = isEnglish)
        return
    }

    if (isFajrAlarmRinging) {
        if (viewModel.isPastTodayFajrWindow()) {
            LaunchedEffect(Unit) {
                viewModel.validateAndExpireFajrAlarmIfPastSunrise()
            }
        } else {
            FajrAlarmOverlayContent(
                viewModel = viewModel,
                ayahConstraintEnabled = fajrAlarmAyahConstraint,
                accentColor = dashboardAccent,
                isEnglish = isEnglish
            )
            return
        }
    }

    if (isJumuahAlarmRinging) {
        JumuahAlarmOverlayContent(
            viewModel = viewModel,
            accentColor = RefinedGreen,
            isEnglish = isEnglish
        )
        return
    }

    if (isSessionEndAlarmRinging) {
        val alarmDialogLayoutDir = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
        CompositionLocalProvider(LocalLayoutDirection provides alarmDialogLayoutDir) {
            AlertDialog(
                onDismissRequest = { viewModel.stopSessionEndAlarm() },
                containerColor = ObsidianSurface,
                title = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isEnglish) "Focus Session Finished!" else "اكتملت جلسة التدبر!",
                            color = GlowGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            fontFamily = Outfit,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    PremiumButton(
                        text = if (isEnglish) "SILENCE ALARM" else "إيقاف المنبه",
                        onClick = {
                            viewModel.stopSessionEndAlarm()
                            if (selectedTab == "ANCHOR") {
                                anchorStep = "TAP_2"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        gradientColors = listOf(RefinedGreen, GlowGreen),
                        contentColor = ObsidianBg
                    )
                }
            )
        }
    }

    if (isTadabburAlarmRinging) {
        val alarmDialogLayoutDir = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
        CompositionLocalProvider(LocalLayoutDirection provides alarmDialogLayoutDir) {
            AlertDialog(
                onDismissRequest = { viewModel.stopTadabburAlarm() },
                containerColor = ObsidianSurface,
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TactileToken(
                            emoji = "⚓",
                            size = 48.dp,
                            emojiSize = 24.sp,
                            borderColor = GlowGreen
                        )
                        Text(
                            text = if (isEnglish) "Meet Yourself at the Anchor" else "التقِ بنفسك عند المرسى",
                            color = GlowGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            fontFamily = Outfit,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                text = {
                    Text(
                        text = if (isEnglish)
                            "7m before sunrise. Your Anchor is calling you back to your White Glass ritual."
                        else
                            "قبل الشروق بـ ٧ دقائق. مرساتك تناديك لجلسة اللوحة البيضاء.",
                        color = WarmOffWhite,
                        fontSize = 14.sp,
                        fontFamily = Outfit,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    PremiumButton(
                        text = if (isEnglish) "⚡ SIMULATE ANCHOR NFC TAP" else "⚡ محاكاة مسح NFC للمرساة",
                        onClick = {
                            viewModel.simulateTadabburAnchorTap()
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        gradientColors = listOf(RefinedGreen, GlowGreen),
                        contentColor = ObsidianBg
                    )
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.stopTadabburAlarm() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isEnglish) "DISMISS ALARM ONLY" else "إيقاف المنبه فقط",
                            color = MutedSilver,
                            fontSize = 13.sp,
                            fontFamily = Outfit
                        )
                    }
                }
            )
        }
    }

    if (appPinEnabled && !appPinUnlocked) {
        AppPinGateContent(
            viewModel = viewModel,
            accentColor = dashboardAccent,
            isEnglish = isEnglish,
            onUnlocked = {}
        )
        return
    }

    val prayerBufferPrompt by viewModel.prayerBufferPrompt.collectAsState()

    if (prayerBufferPrompt != null) {
        val prompt = prayerBufferPrompt!!
        val prayerName = if (isEnglish) prompt.prayerNameEn else prompt.prayerNameAr
        Dialog(
            onDismissRequest = { viewModel.dismissPrayerBufferPrompt() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .background(ObsidianSurface, RoundedCornerShape(20.dp))
                    .border(1.2.dp, GlowGreen.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TactileToken(
                        emoji = "🛡️",
                        size = 52.dp,
                        emojiSize = 26.sp,
                        borderColor = GlowGreen
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isEnglish) "Confirm $prayerName Soft Landing?" else "تأكيد الهبوط الناعم لصلاة $prayerName؟",
                            color = WarmOffWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            fontFamily = Outfit,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (isEnglish)
                                "Restores sound automatically after prayer."
                            else
                                "يُستعاد وضع الصوت تلقائياً بعد انقضاء الصلاة.",
                            color = MutedSilver,
                            fontSize = 13.sp,
                            fontFamily = Outfit,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PremiumButton(
                            text = if (isEnglish) "🛡️ Activate Soft Landing" else "🛡️ تفعيل الهبوط الناعم",
                            onClick = {
                                val (success, message) = viewModel.confirmPrayerBufferFromPrompt(isEnglish)
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            gradientColors = listOf(RefinedGreen, GlowGreen),
                            contentColor = ObsidianBg
                        )
                        TextButton(
                            onClick = { viewModel.dismissPrayerBufferPrompt() },
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "Not Now / Dismiss" else "ليس الآن / إغلاق",
                                color = MutedSilver,
                                fontFamily = Outfit,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    if (isFajrAlarmMissed) {
        AlertDialog(
            onDismissRequest = { viewModel.acknowledgeMissedFajrAlarm() },
            title = {
                Text(
                    text = if (isEnglish) "⚠️ Missed Fajr Prayer (-10 PTS)" else "⚠️ صلاة الفجر الفائتة (-10 نقاط)",
                    color = SignalAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = Outfit
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.acknowledgeMissedFajrAlarm() }
                ) {
                    Text(
                        text = if (isEnglish) "I ACKNOWLEDGE" else "أقر بذلك",
                        color = GlowGreen,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Outfit
                    )
                }
            },
            containerColor = ObsidianSurface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        )
    }

    if (isJumuahAlarmMissed) {
        AlertDialog(
            onDismissRequest = { viewModel.acknowledgeMissedJumuahAlarm() },
            title = {
                Text(
                    text = if (isEnglish) "⚠️ Missed Friday Jumu'ah (-40 PTS)" else "⚠️ صلاة الجمعة الفائتة (-40 نقطة)",
                    color = SignalAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = Outfit
                )
            },
            text = {
                Text(
                    text = if (isEnglish)
                        "The Jumu'ah alarm vibrated & rang for 15 minutes without user interaction. System executed successfully. 40 points deducted from Reserve balance."
                    else
                        "رن منبه صلاة الجمعة واهتز لمدة 15 دقيقة متواصلة دون أي تفاعل. عمل النظام بنجاح. تم خصم 40 نقطة من رصيد الاحتياطي.",
                    color = WarmOffWhite,
                    fontSize = 14.sp,
                    fontFamily = Outfit
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.acknowledgeMissedJumuahAlarm() }
                ) {
                    Text(
                        text = if (isEnglish) "I ACKNOWLEDGE" else "أقر بذلك",
                        color = GlowGreen,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Outfit
                    )
                }
            },
            containerColor = ObsidianSurface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        )
    }

    if (showJumuahBoundConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissJumuahBoundConfirmationDialog() },
            containerColor = ObsidianSurface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            title = {
                Text(
                    text = if (isEnglish) "Friday Jumu'ah Bound" else "تم ربط صلاة الجمعة",
                    color = GlowGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = Outfit
                )
            },
            text = {
                Text(
                    text = if (isEnglish)
                        "A non-reversible alarm for Friday Jumu'ah prayer has been set up."
                    else
                        "تم تفعيل منبه غير قابل للإلغاء لصلاة الجمعة.",
                    color = WarmOffWhite,
                    fontSize = 14.sp,
                    fontFamily = Outfit
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.dismissJumuahBoundConfirmationDialog() }
                ) {
                    Text(
                        text = if (isEnglish) "OK" else "حسناً",
                        color = GlowGreen,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Outfit
                    )
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.updateJumuahCountdown()
            kotlinx.coroutines.delay(1000L)
        }
    }

    // Sync bottom navigation tab selection with sanctuaryType changes automatically
    LaunchedEffect(sanctuaryType) {
        if (sanctuaryType == "runaway") {
            if (selectedTab == "MOSQUE") {
                selectedTab = "RUNAWAY"
            }
        } else {
            if (selectedTab == "RUNAWAY") {
                selectedTab = "MOSQUE"
            }
        }
    }

    // Main scaffold
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!focusSessionFullscreen && !isRetreatActive) {
                // Premium dark bottom bar utilizing emojis instead of SVG icons as per constraints
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    color = DeepPanel,
                    border = BorderStroke(1.dp, ObsidianBorder.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val sanctuaryTabId = if (sanctuaryType == "runaway") "RUNAWAY" else "MOSQUE"
                        val sanctuaryLabel = if (sanctuaryType == "runaway") "RUNAWAY" else "OUTDOOR"

                        val tabs = listOf(
                            sanctuaryTabId to sanctuaryLabel,
                            "DAY_GUARD" to "CONTROL",
                            "ANCHOR" to "ANCHOR"
                        )

                        tabs.forEach { (tabId, label) ->
                            val isSelected = selectedTab == tabId
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedTab = tabId
                                        if (tabId == "PROOF") {
                                            proofFlowScreen = "DASHBOARD"
                                        }

                                    }
                                    .padding(vertical = 4.dp)
                                    .testTag("tab_$tabId")
                            ) {
                                PillarNavIcon(
                                    tabId = tabId,
                                    isSelected = isSelected,
                                    accentColor = dashboardAccent
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = label,
                                    color = if (isSelected) dashboardAccent else MutedSilver,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                // Elegant dot indicator
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 3.dp)
                                            .width(5.dp)
                                            .height(5.dp)
                                            .background(dashboardAccent, shape = RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        if (focusSessionFullscreen) {
            AnchorFocusSessionFullscreen(
                timeLeftSeconds = timeLeftSeconds,
                chosenDuration = chosenDuration,
                pickupCount = focusPickupCount,
                isFaceDown = focusPhoneFaceDown,
                warning = focusDisciplineWarning,
                dndActive = focusDndActive,
                dndAccessGranted = notificationPolicyAccessGranted,
                deviceAdminActive = deviceAdminActive,
                isEnglish = isEnglish
            )
        } else if (isRetreatActive) {
            MaghribIshaRetreatFullscreen(
                retreatSecondsRemaining = retreatSecondsRemaining,
                totalDurationMinutes = durationMinutes,
                sanctuaryLabel = effectiveSanctuaryLabel,
                isEnglish = isEnglish,
                onSimulateBreak = {
                    isRetreatActive = false
                    viewModel.failFocusSession("Left Mosque Geofence during Retreat")
                    Toast.makeText(context, if (isEnglish) "Retreat broken. Penalty recorded." else "تم كسر الخلوة. تم تسجيل الغرامة.", Toast.LENGTH_SHORT).show()
                },
                onCompleteRetreat = {
                    isRetreatActive = false
                    viewModel.addPoints(40, "Completed Maghrib-Isha Retreat", "retreat")
                    Toast.makeText(context, if (isEnglish) "Retreat Complete! +40 pts" else "اكتملت الخلوة! +40 نقطة", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .background(ObsidianBg)
            ) {
            
            if (selectedTab != "PROOF") {
                // ---------------- HEADER SECTION (Outfit Black Practical Minimalism HUD) ----------------
                Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DeepPanel)
                    .border(BorderStroke(1.dp, GlowGreen.copy(alpha = 0.28f)))
                    .statusBarsPadding()
                    .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianBg, shape = RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.4.dp, GlowGreen.copy(alpha = 0.72f)), shape = RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = "App logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) {
                            Text(
                                text = "PRACTICAL",
                                color = GlowGreen,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = Outfit,
                                letterSpacing = 0.8.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "MINIMALISM",
                                color = WarmOffWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = Outfit,
                                letterSpacing = 1.0.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // English / Arabic toggler Badge (Larger and easier to tap)
                    Row(
                        modifier = Modifier
                            .background(ObsidianSurface, RoundedCornerShape(6.dp))
                            .border(BorderStroke(1.dp, ObsidianBorder), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                            .clickable {
                                val nextVal = !isEnglish
                                isEnglish = nextVal
                                viewModel.isEnglish = nextVal
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "عربي" else "EN",
                            color = GlowGreen,
                            fontSize = 15.sp,
                            fontFamily = if (isEnglish) Outfit else FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // System local clock + Pulse dot
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(8.dp)
                                .background(GlowGreen, shape = RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = systemTime.ifEmpty { "18:09" },
                            color = WarmOffWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // User-facing settings; founder onboarding stays behind the title triple-tap.
                    IconButton(
                        onClick = {
                            showSettingsDialog = true
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("settings_icon")
                    ) {
                        SettingsGlyph(accentColor = dashboardAccent)
                    }
                }
            }

                // Small horizontal brand divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(GlowGreen.copy(alpha = 0.18f))
                )
            }

            // ---------------- CONDITIONAL VIEWS PER SELECTED TAB ----------------
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    "ANCHOR" -> AnchorTabContent(
                        viewModel = viewModel,
                        anchorStep = anchorStep,
                        chosenDuration = chosenDuration,
                        checklistPhoneFaceDown = checklistPhoneFaceDown,
                        checklistWhiteboardOpen = checklistWhiteboardOpen,
                        checklistTimerSet = checklistTimerSet,
                        isEnglish = isEnglish,
                        timeLeftSeconds = timeLeftSeconds,
                        notificationPolicyAccessGranted = notificationPolicyAccessGranted,
                        onStepChange = { anchorStep = it },
                        onDurationSelect = { chosenDuration = it },
                        onChecklistPhoneToggle = { checklistPhoneFaceDown = it },
                        onChecklistWhiteboardToggle = { checklistWhiteboardOpen = it },
                        onChecklistTimerToggle = { checklistTimerSet = it },
                        onRequestDndAccess = requestDndAccess,
                        onReturnToReserve = {
                            selectedTab = if (sanctuaryType == "runaway") "RUNAWAY" else "MOSQUE"
                            anchorStep = "DURATION"
                        }
                    )
                    "MOSQUE" -> MosqueTabContent(
                        viewModel = viewModel,
                        isRetreatActive = isRetreatActive,
                        retreatSecondsRemaining = retreatSecondsRemaining,
                        isEnglish = isEnglish,
                        onRetreatToggle = {
                            isRetreatActive = it
                            if (it) {
                                retreatSecondsRemaining = durationMinutes * 60
                                viewModel.enableDndMode()
                            } else {
                                viewModel.disableDndMode()
                            }
                        },
                        onSyncLocation = syncRealLocation,
                        onVerifyClick = { type ->
                            syncRealLocation()
                            gpsVerificationType = type
                            showGpsVerificationDialog = true
                        },
                        onOpenFajrSettings = {
                            autoFocusFajrPinOnOpen = true
                            showSettingsDialog = true
                        }
                    )
                    "DAY_GUARD" -> {
                        val navSettingsController = androidx.navigation.compose.rememberNavController()
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val app = context.applicationContext as com.analoganchor.app.AnalogAnchorApplication
                        val dayGuardLayoutDirection = if (isEnglish) androidx.compose.ui.unit.LayoutDirection.Ltr else androidx.compose.ui.unit.LayoutDirection.Rtl
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.ui.platform.LocalLayoutDirection provides dayGuardLayoutDirection
                        ) {
                            com.analoganchor.app.ui.screens.settings.SettingsScreen(
                                viewModel = viewModel,
                                preferencesRepository = app.preferencesRepository,
                                innerPadding = PaddingValues(0.dp),
                                navController = navSettingsController,
                                isEnglish = isEnglish,
                                onOpenPermissions = {
                                    showPermissionBottomSheetFromSettings = true
                                }
                            )
                        }
                    }
                    "RUNAWAY", "OUTDOOR" -> MosqueTabContent(
                        viewModel = viewModel,
                        isRetreatActive = isRetreatActive,
                        retreatSecondsRemaining = retreatSecondsRemaining,
                        isEnglish = isEnglish,
                        onRetreatToggle = {
                            isRetreatActive = it
                            if (it) {
                                retreatSecondsRemaining = durationMinutes * 60
                                viewModel.enableDndMode()
                            } else {
                                viewModel.disableDndMode()
                            }
                        },
                        onSyncLocation = syncRealLocation,
                        onVerifyClick = { type ->
                            syncRealLocation()
                            gpsVerificationType = type
                            showGpsVerificationDialog = true
                        },
                        onOpenFajrSettings = {
                            autoFocusFajrPinOnOpen = true
                            showSettingsDialog = true
                        }
                    )
                    "PROOF" -> {
                        when (proofFlowScreen) {
                            "RETURN" -> ReturnProofScreen(
                                viewModel = viewModel,
                                onDone = { proofFlowScreen = "DASHBOARD" },
                                onRecovery = { proofFlowScreen = "RECOVERY" }
                            )
                            "RECOVERY" -> RecoveryScreen(
                                viewModel = viewModel,
                                onDone = { proofFlowScreen = "DASHBOARD" }
                            )
                            else -> AccountabilityDashboardScreen(
                                viewModel = viewModel,
                                isEnglish = isEnglish,
                                onNavigateToReturn = { proofFlowScreen = "RETURN" },
                                onNavigateToRecovery = { proofFlowScreen = "RECOVERY" }
                            )
                        }
                    }
                }
            }
            }
        }
    }

    // Sliding Animated Bottom Sheet Settings Panel (not just a popup)
    AnimatedVisibility(
        visible = showSettingsDialog,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { showSettingsDialog = false }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clickable(enabled = false) {}
                    .background(
                        ObsidianSurface,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .border(
                        BorderStroke(1.2.dp, GlowGreen.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .padding(20.dp)
            ) {
                var settingsTapCount by remember { mutableStateOf(0) }
                var appPinInput by remember { mutableStateOf("") }
                var fajrAccountabilityPinInput by remember { mutableStateOf("") }
                var fajrAccountabilityVerifyInput by remember { mutableStateOf("") }
                val layoutDirection = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Sticky Header (Fixed top bar with Title and Red 'X' Close button)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 40.dp, height = 4.dp)
                                    .background(MutedSilver.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                                    .align(Alignment.CenterHorizontally)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isEnglish) "SETTINGS" else "الإعدادات",
                                    color = WarmOffWhite,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = Outfit,
                                    modifier = Modifier.clickable {
                                        settingsTapCount += 1
                                        if (settingsTapCount >= 3) {
                                            settingsTapCount = 0
                                            showFounderResetConfirmDialog = true
                                        }
                                    }
                                )
                                IconButton(onClick = { showSettingsDialog = false }) {
                                    Text("❌", fontSize = 16.sp)
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ObsidianBorder))
                        }

                        // Scrollable Body Content
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                        // 1. FajrSettingsContent
                        FajrSettingsContent(
                            viewModel = viewModel,
                            alarmEnabled = fajrAlarmEnabled,
                            ayahConstraintEnabled = fajrAlarmAyahConstraint,
                            accountabilityEnabled = fajrAlarmAccountabilityPinEnabled,
                            accountabilityPinSet = fajrAccountabilityPinSet,
                            newPinInput = fajrAccountabilityPinInput,
                            verifyPinInput = fajrAccountabilityVerifyInput,
                            accentColor = dashboardAccent,
                            isEnglish = isEnglish,
                            autoFocusPin = autoFocusFajrPinOnOpen,
                            onAutoFocusDone = { autoFocusFajrPinOnOpen = false },
                            onNewPinInputChange = { value ->
                                fajrAccountabilityPinInput = value.filter { it.isDigit() }.take(4)
                            },
                            onVerifyPinInputChange = { value ->
                                fajrAccountabilityVerifyInput = value.filter { it.isDigit() }.take(4)
                            },
                            onClearPins = {
                                fajrAccountabilityPinInput = ""
                                fajrAccountabilityVerifyInput = ""
                            }
                        )

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ObsidianBorder))

                        val app = context.applicationContext as com.analoganchor.app.AnalogAnchorApplication
                        val preferencesRepository = app.preferencesRepository
                        val preferences by preferencesRepository.userPreferencesFlow.collectAsState(initial = UserPreferences())
                        val coroutineScope = rememberCoroutineScope()

                        // Appearance (Display Compactness)
                        Text(
                            text = if (isEnglish) "Appearance" else "المظهر",
                            color = WarmOffWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Outfit
                        )
                        CompactnessSelector(
                            selectedCompactness = preferences.compactnessOption,
                            onCompactnessChange = { compactness ->
                                coroutineScope.launch {
                                    preferencesRepository.setCompactnessOption(compactness)
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Floating Session Timer
                        Text(
                            text = if (isEnglish) "Floating Session Timer" else "المؤقت العائم للجلسة",
                            color = WarmOffWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Outfit
                        )
                        HUDAppearanceSettings(
                            size = preferences.sessionUsageOverlaySize,
                            opacity = preferences.sessionUsageOverlayOpacity,
                            onSizeChange = { size ->
                                coroutineScope.launch { preferencesRepository.setSessionUsageOverlaySize(size) }
                            },
                            onOpacityChange = { opacity ->
                                coroutineScope.launch { preferencesRepository.setSessionUsageOverlayOpacity(opacity) }
                            },
                            shape = RoundedCornerShape(24.dp),
                            isEnglish = isEnglish
                        )

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ObsidianBorder))

                        // 3. App PIN Lock settings
                        Text(
                            text = "App Lock Configuration",
                            color = WarmOffWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Outfit
                        )
                        SettingsToggleRow(
                            title = "PIN lock",
                            subtitle = if (appPinSet) "Protects dashboard/settings. Emergency flow stays reachable." else "Set a 4-digit dashboard PIN first.",
                            enabled = appPinEnabled,
                            accentColor = dashboardAccent,
                            onToggle = {
                                val changed = viewModel.setAppPinEnabled(!appPinEnabled)
                                if (!changed) {
                                    Toast.makeText(context, "Set a PIN first.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        TextField(
                            value = appPinInput,
                            onValueChange = { value ->
                                appPinInput = value.filter { it.isDigit() }.take(4)
                            },
                            placeholder = { Text("New PIN (4 digits)", color = MutedSilver) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = WarmOffWhite,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = WarmOffWhite,
                                unfocusedTextColor = WarmOffWhite,
                                focusedContainerColor = ObsidianBg,
                                unfocusedContainerColor = ObsidianBg,
                                focusedIndicatorColor = dashboardAccent,
                                unfocusedIndicatorColor = ObsidianBorder,
                                cursorColor = dashboardAccent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PremiumButton(
                                text = "SAVE PIN",
                                onClick = {
                                    if (viewModel.setAppPin(appPinInput)) {
                                        appPinInput = ""
                                        Toast.makeText(context, "PIN lock enabled.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Use exactly 4 digits.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                gradientColors = listOf(dashboardAccent, dashboardAccent.copy(alpha = 0.7f)),
                                contentColor = ObsidianBg,
                                modifier = Modifier.weight(1f)
                            )
                            PremiumButton(
                                text = "CLEAR",
                                onClick = {
                                    viewModel.clearAppPin()
                                    appPinInput = ""
                                    Toast.makeText(context, "PIN lock cleared.", Toast.LENGTH_SHORT).show()
                                },
                                gradientColors = listOf(DeepPanel, ObsidianSurface),
                                contentColor = WarmOffWhite,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ObsidianBorder))

                        // 4. Triple-tap emergency access section (Positioned comfortably above bottom edge)
                        var emergencyClicks by remember { mutableStateOf(0) }
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
                                    .clickable { emergencyClicks += 1 }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (emergencyClicks > 0) "Need emergency access? (${3 - emergencyClicks} more tap${if (3 - emergencyClicks > 1) "s" else ""})" else "Need emergency access?",
                                    color = if (emergencyClicks > 0) SignalAmber else MutedSilver,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = Outfit,
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (emergencyClicks >= 3) {
                                Spacer(modifier = Modifier.height(12.dp))
                                PremiumButton(
                                    text = "ACCESS EMERGENCY PORTAL",
                                    onClick = {
                                        emergencyClicks = 0
                                        viewModel.setShowDecoyShortcutScreen(true)
                                        showSettingsDialog = false
                                    },
                                    gradientColors = listOf(Color(0xFFFF4D4D), Color(0xFFCC0000)),
                                    contentColor = Color.White,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
                    }
                }
            }
        }

    if (showFounderResetConfirmDialog) {
        val dialogLayoutDir = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
        CompositionLocalProvider(LocalLayoutDirection provides dialogLayoutDir) {
            AlertDialog(
                onDismissRequest = { showFounderResetConfirmDialog = false },
                containerColor = ObsidianSurface,
                title = {
                    CompositionLocalProvider(LocalLayoutDirection provides (if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl)) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = if (isEnglish) Alignment.CenterStart else Alignment.CenterEnd
                        ) {
                            Text(
                                text = if (isEnglish) "Reset System Configuration?" else "إعادة ضبط إعدادات النظام؟",
                                color = SignalAmber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                fontFamily = Outfit,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = if (isEnglish) TextAlign.Start else TextAlign.Right,
                                style = TextStyle(
                                    textDirection = if (isEnglish) TextDirection.Ltr else TextDirection.Rtl,
                                    textAlign = if (isEnglish) TextAlign.Start else TextAlign.Right
                                )
                            )
                        }
                    }
                },
                text = {
                    CompositionLocalProvider(LocalLayoutDirection provides (if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl)) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = if (isEnglish) Alignment.CenterStart else Alignment.CenterEnd
                        ) {
                            Text(
                                text = if (isEnglish)
                                    "This developer action will reset all streaks, session logs, and reserve points to zero, and return the device to the initial founder setup. Are you sure you want to proceed?"
                                else
                                    "هذا الإجراء مخصص للمطورين وسيقوم بتصفير جميع نقاط الاحتياطي وسجلات الجلسات والالتزام، وإعادة تهيئة النظام بالكامل. هل أنت متأكد من المتابعة؟",
                                color = WarmOffWhite,
                                fontSize = 14.sp,
                                fontFamily = Outfit,
                                lineHeight = 22.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = if (isEnglish) TextAlign.Start else TextAlign.Right,
                                style = TextStyle(
                                    textDirection = if (isEnglish) TextDirection.Ltr else TextDirection.Rtl,
                                    textAlign = if (isEnglish) TextAlign.Start else TextAlign.Right
                                )
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showFounderResetConfirmDialog = false
                            showSettingsDialog = false
                            viewModel.resetFounderPanel()
                            onNavigateToFounderReset()
                            Toast.makeText(
                                context,
                                if (isEnglish) "System configuration unlocked & reset." else "تمت إعادة ضبط النظام وتهيئة الإعدادات.",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SignalAmber,
                            contentColor = ObsidianBg
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isEnglish) "CONFIRM RESET" else "تأكيد إعادة الضبط",
                            fontWeight = FontWeight.Bold,
                            fontFamily = Outfit
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showFounderResetConfirmDialog = false }
                    ) {
                        Text(
                            text = if (isEnglish) "Cancel" else "إلغاء",
                            color = MutedSilver,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Outfit
                        )
                    }
                }
            )
        }
    }

    // Sliding Animated Bottom Sheet GPS Verification Panel
    AnimatedVisibility(
        visible = showGpsVerificationDialog,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { showGpsVerificationDialog = false }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .background(
                        ObsidianSurface,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .border(
                        BorderStroke(1.2.dp, GlowGreen.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .padding(24.dp)
            ) {
                var loadingStage by remember { mutableStateOf("LOCATING") } // "LOCATING", "RESOLVED", "GPS_OFF"
                val userLat by viewModel.userLatitude.collectAsState()
                val userLng by viewModel.userLongitude.collectAsState()

                val sanctuaryLatitude by viewModel.sanctuaryLatitude.collectAsState()
                val sanctuaryLongitude by viewModel.sanctuaryLongitude.collectAsState()
                val sanctuaryType by viewModel.sanctuaryType.collectAsState()
                val mosqueName by viewModel.mosqueName.collectAsState()

                val sanctuaryLabel = remember(sanctuaryType, mosqueName) {
                    if (sanctuaryType == "mosque") mosqueName else "Designated Sanctuary"
                }

                val distance = remember(userLat, userLng, sanctuaryLatitude, sanctuaryLongitude) {
                    viewModel.calculateDistance(userLat, userLng, sanctuaryLatitude, sanctuaryLongitude)
                }

                var mockLat by remember { mutableStateOf(userLat) }
                var mockLng by remember { mutableStateOf(userLng) }
                var satLockProgress by remember { mutableStateOf(0f) }
                var currentStatus by remember { mutableStateOf("Connecting to satellite system...") }
                var isGpsActive by remember { mutableStateOf(true) }

                LaunchedEffect(showGpsVerificationDialog) {
                    if (showGpsVerificationDialog) {
                        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                        val active = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                        isGpsActive = active

                        if (active) {
                            loadingStage = "LOCATING"
                            satLockProgress = 0f
                            currentStatus = "Initiating real-time high-fidelity GPS check..."

                            // Rapid ticking coordinate animation
                            for (i in 1..25) {
                                delay(80)
                                mockLat = userLat + (Math.random() - 0.5) * 0.0004
                                mockLng = userLng + (Math.random() - 0.5) * 0.0004
                                satLockProgress = i / 25f
                                currentStatus = when {
                                    i < 8 -> "🛰️ Querying cellular nodes..."
                                    i < 15 -> "📡 Syncing atomic clocks. Lat: %.4f".format(mockLat)
                                    i < 22 -> "🔒 Computing differential distance..."
                                    else -> "🔬 Geofence lock acquired."
                                }
                            }
                            mockLat = userLat
                            mockLng = userLng
                            loadingStage = "RESOLVED"
                        } else {
                            loadingStage = "GPS_OFF"
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .background(MutedSilver.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (gpsVerificationType == "FAJR") "FAJR GPS VERIFICATION" else "MAGHRIB-ISHA GPS RETREAT VERIFICATION",
                            color = WarmOffWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                        IconButton(onClick = { showGpsVerificationDialog = false }) {
                            Text("❌", fontSize = 16.sp)
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ObsidianBorder))

                    // Live Mapbox verification context showing user's live position relative to the sanctuary target
                    if (loadingStage != "GPS_OFF") {
                        val verificationPins = remember(sanctuaryLatitude, sanctuaryLongitude, sanctuaryLabel) {
                            if (hasUsableGps(sanctuaryLatitude, sanctuaryLongitude)) {
                                listOf(
                                    MapPin(
                                        name = sanctuaryLabel,
                                        address = "Designated Sanctuary Target",
                                        lat = sanctuaryLatitude,
                                        lng = sanctuaryLongitude
                                    )
                                )
                            } else {
                                emptyList()
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.2.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                        ) {
                            EmbeddedInteractiveMapCanvas(
                                modifier = Modifier.fillMaxSize(),
                                userLat = userLat,
                                userLng = userLng,
                                pins = verificationPins,
                                selectedPinName = sanctuaryLabel
                            )
                        }
                    }

                    if (loadingStage == "GPS_OFF") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(SignalAmber.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape)
                                    .border(BorderStroke(1.5.dp, SignalAmber), androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⚠️", fontSize = 28.sp)
                            }

                            Text(
                                text = "LOCATION SERVICES DISABLED",
                                color = SignalAmber,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "GPS and Location services must be enabled to verify your physical presence at the designated sanctuary. Analog Anchor checks true satellite coordinates for geofence locks.",
                                color = WarmSilverText,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                fontFamily = Outfit,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            PremiumButton(
                                text = "⚙️ ENABLE GPS LOCATION",
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not open Location settings.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                gradientColors = listOf(SignalAmber, Color(0xFFE69A30)),
                                modifier = Modifier.fillMaxWidth()
                            )

                            PremiumButton(
                                text = "🔄 RE-CHECK STATUS",
                                onClick = {
                                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                                    val active = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                                    if (active) {
                                        loadingStage = "LOCATING"
                                        showGpsVerificationDialog = false
                                        showGpsVerificationDialog = true
                                    } else {
                                        Toast.makeText(context, "GPS is still off. Please enable it in Settings.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                gradientColors = listOf(DeepPanel, ObsidianSurface),
                                contentColor = WarmOffWhite,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else if (loadingStage == "LOCATING") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            // Pulsing radar animation
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse_radar")
                            val pulseScale by infiniteTransition.animateFloat(
                                initialValue = 0.8f,
                                targetValue = 1.3f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = FastOutSlowInEasing),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                                ),
                                label = "scale"
                            )
                            val pulseAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.6f,
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = FastOutSlowInEasing),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                                ),
                                label = "alpha"
                            )

                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .background(GlowGreen.copy(alpha = pulseAlpha), androidx.compose.foundation.shape.CircleShape)
                                        .border(BorderStroke(2.dp, GlowGreen.copy(alpha = pulseAlpha)), androidx.compose.foundation.shape.CircleShape)
                                )
                                TactileToken(
                                    emoji = "🛰️",
                                    size = 56.dp,
                                    emojiSize = 24.sp,
                                    borderColor = GlowGreen
                                )
                            }

                            Text(
                                text = currentStatus,
                                color = WarmOffWhite,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )

                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { satLockProgress },
                                color = GlowGreen,
                                trackColor = ObsidianBg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )

                            Text(
                                text = "COORD: %.6f, %.6f".format(mockLat, mockLng),
                                color = MutedSilver,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        val (isPrayerWindowActive, prayerWindowTimeSpan) = remember(gpsVerificationType) {
                            if (gpsVerificationType == "FAJR") {
                                viewModel.isFajrWindowActive()
                            } else {
                                viewModel.isMaghribRetreatWindowActive()
                            }
                        }

                        val (isAtFounderMosque, founderMosqueName) = viewModel.isUserAtAnySanctuaryMosque(userLat, userLng, 200.0)
                        val isWithinProximity = distance <= 200.0 || isAtFounderMosque
                        val effectiveLabel = if (distance <= 200.0) sanctuaryLabel else if (isAtFounderMosque) founderMosqueName else sanctuaryLabel

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (!isPrayerWindowActive) {
                                // LAYER 1 DEFENSE: Time Defense
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(SignalAmber.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape)
                                        .border(BorderStroke(1.5.dp, SignalAmber), androidx.compose.foundation.shape.CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🔒", fontSize = 28.sp)
                                }

                                Text(
                                    text = if (isEnglish) "RETREAT TIMING CLOSED" else "وقت الخلوة مقفل",
                                    color = SignalAmber,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = Outfit
                                )

                                Text(
                                    text = if (isEnglish)
                                        "The Sacred Retreat is strictly locked outside the prayer window ($prayerWindowTimeSpan). Please check in during the Maghrib-to-Isha retreat."
                                    else
                                        "الخلوة مقفلة تماماً خارج وقت الصلاة ($prayerWindowTimeSpan). يرجى التحقق أثناء وقت خلوة المغرب إلى العشاء فقط.",
                                    color = WarmSilverText,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    fontFamily = Outfit
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                PremiumButton(
                                    text = if (isEnglish) "LOCKED UNTIL PRAYER TIME" else "مقفل حتى وقت الصلاة",
                                    enabled = false,
                                    onClick = {},
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else if (!isWithinProximity) {
                                // LAYER 2 DEFENSE: Mosque Geofence Defense
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(SignalAmber.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape)
                                        .border(BorderStroke(1.5.dp, SignalAmber), androidx.compose.foundation.shape.CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("❌", fontSize = 22.sp)
                                }

                                Text(
                                    text = if (isEnglish) "OUTSIDE SANCTUARY GEOFENCE" else "خارج حدود النطاق الجغرافي للمسجد",
                                    color = SignalAmber,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = Outfit
                                )

                                Text(
                                    text = if (isEnglish)
                                        "Proximity check failed. Must be within 200m of your designated mosque ($effectiveLabel at ${distance.toInt()}m)."
                                    else
                                        "فشل التحقق من القرب. يجب أن تكون على بعد أقل من 200م من المسجد المحدد ($effectiveLabel على بعد ${distance.toInt()}م).",
                                    color = WarmSilverText,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    fontFamily = Outfit
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                PremiumButton(
                                    text = if (isEnglish) "MUST BE AT MOSQUE" else "يجب التواجد في المسجد",
                                    enabled = false,
                                    onClick = {},
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                // Both Defenses Passed: Valid Time + Physically at Mosque!
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(RefinedGreen.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape)
                                        .border(BorderStroke(1.5.dp, GlowGreen), androidx.compose.foundation.shape.CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✓", color = GlowGreen, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                }

                                Text(
                                    text = if (isEnglish) "SANCTUARY PRESENCE SECURED" else "تم تأكيد الحضور في المرتكز",
                                    color = GlowGreen,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = Outfit
                                )

                                Text(
                                    text = if (isEnglish)
                                        "You are within secure geofence bounds of $effectiveLabel (${distance.toInt()} meters away)."
                                    else
                                        "أنت داخل حدود النطاق الجغرافي لـ $effectiveLabel (على بعد ${distance.toInt()} متراً).",
                                    color = WarmSilverText,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    fontFamily = Outfit
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                PremiumButton(
                                    text = if (isEnglish) "COMPLETE CHECK-IN" else "إكمال التحقق",
                                    enabled = true,
                                    onClick = {
                                        if (gpsVerificationType == "FAJR") {
                                            viewModel.verifyFajrPresence()
                                        } else {
                                            viewModel.verifyMaghribPresence()
                                        }
                                        Toast.makeText(context, if (isEnglish) "Verification complete! +40 Reserve Credits." else "اكتمل التحقق! +40 نقطة احتياطية.", Toast.LENGTH_SHORT).show()
                                        showGpsVerificationDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }


                        }
                    }
                }
            }
        }
    }

    if (showPermissionBottomSheetFromSettings) {
        val app = context.applicationContext as com.analoganchor.app.AnalogAnchorApplication
        com.analoganchor.app.ui.components.PermissionBottomSheet(
            preferencesRepository = app.preferencesRepository,
            onDismissRequest = { showPermissionBottomSheetFromSettings = false },
            onAllPermissionsGranted = { showPermissionBottomSheetFromSettings = false }
        )
    }
}

@Composable
private fun AppPinGateContent(
    viewModel: AnchorViewModel,
    accentColor: Color,
    isEnglish: Boolean = true,
    onUnlocked: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var pinInput by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val layoutDirection = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .statusBarsPadding()
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Use the app logo drawable instead of adaptive-icon mipmap (prevents Compose painterResource crash)
            Image(
                painter = androidx.compose.ui.res.painterResource(id = com.analoganchor.app.R.drawable.ic_app_logo),
                contentDescription = "Analog Anchor",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = if (isEnglish) "Analog Anchor Locked" else "المرساة مقفلة",
                color = WarmOffWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontFamily = Outfit,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isEnglish) "Enter your app PIN to continue." else "أدخل رمز التطبيق للمتابعة.",
                color = MutedSilver,
                fontSize = 14.sp,
                fontFamily = Outfit,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            TextField(
                value = pinInput,
                onValueChange = { newVal ->
                    val filtered = newVal.filter { ch -> ch.isDigit() }.take(4)
                    pinInput = filtered
                    showError = false
                    // Auto-dismiss keyboard and auto-unlock when 4 digits are entered
                    if (filtered.length == 4) {
                        keyboardController?.hide()
                        if (viewModel.verifyAppPin(filtered)) {
                            pinInput = ""
                            onUnlocked()
                        } else {
                            showError = true
                        }
                    }
                },
                visualTransformation = PasswordVisualTransformation(),
                placeholder = {
                    Text(
                        text = if (isEnglish) "4-digit PIN" else "رمز من 4 أرقام",
                        color = MutedSilver
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = WarmOffWhite,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = WarmOffWhite,
                    unfocusedTextColor = WarmOffWhite,
                    focusedContainerColor = DeepPanel,
                    unfocusedContainerColor = DeepPanel,
                    focusedIndicatorColor = accentColor,
                    unfocusedIndicatorColor = ObsidianBorder,
                    cursorColor = accentColor
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (showError) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isEnglish) "Incorrect PIN" else "رمز غير صحيح",
                    color = SignalAmber,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            PremiumButton(
                text = if (isEnglish) "UNLOCK" else "إلغاء القفل",
                onClick = {
                    keyboardController?.hide()
                    if (viewModel.verifyAppPin(pinInput)) {
                        pinInput = ""
                        onUnlocked()
                    } else {
                        showError = true
                    }
                },
                enabled = pinInput.length == 4,
                gradientColors = listOf(accentColor, accentColor.copy(alpha = 0.7f)),
                contentColor = ObsidianBg,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FajrAlarmOverlayContent(
    viewModel: AnchorViewModel,
    ayahConstraintEnabled: Boolean,
    accentColor: Color,
    isEnglish: Boolean = true
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var ayahInput by remember { mutableStateOf("") }
    var soundSilenced by remember { mutableStateOf(false) }
    var dismissArmed by remember { mutableStateOf(false) }
    val selectedAyah = remember { FAJR_WAKE_AYAHS.random() }
    val requiredAyah = remember(selectedAyah) { normalizeFajrAyahInput(selectedAyah) }
    val ayahMatched = remember(ayahInput, requiredAyah) {
        normalizeFajrAyahInput(ayahInput) == requiredAyah
    }
    val activityRecognitionPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.refreshStepSensorRegistration()
        } else {
            Toast.makeText(context, if (isEnglish) "Step permission is needed to verify wake proof." else "مطلوب إذن تتبع النشاط للتحقق من الاستيقاظ.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(ayahConstraintEnabled, ayahMatched) {
        if (ayahConstraintEnabled && ayahMatched) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
        if (!ayahMatched) {
            dismissArmed = false
        }
    }

    val emptyTextToolbar = remember {
        object : androidx.compose.ui.platform.TextToolbar {
            override val status: androidx.compose.ui.platform.TextToolbarStatus = androidx.compose.ui.platform.TextToolbarStatus.Hidden
            override fun hide() {}
            override fun showMenu(
                rect: androidx.compose.ui.geometry.Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {}
        }
    }

    val layoutDirection = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        androidx.compose.ui.platform.LocalTextToolbar provides emptyTextToolbar
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .statusBarsPadding()
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(SignalAmber.copy(alpha = 0.14f), CircleShape)
                    .border(1.dp, SignalAmber.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isEnglish) "FAJR" else "الفجر", color = SignalAmber, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            }
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = if (isEnglish) "Fajr Discipline Alarm" else "منبه انضباط الفجر",
                color = WarmOffWhite,
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
                fontFamily = Outfit,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isEnglish) {
                    "Five minutes until Fajr. Dismiss the alarm, then carry the phone for ${AnchorViewModel.FAJR_WAKE_STEP_GOAL} real steps."
                } else {
                    "باقٍ خمس دقائق على الفجر. أوقف المنبه، ثم احمل الهاتف وامشِ ${AnchorViewModel.FAJR_WAKE_STEP_GOAL} خطوات للتحقق."
                },
                color = MutedSilver,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontFamily = Outfit,
                textAlign = TextAlign.Center
            )

            if (ayahConstraintEnabled) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = selectedAyah,
                    color = accentColor,
                    fontSize = 22.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Outfit,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                TextField(
                    value = ayahInput,
                    onValueChange = { newValue ->
                        if (newValue.length - ayahInput.length > 2) {
                            Toast.makeText(
                                context,
                                if (isEnglish) "Pasting is disabled. Please type manually." else "النسخ معطّل. يرجى الكتابة يدوياً.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            ayahInput = newValue
                            dismissArmed = false
                        }
                    },
                    placeholder = { Text(if (isEnglish) "Type the ayah without tashkeel" else "اكتب الآية بدون تشكيل", color = MutedSilver) },
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        }
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = WarmOffWhite,
                        fontSize = 18.sp,
                        lineHeight = 28.sp,
                        fontFamily = Outfit,
                        textAlign = TextAlign.Center
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = WarmOffWhite,
                        unfocusedTextColor = WarmOffWhite,
                        focusedContainerColor = DeepPanel,
                        unfocusedContainerColor = DeepPanel,
                        focusedIndicatorColor = accentColor,
                        unfocusedIndicatorColor = ObsidianBorder,
                        cursorColor = accentColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (soundSilenced) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isEnglish) "Sound silenced. Alarm remains active until dismissed." else "تم كتم الصوت. يبقى المنبه نشطاً حتى يتم إيقافه.",
                    color = MutedSilver,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PremiumButton(
                    text = if (isEnglish) "SILENCE" else "كتم",
                    onClick = {
                        viewModel.silenceCurrentFajrAlarm()
                        soundSilenced = true
                    },
                    gradientColors = listOf(DeepPanel, ObsidianSurface),
                    contentColor = WarmOffWhite,
                    modifier = Modifier.weight(1f)
                )
                PremiumButton(
                    text = if (dismissArmed) (if (isEnglish) "CONFIRM" else "تأكيد") else (if (isEnglish) "DISMISS" else "إيقاف"),
                    onClick = {
                        if (ayahConstraintEnabled && !dismissArmed) {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            dismissArmed = true
                            Toast.makeText(context, if (isEnglish) "Press once more to dismiss." else "اضغط مرة أخرى للإيقاف.", Toast.LENGTH_SHORT).show()
                        } else {
                            val stepProofArmed = viewModel.dismissFajrAlarm()
                            if (
                                stepProofArmed &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACTIVITY_RECOGNITION
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                activityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                            }
                            Toast.makeText(
                                context,
                                if (stepProofArmed) {
                                    if (isEnglish) "Alarm dismissed. Take the phone with you for ${AnchorViewModel.FAJR_WAKE_STEP_GOAL} steps." else "تم إيقاف المنبه. احمل الهاتف معك لـ ${AnchorViewModel.FAJR_WAKE_STEP_GOAL} خطوة."
                                } else {
                                    if (isEnglish) "Fajr alarm dismissed." else "تم إيقاف منبه الفجر."
                                },
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    enabled = !ayahConstraintEnabled || ayahMatched,
                    gradientColors = listOf(accentColor, accentColor.copy(alpha = 0.72f)),
                    contentColor = ObsidianBg,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun JumuahAlarmOverlayContent(
    viewModel: AnchorViewModel,
    accentColor: Color,
    isEnglish: Boolean = true
) {
    val context = LocalContext.current
    var soundSilenced by remember { mutableStateOf(false) }

    val emptyTextToolbar = remember {
        object : androidx.compose.ui.platform.TextToolbar {
            override val status: androidx.compose.ui.platform.TextToolbarStatus = androidx.compose.ui.platform.TextToolbarStatus.Hidden
            override fun hide() {}
            override fun showMenu(
                rect: androidx.compose.ui.geometry.Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {}
        }
    }

    val layoutDirection = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        androidx.compose.ui.platform.LocalTextToolbar provides emptyTextToolbar
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .statusBarsPadding()
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(RefinedGreen.copy(alpha = 0.14f), CircleShape)
                    .border(1.dp, RefinedGreen.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isEnglish) "JUMU'AH" else "الجمعة",
                    color = RefinedGreen,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = if (isEnglish) "Friday Jumu'ah Alarm" else "منبه صلاة الجمعة",
                color = WarmOffWhite,
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
                fontFamily = Outfit,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isEnglish) {
                    "Time to prepare and head to the mosque before the 1st Azan."
                } else {
                    "حان وقت الاستعداد والتوجه إلى المسجد قبل الأذان الأول."
                },
                color = MutedSilver,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontFamily = Outfit,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PremiumButton(
                    text = if (soundSilenced) (if (isEnglish) "SILENCED" else "تم الكتم") else (if (isEnglish) "SILENCE" else "كتم الصوت"),
                    onClick = {
                        soundSilenced = true
                        viewModel.silenceCurrentJumuahAlarm()
                        Toast.makeText(context, if (isEnglish) "Alarm audio silenced." else "تم كتم صوت المنبه.", Toast.LENGTH_SHORT).show()
                    },
                    gradientColors = listOf(DeepPanel, ObsidianSurface),
                    contentColor = if (soundSilenced) MutedSilver else WarmOffWhite,
                    modifier = Modifier.weight(1f)
                )

                PremiumButton(
                    text = if (isEnglish) "DISMISS" else "إيقاف",
                    onClick = {
                        viewModel.dismissJumuahAlarm()
                        Toast.makeText(context, if (isEnglish) "Friday alarm dismissed." else "تم إيقاف منبه الجمعة.", Toast.LENGTH_SHORT).show()
                    },
                    gradientColors = listOf(RefinedGreen, RefinedGreen.copy(alpha = 0.72f)),
                    contentColor = ObsidianBg,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FajrSettingsContent(
    viewModel: AnchorViewModel,
    alarmEnabled: Boolean,
    ayahConstraintEnabled: Boolean,
    accountabilityEnabled: Boolean,
    accountabilityPinSet: Boolean,
    newPinInput: String,
    verifyPinInput: String,
    accentColor: Color,
    isEnglish: Boolean,
    autoFocusPin: Boolean = false,
    onAutoFocusDone: () -> Unit = {},
    onNewPinInputChange: (String) -> Unit,
    onVerifyPinInputChange: (String) -> Unit,
    onClearPins: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val pinFocusRequester = remember { FocusRequester() }
    val accountabilityPinForChange = verifyPinInput.takeIf { it.isNotBlank() }
    var pinFieldRequired by remember { mutableStateOf(false) }
    var pendingToggleAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val ringtoneLabel by viewModel.fajrAlarmRingtoneLabel.collectAsState()
    val customRingtoneUri by viewModel.fajrAlarmRingtoneUri.collectAsState()
    val fajrAlarmOffsetMinutes by viewModel.fajrAlarmOffsetMinutes.collectAsState()
    val isFriday by viewModel.isFriday.collectAsState()
    val fajrAlarmNfcLocked by viewModel.fajrAlarmNfcLocked.collectAsState()

    // Auto-focus PIN field & show keyboard when opened via PIN status pill or toggle attempt
    LaunchedEffect(autoFocusPin, accountabilityEnabled) {
        if (autoFocusPin && accountabilityEnabled) {
            kotlinx.coroutines.delay(200) // allow bottom sheet animation to settle
            pinFocusRequester.requestFocus()
            keyboardController?.show()
            pinFieldRequired = true
            onAutoFocusDone()
        }
    }

    // Auto-execute pending toggle action and auto-hide keyboard as soon as 4th digit is entered
    LaunchedEffect(verifyPinInput) {
        if (verifyPinInput.length == 4) {
            keyboardController?.hide()
            pinFieldRequired = false

            if (pendingToggleAction != null) {
                pendingToggleAction?.invoke()
                pendingToggleAction = null
            }
        }
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Some providers grant temporary read access only; we still store and try playback.
            }
            val label = getDisplayNameForUri(context, uri) ?: (if (isEnglish) "Custom Fajr ringtone" else "نغمة الفجر المخصصة")
            viewModel.setFajrAlarmRingtone(uri.toString(), label)
            Toast.makeText(context, if (isEnglish) "Fajr ringtone imported." else "تم استيراد نغمة الفجر.", Toast.LENGTH_SHORT).show()
        }
    }

    val layoutDirection = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (isEnglish) "Fajr Enforcement" else "فرض الفجر",
                color = WarmOffWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Outfit
            )

            if (accountabilityEnabled) {
                FajrPinField(
                    value = verifyPinInput,
                    onValueChange = {
                        onVerifyPinInputChange(it)
                        if (it.isNotBlank()) pinFieldRequired = false
                    },
                    placeholder = if (isEnglish) "Accountability PIN for changes" else "رمز الرقابة لإجراء التغييرات",
                    accentColor = if (pinFieldRequired) SignalAmber else accentColor,
                    focusRequester = pinFocusRequester
                )
                if (pinFieldRequired) {
                    Text(
                        text = if (isEnglish) "⚠️ Enter 4-digit PIN above to apply change" else "⚠️ أدخل الرمز المكون من 4 أرقام أعلاه لإجراء التغيير",
                        color = SignalAmber,
                        fontSize = 12.sp,
                        fontFamily = Outfit,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }

            SettingsToggleRow(
                title = if (isEnglish) "Fajr discipline alarm" else "منبه انضباط الفجر",
                subtitle = if (alarmEnabled) {
                    if (isEnglish) "Active: Triggers at exact Fajr Azan time." else "مفعل: يعمل عند أذان الفجر مباشرة."
                } else {
                    if (isEnglish) "Daily trigger is disabled." else "التفعيل اليومي معطل."
                },
                enabled = alarmEnabled,
                accentColor = accentColor,
                onToggle = {
                    val enabling = !alarmEnabled
                    if (enabling) {
                        // ENABLING Fajr discipline alarm is ALWAYS allowed without any PIN
                        val changed = viewModel.setFajrAlarmEnabled(true)
                        if (changed) {
                            Toast.makeText(context, if (isEnglish) "Fajr alarm activated." else "تم تفعيل منبه الفجر.", Toast.LENGTH_SHORT).show()
                            pinFieldRequired = false
                        }
                    } else {
                        // DISABLING requires accountability PIN if enabled
                        if (accountabilityEnabled && verifyPinInput.length < 4) {
                            pinFieldRequired = true
                            pinFocusRequester.requestFocus()
                            keyboardController?.show()
                            pendingToggleAction = {
                                val pinToUse = verifyPinInput.takeIf { it.isNotBlank() }
                                val changed = viewModel.setFajrAlarmEnabled(false, pinToUse)
                                if (changed) {
                                    Toast.makeText(context, if (isEnglish) "Fajr alarm disabled." else "تم تعطيل منبه الفجر.", Toast.LENGTH_SHORT).show()
                                    pinFieldRequired = false
                                    onClearPins()
                                } else {
                                    Toast.makeText(context, if (isEnglish) "Incorrect accountability PIN" else "رمز الرقابة غير صحيح", Toast.LENGTH_SHORT).show()
                                    pinFieldRequired = true
                                }
                            }
                            Toast.makeText(
                                context,
                                if (isEnglish) "⚠️ Enter the 4-digit PIN above to disable" else "⚠️ أدخل رمز الرقابة المكون من 4 أرقام أعلاه للتعطيل",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            val changed = viewModel.setFajrAlarmEnabled(false, accountabilityPinForChange)
                            if (changed) {
                                Toast.makeText(context, if (isEnglish) "Fajr alarm disabled." else "تم تعطيل منبه الفجر.", Toast.LENGTH_SHORT).show()
                                pinFieldRequired = false
                                onClearPins()
                            } else {
                                val isPinValid = viewModel.verifyFajrAccountabilityPin(accountabilityPinForChange.orEmpty())
                                val msg = if (accountabilityEnabled && !isPinValid) {
                                    if (isEnglish) "Incorrect accountability PIN" else "رمز الرقابة غير صحيح"
                                } else if (fajrAlarmNfcLocked) {
                                    if (isEnglish) "Alarm is locked via NFC pre-commitment until after Fajr." else "المنبه مقفل عبر الالتزام المسبق بـ NFC حتى بعد صلاة الفجر."
                                } else {
                                    if (isEnglish) "Alarm settings are locked." else "إعدادات المنبه مقفلة."
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (accountabilityEnabled && !isPinValid) pinFieldRequired = true
                            }
                        }
                    }
                }
            )

            SettingsToggleRow(
                title = if (isEnglish) "Ayah wake challenge" else "تحدي الاستيقاظ بالآية",
                subtitle = if (isEnglish) "Requires typing the displayed ayah before full dismissal." else "يتطلب كتابة الآية المعروضة قبل الإيقاف الكامل للمنبه.",
                enabled = ayahConstraintEnabled,
                accentColor = accentColor,
                onToggle = {
                    if (accountabilityEnabled && verifyPinInput.length < 4) {
                        pinFieldRequired = true
                        pinFocusRequester.requestFocus()
                        keyboardController?.show()
                        pendingToggleAction = {
                            val pinToUse = verifyPinInput.takeIf { it.isNotBlank() }
                            val changed = viewModel.setFajrAlarmAyahConstraint(!ayahConstraintEnabled, pinToUse)
                            if (changed) {
                                Toast.makeText(context, if (isEnglish) "Ayah wake challenge updated." else "تم تحديث تحدي الاستيقاظ بالآية.", Toast.LENGTH_SHORT).show()
                                pinFieldRequired = false
                                onClearPins()
                            } else {
                                Toast.makeText(context, if (isEnglish) "Incorrect accountability PIN" else "رمز الرقابة غير صحيح", Toast.LENGTH_SHORT).show()
                                pinFieldRequired = true
                            }
                        }
                        Toast.makeText(
                            context,
                            if (isEnglish) "⚠️ Enter the 4-digit PIN above" else "⚠️ أدخل رمز الرقابة المكون من 4 أرقام أعلاه",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val changed = viewModel.setFajrAlarmAyahConstraint(!ayahConstraintEnabled, accountabilityPinForChange)
                        if (changed) {
                            Toast.makeText(context, if (isEnglish) "Ayah wake challenge updated." else "تم تحديث تحدي الاستيقاظ بالآية.", Toast.LENGTH_SHORT).show()
                            pinFieldRequired = false
                            onClearPins()
                        } else {
                            val isPinValid = viewModel.verifyFajrAccountabilityPin(accountabilityPinForChange.orEmpty())
                            val msg = if (accountabilityEnabled && !isPinValid) {
                                if (isEnglish) "Incorrect accountability PIN" else "رمز الرقابة غير صحيح"
                            } else {
                                if (isEnglish) "Alarm settings are locked." else "إعدادات المنبه مقفلة."
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (accountabilityEnabled && !isPinValid) pinFieldRequired = true
                        }
                    }
                }
            )

            if (!accountabilityEnabled) {
                FajrPinField(
                    value = newPinInput,
                    onValueChange = onNewPinInputChange,
                    placeholder = if (isEnglish) "Friend-held PIN (4 digits)" else "رمز الرقابة مع الصديق (4 أرقام)",
                    accentColor = accentColor
                )
                PremiumButton(
                    text = if (isEnglish) "ENABLE ACCOUNTABILITY" else "تفعيل قفل الرقابة",
                    onClick = {
                        val changed = viewModel.setFajrAlarmAccountabilityPinEnabled(true, newPinInput)
                        Toast.makeText(
                            context,
                            if (changed) {
                                if (isEnglish) "Accountability PIN enabled." else "تم تفعيل قفل الرقابة."
                            } else {
                                if (isEnglish) "Use exactly 4 digits." else "استخدم 4 أرقام بالضبط."
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                        if (changed) onClearPins()
                    },
                    enabled = newPinInput.length == 4,
                    gradientColors = listOf(accentColor, accentColor.copy(alpha = 0.72f)),
                    contentColor = ObsidianBg,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                PremiumButton(
                    text = if (isEnglish) "DISABLE ACCOUNTABILITY" else "إلغاء قفل الرقابة",
                    onClick = {
                        val changed = viewModel.setFajrAlarmAccountabilityPinEnabled(false, verifyPinInput)
                        Toast.makeText(
                            context,
                            if (changed) {
                                if (isEnglish) "Accountability PIN disabled." else "تم إلغاء قفل الرقابة."
                            } else {
                                if (isEnglish) "Incorrect accountability PIN." else "رمز الرقابة غير صحيح."
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                        if (changed) onClearPins()
                    },
                    enabled = verifyPinInput.length == 4 && accountabilityPinSet,
                    gradientColors = listOf(SignalAmber, SignalAmber.copy(alpha = 0.72f)),
                    contentColor = ObsidianBg,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            GlassCard(borderColor = accentColor.copy(alpha = 0.42f)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isEnglish) "Scheduled alarm sound" else "صوت المنبه المجدول",
                        color = WarmOffWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Outfit
                    )
                    Text(
                        text = if (fajrAlarmNfcLocked) {
                            if (isEnglish) {
                                "The enforced Fajr alarm will use this sound when it triggers 30 minutes before Fajr."
                            } else {
                                "سيستخدم منبه الفجر المفروض هذا الصوت عندما يتم تفعيله قبل الفجر بـ 30 دقيقة."
                            }
                        } else {
                            if (isEnglish) {
                                "The enforced alarm will use this sound when triggered."
                            } else {
                                "سيستخدم المنبه المفروض هذا الصوت عند التفعيل."
                            }
                        },
                        color = WarmSilverText,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontFamily = Outfit
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianBg.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (customRingtoneUri.isNullOrBlank()) {
                                    if (isEnglish) "BUILT-IN TONE" else "النغمة المدمجة"
                                } else {
                                    if (isEnglish) "CUSTOM RINGTONE" else "نغمة مخصصة"
                                },
                                color = accentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = ringtoneLabel,
                                color = WarmOffWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Outfit,
                                maxLines = 2
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PremiumButton(
                            text = if (isEnglish) "IMPORT" else "استيراد",
                            onClick = { ringtonePickerLauncher.launch(arrayOf("audio/*")) },
                            gradientColors = listOf(accentColor, accentColor.copy(alpha = 0.72f)),
                            contentColor = ObsidianBg,
                            modifier = Modifier.weight(1f)
                        )
                        PremiumButton(
                            text = if (isEnglish) "PREVIEW" else "معاينة",
                            onClick = { viewModel.previewFajrAlarmRingtone() },
                            gradientColors = listOf(DeepPanel, ObsidianSurface),
                            contentColor = WarmOffWhite,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (!customRingtoneUri.isNullOrBlank()) {
                        PremiumButton(
                            text = if (isEnglish) "RESET TO BUILT-IN" else "إعادة تعيين للمدمج",
                            onClick = {
                                viewModel.clearFajrAlarmRingtone()
                                Toast.makeText(context, if (isEnglish) "Built-in Fajr tone restored." else "تمت استعادة نغمة الفجر المدمجة.", Toast.LENGTH_SHORT).show()
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
}

private fun getDisplayNameForUri(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }
    } catch (e: Exception) {
        uri.lastPathSegment
    }
}

@Composable
private fun FajrPinField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    accentColor: Color,
    focusRequester: FocusRequester? = null
) {
    var modifier: Modifier = Modifier.fillMaxWidth()
    if (focusRequester != null) {
        modifier = modifier.focusRequester(focusRequester)
    }
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = MutedSilver) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = androidx.compose.ui.text.TextStyle(
            color = WarmOffWhite,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        ),
        colors = TextFieldDefaults.colors(
            focusedTextColor = WarmOffWhite,
            unfocusedTextColor = WarmOffWhite,
            focusedContainerColor = ObsidianBg,
            unfocusedContainerColor = ObsidianBg,
            focusedIndicatorColor = accentColor,
            unfocusedIndicatorColor = ObsidianBorder,
            cursorColor = accentColor
        ),
        modifier = modifier
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    accentColor: Color,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianBg, RoundedCornerShape(10.dp))
            .border(1.dp, if (enabled) accentColor.copy(alpha = 0.45f) else ObsidianBorder, RoundedCornerShape(10.dp))
            .clickable { onToggle() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = WarmOffWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Outfit)
            Spacer(modifier = Modifier.height(3.dp))
            Text(subtitle, color = MutedSilver, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = Outfit)
        }
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 26.dp)
                .background(if (enabled) accentColor.copy(alpha = 0.16f) else DeepPanel, RoundedCornerShape(13.dp))
                .border(1.dp, if (enabled) accentColor else ObsidianBorder, RoundedCornerShape(13.dp))
                .padding(3.dp),
            contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(if (enabled) accentColor else MutedSilver, CircleShape)
            )
        }
    }
}

@Composable
private fun SettingsOptionChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(if (selected) accentColor.copy(alpha = 0.15f) else ObsidianBg, RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) accentColor else ObsidianBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) accentColor else MutedSilver,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SettingsGlyph(
    accentColor: Color,
    modifier: Modifier = Modifier.size(34.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = w * 0.42f, center = Offset(w * 0.55f, h * 0.58f))
        drawCircle(DeepPanel, radius = w * 0.42f, center = Offset(w * 0.5f, h * 0.5f))
        drawCircle(accentColor.copy(alpha = 0.18f), radius = w * 0.37f, center = Offset(w * 0.5f, h * 0.5f))
        repeat(8) { i ->
            val angle = (Math.PI * 2.0 * i / 8.0).toFloat()
            val x1 = w * 0.5f + kotlin.math.cos(angle) * w * 0.29f
            val y1 = h * 0.5f + kotlin.math.sin(angle) * h * 0.29f
            val x2 = w * 0.5f + kotlin.math.cos(angle) * w * 0.39f
            val y2 = h * 0.5f + kotlin.math.sin(angle) * h * 0.39f
            drawLine(accentColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = w * 0.06f)
        }
        drawCircle(ObsidianBg, radius = w * 0.15f, center = Offset(w * 0.5f, h * 0.5f))
        drawCircle(accentColor, radius = w * 0.07f, center = Offset(w * 0.5f, h * 0.5f))
    }
}

@Composable
private fun PillarNavIcon(
    tabId: String,
    isSelected: Boolean,
    accentColor: Color
) {
    val base = if (isSelected) accentColor else MutedSilver.copy(alpha = 0.78f)
    val panel = if (isSelected) DeepPanel else ObsidianBg
    Canvas(
        modifier = Modifier
            .size(36.dp)
            .scale(if (isSelected) 1.08f else 1f)
    ) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.28f),
            topLeft = Offset(w * 0.13f, h * 0.18f),
            size = Size(w * 0.78f, h * 0.72f),
            cornerRadius = CornerRadius(w * 0.22f, h * 0.22f)
        )
        drawRoundRect(
            color = panel,
            topLeft = Offset(w * 0.08f, h * 0.10f),
            size = Size(w * 0.78f, h * 0.72f),
            cornerRadius = CornerRadius(w * 0.22f, h * 0.22f)
        )
        when (tabId) {
            "DAY_GUARD" -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.5f, h * 0.22f)
                    lineTo(w * 0.82f, h * 0.32f)
                    lineTo(w * 0.78f, h * 0.60f)
                    quadraticBezierTo(w * 0.7f, h * 0.78f, w * 0.5f, h * 0.88f)
                    quadraticBezierTo(w * 0.3f, h * 0.78f, w * 0.22f, h * 0.60f)
                    lineTo(w * 0.18f, h * 0.32f)
                    close()
                }
                drawPath(path, base, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.08f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                drawCircle(base, radius = w * 0.08f, center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.55f))
            }
            "ANCHOR" -> {
                drawLine(base, Offset(w * 0.50f, h * 0.24f), Offset(w * 0.50f, h * 0.66f), strokeWidth = w * 0.08f)
                drawCircle(base, radius = w * 0.10f, center = Offset(w * 0.50f, h * 0.24f))
                drawLine(base, Offset(w * 0.28f, h * 0.54f), Offset(w * 0.72f, h * 0.54f), strokeWidth = w * 0.07f)
                drawLine(base, Offset(w * 0.31f, h * 0.55f), Offset(w * 0.18f, h * 0.68f), strokeWidth = w * 0.07f)
                drawLine(base, Offset(w * 0.69f, h * 0.55f), Offset(w * 0.82f, h * 0.68f), strokeWidth = w * 0.07f)
            }
            "MOSQUE", "OUTDOOR" -> {
                drawRoundRect(base.copy(alpha = 0.9f), Offset(w * 0.26f, h * 0.48f), Size(w * 0.48f, h * 0.20f), CornerRadius(w * 0.05f, w * 0.05f))
                drawCircle(base, radius = w * 0.21f, center = Offset(w * 0.50f, h * 0.48f))
                drawRoundRect(panel, Offset(w * 0.24f, h * 0.51f), Size(w * 0.52f, h * 0.18f), CornerRadius(w * 0.02f, w * 0.02f))
                drawRoundRect(base, Offset(w * 0.74f, h * 0.25f), Size(w * 0.08f, h * 0.42f), CornerRadius(w * 0.04f, w * 0.04f))
            }
            "RUNAWAY" -> {
                drawCircle(base, radius = w * 0.07f, center = Offset(w * 0.24f, h * 0.64f))
                drawLine(base, Offset(w * 0.29f, h * 0.60f), Offset(w * 0.46f, h * 0.40f), strokeWidth = w * 0.08f)
                drawLine(base, Offset(w * 0.46f, h * 0.40f), Offset(w * 0.67f, h * 0.52f), strokeWidth = w * 0.08f)
                drawLine(base, Offset(w * 0.62f, h * 0.34f), Offset(w * 0.78f, h * 0.20f), strokeWidth = w * 0.08f)
            }
        }
    }
}

@Composable
fun AnchorTabContent(
    viewModel: AnchorViewModel,
    anchorStep: String,
    chosenDuration: Int,
    checklistPhoneFaceDown: Boolean,
    checklistWhiteboardOpen: Boolean,
    checklistTimerSet: Boolean,
    isEnglish: Boolean,
    timeLeftSeconds: Int,
    notificationPolicyAccessGranted: Boolean,
    onStepChange: (String) -> Unit,
    onDurationSelect: (Int) -> Unit,
    onChecklistPhoneToggle: (Boolean) -> Unit,
    onChecklistWhiteboardToggle: (Boolean) -> Unit,
    onChecklistTimerToggle: (Boolean) -> Unit,
    onRequestDndAccess: () -> Unit,
    onReturnToReserve: () -> Unit
) {
    val context = LocalContext.current
    val nfcTagId by viewModel.nfcTagId.collectAsState()
    val isEveningAnchorCompletedToday by viewModel.isEveningAnchorCompletedToday.collectAsState()
    val daily25mCount by viewModel.daily25mCount.collectAsState()
    val hasLongSessionToday by viewModel.hasLongSessionToday.collectAsState()
    val whiteboardSessions by viewModel.whiteboardSessions.collectAsState()
    var selectedSessionForDetail by remember { mutableStateOf<SessionLog?>(null) }
    var closingNote by remember { mutableStateOf("") }
    var showCameraNfcWarning by remember { mutableStateOf(false) }
    var showReflectionCapDialog by remember { mutableStateOf(false) }

    LaunchedEffect(daily25mCount, hasLongSessionToday) {
        if (!viewModel.isDurationAvailable(chosenDuration) && viewModel.isDurationAvailable(5)) {
            onDurationSelect(5)
        }
    }
    var capturedWhiteboardPath by remember { mutableStateOf<String?>(null) }
    var pendingWhiteboardPath by remember { mutableStateOf<String?>(null) }
    var pendingWhiteboardUri by remember { mutableStateOf<Uri?>(null) }
    val takeWhiteboardPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            capturedWhiteboardPath = pendingWhiteboardPath
        } else {
            Toast.makeText(context, if (isEnglish) "Whiteboard photo was not captured." else "لم يتم التقاط صورة اللوحة.", Toast.LENGTH_SHORT).show()
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pendingWhiteboardUri?.let { takeWhiteboardPhotoLauncher.launch(it) }
        } else {
            Toast.makeText(context, if (isEnglish) "Camera permission is required for the whiteboard photo." else "مطلوب إذن الكاميرا لالتقاط صورة اللوحة.", Toast.LENGTH_SHORT).show()
        }
    }
    fun launchWhiteboardCamera() {
        val file = ProofFileStore.createProofImageFile(context, "whiteboard_reflection")
        pendingWhiteboardPath = file.absolutePath
        pendingWhiteboardUri = ProofFileStore.uriForFile(context, file)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            pendingWhiteboardUri?.let { takeWhiteboardPhotoLauncher.launch(it) }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val layoutDirection = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tab Subtitle
            Column {
                Text(
                    text = if (isEnglish) "Externalize your thoughts off the screen and let clarity emerge naturally." else "أخرج أفكارك خارج الشاشة ودع الوضوح ينبثق بشكل طبيعي.",
                    color = MutedSilver,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Sequence map indicators (DURATION -> PRE-FLIGHT -> TAP -> CLOSING)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val stages = listOf(
                        "DURATION" to (if (isEnglish) "TIME" else "الوقت"),
                        "PRE_FLIGHT" to (if (isEnglish) "READY" else "جاهز"),
                        "TAP" to (if (isEnglish) "TAP" else "النقر"),
                        "CLOSING" to (if (isEnglish) "CLOSE" else "إغلاق")
                    )
                    stages.forEach { (id, label) ->
                        val isCurrent = when (id) {
                            "DURATION" -> anchorStep == "DURATION"
                            "PRE_FLIGHT" -> anchorStep == "PRE_FLIGHT"
                            "TAP" -> anchorStep == "TAP" || anchorStep == "TAP_1" || anchorStep == "TAP_2" || anchorStep == "SESSION"
                            "CLOSING" -> anchorStep == "CLOSING" || anchorStep == "VERIFIED"
                            else -> false
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                color = if (isCurrent) GlowGreen else SteelShadow,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(if (isCurrent) GlowGreen else ObsidianBorder)
                            )
                        }
                    }
                }
            }

            // Dynamic State Screen Contents
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (anchorStep) {
                    "DURATION" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "Choose duration" else "اختر المدة",
                                color = WarmOffWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            // Duration choices cards
                            val options = listOf(
                                Triple(5, if (isEnglish) "Short" else "قصيرة", if (isEnglish) "One pomodoro • planning sprint" else "بومودورو واحد • تخطيط سريع"),
                                Triple(10, if (isEnglish) "Standard" else "قياسية", if (isEnglish) "Deep block • most days" else "جلسة عميقة • معظم الأيام"),
                                Triple(15, if (isEnglish) "Anchor" else "مرساة", if (isEnglish) "Full ritual • max reward" else "طقس كامل • أقصى مكافأة")
                            )

                            options.forEach { (min, title, desc) ->
                                val isSelected = chosenDuration == min
                                val isAvailable = viewModel.isDurationAvailable(min)
                                val pts = when (min) {
                                    10 -> 35
                                    15 -> 45
                                    else -> 25
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                if (isSelected && isAvailable) GlowGreen
                                                else if (!isAvailable) ObsidianBorder.copy(alpha = 0.25f)
                                                else ObsidianBorder.copy(alpha = 0.42f)
                                            ),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .background(
                                            if (isSelected && isAvailable) GlowGreen.copy(alpha = 0.06f)
                                            else if (!isAvailable) ObsidianGlass.copy(alpha = 0.04f)
                                            else ObsidianGlass.copy(alpha = 0.10f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            if (!isAvailable) {
                                                showReflectionCapDialog = true
                                            } else {
                                                onDurationSelect(min)
                                            }
                                        }
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f, fill = false),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "$min",
                                                color = if (isAvailable) WarmOffWhite else MutedSilver.copy(alpha = 0.4f),
                                                fontSize = 30.sp,
                                                fontWeight = FontWeight.Light,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                                Text(
                                                    text = title,
                                                    color = if (isAvailable) WarmOffWhite else MutedSilver.copy(alpha = 0.5f),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = desc,
                                                    color = if (isAvailable) MutedSilver else MutedSilver.copy(alpha = 0.4f),
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isEnglish) "+$pts PTS" else "+$pts نقطة",
                                            color = if (isAvailable) GlowGreen else MutedSilver.copy(alpha = 0.4f),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            softWrap = false,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            val isChosenAvailable = viewModel.isDurationAvailable(chosenDuration)
                            if (isChosenAvailable) {
                                Button(
                                    onClick = { onStepChange("PRE_FLIGHT") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("continue_to_preflight_btn"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = GlowGreen,
                                        contentColor = ObsidianBg
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (isEnglish) "CONTINUE TO PRE-FLIGHT" else "الاستمرار إلى مرحلة ما قبل البدء",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            // White Glass / Tadabbur Pre-Commitment Card
                            TadabburCommitmentCard(
                                viewModel = viewModel,
                                isEnglish = isEnglish,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            
                            // Reflection Gallery Header
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isEnglish) "PREVIOUS WHITEBOARD" else "سجل أفكار اللوحة البيضاء",
                                        color = GlowGreen,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(GlowGreen.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isEnglish) "${whiteboardSessions.size} THREADS" else "${whiteboardSessions.size} مواضيع",
                                            color = GlowGreen,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            softWrap = false,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            if (whiteboardSessions.isEmpty()) {
                                // High-fidelity empty state card matching premium aesthetics!
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(BorderStroke(1.dp, ObsidianBorder), RoundedCornerShape(10.dp))
                                        .background(ObsidianSurface.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                        .padding(18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("📝", fontSize = 28.sp)
                                        Text(
                                            text = if (isEnglish) "No whiteboard thread yet." else "دفتر الأفكار فارغ.",
                                            color = WarmOffWhite,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = Outfit
                                        )
                                        Text(
                                            text = if (isEnglish) "After TAP 2, photograph the sentence you leave behind. Tomorrow starts from that exact line."
                                                   else "أكمل جلسة الممحاة، واترك جملة غير مكتملة على اللوحة، واحفظ الصورة لتراها هنا.",
                                            color = MutedSilver,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            fontFamily = Outfit,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                    }
                                }
                            } else {
                                // Horizontal scrollable gallery row (using a fixed height to prevent inner infinite height)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(152.dp)
                                ) {
                                    LazyRow(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(whiteboardSessions) { session ->
                                            val dateStr = remember(session.timestamp) {
                                                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                                sdf.format(Date(session.timestamp))
                                            }
                                            
                                            Box(
                                                modifier = Modifier
                                                    .width(215.dp)
                                                    .fillMaxHeight()
                                                    .border(BorderStroke(1.dp, GlowGreen.copy(alpha = 0.28f)), RoundedCornerShape(10.dp))
                                                    .background(ObsidianSurface, RoundedCornerShape(10.dp))
                                                    .clickable { selectedSessionForDetail = session }
                                                    .padding(10.dp)
                                            ) {
                                                Column(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = dateStr.uppercase(),
                                                            color = GlowGreen,
                                                            fontSize = 11.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Black
                                                        )
                                                        
                                                        Box(
                                                            modifier = Modifier
                                                                .background(
                                                                    color = if (session.photoStatus != "skipped") GlowGreen.copy(alpha = 0.15f) else SteelShadow.copy(alpha = 0.1f),
                                                                    shape = RoundedCornerShape(4.dp)
                                                                )
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = if (session.photoStatus != "skipped") (if (isEnglish) "📷 PHOTO" else "📷 صورة") else (if (isEnglish) "📝 NOTE" else "📝 ملاحظة"),
                                                                color = if (session.photoStatus != "skipped") GlowGreen else MutedSilver,
                                                                fontSize = 9.sp,
                                                                fontFamily = FontFamily.Monospace,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                    
                                                    WhiteboardPhotoFrame(
                                                        imagePath = session.imagePath,
                                                        note = if (!session.userNote.isNullOrEmpty()) session.userNote else (if (isEnglish) "Erase board, leaving one unfinished sentence..." else "امسح اللوحة، واترك جملة واحدة غير مكتملة..."),
                                                        isEnglish = isEnglish,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(72.dp)
                                                    )
                                                    
                                                    Text(
                                                        text = if (isEnglish) "⏱️ ${session.duration ?: 25} MIN COMPLIANCE" else "⏱️ ${session.duration ?: 25} دقيقة التزام",
                                                        color = MutedSilver,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "PRE_FLIGHT" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "PRE-FLIGHT" else "مرحلة ما قبل البدء",
                                color = MutedSilver,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                text = if (isEnglish) "Place phone at NFC zone." else "ضع الهاتف على منطقة NFC.",
                                color = WarmOffWhite,
                                fontSize = 25.sp,
                                lineHeight = 29.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = if (isEnglish) "Experience the thinking on paper effect" else "استشعر تأثير التفكير على الورق",
                                color = SignalAmber,
                                fontSize = 31.sp,
                                lineHeight = 34.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = Outfit,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Checklist items
                            listOf(
                                Triple("Phone at NFC zone on the Anchor", checklistPhoneFaceDown, onChecklistPhoneToggle),
                                Triple("Whiteboard or notebook open", checklistWhiteboardOpen, onChecklistWhiteboardToggle),
                                Triple("Physical timer set", checklistTimerSet, onChecklistTimerToggle)
                            ).forEach { (label, value, toggleAction) ->
                                val localizedLabel = when (label) {
                                    "Phone at NFC zone on the Anchor" -> if (isEnglish) "Phone at NFC zone on the Anchor" else "ضع الهاتف على منطقة NFC على المرساة"
                                    "Whiteboard or notebook open" -> if (isEnglish) "Whiteboard or notebook open" else "افتح لوحة الأفكار أو الدفتر"
                                    "Physical timer set" -> if (isEnglish) "Physical timer set" else "اضبط المؤقت المادي"
                                    else -> label
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(BorderStroke(1.dp, ObsidianBorder.copy(alpha = 0.42f)), RoundedCornerShape(8.dp))
                                        .background(ObsidianGlass.copy(alpha = 0.10f), shape = RoundedCornerShape(8.dp))
                                        .clickable { toggleAction(!value) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = when(label) {
                                                "Physical timer set" -> "⏱️"
                                                "Whiteboard or notebook open" -> "📖"
                                                else -> "🔌"
                                            },
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = localizedLabel,
                                            color = WarmOffWhite,
                                            fontSize = 15.sp,
                                            lineHeight = 19.sp,
                                            maxLines = 2
                                        )
                                    }
                                    Switch(
                                        checked = value,
                                        onCheckedChange = toggleAction,
                                        modifier = Modifier.padding(start = 10.dp),
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = GlowGreen,
                                            checkedTrackColor = GlowGreen.copy(alpha = 0.20f)
                                        )
                                    )
                                }
                            }

                            Button(
                                onClick = { if (isEveningAnchorCompletedToday || !viewModel.isDurationAvailable(chosenDuration)) showReflectionCapDialog = true else onStepChange("TAP") },
                                enabled = checklistPhoneFaceDown && checklistWhiteboardOpen && checklistTimerSet,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("continue_to_tap1_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GlowGreen,
                                    contentColor = ObsidianBg,
                                    disabledContainerColor = ObsidianBorder
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (isEnglish) "PROCEED TO TAP ANCHOR" else "الانتقال إلى مسح المرساة", fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }

                    "TAP", "TAP_1" -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "📡 NFC SCANNER ACTIVE — STAGE 1" else "📡 ماسح NFC نشط — المرحلة الأولى",
                                color = GlowGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.5.sp
                            )
                            
                            // Visual wave pulse animation for physical NFC scanning (No screen tap bypass)
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .border(BorderStroke(1.dp, GlowGreen.copy(alpha = 0.5f)), RoundedCornerShape(60.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .border(BorderStroke(1.dp, GlowGreen.copy(alpha = 0.3f)), RoundedCornerShape(45.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .background(GlowGreen.copy(alpha = 0.15f), RoundedCornerShape(30.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("A", color = GlowGreen, fontSize = 30.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }

                            Text(
                                text = if (isEnglish) "Align Phone with Desktop Anchor" else "ضع الهاتف على منطقة NFC بالمرساة",
                                color = WarmOffWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isEnglish) "Hold the back of your phone directly over the NFC area on your white desktop organizer to start your focus ritual."
                                       else "حرّك وثبّت ظهر هاتفك مباشرة فوق منطقة NFC على منظم المكتب الأبيض لبدء طقوس التركيز.",
                                color = MutedSilver,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )

                            if (!notificationPolicyAccessGranted && !viewModel.hasNotificationPolicyAccess()) {
                                Button(
                                    onClick = { onRequestDndAccess() },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GlowGreen, contentColor = ObsidianBg),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(if (isEnglish) "GRANT DND ACCESS" else "منح إذن عدم الإزعاج", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                LaunchedEffect(anchorStep) {
                                    if (anchorStep == "TAP" || anchorStep == "TAP_1") {
                                        viewModel.nfcScannedEvent.collect { scannedId ->
                                            if (isEveningAnchorCompletedToday || !viewModel.isDurationAvailable(chosenDuration)) {
                                                showReflectionCapDialog = true
                                            } else if (nfcTagId.isEmpty()) {
                                                // Link tag if first time
                                                viewModel.registerNfcTag(scannedId)
                                                viewModel.startFocusSession(chosenDuration)
                                                onStepChange("SESSION")
                                                Toast.makeText(context, if (isEnglish) "Anchor linked! Focus session started." else "تم ربط المرساة! بدأت جلسة التركيز.", Toast.LENGTH_SHORT).show()
                                            } else if (scannedId == nfcTagId) {
                                                viewModel.startFocusSession(chosenDuration)
                                                onStepChange("SESSION")
                                                Toast.makeText(context, if (isEnglish) "Anchor verified! Focus session started." else "تم التحقق من المرساة! بدأت جلسة التركيز.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, if (isEnglish) "❌ Unrecognized NFC Tag ($scannedId). Use the registered Desktop Anchor." else "❌ علامة NFC غير مطابقة. ضع الهاتف على مرساة المكتب المسجلة.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        if (isEveningAnchorCompletedToday || !viewModel.isDurationAvailable(chosenDuration)) {
                                            showReflectionCapDialog = true
                                        } else {
                                            if (nfcTagId.isEmpty()) {
                                                viewModel.registerNfcTag("VIRTUAL_SOFT_TAG")
                                            }
                                            viewModel.startFocusSession(chosenDuration)
                                            onStepChange("SESSION")
                                            Toast.makeText(context, if (isEnglish) "⚡ Soft Anchor simulated! Focus session started." else "⚡ تم محاكاة المرساة! بدأت جلسة التركيز.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = GlowGreen.copy(alpha = 0.18f),
                                        contentColor = GlowGreen
                                    ),
                                    border = BorderStroke(1.dp, GlowGreen.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (isEnglish) "⚡ SIMULATE ANCHOR TAP (SOFT MODE)" else "⚡ محاكاة مسح المرساة (الوضع الافتراضي)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    "TAP_2" -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val isSessionEndAlarmRinging by viewModel.isSessionEndAlarmRinging.collectAsState()
                            if (isSessionEndAlarmRinging) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SignalAmber.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                        .border(BorderStroke(1.5.dp, SignalAmber), RoundedCornerShape(12.dp))
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("⏰", fontSize = 24.sp)
                                            Column {
                                                Text(
                                                    text = if (isEnglish) "SESSION COMPLETE" else "اكتملت الجلسة",
                                                    color = SignalAmber,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = Outfit
                                                )
                                                Text(
                                                    text = if (isEnglish) "Alarm is ringing" else "المنبه يرن حالياً",
                                                    color = WarmOffWhite,
                                                    fontSize = 12.sp,
                                                    fontFamily = Outfit
                                                )
                                            }
                                        }
                                        PremiumButton(
                                            text = if (isEnglish) "SILENCE" else "إيقاف",
                                            onClick = { viewModel.stopSessionEndAlarm() },
                                            modifier = Modifier.width(90.dp).height(38.dp),
                                            gradientColors = listOf(SignalAmber, SignalAmber.copy(alpha = 0.8f)),
                                            contentColor = ObsidianBg
                                        )
                                    }
                                }
                            }

                            Text(
                                text = if (isEnglish) "📡 RETURN NFC SCANNER ACTIVE" else "📡 ماسح تأكيد العودة نشط",
                                color = GlowGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.5.sp
                            )
                            
                            // Visual wave pulse animation for physical return scan (No screen tap bypass)
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .border(BorderStroke(1.dp, GlowGreen.copy(alpha = 0.5f)), RoundedCornerShape(60.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .border(BorderStroke(1.dp, GlowGreen.copy(alpha = 0.3f)), RoundedCornerShape(45.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .background(GlowGreen.copy(alpha = 0.15f), RoundedCornerShape(30.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("A", color = GlowGreen, fontSize = 30.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }

                            Text(
                                text = if (isEnglish) "Scan Desktop Anchor to Verify Return" else "امسح مرساة المكتب لتأكيد العودة",
                                color = WarmOffWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isEnglish) "Return to your desk and hold your phone over the white desktop organizer's NFC target to verify your physical presence and unlock reflection."
                                       else "عد إلى مكتبك وضع ظهر هاتفك فوق منطقة NFC على منظم المكتب الأبيض لتأكيد تواجدك الفعلي وفتح لوحة الأفكار.",
                                color = MutedSilver,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )

                            LaunchedEffect(anchorStep) {
                                if (anchorStep == "TAP_2") {
                                    viewModel.nfcScannedEvent.collect { scannedId ->
                                        if (nfcTagId.isEmpty() || scannedId == nfcTagId) {
                                            viewModel.stopSessionEndAlarm()
                                            closingNote = ""
                                            capturedWhiteboardPath = null
                                            pendingWhiteboardPath = null
                                            pendingWhiteboardUri = null
                                            Toast.makeText(context, if (isEnglish) "Desktop Anchor return verified! Complete your reflection." else "تم تأكيد العودة لمرساة المكتب! أكمل ملاحظتك.", Toast.LENGTH_SHORT).show()
                                            onStepChange("CLOSING")
                                        } else {
                                            Toast.makeText(context, if (isEnglish) "❌ Unrecognized NFC Tag ($scannedId). Use the registered Desktop Anchor." else "❌ علامة NFC غير مطابقة. استخدم مرساة المكتب المسجلة.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    viewModel.stopSessionEndAlarm()
                                    closingNote = ""
                                    capturedWhiteboardPath = null
                                    pendingWhiteboardPath = null
                                    pendingWhiteboardUri = null
                                    Toast.makeText(context, if (isEnglish) "⚡ Return verified (Soft Mode)! Complete your reflection." else "⚡ تم تأكيد العودة افتراضياً! أكمل ملاحظتك.", Toast.LENGTH_SHORT).show()
                                    onStepChange("CLOSING")
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GlowGreen.copy(alpha = 0.18f),
                                    contentColor = GlowGreen
                                ),
                                border = BorderStroke(1.dp, GlowGreen.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isEnglish) "⚡ SIMULATE RETURN NFC TAP" else "⚡ محاكاة تأكيد العودة بالمرساة",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    "CLOSING" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val isSessionEndAlarmRinging by viewModel.isSessionEndAlarmRinging.collectAsState()
                            if (isSessionEndAlarmRinging) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SignalAmber.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                        .border(BorderStroke(1.5.dp, SignalAmber), RoundedCornerShape(12.dp))
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("⏰", fontSize = 24.sp)
                                            Column {
                                                Text(
                                                    text = if (isEnglish) "SESSION COMPLETE" else "اكتملت الجلسة",
                                                    color = SignalAmber,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = Outfit
                                                )
                                                Text(
                                                    text = if (isEnglish) "Alarm is ringing" else "المنبه يرن حالياً",
                                                    color = WarmOffWhite,
                                                    fontSize = 12.sp,
                                                    fontFamily = Outfit
                                                )
                                            }
                                        }
                                        PremiumButton(
                                            text = if (isEnglish) "SILENCE" else "إيقاف",
                                            onClick = { viewModel.stopSessionEndAlarm() },
                                            modifier = Modifier.width(90.dp).height(38.dp),
                                            gradientColors = listOf(SignalAmber, SignalAmber.copy(alpha = 0.8f)),
                                            contentColor = ObsidianBg
                                        )
                                    }
                                }
                            }

                            Text(
                                text = if (isEnglish) "CLOSING PROTOCOL" else "بروتوكول الإغلاق",
                                color = GlowGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.4.sp
                            )
                            Text(
                                text = if (isEnglish) "Leave tomorrow one sentence." else "اترك لغدك جملة واحدة.",
                                color = WarmOffWhite,
                                fontSize = 28.sp,
                                lineHeight = 32.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Black,
                                fontFamily = Outfit,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = if (isEnglish) "Erase the board except the line you want your future self to find." else "امسح اللوحة باستثناء السطر الذي تريد أن تجده غداً.",
                                color = MutedSilver,
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center,
                                fontFamily = Outfit,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )

                            OutlinedTextField(
                                value = closingNote,
                                onValueChange = { closingNote = it },
                                placeholder = {
                                    Text(
                                        if (isEnglish) "Type the exact unfinished sentence left on the whiteboard." else "اكتب الجملة غير المكتملة المتروكة على اللوحة بدقة.",
                                        color = MutedSilver,
                                        fontSize = 13.sp
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = WarmOffWhite,
                                    unfocusedTextColor = WarmOffWhite,
                                    focusedContainerColor = ObsidianSurface,
                                    unfocusedContainerColor = ObsidianSurface,
                                    focusedBorderColor = GlowGreen,
                                    unfocusedBorderColor = ObsidianBorder
                                ),
                                maxLines = 3
                            )

                            WhiteboardPhotoFrame(
                                imagePath = capturedWhiteboardPath,
                                note = closingNote.ifBlank { if (isEnglish) "The next line begins here." else "السطر التالي يبدأ من هنا." },
                                isEnglish = isEnglish,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(176.dp)
                            )

                            Text(
                                text = if (capturedWhiteboardPath == null) {
                                    if (isEnglish) "Open the camera to capture the board (optional) before sealing." else "افتح الكاميرا وصوّر اللوحة (اختياري) قبل الإغلاق."
                                } else {
                                    if (isEnglish) "Photo captured. This becomes the next card in Previous Whiteboard." else "تم التقاط الصورة. سيصبح هذا السجل التالي في سجل أفكار اللوحة البيضاء."
                                },
                                color = if (capturedWhiteboardPath == null) SignalAmber else GlowGreen,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center,
                                fontFamily = Outfit,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth()
                            )

                            PremiumButton(
                                text = if (capturedWhiteboardPath == null) (if (isEnglish) "OPEN CAMERA" else "افتح الكاميرا") else (if (isEnglish) "RETAKE WHITEBOARD PHOTO" else "إعادة التقاط صورة اللوحة"),
                                onClick = { showCameraNfcWarning = true },
                                gradientColors = listOf(DeepPanel, ObsidianSurface),
                                contentColor = WarmOffWhite,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("take_whiteboard_photo_btn")
                            )

                            PremiumButton(
                                text = if (isEnglish) "SEAL WHITEBOARD SESSION" else "ختم جلسة اللوحة البيضاء",
                                enabled = true,
                                onClick = {
                                    val cleanNote = closingNote.trim().ifEmpty { null }
                                    viewModel.completeFocusSession(
                                        photoStatus = if (capturedWhiteboardPath != null) "archived" else "skipped",
                                        userNote = cleanNote,
                                        imagePath = capturedWhiteboardPath
                                    )
                                    onStepChange("VERIFIED")
                                },
                                gradientColors = listOf(GlowGreen, RefinedGreen),
                                contentColor = ObsidianBg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("seal_whiteboard_session_btn")
                            )
                        }
                    }

                    "VERIFIED" -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(GlowGreen.copy(alpha = 0.10f), RoundedCornerShape(32.dp))
                                    .border(BorderStroke(2.dp, GlowGreen), RoundedCornerShape(32.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✔️", fontSize = 35.sp)
                            }

                            Text(
                                text = if (isEnglish) "SESSION VERIFIED" else "تم التحقق من الجلسة",
                                color = GlowGreen,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            )

                            val earnedPoints = when (chosenDuration) {
                                10 -> 35
                                15 -> 45
                                else -> 25
                            }
                            Text(
                                text = "+$earnedPoints",
                                color = WarmOffWhite,
                                fontSize = 62.sp,
                                fontWeight = FontWeight.Light
                            )
                            Text(
                                text = if (isEnglish) "RESERVE POINTS" else "نقاط الاحتياطي",
                                color = MutedSilver,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.5.sp
                            )

                            Text(
                                text = if (isEnglish) "Streak extended. Daytime access secured. The Reserve remembers." else "تم تمديد السلسلة. تم تأمين الوصول النهاري. الاحتياطي يسجل الالتزام.",
                                color = WarmOffWhite,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = if (isEnglish) "Reserve balance successfully credited. Accountability record secured."
                                       else "تم إيداع الرصيد بنجاح. سجل الانضباط محفوظ.",
                                color = WarmSilverText,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                fontFamily = Outfit,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            PremiumButton(
                                text = if (isEnglish) "RETURN TO RESERVE" else "العودة للاحتياطي",
                                onClick = { onReturnToReserve() },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    // Details Dialog for browsing previous sessions
    selectedSessionForDetail?.let { session ->
        val dateDetailStr = remember(session.timestamp) {
            val sdf = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
            sdf.format(Date(session.timestamp))
        }
        val timeDetailStr = remember(session.timestamp) {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            sdf.format(Date(session.timestamp))
        }

        AlertDialog(
            onDismissRequest = { selectedSessionForDetail = null },
            title = {
                Column {
                    Text(
                        text = if (isEnglish) "THE THREAD YOU LEFT" else "الخيط الذي تركته",
                        color = GlowGreen,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dateDetailStr,
                        color = WarmOffWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = Outfit
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isEnglish) "Yesterday handed you this line. Continue from here, not from zero." else "لقد سلمك يوم الأمس هذا السطر. استمر من هنا، ليس من الصفر.",
                        color = MutedSilver,
                        fontSize = 14.sp,
                        fontFamily = Outfit
                    )

                    // Simulated Whiteboard Photo Frame with Gridlines (Polaroid style)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            WhiteboardPhotoFrame(
                                imagePath = session.imagePath,
                                note = if (!session.userNote.isNullOrEmpty()) session.userNote else (if (isEnglish) "Leaving board next action..." else "ترك اللوحة للإجراء التالي..."),
                                isEnglish = isEnglish,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (isEnglish) "PREVIOUS WHITEBOARD • SEALED AT $timeDetailStr" else "لوحة الأفكار السابقة • تم الختم في $timeDetailStr",
                                color = Color(0xFF1A1A1A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Metadata
                    GlassCard(borderColor = GlowGreen.copy(alpha = 0.3f)) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(if (isEnglish) "Duration:" else "المدة:", color = MutedSilver, fontSize = 12.sp, fontFamily = Outfit)
                                Text(if (isEnglish) "${session.duration ?: 25} minutes" else "${session.duration ?: 25} دقيقة", color = WarmOffWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Outfit)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(if (isEnglish) "Compliance reward:" else "مكافأة الالتزام:", color = MutedSilver, fontSize = 12.sp, fontFamily = Outfit)
                                val detailPoints = if (session.pointsChange > 0) session.pointsChange else when (session.duration) {
                                    45 -> 35
                                    55 -> 45
                                    else -> 25
                                }
                                Text(if (isEnglish) "+$detailPoints PTS (Credited)" else "+$detailPoints نقطة (تم الإيداع)", color = GlowGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(if (isEnglish) "Eraser NFC Decal:" else "ملصق اتصال الممحاة:", color = MutedSilver, fontSize = 12.sp, fontFamily = Outfit)
                                Text(session.nfcTagId?.take(12) ?: "ANCHOR_NFC_09X", color = WarmOffWhite, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val clipboardManager = LocalClipboardManager.current
                Button(
                    onClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(session.userNote ?: ""))
                        Toast.makeText(context, if (isEnglish) "Copied incomplete sentence!" else "تم نسخ الجملة غير المكتملة!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlowGreen, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (isEnglish) "📋 COPY SENTENCE" else "📋 نسخ الجملة", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { selectedSessionForDetail = null }
                ) {
                    Text(if (isEnglish) "DISMISS" else "إغلاق", color = WarmOffWhite, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = ObsidianSurface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        )
    }

    if (showCameraNfcWarning) {
        AlertDialog(
            onDismissRequest = { showCameraNfcWarning = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isEnglish) "NFC Interference Warning" else "تنبيه تداخل NFC",
                        color = SignalAmber,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Outfit
                    )
                }
            },
            text = {
                Text(
                    text = if (isEnglish) {
                        "Please lift your phone away from the Anchor NFC tag/eraser before opening the camera to prevent system NFC popups or NFC Tools from opening."
                    } else {
                        "يرجى رفع هاتفك عن ملصق/ممحاة NFC الخاصة بالمرساة قبل فتح الكاميرا لتجنب ظهور نوافذ NFC المنبثقة أو فتح تطبيق NFC Tools."
                    },
                    color = WarmOffWhite,
                    fontSize = 14.sp,
                    fontFamily = Outfit
                )
            },
            confirmButton = {
                PremiumButton(
                    text = if (isEnglish) "PROCEED TO CAMERA" else "الذهاب للكاميرا",
                    onClick = {
                        showCameraNfcWarning = false
                        launchWhiteboardCamera()
                    },
                    gradientColors = listOf(RefinedGreen, GlowGreen),
                    contentColor = ObsidianBg,
                    height = 42.dp
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { showCameraNfcWarning = false }
                ) {
                    Text(if (isEnglish) "CANCEL" else "إلغاء", color = MutedSilver, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = ObsidianSurface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        )
    }

    if (showReflectionCapDialog) {
        val dialogLayoutDirection = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
        CompositionLocalProvider(LocalLayoutDirection provides dialogLayoutDirection) {
            AlertDialog(
                onDismissRequest = { showReflectionCapDialog = false },
                containerColor = ObsidianSurface,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 6.dp,
                title = {
                    if (isEnglish) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🧭", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reflection Cap",
                                fontFamily = Outfit,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = WarmOffWhite
                            )
                        }
                    } else {
                        Text(
                            text = "حد جلسات التدبر",
                            fontFamily = Outfit,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = WarmOffWhite,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right,
                            style = TextStyle(
                                textDirection = TextDirection.Rtl,
                                textAlign = TextAlign.Right
                            )
                        )
                    }
                },
                text = {
                    Text(
                        text = if (isEnglish) {
                            "This is an initial drafting, thinking, and reflection tool, not for routine study or generic Pomodoro sprints. If you need a timer for that purpose, please visit the Survival Kit to acquire the offline physical analog Pomodoro."
                        } else {
                            "هذه الأداة مخصصة للتدبر (thinking on paper ) فقط،\nوليست للمذاكرة الروتينية أو فترات البومودورو المتكررة. إذا كنت\nبحاجة إلى مؤقت، تفضل بزيارة Survival Kit للحصول على\nمؤقت البومودورو الملموس"
                        },
                        color = WarmOffWhite,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        fontFamily = Outfit,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = if (isEnglish) TextAlign.Start else TextAlign.Right,
                        style = TextStyle(
                            textDirection = if (isEnglish) TextDirection.Ltr else TextDirection.Rtl,
                            textAlign = if (isEnglish) TextAlign.Start else TextAlign.Right
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://get-analog-anchor.com/survivalkit"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, if (isEnglish) "Could not open browser" else "تعذر فتح المتصفح", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GlowGreen,
                            contentColor = ObsidianBg
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isEnglish) "Visit Survival Kit" else "زيارة Survival Kit",
                            fontWeight = FontWeight.Bold,
                            fontFamily = Outfit,
                            fontSize = 13.sp
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showReflectionCapDialog = false }
                    ) {
                        Text(
                            text = if (isEnglish) "Close" else "إغلاق",
                            color = MutedSilver,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Outfit,
                            fontSize = 13.sp
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun WhiteboardPhotoFrame(
    imagePath: String?,
    note: String?,
    isEnglish: Boolean = true,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(imagePath) {
        imagePath?.let { BitmapFactory.decodeFile(it) }?.asImageBitmap()
    }
    Box(
        modifier = modifier
            .background(Color(0xFF1B2321), RoundedCornerShape(6.dp))
            .border(BorderStroke(1.dp, ObsidianBorder), RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Whiteboard reflection photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeColor = Color.White.copy(alpha = 0.05f)
                drawLine(strokeColor, start = Offset(size.width / 3f, 0f), end = Offset(size.width / 3f, size.height), strokeWidth = 1.dp.toPx())
                drawLine(strokeColor, start = Offset(size.width * 2f / 3f, 0f), end = Offset(size.width * 2f / 3f, size.height), strokeWidth = 1.dp.toPx())
                drawLine(strokeColor, start = Offset(0f, size.height / 3f), end = Offset(size.width, size.height / 3f), strokeWidth = 1.dp.toPx())
                drawLine(strokeColor, start = Offset(0f, size.height * 2f / 3f), end = Offset(size.width, size.height * 2f / 3f), strokeWidth = 1.dp.toPx())
            }
            Text(
                text = note?.takeIf { it.isNotBlank() } ?: (if (isEnglish) "Leaving board next action..." else "ترك اللوحة للإجراء التالي..."),
                color = WarmOffWhite,
                fontSize = 15.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                fontFamily = Outfit,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

// ==========================================
// 3. RUNAWAY TAB CONTENT
// TODO #14: This composable is implemented but not shown (no tab button in nav row).
//           Re-add to the tab list when the fitness/workout feature is ready.
// ==========================================
@Composable
fun RunawayTabContent(
    viewModel: AnchorViewModel,
    isEnglish: Boolean,
    onSyncLocation: () -> Unit,
    onEmergencyUnlockClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val reservePoints by viewModel.reservePoints.collectAsState()
    val stepsCount by viewModel.stepsCount.collectAsState()
    val workoutMode by viewModel.workoutMode.collectAsState()
    val fajrVerified by viewModel.fajrVerified.collectAsState()

    val stepGoal = if (workoutMode == "walk") 4000 else 2000
    val midpointSteps = stepGoal / 2

    val workoutStep = when {
        stepsCount >= stepGoal -> 3
        stepsCount >= midpointSteps -> 2
        else -> 1
    }

    val layoutDirection = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        Column {
            Text(
                text = if (isEnglish) "Runaway Workout" else "مسار الجري / المشي السريع",
                color = WarmOffWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Outfit
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        // --- Premium Sanctuary Toggle Segment Control ---
        val currentSanctuaryType by viewModel.sanctuaryType.collectAsState()
        val mosqueModeSelected = currentSanctuaryType == "mosque" || currentSanctuaryType == "both"
        val runawayModeSelected = currentSanctuaryType == "runaway" || currentSanctuaryType == "both"
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface, RoundedCornerShape(10.dp))
                .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (mosqueModeSelected) GlowGreen else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { 
                        onSyncLocation()
                        viewModel.saveSanctuaryMode("mosque")
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isEnglish) "🕌 Mosque Sanctuary" else "🕌 مرتكز المسجد",
                    color = if (mosqueModeSelected) ObsidianBg else MutedSilver,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = Outfit
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (runawayModeSelected) SignalAmber else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { 
                        onSyncLocation()
                        viewModel.saveSanctuaryMode("runaway")
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isEnglish) "🏃 Runaway Workout" else "🏃 مسار الجري / المشي السريع",
                    color = if (runawayModeSelected) ObsidianBg else MutedSilver,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = Outfit
                )
            }
        }
        // Header card — workout mode selector
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.2.dp, GlowGreen.copy(alpha = 0.35f)), RoundedCornerShape(12.dp))
                .background(ObsidianSurface, shape = RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TactileToken(
                        emoji = if (workoutMode == "walk") "🚶" else "🏃",
                        size = 36.dp,
                        emojiSize = 18.sp,
                        borderColor = GlowGreen
                    )
                    Column {
                        Text(
                            text = if (isEnglish) "WORKOUT MODE" else "وضع التمرين",
                            color = GlowGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = if (workoutMode == "walk") {
                                if (isEnglish) "Walking Workout — المشي السريع" else "المشي السريع"
                            } else {
                                if (isEnglish) "Running Workout" else "تمرين الجري"
                            },
                            color = WarmOffWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = Outfit
                        )
                    }
                }

                // Walk / Run toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("run" to "🏃 Run", "walk" to "🚶 Walk / المشي").forEach { (mode, label) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    BorderStroke(1.dp, if (workoutMode == mode) GlowGreen else ObsidianBorder),
                                    RoundedCornerShape(8.dp)
                                )
                                .background(
                                    if (workoutMode == mode) GlowGreen.copy(alpha = 0.1f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.setWorkoutMode(mode) }
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (workoutMode == mode) GlowGreen else MutedSilver,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Outfit
                            )
                        }
                    }
                }
            }
        }

        // Hard block alert
        if (reservePoints <= 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.5.dp, Color.Red), RoundedCornerShape(12.dp))
                    .background(Color.Red.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️", fontSize = 28.sp)
                    Column {
                        Text(
                            text = if (isEnglish) "HARD BLOCK ACTIVE" else "الحظر الصارم نشط",
                            color = Color.Red,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (isEnglish) "Zero reserve credits. Complete physical actions to restore access."
                                   else "رصيدك 0 نقاط. أكمل إجراءات حركية لاستعادة الوصول.",
                            color = WarmSilverText,
                            fontSize = 13.sp,
                            fontFamily = Outfit
                        )
                    }
                }
            }
        }

        // Workout progress tracker
        GlassCard(borderColor = GlowGreen) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEnglish) "WORKOUT PROGRESS" else "تقدم التمرين",
                        color = GlowGreen,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$stepsCount / $stepGoal",
                        color = GlowGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (workoutMode == "walk") {
                        if (isEnglish) "Walk at least 4,000 steps away from home to verify your workout and earn +30 reserve points."
                        else "اتخذ 4,000 خطوة على الأقل بعيداً عن المنزل لتأكيد تمرينك وكسب +30 نقطة."
                    } else {
                        if (isEnglish) "Run at least 2,000 steps away from home to verify your workout and earn +30 reserve points."
                        else "اجرِ 2,000 خطوة على الأقل بعيداً عن المنزل لتأكيد تمرينك وكسب +30 نقطة."
                    },
                    color = MutedSilver,
                    fontSize = 13.sp,
                    fontFamily = Outfit
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Embedded Map Canvas (Runaway Workout active baseline)
                val userLat by viewModel.userLatitude.collectAsState()
                val userLng by viewModel.userLongitude.collectAsState()
                
                EmbeddedInteractiveMapCanvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    userLat = userLat,
                    userLng = userLng,
                    isRunawayMode = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                PremiumButton(
                    text = if (isEnglish) "📍 Confirm Runway GPS Zone" else "📍 تأكيد الموقع الحالي كمسار الجري / المشي السريع",
                    onClick = {
                        viewModel.saveCurrentLocationAsSanctuary(
                            type = "runaway",
                            name = "Runaway GPS zone",
                            address = "Current live GPS workout region"
                        )
                        Toast.makeText(context, "✓ Runaway GPS zone pinned successfully!", Toast.LENGTH_SHORT).show()
                    },
                    gradientColors = listOf(RefinedGreen, GlowGreen),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Progress bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f).height(6.dp).background(GlowGreen, RoundedCornerShape(3.dp)))
                    Box(modifier = Modifier.weight(1f).height(6.dp).background(if (workoutStep >= 2) GlowGreen else ObsidianBorder, RoundedCornerShape(3.dp)))
                    Box(modifier = Modifier.weight(1f).height(6.dp).background(if (workoutStep >= 3) GlowGreen else ObsidianBorder, RoundedCornerShape(3.dp)))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0", color = MutedSilver, fontSize = 11.sp, fontFamily = Outfit)
                    Text("$midpointSteps", color = if (workoutStep >= 2) GlowGreen else MutedSilver, fontSize = 11.sp, fontFamily = Outfit)
                    Text("$stepGoal ✓", color = if (workoutStep >= 3) GlowGreen else MutedSilver, fontSize = 11.sp, fontFamily = Outfit)
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (workoutStep < 3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianBg, RoundedCornerShape(10.dp))
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isEnglish) "WAITING FOR PHONE MOTION SENSOR" else "في انتظار مستشعر الحركة",
                            color = MutedSilver,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GlowGreen.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                            .border(1.dp, GlowGreen, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isEnglish) "✓ WORKOUT COMPLETE — +30 PTS EARNED" else "✓ اكتمل التمرين — +30 نقطة مكتسبة",
                            color = GlowGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Outfit
                        )
                    }
                }
            }
        }

        // Step counter live display
        GlassCard(borderColor = ObsidianBorder) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isEnglish) "STEP COUNTER" else "عداد الخطوات",
                    color = MutedSilver,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "$stepsCount",
                        color = WarmOffWhite,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Outfit
                    )
                    Text(
                        text = if (isEnglish) "steps" else "خطوة",
                        color = MutedSilver,
                        fontSize = 16.sp,
                        fontFamily = Outfit,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Text(
                    text = if (isEnglish) "Tracked automatically by your phone's motion sensor."
                           else "تتبع تلقائي بواسطة مستشعر الحركة.",
                    color = MutedSilver,
                    fontSize = 12.sp,
                    fontFamily = Outfit
                )
            }
        }

    }
    }
}

// ==========================================
// PRAYER BUFFER & FIRST TAKBIR STATUS CARD
// ==========================================
@Composable
fun PrayerBufferTakbirStatusCard(
    viewModel: AnchorViewModel,
    isEnglish: Boolean
) {
    val context = LocalContext.current
    var currentTickMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000L)
            currentTickMs = System.currentTimeMillis()
        }
    }

    val bufferStatus = remember(currentTickMs) {
        viewModel.getLastPrayerBufferStatus()
    }
    val activePrayerWindow = remember(currentTickMs) {
        viewModel.getCurrentActivePrayerWindow()
    }

    val prayerName = if (isEnglish) bufferStatus.prayerNameEn else bufferStatus.prayerNameAr

    // Fajr is exempt from missed buffer warnings (enforced by alarm/NFC eraser)
    val isMissed = bufferStatus.isBufferClosed && bufferStatus.prayerKey != "fajr"
    val isUnacknowledgedMiss = isMissed && !bufferStatus.isAcknowledged

    // Show during active buffer windows, while prayer is being attended,
    // if missed and NOT yet acknowledged (stays until acknowledged),
    // or for a brief 20-minute window after being acknowledged.
    val shouldDisplayCard = bufferStatus.isCurrentlyActive ||
            (bufferStatus.isAttended && activePrayerWindow != null) ||
            isUnacknowledgedMiss ||
            (bufferStatus.isAcknowledged && bufferStatus.minutesSinceClosed <= 20)

    if (!shouldDisplayCard) return

    GlassCard(
        borderColor = if (bufferStatus.isAttended) GlowGreen
                      else if (bufferStatus.isCurrentlyActive) SignalAmber
                      else if (isUnacknowledgedMiss) SignalAmber
                      else ObsidianBorder
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        bufferStatus.isAttended -> if (isEnglish) "✓ $prayerName Soft Landing Active" else "✓ تم تفعيل الهبوط الناعم لصلاة $prayerName"
                        bufferStatus.isCurrentlyActive -> if (isEnglish) "🛡️ $prayerName Soft Landing Active" else "🛡️ الهبوط الناعم لصلاة $prayerName نشط"
                        isUnacknowledgedMiss -> if (isEnglish) "⚠️ $prayerName Soft Landing Bypassed" else "⚠️ فات وقت الاستعداد لصلاة $prayerName"
                        isMissed -> if (isEnglish) "✓ $prayerName Miss Acknowledged" else "✓ تم إقرار فوات صلاة $prayerName"
                        else -> if (isEnglish) "SOFT LANDING (SILENT MODE)" else "الهبوط الناعم (وضع الصامت)"
                    },
                    color = when {
                        bufferStatus.isAttended -> GlowGreen
                        bufferStatus.isCurrentlyActive -> SignalAmber
                        isUnacknowledgedMiss -> SignalAmber
                        isMissed -> MutedSilver
                        else -> WarmOffWhite
                    },
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1
                )

                if (bufferStatus.isAttended) {
                    Text(
                        text = if (isEnglish) "✓ VERIFIED" else "✓ موثّق",
                        color = GlowGreen,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                } else if (bufferStatus.isCurrentlyActive) {
                    Text(
                        text = if (isEnglish) "⏳ OPEN (${bufferStatus.minutesLeft}m)" else "⏳ متاح (${bufferStatus.minutesLeft} د)",
                        color = SignalAmber,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                } else if (isUnacknowledgedMiss) {
                    Text(
                        text = if (isEnglish) "-7 PTS" else "-7 نقاط",
                        color = SignalAmber,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                } else if (isMissed) {
                    Text(
                        text = if (isEnglish) "ACKNOWLEDGED" else "تم الإقرار",
                        color = MutedSilver,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                } else {
                    Text(
                        text = if (isEnglish) "STANDBY" else "في الانتظار",
                        color = MutedSilver,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }

            // When active and not yet attended, show the action button to enter buffer & silence phone:
            if (bufferStatus.isCurrentlyActive && !bufferStatus.isAttended && activePrayerWindow != null) {
                Spacer(modifier = Modifier.height(2.dp))
                PremiumButton(
                    text = if (isEnglish) "🛡️ Activate $prayerName Soft Landing" else "🛡️ تفعيل الهبوط الناعم لصلاة $prayerName وتفعيل الصامت",
                    onClick = {
                        val (success, message) = viewModel.activatePrayerBuffer(
                            prayerKey = activePrayerWindow.prayerKey,
                            prayerNameEn = activePrayerWindow.displayName,
                            prayerNameAr = activePrayerWindow.displayNameArabic,
                            restoreTimeMs = activePrayerWindow.restoreTimeMs,
                            isEnglish = isEnglish
                        )
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    },
                    gradientColors = listOf(RefinedGreen, GlowGreen),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // When missed and not yet acknowledged, show the clean, dignified Acknowledge button:
            if (isUnacknowledgedMiss) {
                Spacer(modifier = Modifier.height(2.dp))
                PremiumButton(
                    text = if (isEnglish) "✍️ Acknowledge (-7 PTS)" else "✍️ إقرار (-7 نقاط)",
                    onClick = {
                        viewModel.acknowledgeMissedPrayerBuffer(
                            prayerKey = bufferStatus.prayerKey,
                            prayerName = bufferStatus.prayerNameEn
                        )
                        Toast.makeText(
                            context,
                            if (isEnglish) "Acknowledged (-7 PTS)" else "تم الإقرار (-7 نقاط)",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    gradientColors = listOf(Color(0xFF7A3800), SignalAmber),
                    contentColor = WarmOffWhite,
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                )
            }
        }
    }
}

@Composable
private fun FajrClockToken(
    is7DayLocked: Boolean,
    isAlarmEnabled: Boolean,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 42.dp
) {
    val cyanAccent = Color(0xFF7DEEFF)
    val isGlow = is7DayLocked || isAlarmEnabled
    val borderColor = if (is7DayLocked) cyanAccent else if (isAlarmEnabled) cyanAccent.copy(alpha = 0.85f) else ObsidianBorder
    val clockColor = if (isGlow) cyanAccent else MutedSilver

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (is7DayLocked) {
                    Modifier.shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = cyanAccent.copy(alpha = 0.4f),
                        spotColor = cyanAccent.copy(alpha = 0.4f)
                    )
                } else Modifier
            )
            .background(Color(0xFF0F171A), RoundedCornerShape(12.dp))
            .border(1.6.dp, borderColor, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.52f)) {
            val strokeW = 1.6.dp.toPx()
            val radius = (this.size.minDimension / 2f) - (strokeW / 2f)
            val c = this.center

            // Outer dial
            drawCircle(
                color = clockColor,
                radius = radius,
                style = Stroke(width = strokeW)
            )

            // Center pivot dot
            drawCircle(
                color = clockColor,
                radius = 1.8.dp.toPx(),
                center = c
            )

            // Hour hand pointing towards ~4 o'clock
            val hourAngleRad = Math.toRadians(120.0).toFloat()
            val hourLen = radius * 0.52f
            drawLine(
                color = clockColor,
                start = c,
                end = Offset(
                    x = c.x + (hourLen * kotlin.math.cos(hourAngleRad)),
                    y = c.y + (hourLen * kotlin.math.sin(hourAngleRad))
                ),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )

            // Minute hand pointing towards 12 o'clock
            drawLine(
                color = clockColor,
                start = c,
                end = Offset(
                    x = c.x,
                    y = c.y - (radius * 0.72f)
                ),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }

        if (isAlarmEnabled || is7DayLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .background(
                        color = if (is7DayLocked) cyanAccent else cyanAccent.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Text(
                    text = if (is7DayLocked) "7D" else "1D",
                    color = Color(0xFF0A1215),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 9.sp
                )
            }
        }
    }
}

// ==========================================
// 3. MOSQUE TAB CONTENT
// ==========================================
@Composable
fun MosqueTabContent(
    viewModel: AnchorViewModel,
    isRetreatActive: Boolean,
    retreatSecondsRemaining: Int,
    isEnglish: Boolean,
    onRetreatToggle: (Boolean) -> Unit,
    onSyncLocation: () -> Unit,
    onVerifyClick: (String) -> Unit,
    onOpenFajrSettings: () -> Unit,
) {
    val context = LocalContext.current

    val dndAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkHardwarePermissions()
    }
    val requestDndAccess = {
        try {
            dndAccessLauncher.launch(viewModel.notificationPolicyAccessIntent())
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open Do Not Disturb access settings.", Toast.LENGTH_SHORT).show()
        }
    }
    val notificationPolicyAccessGranted by viewModel.notificationPolicyAccessGranted.collectAsState()
    val isLocationPermissionGranted by viewModel.isLocationPermissionGranted.collectAsState()
    val isLocationServiceEnabled by viewModel.isLocationServiceEnabled.collectAsState()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val fineGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.checkLocationStatus()
        if (fineGranted || coarseGranted) {
            viewModel.requestRealLocation()
            viewModel.refreshNearbyMosques()
        }
    }

    // Check location status silently on entering Mosque Tab if permission was already granted
    LaunchedEffect(Unit) {
        viewModel.checkLocationStatus()
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            viewModel.requestRealLocation()
            viewModel.refreshNearbyMosques()
        }
    }

    val activityRecognitionPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.refreshStepSensorRegistration()
        } else {
            Toast.makeText(context, "Step permission is needed to verify wake proof.", Toast.LENGTH_LONG).show()
        }
    }
    val userLocationName by viewModel.userLocationName.collectAsState()
    val fajrAlarmEnabled by viewModel.fajrAlarmEnabled.collectAsState()
    val fajrWakeVerified by viewModel.fajrWakeVerified.collectAsState()
    val fajrWakeStepVerificationActive by viewModel.fajrWakeStepVerificationActive.collectAsState()
    val fajrWakeStepsAfterDismiss by viewModel.fajrWakeStepsAfterDismiss.collectAsState()
    val sleepPressureStatus by viewModel.sleepPressureStatus.collectAsState()
    val sleepPressureTimeLeftMs by viewModel.sleepPressureTimeLeftMs.collectAsState()
    val fajrAlarmAyahConstraint by viewModel.fajrAlarmAyahConstraint.collectAsState()
    val fajrAlarmAccountabilityPinEnabled by viewModel.fajrAlarmAccountabilityPinEnabled.collectAsState()
    val fajrAlarmNfcLocked by viewModel.fajrAlarmNfcLocked.collectAsState()
    var showNfcConfirmDialog by remember { mutableStateOf(false) }

    val nextPrayerCountdown by viewModel.nextPrayerCountdown.collectAsState()
    val nextPrayerName by viewModel.nextPrayerName.collectAsState()
    val prayerTimesMap by viewModel.prayerTimesMap.collectAsState()
    val isFriday by viewModel.isFriday.collectAsState()

    val fajrWakeProofStatus = when {
        fajrAlarmNfcLocked -> if (isEnglish) "📡 NFC LOCK • LOCKED" else "📡 قفل NFC • مقفل"
        fajrAlarmAccountabilityPinEnabled -> if (isEnglish) "🔒 PIN PROTECTED" else "🔒 محمي برمز PIN"
        else -> if (isEnglish) "📡 NFC LOCK • READY" else "📡 قفل NFC • جاهز"
    }

    LaunchedEffect(fajrWakeStepVerificationActive) {
        if (
            fajrWakeStepVerificationActive &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            activityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    val scrollState = rememberScrollState()
    val fajrVerified by viewModel.fajrVerified.collectAsState()
    val maghribVerified by viewModel.maghribVerified.collectAsState()
    
    // In-app Mosque Selection popup state
    var activeMosqueSelection by remember { mutableStateOf<Triple<String, String, Pair<Double, Double>>?>(null) }
    var activeNavigationMosqueName by remember { mutableStateOf<String?>(null) }
    var isMosqueListExpanded by remember { mutableStateOf(false) }
    val outdoorSanctuaryName by viewModel.outdoorSanctuaryName.collectAsState()
    val autoOpenPartnerPairingSheet by viewModel.showPartnerPairingSheet.collectAsState()
    val showPartnerCovenantGuiltBanner by viewModel.showPartnerCovenantGuiltBanner.collectAsState()
    var showPartnerPairingSheet by remember { mutableStateOf(false) }
    var activePartnerSession by remember { mutableStateOf(PartnerSessionPrefs.getActiveSession(context)) }
    var recentSoloRoutineStatus by remember { mutableStateOf<String?>(null) }
    var soloStatusResetJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(showPartnerCovenantGuiltBanner) {
        if (showPartnerCovenantGuiltBanner) {
            triggerHighVibration(context)
            kotlinx.coroutines.delay(2500L)
            viewModel.dismissPartnerCovenantGuiltBanner()
        }
    }

    LaunchedEffect(autoOpenPartnerPairingSheet) {
        if (autoOpenPartnerPairingSheet) {
            viewModel.deactivateSoloRoutine()
            showPartnerPairingSheet = true
            viewModel.setShowPartnerPairingSheet(false)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val session = PartnerSessionPrefs.getActiveSession(context)
            if (session != null && !session.isActivated && session.isExpired()) {
                viewModel.handlePartnerSessionPendingExpired()
                activePartnerSession = null
            } else {
                activePartnerSession = session
            }
            kotlinx.coroutines.delay(1000L)
        }
    }

    val layoutDirection = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        // ---------------- "SALAAT FIRST" FAJR / JUMU'AH WIDGET ----------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.2.dp, if (isFriday) RefinedGreen.copy(alpha = 0.5f) else GlowGreen.copy(alpha = 0.28f)), RoundedCornerShape(16.dp))
                .background(ObsidianSurface, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(
                            if (isDarkThemeGlobal) {
                                if (isFriday) Color(0xFF0F1E19) else Color(0xFF0F171A)
                            } else {
                                if (isFriday) Color(0xFFE7F4EE) else Color(0xFFEAF3F6)
                            }
                        )
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val minaretBrush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = if (isDarkThemeGlobal) {
                                if (isFriday) listOf(Color(0x3300FF99), Color(0x0500FF99)) else listOf(Color(0x337DEEFF), Color(0x057DEEFF))
                            } else {
                                if (isFriday) listOf(Color(0x55007A8C), Color(0x11007A8C)) else listOf(Color(0x4400808F), Color(0x1000808F))
                            }
                        )
                        drawRect(minaretBrush, topLeft = androidx.compose.ui.geometry.Offset(60f, 10f), size = androidx.compose.ui.geometry.Size(26f, 180f))
                        drawCircle(minaretBrush, radius = 18f, center = androidx.compose.ui.geometry.Offset(73f, 10f))
                        drawRect(minaretBrush, topLeft = androidx.compose.ui.geometry.Offset(size.width - 86f, 10f), size = androidx.compose.ui.geometry.Size(26f, 180f))
                        drawCircle(minaretBrush, radius = 18f, center = androidx.compose.ui.geometry.Offset(size.width - 73f, 10f))
                        drawArc(
                            brush = minaretBrush,
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = androidx.compose.ui.geometry.Offset(size.width / 2 - 100f, 20f),
                            size = androidx.compose.ui.geometry.Size(200f, 140f)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = nextPrayerName,
                            color = WarmOffWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = Outfit,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = nextPrayerCountdown,
                            color = if (isFriday) RefinedGreen else GlowGreen,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("📍", fontSize = 11.sp)
                            Text(
                                text = userLocationName,
                                color = MutedSilver,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Outfit
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DeepPanel)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (!isFriday) {
                                    showNfcConfirmDialog = true
                                }
                            }
                    ) {
                        if (isFriday) {
                            TactileToken(
                                emoji = "🕌",
                                size = 42.dp,
                                emojiSize = 20.sp,
                                borderColor = RefinedGreen
                            )
                        } else {
                            FajrClockToken(
                                is7DayLocked = fajrAlarmNfcLocked,
                                isAlarmEnabled = fajrAlarmEnabled,
                                size = 42.dp
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = if (isFriday) {
                                    if (isEnglish) "Friday Attendance (الجمعة)" else "صلاة الجمعة"
                                } else {
                                    if (isEnglish) "Fajr Alarm (الفجر)" else "منبه الفجر"
                                },
                                color = WarmOffWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = Outfit
                            )
                            Text(
                                text = if (isFriday) (prayerTimesMap["dhuhr"] ?: "11:48 AM") else (prayerTimesMap["fajr"] ?: "03:15 AM"),
                                color = if (isFriday) RefinedGreen else Color(0xFF7DEEFF),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (isFriday) {
                                Text(
                                    text = if (isEnglish) "🔒 JUMU'AH LOCK (NON-REVERSIBLE)" else "🔒 التزام الجمعة (غير قابل للإلغاء)",
                                    color = RefinedGreen,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                val activeBadges = remember(fajrAlarmAyahConstraint, fajrAlarmAccountabilityPinEnabled, isEnglish) {
                                    val list = mutableListOf<String>()
                                    if (fajrAlarmAyahConstraint) {
                                        list.add(if (isEnglish) "+ AYAH" else "+ الآية")
                                    }
                                    if (fajrAlarmAccountabilityPinEnabled) {
                                        list.add(if (isEnglish) "🔒 PIN PROTECTED" else "🔒 محمي برمز")
                                    }
                                    list
                                }
                                var currentBadgeIndex by remember { mutableStateOf(0) }
                                LaunchedEffect(activeBadges) {
                                    if (activeBadges.size > 1) {
                                        while (true) {
                                            delay(3500L)
                                            currentBadgeIndex = (currentBadgeIndex + 1) % activeBadges.size
                                        }
                                    } else {
                                        currentBadgeIndex = 0
                                    }
                                }

                                if (activeBadges.isNotEmpty()) {
                                    val badgeText = activeBadges.getOrElse(currentBadgeIndex % activeBadges.size) { activeBadges[0] }
                                    Text(
                                        text = badgeText,
                                        color = Color(0xFF7DEEFF),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Original horizontal toggle switch
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 30.dp)
                            .background(
                                color = if (fajrAlarmEnabled) Color(0xFF7DEEFF).copy(alpha = 0.15f) else ObsidianBg,
                                shape = RoundedCornerShape(15.dp)
                            )
                            .border(
                                width = 1.2.dp,
                                color = if (fajrAlarmEnabled) Color(0xFF7DEEFF) else ObsidianBorder,
                                shape = RoundedCornerShape(15.dp)
                            )
                            .clickable { onOpenFajrSettings() }
                            .padding(4.dp),
                        contentAlignment = if (fajrAlarmEnabled) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(
                                    color = if (fajrAlarmEnabled) Color(0xFF7DEEFF) else MutedSilver,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }

        if (showNfcConfirmDialog) {
            val fajrDialogLayoutDirection = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
            CompositionLocalProvider(LocalLayoutDirection provides fajrDialogLayoutDirection) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showNfcConfirmDialog = false },
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                FajrClockToken(
                                    is7DayLocked = true,
                                    isAlarmEnabled = true,
                                    size = 36.dp
                                )
                                Text(
                                    text = if (isEnglish) "Repeat it Daily for 1 week ONLY" else "التكرار الأسبوعي\nلمنبه الفجر",
                                    color = WarmOffWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    lineHeight = 20.sp,
                                    fontFamily = Outfit
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFF7DEEFF).copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .background(Color(0xFF7DEEFF).copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (isEnglish) "⚡ 30 MIN BEFORE FAJR (MINIMUM)" else "⚡ قبل الفجر بـ 30 د (حد أدنى)",
                                    color = Color(0xFF7DEEFF),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = Outfit
                                )
                            }
                        }
                    },
                    text = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F171A), RoundedCornerShape(12.dp))
                                .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = if (isEnglish)
                                    "Rising before Fajr is a divine invitation and a blessed privilege. Set your sincere intention (نـيّة) for the whole week to gain the blessings of the Sunnah Fajr prayer. Once confirmed, your alarm stands firm all week to support your resolve."
                                else
                                    "الاستيقاظ قبل الفجر دعوةٌ مباركة وفضلٌ عظيم. اعقد نيتك الصادقة للأسبوع كاملاً لنيل بركة سنة ركعتي الفجر، وسيثبت منبهك طوال الأسبوع لدعم عزيمتك وصمودك.",
                                color = WarmOffWhite.copy(alpha = 0.9f),
                                fontSize = 13.5.sp,
                                lineHeight = 21.sp,
                                fontFamily = Outfit,
                                modifier = Modifier.fillMaxWidth(),
                                style = TextStyle(
                                    textDirection = if (isEnglish) TextDirection.Ltr else TextDirection.Rtl,
                                    textAlign = TextAlign.Start
                                )
                            )
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.Button(
                            onClick = {
                                viewModel.confirmFajrNfcLockdown(repeatDaily = true)
                                showNfcConfirmDialog = false
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF7DEEFF)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "CONFIRM & LOCK" else "تأكيد وقفل",
                                color = ObsidianBg,
                                fontWeight = FontWeight.Black,
                                fontFamily = Outfit,
                                fontSize = 13.sp
                            )
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { showNfcConfirmDialog = false }
                        ) {
                            Text(
                                text = if (isEnglish) "Cancel" else "إلغاء",
                                color = MutedSilver,
                                fontFamily = Outfit
                            )
                        }
                    },
                    containerColor = DeepPanel,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        // --- Prayer Buffer & Soft Landing Status Card ---
        PrayerBufferTakbirStatusCard(
            viewModel = viewModel,
            isEnglish = isEnglish
        )

        // --- Map Variables & State ---
        val userLat by viewModel.userLatitude.collectAsState()
        val userLng by viewModel.userLongitude.collectAsState()
        var mapCenterLat by remember(userLat) { mutableStateOf(userLat) }
        var mapCenterLng by remember(userLng) { mutableStateOf(userLng) }

        val stepsCount by viewModel.stepsCount.collectAsState()
        val outdoorSoloCountThisWeek by viewModel.outdoorSoloCountThisWeek.collectAsState()
        val isSoloRoutineActive by viewModel.isSoloRoutineActive.collectAsState()

        var outdoorSearchQuery by remember { mutableStateOf("") }
        val outdoorSearchResults by viewModel.outdoorSearchResults.collectAsState()
        val isSearchingOutdoor by viewModel.isSearchingOutdoor.collectAsState()
        var selectedSearchCandidate by remember { mutableStateOf<MapboxPlaceCandidate?>(null) }

        val stepGoal = 2000
        val midpointSteps = stepGoal / 2
        val workoutStep = when {
            stepsCount >= stepGoal -> 3
            stepsCount >= midpointSteps -> 2
            else -> 1
        }

        // --- Dual-Anchor Satellite Map Canvas with Floating Mapbox Search Bar Overlay ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            val pinsList = remember(selectedSearchCandidate) {
                selectedSearchCandidate?.let {
                    listOf(MapPin(it.name, it.address, it.lat, it.lng, "📍"))
                } ?: emptyList()
            }

            EmbeddedInteractiveMapCanvas(
                modifier = Modifier.fillMaxSize(),
                userLat = userLat,
                userLng = userLng,
                pins = pinsList,
                selectedPinName = selectedSearchCandidate?.name,
                isRunawayMode = true,
                showCentralCircuitTarget = true,
                isEnglish = isEnglish,
                isInitiallyLocked = false,
                parentScrollState = scrollState,
                onCenterChanged = { lat, lng ->
                    mapCenterLat = lat
                    mapCenterLng = lng
                },
                onTargetClick = {
                    onSyncLocation()
                    viewModel.requestRealLocation()
                },
                onLockToggle = { locked, lat, lng ->
                    if (locked && hasUsableGps(lat, lng)) {
                        val placeName = selectedSearchCandidate?.name?.takeIf { it.isNotBlank() }
                            ?: if (outdoorSanctuaryName.isNotBlank() && outdoorSanctuaryName != "GPS Anchor") outdoorSanctuaryName
                            else "Outdoor Sanctuary Hub"
                        val (saved, msg) = viewModel.saveOutdoorSanctuary(
                            name = placeName,
                            address = selectedSearchCandidate?.address ?: "Outdoor Destination",
                            lat = lat.toFloat(),
                            lng = lng.toFloat(),
                            isEnglish = isEnglish
                        )
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            )

            val lockedOutdoorSpots by viewModel.lockedOutdoorSpots.collectAsState()
            var isSearchFocused by remember { mutableStateOf(false) }

            androidx.activity.compose.BackHandler(enabled = isSearchFocused || outdoorSearchResults.isNotEmpty()) {
                isSearchFocused = false
                outdoorSearchQuery = ""
                viewModel.clearOutdoorSearchResults()
            }

            // Dismiss search suggestions when tapping anywhere outside
            if (isSearchFocused || outdoorSearchResults.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isSearchFocused = false
                            outdoorSearchQuery = ""
                            viewModel.clearOutdoorSearchResults()
                        }
                )
            }

            // Floating Dark-Glass Mapbox Places Search Bar Overlay at Bottom of Map (Unblocks top controls)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // Autocomplete Suggestions Dropdown (pops UP above the input bar)
                if (outdoorSearchResults.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DeepPanel.copy(alpha = 0.97f), RoundedCornerShape(10.dp))
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                            .padding(vertical = 4.dp)
                    ) {
                        outdoorSearchResults.forEach { place ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSearchCandidate = place
                                        mapCenterLat = place.lat
                                        mapCenterLng = place.lng
                                        outdoorSearchQuery = place.name
                                        isSearchFocused = false
                                        viewModel.clearOutdoorSearchResults()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📍", fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = place.name,
                                        color = WarmOffWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = Outfit,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = place.address,
                                        color = MutedSilver,
                                        fontSize = 10.sp,
                                        fontFamily = Outfit,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                } else if (isSearchFocused && outdoorSearchQuery.isEmpty() && lockedOutdoorSpots.isNotEmpty()) {
                    // 1-Tap Quick Suggestions: 3 Locked Favorite Outdoor Spots
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DeepPanel.copy(alpha = 0.97f), RoundedCornerShape(10.dp))
                            .border(1.dp, GlowGreen.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isEnglish) "⭐ 3 FAVORITE OUTDOOR SPOTS" else "⭐ الوجهات الخارجية الثلاث المعتمدة",
                            color = GlowGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                        lockedOutdoorSpots.forEach { spot ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSearchCandidate = MapboxPlaceCandidate(
                                            id = spot.id,
                                            name = spot.name,
                                            address = spot.address,
                                            lat = spot.lat,
                                            lng = spot.lng
                                        )
                                        mapCenterLat = spot.lat
                                        mapCenterLng = spot.lng
                                        outdoorSearchQuery = spot.name
                                        isSearchFocused = false
                                        viewModel.clearOutdoorSearchResults()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📍", fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = spot.name,
                                        color = WarmOffWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = Outfit,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = spot.address,
                                        color = MutedSilver,
                                        fontSize = 10.sp,
                                        fontFamily = Outfit,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Dark-Glass Search Bar (Centered text and placeholder)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianSurface.copy(alpha = 0.94f), RoundedCornerShape(10.dp))
                        .border(1.dp, GlowGreen.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔍", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    BasicTextField(
                        value = outdoorSearchQuery,
                        onValueChange = { newQuery ->
                            outdoorSearchQuery = newQuery
                            viewModel.searchOutdoorDestinations(newQuery)
                        },
                        textStyle = TextStyle(
                            color = WarmOffWhite,
                            fontSize = 12.sp,
                            fontFamily = Outfit,
                            textAlign = TextAlign.Center
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focusState ->
                                isSearchFocused = focusState.isFocused
                            },
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (outdoorSearchQuery.isEmpty()) {
                                    Text(
                                        text = if (isEnglish) "Search place or Plus Code..."
                                               else "ابحث أو أدخل Plus Code...",
                                        color = MutedSilver.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                        fontFamily = Outfit,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (isSearchingOutdoor) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = GlowGreen
                        )
                    } else if (outdoorSearchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                outdoorSearchQuery = ""
                                viewModel.clearOutdoorSearchResults()
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Text("✕", color = MutedSilver, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // --- Covenant Breach Ephemeral Guilt Banner ---
        if (showPartnerCovenantGuiltBanner) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SignalAmber.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                    .border(1.5.dp, SignalAmber, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isEnglish) {
                        "⚠️ You broke the circle covenant. 25 PTS have been deducted from all your friends ledger."
                    } else {
                        "⚠️ لقد كسرت عهد الرفاق! تم خصم 25 نقطة من رصيد جميع أصدقائك."
                    },
                    color = SignalAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Outfit,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // --- Outdoor Routine: Active Partner Shield HUD vs Mode Switching ---
        val currentPartnerSession = activePartnerSession
        if (currentPartnerSession != null && !currentPartnerSession.isExpired()) {
            val isShieldLive = currentPartnerSession.isActivated
            val remSecs = if (isShieldLive) currentPartnerSession.remainingSeconds() else currentPartnerSession.remainingPendingSeconds()
            if (!isShieldLive && remSecs <= 0L) {
                LaunchedEffect(Unit) {
                    viewModel.handlePartnerSessionPendingExpired()
                    activePartnerSession = null
                }
            }
            val clockText = String.format(java.util.Locale.US, "%02d:%02d", remSecs / 60, remSecs % 60)
            val hudBorderColor = if (isShieldLive) GlowGreen else SignalAmber
            val hudBadgeBg = if (isShieldLive) GlowGreen.copy(alpha = 0.15f) else SignalAmber.copy(alpha = 0.15f)
            val hudBadgeBorder = if (isShieldLive) GlowGreen.copy(alpha = 0.5f) else SignalAmber.copy(alpha = 0.5f)
            val hudBadgeColor = if (isShieldLive) GlowGreen else SignalAmber

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurface, RoundedCornerShape(14.dp))
                    .border(1.5.dp, hudBorderColor, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🛡️", fontSize = 16.sp)
                            Text(
                                text = if (isShieldLive) {
                                    if (isEnglish) "ACTIVE GROUP SHIELD" else "درع جلسة الرفاق نشط"
                                } else {
                                    if (isEnglish) "PENDING ACTIVATION" else "بانتظار التفعيل"
                                },
                                color = hudBadgeColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(hudBadgeBg, RoundedCornerShape(6.dp))
                                .border(1.dp, hudBadgeBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isShieldLive) "⏳ $clockText" else "⚠️ $clockText",
                                color = hudBadgeColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isEnglish) "🤝 Group: ${currentPartnerSession.partnerName}" else "🤝 الرفاق: ${currentPartnerSession.partnerName}",
                            color = WarmOffWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Outfit
                        )
                        if (!isShieldLive) {
                            Text(
                                text = if (isEnglish) {
                                    "Pending both PIN lock with in 5 min. Abandoning drops the session and inflicts a -25 PTS circle penalty for ALL"
                                } else {
                                    "بانتظار قفل الرمزين خلال 5 دقائق. الانسحاب يلغي الجلسة ويفرض غرامة 25- نقطة عهد على الجميع"
                                },
                                color = SignalAmber,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                fontFamily = Outfit
                            )
                        }
                    }

                    if (isShieldLive) {
                        Button(
                            onClick = {
                                PartnerPairingManager.launchOfflineChallenge(
                                    context = context,
                                    partnerName = currentPartnerSession.partnerName,
                                    sanctuaryName = "",
                                    durationMinutes = currentPartnerSession.durationMinutes,
                                    sessionId = currentPartnerSession.sessionId
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GlowGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "Open Shield App" else "فتح تطبيق الدرع",
                                color = ObsidianBg,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Outfit
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                PartnerPairingManager.launchOfflineChallenge(
                                    context = context,
                                    partnerName = currentPartnerSession.partnerName,
                                    sanctuaryName = "",
                                    durationMinutes = currentPartnerSession.durationMinutes,
                                    sessionId = currentPartnerSession.sessionId
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SignalAmber),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "🛡️ Complete Shield Activation" else "🛡️ إكمال تفعيل الدرع",
                                color = ObsidianBg,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Outfit
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // JOIN Button: Switchable anytime (deactivates solo and opens partner pairing)
                val joinActive = showPartnerPairingSheet
                val joinBg = if (joinActive) GlowGreen.copy(alpha = 0.2f)
                             else if (!isSoloRoutineActive) GlowGreen.copy(alpha = 0.12f)
                             else ObsidianSurface.copy(alpha = 0.5f)
                val joinBorder = if (joinActive) GlowGreen
                                 else if (!isSoloRoutineActive) GlowGreen.copy(alpha = 0.7f)
                                 else ObsidianBorder.copy(alpha = 0.4f)
                val joinTextColor = if (joinActive || !isSoloRoutineActive) GlowGreen else MutedSilver.copy(alpha = 0.5f)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(joinBg, RoundedCornerShape(12.dp))
                        .border(if (joinActive || !isSoloRoutineActive) 1.5.dp else 1.dp, joinBorder, RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.deactivateSoloRoutine()
                            showPartnerPairingSheet = true
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isEnglish) "JOIN GROUP" else "انضمام للرفاق",
                        color = joinTextColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Solo Routine Interactive Toggle Button
                val soloBorder = if (isSoloRoutineActive || workoutStep >= 3) GlowGreen else ObsidianBorder
                val soloBg = if (isSoloRoutineActive) GlowGreen.copy(alpha = 0.15f) else ObsidianSurface
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(soloBg, RoundedCornerShape(12.dp))
                        .border(if (isSoloRoutineActive || workoutStep >= 3) 1.5.dp else 1.2.dp, soloBorder, RoundedCornerShape(12.dp))
                        .clickable {
                            val msg = viewModel.toggleSoloOutdoorRoutine(isEnglish)
                            showExtendedToast(context, msg)
                            recentSoloRoutineStatus = msg
                            soloStatusResetJob?.cancel()
                            soloStatusResetJob = coroutineScope.launch {
                                kotlinx.coroutines.delay(7000L)
                                recentSoloRoutineStatus = null
                            }
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isEnglish) {
                            if (workoutStep >= 3) "SOLO VERIFIED ✓" else if (isSoloRoutineActive) "SOLO ACTIVE" else "SOLO ROUTINE"
                        } else {
                            if (workoutStep >= 3) "تم التوثيق ✓" else if (isSoloRoutineActive) "نشط ومتابع" else "الروتين الفردي"
                        },
                        color = if (isSoloRoutineActive || workoutStep >= 3) GlowGreen else WarmOffWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Step motion tracker: ONLY visible when Solo Routine is Active (hidden for Partner mode)
        if (isSoloRoutineActive && !showPartnerPairingSheet) {
            val completedSessions = outdoorSoloCountThisWeek.coerceIn(0, 7)
            val currentSessionNumber = (completedSessions + 1).coerceAtMost(7)
            val currentSessionSteps = stepsCount.coerceIn(0, 2000)

            GlassCard(borderColor = GlowGreen) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header: Title & PTS Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) {
                                if (completedSessions >= 7) "WEEKLY COMPOUND: 7 / 7 SESSIONS ✓"
                                else "WEEKLY COMPOUND: SESSION $currentSessionNumber / 7"
                            } else {
                                if (completedSessions >= 7) "المضاعفة الأسبوعية: 7 / 7 جلسات ✓"
                                else "المضاعفة الأسبوعية: الجلسة $currentSessionNumber / 7"
                            },
                            color = GlowGreen,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                        Box(
                            modifier = Modifier
                                .background(GlowGreen.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .border(1.dp, GlowGreen.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (completedSessions >= 7) "+175 PTS EARNED" else "+25 PTS / SESSION",
                                color = GlowGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Merged 7-Segment Progress Track (Height: 8.dp)
                    // Integrates the 7 weekly milestones directly into the progress line
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (index in 0 until 7) {
                            val isCompleted = index < completedSessions
                            val isCurrent = index == completedSessions && completedSessions < 7

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        color = if (isCompleted) GlowGreen
                                                else ObsidianBorder.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        width = if (isCurrent) 1.dp else 0.5.dp,
                                        color = if (isCurrent) GlowGreen.copy(alpha = 0.8f) else ObsidianBorder,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            ) {
                                if (isCurrent) {
                                    val fillFraction = (currentSessionSteps / 2000f).coerceIn(0f, 1f)
                                    if (fillFraction > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(fillFraction)
                                                .fillMaxHeight()
                                                .background(GlowGreen, RoundedCornerShape(4.dp))
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Progress Stats Text
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) {
                                if (completedSessions >= 7) "ALL 7 SESSIONS COMPLETED"
                                else "SESSION $currentSessionNumber • $currentSessionSteps / 2,000 STEPS"
                            } else {
                                if (completedSessions >= 7) "تم إكمال جميع الجلسات الـ 7"
                                else "الجلسة $currentSessionNumber • $currentSessionSteps / 2,000 خطوة"
                            },
                            color = if (currentSessionSteps >= 2000 || completedSessions >= 7) GlowGreen else WarmOffWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (isEnglish) "${completedSessions * 25} PTS EARNED" else "تم كسب ${completedSessions * 25} نقطة",
                            color = if (completedSessions > 0) GlowGreen else MutedSilver,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Status / Tap to Check Action Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (currentSessionSteps >= 2000) GlowGreen.copy(alpha = 0.12f) else GlowGreen.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (currentSessionSteps >= 2000) GlowGreen else GlowGreen.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                val statusMsg = viewModel.checkSoloRoutineStatus(isEnglish)
                                showExtendedToast(context, statusMsg)
                                recentSoloRoutineStatus = statusMsg
                                soloStatusResetJob?.cancel()
                                soloStatusResetJob = coroutineScope.launch {
                                    kotlinx.coroutines.delay(7000L)
                                    recentSoloRoutineStatus = null
                                }
                            }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (completedSessions >= 7) {
                            Text(
                                text = if (isEnglish) "✓ 7 SESSIONS VERIFIED (+175 PTS CLAIMED)" else "✓ تم توثيق 7 جلسات (+175 نقطة)",
                                color = GlowGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                        } else if (currentSessionSteps >= 2000) {
                            Text(
                                text = if (isEnglish) "✓ 2,000 STEPS VERIFIED (+25 PTS CLAIMED)" else "✓ تم توثيق 2,000 خطوة (+25 نقطة)",
                                color = GlowGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                        } else if (recentSoloRoutineStatus != null) {
                            Text(
                                text = recentSoloRoutineStatus!!,
                                color = GlowGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isEnglish) "TRACKING PHONE MOTION SENSOR" else "مستشعر الحركة نشط",
                                    color = WarmOffWhite.copy(alpha = 0.85f),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(GlowGreen.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                                        .border(1.dp, GlowGreen.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isEnglish) "TAP TO CHECK" else "المس للفحص",
                                        color = GlowGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        MaghribToIshaRetreatCard(
            viewModel = viewModel,
            context = context,
            isRetreatActive = isRetreatActive,
            retreatSecondsRemaining = retreatSecondsRemaining,
            maghribVerified = maghribVerified,
            isEnglish = isEnglish,
            notificationPolicyAccessGranted = notificationPolicyAccessGranted,
            onRequestDndAccess = requestDndAccess,
            onRetreatToggle = onRetreatToggle,
            onVerifyClick = onVerifyClick
        )

        // Emergency Unlock geofence card removed as per PIN custody model guidelines
        Spacer(modifier = Modifier.height(10.dp))
        
    }
    }

    if (showPartnerPairingSheet) {
        PartnerPairingBottomSheet(
            onDismiss = { showPartnerPairingSheet = false },
            isEnglish = isEnglish,
            sanctuaryName = outdoorSanctuaryName,
            localUserName = viewModel.getClientName()
        )
    }
}



@Composable
private fun MaghribToIshaRetreatCard(
    viewModel: AnchorViewModel,
    context: Context,
    isRetreatActive: Boolean,
    retreatSecondsRemaining: Int,
    maghribVerified: Boolean,
    isEnglish: Boolean,
    notificationPolicyAccessGranted: Boolean,
    onRequestDndAccess: () -> Unit,
    onRetreatToggle: (Boolean) -> Unit,
    onVerifyClick: (String) -> Unit
) {
    val layoutDirection = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
    val prayerTimesMap by viewModel.prayerTimesMap.collectAsState()
    val maghribDisplay = prayerTimesMap["maghrib"] ?: "18:11"
    val ishaDisplay = prayerTimesMap["isha"] ?: "19:42"
    val (isMaghribWindowActive, retreatTimeWindow) = remember { viewModel.isMaghribRetreatWindowActive() }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        GlassCard(borderColor = GlowGreen) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TactileToken(emoji = "🌙", size = 36.dp, emojiSize = 18.sp, borderColor = GlowGreen)
                        Text(
                            text = if (isEnglish) "SACRED RETREAT VAULT" else "خلوة المرتكز الآمنة",
                            color = GlowGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp
                        )
                    }
                    if (maghribVerified) {
                        Box(
                            modifier = Modifier
                                .background(RefinedGreen.copy(alpha = 0.15f), RoundedCornerShape(100.dp))
                                .border(BorderStroke(1.dp, RefinedGreen), RoundedCornerShape(100.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "✓ VERIFIED" else "✓ مؤكد",
                                color = RefinedGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = Outfit
                            )
                        }
                    } else {
                        PremiumButton(
                            text = if (isMaghribWindowActive) (if (isEnglish) "VERIFY" else "تحقق") else (if (isEnglish) "LOCKED" else "مقفل"),
                            onClick = {
                                if (!isMaghribWindowActive) {
                                    Toast.makeText(
                                        context,
                                        if (isEnglish) "Retreat verification opens during Maghrib-to-Isha prayer ($retreatTimeWindow)."
                                        else "يفتح التحقق من الخلوة أثناء وقت صلاة المغرب والعشاء ($retreatTimeWindow).",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@PremiumButton
                                }
                                onVerifyClick("MAGHRIB")
                            },
                            modifier = Modifier.width(96.dp).height(38.dp),
                            gradientColors = if (isMaghribWindowActive) listOf(RefinedGreen, GlowGreen) else listOf(DeepPanel, ObsidianSurface),
                            contentColor = if (isMaghribWindowActive) ObsidianBg else MutedSilver
                        )
                    }
                }

                Text(
                    text = if (isEnglish) "Maghrib-to-Isha Retreat" else "خلوة المغرب إلى العشاء",
                    color = WarmOffWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Outfit
                )
                Text(
                    text = if (isEnglish) {
                        "Remain inside the mosque sanctuary until Isha prayer. Strict lock engaged. Accumulate highest Reserve reward."
                    } else {
                        "ابق داخل مرتكز المسجد حتى صلاة العشاء. القفل الصارم نشط. احصل على أعلى مكافأة احتياطية."
                    },
                    color = WarmSilverText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontFamily = Outfit
                )

                if (!isRetreatActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(maghribDisplay, color = WarmOffWhite, fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(if (isEnglish) "MAGHRIB" else "المغرب", color = MutedSilver, fontSize = 12.sp, fontFamily = Outfit)
                        }
                        Box(
                            modifier = Modifier
                                .background(ObsidianSurface, RoundedCornerShape(100.dp))
                                .border(BorderStroke(1.dp, GlowGreen.copy(alpha = 0.3f)), RoundedCornerShape(100.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("+40 PTS", color = GlowGreen, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(ishaDisplay, color = WarmOffWhite, fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(if (isEnglish) "ISHA" else "العشاء", color = MutedSilver, fontSize = 12.sp, fontFamily = Outfit)
                        }
                    }

                    if (!maghribVerified) {
                        PremiumButton(
                            text = if (isEnglish) "VERIFY GPS AT MOSQUE FIRST" else "تحقق من موقع المسجد أولاً",
                            onClick = {
                                if (!isMaghribWindowActive) {
                                    Toast.makeText(
                                        context,
                                        if (isEnglish) "Retreat opens during the prayer window ($retreatTimeWindow)."
                                        else "تفتح الخلوة أثناء وقت الصلاة ($retreatTimeWindow).",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                onVerifyClick("MAGHRIB")
                            },
                            gradientColors = listOf(DeepPanel, ObsidianSurface),
                            contentColor = SignalAmber,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        PremiumButton(
                            text = if (isEnglish) "START SACRED RETREAT" else "بدء الخلوة المباركة",
                            onClick = {
                                if (!notificationPolicyAccessGranted && !viewModel.hasNotificationPolicyAccess()) {
                                    Toast.makeText(
                                        context,
                                        if (isEnglish) "Grant Do Not Disturb access, then start retreat." else "يرجى منح إذن عدم الإزعاج، ثم ابدأ الخلوة.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onRequestDndAccess()
                                } else {
                                    onRetreatToggle(true)
                                }
                            },
                            gradientColors = listOf(Color(0xFF00E5FF), Color(0xFF0097A7)),
                            contentColor = ObsidianBg,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (isEnglish) "TIME UNTIL RETREAT COMPLETE" else "الوقت المتبقي لاكتمال الخلوة",
                            color = MutedSilver,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )

                        Box(
                            modifier = Modifier
                                .background(ObsidianBg, RoundedCornerShape(16.dp))
                                .border(1.5.dp, GlowGreen.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 40.dp, vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val h = retreatSecondsRemaining / 3600
                            val m = (retreatSecondsRemaining % 3600) / 60
                            val s = retreatSecondsRemaining % 60
                            val formatTime = if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)

                            Text(
                                text = formatTime,
                                color = WarmOffWhite,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Light,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }

                        Text(
                            text = if (isEnglish) "Do not exit the mosque sanctuary geofence." else "لا تخرج من النطاق الجغرافي لمرتكز المسجد.",
                            color = SignalAmber,
                            fontSize = 14.sp,
                            fontFamily = Outfit,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PremiumButton(
                                text = if (isEnglish) "SIMULATE BREAK" else "محاكاة المغادرة",
                                onClick = {
                                    onRetreatToggle(false)
                                    viewModel.failFocusSession("Left Mosque Geofence during Retreat")
                                    Toast.makeText(context, if (isEnglish) "Retreat broken. Penalty recorded." else "تم كسر الخلوة. تم تسجيل الغرامة.", Toast.LENGTH_SHORT).show()
                                },
                                gradientColors = listOf(DeepPanel, ObsidianSurface),
                                contentColor = SignalAmber,
                                modifier = Modifier.weight(1f)
                            )

                            PremiumButton(
                                text = if (isEnglish) "COMPLETE" else "إكمال",
                                onClick = {
                                    onRetreatToggle(false)
                                    viewModel.addPoints(40, "Completed Maghrib-Isha Retreat", "retreat")
                                    Toast.makeText(context, if (isEnglish) "Retreat Complete! +40 pts" else "اكتملت الخلوة! +40 نقطة", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. RESERVE TAB CONTENT
// ==========================================
@Composable
fun ReserveTabContent(
    viewModel: AnchorViewModel,
    isEnglish: Boolean,
    onSyncLocation: () -> Unit,
    onOpenFajrSettings: () -> Unit,
    onStartProtocolClick: () -> Unit,
    onCheckInClick: () -> Unit
) {
    val context = LocalContext.current
    val activityRecognitionPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.refreshStepSensorRegistration()
        } else {
            Toast.makeText(context, "Step permission is needed to verify wake proof.", Toast.LENGTH_LONG).show()
        }
    }
    val userLocationName by viewModel.userLocationName.collectAsState()
    val fajrAlarmEnabled by viewModel.fajrAlarmEnabled.collectAsState()
    val fajrWakeVerified by viewModel.fajrWakeVerified.collectAsState()
    val fajrWakeStepVerificationActive by viewModel.fajrWakeStepVerificationActive.collectAsState()
    val fajrWakeStepsAfterDismiss by viewModel.fajrWakeStepsAfterDismiss.collectAsState()
    val sleepPressureStatus by viewModel.sleepPressureStatus.collectAsState()
    val sleepPressureTimeLeftMs by viewModel.sleepPressureTimeLeftMs.collectAsState()
    val fajrAlarmAyahConstraint by viewModel.fajrAlarmAyahConstraint.collectAsState()
    val fajrAlarmAccountabilityPinEnabled by viewModel.fajrAlarmAccountabilityPinEnabled.collectAsState()

    val nextPrayerCountdown by viewModel.nextPrayerCountdown.collectAsState()
    val nextPrayerName by viewModel.nextPrayerName.collectAsState()
    val prayerTimesMap by viewModel.prayerTimesMap.collectAsState()
    val isFriday by viewModel.isFriday.collectAsState()

    // Real dynamic computed times falling back gracefully
    val sunriseTime = prayerTimesMap["sunrise"] ?: "04:40 AM"
    val wakeupTime = prayerTimesMap["fajr"] ?: "03:15 AM"
    val bedtimeStr = prayerTimesMap["isha"] ?: "09:30 PM"
    val fajrWakeGoal = AnchorViewModel.FAJR_WAKE_STEP_GOAL
    val fajrWakeProofStatus = when {
        fajrWakeVerified -> "WAKE VERIFIED +25"
        fajrWakeStepVerificationActive -> "WALK ${fajrWakeStepsAfterDismiss.coerceIn(0, fajrWakeGoal)}/$fajrWakeGoal STEPS"
        else -> "Dismiss alarm, then walk $fajrWakeGoal steps"
    }

    LaunchedEffect(fajrWakeStepVerificationActive) {
        if (
            fajrWakeStepVerificationActive &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            activityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    // Parse hour integers for the visual circade dial
    val sunriseHour = try { sunriseTime.split(":")[0].toFloat() } catch(e: Exception) { 4.67f }
    val wakeupHour = try { wakeupTime.split(":")[0].toFloat() } catch(e: Exception) { 3.25f }
    val bedtimeHour = try { bedtimeStr.split(":")[0].toFloat() + 12f } catch(e: Exception) { 21.5f }
    var sleepDialFocus by remember { mutableStateOf(0) }

    val recommendation = when {
        userLocationName.contains("Busaiteen", ignoreCase = true) -> {
            if (isEnglish) {
                "In the Arabian summer, the day begins exceptionally early. An optimal bedtime of $bedtimeStr ensures full REM recovery prior to the $wakeupTime Fajr anchorage."
            } else {
                "في الصيف العربي، يبدأ اليوم مبكراً بشكل استثنائي. النوم المثالي في $bedtimeStr يضمن الاستشفاء الكامل للدماغ قبل رباط فجر $wakeupTime صباحاً."
            }
        }
        userLocationName.contains("Mecca", ignoreCase = true) -> {
            if (isEnglish) {
                "Sacred sanctuary alignment. Your biological clock syncs with the $wakeupTime Fajr calling, requiring a solid $bedtimeStr bedtime to protect cardiovascular balance."
            } else {
                "محاذاة المرتكز المقدس. تتزامن ساعتك البيولوجية مع نداء فجر $wakeupTime صباحاً، مما يتطلب موعد نوم صارم في $bedtimeStr لحماية التوازن القلبي."
            }
        }
        else -> {
            if (isEnglish) {
                "Aligning your biological clock with the natural astronomical Fajr anchorage at $wakeupTime ensures peak hormonal rhythm and neural recovery."
            } else {
                "إن محاذاة ساعتك البيولوجية مع رباط الفجر الفلكي الطبيعي في $wakeupTime يضمن ذروة الهرمونات والاستشفاء العصبي المتكامل."
            }
        }
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
}
}

@Composable
private fun DayGuardLaunchCard(onOpen: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, GlowGreen.copy(alpha = 0.32f)), RoundedCornerShape(12.dp))
            .background(ObsidianSurface.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
            .clickable { onOpen() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(GlowGreen.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    .border(BorderStroke(1.dp, GlowGreen.copy(alpha = 0.36f)), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "DAY",
                    color = GlowGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Analog Anchor Day Guard",
                    color = WarmOffWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Outfit,
                    maxLines = 1
                )
                Text(
                    text = "Open daytime mindful gateway",
                    color = MutedSilver,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontFamily = Outfit,
                    maxLines = 1
                )
            }

            Text(
                text = "OPEN",
                color = GlowGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun SleepPressureGateCard(
    status: String,
    timeLeftMs: Long,
    alarmEnabled: Boolean,
    ayahConstraintEnabled: Boolean,
    accountabilityEnabled: Boolean
) {
    val gateColor = when (status) {
        "MINIMUM" -> Color(0xFFFF4D4D)
        "LAST_WINDOW" -> SignalAmber
        "NIGHT_WINDOW" -> GlowGreen
        else -> MutedSilver
    }
    val watchLabel = when (status) {
        "MINIMUM" -> "SLEEP NOW"
        "LAST_WINDOW" -> "LAST GOOD WINDOW"
        "NIGHT_WINDOW" -> "NIGHT WINDOW"
        else -> "AUTO WATCH"
    }
    val watchMessage = when (status) {
        "MINIMUM" -> "The 5-hour floor is closing. Alarm pressure is active."
        "LAST_WINDOW" -> "Fajr is ${formatSleepCountdown(timeLeftMs)} away. Protect sleep now."
        "NIGHT_WINDOW" -> "After Isha, the discipline window is live."
        else -> "The app will warn when the 5-hour Fajr sleep floor approaches."
    }
    val pressureTitle = when (status) {
        "MINIMUM" -> "Minimum sleep window is closing"
        "LAST_WINDOW" -> "Last good sleep window"
        "NIGHT_WINDOW" -> "Night discipline window started"
        else -> "Sleep pressure monitor"
    }
    val pressureMessage = when (status) {
        "MINIMUM" -> "Fajr is ${formatSleepCountdown(timeLeftMs)} away. Sleep now."
        "LAST_WINDOW" -> "Fajr is ${formatSleepCountdown(timeLeftMs)} away. Stay away from screen stimulation and protect the final full sleep window."
        "NIGHT_WINDOW" -> "After Isha, the night discipline window has started."
        else -> "Fajr pressure will rise automatically after Isha."
    }

    GlassCard(borderColor = gateColor.copy(alpha = 0.58f)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(gateColor.copy(alpha = 0.13f), CircleShape)
                        .border(1.2.dp, gateColor.copy(alpha = 0.62f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "5h",
                        color = gateColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = watchLabel,
                        color = gateColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = watchMessage,
                        color = WarmOffWhite,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Outfit
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ObsidianBorder))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pressureTitle.uppercase(Locale.US),
                        color = gateColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pressureMessage,
                        color = WarmOffWhite,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Outfit
                    )
                }
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(gateColor.copy(alpha = 0.12f), CircleShape)
                        .border(1.dp, gateColor.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (status == "NORMAL") "AUTO" else formatSleepCountdown(timeLeftMs).substringBefore(" "),
                        color = gateColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SleepStatusPill("ALARM", if (alarmEnabled) "ON" else "OFF", alarmEnabled, Modifier.weight(1f))
                SleepStatusPill("AYAH", if (ayahConstraintEnabled) "ON" else "OFF", ayahConstraintEnabled, Modifier.weight(1f))
                SleepStatusPill("PIN", if (accountabilityEnabled) "HELD" else "OPEN", accountabilityEnabled, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MinimumSleepThresholdCard(
    status: String,
    timeLeftMs: Long
) {
    val thresholdColor = when (status) {
        "MINIMUM" -> Color(0xFFFF4D4D)
        "LAST_WINDOW" -> SignalAmber
        "NIGHT_WINDOW" -> GlowGreen
        else -> MutedSilver
    }
    val statusLabel = when (status) {
        "MINIMUM" -> "SLEEP NOW"
        "LAST_WINDOW" -> "LAST GOOD WINDOW"
        "NIGHT_WINDOW" -> "NIGHT WINDOW"
        else -> "AUTO WATCH"
    }
    val statusMessage = when (status) {
        "MINIMUM" -> "The 5-hour floor is closing. Alarm pressure is active."
        "LAST_WINDOW" -> "Fajr is ${formatSleepCountdown(timeLeftMs)} away. Protect sleep now."
        "NIGHT_WINDOW" -> "After Isha, the discipline window is live."
        else -> "The app will warn when the 5-hour Fajr sleep floor approaches."
    }

    GlassCard(borderColor = thresholdColor.copy(alpha = 0.62f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(thresholdColor.copy(alpha = 0.13f), CircleShape)
                    .border(1.2.dp, thresholdColor.copy(alpha = 0.62f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "5h",
                    color = thresholdColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = statusLabel,
                    color = thresholdColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = statusMessage,
                    color = WarmOffWhite,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Outfit
                )
            }
        }
    }
}

@Composable
private fun SleepPressureStatusCard(
    status: String,
    timeLeftMs: Long,
    alarmEnabled: Boolean,
    ayahConstraintEnabled: Boolean,
    accountabilityEnabled: Boolean,
    isEnglish: Boolean
) {
    val pressureColor = when (status) {
        "MINIMUM" -> Color(0xFFFF4D4D)
        "LAST_WINDOW" -> SignalAmber
        "NIGHT_WINDOW" -> GlowGreen
        else -> MutedSilver
    }
    val pressureTitle = when (status) {
        "MINIMUM" -> "Minimum sleep window is closing"
        "LAST_WINDOW" -> "Last good sleep window"
        "NIGHT_WINDOW" -> "Night discipline window started"
        else -> "Sleep pressure monitor"
    }
    val pressureMessage = when (status) {
        "MINIMUM" -> "Fajr is ${formatSleepCountdown(timeLeftMs)} away. Sleep now."
        "LAST_WINDOW" -> "Fajr is ${formatSleepCountdown(timeLeftMs)} away. Stay away from screen stimulation and protect the final full sleep window."
        "NIGHT_WINDOW" -> "After Isha, the night discipline window has started."
        else -> "Fajr pressure will rise automatically after Isha."
    }

    GlassCard(borderColor = pressureColor.copy(alpha = 0.5f)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pressureTitle.uppercase(Locale.US),
                        color = pressureColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pressureMessage,
                        color = WarmOffWhite,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Outfit
                    )
                }
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(pressureColor.copy(alpha = 0.12f), CircleShape)
                        .border(1.dp, pressureColor.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (status == "NORMAL") "AUTO" else formatSleepCountdown(timeLeftMs).substringBefore(" "),
                        color = pressureColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ObsidianBorder))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SleepStatusPill("ALARM", if (alarmEnabled) "ON" else "OFF", alarmEnabled, Modifier.weight(1f))
                SleepStatusPill("AYAH", if (ayahConstraintEnabled) "ON" else "OFF", ayahConstraintEnabled, Modifier.weight(1f))
                SleepStatusPill("PIN", if (accountabilityEnabled) "HELD" else "OPEN", accountabilityEnabled, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SleepStatusPill(
    label: String,
    value: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (active) GlowGreen else MutedSilver
    Column(
        modifier = modifier
            .background(ObsidianBg, RoundedCornerShape(8.dp))
            .border(1.dp, if (active) color.copy(alpha = 0.45f) else ObsidianBorder, RoundedCornerShape(8.dp))
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = MutedSilver, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}

private fun formatSleepCountdown(timeLeftMs: Long): String {
    val totalMinutes = (timeLeftMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L -> "${hours}h ${minutes}m"
        minutes > 0L -> "${minutes}m"
        else -> "soon"
    }
}




// (Removed: StepInfo and DashboardRitualItem data classes were dead code — never instantiated)

@Composable
fun AnchorLockDecoyContent(
    viewModel: AnchorViewModel,
    isEnglish: Boolean
) {
    val context = LocalContext.current
    val emergencyState by viewModel.emergencyState.collectAsState()
    val coolDownRemaining by viewModel.emergencyCoolDownRemaining.collectAsState()
    val activeUnlockRemaining by viewModel.activeUnlockRemaining.collectAsState()
    
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var clipboardToken by remember { mutableStateOf<String?>(null) }
    var tokenInput by remember { mutableStateOf("") }
    var showManualRescue by remember { mutableStateOf(false) }
    var manualRescueArmed by remember { mutableStateOf(false) }
    LaunchedEffect(emergencyState) {
        if (emergencyState == "REQUESTED") {
            try {
                val clipText = clipboardManager.getText()?.text
                val normalizedClip = clipText?.let(viewModel::normalizeAccessTokenInput)
                if (normalizedClip != null && viewModel.isAccessTokenShape(normalizedClip)) {
                    clipboardToken = normalizedClip
                } else {
                    clipboardToken = null
                }
            } catch (e: Exception) {
                clipboardToken = null
            }
        } else {
            clipboardToken = null
            showManualRescue = false
            manualRescueArmed = false
        }
    }

    LaunchedEffect(clipboardToken) {
        val detectedToken = clipboardToken ?: return@LaunchedEffect
        delay(5_000)
        try {
            val currentClip = clipboardManager.getText()?.text?.let(viewModel::normalizeAccessTokenInput)
            if (currentClip == detectedToken) {
                viewModel.clearClipboard()
            }
        } catch (e: Exception) {
            viewModel.clearClipboard()
        }
        if (clipboardToken == detectedToken) {
            clipboardToken = null
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "decoy_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TOP HUD HEADER
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SECURE ACCESS PORTAL",
                color = MutedSilver,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Box(
                modifier = Modifier
                    .background(
                        color = when(emergencyState) {
                            "ACTIVE_UNLOCKED" -> SignalAmber.copy(alpha = 0.1f)
                            "COOLING" -> SignalAmber.copy(alpha = 0.1f)
                            else -> GlowGreen.copy(alpha = 0.1f)
                        }, 
                        shape = RoundedCornerShape(20.dp)
                    )
                    .border(
                        BorderStroke(
                            1.dp, 
                            when(emergencyState) {
                                "ACTIVE_UNLOCKED" -> SignalAmber.copy(alpha = 0.4f)
                                "COOLING" -> SignalAmber.copy(alpha = 0.4f)
                                else -> GlowGreen.copy(alpha = 0.3f)
                            }
                        ), 
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = when(emergencyState) {
                        "COOLING" -> "COOL-DOWN ACTIVE"
                        "ACTIVE_UNLOCKED" -> "UNLOCKED TEMPORARILY"
                        "REQUESTED" -> "AWAITING CODE"
                        "READY_TO_REQUEST" -> "COOL-DOWN EXPIRED"
                        else -> "PORTAL SECURED"
                    },
                    color = if (emergencyState == "ACTIVE_UNLOCKED" || emergencyState == "COOLING") SignalAmber else GlowGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // CENTRAL CARD
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (emergencyState) {
                "LOCKED" -> {
                    TactileToken(emoji = "🔒", size = 110.dp, emojiSize = 52.sp, borderColor = GlowGreen)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "EMERGENCY ACCESS",
                        color = WarmOffWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Outfit
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (com.analoganchor.app.viewmodel.AnchorViewModel.TEST_MODE_COOLDOWN)
                            "[TEST MODE] This starts a 10-second reflection window instead of 25 min."
                        else
                            "This starts a 25-minute reflection window. The urge may pass.",
                        color = if (com.analoganchor.app.viewmodel.AnchorViewModel.TEST_MODE_COOLDOWN)
                            SignalAmber else MutedSilver,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        fontFamily = Outfit,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    PremiumButton(
                        text = if (com.analoganchor.app.viewmodel.AnchorViewModel.TEST_MODE_COOLDOWN)
                            "BEGIN 10-SECOND WINDOW (TEST)"
                        else
                            "BEGIN 25-MINUTE WINDOW",
                        onClick = { viewModel.startEmergencyCoolDown() },
                        gradientColors = listOf(RefinedGreen, GlowGreen),
                        contentColor = ObsidianBg,
                        modifier = Modifier.fillMaxWidth()
                    )

                }
                "COOLING" -> {
                    val m = coolDownRemaining / 60
                    val s = coolDownRemaining % 60
                    val formatCool = String.format("%02d:%02d", m, s)
                    
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(130.dp)) {
                        androidx.compose.material3.CircularProgressIndicator(
                            progress = { coolDownRemaining / com.analoganchor.app.viewmodel.AnchorViewModel.COOLDOWN_DURATION_SEC.toFloat() },
                            color = SignalAmber,
                            trackColor = ObsidianBorder,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(130.dp)
                        )
                        Text(text = "⏳", fontSize = 48.sp)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = if (com.analoganchor.app.viewmodel.AnchorViewModel.TEST_MODE_COOLDOWN)
                            "REFLECTION WINDOW ACTIVE (TEST)"
                        else
                            "REFLECTION WINDOW ACTIVE",
                        color = SignalAmber,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Outfit
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (com.analoganchor.app.viewmodel.AnchorViewModel.TEST_MODE_COOLDOWN)
                            "Test mode: 10-second window. In production this will be 25 minutes."
                        else
                            "This urge is real, but it may fade. Sit with it for 25 minutes before asking for the access token.",
                        color = MutedSilver,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        fontFamily = Outfit
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = formatCool,
                        color = WarmOffWhite,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    // Survival Kit link — only shown here, during the waiting window
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.2.dp, GlowGreen.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .background(GlowGreen.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            .clickable {
                                try {
                                    val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://get-analog-anchor.com/survivalkit"))
                                    context.startActivity(i)
                                } catch (e: Exception) {}
                            }
                            .padding(18.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🧭", fontSize = 34.sp)
                            Column {
                                Text(
                                    text = "Open the Survival Kit",
                                    color = GlowGreen,
                                    fontSize = 22.sp,
                                    lineHeight = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = Outfit
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap here to visit the Analog Anchor website while the timer runs. Choose one concrete reset action before the access code becomes available.",
                                    color = WarmSilverText,
                                    fontSize = 15.sp,
                                    lineHeight = 21.sp,
                                    fontFamily = Outfit
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    PremiumButton(
                        text = "CANCEL REQUEST",
                        onClick = { viewModel.cancelEmergencyCoolDown() },
                        gradientColors = listOf(DeepPanel, ObsidianSurface),
                        contentColor = WarmOffWhite,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                "READY_TO_REQUEST" -> {
                    TactileToken(emoji = "🔓", size = 110.dp, emojiSize = 52.sp, borderColor = GlowGreen)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "REFLECTION COMPLETE",
                        color = GlowGreen,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Outfit
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your 25-minute reflection window is done. Contact your accountability partner and ask for the access token.",
                        color = MutedSilver,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        fontFamily = Outfit,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    PremiumButton(
                        text = "I HAVE THE CODE — ENTER IT",
                        onClick = { viewModel.requestEmergencyUnlockPayload() },
                        gradientColors = listOf(RefinedGreen, GlowGreen),
                        contentColor = ObsidianBg,
                        modifier = Modifier.fillMaxWidth()
                    )

                }
                "REQUESTED" -> {
                    TactileToken(emoji = "📡", size = 100.dp, emojiSize = 44.sp, borderColor = GlowGreen)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "ENTER ACCESS CODE",
                        color = WarmOffWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Outfit
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Paste the support token here only. Do not paste AA tokens into the blocking app; Analog Anchor converts it internally.",
                        color = MutedSilver,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        fontFamily = Outfit,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // Token input label
                    Text(
                        text = "SECURE TOKEN",
                        color = GlowGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.8.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (!clipboardToken.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .background(GlowGreen.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                            .border(BorderStroke(1.dp, GlowGreen.copy(alpha = 0.4f)), RoundedCornerShape(20.dp))
                            .clickable {
                                tokenInput = clipboardToken?.let(viewModel::normalizeAccessTokenInput) ?: ""
                                viewModel.clearClipboard()
                                clipboardToken = null
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                showManualRescue = false
                                manualRescueArmed = false
                            }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(GlowGreen, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Tap to paste: ${clipboardToken!!.take(5)}...${clipboardToken!!.takeLast(5)}",
                                    color = GlowGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    TextField(
                        value = tokenInput,
                        onValueChange = {
                            val normalizedInput = viewModel.normalizeAccessTokenInput(it)
                            tokenInput = normalizedInput
                            try {
                                val currentClip = clipboardManager.getText()?.text?.let(viewModel::normalizeAccessTokenInput)
                                if (viewModel.isAccessTokenShape(normalizedInput) && currentClip == normalizedInput) {
                                    viewModel.clearClipboard()
                                }
                            } catch (e: Exception) {
                                // Clipboard access can be restricted by Android; token authorization still works.
                            }
                            if (viewModel.isAccessTokenShape(normalizedInput)) {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                            showManualRescue = false
                            manualRescueArmed = false
                        },
                        placeholder = {
                            Text(
                                text = "AA-ANCHOR-...",
                                color = MutedSilver.copy(alpha = 0.5f),
                                fontSize = 17.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            )
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = GlowGreen,
                            fontSize = 17.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center,
                            textDirection = TextDirection.Ltr
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp)
                            .border(BorderStroke(1.5.dp, ObsidianBorder), RoundedCornerShape(10.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = GlowGreen,
                            unfocusedTextColor = GlowGreen,
                            focusedContainerColor = DeepPanel,
                            unfocusedContainerColor = DeepPanel,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = GlowGreen
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    try {
                                        val clipText = clipboardManager.getText()?.text
                                        if (!clipText.isNullOrBlank()) {
                                            val normalizedClip = viewModel.normalizeAccessTokenInput(clipText)
                                            tokenInput = normalizedClip
                                            if (viewModel.isAccessTokenShape(normalizedClip)) {
                                                viewModel.clearClipboard()
                                                keyboardController?.hide()
                                                focusManager.clearFocus()
                                            }
                                            showManualRescue = false
                                            manualRescueArmed = false
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Clipboard empty or inaccessible", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentPaste,
                                    contentDescription = "Paste from Clipboard",
                                    tint = GlowGreen
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                        ),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    PremiumButton(
                        text = "AUTHORIZE ACCESS",
                        onClick = {
                            val success = viewModel.authorizeMaskedToken(tokenInput)
                            if (success) {
                                showManualRescue = false
                                manualRescueArmed = false
                                Toast.makeText(context, "Access authorized. Secure auto-fill armed.", Toast.LENGTH_LONG).show()
                                val intent = AppBlockIntegration.appBlockLaunchIntent(context)
                                if (intent != null) context.startActivity(intent)
                            } else {
                                val tokenLooksValid = viewModel.isMaskedTokenUsable(tokenInput)
                                if (tokenLooksValid) {
                                    showManualRescue = true
                                    Toast.makeText(
                                        context,
                                        "Secure auto-fill is not ready. Manual rescue is available as last resort.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(context, "Invalid or expired Analog Anchor token.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = tokenInput.isNotBlank(),
                        gradientColors = listOf(RefinedGreen, GlowGreen),
                        contentColor = ObsidianBg,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = {
                            try {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://get-analog-anchor.com/support"))
                                context.startActivity(browserIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Need Scenario B emergency code? Open Support Portal",
                            color = GlowGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Outfit
                        )
                    }

                    if (showManualRescue) {
                        val decodedPin = remember(tokenInput) { viewModel.getDecodedEmergencyPin(tokenInput) }
                        Spacer(modifier = Modifier.height(14.dp))
                        GlassCard(borderColor = SignalAmber.copy(alpha = 0.85f)) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "LAST-RESORT MANUAL RESCUE",
                                        color = SignalAmber,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.2.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(SignalAmber.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .border(BorderStroke(0.8.dp, SignalAmber), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "DYNAMIC PASSCODE",
                                            color = SignalAmber,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (!decodedPin.isNullOrBlank()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(SignalAmber.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                                            .border(BorderStroke(1.dp, SignalAmber.copy(alpha = 0.3f)), RoundedCornerShape(10.dp))
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "TEMPORARY SESSION PIN",
                                            color = SignalAmber,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.4.sp
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            decodedPin.forEach { digit ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(46.dp)
                                                        .background(DeepPanel, RoundedCornerShape(10.dp))
                                                        .border(BorderStroke(1.5.dp, SignalAmber), RoundedCornerShape(10.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = digit.toString(),
                                                        color = SignalAmber,
                                                        fontSize = 24.sp,
                                                        fontWeight = FontWeight.Black,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = "⚠️ Single-use session passcode. This dynamic PIN is generated for this specific emergency unlock window and expires immediately. It rotates on every request and will NOT work in future lockouts — memorizing it is useless.",
                                    color = WarmSilverText,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    fontFamily = Outfit
                                )

                                Text(
                                    text = "AppBlock keypad does not allow pasting. Note the PIN digits above, tap below to open AppBlock, and type them directly into the keypad.",
                                    color = MutedSilver,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontFamily = Outfit
                                )

                                PremiumButton(
                                    text = if (manualRescueArmed) "APPBLOCK OPENED — ENTER PIN" else "OPEN APPBLOCK TO ENTER PIN",
                                    onClick = {
                                        val pin = viewModel.authorizeMaskedTokenWithManualReveal(tokenInput)
                                        if (pin != null) {
                                            manualRescueArmed = true
                                            Toast.makeText(
                                                context,
                                                "Active session started. Type the PIN into AppBlock.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            val intent = AppBlockIntegration.appBlockLaunchIntent(context)
                                            if (intent != null) context.startActivity(intent)
                                        } else {
                                            Toast.makeText(context, "Manual rescue failed. Token expired or invalid.", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    gradientColors = listOf(SignalAmber, SignalAmber.copy(alpha = 0.8f)),
                                    contentColor = ObsidianBg,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (!decodedPin.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        TextButton(
                                            onClick = {
                                                try {
                                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(decodedPin))
                                                    Toast.makeText(context, "PIN copied to clipboard (30s fallback)", Toast.LENGTH_SHORT).show()
                                                } catch (_: Exception) {}
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ContentCopy,
                                                contentDescription = "Copy PIN",
                                                tint = MutedSilver,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Copy PIN to clipboard",
                                                color = MutedSilver,
                                                fontSize = 12.sp,
                                                fontFamily = Outfit
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "ACTIVE_UNLOCKED" -> {
                    val m = activeUnlockRemaining / 60
                    val s = activeUnlockRemaining % 60
                    val formatTime = String.format("%02d:%02d", m, s)
                    
                    TactileToken(emoji = "🔓", size = 110.dp, emojiSize = 52.sp, borderColor = SignalAmber)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "TEMPORARY UNLOCK ACTIVE",
                        color = SignalAmber,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Outfit
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Access is temporarily unlocked. Make the needed change, then re-arm your blocking rule before returning.",
                        color = MutedSilver,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        fontFamily = Outfit,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = formatTime,
                        color = WarmOffWhite,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    PremiumButton(
                        text = "RE-ARM BLOCKING RULE",
                        onClick = {
                            viewModel.triggerReEntryScreen()
                        },
                        gradientColors = listOf(DeepPanel, ObsidianSurface),
                        contentColor = WarmOffWhite,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // FOOTER
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "SECURE ACCESS SYSTEM",
                color = MutedSilver,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            
            PremiumButton(
                text = "EXIT",
                onClick = { viewModel.setShowDecoyShortcutScreen(false) },
                gradientColors = listOf(Color.Transparent, Color.Transparent),
                contentColor = WarmOffWhite,
                disabledColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
            )
        }
    }
}

@Composable
fun ReEntryLockoutOverlayContent(
    viewModel: AnchorViewModel,
    isEnglish: Boolean
) {
    val graceTimer by viewModel.graceTimerRemaining.collectAsState()

    GuidedReEntryLockoutOverlayContent(
        viewModel = viewModel,
        graceTimer = graceTimer
    )
}

@Composable
private fun GuidedReEntryLockoutOverlayContent(
    viewModel: AnchorViewModel,
    graceTimer: Int
) {
    val context = LocalContext.current
    var blockingAppOpened by remember { mutableStateOf(false) }
    var showHeadsUpConfirmDialog by remember { mutableStateOf(false) }
    var showManualReEntryRescue by remember { mutableStateOf(false) }
    val graceM = graceTimer / 60
    val graceS = graceTimer % 60
    val graceStr = String.format("%02d:%02d", graceM, graceS)

    if (showHeadsUpConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showHeadsUpConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚠️", fontSize = 20.sp)
                    Text(
                        text = "Confirm AppBlock Re-enrollment",
                        color = SignalAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        fontFamily = Outfit
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Heads-up: Make sure your blocking rules in AppBlock are 100% active and running. If it is not implemented right, the re-alarm will trigger, the entire system will lock into strict mode, and your accountability partner will have to visit you manually to reset your device.",
                        color = WarmOffWhite,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontFamily = Outfit
                    )
                    Text(
                        text = "تنبيه: تأكد أن حظر التطبيقات في AppBlock مفعل ويعمل بنسبة 100%. إذا لم يتم تفعيله بالشكل الصحيح، سينطلق إنذار الطوارئ ويدخل النظام بالكامل في وضع القفل الصارم (Strict Mode)، وسيضطر شريكك لزيارتك يدوياً لإعادة ضبط جهازك.",
                        color = WarmSilverText,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = Outfit,
                        style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showHeadsUpConfirmDialog = false
                        viewModel.confirmBlockingRuleRearmed()
                        Toast.makeText(context, "AppBlock re-armed. System secured.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlowGreen)
                ) {
                    Text(
                        text = "CONFIRM: I AM 100% RE-ENROLLED",
                        color = ObsidianBg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showHeadsUpConfirmDialog = false },
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Text(
                        text = "CANCEL & CHECK APPBLOCK",
                        color = WarmOffWhite,
                        fontSize = 12.sp
                    )
                }
            },
            containerColor = DeepPanel,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(
                text = "RE-ENTRY PROTOCOL",
                color = SignalAmber,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "🛡️", fontSize = 28.sp)
                Text(
                    text = "Re-enroll AppBlock",
                    color = WarmOffWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Outfit
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Do this before the grace timer ends.",
                color = SignalAmber,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                textAlign = TextAlign.Center,
                fontFamily = Outfit,
                fontWeight = FontWeight.Black
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SignalAmber.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                .border(BorderStroke(2.dp, SignalAmber), RoundedCornerShape(8.dp))
                .padding(vertical = 18.dp, horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "GRACE REMAINING",
                    color = SignalAmber,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.4.sp
                )
                Text(
                    text = graceStr,
                    color = WarmOffWhite,
                    fontSize = 64.sp,
                    lineHeight = 66.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        GlassCard(
            borderColor = if (blockingAppOpened) GlowGreen else ObsidianBorder,
            contentPadding = 16.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "RE-ARM NOW",
                    color = GlowGreen,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Make your blocking rule ACTIVE in AppBlock, then return and confirm.",
                    color = WarmSilverText,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PremiumButton(
                text = "OPEN APPBLOCK TO RE-ENROLL",
                onClick = {
                    viewModel.clearClipboard()
                    val autofillArmed = viewModel.armBlockingAppReEntryAutofill()
                    blockingAppOpened = true
                    Toast.makeText(
                        context,
                        if (autofillArmed) {
                            "Secure auto-fill armed. Opening AppBlock..."
                        } else {
                            "Make your rule ACTIVE, then return before grace ends."
                        },
                        Toast.LENGTH_LONG
                    ).show()
                    val intent = AppBlockIntegration.appBlockLaunchIntent(context)
                    if (intent != null) {
                        context.startActivity(intent)
                    }
                },
                gradientColors = listOf(RefinedGreen, GlowGreen),
                contentColor = ObsidianBg,
                modifier = Modifier.fillMaxWidth()
            )

            // Option B / Auto-fill Failure Rescue Fallback
            TextButton(
                onClick = { showManualReEntryRescue = !showManualReEntryRescue },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (showManualReEntryRescue) "Hide Rescue Options ▲" else "Auto-fill having trouble? Scenario B Rescue ▼",
                    color = SignalAmber,
                    fontSize = 12.sp,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Bold
                )
            }

            if (showManualReEntryRescue) {
                val rescuePin = remember { viewModel.getReEntryPinForManualRescue() }
                GlassCard(
                    borderColor = SignalAmber.copy(alpha = 0.8f),
                    contentPadding = 14.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "SCENARIO B RESCUE FALLBACK",
                            color = SignalAmber,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )

                        if (!rescuePin.isNullOrBlank()) {
                            Text(
                                text = "Temporary Re-arm PIN: $rescuePin",
                                color = WarmOffWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "If auto-fill did not type automatically into AppBlock keypad, enter the digits above to re-activate your rule.",
                                color = WarmSilverText,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                fontFamily = Outfit
                            )
                        }

                        PremiumButton(
                            text = "OPEN EMERGENCY SUPPORT PORTAL",
                            onClick = {
                                try {
                                    val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://get-analog-anchor.com/support"))
                                    context.startActivity(i)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                }
                            },
                            gradientColors = listOf(DeepPanel, ObsidianSurface),
                            contentColor = GlowGreen,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            PremiumButton(
                text = "I RE-ENROLLED APPBLOCK",
                enabled = blockingAppOpened || showManualReEntryRescue,
                onClick = {
                    showHeadsUpConfirmDialog = true
                },
                gradientColors = listOf(DeepPanel, ObsidianSurface),
                contentColor = WarmOffWhite,
                modifier = Modifier.fillMaxWidth()
            )

            PremiumButton(
                text = "REPORT NOT RE-ARMED",
                onClick = {
                    viewModel.failBlockingRuleRearm()
                    Toast.makeText(context, "Breach logged locally.", Toast.LENGTH_LONG).show()
                },
                gradientColors = listOf(DeepPanel, ObsidianSurface),
                contentColor = SignalAmber,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun HardwareUpgradeShowcaseCard(isEnglish: Boolean) {
    val context = LocalContext.current
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://get-analog-anchor.com/"))
                context.startActivity(intent)
            }
            .border(1.2.dp, RefinedGreen.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🛡️", fontSize = 20.sp)
                    Text(
                        text = if (isEnglish) "HARDWARE PRE-COMMITMENT" else "الالتزام المادي المتكامل",
                        color = GlowGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp
                    )
                }
                Text(
                    text = if (isEnglish) "OFFICIAL STORE →" else "المتجر الرسمـي ←",
                    color = WarmOffWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Outfit
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isEnglish) "Upgrade to Unbreakable Hardware Pre-Commitment" else "ترقية إلى حزمة الالتزام المادي غير القابلة للكسر",
                color = WarmOffWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Outfit
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isEnglish)
                    "Get physical Whiteboard Anchor & Bedroom Fajr Eraser NFC tags to build unbreakable habits."
                else
                    "احصل على بطاقات NFC المادية لمرساة اللوحة وممحاة الفجر لتفعيل نظام الالتزام السلوكي المادي بالكامل.",
                color = MutedSilver,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFamily = Outfit
            )

            Spacer(modifier = Modifier.height(12.dp))

            PremiumButton(
                text = if (isEnglish) "GET UNBREAKABLE HARDWARE" else "احصل على الحزمة المادية الآن",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://get-analog-anchor.com/"))
                    context.startActivity(intent)
                },
                gradientColors = listOf(RefinedGreen, GlowGreen),
                contentColor = ObsidianBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            )
        }
    }
}

@Composable
fun TadabburCommitmentCard(
    viewModel: AnchorViewModel,
    isEnglish: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isCommitted by viewModel.tadabburCommitmentActive.collectAsState()
    val committedDays by viewModel.tadabburCommitmentDays.collectAsState()
    val remainingDays by viewModel.tadabburCommitmentRemainingDays.collectAsState()
    val isTadabburAlarmRinging by viewModel.isTadabburAlarmRinging.collectAsState()
    var selectedDays by remember { mutableStateOf<Int?>(null) }
    var showCommitCongratulationPopup by remember { mutableStateOf(false) }
    var committedDaysForPopup by remember { mutableStateOf(3) }
    val sunriseMinus7 = remember { viewModel.getSunriseMinus7Formatted() }

    val cardLayoutDir = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl

    CompositionLocalProvider(LocalLayoutDirection provides cardLayoutDir) {
        Box(
            modifier = modifier
                .background(ObsidianSurface, RoundedCornerShape(14.dp))
                .border(
                    BorderStroke(1.dp, if (isCommitted) GlowGreen.copy(alpha = 0.5f) else ObsidianBorder),
                    RoundedCornerShape(14.dp)
                )
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Main Row: Subtitle alongside Sunrise Time (No top header)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEnglish) "Meet yourself at the Anchor." else "إجلس.. خذ القلم وابدأ",
                        color = WarmOffWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Outfit,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEnglish) "7m before sunrise • $sunriseMinus7" else "٧د قبل الشروق • $sunriseMinus7",
                        color = GlowGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (!isCommitted) {
                    // Choices Row: [3 Days] [1 Week]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 3 Days Pill
                        val is3Selected = selectedDays == 3
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .background(
                                    if (is3Selected) GlowGreen.copy(alpha = 0.15f) else ObsidianBg,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    BorderStroke(1.dp, if (is3Selected) GlowGreen else ObsidianBorder),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    selectedDays = if (selectedDays == 3) null else 3
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isEnglish) "3 Days" else "٣ أيام",
                                color = if (is3Selected) GlowGreen else WarmOffWhite,
                                fontSize = 12.sp,
                                fontWeight = if (is3Selected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = Outfit
                            )
                        }

                        // 1 Week Pill
                        val is7Selected = selectedDays == 7
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .background(
                                    if (is7Selected) GlowGreen.copy(alpha = 0.15f) else ObsidianBg,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    BorderStroke(1.dp, if (is7Selected) GlowGreen else ObsidianBorder),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    selectedDays = if (selectedDays == 7) null else 7
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isEnglish) "1 Week" else "أسبوع واحد",
                                color = if (is7Selected) GlowGreen else WarmOffWhite,
                                fontSize = 12.sp,
                                fontWeight = if (is7Selected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = Outfit
                            )
                        }
                    }

                    // Dynamically revealed when user selects 3 Days or 1 Week
                    AnimatedVisibility(visible = selectedDays != null) {
                        Button(
                            onClick = {
                                val days = selectedDays ?: 3
                                committedDaysForPopup = days
                                viewModel.commitToTadabbur(days)
                                showCommitCongratulationPopup = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .padding(top = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GlowGreen,
                                contentColor = ObsidianBg
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "Commit (${if (selectedDays == 7) "1 Week" else "3 Days"})"
                                       else "تأكيد الالتزام (${if (selectedDays == 7) "أسبوع" else "٣ أيام"})",
                                fontWeight = FontWeight.Bold,
                                fontFamily = Outfit,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    // Committed Active State (Non-reversible like Friday Jumu'ah)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish)
                                "$remainingDays of $committedDays days remaining"
                            else
                                "متبقي $remainingDays من $committedDays أيام",
                            color = GlowGreen,
                            fontSize = 12.sp,
                            fontFamily = Outfit,
                            fontWeight = FontWeight.SemiBold
                        )

                        Box(
                            modifier = Modifier
                                .background(GlowGreen.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                .border(BorderStroke(1.dp, GlowGreen.copy(alpha = 0.4f)), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "● ACTIVE" else "● نشط",
                                color = GlowGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                if (isTadabburAlarmRinging) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            viewModel.simulateTadabburAnchorTap()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GlowGreen,
                            contentColor = ObsidianBg
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isEnglish) "⚡ SIMULATE ANCHOR NFC TAP" else "⚡ محاكاة مسح NFC للمرساة",
                            fontWeight = FontWeight.Bold,
                            fontFamily = Outfit,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    if (showCommitCongratulationPopup) {
        val popupLayoutDir = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
        LaunchedEffect(Unit) {
            delay(5000L)
            showCommitCongratulationPopup = false
        }

        Dialog(
            onDismissRequest = { showCommitCongratulationPopup = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides popupLayoutDir) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showCommitCongratulationPopup = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(ObsidianSurface)
                            .border(1.2.dp, GlowGreen.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                showCommitCongratulationPopup = false
                            }
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TactileToken(
                                emoji = "⚓",
                                size = 52.dp,
                                emojiSize = 26.sp,
                                borderColor = GlowGreen
                            )

                            Text(
                                text = if (isEnglish) "Congratulations! 🎉" else "🎉 تهانينا!",
                                color = GlowGreen,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Outfit,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = if (isEnglish)
                                    "“Your Anchor will call you back.”"
                                else
                                    "“مرساتك ستناديك.”",
                                color = WarmOffWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                fontFamily = Outfit,
                                textAlign = TextAlign.Center
                            )

                            if (!isEnglish) {
                                Text(
                                    text = "“Your Anchor will call you back.”",
                                    color = MutedSilver,
                                    fontSize = 12.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    fontFamily = Outfit,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Text(
                                text = if (isEnglish)
                                    "Committed for $committedDaysForPopup days"
                                else
                                    "تم تأكيد الالتزام لمدة ${if (committedDaysForPopup == 7) "أسبوع واحد" else "٣ أيام"}",
                                color = MutedSilver,
                                fontSize = 12.sp,
                                fontFamily = Outfit,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

