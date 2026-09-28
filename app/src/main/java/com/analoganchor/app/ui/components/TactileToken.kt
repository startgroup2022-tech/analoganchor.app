package com.analoganchor.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analoganchor.app.ui.theme.GlowGreen
import com.analoganchor.app.ui.theme.ObsidianBorder
import com.analoganchor.app.ui.theme.ObsidianSurface

@Composable
fun TactileToken(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    emojiSize: TextUnit = 32.sp,
    borderColor: Color = ObsidianBorder,
    glowColor: Color = GlowGreen.copy(alpha = 0.15f)
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = glowColor,
                spotColor = glowColor
            )
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(ObsidianSurface, Color(0xFF0F181B))
                ),
                shape = CircleShape
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(borderColor, borderColor.copy(alpha = 0.2f), borderColor)
                ),
                shape = CircleShape
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Inner reflective glass gloss circle to mimic a physical luxury marble or acrylic lens
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0x1AFFFFFF), Color(0x02FFFFFF), Color(0x00FFFFFF))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = emojiSize,
                lineHeight = emojiSize
            )
        }
    }
}
