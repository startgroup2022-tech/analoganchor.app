package com.analoganchor.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import com.analoganchor.app.ui.theme.GlowGreen
import com.analoganchor.app.ui.theme.MutedSilver
import com.analoganchor.app.ui.theme.ObsidianBorder
import com.analoganchor.app.ui.theme.ObsidianSurface
import com.analoganchor.app.ui.theme.Outfit
import com.analoganchor.app.ui.theme.WarmOffWhite
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Smooth focus animation properties
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) GlowGreen else ObsidianBorder,
        label = "text_field_border_color"
    )
    val glowElevation by animateDpAsState(
        targetValue = if (isFocused) 8.dp else 0.dp,
        label = "text_field_glow_elevation"
    )

    val shape = RoundedCornerShape(12.dp)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(
                elevation = glowElevation,
                shape = shape,
                ambientColor = GlowGreen.copy(alpha = 0.3f),
                spotColor = GlowGreen.copy(alpha = 0.3f)
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = if (isFocused) {
                        listOf(GlowGreen, GlowGreen.copy(alpha = 0.4f))
                    } else {
                        listOf(ObsidianBorder, ObsidianBorder.copy(alpha = 0.5f))
                    }
                ),
                shape = shape
            )
            .clip(shape)
            .background(ObsidianSurface),
        textStyle = TextStyle(
            color = WarmOffWhite,
            fontSize = 15.sp,
            fontFamily = Outfit
        ),
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(GlowGreen),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholderText,
                        color = MutedSilver.copy(alpha = 0.55f),
                        fontSize = 15.sp,
                        fontFamily = Outfit
                    )
                }
                innerTextField()
            }
        }
    )
}
