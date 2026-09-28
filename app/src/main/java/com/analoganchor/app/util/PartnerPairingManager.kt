package com.analoganchor.app.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.UUID

data class DiscoveredPartner(
    val id: String,
    val name: String,
    val rssi: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val remainingMinutes: Int = 60,
    val hasActiveSession: Boolean = false
)

object PartnerPairingManager {
    private const val TAG = "PartnerPairingManager"
    val SERVICE_UUID: UUID = UUID.fromString("0000AA01-0000-1000-8000-00805F9B34FB")
    const val OFFLINE_CHALLENGE_PACKAGE = "com.analoganchor.offlinechallenge"

    private val _discoveredPartners = MutableStateFlow<List<DiscoveredPartner>>(emptyList())
    val discoveredPartners = _discoveredPartners.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising = _isAdvertising.asStateFlow()

    private val _pairedPartner = MutableStateFlow<DiscoveredPartner?>(null)
    val pairedPartner = _pairedPartner.asStateFlow()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null

    fun init(context: Context) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = manager?.adapter
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
    }

    fun hasBluetoothPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun getCleanDeviceName(context: Context): String {
        val raw = try {
            bluetoothAdapter?.name?.takeIf { it.isNotBlank() && it != "Android" }
        } catch (_: SecurityException) {
            null
        } ?: try {
            android.provider.Settings.Global.getString(context.contentResolver, "device_name")
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } ?: run {
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val model = Build.MODEL
            if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        }

        // Hard cap: 20 bytes UTF-8 to stay safely within 31-byte scan response limit on all chipsets
        val bytes = raw.toByteArray(StandardCharsets.UTF_8)
        return if (bytes.size <= 20) raw
        else String(bytes.copyOf(20), StandardCharsets.UTF_8).trimEnd()
    }

    fun startAdvertising(context: Context) {
        if (!isBluetoothEnabled()) return
        if (bleAdvertiser == null) {
            bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: BluetoothAdapter.getDefaultAdapter()?.bluetoothLeAdvertiser
        }
        if (bleAdvertiser == null) return

        try {
            val deviceName = getCleanDeviceName(context)
            val pUuid = ParcelUuid(SERVICE_UUID)

            // Check if there is an active/pending session to broadcast remaining sync time
            val activeSession = com.analoganchor.app.data.preferences.PartnerSessionPrefs.getActiveSession(context)
            val hasSession = activeSession != null && !activeSession.isExpired()
            val remMinutes = if (hasSession) {
                val remSec = activeSession!!.remainingSeconds()
                ((remSec + 59) / 60).toInt().coerceIn(1, 60)
            } else {
                60
            }

            // Encode payload: [status byte: 0x01 = active session, 0x00 = idle] + [remainingMinutes byte] + [name bytes (up to 11 bytes)]
            val rawNameBytes = deviceName.toByteArray(StandardCharsets.UTF_8)
            val cappedNameBytes = if (rawNameBytes.size > 11) rawNameBytes.copyOf(11) else rawNameBytes
            val payload = ByteArray(2 + cappedNameBytes.size)
            payload[0] = if (hasSession) 0x01.toByte() else 0x00.toByte()
            payload[1] = remMinutes.toByte()
            System.arraycopy(cappedNameBytes, 0, payload, 2, cappedNameBytes.size)

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .setTimeout(0) // Run until paired or sheet dismissed
                .build()

            // Primary Advertisement: Flags (3 bytes) + 128-bit UUID (18 bytes) = 21 bytes (well within 31-byte limit)
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(pUuid)
                .build()

            // Scan Response: Service Data: 16 bytes UUID + 2 bytes header + (2 + 11 = 13 bytes payload) = 31 bytes max
            val scanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceData(pUuid, payload)
                .build()

            bleAdvertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
            _isAdvertising.value = true
            Log.d(TAG, "Started BLE advertising with device name: $deviceName (activeSession=$hasSession, remMin=$remMinutes)")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting BLE advertising: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE advertising: ${e.message}")
        }
    }

    private var graceAdvertisingJob: Job? = null

    fun keepAdvertisingGracePeriod(context: Context, durationMs: Long = 300_000L) {
        startAdvertising(context)
        graceAdvertisingJob?.cancel()
        graceAdvertisingJob = CoroutineScope(Dispatchers.Default).launch {
            delay(durationMs)
            stopAdvertising()
        }
    }

    fun stopAdvertising() {
        graceAdvertisingJob?.cancel()
        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            // Ignore
        }
        _isAdvertising.value = false
    }

    fun startScanning() {
        if (!isBluetoothEnabled()) return
        if (bleScanner == null) {
            bleScanner = bluetoothAdapter?.bluetoothLeScanner ?: BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner
        }
        if (bleScanner == null) return

        try {
            _discoveredPartners.value = emptyList()
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            bleScanner?.startScan(listOf(filter), settings, scanCallback)
            _isScanning.value = true
            Log.d(TAG, "Started BLE scan for anchor partners")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting BLE scan: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan: ${e.message}")
        }
    }

    fun pruneStaleDevices() {
        val now = System.currentTimeMillis()
        _discoveredPartners.value = _discoveredPartners.value.filter { now - it.timestamp < 18_000L }
    }

    private var refreshJob: Job? = null

    fun refreshScan(context: Context? = null) {
        refreshJob?.cancel()
        refreshJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                if (context != null) {
                    init(context)
                } else if (bleScanner == null || bluetoothAdapter == null) {
                    bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    bleScanner = bluetoothAdapter?.bluetoothLeScanner
                    bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
                }

                if (!isBluetoothEnabled()) {
                    _isScanning.value = false
                    return@launch
                }

                // 1. Clear out stale entries so radar visibly begins fresh sweep
                _discoveredPartners.value = emptyList()

                // 2. Stop ongoing scan & advertising cleanly
                stopScanning()
                if (context != null) {
                    stopAdvertising()
                }

                // 3. Essential 300ms HAL buffer: Android Bluetooth stack needs ~250ms to release HW scan filter
                delay(300L)

                // 4. Restart advertising so peer devices rediscover us
                if (context != null) {
                    startAdvertising(context)
                }

                // 5. Restart scanning fresh
                startScanning()
                Log.d(TAG, "BLE Pairing successfully refreshed: scanner and advertiser restarted")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing BLE scan: ${e.message}", e)
            }
        }
    }

    fun stopScanning() {
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            // Ignore
        }
        _isScanning.value = false
    }

    fun confirmPairing(partner: DiscoveredPartner) {
        _pairedPartner.value = partner
        stopAdvertising()
        stopScanning()
    }

    fun resetPairing() {
        _pairedPartner.value = null
        _discoveredPartners.value = emptyList()
        stopAdvertising()
        stopScanning()
    }

    // QR Fallback Generators
    fun generateQrPayload(userId: String, userName: String): String {
        return "anchor://pair?v=1&uid=$userId&name=${Uri.encode(userName)}&t=${System.currentTimeMillis()}"
    }

    fun parseQrPayload(raw: String): DiscoveredPartner? {
        if (!raw.startsWith("anchor://pair")) return null
        return try {
            val uri = Uri.parse(raw)
            val uid = uri.getQueryParameter("uid") ?: UUID.randomUUID().toString()
            val name = uri.getQueryParameter("name") ?: "Anchor Partner"
            DiscoveredPartner(id = uid, name = name, rssi = -30)
        } catch (e: Exception) {
            null
        }
    }

    // App Interoperability Helpers
    fun isOfflineChallengeInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    OFFLINE_CHALLENGE_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(OFFLINE_CHALLENGE_PACKAGE, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun launchOfflineChallenge(
        context: Context,
        partnerName: String,
        sanctuaryName: String,
        durationMinutes: Int = 60,
        sessionId: String = UUID.randomUUID().toString(),
        isGroup: Boolean = true
    ): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(OFFLINE_CHALLENGE_PACKAGE)
            val intent = (launchIntent ?: Intent()).apply {
                if (launchIntent == null) {
                    setClassName(OFFLINE_CHALLENGE_PACKAGE, "$OFFLINE_CHALLENGE_PACKAGE.MainActivity")
                }
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                putExtra("EXTRA_LAUNCH_SOURCE", "ANALOG_ANCHOR")
                putExtra("EXTRA_CHALLENGE_TYPE", "PARTNER_OUTDOOR")
                putExtra("EXTRA_TARGET_ROUTE", "shield_permission")
                putExtra("EXTRA_PARTNER_NAME", partnerName)
                putExtra("EXTRA_SANCTUARY_NAME", sanctuaryName)
                putExtra("EXTRA_SESSION_ID", sessionId)
                putExtra("EXTRA_DURATION_MINUTES", durationMinutes)
                putExtra("EXTRA_START_TIME", System.currentTimeMillis())
                putExtra("EXTRA_IS_GROUP", isGroup)
                putExtra("EXTRA_POINTS", if (isGroup) 70 else 40)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            resetPairing()
            keepAdvertisingGracePeriod(context, 300_000L)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error launching offline challenge app: ${e.message}", e)
            false
        }
    }

    fun getOfflineChallengeInstallIntent(): Intent {
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$OFFLINE_CHALLENGE_PACKAGE")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "BLE Advertise success")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE Advertise failed with error code: $errorCode")
            _isAdvertising.value = false
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val serviceData = result.scanRecord?.getServiceData(ParcelUuid(SERVICE_UUID))
            var hasActive = false
            var remMin = 60
            val partnerName: String

            if (serviceData != null && serviceData.isNotEmpty()) {
                if (serviceData.size >= 2 && (serviceData[0] == 0x01.toByte() || serviceData[0] == 0x00.toByte())) {
                    hasActive = serviceData[0] == 0x01.toByte()
                    remMin = serviceData[1].toInt().coerceIn(1, 60)
                    val rawName = String(serviceData, 2, serviceData.size - 2, StandardCharsets.UTF_8).trim()
                    partnerName = if (rawName.isNotBlank()) rawName else (result.scanRecord?.deviceName ?: result.device.name ?: "Nearby Partner")
                } else {
                    partnerName = String(serviceData, StandardCharsets.UTF_8).trim()
                }
            } else {
                partnerName = result.scanRecord?.deviceName ?: result.device.name ?: "Nearby Partner"
            }

            // Composite key: Hash of service data / name to survive Android 12+ MAC rotation
            val partnerId = if (serviceData != null && serviceData.isNotEmpty()) {
                "dev_${serviceData.contentHashCode()}"
            } else {
                result.device.address
            }

            val partner = DiscoveredPartner(
                id = partnerId,
                name = partnerName,
                rssi = result.rssi,
                remainingMinutes = remMin,
                hasActiveSession = hasActive
            )

            val current = _discoveredPartners.value.toMutableList()
            val existingIndex = current.indexOfFirst { it.id == partner.id }
            if (existingIndex >= 0) {
                current[existingIndex] = partner
            } else {
                current.add(partner)
            }
            _discoveredPartners.value = current
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed with error code: $errorCode")
            if (errorCode == ScanCallback.SCAN_FAILED_ALREADY_STARTED) {
                _isScanning.value = true
            } else {
                _isScanning.value = false
            }
        }
    }
}
