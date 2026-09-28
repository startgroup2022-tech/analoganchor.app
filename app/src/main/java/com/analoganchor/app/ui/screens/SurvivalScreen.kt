package com.analoganchor.app.ui.screens

import androidx.compose.animation.core.*
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analoganchor.app.ui.components.GlassCard
import com.analoganchor.app.ui.components.TactileToken
import com.analoganchor.app.ui.components.PremiumButton
import com.analoganchor.app.ui.theme.GlowGreen
import com.analoganchor.app.ui.theme.MutedSilver
import com.analoganchor.app.ui.theme.ObsidianBg
import com.analoganchor.app.ui.theme.ObsidianBorder
import com.analoganchor.app.ui.theme.ObsidianGlass
import com.analoganchor.app.ui.theme.RefinedGreen
import com.analoganchor.app.ui.theme.SignalAmber
import com.analoganchor.app.ui.theme.WarmOffWhite
import com.analoganchor.app.ui.theme.WarmSilverText
import com.analoganchor.app.ui.theme.Outfit
import android.widget.Toast
import android.content.Intent
import android.content.Context
import android.net.Uri
import com.analoganchor.app.viewmodel.AnchorViewModel

@Composable
fun SurvivalScreen(
    viewModel: AnchorViewModel,
    onNavigateToDashboard: () -> Unit
) {
    val context = LocalContext.current
    val sanctuaryType by viewModel.sanctuaryType.collectAsState()
    val scrollState = rememberScrollState()

    // Immersive warning pulse animation for the top alarm token
    val infiniteTransition = rememberInfiniteTransition(label = "alarm_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Collect real location flows & credits
    val userLat by viewModel.userLatitude.collectAsState()
    val userLng by viewModel.userLongitude.collectAsState()
    val sanctuaryLat by viewModel.sanctuaryLatitude.collectAsState()
    val sanctuaryLng by viewModel.sanctuaryLongitude.collectAsState()
    val reservePoints by viewModel.reservePoints.collectAsState()

    val realDistance = viewModel.calculateDistance(userLat, userLng, sanctuaryLat, sanctuaryLng).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        // RED-ALERT Pulsating Alarm Badge
        TactileToken(
            emoji = "🚨",
            size = 84.dp,
            emojiSize = 44.sp,
            borderColor = SignalAmber,
            glowColor = SignalAmber.copy(alpha = 0.35f),
            modifier = Modifier.scale(pulseScale)
        )
        
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "HARDWARE BOUNDARY ENGAGED",
            color = WarmOffWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.2.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (reservePoints <= 0) "Zero credits hard block active." else "Sanctuary alignment releases the lock.",
            color = if (reservePoints <= 0) Color.Red else SignalAmber,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Outfit
        )

        Spacer(modifier = Modifier.height(28.dp))

        // HARD BLOCK CARD WARNING
        if (reservePoints <= 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .border(BorderStroke(1.5.dp, Color.Red), RoundedCornerShape(12.dp))
                    .background(Color.Red.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🚫", fontSize = 28.sp)
                    Column {
                        Text(
                            text = "ZERO CREDITS: HARD LOCK ACTIVE",
                            color = Color.Red,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Your reserve credits are completely exhausted. All digital overrides are disabled. Physical verification is strictly required to unlock.",
                            color = WarmSilverText,
                            fontSize = 12.sp,
                            fontFamily = Outfit
                        )
                    }
                }
            }
        }

        // SANCTUARY RECOVERY ACTION CARD
        val (emoji, title, lat, lng) = when (sanctuaryType) {
            "mosque" -> quadruple("🕌", "Go to the Mosque", sanctuaryLat.toFloat(), sanctuaryLng.toFloat())
            "park" -> quadruple("🏞️", "Go to the Community Park", sanctuaryLat.toFloat(), sanctuaryLng.toFloat())
            else -> quadruple("🏃", "Travel to Refuge Running Trail", sanctuaryLat.toFloat(), sanctuaryLng.toFloat())
        }

        GlassCard(borderColor = if (reservePoints <= 0) Color.Red else SignalAmber) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TactileToken(
                    emoji = emoji,
                    size = 96.dp,
                    emojiSize = 52.sp,
                    borderColor = if (reservePoints <= 0) Color.Red else SignalAmber,
                    glowColor = (if (reservePoints <= 0) Color.Red else SignalAmber).copy(alpha = 0.2f)
                )
                
                Spacer(modifier = Modifier.height(18.dp))
                
                Text(
                    text = title,
                    color = WarmOffWhite,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Outfit
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Verify your physical presence at the designated geofence. Bypassing restores your Reserve Points and releases phone limitations.",
                    color = WarmSilverText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = Outfit
                )

                Spacer(modifier = Modifier.height(24.dp))

                // GPS VERIFY BUTTON
                Button(
                    onClick = {
                        val verified = viewModel.verifyMosquePresence()
                        if (verified) {
                            Toast.makeText(context, "Sanctuary verified! +50 credits. Access restored.", Toast.LENGTH_LONG).show()
                            onNavigateToDashboard()
                        } else {
                            Toast.makeText(
                                context,
                                "Proximity check failed. You are currently ${realDistance}m away from the sanctuary (must be within 100m).",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, (if (reservePoints <= 0) Color.Red else SignalAmber).copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("verify_gps_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📍", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Verify Location Proximity (${realDistance}m)", 
                            color = if (reservePoints <= 0) Color.Red else SignalAmber, 
                            fontWeight = FontWeight.Bold,
                            fontFamily = Outfit,
                            letterSpacing = 0.5.sp
                        )
                    }
                }


            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // REPLACEMENT BEHAVIORS SEGMENT
        Text(
            text = "ANALOG ACTIONS TO TAKE NOW",
            color = MutedSilver,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.5.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Row 1: Journal Prompt
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianGlass.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .border(1.dp, ObsidianBorder.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TactileToken(emoji = "📖", size = 48.dp, emojiSize = 24.sp, borderColor = ObsidianBorder)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Current Journal Prompt", color = WarmOffWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = Outfit)
                    Text("Write one page about what you are avoiding.", color = MutedSilver, fontSize = 13.sp, fontFamily = Outfit)
                }
            }

            // Row 2: Read physical book
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianGlass.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .border(1.dp, ObsidianBorder.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TactileToken(emoji = "📚", size = 48.dp, emojiSize = 24.sp, borderColor = ObsidianBorder)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Unplugged Literary Time", color = WarmOffWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = Outfit)
                    Text("Read one chapter of your physical book.", color = MutedSilver, fontSize = 13.sp, fontFamily = Outfit)
                }
            }

            // Row 3: Mosque Prayer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianGlass.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .border(1.dp, ObsidianBorder.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TactileToken(emoji = "🕌", size = 48.dp, emojiSize = 24.sp, borderColor = ObsidianBorder)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Attend Secondary Devotions", color = WarmOffWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = Outfit)
                    Text("Attend the next prayer at your Sanctuary.", color = MutedSilver, fontSize = 13.sp, fontFamily = Outfit)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // PREMIUM SPIRITUAL NUDGE
        GlassCard(borderColor = ObsidianBorder) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TactileToken(emoji = "💡", size = 36.dp, emojiSize = 18.sp, borderColor = GlowGreen)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("First-Light Focus Protocol", color = GlowGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = Outfit)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Fajr / sitting in the mosque between Maghrib prayer and Isha prayer earns the most, but social apps stay locked until after sunrise to protect first-light focus.",
                    color = MutedSilver,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontFamily = Outfit
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // SURVIVAL KIT LINK NUDGE
        Row(
            modifier = Modifier
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://get-analog-anchor.com/survivalkit"))
                    context.startActivity(intent)
                }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Unlocking the Survival Kit Toolkit",
                color = GlowGreen,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline,
                fontFamily = Outfit
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "🔗",
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// Helper quadruple structure
private data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private fun <A, B, C, D> quadruple(first: A, second: B, third: C, fourth: D): Quadruple<A, B, C, D> {
    return Quadruple(first, second, third, fourth)
}
