package com.analoganchor.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

import com.analoganchor.app.data.preferences.CompactnessOption

private val DarkColorScheme = darkColorScheme(
    primary = GlowGreen,
    secondary = BrushedSteel,
    tertiary = RefinedGreen,
    background = ObsidianBg,
    surface = ObsidianSurface,
    surfaceContainer = DeepPanel,
    onPrimary = ObsidianBg,
    onSecondary = WarmOffWhite,
    onBackground = WarmOffWhite,
    onSurface = WarmOffWhite,
    surfaceVariant = ObsidianBorder,
    onSurfaceVariant = WarmSilverText,
    outline = ObsidianBorder,
    outlineVariant = SteelShadow,
    error = SignalAmber,
    onError = ObsidianBg,
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    compactnessOption: CompactnessOption = CompactnessOption.DEFAULT,
    content: @Composable () -> Unit,
) {
    // Update global state for Color.kt getters
    isDarkThemeGlobal = true

    val colorScheme = DarkColorScheme

    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    val customDensity = androidx.compose.runtime.remember(currentDensity, compactnessOption) {
        val scaleMultiplier = when (compactnessOption) {
            CompactnessOption.DEFAULT -> 1.0f
            CompactnessOption.BIGGER -> 1.15f
            CompactnessOption.SMALLER -> 0.85f
        }
        androidx.compose.ui.unit.Density(
            density = currentDensity.density * scaleMultiplier,
            fontScale = currentDensity.fontScale * scaleMultiplier
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides customDensity
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PremiumDarkRadialBrush)
            ) {
                content()
            }
        }
    }
}
