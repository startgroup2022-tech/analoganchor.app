package com.analoganchor.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analoganchor.app.ui.components.GlassCard
import com.analoganchor.app.ui.components.TactileToken
import com.analoganchor.app.ui.components.PremiumButton
import com.analoganchor.app.ui.theme.*
import com.analoganchor.app.util.AppBlockIntegration
import com.analoganchor.app.viewmodel.AnchorViewModel
import com.analoganchor.app.viewmodel.MapboxPlaceCandidate
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.TextStyle
import java.util.Locale
import androidx.core.content.ContextCompat

private fun onboardingHasUsableGps(lat: Double, lng: Double): Boolean {
    return kotlin.math.abs(lat) > 0.00001 || kotlin.math.abs(lng) > 0.00001
}

@Composable
fun OnboardingScreen(
    viewModel: AnchorViewModel,
    onFinishOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val preferencesRepository = remember { (context.applicationContext as com.analoganchor.app.AnalogAnchorApplication).preferencesRepository }
    val preferences by preferencesRepository.userPreferencesFlow.collectAsState(initial = com.analoganchor.app.data.preferences.UserPreferences())
    var showPermissionBottomSheet by remember { mutableStateOf(false) }

    var currentStep by remember { mutableIntStateOf(1) }
    val nfcTagId by viewModel.nfcTagId.collectAsState()
    val fajrNfcTagId by viewModel.fajrNfcTagId.collectAsState()
    val sanctuaryType by viewModel.sanctuaryType.collectAsState()
    val eveningProtocolTime by viewModel.eveningProtocolTime.collectAsState()

    var isNfcScanning by remember { mutableStateOf(false) }
    var locationSaved by remember { mutableStateOf(false) }
    var activeNfcSlot by remember { mutableStateOf("anchor") } // "anchor" or "fajr"
    var isSoftOnboardingMode by remember { mutableStateOf(false) }

    // Real-time GPS coordinate stream
    val userLatitude by viewModel.userLatitude.collectAsState()
    val userLongitude by viewModel.userLongitude.collectAsState()
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.requestRealLocation()
            viewModel.refreshNearbyMosques()
        } else {
            Toast.makeText(context, "Location permission is needed for live mosque and runway GPS.", Toast.LENGTH_LONG).show()
        }
    }

    val syncRealLocation = {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            viewModel.requestRealLocation()
            viewModel.refreshNearbyMosques()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(currentStep, activeNfcSlot) {
        if (currentStep == 1) {
            viewModel.setActiveNfcRegistrationSlot(activeNfcSlot)
        } else {
            viewModel.setActiveNfcRegistrationSlot(null)
        }
    }

    LaunchedEffect(currentStep, activeNfcSlot, nfcTagId) {
        if (currentStep == 1) {
            viewModel.nfcScannedEvent.collect { scannedId ->
                if (activeNfcSlot == "anchor") {
                    viewModel.registerNfcTag(scannedId)
                    if (fajrNfcTagId.isEmpty()) {
                        activeNfcSlot = "fajr"
                        viewModel.setActiveNfcRegistrationSlot("fajr")
                    }
                } else {
                    viewModel.registerFajrNfcTag(scannedId)
                    viewModel.setActiveNfcRegistrationSlot(null)
                }
            }
        }
        if (currentStep == 2 || currentStep == 3) {
            syncRealLocation()
        }
    }
    
    // Live map center coordinates tracking for sanctuary
    var sanctuaryMapCenterLat by remember(userLatitude) { mutableStateOf(userLatitude) }
    var sanctuaryMapCenterLng by remember(userLongitude) { mutableStateOf(userLongitude) }
    val lockedMosques by viewModel.lockedMosques.collectAsState()
    val lockedOutdoorSpots by viewModel.lockedOutdoorSpots.collectAsState()

    // Live map center coordinates tracking for home base
    var homeMapCenterLat by remember(userLatitude) { mutableStateOf(userLatitude) }
    var homeMapCenterLng by remember(userLongitude) { mutableStateOf(userLongitude) }

    // Sanctuary setup variables
    var selectedSanctuary by remember { mutableStateOf("both") }

    // Evening time values
    var selectedHour by remember { mutableIntStateOf(22) }
    var selectedMinute by remember { mutableIntStateOf(0) }
    var shortcutStepDone by remember { mutableStateOf(viewModel.isAnchorLockShortcutDone()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP HEADER
        Column {
            Spacer(modifier = Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CLIENT ONBOARDING",
                    color = MutedSilver,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "$currentStep of 4",
                        color = GlowGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "SKIP",
                        color = WarmOffWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(ObsidianSurface, RoundedCornerShape(6.dp))
                            .border(BorderStroke(1.dp, ObsidianBorder), RoundedCornerShape(6.dp))
                            .clickable {
                                if (fajrNfcTagId.isEmpty() && nfcTagId.isNotEmpty()) {
                                    viewModel.registerFajrNfcTag(nfcTagId)
                                }
                                onFinishOnboarding()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { currentStep / 4f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = GlowGreen,
                trackColor = ObsidianBorderMuted
            )
        }

        // STEP CONTENT CARDS
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut() using SizeTransform(clip = false)
                },
                label = "step_animation"
            ) { step ->
                when (step) {
                    1 -> {
                        // Card 1: Dual NFC Tag Registration
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TactileToken(
                                emoji = "📡", 
                                size = 80.dp, 
                                emojiSize = 42.sp, 
                                borderColor = RefinedGreen,
                                glowColor = RefinedGreen.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "NFC Dual Tag Registration",
                                color = WarmOffWhite,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Outfit
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Bind your 2 physical NFC tags: your main Whiteboard Anchor tag & your Bedroom Fajr Eraser tag.",
                                color = MutedSilver,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                fontFamily = Outfit
                            )
                            Spacer(modifier = Modifier.height(18.dp))

                            // Slot 1: Main Anchor Tag (Whiteboard)
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeNfcSlot = "anchor" }
                                    .border(
                                        width = if (activeNfcSlot == "anchor") 1.5.dp else 0.dp,
                                        color = if (activeNfcSlot == "anchor") GlowGreen else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("📋", fontSize = 22.sp)
                                        Column {
                                            Text(
                                                text = "Main Anchor Tag (Whiteboard)",
                                                color = WarmOffWhite,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = Outfit
                                            )
                                            Text(
                                                text = if (nfcTagId.isNotEmpty()) "ID: $nfcTagId" else if (activeNfcSlot == "anchor") "Listening for tap..." else "Tap card to scan tag",
                                                color = if (nfcTagId.isNotEmpty()) GlowGreen else if (activeNfcSlot == "anchor") SignalAmber else MutedSilver,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                    if (nfcTagId.isNotEmpty()) {
                                        Text("✓ Bound", color = GlowGreen, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, fontFamily = Outfit)
                                    } else if (activeNfcSlot == "anchor") {
                                        Text("● ACTIVE", color = SignalAmber, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Slot 2: Fajr Eraser Tag (Bedroom)
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeNfcSlot = "fajr" }
                                    .border(
                                        width = if (activeNfcSlot == "fajr") 1.5.dp else 0.dp,
                                        color = if (activeNfcSlot == "fajr") GlowGreen else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("🧹", fontSize = 22.sp)
                                        Column {
                                            Text(
                                                text = "Fajr Eraser Tag (Bedroom Lamp)",
                                                color = WarmOffWhite,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = Outfit
                                            )
                                            Text(
                                                text = if (fajrNfcTagId.isNotEmpty()) "ID: $fajrNfcTagId" else if (activeNfcSlot == "fajr") "Listening for tap..." else "Tap card to scan tag",
                                                color = if (fajrNfcTagId.isNotEmpty()) GlowGreen else if (activeNfcSlot == "fajr") SignalAmber else MutedSilver,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                    if (fajrNfcTagId.isNotEmpty()) {
                                        Text("✓ Bound", color = GlowGreen, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, fontFamily = Outfit)
                                    } else if (activeNfcSlot == "fajr") {
                                        Text("● ACTIVE", color = SignalAmber, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Soft Onboarding Card (No NFC Tag Required)
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isSoftOnboardingMode = true
                                        if (nfcTagId.isEmpty()) {
                                            viewModel.registerNfcTag("VIRTUAL_SOFT_TAG")
                                        }
                                        if (fajrNfcTagId.isEmpty()) {
                                            viewModel.registerFajrNfcTag("VIRTUAL_SOFT_FAJR_TAG")
                                        }
                                        viewModel.setFajrAlarmAyahConstraint(true)
                                        Toast.makeText(context, "Soft mode active. Continuing to Sanctuary Map...", Toast.LENGTH_SHORT).show()
                                        currentStep = 2
                                    }
                                    .border(
                                        width = if (isSoftOnboardingMode) 1.5.dp else 1.dp,
                                        color = if (isSoftOnboardingMode) GlowGreen else ObsidianBorder,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (isSoftOnboardingMode) "✓ Soft Onboarding Active" else "⚡ No Physical NFC Tag? Enable Soft Onboarding",
                                        color = GlowGreen,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = Outfit
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Start immediately using Ayah Typing or Friend PIN for Fajr alarms without physical hardware. Advances to Mosque & Outdoor map setup.",
                                        color = MutedSilver,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        fontFamily = Outfit
                                    )
                                }
                            }


                        }
                    }

                    2 -> {
                        // Card 2: Permanent Sanctuary Setup (Founder Lockdown: 3 Mosques & 3 Outdoor Spots)
                        var onboardingSearchQuery by remember { mutableStateOf("") }
                        val onboardingSearchResults by viewModel.outdoorSearchResults.collectAsState()
                        val isSearchingOnboarding by viewModel.isSearchingOutdoor.collectAsState()
                        var selectedOnboardingCandidate by remember { mutableStateOf<MapboxPlaceCandidate?>(null) }
                        var activeLockdownTab by remember { mutableStateOf("mosque") } // "mosque" or "outdoor"

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "SANCTUARY LOCKDOWN",
                                        color = WarmOffWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "تثبيت المساجد والوجهات الخارجية الدائمة",
                                        color = GlowGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = Outfit
                                    )
                                }
                                Text(
                                    text = if (activeLockdownTab == "mosque") "🕌 3 MOSQUES" else "🏃 3 OUTDOOR",
                                    color = if (activeLockdownTab == "mosque") GlowGreen else SignalAmber,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // Interactive Map Canvas with Floating Mapbox Search Bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(270.dp)
                            ) {
                                val pinsList = remember(selectedOnboardingCandidate, lockedMosques, lockedOutdoorSpots, activeLockdownTab) {
                                    val list = mutableListOf<MapPin>()
                                    selectedOnboardingCandidate?.let {
                                        list.add(MapPin(it.name, it.address, it.lat, it.lng, "📍"))
                                    }
                                    if (activeLockdownTab == "mosque") {
                                        lockedMosques.forEach { m ->
                                            list.add(MapPin(m.name, m.address, m.lat, m.lng, "🕌"))
                                        }
                                    } else {
                                        lockedOutdoorSpots.forEach { o ->
                                            list.add(MapPin(o.name, o.address, o.lat, o.lng, "🏃"))
                                        }
                                    }
                                    list
                                }

                                EmbeddedInteractiveMapCanvas(
                                    modifier = Modifier.fillMaxSize(),
                                    userLat = userLatitude,
                                    userLng = userLongitude,
                                    pins = pinsList,
                                    selectedPinName = selectedOnboardingCandidate?.name,
                                    isRunawayMode = true,
                                    showCentralCircuitTarget = true,
                                    isEnglish = false,
                                    onCenterChanged = { lat, lng ->
                                        sanctuaryMapCenterLat = lat
                                        sanctuaryMapCenterLng = lng
                                    }
                                )

                                // Floating dark-glass Bahrain places search bar at bottom of map
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    if (onboardingSearchResults.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(DeepPanel.copy(alpha = 0.97f), RoundedCornerShape(10.dp))
                                                .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                                                .padding(vertical = 4.dp)
                                        ) {
                                            onboardingSearchResults.forEach { place ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            selectedOnboardingCandidate = place
                                                            sanctuaryMapCenterLat = place.lat
                                                            sanctuaryMapCenterLng = place.lng
                                                            onboardingSearchQuery = place.name
                                                            viewModel.clearOutdoorSearchResults()
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("📍", fontSize = 12.sp)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(place.name, color = WarmOffWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Outfit, maxLines = 1)
                                                        Text(place.address, color = MutedSilver, fontSize = 10.sp, fontFamily = Outfit, maxLines = 1)
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(ObsidianSurface.copy(alpha = 0.94f), RoundedCornerShape(10.dp))
                                            .border(1.dp, GlowGreen.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🔍", fontSize = 13.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        BasicTextField(
                                            value = onboardingSearchQuery,
                                            onValueChange = { q ->
                                                onboardingSearchQuery = q
                                                viewModel.searchOutdoorDestinations(q)
                                            },
                                            textStyle = TextStyle(
                                                color = WarmOffWhite,
                                                fontSize = 12.sp,
                                                fontFamily = Outfit,
                                                textAlign = TextAlign.Center
                                            ),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (onboardingSearchQuery.isEmpty()) {
                                                        Text(
                                                            text = "Search place or Plus Code...",
                                                            color = MutedSilver.copy(alpha = 0.6f),
                                                            fontSize = 11.sp,
                                                            fontFamily = Outfit,
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )
                                        if (isSearchingOnboarding) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = GlowGreen)
                                        } else if (onboardingSearchQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    onboardingSearchQuery = ""
                                                    viewModel.clearOutdoorSearchResults()
                                                },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Text("✕", color = MutedSilver, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Sub-tabs: 3 Mosques vs 3 Outdoor Hubs
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val mosqueTabActive = activeLockdownTab == "mosque"
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (mosqueTabActive) GlowGreen.copy(alpha = 0.15f) else ObsidianSurface,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (mosqueTabActive) GlowGreen else ObsidianBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { activeLockdownTab = "mosque" }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "🕌 3 MOSQUES",
                                        color = if (mosqueTabActive) GlowGreen else MutedSilver,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                val outdoorTabActive = activeLockdownTab == "outdoor"
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (outdoorTabActive) GlowGreen.copy(alpha = 0.15f) else ObsidianSurface,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (outdoorTabActive) GlowGreen else ObsidianBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { activeLockdownTab = "outdoor" }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "🏃 3 OUTDOOR HUBS",
                                        color = if (outdoorTabActive) GlowGreen else MutedSilver,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Slot Cards for Lockdown
                            if (activeLockdownTab == "mosque") {
                                for (slot in 0 until 3) {
                                    val locked = lockedMosques.getOrNull(slot)
                                    val isSlotFilled = locked != null
                                    GlassCard(
                                        borderColor = if (isSlotFilled) GlowGreen.copy(alpha = 0.6f) else ObsidianBorder,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "MOSQUE SLOT #${slot + 1}",
                                                    color = GlowGreen,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    text = locked?.name ?: "Pan map / search to pin",
                                                    color = WarmOffWhite,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = Outfit,
                                                    maxLines = 1
                                                )
                                                if (locked != null) {
                                                    Text(
                                                        text = String.format(Locale.US, "%.4f, %.4f • %s", locked.lat, locked.lng, locked.address),
                                                        color = MutedSilver,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(if (isSlotFilled) GlowGreen.copy(alpha = 0.18f) else ObsidianSurface, RoundedCornerShape(8.dp))
                                                    .border(1.dp, if (isSlotFilled) GlowGreen else ObsidianBorder, RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        val targetLat = if (onboardingHasUsableGps(sanctuaryMapCenterLat, sanctuaryMapCenterLng)) sanctuaryMapCenterLat else userLatitude
                                                        val targetLng = if (onboardingHasUsableGps(sanctuaryMapCenterLat, sanctuaryMapCenterLng)) sanctuaryMapCenterLng else userLongitude
                                                        val name = selectedOnboardingCandidate?.name?.takeIf { it.isNotBlank() }
                                                            ?: onboardingSearchQuery.takeIf { it.isNotBlank() }
                                                            ?: "Mosque #${slot + 1}"
                                                        val addr = selectedOnboardingCandidate?.address ?: "Bahrain Sanctuary Zone"
                                                        val (ok, msg) = viewModel.lockdownMosque(slot, name, addr, targetLat, targetLng)
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (isSlotFilled) "✓ LOCKED" else "🔒 LOCKDOWN",
                                                    color = if (isSlotFilled) GlowGreen else WarmOffWhite,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                for (slot in 0 until 3) {
                                    val locked = lockedOutdoorSpots.getOrNull(slot)
                                    val isSlotFilled = locked != null
                                    GlassCard(
                                        borderColor = if (isSlotFilled) GlowGreen.copy(alpha = 0.6f) else ObsidianBorder,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "OUTDOOR HUB #${slot + 1}",
                                                    color = GlowGreen,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    text = locked?.name ?: "Pan map / search to pin",
                                                    color = WarmOffWhite,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = Outfit,
                                                    maxLines = 1
                                                )
                                                if (locked != null) {
                                                    Text(
                                                        text = String.format(Locale.US, "%.4f, %.4f • %s", locked.lat, locked.lng, locked.address),
                                                        color = MutedSilver,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(if (isSlotFilled) GlowGreen.copy(alpha = 0.18f) else ObsidianSurface, RoundedCornerShape(8.dp))
                                                    .border(1.dp, if (isSlotFilled) GlowGreen else ObsidianBorder, RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        val targetLat = if (onboardingHasUsableGps(sanctuaryMapCenterLat, sanctuaryMapCenterLng)) sanctuaryMapCenterLat else userLatitude
                                                        val targetLng = if (onboardingHasUsableGps(sanctuaryMapCenterLat, sanctuaryMapCenterLng)) sanctuaryMapCenterLng else userLongitude
                                                        val name = selectedOnboardingCandidate?.name?.takeIf { it.isNotBlank() }
                                                            ?: onboardingSearchQuery.takeIf { it.isNotBlank() }
                                                            ?: "Outdoor Hub #${slot + 1}"
                                                        val addr = selectedOnboardingCandidate?.address ?: "Bahrain Outdoor Destination"
                                                        val (ok, msg) = viewModel.lockdownOutdoorSpot(slot, name, addr, targetLat, targetLng)
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (isSlotFilled) "✓ LOCKED" else "🔒 LOCKDOWN",
                                                    color = if (isSlotFilled) GlowGreen else WarmOffWhite,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        // Card 3: Home GPS Baseline Registration
                        val homeBaseSaved by viewModel.homeBaseSaved.collectAsState()
                        val homeLatitude by viewModel.homeLatitude.collectAsState()
                        val homeLongitude by viewModel.homeLongitude.collectAsState()

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TactileToken(
                                emoji = "🏠", 
                                size = 90.dp, 
                                emojiSize = 48.sp, 
                                borderColor = RefinedGreen,
                                glowColor = RefinedGreen.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Home Anchor Location",
                                color = WarmOffWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Outfit
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Record your home location coordinates. This baseline anchors the geofencing calculations to verify when you leave for Mosque or Runway workouts.",
                                color = MutedSilver,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                fontFamily = Outfit
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            // Embedded Interactive Map in Home Selection Mode (Crosshair)
                            EmbeddedInteractiveMapCanvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp),
                                userLat = userLatitude,
                                userLng = userLongitude,
                                isHomeSelectionMode = true,
                                onCenterChanged = { lat, lng ->
                                    homeMapCenterLat = lat
                                    homeMapCenterLng = lng
                                }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            GlassCard(borderColor = if (homeBaseSaved) GlowGreen else ObsidianBorder) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (!homeBaseSaved) {
                                        Text(
                                            text = "Home Baseline Unregistered",
                                            color = SignalAmber,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = Outfit
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = String.format(java.util.Locale.US, "Current GPS: %.5f, %.5f", userLatitude, userLongitude),
                                            color = MutedSilver,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("✓", color = GlowGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text(
                                                text = "Home Base Locked",
                                                color = GlowGreen,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = Outfit
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = String.format(java.util.Locale.US, "Coordinates: %.5f, %.5f", homeLatitude, homeLongitude),
                                            color = MutedSilver,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        PremiumButton(
                                            text = "LOCK HOME BASE",
                                            onClick = {
                                                if (onboardingHasUsableGps(homeMapCenterLat, homeMapCenterLng)) {
                                                    viewModel.setHomeBaselineManual(homeMapCenterLat, homeMapCenterLng)
                                                    Toast.makeText(context, "Home coordinates locked at center.", Toast.LENGTH_SHORT).show()
                                                    currentStep += 1 // Auto-advance to step 4 instantly
                                                } else {
                                                    syncRealLocation()
                                                    Toast.makeText(context, "Sync GPS before locking home base.", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            gradientColors = listOf(RefinedGreen, GlowGreen),
                                            modifier = Modifier.weight(1.3f)
                                        )

                                        PremiumButton(
                                            text = "SKIP",
                                            onClick = {
                                                if (onboardingHasUsableGps(homeMapCenterLat, homeMapCenterLng)) {
                                                    viewModel.setHomeBaselineManual(homeMapCenterLat, homeMapCenterLng)
                                                    Toast.makeText(context, "Using current GPS center.", Toast.LENGTH_SHORT).show()
                                                    currentStep += 1 // Auto-advance to step 4 instantly
                                                } else {
                                                    syncRealLocation()
                                                    Toast.makeText(context, "Sync GPS before skipping home setup.", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            gradientColors = listOf(DeepPanel, ObsidianSurface),
                                            contentColor = WarmOffWhite,
                                            modifier = Modifier.weight(0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    4 -> {
                        // Card 4: Evening Protocol & Bedtime Cutoff Selection
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            TactileToken(
                                emoji = "⏱️", 
                                size = 80.dp, 
                                emojiSize = 42.sp, 
                                borderColor = RefinedGreen,
                                glowColor = RefinedGreen.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Bedtime & Accountability Cutoff",
                                color = WarmOffWhite,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Outfit,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "This is your permanent daily bedtime deadline and cannot be changed later. Make this decision carefully.",
                                color = SignalAmber,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                fontFamily = Outfit
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            // Key Benefits / Rule Highlights Card
                            GlassCard(borderColor = GlowGreen.copy(alpha = 0.35f)) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("⏱️", fontSize = 16.sp)
                                        Column {
                                            Text(
                                                text = "Daily Pomodoro Focus Deadline",
                                                color = GlowGreen,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = Outfit
                                            )
                                            Text(
                                                text = "Your daily Pomodoro session must be completed before this hour. Any session done after this cutoff counts toward the next day. Missing the 24-hour cycle incurs a -10 PTS penalty.",
                                                color = WarmSilverText,
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp,
                                                fontFamily = Outfit
                                            )
                                        }
                                    }

                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ObsidianBorder))

                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("🔒", fontSize = 16.sp)
                                        Column {
                                            Text(
                                                text = "Day Guard Apps Bedtime Lock",
                                                color = RefinedGreen,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = Outfit
                                            )
                                            Text(
                                                text = "All protected apps in Day Guard strictly lock at this hour regardless of remaining daily limits (e.g. even if you have 15 min left on Instagram, access ceases at bedtime).",
                                                color = WarmSilverText,
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp,
                                                fontFamily = Outfit
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            GlassCard {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Accountability Cutoff",
                                        color = MutedSilver,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = Outfit
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Time display text inside a premium glass HUD segment
                                    val formattedTime = String.format("%02d:%02d PM", selectedHour, selectedMinute)
                                    Box(
                                        modifier = Modifier
                                            .background(ObsidianBg, RoundedCornerShape(8.dp))
                                            .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 24.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = formattedTime,
                                            color = GlowGreen,
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Custom visual selectors for Hour & Minute using elegant circular buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Hour", fontSize = 13.sp, color = MutedSilver, fontFamily = Outfit, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            val allowedOnboardingHours = listOf(20, 21, 22, 23, 0, 1)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .background(ObsidianSurface, RoundedCornerShape(17.dp))
                                                        .border(1.dp, ObsidianBorder, RoundedCornerShape(17.dp))
                                                        .clickable {
                                                            val currentIndex = allowedOnboardingHours.indexOf(selectedHour).takeIf { it != -1 } ?: 2
                                                            val newIndex = if (currentIndex > 0) currentIndex - 1 else allowedOnboardingHours.size - 1
                                                            selectedHour = allowedOnboardingHours[newIndex]
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("◀", color = GlowGreen, fontSize = 13.sp)
                                                }
                                                Text(
                                                    String.format(java.util.Locale.US, "%02d", selectedHour),
                                                    color = WarmOffWhite,
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.padding(horizontal = 12.dp)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .background(ObsidianSurface, RoundedCornerShape(17.dp))
                                                        .border(1.dp, ObsidianBorder, RoundedCornerShape(17.dp))
                                                        .clickable {
                                                            val currentIndex = allowedOnboardingHours.indexOf(selectedHour).takeIf { it != -1 } ?: 2
                                                            val newIndex = if (currentIndex < allowedOnboardingHours.size - 1) currentIndex + 1 else 0
                                                            selectedHour = allowedOnboardingHours[newIndex]
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("▶", color = GlowGreen, fontSize = 13.sp)
                                                }
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Minute", fontSize = 13.sp, color = MutedSilver, fontFamily = Outfit, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .background(ObsidianSurface, RoundedCornerShape(17.dp))
                                                        .border(1.dp, ObsidianBorder, RoundedCornerShape(17.dp))
                                                        .clickable { if (selectedMinute >= 15) selectedMinute -= 15 },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("◀", color = GlowGreen, fontSize = 13.sp)
                                                }
                                                Text(
                                                    String.format("%02d", selectedMinute),
                                                    color = WarmOffWhite,
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.padding(horizontal = 12.dp)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .background(ObsidianSurface, RoundedCornerShape(17.dp))
                                                        .border(1.dp, ObsidianBorder, RoundedCornerShape(17.dp))
                                                        .clickable { if (selectedMinute <= 30) selectedMinute += 15 },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("▶", color = GlowGreen, fontSize = 13.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // NAVIGATION ACTIONS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentStep > 1) {
                PremiumButton(
                    text = "BACK",
                    onClick = { currentStep -= 1 },
                    gradientColors = listOf(Color.Transparent, Color.Transparent),
                    contentColor = WarmOffWhite,
                    disabledColor = Color.Transparent,
                    modifier = Modifier
                        .width(90.dp)
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                )
            } else {
                Spacer(modifier = Modifier.width(90.dp))
            }

            if (currentStep < 4) {
                // Next step buttons
                val nextEnabled = when (currentStep) {
                    1 -> nfcTagId.isNotEmpty() || isSoftOnboardingMode
                    2 -> true
                    3 -> true
                    else -> true
                }

                PremiumButton(
                    text = "CONTINUE",
                    onClick = {
                        if (currentStep == 1) {
                            if (fajrNfcTagId.isEmpty() && nfcTagId.isNotEmpty()) {
                                viewModel.registerFajrNfcTag(nfcTagId)
                            }
                        }
                        if (currentStep == 2) {
                            val primaryMosque = lockedMosques.firstOrNull()
                            val mosqueLat = primaryMosque?.lat?.toFloat() ?: sanctuaryMapCenterLat.toFloat()
                            val mosqueLng = primaryMosque?.lng?.toFloat() ?: sanctuaryMapCenterLng.toFloat()
                            val mosqueName = primaryMosque?.name ?: "Designated Mosque"
                            val mosqueAddr = primaryMosque?.address ?: "Bahrain Sanctuary Zone"
                            viewModel.saveDesignatedMosque(mosqueName, mosqueAddr, mosqueLat, mosqueLng, type = "both")

                            val primaryOutdoor = lockedOutdoorSpots.firstOrNull()
                            val outdoorLat = primaryOutdoor?.lat?.toFloat() ?: sanctuaryMapCenterLat.toFloat()
                            val outdoorLng = primaryOutdoor?.lng?.toFloat() ?: sanctuaryMapCenterLng.toFloat()
                            val outdoorName = primaryOutdoor?.name ?: "Favorite Outdoor Sanctuary"
                            val outdoorAddr = primaryOutdoor?.address ?: "Bahrain Outdoor Destination"
                            viewModel.saveOutdoorSanctuary(outdoorName, outdoorAddr, outdoorLat, outdoorLng, type = "both")

                            locationSaved = true
                        }
                        currentStep += 1
                    },
                    enabled = nextEnabled,
                    gradientColors = listOf(RefinedGreen, GlowGreen),
                    modifier = Modifier.width(128.dp)
                )
            } else {
                // Final begin button on Step 4!
                PremiumButton(
                    text = "BEGIN PLATFORM",
                    onClick = {
                        val finalTimeStr = String.format("%02d:%02d", selectedHour, selectedMinute)
                        viewModel.saveEveningProtocolTime(finalTimeStr)
                        showPermissionBottomSheet = true
                    },
                    enabled = true,
                    gradientColors = listOf(GlowGreen, RefinedGreen),
                    contentColor = ObsidianBg,
                    modifier = Modifier
                        .width(156.dp)
                        .testTag("begin_onboarding_btn")
                )
            }
        }

        if (showPermissionBottomSheet) {
            com.analoganchor.app.ui.components.PermissionBottomSheet(
                preferencesRepository = preferencesRepository,
                onDismissRequest = { showPermissionBottomSheet = false },
                onAllPermissionsGranted = {
                    showPermissionBottomSheet = false
                    onFinishOnboarding()
                }
            )
        }
    }
}

private data class BusaiteenMosque(
    val name: String,
    val address: String,
    val distance: Int,
    val lat: Float,
    val lng: Float
)
