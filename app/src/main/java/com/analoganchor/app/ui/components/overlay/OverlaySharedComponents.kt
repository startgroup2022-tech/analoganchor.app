package com.analoganchor.app.ui.components.overlay

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Lock
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analoganchor.app.data.preferences.UserPreferences
import com.analoganchor.app.ui.components.ZenithButton
import com.analoganchor.app.ui.components.ZenithButtonSize
import com.analoganchor.app.ui.components.ZenithButtonType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BedtimeAlertPill(
    userPreferences: UserPreferences,
    modifier: Modifier = Modifier
) {
    val bedtimeInfo = remember(userPreferences) {
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val currentDay = now.get(Calendar.DAY_OF_WEEK)
        
        val yesterdayCalendar = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterdayDay = yesterdayCalendar.get(Calendar.DAY_OF_WEEK)
        
        val startParts = userPreferences.bedtimeStartTime.split(":")
        val endParts = userPreferences.bedtimeEndTime.split(":")
        val startMinutes = (startParts.getOrNull(0)?.toIntOrNull() ?: 22) * 60 + (startParts.getOrNull(1)?.toIntOrNull() ?: 0)
        val endMinutes = (endParts.getOrNull(0)?.toIntOrNull() ?: 7) * 60 + (endParts.getOrNull(1)?.toIntOrNull() ?: 0)

        var isBedtime = false
        if (userPreferences.bedtimeEnabled) {
            if (startMinutes <= endMinutes) {
                if (currentDay in userPreferences.bedtimeDays) {
                    isBedtime = currentMinutes in startMinutes until endMinutes
                }
            } else {
                if (currentDay in userPreferences.bedtimeDays && currentMinutes >= startMinutes) {
                    isBedtime = true
                } else if (yesterdayDay in userPreferences.bedtimeDays && currentMinutes < endMinutes) {
                    isBedtime = true
                }
            }
        }

        if (!isBedtime) return@remember Triple(0f, "", false)

        val totalDuration = if (endMinutes > startMinutes) {
            endMinutes - startMinutes
        } else {
            (1440 - startMinutes) + endMinutes
        }

        val elapsed = if (startMinutes <= endMinutes) {
            (currentMinutes - startMinutes).coerceAtLeast(0)
        } else {
            if (currentMinutes >= startMinutes) {
                currentMinutes - startMinutes
            } else {
                (1440 - startMinutes) + currentMinutes
            }
        }

        val progress = (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
        val remaining = (totalDuration - elapsed).coerceAtLeast(0)
        val h = remaining / 60
        val m = remaining % 60
        
        val formattedTime = if (h > 0) "${h}h ${m}m" else "${m}m"

        Triple(progress, formattedTime, true)
    }

    if (!bedtimeInfo.third) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = 1.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.its_bedtime),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.bedtime_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    lineHeight = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = bedtimeInfo.second,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.tertiary
            )

            Spacer(modifier = Modifier.width(12.dp))
            
            CircularWavyProgressIndicator(
                progress = { bedtimeInfo.first },
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                stroke = Stroke(width = 6.dp.value),
                trackStroke = Stroke(width = 6.dp.value),
                wavelength = 12.dp
            )
        }
    }
}

