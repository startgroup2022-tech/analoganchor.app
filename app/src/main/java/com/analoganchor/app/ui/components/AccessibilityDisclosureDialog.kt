package com.analoganchor.app.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analoganchor.app.ui.theme.GlowGreen
import com.analoganchor.app.ui.theme.MutedSilver
import com.analoganchor.app.ui.theme.ObsidianBg
import com.analoganchor.app.ui.theme.ObsidianBorder
import com.analoganchor.app.ui.theme.ObsidianSurface
import com.analoganchor.app.ui.theme.RefinedGreen
import com.analoganchor.app.ui.theme.WarmOffWhite

@Composable
fun AccessibilityDisclosureDialog(
    context: Context,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianBg,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccessibilityNew,
                    contentDescription = null,
                    tint = GlowGreen,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "ACCESSIBILITY PROMINENT DISCLOSURE",
                    color = GlowGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "System Monitor & Focus Enforcement",
                    color = WarmOffWhite,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Analog Anchor utilizes Android's Accessibility Service API to detect when shielded applications enter the foreground.",
                    color = WarmOffWhite,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = GlowGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Zero Data Transmission Guarantee",
                                color = GlowGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Active window package checking runs 100% locally on device.\n• No personal data, keystrokes, messages, or usage history is collected, stored, or transmitted.",
                            color = MutedSilver,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    val zenithComponent = ComponentName(
                        context,
                        "com.analoganchor.app.service.ZenithAccessibilityService"
                    )
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        val bundle = android.os.Bundle()
                        bundle.putString(":settings:fragment_args_key", zenithComponent.flattenToString())
                        putExtra(":settings:show_fragment_args", bundle)
                        putExtra("extra_fragment_arg_key", zenithComponent.flattenToString())
                    })
                },
                colors = ButtonDefaults.buttonColors(containerColor = RefinedGreen, contentColor = ObsidianBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("AGREE & OPEN SETTINGS", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("NOT NOW", color = MutedSilver)
            }
        }
    )
}
