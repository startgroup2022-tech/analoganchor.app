package com.analoganchor.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analoganchor.app.ui.components.GlassCard
import com.analoganchor.app.ui.components.PremiumButton
import com.analoganchor.app.ui.components.TactileToken
import com.analoganchor.app.ui.theme.DeepPanel
import com.analoganchor.app.ui.theme.GlowGreen
import com.analoganchor.app.ui.theme.MutedSilver
import com.analoganchor.app.ui.theme.ObsidianBg
import com.analoganchor.app.ui.theme.ObsidianBorder
import com.analoganchor.app.ui.theme.ObsidianSurface
import com.analoganchor.app.ui.theme.Outfit
import com.analoganchor.app.ui.theme.RefinedGreen
import com.analoganchor.app.ui.theme.SignalAmber
import com.analoganchor.app.ui.theme.WarmOffWhite
import com.analoganchor.app.ui.theme.WarmSilverText
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.LaunchedEffect
import com.analoganchor.app.engine.DailyRuleEngineRunner
import com.analoganchor.app.util.AppBlockIntegration
import com.analoganchor.app.viewmodel.AnchorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FounderScreen(
    viewModel: AnchorViewModel,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToDashboard: () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
) {
    val context = LocalContext.current
    val deviceAdminGranted by viewModel.deviceAdminActive.collectAsState()
    var usageAccessGranted by remember { mutableStateOf(DailyRuleEngineRunner.hasUsageAccess(context)) }
    var accessibilityAccessGranted by remember { mutableStateOf(viewModel.isAccessibilityServiceEnabled()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val app = context.applicationContext as? com.analoganchor.app.AnalogAnchorApplication
        app?.let {
            it.preferencesRepository.setPolicyEditWindowEndTimestamp(System.currentTimeMillis() + 15 * 60 * 1000L)
        }
        while (true) {
            viewModel.checkHardwarePermissions()
            usageAccessGranted = DailyRuleEngineRunner.hasUsageAccess(context)
            accessibilityAccessGranted = viewModel.isAccessibilityServiceEnabled()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Column {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "ANALOG ANCHOR CONFIGURATION",
                color = WarmOffWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "White-glove locking environment setup. Ensure every protection step is resolved correctly.",
                color = MutedSilver,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = Outfit
            )

            Spacer(modifier = Modifier.height(18.dp))

            SetupControlRow(
                token = "SHIELD",
                title = "Grant Device Admin",
                subtitle = if (deviceAdminGranted)
                    "Android confirms Analog Anchor is active as a device admin."
                else
                    "Tap GRANT, then approve the Android Device Admin screen.",
                actionText = if (deviceAdminGranted) "READY" else "GRANT",
                confirmed = deviceAdminGranted,
                onClick = {
                    try {
                        context.startActivity(viewModel.deviceAdminActivationIntent())
                    } catch (e: Exception) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                        } catch (e2: Exception) {
                            Toast.makeText(context, "Could not open Device Admin settings.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SetupControlRow(
                token = "USAGE",
                title = "Grant Usage Access",
                subtitle = "Allows tracking device context for strict lock enforcement.",
                actionText = if (usageAccessGranted) "READY" else "GRANT",
                confirmed = usageAccessGranted,
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open settings.", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SetupControlRow(
                token = "TYPER",
                title = "Accessibility Gesture Unlock",
                subtitle = if (accessibilityAccessGranted)
                    "Enables secure PIN gesture taps for custom keypads."
                else
                    "Tap GRANT → find \"Analog Anchor\" in the list → enable the toggle.",
                actionText = if (accessibilityAccessGranted) "READY" else "GRANT",
                confirmed = accessibilityAccessGranted,
                onClick = {
                    try {
                        // Deep-link directly to the ZenithAccessibilityService (Day Guard) settings page
                        val componentName = android.content.ComponentName(
                            context.packageName,
                            "com.analoganchor.app.service.ZenithAccessibilityService"
                        )
                        val deepLinkIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            val bundle = android.os.Bundle()
                            bundle.putString(
                                ":settings:fragment_args_key",
                                componentName.flattenToString()
                            )
                            putExtra(":settings:show_fragment_args", bundle)
                            // Some OEM forks (Samsung / Xiaomi) need this extra instead
                            putExtra("extra_fragment_arg_key", componentName.flattenToString())
                        }
                        try {
                            context.startActivity(deepLinkIntent)
                        } catch (e2: Exception) {
                            // Fallback: plain accessibility settings
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open accessibility settings.", Toast.LENGTH_SHORT).show()
                    }
                }
            )


            Column(modifier = Modifier.padding(bottom = 14.dp)) {
                PremiumButton(
                    text = "SEAL THE ANCHOR",
                    onClick = {
                        viewModel.sealTheAnchor()
                        Toast.makeText(context, "Anchor sealed. Begin client portal.", Toast.LENGTH_SHORT).show()
                        onNavigateToOnboarding()
                    },
                    enabled = true,
                    gradientColors = listOf(GlowGreen, RefinedGreen),
                    contentColor = ObsidianBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "SEALING LOCKS THIS PANEL FOREVER. IT TRANSITIONS THE HARDWARE FRAMEWORKS TO THE CLIENT PORTAL.",
                    color = WarmSilverText,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DeepPanel, RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, ObsidianBorder), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        viewModel.sealTheAnchor()
                        viewModel.completeOnboarding()
                        onNavigateToDashboard()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ObsidianSurface,
                        contentColor = MutedSilver
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Text(
                        text = "BYPASS ALL & ENTER DASHBOARD",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupControlRow(
    token: String,
    title: String,
    subtitle: String,
    actionText: String,
    confirmed: Boolean,
    onClick: () -> Unit
) {
    GlassCard(
        borderColor = if (confirmed) GlowGreen.copy(alpha = 0.65f) else ObsidianBorder,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TactileToken(
                emoji = token,
                size = 40.dp,
                emojiSize = 10.sp,
                borderColor = if (confirmed) GlowGreen else RefinedGreen,
                glowColor = RefinedGreen.copy(alpha = 0.16f)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = WarmOffWhite,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    fontFamily = Outfit,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = MutedSilver,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    fontFamily = Outfit
                )
            }
            PremiumButton(
                text = actionText,
                onClick = onClick,
                enabled = !confirmed,
                gradientColors = listOf(RefinedGreen, GlowGreen),
                disabledColor = ObsidianSurface,
                contentColor = if (confirmed) GlowGreen else ObsidianBg,
                fontSize = 12.sp,
                horizontalPadding = 8.dp,
                modifier = Modifier.width(78.dp)
            )
        }
    }
}