@Composable
fun OverlayDragHandleWithIndicators(
    modifier: Modifier = Modifier,
    currentUses: Int? = null,
    maxUses: Int? = null,
    emergencyCount: Int? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (currentUses != null && maxUses != null) {
                ZenithButton(
                    onClick = { },
                    text = "$currentUses/$maxUses",
                    icon = Icons.Outlined.Timer,
                    type = ZenithButtonType.Tonal,
                    size = ZenithButtonSize.Small,
                    modifier = Modifier.padding(start = 16.dp).widthIn(max = 110.dp),
                    isDisableWeight = true,
                    isDisableExpand = true,
                    backgroundProgressProvider = { ((maxUses - currentUses).toFloat() / maxUses.toFloat()).coerceIn(0f, 1f) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (emergencyCount != null) {
                ZenithButton(
                    onClick = { },
                    text = "$emergencyCount",
                    icon = Icons.Outlined.Bolt,
                    type = ZenithButtonType.Tonal,
                    size = ZenithButtonSize.Small,
                    modifier = Modifier.padding(end = 16.dp).widthIn(max = 110.dp),
                    isDisableWeight = true,
                    isDisableExpand = true,
                    backgroundProgressProvider = { (emergencyCount.toFloat() / 3f).coerceIn(0f, 1f) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun TotalUsagePill(totalGlobalUsageToday: Long, userPrefs: UserPreferences, modifier: Modifier = Modifier) {
    if (!userPrefs.totalUsagePillEnabled) return

    val screenTimeTargetMinutes = userPrefs.screenTimeTargetMinutes
    val totalUsageMinutes = totalGlobalUsageToday / 60000
    val isTargetExceeded = totalUsageMinutes >= screenTimeTargetMinutes && screenTimeTargetMinutes > 0

    val totalPillColor = if (isTargetExceeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val totalPillContentColor = if (isTargetExceeded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiary

    val baseColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.tertiary
    val contentColor = MaterialTheme.colorScheme.onPrimary
    val cornerRadiusLarge = 24.dp
    val cornerRadiusSmall = 4.dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Surface(
            color = totalPillColor,
            shape = RoundedCornerShape(
                topStart = cornerRadiusLarge,
                bottomStart = cornerRadiusLarge,
                topEnd = cornerRadiusSmall,
                bottomEnd = cornerRadiusSmall
            )
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.total_label),
                style = MaterialTheme.typography.labelSmall,
                color = totalPillContentColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        val remainingMinutes = (screenTimeTargetMinutes - totalUsageMinutes).coerceAtLeast(0)
        val fillRatio = if (screenTimeTargetMinutes > 0) {
            remainingMinutes.toFloat() / screenTimeTargetMinutes
        } else 0f

        Surface(
            color = baseColor,
            shape = RoundedCornerShape(
                topStart = cornerRadiusSmall,
                bottomStart = cornerRadiusSmall,
                topEnd = cornerRadiusLarge,
                bottomEnd = cornerRadiusLarge
            )
        ) {
            val separatorColor = MaterialTheme.colorScheme.surface
            Box(
                modifier = Modifier.drawBehind {
                    val progressWidth = size.width * fillRatio.coerceIn(0f, 1f)
                    drawRect(
                        color = fillColor,
                        size = Size(width = progressWidth, height = size.height)
                    )

                    if (fillRatio > 0f && fillRatio < 1f) {
                        drawRect(
                            color = separatorColor,
                            topLeft = Offset(progressWidth - 1.dp.toPx(), 0f),
                            size = Size(width = 2.dp.toPx(), height = size.height)
                        )
                    }
                }
            ) {
                Text(
                    text = formatMillis(totalGlobalUsageToday),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun EmergencyButton(onEmergencyUse: () -> Unit, onHoldingChange: (Boolean) -> Unit = {}) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val holdProgress = remember { Animatable(0f) }

    LaunchedEffect(isPressed) {
        onHoldingChange(isPressed)
        if (isPressed) {
            if (holdProgress.animateTo(1f, tween(5000, easing = LinearEasing)).endReason == AnimationEndReason.Finished) {
                onEmergencyUse()
                holdProgress.snapTo(0f)
            }
        } else {
            holdProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
        }
    }

    ZenithButton(
        onClick = {},
        type = ZenithButtonType.Filled,
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        backgroundProgressProvider = { holdProgress.value },
        interactionSource = interactionSource,
        fillMaxWidth = true,
        size = ZenithButtonSize.ExtraLarge,
        content = {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            val secondsRemaining = kotlin.math.ceil(5 * (1f - holdProgress.value)).toInt()
            Text(
                text = if (isPressed) androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.hold_progress, secondsRemaining) else androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.hold_to_use_emergency),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    )
}

@Composable
fun CloseAppTextButton(
    onCloseApp: () -> Unit,
    autoKickProgress: () -> Float = { 0f },
    size: ZenithButtonSize = ZenithButtonSize.ExtraLarge
) {
    val context = LocalContext.current
    ZenithButton(
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
        text = androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.close_app),
        type = ZenithButtonType.Text,
        contentColor = MaterialTheme.colorScheme.error,
        backgroundProgressProvider = autoKickProgress,
        fillMaxWidth = true,
        size = size
    )
}

fun calculateAvailableDurations(remainingMinutes: Int?): List<Int> {
    if (remainingMinutes == null || remainingMinutes >= 30) {
        return listOf(2, 7, 13, 30)
    }
    val maxM = remainingMinutes.coerceAtLeast(1)
    if (maxM <= 1) return listOf(1)
    if (maxM == 2) return listOf(1, 2)
    if (maxM == 3) return listOf(1, 2, 3)
    if (maxM in 4..7) {
        val step1 = 1
        val step2 = (maxM * 0.4).roundToInt().coerceIn(step1 + 1, maxM - 1)
        val step3 = (maxM * 0.7).roundToInt().coerceIn(step2 + 1, maxM - 1)
        return listOf(step1, step2, step3, maxM).distinct()
    }
    if (maxM in 8..14) {
        val step1 = 2
        val step2 = (maxM * 0.4).roundToInt().coerceIn(step1 + 1, maxM - 1)
        val step3 = (maxM * 0.7).roundToInt().coerceIn(step2 + 1, maxM - 1)
        return listOf(step1, step2, step3, maxM).distinct()
    }
    // 15..29:
    return listOf(2, 7, 13, maxM).distinct()
}

@Composable
fun DurationButtonsGrid(remainingMinutes: Int?, onAllowUse: (Int) -> Unit) {
    val outerRadius = 24.dp
    val innerRadius = 4.dp

    // Dynamically calculate durations strictly capped to remaining quota,
    // and randomize positions on each overlay appearance to eliminate muscle-memory autopilot
    val durations = remember(remainingMinutes) {
        calculateAvailableDurations(remainingMinutes).shuffled()
    }

    when (durations.size) {
        1 -> {
            DurationButton(
                minutes = durations[0],
                onAllowUse = onAllowUse,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(outerRadius)
            )
        }
        2 -> {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                DurationButton(
                    minutes = durations[0],
                    onAllowUse = onAllowUse,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(topStart = outerRadius, bottomStart = outerRadius, topEnd = innerRadius, bottomEnd = innerRadius)
                )
                DurationButton(
                    minutes = durations[1],
                    onAllowUse = onAllowUse,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(topStart = innerRadius, bottomStart = innerRadius, topEnd = outerRadius, bottomEnd = outerRadius)
                )
            }
        }
        3 -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    DurationButton(
                        minutes = durations[0],
                        onAllowUse = onAllowUse,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(topStart = outerRadius, topEnd = innerRadius, bottomStart = innerRadius, bottomEnd = innerRadius)
                    )
                    DurationButton(
                        minutes = durations[1],
                        onAllowUse = onAllowUse,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(topStart = innerRadius, topEnd = outerRadius, bottomStart = innerRadius, bottomEnd = innerRadius)
                    )
                }
                DurationButton(
                    minutes = durations[2],
                    onAllowUse = onAllowUse,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = innerRadius, topEnd = innerRadius, bottomStart = outerRadius, bottomEnd = outerRadius)
                )
            }
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    DurationButton(
                        minutes = durations[0],
                        onAllowUse = onAllowUse,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(topStart = outerRadius, topEnd = innerRadius, bottomStart = innerRadius, bottomEnd = innerRadius)
                    )
                    DurationButton(
                        minutes = durations[1],
                        onAllowUse = onAllowUse,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(topStart = innerRadius, topEnd = outerRadius, bottomStart = innerRadius, bottomEnd = innerRadius)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    DurationButton(
                        minutes = durations[2],
                        onAllowUse = onAllowUse,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(topStart = innerRadius, topEnd = innerRadius, bottomStart = outerRadius, bottomEnd = innerRadius)
                    )
                    DurationButton(
                        minutes = durations[3],
                        onAllowUse = onAllowUse,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(topStart = innerRadius, topEnd = innerRadius, bottomStart = innerRadius, bottomEnd = outerRadius)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DurationButton(
    minutes: Int,
    onAllowUse: (Int) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large
) {
    val isArabic = java.util.Locale.getDefault().language == "ar"

    FilledTonalButton(
        onClick = { onAllowUse(minutes) },
        modifier = modifier.height(64.dp),
        shape = shape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(com.analoganchor.app.R.string.minutes_label_short, minutes),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

fun formatMillis(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    return if (hours > 0) {
        "${hours}h ${minutes % 60}m"
    } else {
        "${minutes}m"
    }
}

fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}
