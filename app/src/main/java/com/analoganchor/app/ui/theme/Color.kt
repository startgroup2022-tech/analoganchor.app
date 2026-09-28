package com.analoganchor.app.ui.theme

import androidx.compose.ui.graphics.Color

// Global theme state checked by the dynamic getters below.
var isDarkThemeGlobal: Boolean = true

// Practical Minimalism palette: warm charcoal, brushed silver, and cyan glow.
val ObsidianBg: Color
    get() = if (isDarkThemeGlobal) Color(0xFF0B1012) else Color(0xFFF3F8FA)

val ObsidianSurface: Color
    get() = if (isDarkThemeGlobal) Color(0xFF11191D) else Color(0xFFFFFFFF)

val DeepPanel: Color
    get() = if (isDarkThemeGlobal) Color(0xFF172126) else Color(0xFFE6EDF0)

val ObsidianBorder: Color
    get() = if (isDarkThemeGlobal) Color(0xFF2B3A40) else Color(0xFFCFDCE1)

val SteelShadow: Color
    get() = if (isDarkThemeGlobal) Color(0xFF4E5C62) else Color(0xFF8BA0A8)

val MutedSilver: Color
    get() = if (isDarkThemeGlobal) Color(0xFFA2AFB6) else Color(0xFF4E5C62)

val WarmOffWhite: Color
    get() = if (isDarkThemeGlobal) Color(0xFFF3F8FA) else Color(0xFF0B1012)

val WarmSilverText: Color
    get() = if (isDarkThemeGlobal) Color(0xFFD1DCE1) else Color(0xFF2B3A40)

val BrushedSteel: Color
    get() = if (isDarkThemeGlobal) Color(0xFFC4D0D5) else Color(0xFF2B3A40)

val RefinedGreen: Color
    get() = if (isDarkThemeGlobal) Color(0xFF76E8F7) else Color(0xFF007A8C)

val GlowGreen: Color
    get() = if (isDarkThemeGlobal) Color(0xFF7DEEFF) else Color(0xFF00808F)

val CyanAura: Color
    get() = if (isDarkThemeGlobal) Color(0xFF1FE6FF) else Color(0xFF009688)

val SignalAmber: Color
    get() = if (isDarkThemeGlobal) Color(0xFFF3B65C) else Color(0xFFD84315)

val SignalRed: Color
    get() = if (isDarkThemeGlobal) Color(0xFFFF5252) else Color(0xFFD32F2F)

val ObsidianGlass: Color
    get() = if (isDarkThemeGlobal) Color(0x121FE6FF) else Color(0x12009688)

// Frosted Glass and Neon Glow tokens for visual depth
val ObsidianGlowBg: Color
    get() = if (isDarkThemeGlobal) Color(0xFF081013) else Color(0xFFEAF1F3)

val GlowGreenMuted: Color
    get() = if (isDarkThemeGlobal) Color(0x1A7DEEFF) else Color(0x1A00808F)

val SignalAmberMuted: Color
    get() = if (isDarkThemeGlobal) Color(0x1AF3B65C) else Color(0x1AD84315)

val SignalRedMuted: Color
    get() = if (isDarkThemeGlobal) Color(0x1AFF5252) else Color(0x1AD32F2F)

val ObsidianBorderMuted: Color
    get() = if (isDarkThemeGlobal) Color(0x332B3A40) else Color(0x33CFDCE1)

// Brushes and Gradient Grids for premium layout backdrops
val PremiumDarkRadialBrush: androidx.compose.ui.graphics.Brush
    get() = androidx.compose.ui.graphics.Brush.radialGradient(
        colors = if (isDarkThemeGlobal) {
            listOf(Color(0xFF0E1A1E), Color(0xFF0B1012))
        } else {
            listOf(Color(0xFFFFFFFF), Color(0xFFEAF1F3))
        },
        radius = 1800f
    )

val GlowGreenGradientBrush: androidx.compose.ui.graphics.Brush
    get() = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(GlowGreen, CyanAura)
    )

val SignalAmberGradientBrush: androidx.compose.ui.graphics.Brush
    get() = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(SignalAmber, if (isDarkThemeGlobal) Color(0xFFE69A30) else Color(0xFFBF360C))
    )

val SteelGradientBrush: androidx.compose.ui.graphics.Brush
    get() = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(WarmOffWhite, MutedSilver)
    )

val GlassBorderGradientBrush: androidx.compose.ui.graphics.Brush
    get() = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = if (isDarkThemeGlobal) {
            listOf(Color(0x3C7DEEFF), Color(0x0A7DEEFF), Color(0x00FFFFFF), Color(0x1E7DEEFF))
        } else {
            listOf(Color(0x3C00808F), Color(0x0A00808F), Color(0x00FFFFFF), Color(0x1E00808F))
        }
    )
