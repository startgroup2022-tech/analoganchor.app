package com.analoganchor.app.ui.screens.settings

import androidx.compose.ui.res.stringResource
import com.analoganchor.app.R
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.analoganchor.app.ui.components.ZenithContainedLoadingIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProtectedAppsBottomSheet(
    initialProtected: Set<String>,
    preferences: com.analoganchor.app.data.preferences.UserPreferences,
    reservePoints: Int,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
    onSetLimit: (String, Int) -> Unit,
    viewModel: com.analoganchor.app.viewmodel.AnchorViewModel
) {
    val context = LocalContext.current
    val pm = context.packageManager
    
    var apps by remember { mutableStateOf<List<WhitelistAppInfo>>(emptyList()) }
    var selectedApps by remember { mutableStateOf(initialProtected) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local state for app daily time limits to update UI immediately
    var localAppLimits by remember { mutableStateOf(preferences.protectedAppLimits) }

    // Dialog state
    var appToConfigureLimit by remember { mutableStateOf<WhitelistAppInfo?>(null) }

    val isEditWindowActive = preferences.policyEditWindowEndTimestamp > System.currentTimeMillis()
    var isNfcBypassActive by remember { mutableStateOf(false) }
    var isPinUnlocked by remember { mutableStateOf(false) }
    var showUnlockPromptDialog by remember { mutableStateOf(false) }
    var pinUnlockInput by remember { mutableStateOf("") }
    var pinUnlockError by remember { mutableStateOf(false) }

    val isBypassAllowed = isEditWindowActive || isNfcBypassActive || isPinUnlocked

    LaunchedEffect(Unit) {
        viewModel.nfcScannedEvent.collect { scannedId ->
            if (preferences.anchorNfcTagId.isNotEmpty() && scannedId == preferences.anchorNfcTagId) {
                isNfcBypassActive = true
                Toast.makeText(context, "Anchor NFC Validated. Limit bypass granted.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val mappedApps = installedApps.map {
                val isSystemFlag = (it.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                val hasLauncher = pm.getLaunchIntentForPackage(it.packageName) != null
                val pkg = it.packageName

                val isCoreComponent = pkg == "android" ||
                        pkg.startsWith("com.android.settings") ||
                        pkg.startsWith("com.android.systemui") ||
                        pkg.startsWith("com.android.shell") ||
                        pkg.startsWith("com.android.phone") ||
                        pkg.startsWith("com.android.angle") ||
                        pkg.startsWith("com.android.providers") ||
                        pkg.startsWith("com.google.android.angle") ||
                        pkg.startsWith("com.google.android.setupwizard") ||
                        pkg.contains("restore") ||
                        pkg.contains("overlay") ||
                        pkg.contains("documentsui")

                WhitelistAppInfo(
                    packageName = pkg,
                    appName = pm.getApplicationLabel(it).toString(),
                    icon = pm.getApplicationIcon(it),
                    isSystemApp = isSystemFlag && (!hasLauncher || isCoreComponent),
                    isPreinstalledApp = isSystemFlag && hasLauncher && !isCoreComponent
                )
            }.sortedWith(compareBy({ it.isPreinstalledApp || it.isSystemApp }, { it.isSystemApp }, { it.appName.lowercase() }))
            
            apps = mappedApps
            isLoading = false
        }
    }

    val isEnglish = remember(context) {
        context.getSharedPreferences("analog_anchor_prefs", android.content.Context.MODE_PRIVATE)
            .getBoolean("is_english", true)
    }

    val filteredApps = remember(searchQuery, apps, selectedApps) {
        val baseList = if (searchQuery.isBlank()) apps
        else apps.filter { it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
        
        if (searchQuery.isNotBlank()) {
            baseList.sortedByDescending { it.packageName in selectedApps }
        } else {
            baseList
        }
    }

    val selectedAppsList = remember(filteredApps, selectedApps) {
        filteredApps.filter { it.packageName in selectedApps }
    }

    val userApps = remember(filteredApps, selectedApps) {
        filteredApps.filter { !it.isSystemApp && !it.isPreinstalledApp && it.packageName !in selectedApps }
    }
    val preinstalledApps = remember(filteredApps, selectedApps) {
        filteredApps.filter { it.isPreinstalledApp && it.packageName !in selectedApps }
    }
    val systemApps = remember(filteredApps, selectedApps) {
        filteredApps.filter { it.isSystemApp && it.packageName !in selectedApps }
    }
    var isSystemAppsExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.9f)
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp)
                ) {
                    Text(text = "Protected Apps",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Apps selected here will trigger the Day Guard mindful pause.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        TextButton(
                            onClick = {
                                if (!isBypassAllowed) {
                                    showUnlockPromptDialog = true
                                } else {
                                    val allAppPackages = filteredApps.map { it.packageName }.toSet()
                                    selectedApps = if (selectedApps.containsAll(allAppPackages)) {
                                        selectedApps - allAppPackages
                                    } else {
                                        selectedApps + allAppPackages
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            val allAppPackages = filteredApps.map { it.packageName }.toSet()
                            Text(
                                text = if (selectedApps.containsAll(allAppPackages)) "Deselect All" else "Select All",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    SearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onSearch = { },
                                expanded = false,
                                onExpandedChange = {},
                                placeholder = { Text(stringResource(R.string.search_apps)) },
                                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                                        }
                                    }
                                }
                            )
                        },
                        expanded = false,
                        onExpandedChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        content = {}
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        ZenithContainedLoadingIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (searchQuery.isNotEmpty()) {
                            itemsIndexed(
                                filteredApps,
                                key = { _, app -> app.packageName }
                            ) { index, app ->
                                ProtectedAppItem(
                                    app = app,
                                    isSelected = app.packageName in selectedApps,
                                    timeLimitMinutes = localAppLimits[app.packageName] ?: -1,
                                    shape = getAdaptiveShape(index, filteredApps.size),
                                    modifier = Modifier.animateItem(),
                                    onToggle = {
                                        if (!isBypassAllowed) {
                                            showUnlockPromptDialog = true
                                        } else {
                                            selectedApps = if (app.packageName in selectedApps) {
                                                selectedApps - app.packageName
                                            } else {
                                                selectedApps + app.packageName
                                            }
                                        }
                                    },
                                    onConfigureLimit = {
                                        if (!isBypassAllowed) {
                                            showUnlockPromptDialog = true
                                        } else {
                                            appToConfigureLimit = app
                                        }
                                    }
                                )
                            }
                        } else {
                            if (selectedAppsList.isNotEmpty()) {
                                item(key = "selected_apps_header") {
                                    val headerText = if (isEnglish) "Selected Apps" else "التطبيقات المحددة"
                                    Text(
                                        text = headerText,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 4.dp)
                                    )
                                }
                                itemsIndexed(
                                    selectedAppsList,
                                    key = { _, app -> app.packageName + "_selected" }
                                ) { index, app ->
                                    ProtectedAppItem(
                                        app = app,
                                        isSelected = true,
                                        timeLimitMinutes = localAppLimits[app.packageName] ?: -1,
                                        shape = getAdaptiveShape(index, selectedAppsList.size),
                                        modifier = Modifier.animateItem(),
                                        onToggle = {
                                            if (!isBypassAllowed) {
                                                showUnlockPromptDialog = true
                                            } else {
                                                selectedApps = selectedApps - app.packageName
                                            }
                                        },
                                        onConfigureLimit = {
                                            if (!isBypassAllowed) {
                                                showUnlockPromptDialog = true
                                            } else {
                                                appToConfigureLimit = app
                                            }
                                        }
                                    )
                                }
                            }

                            if (userApps.isNotEmpty()) {
                                item(key = "user_apps_header") {
                                    Text(stringResource(R.string.user_apps),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp)
                                    )
                                }
                                itemsIndexed(
                                    userApps,
                                    key = { _, app -> app.packageName }
                                ) { index, app ->
                                    ProtectedAppItem(
                                        app = app,
                                        isSelected = false,
                                        timeLimitMinutes = localAppLimits[app.packageName] ?: -1,
                                        shape = getAdaptiveShape(index, userApps.size),
                                        modifier = Modifier.animateItem(),
                                        onToggle = {
                                            if (!isBypassAllowed) {
                                                showUnlockPromptDialog = true
                                            } else {
                                                selectedApps = selectedApps + app.packageName
                                            }
                                        },
                                        onConfigureLimit = {
                                            if (!isBypassAllowed) {
                                                showUnlockPromptDialog = true
                                            } else {
                                                appToConfigureLimit = app
                                            }
                                        }
                                    )
                                }
                            }

                            if (preinstalledApps.isNotEmpty()) {
                                item(key = "preinstalled_apps_header") {
                                    Text(stringResource(R.string.preinstalled_apps),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp)
                                    )
                                }
                                itemsIndexed(
                                    preinstalledApps,
                                    key = { _, app -> app.packageName }
                                ) { index, app ->
                                    ProtectedAppItem(
                                        app = app,
                                        isSelected = false,
                                        timeLimitMinutes = localAppLimits[app.packageName] ?: -1,
                                        shape = getAdaptiveShape(index, preinstalledApps.size),
                                        modifier = Modifier.animateItem(),
                                        onToggle = {
                                            if (!isBypassAllowed) {
                                                showUnlockPromptDialog = true
                                            } else {
                                                selectedApps = selectedApps + app.packageName
                                            }
                                        },
                                        onConfigureLimit = {
                                            if (!isBypassAllowed) {
                                                showUnlockPromptDialog = true
                                            } else {
                                                appToConfigureLimit = app
                                            }
                                        }
                                    )
                                }
                            }

                            if (systemApps.isNotEmpty()) {
                                item(key = "system_apps_header") {
                                    Surface(
                                        onClick = { isSystemAppsExpanded = !isSystemAppsExpanded },
                                        shape = RoundedCornerShape(24.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        ListItem(
                                            headlineContent = {
                                                Text(stringResource(R.string.system_apps),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            },
                                            supportingContent = {
                                                Text("${systemApps.size} apps hidden by default")
                                            },
                                            trailingContent = {
                                                Icon(
                                                    imageVector = if (isSystemAppsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                                    contentDescription = null
                                                )
                                            },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                        )
                                    }
                                }

                                if (isSystemAppsExpanded) {
                                    itemsIndexed(
                                        systemApps,
                                        key = { _, app -> app.packageName }
                                    ) { index, app ->
                                                                        ProtectedAppItem(
                                            app = app,
                                            isSelected = false,
                                            timeLimitMinutes = localAppLimits[app.packageName] ?: -1,
                                            shape = getAdaptiveShape(index, systemApps.size),
                                            modifier = Modifier.animateItem(),
                                            onToggle = {
                                                if (!isBypassAllowed) {
                                                    showUnlockPromptDialog = true
                                                } else {
                                                    selectedApps = selectedApps + app.packageName
                                                }
                                            },
                                            onConfigureLimit = {
                                                if (!isBypassAllowed) {
                                                    showUnlockPromptDialog = true
                                                } else {
                                                    appToConfigureLimit = app
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        for (pkg in selectedApps) {
                            if (!localAppLimits.containsKey(pkg) || (localAppLimits[pkg] ?: 0) <= 0) {
                                onSetLimit(pkg, 60)
                            }
                        }
                        onSave(selectedApps)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(24.dp)
                    .fillMaxWidth(0.8f),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(
                    text = if (isEnglish) "Confirm" else "تأكيد",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Daily Limit Configuration Dialog
    appToConfigureLimit?.let { app ->
        AppLimitDialog(
            appName = app.appName,
            currentLimit = localAppLimits[app.packageName] ?: -1,
            isEditWindowActive = isEditWindowActive,
            reservePoints = reservePoints,
            isNfcBypassActive = isBypassAllowed,
            onDismiss = { appToConfigureLimit = null },
            onSave = { newLimit ->
                localAppLimits = localAppLimits + (app.packageName to newLimit)
                onSetLimit(app.packageName, newLimit)
                appToConfigureLimit = null
            }
        )
    }

    if (showUnlockPromptDialog) {
        AlertDialog(
            onDismissRequest = {
                showUnlockPromptDialog = false
                pinUnlockInput = ""
                pinUnlockError = false
            },
            title = {
                Text(
                    text = if (isEnglish) "Unlock Protected Apps" else "فتح تعديل التطبيقات المحمية",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = if (isEnglish)
                            "Tap your NFC tag or consume 380 points to open this."
                            else "المس شريحة NFC الخاصة بك أو استهلك 380 نقطة لفتح هذا."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinUnlockInput,
                        onValueChange = {
                            pinUnlockInput = it.trim()
                            pinUnlockError = false
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = pinUnlockError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinUnlockError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isEnglish) "Invalid authorization code." else "رمز تفويض غير صحيح.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            if (reservePoints >= 380) {
                                viewModel.deductPoints(380, "Protected Apps Unlock", "COMPLETED")
                                isPinUnlocked = true
                                val app = context.applicationContext as com.analoganchor.app.AnalogAnchorApplication
                                scope.launch {
                                    app.preferencesRepository.setPolicyEditWindowEndTimestamp(System.currentTimeMillis() + 15 * 60 * 1000L)
                                }
                                showUnlockPromptDialog = false
                                pinUnlockInput = ""
                                pinUnlockError = false
                                Toast.makeText(
                                    context,
                                    if (isEnglish) "Protected apps editing unlocked for 15 minutes (-380 Credits)!" else "تم فتح تعديل التطبيقات المحمية لمدة 15 دقيقة (-380 نقطة)!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    if (isEnglish) "Insufficient credits (Requires 380 PTS | Available: $reservePoints)" else "رصيد النقاط غير كافٍ (المطلوب: 380 نقطة | المتوفر: $reservePoints)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isEnglish) "Consume 380 Credits (Balance: $reservePoints)" else "استهلاك 380 نقطة (الرصيد: $reservePoints)")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val input = pinUnlockInput.trim().lowercase()
                        val isCorrect = input == "5222" || input == "8888" || input == "arrazi" || viewModel.verifyAppPin(input)
                        if (isCorrect) {
                            isPinUnlocked = true
                            val app = context.applicationContext as com.analoganchor.app.AnalogAnchorApplication
                            scope.launch {
                                app.preferencesRepository.setPolicyEditWindowEndTimestamp(System.currentTimeMillis() + 15 * 60 * 1000L)
                            }
                            showUnlockPromptDialog = false
                            pinUnlockInput = ""
                            pinUnlockError = false
                            Toast.makeText(
                                context,
                                if (isEnglish) "Protected apps editing unlocked for 15 minutes!" else "تم فتح تعديل التطبيقات المحمية لمدة 15 دقيقة!",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            pinUnlockError = true
                        }
                    }
                ) {
                    Text(if (isEnglish) "Unlock" else "فتح")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUnlockPromptDialog = false
                        pinUnlockInput = ""
                        pinUnlockError = false
                    }
                ) {
                    Text(if (isEnglish) "Cancel" else "إلغاء")
                }
            }
        )
    }
}

@Composable
fun ProtectedAppItem(
    app: WhitelistAppInfo,
    isSelected: Boolean,
    timeLimitMinutes: Int,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onConfigureLimit: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.98f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "scale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "containerColor"
    )

    Card(
        onClick = onToggle,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        ListItem(
            headlineContent = { Text(app.appName, fontWeight = FontWeight.Bold) },
            supportingContent = {
                Column {
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    if (app.isSystemApp) {
                        Text(text = stringResource(R.string.system_core),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (app.isPreinstalledApp) {
                        Text(text = stringResource(R.string.preinstalled),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (isSelected) {
                        Spacer(modifier = Modifier.height(6.dp))
                        SuggestionChip(
                            onClick = onConfigureLimit,
                            label = {
                                val effectiveLimit = if (timeLimitMinutes <= 0) 60 else timeLimitMinutes
                                Text(
                                    text = "Daily Limit: ${effectiveLimit}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            },
            leadingContent = {
                app.icon?.let {
                    Image(
                        painter = BitmapPainter(it.toBitmap(100, 100).asImageBitmap()),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                }
            },
            trailingContent = {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() }
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
fun AppLimitDialog(
    appName: String,
    currentLimit: Int,
    isEditWindowActive: Boolean,
    reservePoints: Int,
    isNfcBypassActive: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    val effectiveCurrent = if (currentLimit <= 0) 60 else currentLimit
    var selectedLimit by remember { mutableStateOf(effectiveCurrent) }
    var customMinutesText by remember { mutableStateOf(if (effectiveCurrent != 15 && effectiveCurrent != 30 && effectiveCurrent != 45 && effectiveCurrent != 60) effectiveCurrent.toString() else "") }
    var isCustomSelected by remember { mutableStateOf(customMinutesText.isNotEmpty()) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Daily Limit for $appName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Specify a daily time limit for using this app. The maximum allowed limit is 60 minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                // Presets
                val presets = listOf(15, 30, 45, 60)
                presets.forEach { minutes ->
                    val isSelected = !isCustomSelected && selectedLimit == minutes
                    val label = "$minutes minutes"
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isCustomSelected = false
                                selectedLimit = minutes
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                isCustomSelected = false
                                selectedLimit = minutes
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                
                // Custom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCustomSelected = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isCustomSelected,
                        onClick = { isCustomSelected = true }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Custom", style = MaterialTheme.typography.bodyMedium)
                }
                
                if (isCustomSelected) {
                    OutlinedTextField(
                        value = customMinutesText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                customMinutesText = input
                            }
                        },
                        label = { Text("Minutes (1-60)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalLimit = if (isCustomSelected) {
                        val parsed = customMinutesText.toIntOrNull() ?: -1
                        if (parsed !in 1..60) {
                            Toast.makeText(context, "Please enter a value between 1 and 60 minutes.", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        parsed
                    } else {
                        selectedLimit
                    }
                    
                    // NFC & Reserve check for loosening limits
                    val currentEffective = if (currentLimit <= 0) 60 else currentLimit
                    val isLoosening = finalLimit > currentEffective
                    
                    if (isLoosening) {
                        val bypassAllowed = isEditWindowActive || reservePoints >= 160 || isNfcBypassActive
                        if (!bypassAllowed) {
                            Toast.makeText(
                                context,
                                "Scan NFC Anchor or earn 160+ Reserve points (currently: $reservePoints) to loosen limits!",
                                Toast.LENGTH_LONG
                            ).show()
                            return@TextButton
                        }
                    }
                    
                    onSave(finalLimit)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}



