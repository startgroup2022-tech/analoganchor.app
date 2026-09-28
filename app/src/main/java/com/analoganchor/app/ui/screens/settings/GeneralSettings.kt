package com.analoganchor.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.analoganchor.app.data.preferences.UserPreferences
import com.analoganchor.app.ui.components.ZenithButton
import kotlinx.coroutines.launch

@Composable
fun GeneralSettings(
    preferences: UserPreferences,
    onAccessibilityDisabledChange: (Boolean) -> Unit,
    onShowWhitelistSheetChange: (Boolean) -> Unit,
    onOpenPermissions: () -> Unit
) {

    Column {
        PreferenceCategory(title = "General")

        SettingsActionItem(
            title = "Permissions",
            summary = "Manage required and optional system permissions",
            onClick = onOpenPermissions,
            icon = Icons.Outlined.Security,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsToggle(
            title = "Disable Accessibility",
            description = "Remove Accessibility requirements from permission checks",
            checked = preferences.accessibilityDisabled,
            onCheckedChange = onAccessibilityDisabledChange,
            icon = Icons.Outlined.AccessibilityNew,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsActionItem(
            title = "Whitelist Apps",
            summary = "${preferences.whitelistedPackages.size} apps bypassed",
            onClick = { onShowWhitelistSheetChange(true) },
            icon = Icons.Outlined.VerifiedUser,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        )
    }
}


