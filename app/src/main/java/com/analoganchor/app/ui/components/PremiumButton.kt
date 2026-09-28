package com.analoganchor.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analoganchor.app.ui.theme.GlowGreen
import com.analoganchor.app.ui.theme.ObsidianBg
import com.analoganchor.app.ui.theme.Outfit

@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 50.dp,
    gradientColors: List<Color> = listOf(GlowGreen, Color(0xFF1FE6FF)),
    contentColor: Color = ObsidianBg,
    disabledColor: Color = Color(0xFF172126),
    disabledContentColor: Color = Color(0xFF4E5C62),
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    horizontalPadding: Dp = 16.dp,
    icon: @Composable (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Scale animation to simulate tactile micro-feedback (3% scale down)
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1.0f,
        label = "premium_button_scale"
    )

    val shape = RoundedCornerShape(12.dp)
    
    val backgroundBrush = if (enabled) {
        Brush.linearGradient(colors = gradientColors)
    } else {
        Brush.linearGradient(colors = listOf(disabledColor, disabledColor))
    }

    val borderStroke = if (enabled) {
        BorderStroke(1.dp, Brush.linearGradient(colors = listOf(Color(0x4DFFFFFF), Color(0x00FFFFFF))))
    } else {
        BorderStroke(1.dp, Color(0x1F2B3A40))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .scale(scale)
            .clip(shape)
            .border(borderStroke, shape)
            .background(brush = backgroundBrush, shape = shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null, // Disable stock ripple to prioritize smooth premium scaling feedback
                onClick = onClick
            )
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.padding(end = 8.dp))
            }
            Text(
                text = text,
                color = if (enabled) contentColor else disabledContentColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = Outfit,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                letterSpacing = 0.5.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
