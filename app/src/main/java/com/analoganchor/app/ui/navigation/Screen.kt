package com.analoganchor.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object AnalogAnchor : Screen("analog_anchor", "Analog Anchor", Icons.Filled.Whatshot, Icons.Outlined.Whatshot)
    object Focus : Screen("focus", "Focus", Icons.Filled.Shield, Icons.Outlined.Shield)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    object UsageStats :
        Screen("usage_stats", "Usage Stats", Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List)

    object Bedtime : Screen("bedtime", "Bedtime", Icons.Filled.Bedtime, Icons.Outlined.Bedtime)

    object AppDetail : Screen("app_detail/{packageName}", "App Detail", Icons.Filled.Home, Icons.Outlined.Home) {
        fun createRoute(packageName: String) = "app_detail/$packageName"
    }

    object DatabaseDebug : Screen("database_debug", "Database Records", Icons.Filled.Settings, Icons.Outlined.Settings)

    object DataRepairment : Screen("data_repairment", "Data Repairment", Icons.Filled.Settings, Icons.Outlined.Settings)

    object FontTest : Screen("font_test", "Font Test", Icons.Filled.Settings, Icons.Outlined.Settings)

    object GSFlexCustomizer : Screen("gs_flex_customizer", "GS Flex Customizer", Icons.Filled.Settings, Icons.Outlined.Settings)

    object SystemUsageDebug : Screen("system_usage_debug", "System Usage Fetch", Icons.Filled.Settings, Icons.Outlined.Settings)

    object SettingsCategory : Screen("settings_category/{category}", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings) {
        fun createRoute(category: String) = "settings_category/$category"
    }
}

val navItems = listOf(
    Screen.Home,
    Screen.AnalogAnchor,
    Screen.Settings
)
