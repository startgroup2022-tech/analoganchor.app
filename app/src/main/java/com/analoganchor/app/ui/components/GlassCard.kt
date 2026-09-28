package com.analoganchor.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.analoganchor.app.ui.theme.ObsidianBorder
import com.analoganchor.app.ui.theme.ObsidianSurface

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = ObsidianBorder,
    backgroundColor: Color = ObsidianSurface,
    cornerRadius: Dp = 16.dp,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    // Frosted glass translucent background
    val glassBgColor = backgroundColor.copy(alpha = 0.85f)
    
    // Dynamic linear gradient brush to create tactile border light reflections
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            borderColor.copy(alpha = 0.9f),
            borderColor.copy(alpha = 0.25f),
            borderColor.copy(alpha = 0.1f),
            borderColor.copy(alpha = 0.45f)
        )
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        color = glassBgColor,
        border = BorderStroke(1.dp, brush = borderBrush),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
