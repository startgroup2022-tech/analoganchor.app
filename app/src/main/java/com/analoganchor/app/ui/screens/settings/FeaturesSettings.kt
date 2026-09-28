package com.analoganchor.app.ui.screens.settings

import androidx.compose.ui.res.stringResource
import com.analoganchor.app.R
import android.app.WallpaperManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.analoganchor.app.data.preferences.UserPreferences
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import androidx.appcompat.app.AppCompatDelegate

@Composable
fun FeaturesSettings(
    preferences: UserPreferences,
    onTotalUsagePillEnabledChange: (Boolean) -> Unit = {},
    onSessionUsageOverlayEnabledChange: (Boolean) -> Unit = {},
    onSessionUsageOverlaySizeChange: (Int) -> Unit,
    onSessionUsageOverlayOpacityChange: (Int) -> Unit,
    onMindfulGatewayEnabledChange: (Boolean) -> Unit = {},
    onEarlyKickEnabledChange: (Boolean) -> Unit = {},
    onInterceptAudioFocusEnabledChange: (Boolean) -> Unit = {}
) {
    Column {
        PreferenceCategory(title = stringResource(R.string.session_usage_overlay))

        HUDAppearanceSettings(
            size = preferences.sessionUsageOverlaySize,
            opacity = preferences.sessionUsageOverlayOpacity,
            onSizeChange = onSessionUsageOverlaySizeChange,
            onOpacityChange = onSessionUsageOverlayOpacityChange,
            shape = RoundedCornerShape(24.dp),
            isEnglish = !AppCompatDelegate.getApplicationLocales().toLanguageTags().contains("ar")
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HUDAppearanceSettings(
    size: Int,
    opacity: Int,
    onSizeChange: (Int) -> Unit,
    onOpacityChange: (Int) -> Unit,
    shape: Shape = RoundedCornerShape(8.dp),
    isEnglish: Boolean = true
) {
    val context = LocalContext.current
    val wallpaperDrawable = remember {
        try {
            WallpaperManager.getInstance(context).drawable
        } catch (_: Exception) {
            null
        }
    }

    var localSize by remember(size) { mutableFloatStateOf(size.toFloat()) }
    var localOpacity by remember(opacity) { mutableFloatStateOf(opacity.toFloat()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.hud_appearance),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PhotoSizeSelectSmall, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = localSize,
                    onValueChange = { localSize = it },
                    onValueChangeFinished = { onSizeChange(localSize.toInt()) },
                    valueRange = 50f..200f,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                )
                Icon(Icons.Outlined.PhotoSizeSelectLarge, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = if (isEnglish) "Size: ${localSize.toInt()}%" else "الحجم: ${localSize.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Opacity, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = localOpacity,
                    onValueChange = { localOpacity = it },
                    onValueChangeFinished = { onOpacityChange(localOpacity.toInt()) },
                    valueRange = 20f..100f,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                )
                Icon(Icons.Outlined.Contrast, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = if (isEnglish) "Opacity: ${localOpacity.toInt()}%" else "الشفافية: ${localOpacity.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(text = stringResource(R.string.preview),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (wallpaperDrawable != null) {
                    androidx.compose.foundation.Image(
                        painter = rememberDrawablePainter(wallpaperDrawable),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                }

                val animatedScale by animateFloatAsState(
                    targetValue = localSize / 100f,
                    label = "HUDScale"
                )
                val animatedOpacity by animateFloatAsState(
                    targetValue = localOpacity / 100f,
                    label = "HUDOpacity"
                )

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                            alpha = animatedOpacity
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularWavyProgressIndicator(
                        progress = { 0.75f },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        stroke = Stroke(width = 6.dp.value),
                        trackStroke = Stroke(width = 6.dp.value),
                        wavelength = 20.dp,
                        waveSpeed = 0.dp
                    )
                    Text(text = stringResource(R.string.s_15m),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
