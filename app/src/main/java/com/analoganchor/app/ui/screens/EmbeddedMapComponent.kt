package com.analoganchor.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analoganchor.app.ui.theme.GlowGreen
import com.analoganchor.app.ui.theme.ObsidianBg
import com.analoganchor.app.ui.theme.ObsidianBorder
import com.analoganchor.app.ui.theme.ObsidianSurface
import com.analoganchor.app.ui.theme.SignalAmber
import com.analoganchor.app.ui.theme.WarmOffWhite
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.style.MapStyle
import java.util.Locale

data class MapPin(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val emoji: String = "M"
)

@Composable
fun InteractiveGpsTargetCircuit(
    modifier: Modifier = Modifier,
    sizeDp: Int = 86,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarTargetPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .scale(pulseScale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing cyan/turquoise ring with subtle translucent background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x2E00E5FF), CircleShape)
                .border(2.5.dp, Color(0xFF00E5FF).copy(alpha = pulseAlpha), CircleShape)
        )

        // Central white disc
        Box(
            modifier = Modifier
                .size((sizeDp * 0.42f).dp)
                .background(Color.White, CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // 4-quadrant blue crosshair matching the screenshot reference
            ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                val center = size / 2f
                val blueColor = Color(0xFF1E88E5)

                // Top-Left quadrant
                drawArc(
                    color = blueColor,
                    startAngle = 180f,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = androidx.compose.ui.geometry.Offset.Zero,
                    size = size
                )
                // Top-Right quadrant
                drawArc(
                    color = blueColor,
                    startAngle = 270f,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = androidx.compose.ui.geometry.Offset.Zero,
                    size = size
                )
                // Bottom-Right quadrant
                drawArc(
                    color = blueColor,
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = androidx.compose.ui.geometry.Offset.Zero,
                    size = size
                )
                // Bottom-Left quadrant
                drawArc(
                    color = blueColor,
                    startAngle = 90f,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = androidx.compose.ui.geometry.Offset.Zero,
                    size = size
                )

                // Dividing white crosshair lines
                val whiteColor = Color.White
                val crossWidth = 2.dp.toPx()
                drawLine(
                    color = whiteColor,
                    start = androidx.compose.ui.geometry.Offset(0f, center.height),
                    end = androidx.compose.ui.geometry.Offset(size.width, center.height),
                    strokeWidth = crossWidth
                )
                drawLine(
                    color = whiteColor,
                    start = androidx.compose.ui.geometry.Offset(center.width, 0f),
                    end = androidx.compose.ui.geometry.Offset(center.width, size.height),
                    strokeWidth = crossWidth
                )
            }
        }
    }
}

@Composable
fun MapScaleBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "0",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(3.dp))
        Box(
            modifier = Modifier
                .width(30.dp)
                .height(3.5.dp)
                .background(Color.White)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = "75m",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(3.dp))
        Box(
            modifier = Modifier
                .width(30.dp)
                .height(3.5.dp)
                .background(Color.White)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = "150m",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun EmbeddedInteractiveMapCanvas(
    modifier: Modifier = Modifier,
    userLat: Double,
    userLng: Double,
    pins: List<MapPin> = emptyList(),
    selectedPinName: String? = null,
    isHomeSelectionMode: Boolean = false,
    isRunawayMode: Boolean = false,
    showCentralCircuitTarget: Boolean = true,
    showGpsTargetPill: Boolean = false,
    isEnglish: Boolean = true,
    isInitiallyLocked: Boolean = false,
    parentScrollState: ScrollState? = null,
    onPinSelected: (MapPin) -> Unit = {},
    onCenterChanged: (Double, Double) -> Unit = { _, _ -> },
    onTargetClick: () -> Unit = {},
    onLockToggle: (Boolean, Double, Double) -> Unit = { _, _, _ -> }
) {
    // Ensure Mapbox token is initialized synchronously before Mapbox components compose
    try {
        if (com.mapbox.common.MapboxOptions.accessToken.isEmpty() && com.analoganchor.app.BuildConfig.MAPBOX_ACCESS_TOKEN.isNotEmpty()) {
            com.mapbox.common.MapboxOptions.accessToken = com.analoganchor.app.BuildConfig.MAPBOX_ACCESS_TOKEN
        }
    } catch (e: Throwable) {
        android.util.Log.e("EmbeddedMap", "Mapbox token sync init: ${e.message}")
    }

    var isMapLocked by remember(isInitiallyLocked) { mutableStateOf(isInitiallyLocked) }
    var isMapInteractionModeEnabled by remember { mutableStateOf(!isHomeSelectionMode && !isInitiallyLocked) }
    val coroutineScope = rememberCoroutineScope()
    var autoRelockJob by remember { mutableStateOf<Job?>(null) }

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(userLng, userLat))
            zoom(16.5)
        }
    }

    fun scheduleAutoRelock() {
        autoRelockJob?.cancel()
        autoRelockJob = coroutineScope.launch {
            delay(15_000L)
            isMapLocked = true
            isMapInteractionModeEnabled = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            autoRelockJob?.cancel()
        }
    }

    LaunchedEffect(isInitiallyLocked) {
        if (!isInitiallyLocked) {
            scheduleAutoRelock()
        }
    }

    LaunchedEffect(Unit) {
        try {
            if (com.mapbox.common.MapboxOptions.accessToken.isEmpty() && com.analoganchor.app.BuildConfig.MAPBOX_ACCESS_TOKEN.isNotEmpty()) {
                com.mapbox.common.MapboxOptions.accessToken = com.analoganchor.app.BuildConfig.MAPBOX_ACCESS_TOKEN
            }
        } catch (e: Throwable) {
            android.util.Log.e("EmbeddedMap", "Mapbox token setup: ${e.message}")
        }
    }

    val mapNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Consume drag deltas while interacting with map so parent verticalScroll does not freeze map panning
                return if (isMapInteractionModeEnabled) available else Offset.Zero
            }
        }
    }
    

    val userLocationBitmap = remember {
        val size = 72
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            isAntiAlias = true
        }

        // Outer cyan ring
        paint.color = android.graphics.Color.parseColor("#00E5FF")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, paint)

        // Subtle cyan background inside
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.parseColor("#3300E5FF")
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 6f, paint)

        // White disc
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 4.2f, paint)

        // Blue 4-quadrant crosshair dot
        paint.color = android.graphics.Color.parseColor("#1E88E5")
        canvas.drawCircle(size / 2f, size / 2f, size / 5.2f, paint)

        // White cross lines
        paint.color = android.graphics.Color.WHITE
        paint.strokeWidth = 2.5f
        canvas.drawLine(size / 2f - size / 5.2f, size / 2f, size / 2f + size / 5.2f, size / 2f, paint)
        canvas.drawLine(size / 2f, size / 2f - size / 5.2f, size / 2f, size / 2f + size / 5.2f, paint)

        bitmap
    }

    val isUsableLocation = userLat != 0.0 && userLng != 0.0 && userLat != -999.0 && userLng != -999.0
    var hasCenteredOnUser by remember { mutableStateOf(false) }

    // Camera follow user initially when valid GPS coordinates arrive (NO re-centering on lock!)
    LaunchedEffect(userLat, userLng, isUsableLocation) {
        if (isUsableLocation && !hasCenteredOnUser) {
            hasCenteredOnUser = true
            mapViewportState.setCameraOptions {
                center(Point.fromLngLat(userLng, userLat))
                zoom(16.5)
            }
        }
    }

    // Camera follow selected pin if changed
    LaunchedEffect(selectedPinName) {
        val selectedPin = pins.find { it.name == selectedPinName }
        if (selectedPin != null) {
            mapViewportState.setCameraOptions {
                center(Point.fromLngLat(selectedPin.lng, selectedPin.lat))
                zoom(16.5)
            }
        }
    }

    // Notify parent of center changes (especially in crosshair/target mode)
    val cameraState = mapViewportState.cameraState
    LaunchedEffect(cameraState) {
        val center = cameraState?.center
        if (center != null) {
            onCenterChanged(center.latitude(), center.longitude())
        }
    }

    val triggerUnifiedGpsAction = {
        onTargetClick()
        if (isUsableLocation) {
            mapViewportState.setCameraOptions {
                center(Point.fromLngLat(userLng, userLat))
                zoom(16.5)
            }
        }
    }

    Box(
        modifier = modifier
            .nestedScroll(mapNestedScrollConnection)
            .background(ObsidianBg, RoundedCornerShape(14.dp))
            .border(1.2.dp, ObsidianBorder, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
    ) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState
        ) {
            MapStyle(Style.SATELLITE_STREETS)

            // 1. User Location Marker (Custom Radar Target Point)
            PointAnnotation(
                point = Point.fromLngLat(userLng, userLat),
                iconImageBitmap = userLocationBitmap,
                onClick = {
                    triggerUnifiedGpsAction()
                    true
                }
            )

            // 2. Mosque Pins
            if (!isHomeSelectionMode) {
                pins.forEach { pin ->
                    PointAnnotation(
                        point = Point.fromLngLat(pin.lng, pin.lat),
                        onClick = {
                            onPinSelected(pin)
                            true
                        }
                    )
                }
            }


        }

        // 3. Gesture Shield when LOCKED: allows vertical drag gestures to scroll parent and detects double-swipe to unlock
        if (isMapLocked) {
            var lastSwipeDownTime by remember { mutableLongStateOf(0L) }
            var swipeCount by remember { mutableIntStateOf(0) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(parentScrollState) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                val now = System.currentTimeMillis()
                                if (now - lastSwipeDownTime < 1500L) {
                                    swipeCount += 1
                                } else {
                                    swipeCount = 1
                                }
                                lastSwipeDownTime = now
                                if (swipeCount >= 2) {
                                    // 2 quick downward gestures: unlock map & allow scroll down
                                    isMapLocked = false
                                    isMapInteractionModeEnabled = true
                                    scheduleAutoRelock()
                                    swipeCount = 0
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                parentScrollState?.let { state ->
                                    coroutineScope.launch {
                                        state.scrollBy(-dragAmount)
                                    }
                                }
                            }
                        )
                    }
            )
        }

        // 3b. Left & Right Edge Scroll Gutters (56dp wide on each side)
        // Passes vertical swipes directly to parent scroll container even when map is in Pan mode
        val edgeGutterWidth = 56.dp
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(edgeGutterWidth)
                .fillMaxHeight()
                .pointerInput(parentScrollState) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        parentScrollState?.let { state ->
                            coroutineScope.launch {
                                state.scrollBy(-dragAmount)
                            }
                        }
                    }
                }
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(edgeGutterWidth)
                .fillMaxHeight()
                .pointerInput(parentScrollState) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        parentScrollState?.let { state ->
                            coroutineScope.launch {
                                state.scrollBy(-dragAmount)
                            }
                        }
                    }
                }
        )

        // 4. Top-Left Scale Bar Overlay (Reference Match)
        MapScaleBar(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        )

        // 5. Interactive Radar Central Circuit Target (in center of map canvas)
        if (showCentralCircuitTarget) {
            InteractiveGpsTargetCircuit(
                modifier = Modifier.align(Alignment.Center),
                onClick = triggerUnifiedGpsAction
            )
        }

        // 6. Map lock/zoom controls at top right
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(ObsidianBg.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Prominent Green Lock / Amber Unlock Button (Compact Normal Size)
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .background(
                        color = if (isMapLocked) GlowGreen else SignalAmber,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        val willLock = !isMapLocked
                        isMapLocked = willLock
                        isMapInteractionModeEnabled = !willLock
                        if (!willLock) {
                            scheduleAutoRelock()
                        } else {
                            autoRelockJob?.cancel()
                        }
                        val centerLat = cameraState?.center?.latitude() ?: userLat
                        val centerLng = cameraState?.center?.longitude() ?: userLng
                        onLockToggle(willLock, centerLat, centerLng)
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isMapLocked) {
                        if (isEnglish) "🔒 LOCKED" else "🔒 مقفل"
                    } else {
                        if (isEnglish) "🔓 PAN MAP" else "🔓 تحريك الخريطة"
                    },
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
            MapZoomButton(
                label = "+",
                onClick = {
                    val currentZoom = mapViewportState.cameraState?.zoom ?: 16.5
                    mapViewportState.setCameraOptions {
                        zoom(currentZoom + 0.5)
                    }
                }
            )
            MapZoomButton(
                label = "-",
                onClick = {
                    val currentZoom = mapViewportState.cameraState?.zoom ?: 16.5
                    mapViewportState.setCameraOptions {
                        zoom(currentZoom - 0.5)
                    }
                }
            )
        }

        // 7. Floating GPS Target Bottom Pill (only if explicitly enabled)
        if (showGpsTargetPill) {
            val centerLat = cameraState?.center?.latitude() ?: userLat
            val centerLng = cameraState?.center?.longitude() ?: userLng
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .background(ObsidianBg.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "GPS Target: %.5f, %.5f", centerLat, centerLng),
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun MapZoomButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(ObsidianSurface, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = GlowGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
