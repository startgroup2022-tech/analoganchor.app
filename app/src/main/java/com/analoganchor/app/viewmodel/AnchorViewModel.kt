package com.analoganchor.app.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.analoganchor.app.MainActivity
import com.analoganchor.app.data.AppDatabase
import com.analoganchor.app.data.ProofMission
import com.analoganchor.app.data.RecoveryRecord
import com.analoganchor.app.data.RuleFlag
import com.analoganchor.app.data.SessionLog
import com.analoganchor.app.data.SessionLogRepository
import com.analoganchor.app.data.PrayerTimesCalculator
import com.analoganchor.app.data.UnlockSession
import com.analoganchor.app.service.AnalogAnchorDeviceAdminReceiver
import com.analoganchor.app.service.PrayerDndRestoreReceiver
import com.analoganchor.app.service.PrayerBufferScheduler
import com.analoganchor.app.util.PendingAutofillPinStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*

data class NearbyMosqueCandidate(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val distanceMeters: Int
)

data class MapboxPlaceCandidate(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double
)

data class FounderSanctuary(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val type: String
)

class AnchorViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {
    companion object {
        @Volatile
        var instance: AnchorViewModel? = null
            private set

        const val RETURN_PROOF_WINDOW_MS = 20 * 60 * 1000L

        // ─── TEST MODE ──────────────────────────────────────────────────────────
        // Set true during development to use a 10-second cooldown instead of 25 min.
        // IMPORTANT: change to false before deploying to the client phone!
        const val TEST_MODE_COOLDOWN = true
        // Set true during development so Anchor tab focus sessions run for 15 seconds.
        // IMPORTANT: change to false before deploying to the client phone!
        const val ANCHOR_TAP_ONE_TEST_MODE = true
        const val ANCHOR_TAP_ONE_TEST_DURATION_SEC = 15
        const val FOCUS_PICKUP_LIMIT = 3
        val COOLDOWN_DURATION_SEC = if (TEST_MODE_COOLDOWN) 10 else 1500
        private const val TOKEN_VALIDITY_MS = 60 * 60 * 1000L
        private const val ACTIVE_UNLOCK_DURATION_SEC = 15
        private const val GRACE_DURATION_SEC = 25
        private const val AUTOFILL_ARM_TTL_MS = 90_000L
        private const val RE_ENTRY_PIN_CUSTODY_MS = 75 * 60 * 1000L
        private const val APP_PIN_AUTO_LOCK_MS = 60_000L
        private const val FAJR_ALARM_ACTION = "com.analoganchor.app.action.FAJR_ALARM"
        private const val FAJR_5_HOUR_WARNING_ACTION = "com.analoganchor.app.action.FAJR_5HOUR_WARNING"
        private const val SERIAL_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        private const val LOCATION_UNSET = 0.0
        private const val LEGACY_DEFAULT_LAT = 26.2285
        private const val LEGACY_DEFAULT_LNG = 50.5860

        const val MAPBOX_DEFAULT_TOKEN = ""

        val FOUNDER_MOSQUES = listOf(
            FounderSanctuary("mosque_1", "Al Fateh Grand Mosque", "Manama, Bahrain", 26.2190, 50.5980, "mosque"),
            FounderSanctuary("mosque_2", "Imam Al-Kadhim Mosque", "Mahooz / Zinj, Bahrain", 26.20859, 50.58328, "mosque"),
            FounderSanctuary("mosque_3", "Busaiteen Grand Mosque", "Busaiteen, Bahrain", 26.2285, 50.5860, "mosque")
        )

        val FOUNDER_OUTDOOR_SPOTS = listOf(
            FounderSanctuary("outdoor_1", "Zinj / Mahooz Activity Hub", "Burger Land / Kuwait Ave area", 26.21318, 50.58615, "outdoor"),
            FounderSanctuary("outdoor_2", "Busaiteen Seafront Corniche", "Busaiteen Promenade", 26.2420, 50.5980, "outdoor"),
            FounderSanctuary("outdoor_3", "Manama Central Walkway", "Capital Green Walkway", 26.2250, 50.5750, "outdoor")
        )
        private const val MOSQUE_LOOKUP_RADIUS_METERS = 6_000
        private const val MOSQUE_LOOKUP_REFRESH_DISTANCE_METERS = 700.0
        private const val MOSQUE_LOOKUP_REFRESH_INTERVAL_MS = 5 * 60 * 1000L
        private const val FACE_DOWN_Z_THRESHOLD = -7.0f
        private const val LIFTED_Z_THRESHOLD = -3.0f
        private const val LIFTED_TILT_THRESHOLD = 6.0f
        private const val PICKUP_DEBOUNCE_MS = 1_500L
        const val FAJR_WAKE_STEP_GOAL = 2
        private const val EMERGENCY_UNLOCK_CHANNEL_ID = "emergency_unlock_channel"
        private const val TEMP_UNLOCK_REMINDER_30_ID = 3101
        private const val TEMP_UNLOCK_REMINDER_15_ID = 3102
        private const val TEMP_UNLOCK_EXPIRED_ID = 3103
        private const val FAJR_WAKE_MOVEMENT_ID = 2104
        private const val FAJR_WAKE_MOVEMENT_DONE_ID = 2105
        // ────────────────────────────────────────────────────────────────────────
    }

    private val context = application.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
    private val preferencesRepository = (application as com.analoganchor.app.AnalogAnchorApplication).preferencesRepository
    var isEnglish: Boolean
        get() = prefs.getBoolean("is_english", true)
        set(value) {
            prefs.edit().putBoolean("is_english", value).apply()
        }
    private val database = AppDatabase.getDatabase(context)
    private val repository = SessionLogRepository(database.sessionLogDao())
    private val mosqueLookupClient = OkHttpClient()
    private var lastMosqueFetchLat: Double? = null
    private var lastMosqueFetchLng: Double? = null
    private var lastMosqueFetchMs: Long = 0L
    // Bug #9 fix: todayDate is now a function so it stays accurate past midnight
    private fun todayDate() = formatDateKey(System.currentTimeMillis())
    private val currentWeekStartMillis = calculateWeekStartMillis()
    
    // Room integration
    val sessionLogs: StateFlow<List<SessionLog>>
    val whiteboardSessions: StateFlow<List<SessionLog>>
    val todayProofMission: StateFlow<ProofMission?>
    val requestsToday: StateFlow<Int>
    val requestsThisWeek: StateFlow<Int>
    val pendingReturnSession: StateFlow<UnlockSession?>
    val latestBreachSession: StateFlow<UnlockSession?>
    val openRecoveryRecord: StateFlow<RecoveryRecord?>
    val activeRuleFlagCount: StateFlow<Int>
    val activeRuleFlags: StateFlow<List<RuleFlag>>

    enum class LedgerTransactionType { EARN, PENALTY }

    private val _reservePoints = MutableStateFlow(100)
    val reservePoints = _reservePoints.asStateFlow()

    private val _lastPenaltyTimestamp = MutableStateFlow(0L)
    val lastPenaltyTimestamp = _lastPenaltyTimestamp.asStateFlow()

    private val _lastLedgerTransactionTimestamp = MutableStateFlow(0L)
    val lastLedgerTransactionTimestamp = _lastLedgerTransactionTimestamp.asStateFlow()

    private val _lastLedgerTransactionType = MutableStateFlow<LedgerTransactionType?>(null)
    val lastLedgerTransactionType = _lastLedgerTransactionType.asStateFlow()

    private val _recentTransactionType = MutableStateFlow<LedgerTransactionType?>(null)
    val recentTransactionType = _recentTransactionType.asStateFlow()

    private val _hasRecentPenalty = MutableStateFlow(false)
    val hasRecentPenalty = _hasRecentPenalty.asStateFlow()

    fun updateRecentPenaltyStatus() {
        updateRecentLedgerTransactionStatus()
    }

    fun updateRecentLedgerTransactionStatus() {
        val ts = prefs.getLong("last_ledger_transaction_timestamp", 0L)
        val typeStr = prefs.getString("last_ledger_transaction_type", null)
        val elapsed = System.currentTimeMillis() - ts
        if (ts > 0L && elapsed < 30 * 60 * 1000L && typeStr != null) {
            val type = runCatching { LedgerTransactionType.valueOf(typeStr) }.getOrNull()
            _recentTransactionType.value = type
            _hasRecentPenalty.value = (type == LedgerTransactionType.PENALTY)
        } else {
            val legacyPenaltyTs = prefs.getLong("last_penalty_timestamp", 0L)
            val legacyElapsed = System.currentTimeMillis() - legacyPenaltyTs
            if (legacyPenaltyTs > 0L && legacyElapsed < 30 * 60 * 1000L) {
                _recentTransactionType.value = LedgerTransactionType.PENALTY
                _hasRecentPenalty.value = true
            } else {
                _recentTransactionType.value = null
                _hasRecentPenalty.value = false
            }
        }
    }

    private val _isLocked = MutableStateFlow(false)
    val isLocked = _isLocked.asStateFlow()

    private val _isSealed = MutableStateFlow(true)
    val isSealed = _isSealed.asStateFlow()

    private val _isOnboarded = MutableStateFlow(false)
    val isOnboarded = _isOnboarded.asStateFlow()

    private val _isLocationPermissionGranted = MutableStateFlow(false)
    val isLocationPermissionGranted = _isLocationPermissionGranted.asStateFlow()

    private val _isLocationServiceEnabled = MutableStateFlow(false)
    val isLocationServiceEnabled = _isLocationServiceEnabled.asStateFlow()

    private val _appPinEnabled = MutableStateFlow(false)
    val appPinEnabled = _appPinEnabled.asStateFlow()

    private val _appPinSet = MutableStateFlow(false)
    val appPinSet = _appPinSet.asStateFlow()

    private val _appThemeMode = MutableStateFlow("DARK")
    val appThemeMode = _appThemeMode.asStateFlow()

    private val _appPinUnlocked = MutableStateFlow(!context.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE).getBoolean("app_pin_enabled", false))
    val appPinUnlocked = _appPinUnlocked.asStateFlow()

    private val _isFajrAlarmRinging = MutableStateFlow(false)
    val isFajrAlarmRinging = _isFajrAlarmRinging.asStateFlow()

    private val _isJumuahAlarmRinging = MutableStateFlow(false)
    val isJumuahAlarmRinging = _isJumuahAlarmRinging.asStateFlow()

    private val _isSessionEndAlarmRinging = MutableStateFlow(false)
    val isSessionEndAlarmRinging = _isSessionEndAlarmRinging.asStateFlow()

    private val _isTadabburAlarmRinging = MutableStateFlow(false)
    val isTadabburAlarmRinging = _isTadabburAlarmRinging.asStateFlow()

    private val _tadabburCommitmentActive = MutableStateFlow(false)
    val tadabburCommitmentActive = _tadabburCommitmentActive.asStateFlow()

    private val _tadabburCommitmentDays = MutableStateFlow(3)
    val tadabburCommitmentDays = _tadabburCommitmentDays.asStateFlow()

    private val _tadabburCommitmentRemainingDays = MutableStateFlow(3)
    val tadabburCommitmentRemainingDays = _tadabburCommitmentRemainingDays.asStateFlow()

    private val _isFajrAlarmMissed = MutableStateFlow(false)
    val isFajrAlarmMissed = _isFajrAlarmMissed.asStateFlow()

    private val _isJumuahAlarmMissed = MutableStateFlow(false)
    val isJumuahAlarmMissed = _isJumuahAlarmMissed.asStateFlow()

    private val _showJumuahBoundConfirmationDialog = MutableStateFlow(false)
    val showJumuahBoundConfirmationDialog = _showJumuahBoundConfirmationDialog.asStateFlow()

    fun dismissJumuahBoundConfirmationDialog() {
        _showJumuahBoundConfirmationDialog.value = false
    }

    private val _jumuahCountdownText = MutableStateFlow<String?>(null)
    val jumuahCountdownText = _jumuahCountdownText.asStateFlow()

    private val _sleepPressureStatus = MutableStateFlow("NORMAL")
    val sleepPressureStatus = _sleepPressureStatus.asStateFlow()

    private val _sleepPressureTimeLeftMs = MutableStateFlow(0L)
    val sleepPressureTimeLeftMs = _sleepPressureTimeLeftMs.asStateFlow()

    private val _fajrAlarmAyahConstraint = MutableStateFlow(false)
    val fajrAlarmAyahConstraint = _fajrAlarmAyahConstraint.asStateFlow()

    private val _fajrAlarmAccountabilityPinEnabled = MutableStateFlow(false)
    val fajrAlarmAccountabilityPinEnabled = _fajrAlarmAccountabilityPinEnabled.asStateFlow()

    private val _fajrAccountabilityPinSet = MutableStateFlow(false)
    val fajrAccountabilityPinSet = _fajrAccountabilityPinSet.asStateFlow()

    private val _fajrAlarmRingtoneUri = MutableStateFlow<String?>(null)
    val fajrAlarmRingtoneUri = _fajrAlarmRingtoneUri.asStateFlow()

    private val _fajrAlarmRingtoneLabel = MutableStateFlow("Built-in Analog tone")
    val fajrAlarmRingtoneLabel = _fajrAlarmRingtoneLabel.asStateFlow()

    private val _sanctuaryType = MutableStateFlow("mosque")
    val sanctuaryType = _sanctuaryType.asStateFlow()

    private val _mosqueName = MutableStateFlow("Designated Mosque")
    val mosqueName = _mosqueName.asStateFlow()

    private val _mosqueAddress = MutableStateFlow("Pin a mosque from the live GPS map")
    val mosqueAddress = _mosqueAddress.asStateFlow()

    private val _sanctuaryLatitude = MutableStateFlow(LOCATION_UNSET)
    val sanctuaryLatitude = _sanctuaryLatitude.asStateFlow()

    private val _sanctuaryLongitude = MutableStateFlow(LOCATION_UNSET)
    val sanctuaryLongitude = _sanctuaryLongitude.asStateFlow()

    private val _outdoorSanctuaryName = MutableStateFlow("Favorite Outdoor Sanctuary")
    val outdoorSanctuaryName = _outdoorSanctuaryName.asStateFlow()

    private val _outdoorSanctuaryAddress = MutableStateFlow("Pinned outdoor sanctuary")
    val outdoorSanctuaryAddress = _outdoorSanctuaryAddress.asStateFlow()

    private val _outdoorSanctuaryLat = MutableStateFlow(LOCATION_UNSET)
    val outdoorSanctuaryLat = _outdoorSanctuaryLat.asStateFlow()

    private val _outdoorSanctuaryLng = MutableStateFlow(LOCATION_UNSET)
    val outdoorSanctuaryLng = _outdoorSanctuaryLng.asStateFlow()

    private val _isOutsideHomeBase = MutableStateFlow(false)
    val isOutsideHomeBase = _isOutsideHomeBase.asStateFlow()

    private val _isAtOutdoorSanctuary = MutableStateFlow(false)
    val isAtOutdoorSanctuary = _isAtOutdoorSanctuary.asStateFlow()

    private val _outdoorSoloCountThisWeek = MutableStateFlow(0)
    val outdoorSoloCountThisWeek = _outdoorSoloCountThisWeek.asStateFlow()

    private val _outdoorPartnerCountThisWeek = MutableStateFlow(0)
    val outdoorPartnerCountThisWeek = _outdoorPartnerCountThisWeek.asStateFlow()

    private val _userName = MutableStateFlow(prefs.getString("user_name", "Client") ?: "Client")
    val userName = _userName.asStateFlow()

    private val _userGender = MutableStateFlow(prefs.getString("user_gender", "male") ?: "male")
    val userGender = _userGender.asStateFlow()

    private val _isFemaleUserFlow = MutableStateFlow(_userGender.value.equals("female", ignoreCase = true))
    val isFemaleUserFlow = _isFemaleUserFlow.asStateFlow()

    val isFemaleUser: Boolean
        get() = _userGender.value.equals("female", ignoreCase = true)

    private val _isSoloRoutineActive = MutableStateFlow(false)
    val isSoloRoutineActive = _isSoloRoutineActive.asStateFlow()

    private val _userLocationName = MutableStateFlow("GPS pending")
    val userLocationName = _userLocationName.asStateFlow()

    private val _fajrAlarmEnabled = MutableStateFlow(true)
    val fajrAlarmEnabled = _fajrAlarmEnabled.asStateFlow()

    private val _fajrAlarmNfcLocked = MutableStateFlow(false)
    val fajrAlarmNfcLocked = _fajrAlarmNfcLocked.asStateFlow()

    private val _fajrAlarmOffsetMinutes = MutableStateFlow(0)
    val fajrAlarmOffsetMinutes = _fajrAlarmOffsetMinutes.asStateFlow()

    private val _nfcTagId = MutableStateFlow("")
    val nfcTagId = _nfcTagId.asStateFlow()

    private val _fajrNfcTagId = MutableStateFlow("")
    val fajrNfcTagId = _fajrNfcTagId.asStateFlow()

    private val _activeNfcRegistrationSlot = MutableStateFlow<String?>(if (!_isOnboarded.value) "anchor" else null)
    val activeNfcRegistrationSlot = _activeNfcRegistrationSlot.asStateFlow()

    fun setActiveNfcRegistrationSlot(slot: String?) {
        _activeNfcRegistrationSlot.value = slot
    }

    private var isAppInForeground = false

    fun setAppForegroundState(inForeground: Boolean) {
        isAppInForeground = inForeground
    }

    private val _nfcScannedEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val nfcScannedEvent = _nfcScannedEvent.asSharedFlow()

    // Fires when the Fajr Eraser NFC tag is tapped — even from a closed app.
    // Uses a buffered channel to guarantee delivery even during cold launches.
    private val _fajrEraserNfcChannel = Channel<Unit>(Channel.BUFFERED)
    val fajrEraserNfcEvent = _fajrEraserNfcChannel.receiveAsFlow()

    // Fires to navigate to a specific tab (e.g., Mosque or Day Guard when tags are tapped)
    private val _navigateToTabChannel = Channel<String>(Channel.BUFFERED)
    val navigateToTabEvent = _navigateToTabChannel.receiveAsFlow()

    data class PrayerBufferPromptState(
        val prayerKey: String,
        val prayerNameEn: String,
        val prayerNameAr: String,
        val restoreTimeMs: Long
    )

    private val _prayerBufferPrompt = MutableStateFlow<PrayerBufferPromptState?>(null)
    val prayerBufferPrompt = _prayerBufferPrompt.asStateFlow()

    private val _currentActiveTab = MutableStateFlow("MOSQUE")
    val currentActiveTab = _currentActiveTab.asStateFlow()

    fun setActiveTab(tab: String) {
        _currentActiveTab.value = tab
    }

    // Whether Fajr alarm repeats daily or is one-shot (default = one-shot for new lockdowns)
    private val _fajrAlarmDays = MutableStateFlow<Set<Int>>(emptySet()) // empty = one-shot
    val fajrAlarmDays = _fajrAlarmDays.asStateFlow()

    private val _eveningProtocolTime = MutableStateFlow("22:00")
    val eveningProtocolTime = _eveningProtocolTime.asStateFlow()

    private val _isEveningAnchorCompletedToday = MutableStateFlow(false)
    val isEveningAnchorCompletedToday = _isEveningAnchorCompletedToday.asStateFlow()

    private val _daily25mCount = MutableStateFlow(0)
    val daily25mCount = _daily25mCount.asStateFlow()

    private val _hasLongSessionToday = MutableStateFlow(false)
    val hasLongSessionToday = _hasLongSessionToday.asStateFlow()

    fun isDurationAvailable(duration: Int): Boolean {
        if (_hasLongSessionToday.value) return false
        return if (duration == 5) {
            _daily25mCount.value < 2
        } else {
            _daily25mCount.value == 0
        }
    }

    // Real GPS Coordinates State
    private val _userLatitude = MutableStateFlow(LOCATION_UNSET)
    val userLatitude = _userLatitude.asStateFlow()

    private val _userLongitude = MutableStateFlow(LOCATION_UNSET)
    val userLongitude = _userLongitude.asStateFlow()

    private val _nearbyMosques = MutableStateFlow<List<NearbyMosqueCandidate>>(emptyList())
    val nearbyMosques = _nearbyMosques.asStateFlow()

    private val _mosqueSearchStatus = MutableStateFlow("Sync GPS to find nearby mosques")
    val mosqueSearchStatus = _mosqueSearchStatus.asStateFlow()

    // Mapbox Outdoor Destination Search
    private val _outdoorSearchResults = MutableStateFlow<List<MapboxPlaceCandidate>>(emptyList())
    val outdoorSearchResults = _outdoorSearchResults.asStateFlow()

    private val _isSearchingOutdoor = MutableStateFlow(false)
    val isSearchingOutdoor = _isSearchingOutdoor.asStateFlow()

    private val _lockedMosques = MutableStateFlow<List<FounderSanctuary>>(emptyList())
    val lockedMosques = _lockedMosques.asStateFlow()

    private val _lockedOutdoorSpots = MutableStateFlow<List<FounderSanctuary>>(emptyList())
    val lockedOutdoorSpots = _lockedOutdoorSpots.asStateFlow()

    private var outdoorSearchJob: Job? = null

    // Astronomical dynamic prayer timers Flow
    private val _nextPrayerCountdown = MutableStateFlow("04:52:10")
    val nextPrayerCountdown = _nextPrayerCountdown.asStateFlow()

    private val _nextPrayerName = MutableStateFlow("AL-FAJR (الفجر)")
    val nextPrayerName = _nextPrayerName.asStateFlow()

    private val _prayerTimesMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val prayerTimesMap = _prayerTimesMap.asStateFlow()

    private val _isFriday = MutableStateFlow(false)
    val isFriday = _isFriday.asStateFlow()

    // Step Sensor states
    private val _stepsCount = MutableStateFlow(0)
    val stepsCount = _stepsCount.asStateFlow()

    // Behavior learn variables (streaks and compliance)
    private val _mosqueComplianceStreak = MutableStateFlow(0)
    val mosqueComplianceStreak = _mosqueComplianceStreak.asStateFlow()

    private val _runningConsistencyScore = MutableStateFlow(80)
    val runningConsistencyScore = _runningConsistencyScore.asStateFlow()

    private val _protocolComplianceStreak = MutableStateFlow(0)
    val protocolComplianceStreak = _protocolComplianceStreak.asStateFlow()

    private val _weeklyMosqueCheckInCount = MutableStateFlow(0)
    val weeklyMosqueCheckInCount = _weeklyMosqueCheckInCount.asStateFlow()

    private val _maghribRetreatStreak = MutableStateFlow(0)
    val maghribRetreatStreak = _maghribRetreatStreak.asStateFlow()

    private val _habitScore = MutableStateFlow(85)
    val habitScore = _habitScore.asStateFlow()

    // 40-Day Takbir Covenant (عهد الـ 40 يوماً)
    private val _covenantDayNumber = MutableStateFlow(1)
    val covenantDayNumber = _covenantDayNumber.asStateFlow()

    private val _covenantScore = MutableStateFlow(0)
    val covenantScore = _covenantScore.asStateFlow()

    private val _covenantPercentage = MutableStateFlow(0)
    val covenantPercentage = _covenantPercentage.asStateFlow()

    private val _covenantBonusClaimed = MutableStateFlow(false)
    val covenantBonusClaimed = _covenantBonusClaimed.asStateFlow()

    // 90-Day Commitment Stake (6,000 PTS Scale)
    private val _stakeWeek = MutableStateFlow(1)
    val stakeWeek = _stakeWeek.asStateFlow()

    private val _cumulativeStakePercentage = MutableStateFlow(1.2f)
    val cumulativeStakePercentage = _cumulativeStakePercentage.asStateFlow()

    // Today's 5 Prayers Status (Fajr, Dhuhr, Asr, Maghrib, Isha)
    private val _todayPrayersCompleted = MutableStateFlow(listOf(false, false, false, false, false))
    val todayPrayersCompleted = _todayPrayersCompleted.asStateFlow()

    private val _anchorStreak = MutableStateFlow(0)
    val anchorStreak = _anchorStreak.asStateFlow()

    // Prayer Intentionality Buffer (Adhan-to-Iqamah & DND Covenant)
    private val _prayerBufferActive = MutableStateFlow(false)
    val prayerBufferActive = _prayerBufferActive.asStateFlow()

    private val _prayerBufferPrayerKey = MutableStateFlow("")
    val prayerBufferPrayerKey = _prayerBufferPrayerKey.asStateFlow()

    private val _prayerBufferPrayerNameEn = MutableStateFlow("")
    val prayerBufferPrayerNameEn = _prayerBufferPrayerNameEn.asStateFlow()

    private val _prayerBufferPrayerNameAr = MutableStateFlow("")
    val prayerBufferPrayerNameAr = _prayerBufferPrayerNameAr.asStateFlow()

    private val _prayerBufferStartTimeMs = MutableStateFlow(0L)
    val prayerBufferStartTimeMs = _prayerBufferStartTimeMs.asStateFlow()

    private val _prayerBufferEndTimeMs = MutableStateFlow(0L)
    val prayerBufferEndTimeMs = _prayerBufferEndTimeMs.asStateFlow()

    // Hardware status logs
    private val _deviceAdminActive = MutableStateFlow(false)
    val deviceAdminActive = _deviceAdminActive.asStateFlow()

    private val _accessibilityActive = MutableStateFlow(false)
    val accessibilityActive = _accessibilityActive.asStateFlow()

    private val _uninstallPinSet = MutableStateFlow(false)
    val uninstallPinSet = _uninstallPinSet.asStateFlow()

    private val _fajrVerified = MutableStateFlow(false)
    val fajrVerified = _fajrVerified.asStateFlow()

    private val _fajrWakeVerified = MutableStateFlow(false)
    val fajrWakeVerified = _fajrWakeVerified.asStateFlow()

    private val _fajrWakeStepVerificationActive = MutableStateFlow(false)
    val fajrWakeStepVerificationActive = _fajrWakeStepVerificationActive.asStateFlow()

    private val _fajrWakeStepsAfterDismiss = MutableStateFlow(0)
    val fajrWakeStepsAfterDismiss = _fajrWakeStepsAfterDismiss.asStateFlow()

    private val _maghribVerified = MutableStateFlow(false)
    val maghribVerified = _maghribVerified.asStateFlow()

    private val _showDecoyShortcutScreen = MutableStateFlow(false)
    val showDecoyShortcutScreen = _showDecoyShortcutScreen.asStateFlow()

    fun setShowDecoyShortcutScreen(show: Boolean) {
        _showDecoyShortcutScreen.value = show
    }

    private val _showPartnerPairingSheet = MutableStateFlow(false)
    val showPartnerPairingSheet = _showPartnerPairingSheet.asStateFlow()

    fun setShowPartnerPairingSheet(show: Boolean) {
        _showPartnerPairingSheet.value = show
    }

    private val _showPartnerCovenantGuiltBanner = MutableStateFlow(false)
    val showPartnerCovenantGuiltBanner = _showPartnerCovenantGuiltBanner.asStateFlow()

    fun dismissPartnerCovenantGuiltBanner() {
        _showPartnerCovenantGuiltBanner.value = false
    }

    fun handlePartnerSessionPendingExpired(): Boolean {
        val session = com.analoganchor.app.data.preferences.PartnerSessionPrefs.getActiveSession(getApplication())
        if (session != null && !session.isActivated && session.isExpired()) {
            android.util.Log.w("AnchorViewModel", "Partner session pending activation expired (5 min). Deducting -25 PTS covenant penalty.")
            com.analoganchor.app.data.preferences.PartnerSessionPrefs.clearSession(getApplication())
            deductPoints(25, "Broke Circle Covenant: Shield Not Activated", "penalty")
            _showPartnerCovenantGuiltBanner.value = true
            return true
        }
        return false
    }

    // Emergency Unlock PIN-code Custody Stateflows
    private val _emergencyCoolDownRemaining = MutableStateFlow(0) // 60-min countdown in seconds (3600s)
    val emergencyCoolDownRemaining = _emergencyCoolDownRemaining.asStateFlow()

    private val _activeUnlockRemaining = MutableStateFlow(0) // 1-hour active countdown in seconds (3600s)
    val activeUnlockRemaining = _activeUnlockRemaining.asStateFlow()

    private val _showReEntryShield = MutableStateFlow(false)
    val showReEntryShield = _showReEntryShield.asStateFlow()

    private val _emergencyState = MutableStateFlow("LOCKED") // "LOCKED", "COOLING", "READY_TO_REQUEST", "REQUESTED", "ACTIVE_UNLOCKED", "RE_ENTRY"
    val emergencyState = _emergencyState.asStateFlow()

    // Timestamp when the 60-min unlock request window started (for token expiry check)
    private var emergencyRequestStartTimestamp = 0L

    // Count of emergency unlock requests total
    private val _emergencyUnlockRequestCount = MutableStateFlow(0)
    val emergencyUnlockRequestCount = _emergencyUnlockRequestCount.asStateFlow()

    // Bug #10 fix: grace timer lives in ViewModel (not UI state) so it survives recomposition
    private val _graceTimerRemaining = MutableStateFlow(600)
    val graceTimerRemaining = _graceTimerRemaining.asStateFlow()

    // Home baseline GPS (recorded during onboarding)
    private val _homeLatitude = MutableStateFlow(0.0)
    val homeLatitude = _homeLatitude.asStateFlow()

    private val _homeLongitude = MutableStateFlow(0.0)
    val homeLongitude = _homeLongitude.asStateFlow()

    private val _homeBaseSaved = MutableStateFlow(false)
    val homeBaseSaved = _homeBaseSaved.asStateFlow()

    // Walk vs Run mode for Runway tab
    private val _workoutMode = MutableStateFlow("run") // "run" or "walk"
    val workoutMode = _workoutMode.asStateFlow()

    private var coolDownJob: Job? = null
    private var activeUnlockJob: Job? = null
    private var graceTimerJob: Job? = null
    private var clipboardRescueJob: Job? = null

    // Active session variables
    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive = _isSessionActive.asStateFlow()

    private val _sessionTimeLeftSeconds = MutableStateFlow(0)
    val sessionTimeLeftSeconds = _sessionTimeLeftSeconds.asStateFlow()

    private val _sessionTotalDurationMinutes = MutableStateFlow(25)
    val sessionTotalDurationMinutes = _sessionTotalDurationMinutes.asStateFlow()

    private val _sessionVoidedMessage = MutableStateFlow<String?>(null)
    val sessionVoidedMessage = _sessionVoidedMessage.asStateFlow()

    private val _focusPickupCount = MutableStateFlow(0)
    val focusPickupCount = _focusPickupCount.asStateFlow()

    private val _focusPhoneFaceDown = MutableStateFlow(false)
    val focusPhoneFaceDown = _focusPhoneFaceDown.asStateFlow()

    private val _focusDisciplineWarning = MutableStateFlow<String?>(null)
    val focusDisciplineWarning = _focusDisciplineWarning.asStateFlow()

    private val _focusDndActive = MutableStateFlow(false)
    val focusDndActive = _focusDndActive.asStateFlow()

    private val _notificationPolicyAccessGranted = MutableStateFlow(false)
    val notificationPolicyAccessGranted = _notificationPolicyAccessGranted.asStateFlow()

    var sessionStartTime: Long = 0
        private set

    // Sensors
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val stepDetectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager: PowerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var lastGyroX = 0f
    private var lastGyroY = 0f
    private var lastGyroZ = 0f
    private var initialRingerMode: Int = AudioManager.RINGER_MODE_NORMAL
    private var initialInterruptionFilter: Int? = null
    private var focusDndApplied = false
    private var focusWakeLock: PowerManager.WakeLock? = null
    private var focusFaceDownArmed = false
    private var focusWasFaceDown = false
    private var focusScreenLockedForCurrentFaceDown = false
    private var lastFocusPickupMs = 0L
    private var initialStepCount = -1f
    private var lastStepCounterValue: Float? = null
    private var fajrWakeStepBaseline = -1f
    private var emergencyExpiryRingtone: Ringtone? = null

    // Timer countdown handler
    private val handler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (_isSessionActive.value) {
                val totalSeconds = if (ANCHOR_TAP_ONE_TEST_MODE) ANCHOR_TAP_ONE_TEST_DURATION_SEC else _sessionTotalDurationMinutes.value * 60
                val elapsedSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()
                val remaining = (totalSeconds - elapsedSeconds).coerceAtLeast(0)
                _sessionTimeLeftSeconds.value = remaining
                if (remaining > 0) {
                    handler.postDelayed(this, 1000)
                } else {
                    _isSessionActive.value = false
                    prefs.edit().putBoolean("is_session_active", false).apply()
                    unregisterSensors()
                    restoreAudioMode()
                    releaseFocusWakeLock()
                    _focusPhoneFaceDown.value = false
                    focusFaceDownArmed = false
                    focusWasFaceDown = false
                    _isSessionEndAlarmRinging.value = true
                    com.analoganchor.app.service.FajrAlarmReceiver.triggerSessionEndAlarm(context)
                }
            }
        }
    }

    private var tickerJob: Job? = null

    init {
        // Load latest 5 logs reactively
        sessionLogs = repository.getLatestLogs(5).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        whiteboardSessions = repository.getWhiteboardSessions().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        val currentDate = todayDate() // capture once; flows are built at init time
        todayProofMission = database.proofMissionDao().observeLatestCompletedForDate(currentDate).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
        requestsToday = database.unlockSessionDao().observeRequestCountForDate(currentDate).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
        requestsThisWeek = database.unlockSessionDao().observeRequestCountSince(currentWeekStartMillis).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
        pendingReturnSession = database.unlockSessionDao().observePendingReturnSession().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
        latestBreachSession = database.unlockSessionDao().observeLatestBreachSession().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
        openRecoveryRecord = database.recoveryRecordDao().observeOpenRecovery().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
        activeRuleFlagCount = database.ruleFlagDao().observeUnacknowledgedCount().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
        activeRuleFlags = database.ruleFlagDao().observeUnacknowledged().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Read states from SharedPreferences
        _reservePoints.value = prefs.getInt("reserve_points", 0)
        _lastPenaltyTimestamp.value = prefs.getLong("last_penalty_timestamp", 0L)
        _lastLedgerTransactionTimestamp.value = prefs.getLong("last_ledger_transaction_timestamp", 0L)
        _lastLedgerTransactionType.value = prefs.getString("last_ledger_transaction_type", null)?.let {
            runCatching { LedgerTransactionType.valueOf(it) }.getOrNull()
        }
        updateRecentLedgerTransactionStatus()

        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                updateRecentLedgerTransactionStatus()
            }
        }
        instance = this
        _isLocked.value = prefs.getBoolean("is_locked", false)
        _isSealed.value = prefs.getBoolean("is_sealed", true)
        _isOnboarded.value = prefs.getBoolean("is_onboarded", false)
        _appPinEnabled.value = prefs.getBoolean("app_pin_enabled", false)
        _appPinSet.value = prefs.getString("app_pin_hash", "").orEmpty().isNotEmpty()
        _appThemeMode.value = prefs.getString("app_theme_mode", "DARK") ?: "DARK"
        _sanctuaryType.value = prefs.getString("sanctuary_type", "mosque") ?: "mosque"
        _mosqueName.value = prefs.getString("mosque_name", "Designated Mosque") ?: "Designated Mosque"
        _mosqueAddress.value = prefs.getString("mosque_address", "Pin a mosque from the live GPS map") ?: "Pin a mosque from the live GPS map"
        _userLocationName.value = prefs.getString("user_location_name", "GPS pending") ?: "GPS pending"
        _fajrAlarmEnabled.value = prefs.getBoolean("fajr_alarm_enabled", false)
        _fajrAlarmNfcLocked.value = prefs.getBoolean("fajr_alarm_nfc_locked", false)
        _fajrAlarmOffsetMinutes.value = prefs.getInt("fajr_alarm_offset_minutes", 0)
        _fajrAlarmDays.value = prefs.getString("fajr_alarm_days", "")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet() ?: emptySet()
        if (validateAndExpireFajrAlarmIfPastSunrise()) {
            _isFajrAlarmRinging.value = false
        } else {
            _isFajrAlarmRinging.value = prefs.getBoolean("fajr_alarm_ringing", false)
        }
        _isJumuahAlarmRinging.value = prefs.getBoolean("jumuah_alarm_ringing", false)
        _isFajrAlarmMissed.value = prefs.getBoolean("fajr_alarm_missed", false)
        _isJumuahAlarmMissed.value = prefs.getBoolean("jumuah_alarm_missed", false)
        handlePartnerSessionPendingExpired()
        val (initLat, initLng) = getEffectiveCoordinates()
        com.analoganchor.app.service.FajrAlarmScheduler.scheduleJumuahAlarm(getApplication(), initLat, initLng)
        com.analoganchor.app.service.FajrAlarmScheduler.scheduleThursdayPreBedtimePrompt(getApplication())
        _fajrAlarmAyahConstraint.value = prefs.getBoolean("fajr_alarm_ayah_constraint", false)
        _fajrAlarmAccountabilityPinEnabled.value = prefs.getBoolean("fajr_alarm_accountability_pin_enabled", false)
        _fajrAccountabilityPinSet.value = prefs.getString("fajr_accountability_pin_hash", "").orEmpty().isNotEmpty()
        _fajrAlarmRingtoneUri.value = prefs.getString("fajr_alarm_ringtone_uri", null)
        _fajrAlarmRingtoneLabel.value = prefs.getString("fajr_alarm_ringtone_label", null)
            ?: if (_fajrAlarmRingtoneUri.value.isNullOrBlank()) "Built-in Analog tone" else "Custom ringtone"
        _nfcTagId.value = prefs.getString("nfc_tag_id", "") ?: ""
        _fajrNfcTagId.value = prefs.getString("fajr_nfc_tag_id", "") ?: ""
        if ((_fajrNfcTagId.value.isEmpty() || _fajrNfcTagId.value == "VIRTUAL_SOFT_FAJR_TAG") && _nfcTagId.value.isNotEmpty() && _nfcTagId.value != "VIRTUAL_SOFT_TAG") {
            _fajrNfcTagId.value = _nfcTagId.value
            prefs.edit().putString("fajr_nfc_tag_id", _nfcTagId.value).apply()
        }
        viewModelScope.launch {
            preferencesRepository.userPreferencesFlow.collect { preferences ->
                _nfcTagId.value = preferences.anchorNfcTagId
            }
        }
        _eveningProtocolTime.value = prefs.getString("evening_protocol_time", "22:00") ?: "22:00"
        _uninstallPinSet.value = prefs.getString("uninstall_pin_hash", "").orEmpty().isNotEmpty()

        val todayStr = formatDateKey(System.currentTimeMillis())
        _fajrVerified.value = prefs.getBoolean("fajr_verified_$todayStr", false)
        val wakeVerifiedToday = prefs.getBoolean("fajr_wake_verified_$todayStr", false)
        _fajrWakeVerified.value = wakeVerifiedToday
        _fajrWakeStepVerificationActive.value =
            !wakeVerifiedToday && prefs.getBoolean("fajr_wake_step_active_$todayStr", false)
        _fajrWakeStepsAfterDismiss.value = if (_fajrWakeStepVerificationActive.value) {
            prefs.getInt("fajr_wake_steps_$todayStr", 0).coerceIn(0, FAJR_WAKE_STEP_GOAL)
        } else {
            0
        }
        fajrWakeStepBaseline = prefs.getFloat("fajr_wake_step_baseline_$todayStr", -1f)
        _maghribVerified.value = prefs.getBoolean("maghrib_verified_$todayStr", false)
        val saved25mCount = prefs.getInt("daily_25m_count_$todayStr", 0)
        val savedLongSession = prefs.getBoolean("daily_long_session_$todayStr", false)
        _daily25mCount.value = saved25mCount
        _hasLongSessionToday.value = savedLongSession
        val isFullyCapped = savedLongSession || (saved25mCount >= 2) || prefs.getBoolean("evening_anchor_completed_$todayStr", false)
        _isEveningAnchorCompletedToday.value = isFullyCapped

        _isTadabburAlarmRinging.value = prefs.getBoolean("tadabbur_alarm_ringing", false)
        val initTadActive = prefs.getBoolean("tadabbur_commitment_active", false)
        val initTadUntil = prefs.getLong("tadabbur_commitment_until_ms", 0L)
        val initNowMs = System.currentTimeMillis()
        if (initTadActive && initNowMs > initTadUntil) {
            prefs.edit().putBoolean("tadabbur_commitment_active", false).apply()
            _tadabburCommitmentActive.value = false
        } else {
            _tadabburCommitmentActive.value = initTadActive
            _tadabburCommitmentDays.value = prefs.getInt("tadabbur_commitment_days", 3)
            val remDays = if (initTadActive && initTadUntil > initNowMs) {
                (((initTadUntil - initNowMs) / 86400000L).toInt() + 1).coerceAtLeast(1)
            } else {
                prefs.getInt("tadabbur_commitment_days", 3)
            }
            _tadabburCommitmentRemainingDays.value = remDays
        }

        val savedUserLat = prefs.getFloat("user_lat", Float.NaN).toDouble()
        val savedUserLng = prefs.getFloat("user_lng", Float.NaN).toDouble()
        if (savedUserLat.isNaN() || savedUserLng.isNaN() || isLegacyDefaultCoordinate(savedUserLat, savedUserLng)) {
            _userLatitude.value = LOCATION_UNSET
            _userLongitude.value = LOCATION_UNSET
            if (_userLocationName.value == "Busaiteen, Bahrain") {
                _userLocationName.value = "GPS pending"
                prefs.edit().remove("user_lat").remove("user_lng").remove("user_location_name").apply()
            }
        } else {
            _userLatitude.value = savedUserLat
            _userLongitude.value = savedUserLng
        }

        // Read sanctuary coordinates
        val savedSanctuaryLat = prefs.getFloat("sanctuary_lat", Float.NaN).toDouble()
        val savedSanctuaryLng = prefs.getFloat("sanctuary_lng", Float.NaN).toDouble()
        if (savedSanctuaryLat.isNaN() || savedSanctuaryLng.isNaN() || isLegacyDefaultSanctuary(savedSanctuaryLat, savedSanctuaryLng)) {
            _sanctuaryLatitude.value = LOCATION_UNSET
            _sanctuaryLongitude.value = LOCATION_UNSET
            if (isLegacyDefaultSanctuary(savedSanctuaryLat, savedSanctuaryLng)) {
                prefs.edit().remove("sanctuary_lat").remove("sanctuary_lng").apply()
            }
        } else {
            _sanctuaryLatitude.value = savedSanctuaryLat
            _sanctuaryLongitude.value = savedSanctuaryLng
        }

        // Read behavior streaking records
        _mosqueComplianceStreak.value = prefs.getInt("mosque_streak", 0)
        _runningConsistencyScore.value = prefs.getInt("running_consistency_score", 82)
        _protocolComplianceStreak.value = prefs.getInt("protocol_streak", 0)
        _maghribRetreatStreak.value = prefs.getInt("maghrib_retreat_streak", 0)
        _anchorStreak.value = prefs.getInt("anchor_streak", _protocolComplianceStreak.value)
        recalculateWeeklyPillarStreaks()
        recalculateHabitCompliance()
        recalculateWeeklyMosqueStats()
        recalculateCovenantStats()
        recalculateStakeStats()
        recalculateTodayPrayerStatus()
        syncPrayerBufferState()

        // Read emergency unlock request count
        _emergencyUnlockRequestCount.value = prefs.getInt("emergency_unlock_count", 0)

        restoreEmergencyState()

        // Read home baseline GPS
        val homeLat = prefs.getFloat("home_lat", 0f).toDouble()
        val homeLng = prefs.getFloat("home_lng", 0f).toDouble()
        _homeLatitude.value = homeLat
        _homeLongitude.value = homeLng
        _homeBaseSaved.value = (homeLat != 0.0 && homeLng != 0.0)

        // Read outdoor sanctuary preferences
        _outdoorSanctuaryName.value = prefs.getString("outdoor_sanctuary_name", "Favorite Outdoor Sanctuary") ?: "Favorite Outdoor Sanctuary"
        _outdoorSanctuaryAddress.value = prefs.getString("outdoor_sanctuary_address", "Pinned outdoor sanctuary") ?: "Pinned outdoor sanctuary"
        val savedOutdoorLat = prefs.getFloat("outdoor_sanctuary_lat", Float.NaN).toDouble()
        val savedOutdoorLng = prefs.getFloat("outdoor_sanctuary_lng", Float.NaN).toDouble()
        if (!savedOutdoorLat.isNaN() && !savedOutdoorLng.isNaN()) {
            _outdoorSanctuaryLat.value = savedOutdoorLat
            _outdoorSanctuaryLng.value = savedOutdoorLng
        } else if (_sanctuaryType.value == "runaway" || _sanctuaryType.value == "outdoor") {
            _outdoorSanctuaryLat.value = _sanctuaryLatitude.value
            _outdoorSanctuaryLng.value = _sanctuaryLongitude.value
        }
        val currentWeekKey = "${Calendar.getInstance().get(Calendar.YEAR)}_${Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)}"
        val savedWeekKey = prefs.getString("outdoor_solo_week_key", "") ?: ""
        if (savedWeekKey != currentWeekKey) {
            prefs.edit()
                .putString("outdoor_solo_week_key", currentWeekKey)
                .putInt("outdoor_solo_count_this_week", 0)
                .apply()
            _outdoorSoloCountThisWeek.value = 0
        } else {
            _outdoorSoloCountThisWeek.value = prefs.getInt("outdoor_solo_count_this_week", 0)
        }
        _outdoorPartnerCountThisWeek.value = prefs.getInt("outdoor_partner_count_this_week", 0)

        // Read workout mode
        _workoutMode.value = prefs.getString("workout_mode", "run") ?: "run"

        checkHardwarePermissions()
        requestRealLocation()

        // Register step sensors
        registerStepCounterSensor()

        loadLockedSpots()

        // Start dynamic ticking countdown coroutine loop (updates every second)
        startTicker()
        refreshExpiredUnlockSessions()
        
        // Schedule nightly reminder for Fajr alarm
        com.analoganchor.app.service.FajrAlarmScheduler.scheduleNightlyReminder(context)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                updateTimeTickers()
                delay(1000)
            }
        }
    }

    private fun restoreEmergencyState() {
        emergencyRequestStartTimestamp = prefs.getLong("emergency_token_timestamp", 0L)

        when (prefs.getString("emergency_state", "LOCKED") ?: "LOCKED") {
            "COOLING" -> restoreCooldownState()
            "REQUESTED" -> {
                if (isEmergencyTokenFresh()) {
                    _emergencyState.value = "REQUESTED"
                } else {
                    lockEmergencyState()
                }
            }
            "ACTIVE_UNLOCKED" -> restoreActiveUnlockState()
            "RE_ENTRY" -> restoreReEntryState()
            "READY_TO_REQUEST" -> _emergencyState.value = "READY_TO_REQUEST"
            else -> lockEmergencyState()
        }
    }

    private fun launchCooldownCountdown(startEpoch: Long) {
        val targetEpoch = startEpoch + COOLDOWN_DURATION_SEC * 1000L
        coolDownJob?.cancel()
        coolDownJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val remaining = ((targetEpoch - now) / 1000).toInt()
                if (remaining <= 0) {
                    _emergencyCoolDownRemaining.value = 0
                    _emergencyState.value = "READY_TO_REQUEST"
                    prefs.edit()
                        .putString("emergency_state", "READY_TO_REQUEST")
                        .remove("emergency_cooldown_start_epoch")
                        .apply()
                    com.analoganchor.app.service.FajrAlarmScheduler.cancelReflectionWindowAlarm(context)
                    try {
                        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(400)
                        }
                    } catch (_: Exception) {}
                    break
                } else {
                    _emergencyCoolDownRemaining.value = remaining
                }
                delay(1000)
            }
        }
    }

    private fun restoreCooldownState() {
        val cooldownStartEpoch = prefs.getLong("emergency_cooldown_start_epoch", 0L)
        if (cooldownStartEpoch <= 0L) {
            lockEmergencyState()
            return
        }
        val targetEpoch = cooldownStartEpoch + COOLDOWN_DURATION_SEC * 1000L
        val remainingSec = ((targetEpoch - System.currentTimeMillis()) / 1000).toInt()

        if (remainingSec <= 0) {
            _emergencyCoolDownRemaining.value = 0
            _emergencyState.value = "READY_TO_REQUEST"
            prefs.edit()
                .putString("emergency_state", "READY_TO_REQUEST")
                .remove("emergency_cooldown_start_epoch")
                .apply()
            com.analoganchor.app.service.FajrAlarmScheduler.cancelReflectionWindowAlarm(context)
            return
        }

        _emergencyState.value = "COOLING"
        _emergencyCoolDownRemaining.value = remainingSec
        launchCooldownCountdown(cooldownStartEpoch)
    }

    private fun restoreActiveUnlockState() {
        val expiresAt = prefs.getLong("emergency_active_unlock_expires_epoch", 0L)
        val remainingSec = remainingSecondsUntil(expiresAt)

        if (remainingSec <= 0) {
            clearClipboard()
            playTemporaryUnlockExpiredAlarm()
            enterReEntryState()
            return
        }

        launchActiveUnlockCountdown(expiresAt, initialClipboardClearDelayMs = 0L)
    }

    private fun restoreReEntryState() {
        val graceExpiresAt = prefs.getLong("emergency_grace_expires_epoch", 0L)
        val remainingSec = remainingSecondsUntil(graceExpiresAt)

        _showReEntryShield.value = true
        _emergencyState.value = "RE_ENTRY"

        if (remainingSec <= 0) {
            failBlockingRuleRearm()
        } else {
            startGraceTimer(remainingSec = remainingSec, persistDeadline = false)
        }
    }

    private fun remainingSecondsUntil(epochMillis: Long): Int {
        if (epochMillis <= 0L) return 0
        val remainingMs = epochMillis - System.currentTimeMillis()
        if (remainingMs <= 0L) return 0
        return ((remainingMs + 999L) / 1000L).toInt()
    }

    private fun formatDateKey(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }

    private fun calculateWeekStartMillis(): Long {
        return Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun yesterdayDateKey(): String {
        return Calendar.getInstance().apply {
            add(Calendar.DATE, -1)
        }.let { formatDateKey(it.timeInMillis) }
    }

    private fun getDaysInCurrentWeekToToday(): List<String> {
        val list = mutableListOf<String>()
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysSinceFriday = (dayOfWeek - Calendar.FRIDAY + 7) % 7
        for (offset in 0..daysSinceFriday) {
            val checkCal = (cal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -offset)
            }
            list.add(formatDateKey(checkCal.timeInMillis))
        }
        return list
    }

    private fun calculateConsecutiveStreak(
        daysInWeek: List<String>,
        isCompleted: (dateKey: String) -> Boolean
    ): Int {
        if (daysInWeek.isEmpty()) return 0
        val todayCompleted = isCompleted(daysInWeek[0])
        return if (todayCompleted) {
            var streak = 1
            for (i in 1 until daysInWeek.size) {
                if (isCompleted(daysInWeek[i])) {
                    streak++
                } else {
                    break
                }
            }
            streak
        } else {
            if (daysInWeek.size > 1 && isCompleted(daysInWeek[1])) {
                var streak = 1
                for (i in 2 until daysInWeek.size) {
                    if (isCompleted(daysInWeek[i])) {
                        streak++
                    } else {
                        break
                    }
                }
                streak
            } else {
                0
            }
        }
    }

    fun recalculateWeeklyPillarStreaks() {
        checkWeeklyFridayReset()

        val daysInWeek = getDaysInCurrentWeekToToday()

        val protocolStreak = calculateConsecutiveStreak(daysInWeek) { dateKey ->
            prefs.getBoolean("evening_anchor_completed_$dateKey", false)
        }
        val retreatStreak = calculateConsecutiveStreak(daysInWeek) { dateKey ->
            prefs.getBoolean("retreat_completed_$dateKey", false) ||
                prefs.getBoolean("maghrib_verified_$dateKey", false)
        }
        val fajrStreak = calculateConsecutiveStreak(daysInWeek) { dateKey ->
            prefs.getBoolean("fajr_verified_$dateKey", false) ||
                prefs.getBoolean("fajr_wake_verified_$dateKey", false) ||
                prefs.getBoolean("prayer_verified_fajr_$dateKey", false)
        }

        prefs.edit()
            .putInt("protocol_streak", protocolStreak)
            .putInt("maghrib_retreat_streak", retreatStreak)
            .putInt("mosque_streak", fajrStreak)
            .apply()

        _protocolComplianceStreak.value = protocolStreak
        _maghribRetreatStreak.value = retreatStreak
        _mosqueComplianceStreak.value = fajrStreak

        // Check 3-day all pillars mastery bonus (250 pts)
        if (protocolStreak >= 3 && retreatStreak >= 3 && fajrStreak >= 3) {
            val lastFriday = prefs.getString("last_friday_reset_date", "") ?: ""
            val bonus3DayKey = "weekly_3day_all_pillars_bonus_$lastFriday"
            if (!prefs.getBoolean(bonus3DayKey, false)) {
                prefs.edit().putBoolean(bonus3DayKey, true).apply()
                addPoints(250, "3-Day Triple Pillar Mastery Bonus", "milestone")
            }
        }

        // Check 7-day all pillars mastery bonus (500 pts)
        if (protocolStreak >= 7 && retreatStreak >= 7 && fajrStreak >= 7) {
            val lastFriday = prefs.getString("last_friday_reset_date", "") ?: ""
            val weekBonusKey = "weekly_7day_all_pillars_bonus_$lastFriday"
            if (!prefs.getBoolean(weekBonusKey, false)) {
                prefs.edit().putBoolean(weekBonusKey, true).apply()
                addPoints(500, "7-Day Triple Pillar Mastery Bonus", "milestone")
            }
        }

        recalculateHabitCompliance()
    }

    // Mathematical Haversine formula to compute true geographical distance in meters
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2.0).pow(2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return R * c
    }

    private fun isUsableCoordinate(lat: Double, lng: Double): Boolean {
        return lat.isFinite() && lng.isFinite() && !(abs(lat) < 0.00001 && abs(lng) < 0.00001)
    }

    fun getEffectiveCoordinates(): Pair<Double, Double> {
        val lat = _userLatitude.value
        val lng = _userLongitude.value
        if (isUsableCoordinate(lat, lng)) {
            return Pair(lat, lng)
        }
        val savedLat = prefs.getFloat("user_lat", Float.NaN).toDouble()
        val savedLng = prefs.getFloat("user_lng", Float.NaN).toDouble()
        if (isUsableCoordinate(savedLat, savedLng)) {
            return Pair(savedLat, savedLng)
        }
        return Pair(21.4225, 39.8262) // Mecca fallback
    }

    private fun isLegacyDefaultCoordinate(lat: Double, lng: Double): Boolean {
        return abs(lat - LEGACY_DEFAULT_LAT) < 0.0002 && abs(lng - LEGACY_DEFAULT_LNG) < 0.0002
    }

    private fun isLegacyGeneratedSanctuaryCoordinate(lat: Double, lng: Double): Boolean {
        val legacySeeds = listOf(
            26.2285 to 50.5860,
            26.2300 to 50.5820,
            26.2250 to 50.5790,
            26.2210 to 50.5840,
            26.2230 to 50.5890,
            26.2190 to 50.5750,
            26.1900 to 50.5500
        )
        return legacySeeds.any { (seedLat, seedLng) ->
            abs(lat - seedLat) < 0.0002 && abs(lng - seedLng) < 0.0002
        }
    }

    private fun isLegacyDefaultSanctuary(lat: Double, lng: Double): Boolean {
        val hasLegacyCoordinate = isLegacyGeneratedSanctuaryCoordinate(lat, lng)
        val nameLooksDefault = _mosqueName.value == "Al Fateh Grand Mosque" ||
            _mosqueAddress.value == "Manama, Bahrain" ||
            _mosqueAddress.value.contains("Busaiteen", ignoreCase = true) ||
            _mosqueAddress.value.contains("Bahrain", ignoreCase = true)
        return hasLegacyCoordinate && nameLooksDefault
    }

    fun checkLocationStatus() {
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        _isLocationPermissionGranted.value = hasFine || hasCoarse

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isEnabled = lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
        _isLocationServiceEnabled.value = isEnabled
    }

    // Android LocationManager GPS coordinate listener check-ins
    fun requestRealLocation() {
        checkLocationStatus()
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (!hasFine && !hasCoarse) {
            if (!isUsableCoordinate(_userLatitude.value, _userLongitude.value)) {
                setLocationName("Location permission needed")
            }
            return
        }

        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            _isLocationServiceEnabled.value = isEnabled
            if (!isEnabled) {
                if (!isUsableCoordinate(_userLatitude.value, _userLongitude.value)) {
                    setLocationName("GPS off - enable location services")
                }
                return
            }

            val enabledProviders = lm.getProviders(true).filter { provider ->
                hasFine || provider != LocationManager.GPS_PROVIDER
            }

            if (enabledProviders.isEmpty()) {
                if (!isUsableCoordinate(_userLatitude.value, _userLongitude.value)) {
                    setLocationName("GPS off - enable location services")
                }
                return
            }

            val lastKnown = enabledProviders.mapNotNull { provider ->
                try {
                    lm.getLastKnownLocation(provider)
                } catch (e: SecurityException) {
                    null
                }
            }.maxByOrNull { it.time }

            if (lastKnown != null) {
                updateUserCoordinates(lastKnown.latitude, lastKnown.longitude)
            }

            for (provider in enabledProviders) {
                try {
                    lm.requestSingleUpdate(provider, object : LocationListener {
                        override fun onLocationChanged(l: Location) {
                            updateUserCoordinates(l.latitude, l.longitude)
                        }
                        override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                        override fun onProviderEnabled(p: String) { checkLocationStatus() }
                        override fun onProviderDisabled(p: String) { checkLocationStatus() }
                    }, Looper.getMainLooper())
                } catch (e: SecurityException) {
                    // Ignore provider permission errors
                }
            }
        } catch (e: Exception) {
            Log.e("AnchorViewModel", "Error fetching coordinates dynamically: ${e.message}")
        }
    }

    private fun updateUserCoordinates(lat: Double, lng: Double) {
        if (!isUsableCoordinate(lat, lng)) return

        _userLatitude.value = lat
        _userLongitude.value = lng
        prefs.edit().putFloat("user_lat", lat.toFloat()).putFloat("user_lng", lng.toFloat()).apply()
        setLocationName(resolveLocationName(lat, lng))
        fetchNearbyMosques(lat, lng)
        updateOutdoorProximity()
        updateTimeTickers()
        scheduleFajrAlarm()
    }

    private fun resolveLocationName(lat: Double, lng: Double): String {
        data class KnownPlace(val name: String, val lat: Double, val lng: Double, val radiusMeters: Double)

        val nearbyKnownPlace = listOf(
            KnownPlace("Busaiteen, Bahrain", 26.2285, 50.5860, 8_000.0),
            KnownPlace("Manama, Bahrain", 26.2235, 50.5876, 15_000.0),
            KnownPlace("Dubai, UAE", 25.2048, 55.2708, 25_000.0),
            KnownPlace("Abu Dhabi, UAE", 24.4539, 54.3773, 25_000.0),
            KnownPlace("Sharjah, UAE", 25.3463, 55.4209, 18_000.0),
            KnownPlace("Ajman, UAE", 25.4052, 55.5136, 12_000.0),
            KnownPlace("Al Ain, UAE", 24.1302, 55.8023, 20_000.0),
            KnownPlace("Mecca, Saudi Arabia", 21.4225, 39.8262, 8_000.0),
            KnownPlace("London, United Kingdom", 51.5074, -0.1278, 8_000.0)
        ).map { place ->
            place to calculateDistance(lat, lng, place.lat, place.lng)
        }.filter { (place, distance) ->
            distance <= place.radiusMeters
        }.minByOrNull { (_, distance) ->
            distance
        }?.first

        if (nearbyKnownPlace != null) return nearbyKnownPlace.name

        return when {
            lat in 25.5..26.5 && lng in 50.3..50.9 -> "Bahrain"
            lat in 22.5..26.5 && lng in 51.4..56.6 -> "United Arab Emirates"
            else -> String.format(Locale.US, "GPS: %.4f, %.4f", lat, lng)
        }
    }

    fun refreshNearbyMosques() {
        fetchNearbyMosques(_userLatitude.value, _userLongitude.value, force = true)
    }

    private fun fetchNearbyMosques(lat: Double, lng: Double, force: Boolean = false) {
        if (!isUsableCoordinate(lat, lng)) {
            _mosqueSearchStatus.value = "Sync GPS to find nearby mosques"
            return
        }

        val now = System.currentTimeMillis()
        val lastLat = lastMosqueFetchLat
        val lastLng = lastMosqueFetchLng
        val closeToLastFetch = lastLat != null &&
            lastLng != null &&
            calculateDistance(lat, lng, lastLat, lastLng) < MOSQUE_LOOKUP_REFRESH_DISTANCE_METERS

        if (!force && closeToLastFetch && now - lastMosqueFetchMs < MOSQUE_LOOKUP_REFRESH_INTERVAL_MS) {
            return
        }

        lastMosqueFetchLat = lat
        lastMosqueFetchLng = lng
        lastMosqueFetchMs = now
        _mosqueSearchStatus.value = "Searching nearby mosques..."

        viewModelScope.launch(Dispatchers.IO) {
            val candidates = try {
                queryNearbyMosques(lat, lng)
            } catch (e: Exception) {
                Log.e("AnchorViewModel", "Nearby mosque lookup failed: ${e.message}")
                emptyList()
            }

            _nearbyMosques.value = candidates
            _mosqueSearchStatus.value = if (candidates.isEmpty()) {
                "No OSM mosques found. Pin current GPS or map center."
            } else {
                "Found ${candidates.size} nearby mosques near ${resolveLocationName(lat, lng)}"
            }
        }
    }

    private fun queryNearbyMosques(lat: Double, lng: Double): List<NearbyMosqueCandidate> {
        val query = """
            [out:json][timeout:12];
            (
              node["amenity"="place_of_worship"]["religion"="muslim"](around:$MOSQUE_LOOKUP_RADIUS_METERS,$lat,$lng);
              way["amenity"="place_of_worship"]["religion"="muslim"](around:$MOSQUE_LOOKUP_RADIUS_METERS,$lat,$lng);
              relation["amenity"="place_of_worship"]["religion"="muslim"](around:$MOSQUE_LOOKUP_RADIUS_METERS,$lat,$lng);
            );
            out center tags 24;
        """.trimIndent()

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://overpass-api.de/api/interpreter?data=$encodedQuery")
            .header("User-Agent", "AnalogAnchor/1.0 Android mosque lookup")
            .build()

        mosqueLookupClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()

            val elements = JSONObject(body).optJSONArray("elements") ?: return emptyList()
            val seen = mutableSetOf<String>()
            val candidates = mutableListOf<NearbyMosqueCandidate>()

            for (index in 0 until elements.length()) {
                val element = elements.optJSONObject(index) ?: continue
                val center = element.optJSONObject("center")
                val mosqueLat = when {
                    element.has("lat") -> element.optDouble("lat", Double.NaN)
                    center != null -> center.optDouble("lat", Double.NaN)
                    else -> Double.NaN
                }
                val mosqueLng = when {
                    element.has("lon") -> element.optDouble("lon", Double.NaN)
                    center != null -> center.optDouble("lon", Double.NaN)
                    else -> Double.NaN
                }
                if (!isUsableCoordinate(mosqueLat, mosqueLng)) continue

                val tags = element.optJSONObject("tags") ?: JSONObject()
                val name = tags.optString("name:ar")
                    .ifBlank { tags.optString("name:en") }
                    .ifBlank { tags.optString("name") }
                    .ifBlank { "Nearby Mosque" }
                val addressParts = listOf(
                    tags.optString("addr:housenumber"),
                    tags.optString("addr:street"),
                    tags.optString("addr:suburb"),
                    tags.optString("addr:city"),
                    tags.optString("addr:emirate"),
                    tags.optString("addr:country")
                ).filter { it.isNotBlank() }
                val address = addressParts.joinToString(", ").ifBlank {
                    "Nearby sanctuary zone"
                }
                val distance = calculateDistance(lat, lng, mosqueLat, mosqueLng).roundToInt()
                val key = "${name.lowercase(Locale.US)}:${String.format(Locale.US, "%.5f:%.5f", mosqueLat, mosqueLng)}"
                if (seen.add(key)) {
                    candidates += NearbyMosqueCandidate(name, address, mosqueLat, mosqueLng, distance)
                }
            }

            return candidates.sortedBy { it.distanceMeters }.take(8)
        }
    }

    fun searchOutdoorDestinations(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _outdoorSearchResults.value = emptyList()
            _isSearchingOutdoor.value = false
            return
        }

        // Check if query is a Google Plus Code or numerical coordinate (100% offline pinpoint accuracy)
        val (effLat, effLng) = getEffectiveCoordinates()
        val parsedLoc = com.analoganchor.app.util.LocationCodeParser.parse(trimmed, effLat, effLng)
        if (parsedLoc != null) {
            val candidate = MapboxPlaceCandidate(
                id = "parsed_${System.currentTimeMillis()}",
                name = parsedLoc.title,
                address = parsedLoc.address,
                lat = parsedLoc.lat,
                lng = parsedLoc.lng
            )
            _outdoorSearchResults.value = listOf(candidate)
            _isSearchingOutdoor.value = false
            return
        }

        outdoorSearchJob?.cancel()
        outdoorSearchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300) // debounce
            _isSearchingOutdoor.value = true
            try {
                val (effLat, effLng) = getEffectiveCoordinates()
                val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
                val url = "https://api.mapbox.com/geocoding/v5/mapbox.places/$encodedQuery.json?access_token=$MAPBOX_DEFAULT_TOKEN&proximity=$effLng,$effLat&autocomplete=true&limit=6&country=bh"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AnalogAnchor/2.0 Android Mapbox search")
                    .build()
                mosqueLookupClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _isSearchingOutdoor.value = false
                        return@use
                    }
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val features = json.optJSONArray("features") ?: return@use
                    val results = mutableListOf<MapboxPlaceCandidate>()
                    for (i in 0 until features.length()) {
                        val feat = features.optJSONObject(i) ?: continue
                        val id = feat.optString("id", "feat_$i")
                        val text = feat.optString("text", "")
                        val placeName = feat.optString("place_name", text)
                        val center = feat.optJSONArray("center")
                        if (center != null && center.length() >= 2) {
                            val lng = center.optDouble(0)
                            val lat = center.optDouble(1)
                            results.add(MapboxPlaceCandidate(id, text, placeName, lat, lng))
                        }
                    }
                    _outdoorSearchResults.value = results
                }
            } catch (e: Exception) {
                Log.e("AnchorViewModel", "Outdoor destination search failed: ${e.message}")
            } finally {
                _isSearchingOutdoor.value = false
            }
        }
    }

    fun clearOutdoorSearchResults() {
        outdoorSearchJob?.cancel()
        _outdoorSearchResults.value = emptyList()
        _isSearchingOutdoor.value = false
    }

    // Recalculates astronomical prayer times and evaluates ticks
    private fun updateTimeTickers() {
        val now = Date()
        val calendar = Calendar.getInstance()
        calendar.time = now

        val isFridayToday = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
        _isFriday.value = isFridayToday

        checkWeeklyFridayReset()
        checkFridayJumuahMissedAt3PM()

        val tzOffset = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0
        val (effLat, effLng) = getEffectiveCoordinates()
        val calculator = PrayerTimesCalculator(effLat, effLng, tzOffset)
        
        // Today times
        val timesToday = calculator.calculateTimes(now)
        
        // Tomorrow times for wrapping countdown after Isha
        val tomorrow = Date(now.time + 86400000)
        val timesTomorrow = calculator.calculateTimes(tomorrow)

        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        _prayerTimesMap.value = mapOf(
            "fajr" to sdf.format(timesToday.fajr.time),
            "sunrise" to sdf.format(timesToday.sunrise.time),
            "dhuhr" to sdf.format(timesToday.dhuhr.time),
            "asr" to sdf.format(timesToday.asr.time),
            "maghrib" to sdf.format(timesToday.maghrib.time),
            "isha" to sdf.format(timesToday.isha.time)
        )

        PrayerBufferScheduler.schedulePrayerBufferAlerts(context, effLat, effLng)

        // Determine next prayer target & countdown
        val nowCal = Calendar.getInstance().apply { time = now }
        
        val nextPrayer: Calendar
        val nextName: String

        when {
            nowCal.before(timesToday.fajr) -> {
                nextPrayer = timesToday.fajr
                nextName = if (isFridayToday) "AL-FAJR (الفجر)" else "AL-FAJR (الفجر)"
            }
            nowCal.before(timesToday.sunrise) -> {
                // Technically sunrise isn't a prayer, but let's count down to Sunrise/Shuruq to release Fajr lock
                nextPrayer = timesToday.sunrise
                nextName = "SUNRISE (الشروق)"
            }
            nowCal.before(timesToday.dhuhr) -> {
                nextPrayer = timesToday.dhuhr
                nextName = if (isFridayToday) "AL-JUMU'AH (الجمعة)" else "AD-DHUHR (الظهر)"
            }
            nowCal.before(timesToday.asr) -> {
                nextPrayer = timesToday.asr
                nextName = "AL-ASR (العصر)"
            }
            nowCal.before(timesToday.maghrib) -> {
                nextPrayer = timesToday.maghrib
                nextName = "AL-MAGHRIB (المغرب)"
            }
            nowCal.before(timesToday.isha) -> {
                nextPrayer = timesToday.isha
                nextName = "AL-ISHA (العشاء)"
            }
            else -> {
                // Next day's Fajr
                nextPrayer = timesTomorrow.fajr
                nextName = "AL-FAJR (الفجر)"
            }
        }

        _nextPrayerName.value = nextName

        val remainingMs = nextPrayer.timeInMillis - nowCal.timeInMillis
        val remainingSeconds = maxOf(0L, remainingMs / 1000)
        
        val hours = remainingSeconds / 3600
        val minutes = (remainingSeconds % 3600) / 60
        val seconds = remainingSeconds % 60

        _nextPrayerCountdown.value = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

        val nextFajrForSleep = if (nowCal.before(timesToday.fajr)) timesToday.fajr else timesTomorrow.fajr
        val timeUntilFajrMs = (nextFajrForSleep.timeInMillis - nowCal.timeInMillis).coerceAtLeast(0L)
        val isNightDisciplineWindow = nowCal.after(timesToday.isha) || nowCal.before(timesToday.fajr)
        _sleepPressureTimeLeftMs.value = timeUntilFajrMs
        _sleepPressureStatus.value = when {
            !isNightDisciplineWindow -> "NORMAL"
            timeUntilFajrMs <= 5 * 60 * 60 * 1000L -> "MINIMUM"
            timeUntilFajrMs <= 6 * 60 * 60 * 1000L -> "LAST_WINDOW"
            else -> "NIGHT_WINDOW"
        }

        // Check if previous Fajr NFC pre-commitment lock has expired (past Fajr/sunrise)
        checkFajrLockExpiration()

        // Sync emergency cooldown with wall-clock time
        if (_emergencyState.value == "COOLING") {
            val cooldownStartEpoch = prefs.getLong("emergency_cooldown_start_epoch", 0L)
            if (cooldownStartEpoch > 0L) {
                val targetEpoch = cooldownStartEpoch + COOLDOWN_DURATION_SEC * 1000L
                val remaining = ((targetEpoch - System.currentTimeMillis()) / 1000).toInt()
                if (remaining <= 0) {
                    _emergencyCoolDownRemaining.value = 0
                    _emergencyState.value = "READY_TO_REQUEST"
                    prefs.edit()
                        .putString("emergency_state", "READY_TO_REQUEST")
                        .remove("emergency_cooldown_start_epoch")
                        .apply()
                    com.analoganchor.app.service.FajrAlarmScheduler.cancelReflectionWindowAlarm(context)
                } else {
                    _emergencyCoolDownRemaining.value = remaining
                }
            }
        }
    }

    // Deducts 60 credits for lock bypass. 0 credits = Hard lockdown!
    fun bypassLockWithCredits(): Boolean {
        val currentPoints = _reservePoints.value
        if (currentPoints >= 60) {
            deductPoints(60, "Emergency Bypass Unlock", "deduction")
            prefs.edit().putBoolean("is_override_active", true).apply()
            setLockedState(false)
            return true
        }
        return false // Hard block engaged!
    }

    // Attempt to bypass lock using the 380 Reserve Ledger Balance (76% refund target)
    fun requestReserveUnlock(): Boolean {
        val currentPoints = _reservePoints.value
        if (currentPoints >= 380) {
            deductPoints(380, "Reserve Balance Unlock", "deduction")
            prefs.edit().putBoolean("is_override_active", true).apply()
            setLockedState(false)
            return true
        }
        return false // Insufficient balance (< 380 PTS)
    }

    // GPS Mosque Sanctuary Geofence Check Proximity Validation
    fun verifyMosquePresence(): Boolean {
        requestRealLocation()
        val userLat = _userLatitude.value
        val userLng = _userLongitude.value

        val targetLat = prefs.getFloat("sanctuary_lat", LOCATION_UNSET.toFloat()).toDouble()
        val targetLng = prefs.getFloat("sanctuary_lng", LOCATION_UNSET.toFloat()).toDouble()
        if (!isUsableCoordinate(userLat, userLng) || !isUsableCoordinate(targetLat, targetLng)) {
            return false
        }

        val distance = calculateDistance(userLat, userLng, targetLat, targetLng)
        if (distance <= 200.0) { // Real geofence boundary: 200 meters
            // Earn credits
            addPoints(25, "Sanctuary Geofence Verified", "redemption")
            setLockedState(false)
            prefs.edit().putBoolean("is_override_active", true).apply()

            // Learning behavior streaking increment
            val currentStreak = prefs.getInt("mosque_streak", 0) + 1
            prefs.edit().putInt("mosque_streak", currentStreak).apply()
            _mosqueComplianceStreak.value = currentStreak

            recalculateHabitCompliance()
            return true
        }
        return false
    }

    // Step running threshold verification (+30 credits, unlocks app)
    fun addSteps(amount: Int) {
        val current = _stepsCount.value
        val updated = current + amount
        _stepsCount.value = updated

        val goal = getWorkoutStepGoal()
        if (current < goal && updated >= goal) {
            addPoints(15, "Completed Step Run Threshold", "complete")
            setLockedState(false)
            prefs.edit().putBoolean("is_override_active", true).apply()

            val runScore = minOf(100, _runningConsistencyScore.value + 5)
            prefs.edit().putInt("running_consistency_score", runScore).apply()
            _runningConsistencyScore.value = runScore

            recalculateHabitCompliance()
        }
    }

    fun simulateStepIncrement() {
        // Keep for founder debug only
        addSteps(350)
    }

    fun setWorkoutMode(mode: String) {
        // mode: "run" or "walk"
        prefs.edit().putString("workout_mode", mode).apply()
        _workoutMode.value = mode
        // Reset step count when switching modes
        _stepsCount.value = 0
        initialStepCount = -1f
    }

    fun getWorkoutStepGoal(): Int {
        return if (_workoutMode.value == "walk") 4000 else 2000
    }

    /** Records the user's current GPS as the home baseline anchor point. */
    fun saveHomeBaseline() {
        requestRealLocation()
        val lat = _userLatitude.value
        val lng = _userLongitude.value
        if (lat != 0.0 && lng != 0.0) {
            prefs.edit()
                .putFloat("home_lat", lat.toFloat())
                .putFloat("home_lng", lng.toFloat())
                .apply()
            _homeLatitude.value = lat
            _homeLongitude.value = lng
            _homeBaseSaved.value = true
        }
    }

    /** Manually set home baseline (for founder setup) */
    fun setHomeBaselineManual(lat: Double, lng: Double) {
        prefs.edit()
            .putFloat("home_lat", lat.toFloat())
            .putFloat("home_lng", lng.toFloat())
            .apply()
        _homeLatitude.value = lat
        _homeLongitude.value = lng
        _homeBaseSaved.value = true
    }

    /** Distance from home baseline to current GPS. Returns -1 if baseline not set. */
    fun distanceFromHome(): Double {
        if (!_homeBaseSaved.value) return -1.0
        return calculateDistance(_userLatitude.value, _userLongitude.value, _homeLatitude.value, _homeLongitude.value)
    }

    private fun recalculateHabitCompliance() {
        val mosqueStreak = _mosqueComplianceStreak.value
        val runScore = _runningConsistencyScore.value
        val protocolStreak = _protocolComplianceStreak.value

        val calculatedScore = ((mosqueStreak * 5) + (runScore) + (protocolStreak * 4)).coerceIn(50, 100)
        _habitScore.value = calculatedScore
    }

    fun recalculateWeeklyMosqueStats() {
        var count = 0
        val cal = Calendar.getInstance()
        val prayerKeys = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
        for (i in 0 until 7) {
            val dateKey = formatDateKey(cal.timeInMillis)
            for (p in prayerKeys) {
                if (prefs.getBoolean("prayer_verified_${p}_$dateKey", false)) {
                    count++
                }
            }
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        _weeklyMosqueCheckInCount.value = count
        _maghribRetreatStreak.value = prefs.getInt("maghrib_retreat_streak", 0)
    }

    fun saveUserProfile(name: String, gender: String) {
        val cleanName = name.trim().ifEmpty { "Client" }
        val cleanGender = if (gender.lowercase() == "female") "female" else "male"
        prefs.edit()
            .putString("user_name", cleanName)
            .putString("client_name", cleanName)
            .putString("user_gender", cleanGender)
            .apply()
        _userName.value = cleanName
        _userGender.value = cleanGender
        _isFemaleUserFlow.value = cleanGender == "female"
        recalculateCovenantStats()
    }

    fun recalculateCovenantStats() {
        var startTs = prefs.getLong("covenant_start_timestamp", 0L)
        if (startTs == 0L) {
            startTs = System.currentTimeMillis()
            prefs.edit().putLong("covenant_start_timestamp", startTs).apply()
        }

        val cycleIndex = prefs.getInt("covenant_cycle_index", 1)
        val now = System.currentTimeMillis()
        val diffDays = ((now - startTs) / (24 * 60 * 60 * 1000L)).toInt()

        // Unified 40-Day Takbir Covenant (عهد الأربعين يوماً لتكبيرة الإحرام والسكينة)
        val currentDay = (diffDays + 1).coerceIn(1, 40)
        _covenantDayNumber.value = currentDay

        var totalScore = 0
        val cal = Calendar.getInstance()
        cal.timeInMillis = startTs

        val mosquePrayerKeys = listOf("dhuhr", "asr", "maghrib", "isha")
        for (day in 1..currentDay) {
            val dateKey = formatDateKey(cal.timeInMillis)
            // Mosque prayers (Dhuhr, Asr, Maghrib, Isha) -> 40 PTS each in Hadith calibration
            for (p in mosquePrayerKeys) {
                if (prefs.getBoolean("prayer_verified_${p}_$dateKey", false)) {
                    totalScore += 40
                }
            }
            // Fajr prayer -> 40 PTS if verified (dismissed alarm / wake activity / NFC) and not missed
            val isFajrMosque = prefs.getBoolean("fajr_verified_$dateKey", false)
            val isFajrWake = prefs.getBoolean("fajr_wake_verified_$dateKey", false)
            val isFajrPrayer = prefs.getBoolean("prayer_verified_fajr_$dateKey", false)
            val isFajrMissed = prefs.getBoolean("fajr_alarm_missed_$dateKey", false)
            if ((isFajrMosque || isFajrWake || isFajrPrayer) && !isFajrMissed) {
                totalScore += 40
            }
            // Outdoor routine sessions also earn covenant points
            val outdoorCount = prefs.getInt("outdoor_sessions_$dateKey", 0)
            if (outdoorCount > 0) {
                totalScore += (outdoorCount * 15)
            }

            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val cappedScore = totalScore.coerceIn(0, 8000)
        _covenantScore.value = cappedScore
        _covenantPercentage.value = ((cappedScore / 8000f) * 100f).toInt().coerceIn(0, 100)
        _covenantBonusClaimed.value = prefs.getBoolean("covenant_bonus_claimed_cycle_$cycleIndex", false)
        recalculateTodayPrayerStatus()
        recalculateStakeStats()
    }

    fun recalculateTodayPrayerStatus() {
        val todayKey = formatDateKey(System.currentTimeMillis())
        val isFajr = (prefs.getBoolean("fajr_verified_$todayKey", false) ||
                prefs.getBoolean("fajr_wake_verified_$todayKey", false) ||
                prefs.getBoolean("prayer_verified_fajr_$todayKey", false)) &&
                !prefs.getBoolean("fajr_alarm_missed_$todayKey", false)
        val isDhuhr = prefs.getBoolean("prayer_verified_dhuhr_$todayKey", false)
        val isAsr = prefs.getBoolean("prayer_verified_asr_$todayKey", false)
        val isMaghrib = prefs.getBoolean("prayer_verified_maghrib_$todayKey", false)
        val isIsha = prefs.getBoolean("prayer_verified_isha_$todayKey", false)
        _todayPrayersCompleted.value = listOf(isFajr, isDhuhr, isAsr, isMaghrib, isIsha)
    }

    fun recalculateStakeStats() {
        val installTs = prefs.getLong("app_install_timestamp", System.currentTimeMillis())
        val now = System.currentTimeMillis()
        val diffDays = Math.max(0, ((now - installTs) / (24 * 60 * 60 * 1000L)).toInt())
        val currentWeek = ((diffDays / 7) + 1).coerceIn(1, 12)
        _stakeWeek.value = currentWeek

        val banked = prefs.getInt("banked_stake_points", 0)
        val current = _reservePoints.value.coerceAtLeast(0)
        val totalCumulative = banked + current
        val pct = ((totalCumulative / 6000f) * 100f)
        _cumulativeStakePercentage.value = pct
    }

    fun claimCovenantMilestoneBonus(): Boolean {
        val cycleIndex = prefs.getInt("covenant_cycle_index", 1)
        val alreadyClaimed = prefs.getBoolean("covenant_bonus_claimed_cycle_$cycleIndex", false)
        if (alreadyClaimed) return false

        val targetDays = 40
        if (_covenantDayNumber.value >= targetDays && _covenantScore.value >= 6000) {
            prefs.edit().putBoolean("covenant_bonus_claimed_cycle_$cycleIndex", true).apply()
            _covenantBonusClaimed.value = true
            addPoints(500, "40-Day Takbir Covenant Milestone Completed", "covenant")
            return true
        }
        return false
    }

    fun checkHardwarePermissions() {
        // Bug #7 fix: Wire to real accessibility service check
        _accessibilityActive.value = isAccessibilityServiceEnabled()
        _deviceAdminActive.value = isDeviceAdminActive()
        _notificationPolicyAccessGranted.value = hasNotificationPolicyAccess()
        checkLocationStatus()
    }

    fun deviceAdminComponentName(): ComponentName {
        return ComponentName(context, AnalogAnchorDeviceAdminReceiver::class.java)
    }

    fun isDeviceAdminActive(): Boolean {
        return try {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            devicePolicyManager.isAdminActive(deviceAdminComponentName())
        } catch (e: Exception) {
            false
        }
    }

    fun deviceAdminActivationIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponentName())
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Analog Anchor uses Android Device Admin so uninstalling requires first deactivating this admin privilege in system settings."
            )
        }
    }

    fun hasNotificationPolicyAccess(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            notificationManager().isNotificationPolicyAccessGranted
    }

    fun notificationPolicyAccessIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }

    private fun hashPin(pin: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(pin.toByteArray())
            hash.fold("") { str, it -> str + "%02x".format(it) }
        } catch (e: Exception) {
            pin
        }
    }

    fun setUninstallPin(pin: String): Boolean {
        val cleanPin = pin.trim()
        if (cleanPin.length != 4 || cleanPin.any { !it.isDigit() }) return false

        val hash = hashPin(cleanPin)
        prefs.edit().putString("uninstall_pin_hash", hash).apply()
        _uninstallPinSet.value = true
        return true
    }

    fun setAppPin(pin: String): Boolean {
        val cleanPin = pin.trim()
        if (cleanPin.length != 4 || cleanPin.any { !it.isDigit() }) return false

        prefs.edit()
            .putString("app_pin_hash", hashPin(cleanPin))
            .putBoolean("app_pin_enabled", true)
            .apply()
        _appPinSet.value = true
        _appPinEnabled.value = true
        _appPinUnlocked.value = true
        return true
    }

    fun setAppPinEnabled(enabled: Boolean): Boolean {
        if (enabled && !_appPinSet.value) return false
        prefs.edit().putBoolean("app_pin_enabled", enabled).apply()
        _appPinEnabled.value = enabled
        if (!enabled) {
            _appPinUnlocked.value = true
        }
        return true
    }

    fun verifyAppPin(pin: String): Boolean {
        val savedHash = prefs.getString("app_pin_hash", "").orEmpty()
        val verified = savedHash.isNotEmpty() && savedHash == hashPin(pin.trim())
        if (verified) {
            _appPinUnlocked.value = true
        }
        return verified
    }

    fun clearAppPin() {
        prefs.edit()
            .remove("app_pin_hash")
            .putBoolean("app_pin_enabled", false)
            .apply()
        _appPinSet.value = false
        _appPinEnabled.value = false
        _appPinUnlocked.value = true
    }

    private var lastBackgroundTime = 0L

    fun unlockAppPin() {
        _appPinUnlocked.value = true
    }

    fun lockAppPin() {
        _appPinUnlocked.value = false
    }

    fun onScreenOff() {
        if (_appPinEnabled.value) {
            lastBackgroundTime = System.currentTimeMillis()
        }
    }

    fun onAppBackgrounded() {
        if (_appPinEnabled.value) {
            lastBackgroundTime = System.currentTimeMillis()
        }
        // Pomodoro timer continues running in the background if phone is locked or app backgrounded
    }

    fun onAppResumed() {
        validateAndExpireFajrAlarmIfPastSunrise()
        handlePartnerSessionPendingExpired()
        _isFajrAlarmRinging.value = prefs.getBoolean("fajr_alarm_ringing", false)
        _isJumuahAlarmRinging.value = prefs.getBoolean("jumuah_alarm_ringing", false)
        _isSessionEndAlarmRinging.value = prefs.getBoolean("session_end_alarm_ringing", false)
        _isTadabburAlarmRinging.value = prefs.getBoolean("tadabbur_alarm_ringing", false)
        val tadActive = prefs.getBoolean("tadabbur_commitment_active", false)
        val tadUntil = prefs.getLong("tadabbur_commitment_until_ms", 0L)
        val nowMs = System.currentTimeMillis()
        if (tadActive && nowMs > tadUntil) {
            prefs.edit().putBoolean("tadabbur_commitment_active", false).apply()
            _tadabburCommitmentActive.value = false
        } else {
            _tadabburCommitmentActive.value = tadActive
            _tadabburCommitmentDays.value = prefs.getInt("tadabbur_commitment_days", 3)
            val remDays = if (tadActive && tadUntil > nowMs) {
                (((tadUntil - nowMs) / 86400000L).toInt() + 1).coerceAtLeast(1)
            } else {
                prefs.getInt("tadabbur_commitment_days", 3)
            }
            _tadabburCommitmentRemainingDays.value = remDays
        }
        _isFajrAlarmMissed.value = prefs.getBoolean("fajr_alarm_missed", false)
        _isJumuahAlarmMissed.value = prefs.getBoolean("jumuah_alarm_missed", false)
        _reservePoints.value = prefs.getInt("reserve_points", 0)
        _lastPenaltyTimestamp.value = prefs.getLong("last_penalty_timestamp", 0L)
        updateRecentPenaltyStatus()
        val (resLat, resLng) = getEffectiveCoordinates()
        com.analoganchor.app.service.FajrAlarmScheduler.scheduleThursdayPreBedtimePrompt(getApplication())
        if (_fajrAlarmEnabled.value || _fajrAlarmNfcLocked.value) {
            com.analoganchor.app.service.FajrAlarmScheduler.scheduleFajrAlarm(getApplication(), resLat, resLng)
            com.analoganchor.app.service.FajrAlarmScheduler.scheduleJumuahAlarm(getApplication(), resLat, resLng)
        } else {
            com.analoganchor.app.service.FajrAlarmScheduler.cancelFajrAlarm(getApplication())
            com.analoganchor.app.service.FajrAlarmScheduler.cancelJumuahAlarm(getApplication())
        }
        updateJumuahCountdown()
        checkWeeklyFridayReset()
        checkFridayJumuahMissedAt3PM()
        checkFajrMissed()
        checkFajrLockExpiration()
        recalculateHabitCompliance()

        if (_isSessionActive.value) {
            val totalSeconds = if (ANCHOR_TAP_ONE_TEST_MODE) ANCHOR_TAP_ONE_TEST_DURATION_SEC else _sessionTotalDurationMinutes.value * 60
            val elapsedSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()
            val remaining = (totalSeconds - elapsedSeconds).coerceAtLeast(0)
            _sessionTimeLeftSeconds.value = remaining
            handler.removeCallbacks(countdownRunnable)
            if (remaining <= 0) {
                _isSessionActive.value = false
                prefs.edit().putBoolean("is_session_active", false).apply()
            } else {
                handler.postDelayed(countdownRunnable, 1000)
            }
        }

        if (_appPinEnabled.value) {
            if (lastBackgroundTime == 0L) {
                _appPinUnlocked.value = false
            } else {
                val elapsed = System.currentTimeMillis() - lastBackgroundTime
                if (elapsed >= APP_PIN_AUTO_LOCK_MS) {
                    _appPinUnlocked.value = false
                }
            }
        } else {
            _appPinUnlocked.value = true
        }

        syncEmergencyCooldownState()
    }

    private fun syncEmergencyCooldownState() {
        val state = prefs.getString("emergency_state", "LOCKED") ?: "LOCKED"
        if (state == "COOLING") {
            val cooldownStartEpoch = prefs.getLong("emergency_cooldown_start_epoch", 0L)
            if (cooldownStartEpoch <= 0L) {
                lockEmergencyState()
                return
            }
            val targetEpoch = cooldownStartEpoch + COOLDOWN_DURATION_SEC * 1000L
            val remainingSec = ((targetEpoch - System.currentTimeMillis()) / 1000).toInt()
            if (remainingSec <= 0) {
                _emergencyCoolDownRemaining.value = 0
                _emergencyState.value = "READY_TO_REQUEST"
                prefs.edit()
                    .putString("emergency_state", "READY_TO_REQUEST")
                    .remove("emergency_cooldown_start_epoch")
                    .apply()
                com.analoganchor.app.service.FajrAlarmScheduler.cancelReflectionWindowAlarm(context)
            } else {
                _emergencyState.value = "COOLING"
                _emergencyCoolDownRemaining.value = remainingSec
                if (coolDownJob?.isActive != true) {
                    launchCooldownCountdown(cooldownStartEpoch)
                }
            }
        } else if (state == "ACTIVE_UNLOCKED") {
            val expiresAt = prefs.getLong("emergency_active_unlock_expires_epoch", 0L)
            val remainingSec = remainingSecondsUntil(expiresAt)
            if (remainingSec <= 0) {
                clearClipboard()
                playTemporaryUnlockExpiredAlarm()
                enterReEntryState()
            } else {
                _emergencyState.value = "ACTIVE_UNLOCKED"
                _activeUnlockRemaining.value = remainingSec
                if (activeUnlockJob?.isActive != true) {
                    launchActiveUnlockCountdown(expiresAt, initialClipboardClearDelayMs = 0L)
                }
            }
        } else if (state == "READY_TO_REQUEST") {
            _emergencyState.value = "READY_TO_REQUEST"
        }
    }

    fun setAppThemeMode(mode: String) {
        val cleanMode = if (mode in setOf("DARK", "LIGHT")) mode else "DARK"
        prefs.edit().putString("app_theme_mode", cleanMode).apply()
        _appThemeMode.value = cleanMode
    }

    private fun canChangeFajrEnforcement(accountabilityPin: String?): Boolean {
        return !_fajrAlarmAccountabilityPinEnabled.value || verifyFajrAccountabilityPin(accountabilityPin.orEmpty())
    }

    /**
     * Confirms Fajr NFC lockdown for tonight.
     * @param repeatDaily  If true, alarm reschedules automatically every morning.
     *                     If false (default), it is a one-shot commitment for tonight only.
     */
    fun confirmFajrNfcLockdown(repeatDaily: Boolean = false): Boolean {
        val days: Set<Int> = if (repeatDaily) setOf(0, 1, 2, 3, 4, 5, 6) else emptySet()

        // Calculate next Fajr time to store target timestamp for automatic lock expiration
        val now = Date()
        val tzOffset = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0
        val (effLat, effLng) = getEffectiveCoordinates()
        val calculator = com.analoganchor.app.data.PrayerTimesCalculator(effLat, effLng, tzOffset)
        val timesToday = calculator.calculateTimes(now)
        val tomorrow = Date(now.time + 86400000)
        val timesTomorrow = calculator.calculateTimes(tomorrow)
        val nowCal = Calendar.getInstance()
        val nextFajr = if (nowCal.before(timesToday.fajr)) timesToday.fajr else timesTomorrow.fajr

        val editor = prefs.edit()
        editor.putBoolean("fajr_alarm_enabled", true)
        editor.putBoolean("fajr_alarm_ayah_constraint", true)
        editor.putBoolean("fajr_alarm_nfc_locked", true)
        editor.putString("fajr_alarm_days", days.joinToString(","))
        editor.putLong("fajr_alarm_target_time_ms", nextFajr.timeInMillis)
        editor.putInt("fajr_alarm_offset_minutes", -30)

        if (repeatDaily) {
            val repeatUntilMs = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L)
            editor.putLong("fajr_nfc_repeat_until_ms", repeatUntilMs)
        } else {
            editor.remove("fajr_nfc_repeat_until_ms")
        }
        editor.apply()

        _fajrAlarmEnabled.value = true
        _fajrAlarmAyahConstraint.value = true
        _fajrAlarmNfcLocked.value = true
        _fajrAlarmOffsetMinutes.value = -30
        _fajrAlarmDays.value = days
        scheduleFajrAlarm()
        postFajrActivatedNotification()
        return true
    }

    private fun postFajrActivatedNotification() {
        try {
            val nm = notificationManager()
            val channelId = "fajr_nfc_confirmation_channel"
            val vibrationPattern = longArrayOf(0, 350, 150, 450)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    if (isEnglish) "Fajr Alarm Activation" else "تأكيد تفعيل منبه الفجر",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = if (isEnglish) "Haptic confirmation when Fajr alarm is locked" else "تأكيد اهتزازي عند قفل منبه الفجر"
                    enableVibration(true)
                    this.vibrationPattern = vibrationPattern
                }
                nm.createNotificationChannel(channel)
            }

            val title = if (isEnglish) "🔒 Fajr Alarm ACTIVATED & LOCKED" else "🔒 تم تفعيل وقفل منبه الفجر"
            val text = if (isEnglish) "Alarm is set for tonight and cannot be undone." else "تم ضبط المنبه لليلة واحدة ولا يمكن إلغاؤه."

            val activityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("target_tab", "MOSQUE")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                9941,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(vibrationPattern)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notifySafely(9941, notification)
        } catch (e: Exception) {
            Log.w("AnchorTelemetry", "Failed to post Fajr activation notification: ${e.message}")
        }
    }

    private fun checkFajrLockExpiration() {
        val nowCal = Calendar.getInstance()
        val isFriday = nowCal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
        val currentHour = nowCal.get(Calendar.HOUR_OF_DAY)

        // Friday Afternoon Auto-Unlock (after 2:00 PM / 14:00)
        if (isFriday && currentHour >= 14 && _fajrAlarmNfcLocked.value) {
            prefs.edit()
                .putBoolean("fajr_alarm_nfc_locked", false)
                .remove("fajr_alarm_target_time_ms")
                .apply()
            _fajrAlarmNfcLocked.value = false
            return
        }

        if (_fajrAlarmNfcLocked.value) {
            val targetMs = prefs.getLong("fajr_alarm_target_time_ms", 0L)
            val nowMs = System.currentTimeMillis()

            // Expire lock ONLY when target timestamp has passed by 10+ mins (zero daytime early unlock)
            if (targetMs > 0L && nowMs > targetMs + (10 * 60 * 1000L)) {
                prefs.edit()
                    .putBoolean("fajr_alarm_nfc_locked", false)
                    .remove("fajr_alarm_target_time_ms")
                    .apply()
                _fajrAlarmNfcLocked.value = false
            }
        }
    }

    fun resetFajrNfcLock() {
        prefs.edit()
            .putBoolean("fajr_alarm_nfc_locked", false)
            .remove("fajr_alarm_target_time_ms")
            .remove("fajr_nfc_repeat_until_ms")
            .putInt("fajr_alarm_offset_minutes", 0)
            .apply()
        _fajrAlarmNfcLocked.value = false
        _fajrAlarmOffsetMinutes.value = 0
        scheduleFajrAlarm()
        Toast.makeText(context, if (isEnglish) "NFC Lockdown released. Standard alarm unlocked." else "تم تحرير قفل NFC. المنبه القياسي متاح الآن.", Toast.LENGTH_SHORT).show()
    }

    fun setFajrAlarmEnabled(enabled: Boolean, accountabilityPin: String? = null): Boolean {
        if (!enabled) {
            if (_fajrAlarmNfcLocked.value) {
                return false // Cannot disable while locked via NFC pre-commitment
            }
            if (!canChangeFajrEnforcement(accountabilityPin)) {
                return false // Requires accountability PIN only to disable
            }
        }

        val editor = prefs.edit()
        editor.putBoolean("fajr_alarm_enabled", enabled)
        if (enabled) {
            editor.putInt("fajr_alarm_offset_minutes", 0)
            _fajrAlarmOffsetMinutes.value = 0
        } else {
            editor.remove("fajr_alarm_days")
            editor.remove("fajr_nfc_repeat_until_ms")
            editor.remove("fajr_alarm_target_time_ms")
            editor.putBoolean("fajr_alarm_nfc_locked", false)
            _fajrAlarmDays.value = emptySet()
            _fajrAlarmNfcLocked.value = false
        }
        editor.apply()

        _fajrAlarmEnabled.value = enabled
        if (enabled) {
            val cal = Calendar.getInstance()
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek == Calendar.THURSDAY || dayOfWeek == Calendar.FRIDAY) {
                _showJumuahBoundConfirmationDialog.value = true
            }
            scheduleFajrAlarm()
        } else {
            cancelFajrAlarm()
        }
        return true
    }

    fun setFajrAlarmAyahConstraint(enabled: Boolean, accountabilityPin: String? = null): Boolean {
        if (!canChangeFajrEnforcement(accountabilityPin)) return false

        prefs.edit().putBoolean("fajr_alarm_ayah_constraint", enabled).apply()
        _fajrAlarmAyahConstraint.value = enabled
        return true
    }

    fun setFajrAlarmAccountabilityPinEnabled(enabled: Boolean, pin: String? = null): Boolean {
        val editor = prefs.edit()
        if (enabled) {
            if (pin != null && pin.trim().length == 4 && pin.trim().all { it.isDigit() }) {
                val hash = hashPin(pin.trim())
                editor.putString("fajr_accountability_pin_hash", hash)
                editor.putBoolean("fajr_alarm_accountability_pin_enabled", true)
                editor.apply()
                _fajrAccountabilityPinSet.value = true
                _fajrAlarmAccountabilityPinEnabled.value = true
                return true
            }
            return false
        } else {
            if (_fajrAlarmAccountabilityPinEnabled.value && !verifyFajrAccountabilityPin(pin.orEmpty())) {
                return false
            }
            editor.putBoolean("fajr_alarm_accountability_pin_enabled", false)
            editor.apply()
            _fajrAlarmAccountabilityPinEnabled.value = false
            return true
        }
    }

    fun verifyFajrAccountabilityPin(pin: String): Boolean {
        val hash = hashPin(pin.trim())
        val saved = prefs.getString("fajr_accountability_pin_hash", "") ?: ""
        return saved.isNotEmpty() && hash == saved
    }

    fun dismissFajrAlarm(): Boolean {
        prefs.edit()
            .putBoolean("fajr_alarm_ringing", false)
            .putBoolean("fajr_alarm_nfc_locked", false)
            .apply()
        _isFajrAlarmRinging.value = false
        _fajrAlarmNfcLocked.value = false
        com.analoganchor.app.service.FajrAlarmReceiver.stopServiceAndCancelPenalty(getApplication<Application>().applicationContext)

        val repeatUntilMs = prefs.getLong("fajr_nfc_repeat_until_ms", 0L)
        val isExpired7Days = repeatUntilMs > 0L && System.currentTimeMillis() >= repeatUntilMs

        if (isExpired7Days) {
            prefs.edit()
                .remove("fajr_alarm_days")
                .putBoolean("fajr_alarm_enabled", false)
                .remove("fajr_nfc_repeat_until_ms")
                .apply()
            _fajrAlarmDays.value = emptySet()
            _fajrAlarmEnabled.value = false
        } else if (_fajrAlarmDays.value.isNotEmpty() || _fajrAlarmEnabled.value) {
            scheduleFajrAlarm()
        }

        verifyFajrPresence()
        return markFajrWakeProof()
    }

    fun acknowledgeMissedFajrAlarm() {
        prefs.edit().putBoolean("fajr_alarm_missed", false).apply()
        _isFajrAlarmMissed.value = false
        com.analoganchor.app.service.FajrAlarmReceiver.cancelMissedNotification(getApplication())
    }

    fun acknowledgeMissedJumuahAlarm() {
        prefs.edit().putBoolean("jumuah_alarm_missed", false).apply()
        _isJumuahAlarmMissed.value = false
        com.analoganchor.app.service.FajrAlarmReceiver.cancelMissedJumuahNotification(getApplication())
    }

    fun dismissJumuahAlarm(): Boolean {
        prefs.edit()
            .putBoolean("jumuah_alarm_ringing", false)
            .apply()
        _isJumuahAlarmRinging.value = false
        com.analoganchor.app.service.FajrAlarmReceiver.stopServiceAndCancelPenalty(getApplication<Application>().applicationContext)

        val todayStr = formatDateKey(System.currentTimeMillis())
        if (!prefs.getBoolean("jumuah_attended_$todayStr", false)) {
            prefs.edit().putBoolean("jumuah_attended_$todayStr", true).apply()
            addPoints(40, "Friday Jumu'ah Attendance", "jumuah")
        }
        return true
    }

    fun silenceCurrentJumuahAlarm() {
        com.analoganchor.app.service.FajrAlarmReceiver.stopAlarmAudio(getApplication<Application>().applicationContext)
    }

    fun getTodayFajrCutoffMs(): Long {
        val tzOffset = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0
        val (effLat, effLng) = getEffectiveCoordinates()
        val calculator = com.analoganchor.app.data.PrayerTimesCalculator(effLat, effLng, tzOffset)
        val nowCal = Calendar.getInstance()
        val times = calculator.calculateTimes(nowCal.time)
        return times.sunrise.timeInMillis.coerceAtLeast(times.fajr.timeInMillis + (30 * 60 * 1000L))
    }

    fun isPastTodayFajrWindow(): Boolean {
        return System.currentTimeMillis() > getTodayFajrCutoffMs()
    }

    fun validateAndExpireFajrAlarmIfPastSunrise(): Boolean {
        val isRinging = prefs.getBoolean("fajr_alarm_ringing", false)
        if (isRinging && isPastTodayFajrWindow()) {
            android.util.Log.w("AnchorViewModel", "Fajr alarm was ringing but current time is past sunrise. Expiring alarm and applying penalty.")
            prefs.edit().putBoolean("fajr_alarm_ringing", false).apply()
            _isFajrAlarmRinging.value = false
            com.analoganchor.app.service.FajrAlarmReceiver.stopServiceAndCancelPenalty(getApplication())
            checkFajrMissed()
            return true
        }
        return false
    }

    fun checkFajrMissed() {
        // Do not assess missed prayer penalties if user has not completed onboarding
        val isOnboarded = prefs.getBoolean("is_onboarded", false)
        if (!isOnboarded) return

        val fajrEnabled = prefs.getBoolean("fajr_alarm_enabled", false)
        val isNfcLocked = prefs.getBoolean("fajr_alarm_nfc_locked", false)
        val repeatUntilMs = prefs.getLong("fajr_nfc_repeat_until_ms", 0L)
        val isWeeklyRepeatActive = repeatUntilMs > 0L && System.currentTimeMillis() < repeatUntilMs
        if (!fajrEnabled && !isNfcLocked && !isWeeklyRepeatActive) return

        var installTimestamp = prefs.getLong("app_install_timestamp", 0L)
        if (installTimestamp == 0L) {
            installTimestamp = System.currentTimeMillis()
            prefs.edit().putLong("app_install_timestamp", installTimestamp).apply()
        }

        val tzOffset = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0
        val (effLat, effLng) = getEffectiveCoordinates()
        val calculator = com.analoganchor.app.data.PrayerTimesCalculator(effLat, effLng, tzOffset)

        val nowCal = Calendar.getInstance()
        val nowMs = nowCal.timeInMillis
        var newlyMissedFajrCount = 0

        // Check past days (up to 7 days, but NEVER before the app was installed)
        for (dayOffset in 6 downTo 0) {
            val checkCal = (nowCal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -dayOffset)
            }
            val dateKey = formatDateKey(checkCal.timeInMillis)
            val times = calculator.calculateTimes(checkCal.time)
            val fajrCutoffMs = times.sunrise.timeInMillis.coerceAtLeast(times.fajr.timeInMillis + (30 * 60 * 1000L))

            // Never penalize for Fajr if Fajr occurred before the app was installed/onboarded
            if (times.fajr.timeInMillis < installTimestamp && nowMs - installTimestamp < 24 * 3600 * 1000L) {
                prefs.edit().putBoolean("fajr_penalized_$dateKey", true).apply()
                continue
            }

            if (nowMs > fajrCutoffMs) {
                val isAttendedOrVerified = prefs.getBoolean("fajr_verified_$dateKey", false) ||
                        prefs.getBoolean("fajr_wake_verified_$dateKey", false)
                val isPenalized = prefs.getBoolean("fajr_penalized_$dateKey", false)

                if (!isAttendedOrVerified && !isPenalized) {
                    prefs.edit().putBoolean("fajr_penalized_$dateKey", true).apply()
                    newlyMissedFajrCount++
                    deductPoints(10, "Missed Fajr Prayer ($dateKey)", "penalty")
                }
            }
        }

        if (newlyMissedFajrCount > 0) {
            prefs.edit()
                .putBoolean("fajr_alarm_ringing", false)
                .putBoolean("fajr_alarm_missed", true)
                .putLong("fajr_alarm_missed_time", System.currentTimeMillis())
                .putInt("mosque_streak", 0)
                .apply()
            _isFajrAlarmRinging.value = false
            _isFajrAlarmMissed.value = true
            _mosqueComplianceStreak.value = 0
            recalculateHabitCompliance()
            com.analoganchor.app.service.FajrAlarmReceiver.stopServiceAndCancelPenalty(getApplication())
            com.analoganchor.app.service.FajrAlarmReceiver.showMissedFajrNotification(getApplication(), newlyMissedFajrCount * 10)
        }
    }

    fun checkFridayJumuahMissedAt3PM() {
        val nowCal = Calendar.getInstance()
        val isFriday = nowCal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
        val hour = nowCal.get(Calendar.HOUR_OF_DAY)
        if (isFriday && hour >= 15) {
            val todayStr = formatDateKey(nowCal.timeInMillis)
            val attended = prefs.getBoolean("jumuah_attended_$todayStr", false)
            val penalized = prefs.getBoolean("jumuah_penalized_$todayStr", false)
            if (!attended && !penalized) {
                prefs.edit()
                    .putBoolean("jumuah_penalized_$todayStr", true)
                    .putBoolean("jumuah_alarm_missed", true)
                    .apply()
                _isJumuahAlarmMissed.value = true
                deductPoints(40, "Missed Friday Jumu'ah Prayer", "penalty")
                com.analoganchor.app.service.FajrAlarmReceiver.showMissedJumuahNotification(getApplication())
            }
        }
    }

    fun checkWeeklyFridayReset() {
        val nowCal = Calendar.getInstance()
        val isFriday = nowCal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
        if (isFriday) {
            val todayStr = formatDateKey(nowCal.timeInMillis)
            val lastResetDate = prefs.getString("last_friday_reset_date", "") ?: ""
            if (lastResetDate != todayStr) {
                val currentReserve = prefs.getInt("reserve_points", 0)
                val currentBanked = prefs.getInt("banked_stake_points", 0)
                val newBanked = currentBanked + Math.max(0, currentReserve)
                prefs.edit()
                    .putInt("banked_stake_points", newBanked)
                    .putInt("reserve_points", 0)
                    .putInt("protocol_streak", 0)
                    .putInt("maghrib_retreat_streak", 0)
                    .putInt("mosque_streak", 0)
                    .putString("last_friday_reset_date", todayStr)
                    .apply()
                _reservePoints.value = 0
                _protocolComplianceStreak.value = 0
                _maghribRetreatStreak.value = 0
                _mosqueComplianceStreak.value = 0
                recalculateHabitCompliance()
                recalculateStakeStats()
                viewModelScope.launch {
                    repository.insertLog(
                        SessionLog(
                            action = "Weekly Reserve Reset (Friday Morning)",
                            pointsChange = 0,
                            status = "reset"
                        )
                    )
                }
            }
        }
    }

    fun updateJumuahCountdown() {
        val nowCal = Calendar.getInstance()
        val isFriday = nowCal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
        if (!isFriday) {
            _jumuahCountdownText.value = null
            return
        }

        val tzOffset = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0
        val (effLat, effLng) = getEffectiveCoordinates()
        val calculator = com.analoganchor.app.data.PrayerTimesCalculator(effLat, effLng, tzOffset)
        val todayTimes = calculator.calculateTimes(nowCal.time)
        val dhuhrCal = todayTimes.dhuhr

        val diffMs = dhuhrCal.timeInMillis - nowCal.timeInMillis
        if (diffMs > 0 && diffMs <= 12 * 60 * 60 * 1000L) {
            val totalSec = diffMs / 1000
            val hours = totalSec / 3600
            val mins = (totalSec % 3600) / 60
            val secs = totalSec % 60
            val formatted = String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
            _jumuahCountdownText.value = formatted
        } else {
            _jumuahCountdownText.value = null
        }
    }

    private fun startFajrWakeStepVerification(): Boolean {
        val todayStr = formatDateKey(System.currentTimeMillis())
        if (prefs.getBoolean("fajr_wake_verified_$todayStr", false)) {
            _fajrWakeVerified.value = true
            _fajrWakeStepVerificationActive.value = false
            _fajrWakeStepsAfterDismiss.value = FAJR_WAKE_STEP_GOAL
            return false
        }

        fajrWakeStepBaseline = lastStepCounterValue ?: -1f
        prefs.edit()
            .putBoolean("fajr_wake_step_active_$todayStr", true)
            .putInt("fajr_wake_steps_$todayStr", 0)
            .putFloat("fajr_wake_step_baseline_$todayStr", fajrWakeStepBaseline)
            .apply()
        _fajrWakeStepVerificationActive.value = true
        _fajrWakeStepsAfterDismiss.value = 0
        registerStepCounterSensor()
        showFajrWakeMovementNotification()
        return true
    }

    private fun markFajrWakeProof(): Boolean {
        val todayStr = formatDateKey(System.currentTimeMillis())
        val key = "fajr_wake_verified_$todayStr"
        if (prefs.getBoolean(key, false)) {
            _fajrWakeVerified.value = true
            return false
        }

        prefs.edit()
            .putBoolean(key, true)
            .putBoolean("fajr_verified_$todayStr", true)
            .putBoolean("prayer_verified_fajr_$todayStr", true)
            .putBoolean("fajr_wake_step_active_$todayStr", false)
            .remove("fajr_wake_step_baseline_$todayStr")
            .putInt("fajr_wake_steps_$todayStr", FAJR_WAKE_STEP_GOAL)
            .apply()
        _fajrWakeVerified.value = true
        _fajrVerified.value = true
        _fajrWakeStepVerificationActive.value = false
        _fajrWakeStepsAfterDismiss.value = FAJR_WAKE_STEP_GOAL
        fajrWakeStepBaseline = -1f
        addPoints(10, "Fajr Wake Proof", "sleep")
        showFajrWakeVerifiedNotification()
        recalculateWeeklyPillarStreaks()
        recalculateWeeklyMosqueStats()
        recalculateCovenantStats()
        recalculateTodayPrayerStatus()
        return true
    }

    private fun updateFajrWakeStepVerification(stepsValue: Float) {
        if (!_fajrWakeStepVerificationActive.value || _fajrWakeVerified.value) return

        val todayStr = formatDateKey(System.currentTimeMillis())
        if (!prefs.getBoolean("fajr_wake_step_active_$todayStr", false)) {
            _fajrWakeStepVerificationActive.value = false
            _fajrWakeStepsAfterDismiss.value = 0
            return
        }

        if (fajrWakeStepBaseline < 0f) {
            fajrWakeStepBaseline = stepsValue
            prefs.edit().putFloat("fajr_wake_step_baseline_$todayStr", fajrWakeStepBaseline).apply()
            return
        }

        val walkedSteps = maxOf(
            _fajrWakeStepsAfterDismiss.value,
            (stepsValue - fajrWakeStepBaseline).toInt()
        ).coerceIn(0, FAJR_WAKE_STEP_GOAL)
        if (walkedSteps != _fajrWakeStepsAfterDismiss.value) {
            _fajrWakeStepsAfterDismiss.value = walkedSteps
            prefs.edit().putInt("fajr_wake_steps_$todayStr", walkedSteps).apply()
        }

        if (walkedSteps >= FAJR_WAKE_STEP_GOAL) {
            markFajrWakeProof()
        }
    }

    private fun recordFajrWakeDetectorStep() {
        if (!_fajrWakeStepVerificationActive.value || _fajrWakeVerified.value) return

        val todayStr = formatDateKey(System.currentTimeMillis())
        if (!prefs.getBoolean("fajr_wake_step_active_$todayStr", false)) {
            _fajrWakeStepVerificationActive.value = false
            _fajrWakeStepsAfterDismiss.value = 0
            return
        }

        val walkedSteps = (_fajrWakeStepsAfterDismiss.value + 1).coerceIn(0, FAJR_WAKE_STEP_GOAL)
        _fajrWakeStepsAfterDismiss.value = walkedSteps
        prefs.edit().putInt("fajr_wake_steps_$todayStr", walkedSteps).apply()

        if (walkedSteps >= FAJR_WAKE_STEP_GOAL) {
            markFajrWakeProof()
        }
    }

    fun silenceCurrentFajrAlarm() {
        previewRingtone?.stop()
        com.analoganchor.app.service.FajrAlarmReceiver.stopAlarmAudio(getApplication<Application>().applicationContext)
    }

    fun setFajrAlarmRingtone(uri: String, label: String) {
        val cleanLabel = label.ifBlank { "Custom ringtone" }
        prefs.edit()
            .putString("fajr_alarm_ringtone_uri", uri)
            .putString("fajr_alarm_ringtone_label", cleanLabel)
            .apply()
        _fajrAlarmRingtoneUri.value = uri
        _fajrAlarmRingtoneLabel.value = cleanLabel
        previewFajrAlarmRingtone()
    }

    fun clearFajrAlarmRingtone() {
        prefs.edit()
            .remove("fajr_alarm_ringtone_uri")
            .remove("fajr_alarm_ringtone_label")
            .apply()
        _fajrAlarmRingtoneUri.value = null
        _fajrAlarmRingtoneLabel.value = "Built-in Analog tone"
        previewFajrAlarmRingtone()
    }

    fun previewFajrAlarmRingtone() {
        try {
            val alarmUri = _fajrAlarmRingtoneUri.value
                ?.takeIf { it.isNotBlank() }
                ?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
            previewRingtone?.stop()
            val ringtone = RingtoneManager.getRingtone(context, alarmUri) ?: return
            previewRingtone = ringtone
            ringtone.play()

            Handler(Looper.getMainLooper()).postDelayed({
                if (previewRingtone == ringtone) {
                    ringtone.stop()
                }
            }, 2500)
        } catch (e: Exception) {
            Log.e("AnchorViewModel", "Failed to preview Fajr ringtone: ${e.message}")
            Toast.makeText(context, "Could not preview this ringtone.", Toast.LENGTH_SHORT).show()
        }
    }

    fun sealTheAnchor() {
        prefs.edit().putBoolean("is_sealed", true).apply()
        _isSealed.value = true
    }

    /**
     * Called whenever an NFC tag is scanned, from foreground (ReaderMode) or cold launch.
     * @param tagId  The hex ID of the tag.
     * @param mimeType  The MIME type from the NDEF record, if present. Used for reliable routing.
     *                  Null for unwritten tags or TECH_DISCOVERED intents.
     */
    fun onNfcTagScanned(tagId: String, mimeType: String? = null) {
        _nfcScannedEvent.tryEmit(tagId)

        val activeTab = _currentActiveTab.value
        val registeredFajrTag = _fajrNfcTagId.value
        val registeredAnchorTag = _nfcTagId.value

        val isFajrTag = (registeredFajrTag.isNotEmpty() && tagId.equals(registeredFajrTag, ignoreCase = true)) ||
            mimeType == "application/vnd.com.analoganchor.app.fajr" ||
            mimeType == "application/vnd.com.analoganchor.app.analoganchor.fajr"

        val isAnchorTag = (registeredAnchorTag.isNotEmpty() && tagId.equals(registeredAnchorTag, ignoreCase = true)) ||
            mimeType == "application/vnd.com.analoganchor.app.anchor" ||
            mimeType == "application/vnd.com.analoganchor.app.analoganchor.anchor"

        // 1. FAJR ALARM NFC TAG: Exclusively activates & locks Fajr alarm for tonight
        if (isFajrTag) {
            confirmFajrNfcLockdown(repeatDaily = false)
            _fajrEraserNfcChannel.trySend(Unit)
            _navigateToTabChannel.trySend("MOSQUE")
            return
        }

        // 2. MAIN ANCHOR NFC TAG: Controls Anchor Sessions & Protected App Access
        if (isAnchorTag) {
            // Check if morning Tadabbur alarm is active or ringing
            val isTadabburActive = _isTadabburAlarmRinging.value ||
                prefs.getBoolean("tadabbur_alarm_ringing", false)

            if (isTadabburActive) {
                stopTadabburAlarm()
                recordTadabburCheckin()
                _navigateToTabChannel.trySend("ANCHOR") // MUST stay on ANCHOR tab!
                startFocusSession(5)
                return
            }

            // Unlock the 15-minute policy edit window whenever the physical Main Anchor tag is scanned
            viewModelScope.launch {
                preferencesRepository.setPolicyEditWindowEndTimestamp(System.currentTimeMillis() + 15 * 60 * 1000L)
            }

            // If a Pomodoro session is active, or if the user is actively foregrounded in the ANCHOR tab:
            // remain on the ANCHOR tab so Tap 1 (start) and Tap 2 (verify return) proceed uninterrupted
            if (_isSessionActive.value || (isAppInForeground && activeTab == "ANCHOR")) {
                return
            }

            // When tapped from background, lock screen, or outside the Anchor tab, route to DAY_GUARD tab
            _navigateToTabChannel.trySend("DAY_GUARD")
            return
        }
    }

    fun registerNfcTag(tagId: String) {
        prefs.edit().putString("nfc_tag_id", tagId).apply()
        _nfcTagId.value = tagId
        viewModelScope.launch {
            preferencesRepository.setAnchorNfcTagId(tagId)
        }
    }

    fun registerFajrNfcTag(tagId: String) {
        prefs.edit().putString("fajr_nfc_tag_id", tagId).apply()
        _fajrNfcTagId.value = tagId
    }

    fun saveSanctuary(type: String, lat: Float, lng: Float) {
        if (!isUsableCoordinate(lat.toDouble(), lng.toDouble())) {
            saveSanctuaryMode(type)
            return
        }

        prefs.edit().apply {
            putString("sanctuary_type", type)
            putFloat("sanctuary_lat", lat)
            putFloat("sanctuary_lng", lng)
        }.apply()
        _sanctuaryType.value = type
        _sanctuaryLatitude.value = lat.toDouble()
        _sanctuaryLongitude.value = lng.toDouble()
    }

    fun saveSanctuaryMode(type: String) {
        val editor = prefs.edit().putString("sanctuary_type", type)
        if (!isUsableCoordinate(_sanctuaryLatitude.value, _sanctuaryLongitude.value) &&
            isUsableCoordinate(_userLatitude.value, _userLongitude.value)
        ) {
            editor
                .putFloat("sanctuary_lat", _userLatitude.value.toFloat())
                .putFloat("sanctuary_lng", _userLongitude.value.toFloat())
            _sanctuaryLatitude.value = _userLatitude.value
            _sanctuaryLongitude.value = _userLongitude.value
        }
        editor.apply()
        _sanctuaryType.value = type
        requestRealLocation()
    }

    fun saveCurrentLocationAsSanctuary(type: String, name: String, address: String) {
        val lat = _userLatitude.value
        val lng = _userLongitude.value
        if (!isUsableCoordinate(lat, lng)) {
            saveSanctuaryMode(type)
            return
        }

        if (type == "mosque") {
            saveDesignatedMosque(name, address, lat.toFloat(), lng.toFloat(), type)
        } else {
            saveSanctuary(type, lat.toFloat(), lng.toFloat())
        }
    }

    fun saveDesignatedMosque(
        name: String,
        address: String,
        lat: Float,
        lng: Float,
        type: String = "mosque"
    ) {
        if (!isUsableCoordinate(lat.toDouble(), lng.toDouble())) return

        prefs.edit().apply {
            putString("sanctuary_type", type)
            putString("mosque_name", name)
            putString("mosque_address", address)
            putFloat("sanctuary_lat", lat)
            putFloat("sanctuary_lng", lng)
        }.apply()
        _sanctuaryType.value = type
        _mosqueName.value = name
        _mosqueAddress.value = address
        _sanctuaryLatitude.value = lat.toDouble()
        _sanctuaryLongitude.value = lng.toDouble()

        // Recalculate geographical targets
        requestRealLocation()
    }

    fun saveOutdoorSanctuary(
        name: String,
        address: String,
        lat: Float,
        lng: Float,
        type: String = "outdoor",
        isEnglish: Boolean = true
    ): Pair<Boolean, String> {
        if (!isUsableCoordinate(lat.toDouble(), lng.toDouble())) {
            return Pair(false, if (isEnglish) "Invalid GPS coordinates." else "إحداثيات غير صالحة.")
        }

        // Rule #1: Outdoor destination must be outside Home Base geofence (> 150m)
        val homeLat = _homeLatitude.value
        val homeLng = _homeLongitude.value
        if (_homeBaseSaved.value && isUsableCoordinate(homeLat, homeLng)) {
            val distHome = calculateDistance(lat.toDouble(), lng.toDouble(), homeLat, homeLng)
            if (distHome < 150.0) {
                return Pair(
                    false,
                    if (isEnglish) "❌ Outdoor spot must be outside Home Base! (${distHome.toInt()}m from home, required > 150m)"
                    else "❌ يجب أن تكون وجهة النشاط الخارجي خارج نطاق المنزل! (${distHome.toInt()}م عن المنزل، المطلوب > 150م)"
                )
            }
        }

        val resolvedType = if (_sanctuaryType.value == "mosque" || _sanctuaryType.value == "both") "both" else "outdoor"

        prefs.edit().apply {
            putString("sanctuary_type", resolvedType)
            putString("outdoor_sanctuary_name", name)
            putString("outdoor_sanctuary_address", address)
            putFloat("outdoor_sanctuary_lat", lat)
            putFloat("outdoor_sanctuary_lng", lng)
            // Also update primary sanctuary coordinates if outdoor or both
            putFloat("sanctuary_lat", lat)
            putFloat("sanctuary_lng", lng)
        }.apply()

        _sanctuaryType.value = resolvedType
        _outdoorSanctuaryName.value = name
        _outdoorSanctuaryAddress.value = address
        _outdoorSanctuaryLat.value = lat.toDouble()
        _outdoorSanctuaryLng.value = lng.toDouble()

        updateOutdoorProximity()
        requestRealLocation()

        return Pair(
            true,
            if (isEnglish) "✓ Outdoor destination pinned: $name" else "✓ تم تثبيت وجهة النشاط الخارجي: $name"
        )
    }

    fun isUserAtAnySanctuaryMosque(userLat: Double, userLng: Double, thresholdMeters: Double = 200.0): Pair<Boolean, String> {
        val designatedLat = _sanctuaryLatitude.value
        val designatedLng = _sanctuaryLongitude.value
        if (isUsableCoordinate(designatedLat, designatedLng) && calculateDistance(userLat, userLng, designatedLat, designatedLng) <= thresholdMeters) {
            return Pair(true, _mosqueName.value.ifEmpty { "Designated Mosque" })
        }
        val mosquesToCheck = if (_lockedMosques.value.isNotEmpty()) _lockedMosques.value else FOUNDER_MOSQUES
        for (m in mosquesToCheck) {
            if (calculateDistance(userLat, userLng, m.lat, m.lng) <= thresholdMeters) {
                return Pair(true, m.name)
            }
        }
        return Pair(false, "")
    }

    fun isUserAtAnyOutdoorSanctuary(userLat: Double, userLng: Double, thresholdMeters: Double = 400.0): Pair<Boolean, String> {
        val designatedLat = _outdoorSanctuaryLat.value
        val designatedLng = _outdoorSanctuaryLng.value
        if (isUsableCoordinate(designatedLat, designatedLng) && calculateDistance(userLat, userLng, designatedLat, designatedLng) <= thresholdMeters) {
            return Pair(true, _outdoorSanctuaryName.value.ifEmpty { "Designated Outdoor Destination" })
        }
        val spotsToCheck = if (_lockedOutdoorSpots.value.isNotEmpty()) _lockedOutdoorSpots.value else FOUNDER_OUTDOOR_SPOTS
        for (o in spotsToCheck) {
            if (calculateDistance(userLat, userLng, o.lat, o.lng) <= thresholdMeters) {
                return Pair(true, o.name)
            }
        }
        return Pair(false, "")
    }

    fun loadLockedSpots() {
        val mosques = mutableListOf<FounderSanctuary>()
        for (i in 0 until 3) {
            val name = prefs.getString("locked_mosque_name_$i", null)
            val addr = prefs.getString("locked_mosque_addr_$i", null)
            val lat = prefs.getFloat("locked_mosque_lat_$i", 0f).toDouble()
            val lng = prefs.getFloat("locked_mosque_lng_$i", 0f).toDouble()
            if (!name.isNullOrBlank() && isUsableCoordinate(lat, lng)) {
                mosques.add(FounderSanctuary("mosque_${i + 1}", name, addr ?: "Designated Mosque", lat, lng, "mosque"))
            } else {
                val default = FOUNDER_MOSQUES.getOrNull(i)
                if (default != null) mosques.add(default)
            }
        }
        _lockedMosques.value = mosques

        val spots = mutableListOf<FounderSanctuary>()
        for (i in 0 until 3) {
            val name = prefs.getString("locked_outdoor_name_$i", null)
            val addr = prefs.getString("locked_outdoor_addr_$i", null)
            val lat = prefs.getFloat("locked_outdoor_lat_$i", 0f).toDouble()
            val lng = prefs.getFloat("locked_outdoor_lng_$i", 0f).toDouble()
            if (!name.isNullOrBlank() && isUsableCoordinate(lat, lng)) {
                spots.add(FounderSanctuary("outdoor_${i + 1}", name, addr ?: "Outdoor Hub", lat, lng, "outdoor"))
            } else {
                val default = FOUNDER_OUTDOOR_SPOTS.getOrNull(i)
                if (default != null) spots.add(default)
            }
        }
        _lockedOutdoorSpots.value = spots
    }

    fun lockdownMosque(slotIndex: Int, name: String, address: String, lat: Double, lng: Double): Pair<Boolean, String> {
        if (!isUsableCoordinate(lat, lng)) {
            return Pair(false, "Invalid coordinates")
        }
        val cleanName = name.trim().ifBlank { "Mosque ${slotIndex + 1}" }
        val cleanAddr = address.trim().ifBlank { "Bahrain Sanctuary Zone" }
        prefs.edit()
            .putString("locked_mosque_name_$slotIndex", cleanName)
            .putString("locked_mosque_addr_$slotIndex", cleanAddr)
            .putFloat("locked_mosque_lat_$slotIndex", lat.toFloat())
            .putFloat("locked_mosque_lng_$slotIndex", lng.toFloat())
            .apply()
        loadLockedSpots()
        return Pair(true, "✓ Locked Mosque #$slotIndex: $cleanName")
    }

    fun lockdownOutdoorSpot(slotIndex: Int, name: String, address: String, lat: Double, lng: Double): Pair<Boolean, String> {
        if (!isUsableCoordinate(lat, lng)) {
            return Pair(false, "Invalid coordinates")
        }
        val cleanName = name.trim().ifBlank { "Outdoor Hub ${slotIndex + 1}" }
        val cleanAddr = address.trim().ifBlank { "Bahrain Outdoor Destination" }
        prefs.edit()
            .putString("locked_outdoor_name_$slotIndex", cleanName)
            .putString("locked_outdoor_addr_$slotIndex", cleanAddr)
            .putFloat("locked_outdoor_lat_$slotIndex", lat.toFloat())
            .putFloat("locked_outdoor_lng_$slotIndex", lng.toFloat())
            .apply()
        loadLockedSpots()
        return Pair(true, "✓ Locked Outdoor Spot #$slotIndex: $cleanName")
    }

    fun updateOutdoorProximity() {
        val userLat = _userLatitude.value
        val userLng = _userLongitude.value
        val homeLat = _homeLatitude.value
        val homeLng = _homeLongitude.value
        val outdoorLat = _outdoorSanctuaryLat.value
        val outdoorLng = _outdoorSanctuaryLng.value

        if (isUsableCoordinate(userLat, userLng) && isUsableCoordinate(homeLat, homeLng)) {
            val distHome = calculateDistance(userLat, userLng, homeLat, homeLng)
            _isOutsideHomeBase.value = distHome >= 200.0
        } else {
            _isOutsideHomeBase.value = false
        }

        if (isUsableCoordinate(userLat, userLng) && isUsableCoordinate(outdoorLat, outdoorLng)) {
            val distOutdoor = calculateDistance(userLat, userLng, outdoorLat, outdoorLng)
            _isAtOutdoorSanctuary.value = distOutdoor <= 400.0
        } else {
            _isAtOutdoorSanctuary.value = false
        }
    }

    fun recordSoloOutdoorCompletion(durationMinutes: Int = 30) {
        val currentSolo = _outdoorSoloCountThisWeek.value + 1
        _outdoorSoloCountThisWeek.value = currentSolo
        prefs.edit().putInt("outdoor_solo_count_this_week", currentSolo).apply()
        addPoints(25, "Completed Solo Meshwar Al-Sakeenah ($durationMinutes min)", "outdoor_solo", duration = durationMinutes)
        recalculateCovenantStats()
    }

    fun recordPartnerOutdoorCompletion(
        durationMinutes: Int = 60,
        partnerName: String = "Anchor Partner",
        points: Int = 40,
        isGroup: Boolean = false
    ) {
        val currentPartner = _outdoorPartnerCountThisWeek.value + 1
        _outdoorPartnerCountThisWeek.value = currentPartner
        prefs.edit().putInt("outdoor_partner_count_this_week", currentPartner).apply()
        val actionLabel = if (isGroup) {
            "Completed Group Sanctuary Circle with $partnerName ($durationMinutes min, +$points PTS)"
        } else {
            "Completed Partner Meshwar Al-Sakeenah with $partnerName ($durationMinutes min, +$points PTS)"
        }
        addPoints(points, actionLabel, "outdoor_partner", duration = durationMinutes)
        recalculateCovenantStats()
    }

    fun isOutdoorWeeklyQuotaMet(): Boolean {
        return _outdoorPartnerCountThisWeek.value >= 1 || _outdoorSoloCountThisWeek.value >= 2
    }

    data class ActivePrayerWindow(
        val prayerKey: String,
        val displayName: String,
        val displayNameArabic: String,
        val startTimeFormatted: String,
        val endTimeFormatted: String,
        val restoreTimeFormatted: String,
        val isActiveNow: Boolean,
        val minutesRemainingInWindow: Int,
        val adhanTimeMs: Long,
        val iqamahTimeMs: Long,
        val restoreTimeMs: Long,
        val totalDndMinutes: Int,
        val isInitiativeOpen: Boolean,
        val isPrayerCommenced: Boolean,
        val isAlreadyVerified: Boolean
    )

    fun getCurrentActivePrayerWindow(): ActivePrayerWindow? {
        val tzOffset = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0
        val (effLat, effLng) = getEffectiveCoordinates()
        val calculator = PrayerTimesCalculator(effLat, effLng, tzOffset)
        val now = java.util.Date()
        val timesToday = calculator.calculateTimes(now)
        val nowCal = java.util.Calendar.getInstance().apply { time = now }
        val nowMs = nowCal.timeInMillis

        data class PrayerCandidate(
            val key: String,
            val nameEn: String,
            val nameAr: String,
            val adhanCal: java.util.Calendar,
            val bufferLeadMinutes: Int,
            val iqamahDelayMinutes: Int,
            val prayerDurationMinutes: Int
        )
        val isFridayToday = nowCal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.FRIDAY

        val candidates = listOf(
            PrayerCandidate("fajr", "Fajr", "الفجر", timesToday.fajr, 0, 20, 5),
            PrayerCandidate("dhuhr", if (isFridayToday) "Jumu'ah" else "Dhuhr", if (isFridayToday) "الجمعة" else "الظهر", timesToday.dhuhr, 5, 15, if (isFridayToday) 10 else 5),
            PrayerCandidate("asr", "Asr", "العصر", timesToday.asr, 5, 20, 15),
            PrayerCandidate("maghrib", "Maghrib", "المغرب", timesToday.maghrib, 5, 15, 5),
            PrayerCandidate("isha", "Isha", "العشاء", timesToday.isha, 0, 15, 5)
        )

        val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())

        for (c in candidates) {
            val adhanMs = c.adhanCal.timeInMillis
            val bufferStartMs = adhanMs - (c.bufferLeadMinutes * 60 * 1000L)
            val iqamahMs = adhanMs + (c.iqamahDelayMinutes * 60 * 1000L)
            val restoreMs = iqamahMs + (c.prayerDurationMinutes * 60 * 1000L)

            if (nowMs in bufferStartMs until restoreMs) {
                val isInitiativeOpen = nowMs < iqamahMs
                val isPrayerCommenced = nowMs >= iqamahMs
                val minsUntilIqamah = if (isInitiativeOpen) {
                    Math.ceil((iqamahMs - nowMs) / 60000.0).toInt().coerceAtLeast(1)
                } else 0
                val totalDndMins = Math.ceil((restoreMs - nowMs) / 60000.0).toInt().coerceAtLeast(1)
                val isClaimed = isPrayerAlreadyVerified(c.key)

                return ActivePrayerWindow(
                    prayerKey = c.key,
                    displayName = c.nameEn,
                    displayNameArabic = c.nameAr,
                    startTimeFormatted = sdf.format(c.adhanCal.time),
                    endTimeFormatted = sdf.format(java.util.Date(iqamahMs)),
                    restoreTimeFormatted = sdf.format(java.util.Date(restoreMs)),
                    isActiveNow = isInitiativeOpen,
                    minutesRemainingInWindow = minsUntilIqamah,
                    adhanTimeMs = adhanMs,
                    iqamahTimeMs = iqamahMs,
                    restoreTimeMs = restoreMs,
                    totalDndMinutes = totalDndMins,
                    isInitiativeOpen = isInitiativeOpen,
                    isPrayerCommenced = isPrayerCommenced,
                    isAlreadyVerified = isClaimed
                )
            }
        }
        return null
    }

    /**
     * Checks whether the current time is within the authentic Maghrib-to-Isha Retreat window.
     * Maghrib starts 15 minutes before Maghrib Adhan until 60 minutes after Isha.
     * Returns Pair(isActive, timeSpanString).
     */
    fun isMaghribRetreatWindowActive(): Pair<Boolean, String> {
        val tzOffset = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0
        val (effLat, effLng) = getEffectiveCoordinates()
        val calculator = PrayerTimesCalculator(effLat, effLng, tzOffset)
        val now = java.util.Date()
        val timesToday = calculator.calculateTimes(now)
        val nowMs = System.currentTimeMillis()

        val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        val maghribStr = sdf.format(timesToday.maghrib.time)
        val ishaStr = sdf.format(timesToday.isha.time)

        val windowStartMs = timesToday.maghrib.timeInMillis - (15 * 60 * 1000L)
        val windowEndMs = timesToday.isha.timeInMillis + (60 * 60 * 1000L)

        val isActive = nowMs in windowStartMs..windowEndMs
        return Pair(isActive, "$maghribStr – $ishaStr")
    }

    /**
     * Checks whether the current time is within the Fajr prayer window (Fajr until Sunrise + 30m).
     */
    fun isFajrWindowActive(): Pair<Boolean, String> {
        val tzOffset = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0
        val (effLat, effLng) = getEffectiveCoordinates()
        val calculator = PrayerTimesCalculator(effLat, effLng, tzOffset)
        val now = java.util.Date()
        val timesToday = calculator.calculateTimes(now)
        val nowMs = System.currentTimeMillis()

        val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        val fajrStr = sdf.format(timesToday.fajr.time)
        val sunriseStr = sdf.format(timesToday.sunrise.time)

        val windowStartMs = timesToday.fajr.timeInMillis - (15 * 60 * 1000L)
        val windowEndMs = timesToday.sunrise.timeInMillis + (30 * 60 * 1000L)

        val isActive = nowMs in windowStartMs..windowEndMs
        return Pair(isActive, "$fajrStr – $sunriseStr")
    }

    fun syncPrayerBufferState() {
        val isActive = prefs.getBoolean("prayer_buffer_active", false)
        val endMs = prefs.getLong("prayer_buffer_end_time_ms", 0L)
        val now = System.currentTimeMillis()
        if (isActive && endMs in 1..now) {
            PrayerDndRestoreReceiver.restorePrayerDndState(context, triggerHaptic = false, showNotification = true)
            _prayerBufferActive.value = false
            _prayerBufferEndTimeMs.value = 0L
            _prayerBufferStartTimeMs.value = 0L
            _prayerBufferPrayerKey.value = ""
            _prayerBufferPrayerNameEn.value = ""
            _prayerBufferPrayerNameAr.value = ""
        } else if (isActive) {
            _prayerBufferActive.value = true
            _prayerBufferEndTimeMs.value = endMs
            _prayerBufferStartTimeMs.value = prefs.getLong("prayer_buffer_start_time_ms", now)
            _prayerBufferPrayerKey.value = prefs.getString("prayer_buffer_key", "") ?: ""
            _prayerBufferPrayerNameEn.value = prefs.getString("prayer_buffer_name_en", "") ?: ""
            _prayerBufferPrayerNameAr.value = prefs.getString("prayer_buffer_name_ar", "") ?: ""
        } else {
            _prayerBufferActive.value = false
        }
    }

    fun activatePrayerBuffer(
        prayerKey: String,
        prayerNameEn: String,
        prayerNameAr: String,
        restoreTimeMs: Long,
        isEnglish: Boolean
    ): Pair<Boolean, String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasNotificationPolicyAccess()) {
            return Pair(
                false,
                if (isEnglish) "Do Not Disturb access is required to activate Soft Landing."
                else "يلزم منح إذن عدم الإزعاج لتفعيل الهبوط الناعم للصلاة."
            )
        }

        val todayKey = formatDateKey(System.currentTimeMillis())
        val prefKey = "prayer_verified_${prayerKey}_$todayKey"
        val alreadyClaimed = prefs.getBoolean(prefKey, false)

        if (alreadyClaimed) {
            return Pair(
                true,
                if (isEnglish) "✓ $prayerNameEn prayer presence is already verified for today."
                else "✓ تم توثيق حضور صلاة $prayerNameAr لهذا اليوم مسبقاً."
            )
        }

        val originalRinger = audioManager.ringerMode
        val originalFilter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                notificationManager().currentInterruptionFilter
            } catch (e: Exception) {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasNotificationPolicyAccess()) {
                notificationManager().setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
            }
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
        } catch (e: Exception) {
            Log.e("AnchorViewModel", "Failed to mute phone for prayer buffer: ${e.message}")
        }

        val nowMs = System.currentTimeMillis()

        prefs.edit()
            .putBoolean("prayer_buffer_active", true)
            .putLong("prayer_buffer_start_time_ms", nowMs)
            .putLong("prayer_buffer_end_time_ms", restoreTimeMs)
            .putString("prayer_buffer_key", prayerKey)
            .putString("prayer_buffer_name_en", prayerNameEn)
            .putString("prayer_buffer_name_ar", prayerNameAr)
            .putInt("prayer_buffer_initial_ringer", originalRinger)
            .putInt("prayer_buffer_initial_filter", originalFilter)
            .putBoolean(prefKey, true)
            .apply()

        _prayerBufferActive.value = true
        _prayerBufferStartTimeMs.value = nowMs
        _prayerBufferEndTimeMs.value = restoreTimeMs
        _prayerBufferPrayerKey.value = prayerKey
        _prayerBufferPrayerNameEn.value = prayerNameEn
        _prayerBufferPrayerNameAr.value = prayerNameAr

        // Dismiss pre-Adhan notification if user activated via in-app dialog
        try {
            notificationManager().cancel(PrayerDndRestoreReceiver.BUFFER_ALERT_NOTIFICATION_ID)
        } catch (_: Exception) {}

        // Streak updated without gamifying prayer with child points
        val currentStreak = prefs.getInt("mosque_streak", 0) + 1
        prefs.edit().putInt("mosque_streak", currentStreak).apply()
        _mosqueComplianceStreak.value = currentStreak
        recalculateHabitCompliance()
        recalculateWeeklyMosqueStats()
        recalculateCovenantStats()

        PrayerDndRestoreReceiver.scheduleRestoreAlarm(context, restoreTimeMs, prayerKey, prayerNameEn, prayerNameAr)
        PrayerDndRestoreReceiver.postStage2SanctuaryNotification(context, isEnglish, prayerNameEn, prayerNameAr)

        return Pair(
            true,
            if (isEnglish) "✓ $prayerNameEn Soft Landing Active — DND Enabled"
            else "✓ تم تفعيل الهبوط الناعم لصلاة $prayerNameAr ووضع عدم الإزعاج"
        )
    }

    fun emergencyCancelPrayerBuffer(isEnglish: Boolean): Pair<Boolean, String> {
        val now = System.currentTimeMillis()
        val start = _prayerBufferStartTimeMs.value.takeIf { it > 0 } ?: prefs.getLong("prayer_buffer_start_time_ms", now)
        val end = _prayerBufferEndTimeMs.value.takeIf { it > 0 } ?: prefs.getLong("prayer_buffer_end_time_ms", now)
        val prayerKey = _prayerBufferPrayerKey.value.ifEmpty { prefs.getString("prayer_buffer_key", "") ?: "" }
        val prayerNameEn = _prayerBufferPrayerNameEn.value.ifEmpty { prefs.getString("prayer_buffer_name_en", "Prayer") ?: "Prayer" }
        val prayerNameAr = _prayerBufferPrayerNameAr.value.ifEmpty { prefs.getString("prayer_buffer_name_ar", "الصلاة") ?: "الصلاة" }

        val totalDuration = (end - start).coerceAtLeast(1L)
        val elapsed = (now - start).coerceAtLeast(0L)
        val progress = (elapsed.toDouble() / totalDuration.toDouble()).coerceIn(0.0, 1.0)
        val passed80Percent = progress >= 0.80

        PrayerDndRestoreReceiver.cancelRestoreAlarm(context)
        PrayerDndRestoreReceiver.restorePrayerDndState(context, triggerHaptic = true, showNotification = false)

        _prayerBufferActive.value = false
        _prayerBufferStartTimeMs.value = 0L
        _prayerBufferEndTimeMs.value = 0L
        _prayerBufferPrayerKey.value = ""
        _prayerBufferPrayerNameEn.value = ""
        _prayerBufferPrayerNameAr.value = ""

        if (!passed80Percent) {
            if (prayerKey.isNotEmpty()) {
                val todayKey = formatDateKey(System.currentTimeMillis())
                prefs.edit().putBoolean("prayer_verified_${prayerKey}_$todayKey", false).apply()
            }
            recalculateHabitCompliance()
            recalculateWeeklyMosqueStats()
            recalculateCovenantStats()
            return Pair(
                true,
                if (isEnglish) "⚠️ Sound restored. Soft landing cancelled early (<80%)."
                else "⚠️ تم استعادة الصوت. تم إلغاء الهبوط الناعم مبكراً (أقل من 80%)."
            )
        } else {
            return Pair(
                true,
                if (isEnglish) "✓ Sound restored. Over 80% of prayer soft landing completed."
                else "✓ تم استعادة الصوت. تم إتمام أكثر من 80% من الهبوط الناعم للصلاة."
            )
        }
    }

    /**
     * Checks in at the mosque during an active prayer window.
     */
    fun checkInAtMosqueDuringPrayer(isEnglish: Boolean): Pair<Boolean, String> {
        val activeWindow = getCurrentActivePrayerWindow()
        if (activeWindow == null) {
            return Pair(
                false,
                if (isEnglish) "Prayer presence verification is active during the initiative window between Adhan and Iqamah."
                else "توثيق حضور الصلاة يكون متاحاً في نافذة المبادرة بين الأذان والإقامة."
            )
        }

        if (!activeWindow.isInitiativeOpen) {
            return Pair(
                false,
                if (isEnglish) "⏳ Prayer has commenced. The initiative window closed at Iqamah."
                else "⏳ أقيمت الصلاة. انتهت نافذة المبادرة عند موعد الإقامة."
            )
        }

        return activatePrayerBuffer(
            prayerKey = activeWindow.prayerKey,
            prayerNameEn = activeWindow.displayName,
            prayerNameAr = activeWindow.displayNameArabic,
            restoreTimeMs = activeWindow.restoreTimeMs,
            isEnglish = isEnglish
        )
    }

    fun isPrayerAlreadyVerified(prayerKey: String): Boolean {
        val todayKey = formatDateKey(System.currentTimeMillis())
        val prefKey = "prayer_verified_${prayerKey}_$todayKey"
        return prefs.getBoolean(prefKey, false)
    }

    fun triggerPrayerBufferPrompt(prayerKey: String, prayerNameEn: String, prayerNameAr: String) {
        _navigateToTabChannel.trySend("MOSQUE")
        val activeWindow = getCurrentActivePrayerWindow()
        val restoreMs = if (activeWindow != null && activeWindow.prayerKey == prayerKey) {
            activeWindow.restoreTimeMs
        } else {
            val fallbackMinutes = if (prayerKey == "asr") 35L else 20L
            System.currentTimeMillis() + (fallbackMinutes * 60 * 1000L)
        }
        _prayerBufferPrompt.value = PrayerBufferPromptState(
            prayerKey = prayerKey,
            prayerNameEn = prayerNameEn,
            prayerNameAr = prayerNameAr,
            restoreTimeMs = restoreMs
        )
    }

    fun dismissPrayerBufferPrompt() {
        _prayerBufferPrompt.value = null
    }

    fun confirmPrayerBufferFromPrompt(isEnglish: Boolean): Pair<Boolean, String> {
        val prompt = _prayerBufferPrompt.value ?: return Pair(false, "")
        _prayerBufferPrompt.value = null
        return activatePrayerBuffer(
            prayerKey = prompt.prayerKey,
            prayerNameEn = prompt.prayerNameEn,
            prayerNameAr = prompt.prayerNameAr,
            restoreTimeMs = prompt.restoreTimeMs,
            isEnglish = isEnglish
        )
    }

    fun navigateToTab(tab: String) {
        _navigateToTabChannel.trySend(tab)
    }

    fun startOrVerifySoloOutdoorRoutine(isEnglish: Boolean): String {
        return toggleSoloOutdoorRoutine(isEnglish)
    }

    fun toggleSoloOutdoorRoutine(isEnglish: Boolean): String {
        if (_isSoloRoutineActive.value) {
            _isSoloRoutineActive.value = false
            return if (isEnglish) "Solo Routine paused." else "تم إيقاف الروتين الفردي."
        } else {
            _isSoloRoutineActive.value = true
            refreshStepSensorRegistration()
            requestRealLocation()
            val dist = distanceFromHome()
            val steps = _stepsCount.value
            if (steps >= 2000) {
                recordSoloOutdoorCompletion(30)
                _stepsCount.value = Math.max(0, steps - 2000)
                return if (isEnglish) "✓ Solo Routine Completed! 2,000 steps verified (+25 PTS added to Reserve)."
                else "✓ تم إتمام الروتين الفردي بنجاح! تم توثيق 2,000 خطوة (+25 نقطة للأرصدة)."
            }
            return if (dist in 0.0..150.0) {
                if (isEnglish) "📍 You are at Home Base — step counting will activate once you head outside."
                else "📍 أنت في المنزل — سيتم احتساب الخطوات بمجرد خروجك."
            } else {
                if (isEnglish) "🏃 Motion tracking active away from Home Base (Goal: 2,000 steps)."
                else "🏃 تم تفعيل تتبع الحركة خارج المنزل (الهدف: 2,000 خطوة)."
            }
        }
    }

    fun deactivateSoloRoutine() {
        _isSoloRoutineActive.value = false
    }

    fun checkSoloRoutineStatus(isEnglish: Boolean): String {
        requestRealLocation()
        val dist = distanceFromHome()
        val steps = _stepsCount.value
        return if (steps >= 2000) {
            recordSoloOutdoorCompletion(30)
            _stepsCount.value = Math.max(0, steps - 2000)
            if (isEnglish) "✓ Goal reached: 2,000 steps verified! (+25 PTS)"
            else "✓ تم الوصول للهدف: 2,000 خطوة موثقة! (+25 نقطة)"
        } else if (dist in 0.0..150.0) {
            if (isEnglish) "📍 At Home Base (${dist.toInt()}m). Move >150m outside to track outdoor steps."
            else "📍 داخل نطاق المنزل (${dist.toInt()}م). ابتعد أكثر من 150م لاحتساب الخطوات."
        } else {
            if (isEnglish) "🏃 Active outside: $steps / 2,000 steps tracked."
            else "🏃 نشط بالخارج: $steps / 2,000 خطوة محسوبة."
        }
    }

    data class PrayerBufferStatus(
        val prayerKey: String,
        val prayerNameEn: String,
        val prayerNameAr: String,
        val isAttended: Boolean,
        val isDndActivated: Boolean,
        val isCurrentlyActive: Boolean,
        val minutesLeft: Int,
        val isBufferClosed: Boolean,
        val penaltyDeducted: Boolean = false,
        val minutesSinceClosed: Int = 999,
        val isAcknowledged: Boolean = false
    )

    fun getLastPrayerBufferStatus(): PrayerBufferStatus {
        val nowMs = System.currentTimeMillis()
        val activeWindow = getCurrentActivePrayerWindow()
        if (activeWindow != null) {
            val claimed = isPrayerAlreadyVerified(activeWindow.prayerKey)
            val isClosed = activeWindow.isPrayerCommenced && !claimed
            val todayKey = formatDateKey(nowMs)
            val ackKey = "prayer_missed_acknowledged_${activeWindow.prayerKey}_$todayKey"
            val isAck = prefs.getBoolean(ackKey, false)
            var penaltyApplied = false
            if (isClosed && (activeWindow.prayerKey == "dhuhr" || activeWindow.prayerKey == "asr" || activeWindow.prayerKey == "maghrib")) {
                val penaltyKey = "prayer_missed_penalty_${activeWindow.prayerKey}_$todayKey"
                if (!prefs.getBoolean(penaltyKey, false)) {
                    prefs.edit().putBoolean(penaltyKey, true).apply()
                    addPoints(-7, "${activeWindow.displayName} Soft Landing Bypassed (-7 PTS)", "prayer_buffer_penalty")
                }
                penaltyApplied = true
            }
            val minsSince = if (activeWindow.isPrayerCommenced) Math.max(0, ((nowMs - activeWindow.iqamahTimeMs) / 60000).toInt()) else 0
            return PrayerBufferStatus(
                prayerKey = activeWindow.prayerKey,
                prayerNameEn = activeWindow.displayName,
                prayerNameAr = activeWindow.displayNameArabic,
                isAttended = claimed,
                isDndActivated = claimed || _prayerBufferActive.value,
                isCurrentlyActive = activeWindow.isInitiativeOpen,
                minutesLeft = activeWindow.minutesRemainingInWindow,
                isBufferClosed = isClosed,
                penaltyDeducted = penaltyApplied,
                minutesSinceClosed = minsSince,
                isAcknowledged = isAck
            )
        }

        val tzOffset = java.util.TimeZone.getDefault().getOffset(nowMs) / 3600000.0
        val (effLat, effLng) = getEffectiveCoordinates()
        val calculator = PrayerTimesCalculator(effLat, effLng, tzOffset)
        val now = java.util.Date()
        val timesToday = calculator.calculateTimes(now)

        val candidates = listOf(
            Triple("maghrib", "Maghrib", "المغرب") to (timesToday.maghrib.timeInMillis - (5 * 60 * 1000L)),
            Triple("asr", "Asr", "العصر") to timesToday.asr.timeInMillis,
            Triple("dhuhr", "Dhuhr", "الظهر") to timesToday.dhuhr.timeInMillis,
            Triple("fajr", "Fajr", "الفجر") to timesToday.fajr.timeInMillis
        )

        for ((pInfo, pMs) in candidates) {
            if (nowMs >= pMs) {
                val claimed = isPrayerAlreadyVerified(pInfo.first)
                val isFajr = pInfo.first == "fajr"
                val isClosed = if (isFajr) false else !claimed
                val todayKey = formatDateKey(nowMs)
                val ackKey = "prayer_missed_acknowledged_${pInfo.first}_$todayKey"
                val isAck = prefs.getBoolean(ackKey, false)
                var penaltyApplied = false
                if (isClosed && (pInfo.first == "dhuhr" || pInfo.first == "asr" || pInfo.first == "maghrib")) {
                    val penaltyKey = "prayer_missed_penalty_${pInfo.first}_$todayKey"
                    if (!prefs.getBoolean(penaltyKey, false)) {
                        prefs.edit().putBoolean(penaltyKey, true).apply()
                        addPoints(-7, "${pInfo.second} Soft Landing Bypassed (-7 PTS)", "prayer_buffer_penalty")
                    }
                    penaltyApplied = true
                }
                val minsSince = Math.max(0, ((nowMs - pMs) / 60000).toInt())
                return PrayerBufferStatus(
                    prayerKey = pInfo.first,
                    prayerNameEn = pInfo.second,
                    prayerNameAr = pInfo.third,
                    isAttended = claimed,
                    isDndActivated = claimed,
                    isCurrentlyActive = false,
                    minutesLeft = 0,
                    isBufferClosed = isClosed,
                    penaltyDeducted = penaltyApplied,
                    minutesSinceClosed = minsSince,
                    isAcknowledged = isAck
                )
            }
        }

        return PrayerBufferStatus(
            prayerKey = "fajr",
            prayerNameEn = "Fajr",
            prayerNameAr = "الفجر",
            isAttended = false,
            isDndActivated = false,
            isCurrentlyActive = false,
            minutesLeft = 0,
            isBufferClosed = false,
            isAcknowledged = false
        )
    }

    fun acknowledgeMissedPrayerBuffer(prayerKey: String, prayerName: String) {
        val nowMs = System.currentTimeMillis()
        val todayKey = formatDateKey(nowMs)
        val ackKey = "prayer_missed_acknowledged_${prayerKey}_$todayKey"
        val penaltyKey = "prayer_missed_penalty_${prayerKey}_$todayKey"

        prefs.edit().putBoolean(ackKey, true).apply()

        if (!prefs.getBoolean(penaltyKey, false)) {
            prefs.edit().putBoolean(penaltyKey, true).apply()
            deductPoints(7, "$prayerName Soft Landing Bypassed (-7 PTS)", "prayer_buffer_penalty")
        } else {
            viewModelScope.launch {
                repository.insertLog(
                    SessionLog(
                        action = "$prayerName Soft Landing Miss Acknowledged (-7 PTS)",
                        pointsChange = 0,
                        status = "prayer_buffer_penalty"
                    )
                )
            }
        }
        recalculateCovenantStats()
    }

    private var previewRingtone: Ringtone? = null

    fun toggleFajrAlarm() {
        if (_fajrAlarmAccountabilityPinEnabled.value) {
            Toast.makeText(context, "Fajr enforcement is protected by the accountability PIN.", Toast.LENGTH_SHORT).show()
            return
        }

        val current = _fajrAlarmEnabled.value
        val target = !current

        if (target) {
            // Play a brief 2-second preview of the alarm sound so the user gets instant validation
            try {
                previewFajrAlarmRingtone()
                Toast.makeText(context, "Fajr Alarm activated! Playing preview.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("AnchorViewModel", "Failed to play alarm preview: ${e.message}")
            }

            setFajrAlarmEnabled(true)
        } else {
            previewRingtone?.stop()
            setFajrAlarmEnabled(false)
            Toast.makeText(context, "Fajr Alarm deactivated.", Toast.LENGTH_SHORT).show()
        }
    }

    fun scheduleFajrAlarm() {
        val (lat, lng) = getEffectiveCoordinates()
        if (!_fajrAlarmEnabled.value && !_fajrAlarmNfcLocked.value) {
            cancelFajrAlarm()
            return
        }
        com.analoganchor.app.service.FajrAlarmScheduler.scheduleFajrAlarm(context, lat, lng)
        com.analoganchor.app.service.FajrAlarmScheduler.scheduleJumuahAlarm(getApplication(), lat, lng)
    }

    fun cancelFajrAlarm() {
        com.analoganchor.app.service.FajrAlarmScheduler.cancelFajrAlarm(context)
        com.analoganchor.app.service.FajrAlarmScheduler.cancelJumuahAlarm(getApplication())
    }

    fun setLocationName(name: String) {
        prefs.edit().putString("user_location_name", name).apply()
        _userLocationName.value = name
    }

    fun saveEveningProtocolTime(timeStr: String) {
        prefs.edit().putString("evening_protocol_time", timeStr).apply()
        _eveningProtocolTime.value = timeStr
        viewModelScope.launch {
            preferencesRepository.setBedtimeStartTime(timeStr)
            preferencesRepository.setBedtimeEndTime("05:00")
            preferencesRepository.setBedtimeEnabled(true)
        }
        com.analoganchor.app.service.FajrAlarmScheduler.scheduleNightlyReminder(getApplication())
        com.analoganchor.app.service.FajrAlarmScheduler.scheduleThursdayPreBedtimePrompt(getApplication())
    }

    fun completeOnboarding() {
        prefs.edit().apply {
            putBoolean("is_onboarded", true)
            if (!prefs.contains("reserve_points") || prefs.getInt("reserve_points", 0) < 0) {
                putInt("reserve_points", 0)
            }
            if (!prefs.contains("app_install_timestamp")) {
                putLong("app_install_timestamp", System.currentTimeMillis())
            }
        }.apply()
        _isOnboarded.value = true
        _activeNfcRegistrationSlot.value = null
        if (_reservePoints.value < 0) {
            _reservePoints.value = 0
        }
    }

    fun saveCustodyContact(clientName: String, supportPhone: String) {
        prefs.edit()
            .putString("client_name", clientName.trim().ifEmpty { "Client" })
            .putString("support_phone", supportPhone.trim())
            .apply()
    }

    fun getClientName(): String {
        return prefs.getString("client_name", "Client") ?: "Client"
    }

    fun getSupportPhone(): String {
        return prefs.getString("support_phone", "") ?: ""
    }

    // Keep backward compat alias
    fun getFounderEmail(): String = getSupportPhone()

    fun markAnchorLockShortcutDone() {
        prefs.edit().putBoolean("anchor_lock_shortcut_done", true).apply()
    }

    fun isAnchorLockShortcutDone(): Boolean {
        return prefs.getBoolean("anchor_lock_shortcut_done", false)
    }

    suspend fun recordManualProof(imagePath: String, note: String?): Long {
        val id = database.proofMissionDao().insert(
            ProofMission(
                date = todayDate(), // Bug #9 fix: use function for live date
                proofType = "MANUAL_IMAGE",
                imagePath = imagePath,
                note = note?.trim()?.ifEmpty { null }
            )
        )
        updateAnchorStreakForProof(todayDate())
        return id
    }

    suspend fun createUnlockReview(beforeInsightsPath: String): Long {
        return database.unlockSessionDao().insert(
            UnlockSession(
                date = todayDate(), // Bug #9 fix: use function for live date
                proofMissionId = todayProofMission.value?.id,
                beforeInsightsPath = beforeInsightsPath,
                reviewCardPath = null,
                sharedAt = null,
                returnDueAt = null,
                returnProofPath = null,
                afterNote = null,
                outcome = UnlockSession.OUTCOME_DRAFT
            )
        )
    }

    suspend fun getUnlockSession(id: Long): UnlockSession? {
        return database.unlockSessionDao().getById(id)
    }

    suspend fun getProofMission(id: Long): ProofMission? {
        return database.proofMissionDao().getById(id)
    }

    suspend fun markReviewShared(sessionId: Long, reviewCardPath: String) {
        val session = database.unlockSessionDao().getById(sessionId) ?: return
        val now = System.currentTimeMillis()
        database.unlockSessionDao().update(
            session.copy(
                reviewCardPath = reviewCardPath,
                sharedAt = now,
                returnDueAt = now + RETURN_PROOF_WINDOW_MS,
                outcome = UnlockSession.OUTCOME_AWAITING_RETURN
            )
        )
    }

    suspend fun submitReturnProof(sessionId: Long, returnProofPath: String, note: String?): String {
        val session = database.unlockSessionDao().getById(sessionId) ?: return UnlockSession.OUTCOME_BREACH
        val now = System.currentTimeMillis()
        val dueAt = session.returnDueAt ?: now
        val outcome = if (now <= dueAt) {
            UnlockSession.OUTCOME_COMPLIANT
        } else {
            UnlockSession.OUTCOME_BREACH
        }

        database.unlockSessionDao().update(
            session.copy(
                returnProofPath = returnProofPath,
                afterNote = note?.trim()?.ifEmpty { null },
                outcome = outcome,
                completedAt = now
            )
        )

        if (outcome == UnlockSession.OUTCOME_BREACH) {
            openRecovery("Return proof was submitted after the custody window.")
        }

        return outcome
    }

    suspend fun resolveRecoveryWithProof(imagePath: String, note: String?) {
        val open = openRecoveryRecord.value
        if (open == null) {
            database.recoveryRecordDao().insert(
                RecoveryRecord(
                    resolvedAt = System.currentTimeMillis(),
                    reason = note?.trim()?.ifEmpty { null } ?: "Recovery proof submitted.",
                    proofImagePath = imagePath,
                    status = RecoveryRecord.STATUS_RESOLVED
                )
            )
        } else {
            database.recoveryRecordDao().update(
                open.copy(
                    resolvedAt = System.currentTimeMillis(),
                    proofImagePath = imagePath,
                    status = RecoveryRecord.STATUS_RESOLVED
                )
            )
        }
    }

    fun refreshExpiredUnlockSessions() {
        viewModelScope.launch {
            val expired = database.unlockSessionDao().getExpiredPendingSessions(System.currentTimeMillis())
            expired.forEach { session ->
                database.unlockSessionDao().update(
                    session.copy(
                        outcome = UnlockSession.OUTCOME_RECOVERY_REQUIRED,
                        completedAt = System.currentTimeMillis()
                    )
                )
                openRecovery("Return proof was not submitted before the custody window closed.")
            }
        }
    }

    fun dismissRuleFlag(ruleFlagId: Long) {
        viewModelScope.launch {
            database.ruleFlagDao().acknowledge(ruleFlagId)
        }
    }

    private fun updateAnchorStreakForProof(date: String) {
        val lastDate = prefs.getString("last_anchor_proof_date", "") ?: ""
        if (lastDate == date) return

        val current = prefs.getInt("anchor_streak", _anchorStreak.value)
        val updated = if (lastDate == yesterdayDateKey()) current + 1 else 1
        prefs.edit()
            .putString("last_anchor_proof_date", date)
            .putInt("anchor_streak", updated)
            .apply()
        _anchorStreak.value = updated
    }

    private suspend fun openRecovery(reason: String) {
        _anchorStreak.value = 0
        prefs.edit()
            .putInt("anchor_streak", 0)
            .putString("last_breach_date", todayDate()) // Bug #9 fix
            .apply()
        if (openRecoveryRecord.value == null) {
            database.recoveryRecordDao().insert(
                RecoveryRecord(
                    resolvedAt = null,
                    reason = reason,
                    proofImagePath = null,
                    status = RecoveryRecord.STATUS_OPEN
                )
            )
        }
    }

    fun resetFounderPanel() {
        prefs.edit().apply {
            putBoolean("is_sealed", true)
            putBoolean("is_onboarded", false)
            putBoolean("is_locked", false)
            putInt("reserve_points", 0)
            putString("uninstall_pin_hash", "")
            remove("app_pin_hash")
            putBoolean("app_pin_enabled", false)
            putString("app_theme_mode", "DARK")
            putBoolean("fajr_alarm_ringing", false)
            putBoolean("fajr_wake_step_active_${todayDate()}", false)
            putInt("fajr_wake_steps_${todayDate()}", 0)
            remove("fajr_wake_step_baseline_${todayDate()}")
            putBoolean("is_override_active", false)
            remove("user_lat")
            remove("user_lng")
            putString("user_location_name", "GPS pending")
            putInt("mosque_streak", 0)
            putInt("protocol_streak", 0)
            putInt("maghrib_retreat_streak", 0)
        }.apply()
        _isSealed.value = true
        _isOnboarded.value = false
        _isLocked.value = false
        _reservePoints.value = 0
        _uninstallPinSet.value = false
        _appPinSet.value = false
        _appPinEnabled.value = false
        _appPinUnlocked.value = true
        _appThemeMode.value = "DARK"
        _isFajrAlarmRinging.value = false
        _fajrWakeStepVerificationActive.value = false
        _fajrWakeStepsAfterDismiss.value = 0
        fajrWakeStepBaseline = -1f
        _userLatitude.value = LOCATION_UNSET
        _userLongitude.value = LOCATION_UNSET
        _userLocationName.value = "GPS pending"
        _mosqueComplianceStreak.value = 0
        _protocolComplianceStreak.value = 0
        _maghribRetreatStreak.value = 0
        _stepsCount.value = 0
        recalculateHabitCompliance()
        
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun addPoints(
        points: Int, 
        actionLabel: String, 
        status: String,
        startTime: Long? = null,
        endTime: Long? = null,
        duration: Int? = null,
        nfcTagId: String? = null,
        photoStatus: String? = null,
        userNote: String? = null,
        imagePath: String? = null
    ) {
        if (points < 0) {
            deductPoints(abs(points), actionLabel, status)
            return
        }
        val current = _reservePoints.value
        val updated = current + points
        val now = System.currentTimeMillis()
        prefs.edit()
            .putInt("reserve_points", updated)
            .putLong("last_ledger_transaction_timestamp", now)
            .putString("last_ledger_transaction_type", LedgerTransactionType.EARN.name)
            .apply()
        _reservePoints.value = updated
        _lastLedgerTransactionTimestamp.value = now
        _lastLedgerTransactionType.value = LedgerTransactionType.EARN
        _recentTransactionType.value = LedgerTransactionType.EARN
        _hasRecentPenalty.value = false
        recalculateStakeStats()

        viewModelScope.launch {
            repository.insertLog(
                SessionLog(
                    action = actionLabel,
                    pointsChange = points,
                    status = status,
                    startTime = startTime,
                    endTime = endTime,
                    duration = duration,
                    nfcTagId = nfcTagId,
                    photoStatus = photoStatus,
                    userNote = userNote,
                    imagePath = imagePath
                )
            )
        }
    }

    fun deductPoints(points: Int, actionLabel: String, status: String) {
        val current = _reservePoints.value
        val updated = current - points
        val now = System.currentTimeMillis()
        prefs.edit()
            .putInt("reserve_points", updated)
            .putLong("last_penalty_timestamp", now)
            .putLong("last_ledger_transaction_timestamp", now)
            .putString("last_ledger_transaction_type", LedgerTransactionType.PENALTY.name)
            .apply()
        _reservePoints.value = updated
        _lastPenaltyTimestamp.value = now
        _lastLedgerTransactionTimestamp.value = now
        _lastLedgerTransactionType.value = LedgerTransactionType.PENALTY
        _recentTransactionType.value = LedgerTransactionType.PENALTY
        _hasRecentPenalty.value = true
        recalculateStakeStats()

        viewModelScope.launch {
            repository.insertLog(
                SessionLog(
                    action = actionLabel,
                    pointsChange = -points,
                    status = status
                )
            )
        }
    }

    // Bug #1 fix: parameter `locked` was previously ignored (hardcoded to false)
    fun setLockedState(locked: Boolean) {
        prefs.edit().putBoolean("is_locked", locked).apply()
        _isLocked.value = locked
    }

    fun claimFajrBonus(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        if (hour >= 7) {
            return "Too late. Fajr bonus is only active before 07:00 AM."
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastClaimDate = prefs.getString("last_fajr_claim_date", "") ?: ""
        if (lastClaimDate == todayStr) {
            return "You have already claimed your Fajr bonus for today."
        }

        prefs.edit().putString("last_fajr_claim_date", todayStr).apply()
        addPoints(20, "Fajr Bonus Presence", "bonus")
        return "Bonus granted: +40 Reserve Points."
    }

    fun verifyFajrPresence() {
        val todayStr = formatDateKey(System.currentTimeMillis())
        prefs.edit()
            .putBoolean("fajr_verified_$todayStr", true)
            .putBoolean("prayer_verified_fajr_$todayStr", true)
            .apply()
        _fajrVerified.value = true
        // Fajr presence verified without point award as requested
        recalculateWeeklyPillarStreaks()
        recalculateWeeklyMosqueStats()
        recalculateCovenantStats()
        recalculateTodayPrayerStatus()
    }

    fun completeMaghribRetreat() {
        val todayStr = formatDateKey(System.currentTimeMillis())
        prefs.edit()
            .putBoolean("retreat_completed_$todayStr", true)
            .putBoolean("maghrib_verified_$todayStr", true)
            .apply()
        _maghribVerified.value = true
        addPoints(40, "Completed Maghrib-Isha Retreat", "retreat")
        recalculateWeeklyPillarStreaks()
        recalculateWeeklyMosqueStats()
        recalculateCovenantStats()
    }

    fun verifyMaghribPresence() {
        val todayStr = formatDateKey(System.currentTimeMillis())
        prefs.edit()
            .putBoolean("maghrib_verified_$todayStr", true)
            .putBoolean("retreat_completed_$todayStr", true)
            .apply()
        _maghribVerified.value = true
        addPoints(20, "Maghrib-to-Isha Retreat Verified", "mosque")
        recalculateWeeklyPillarStreaks()
        recalculateWeeklyMosqueStats()
        recalculateCovenantStats()
    }

    fun startFocusSession(durationMinutes: Int) {
        val countdownSeconds = if (ANCHOR_TAP_ONE_TEST_MODE) {
            ANCHOR_TAP_ONE_TEST_DURATION_SEC
        } else {
            durationMinutes * 60
        }
        startFocusSession(durationMinutes, countdownSeconds)
    }

    fun startAnchorTabTapOneSession(durationMinutes: Int) {
        val countdownSeconds = if (ANCHOR_TAP_ONE_TEST_MODE) {
            ANCHOR_TAP_ONE_TEST_DURATION_SEC
        } else {
            durationMinutes * 60
        }
        startFocusSession(durationMinutes, countdownSeconds)
    }

    private fun startFocusSession(durationMinutes: Int, countdownSeconds: Int) {
        sessionStartTime = System.currentTimeMillis()
        prefs.edit().putBoolean("is_session_active", true).apply()
        _sessionTotalDurationMinutes.value = durationMinutes
        _sessionTimeLeftSeconds.value = countdownSeconds.coerceAtLeast(0)
        _isSessionActive.value = true
        _sessionVoidedMessage.value = null
        resetFocusDisciplineState()
        
        silencePhone()
        acquireFocusWakeLock(countdownSeconds)

        lastGyroX = 0f
        lastGyroY = 0f
        lastGyroZ = 0f
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        handler.removeCallbacks(countdownRunnable)
        handler.postDelayed(countdownRunnable, 1000)
    }

    fun completeFocusSession(
        photoStatus: String = "skipped",
        userNote: String? = null,
        imagePath: String? = null
    ) {
        val startTime = sessionStartTime
        val endTime = System.currentTimeMillis()
        val duration = _sessionTotalDurationMinutes.value
        val tagId = _nfcTagId.value
        val pickupSummary = "Pickup count during focus: ${_focusPickupCount.value}/$FOCUS_PICKUP_LIMIT"
        val finalUserNote = listOfNotNull(userNote, pickupSummary).joinToString("\n")
        _isSessionEndAlarmRinging.value = false
        com.analoganchor.app.service.FajrAlarmReceiver.stopSessionEndAlarm(getApplication())
        unsilencePhone()

        prefs.edit().putBoolean("is_session_active", false).apply()
        _isSessionActive.value = false
        handler.removeCallbacks(countdownRunnable)
        unregisterSensors()
        unsilencePhone()
        restoreAudioMode()
        releaseFocusWakeLock()

        val todayStr = formatDateKey(System.currentTimeMillis())
        val reflectionCount = prefs.getInt("daily_reflection_count_$todayStr", 0) + 1
        val new25mCount = _daily25mCount.value + (if (duration == 5) 1 else 0)
        val newLongSession = _hasLongSessionToday.value || (duration > 5)
        val isFullyCapped = newLongSession || (new25mCount >= 2)

        prefs.edit()
            .putBoolean("evening_anchor_completed_$todayStr", isFullyCapped)
            .putInt("daily_reflection_count_$todayStr", reflectionCount)
            .putInt("daily_25m_count_$todayStr", new25mCount)
            .putBoolean("daily_long_session_$todayStr", newLongSession)
            .apply()

        _daily25mCount.value = new25mCount
        _hasLongSessionToday.value = newLongSession
        _isEveningAnchorCompletedToday.value = isFullyCapped

        val earnedPoints = when (duration) {
            10 -> 35
            15 -> 45
            else -> 25
        }

        addPoints(
            points = earnedPoints,
            actionLabel = "Completed ${duration}m Eraser Protocol",
            status = "complete",
            startTime = startTime,
            endTime = endTime,
            duration = duration,
            nfcTagId = tagId,
            photoStatus = photoStatus,
            userNote = finalUserNote,
            imagePath = imagePath
        )

        recalculateWeeklyPillarStreaks()
        resetFocusDisciplineState()
    }

    fun resetDailyReflectionCap() {
        val todayStr = formatDateKey(System.currentTimeMillis())
        prefs.edit()
            .putBoolean("evening_anchor_completed_$todayStr", false)
            .remove("daily_reflection_count_$todayStr")
            .remove("daily_25m_count_$todayStr")
            .remove("daily_long_session_$todayStr")
            .apply()
        _daily25mCount.value = 0
        _hasLongSessionToday.value = false
        _isEveningAnchorCompletedToday.value = false
    }

    fun stopSessionEndAlarm() {
        _isSessionEndAlarmRinging.value = false
        com.analoganchor.app.service.FajrAlarmReceiver.stopSessionEndAlarm(getApplication())
    }

    fun setTadabburAlarmRinging(ringing: Boolean) {
        _isTadabburAlarmRinging.value = ringing
        prefs.edit().putBoolean("tadabbur_alarm_ringing", ringing).apply()
    }

    fun stopTadabburAlarm() {
        _isTadabburAlarmRinging.value = false
        prefs.edit().putBoolean("tadabbur_alarm_ringing", false).apply()
        com.analoganchor.app.service.FajrAlarmReceiver.stopTadabburAlarm(getApplication())
    }

    fun simulateTadabburAnchorTap() {
        stopTadabburAlarm()
        recordTadabburCheckin()
        _navigateToTabChannel.trySend("ANCHOR")
        startFocusSession(5)
    }

    fun commitToTadabbur(days: Int) {
        val nowMs = System.currentTimeMillis()
        val untilMs = nowMs + (days * 86400000L)
        prefs.edit().apply {
            putBoolean("tadabbur_commitment_active", true)
            putInt("tadabbur_commitment_days", days)
            putLong("tadabbur_commitment_start_ms", nowMs)
            putLong("tadabbur_commitment_until_ms", untilMs)
        }.apply()

        _tadabburCommitmentActive.value = true
        _tadabburCommitmentDays.value = days
        _tadabburCommitmentRemainingDays.value = days

        com.analoganchor.app.service.FajrAlarmScheduler.scheduleTadabburAlarm(getApplication(), isSecondaryFajr = false)

        viewModelScope.launch {
            try {
                repository.insertLog(
                    SessionLog(
                        action = "Tadabbur Pre-Commitment: $days Days (7m before sunrise)",
                        pointsChange = 0,
                        status = "active"
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun cancelTadabburCommitment() {
        prefs.edit().apply {
            putBoolean("tadabbur_commitment_active", false)
            remove("tadabbur_commitment_until_ms")
        }.apply()

        _tadabburCommitmentActive.value = false
        stopTadabburAlarm()
        com.analoganchor.app.service.FajrAlarmScheduler.cancelTadabburAlarm(getApplication())
    }

    fun recordTadabburCheckin() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        prefs.edit().putBoolean("tadabbur_checked_in_$todayStr", true).apply()
        viewModelScope.launch {
            try {
                repository.insertLog(
                    SessionLog(
                        action = "Tadabbur Physical Return: White Glass Check-in",
                        pointsChange = 25,
                        status = "completed"
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun getSunriseMinus7Formatted(): String {
        return try {
            val (lat, lng) = getEffectiveCoordinates()
            val tzOffset = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0
            val calculator = PrayerTimesCalculator(lat, lng, tzOffset)
            val times = calculator.calculateTimes(Date())
            val targetCal = Calendar.getInstance().apply {
                timeInMillis = times.sunrise.timeInMillis - (7 * 60 * 1000L)
            }
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            sdf.format(targetCal.time)
        } catch (_: Exception) {
            "7m before sunrise"
        }
    }

    fun failFocusSession(reason: String) {
        val pickupSuffix = if (_focusPickupCount.value > 0) {
            " (${_focusPickupCount.value}/$FOCUS_PICKUP_LIMIT pickups)"
        } else {
            ""
        }
        _isSessionEndAlarmRinging.value = false
        com.analoganchor.app.service.FajrAlarmReceiver.stopSessionEndAlarm(getApplication())
        unsilencePhone()

        prefs.edit().putBoolean("is_session_active", false).apply()
        _isSessionActive.value = false
        handler.removeCallbacks(countdownRunnable)
        unregisterSensors()
        restoreAudioMode()
        releaseFocusWakeLock()

        _sessionVoidedMessage.value = reason

        deductPoints(35, "Failed Protocol: $reason$pickupSuffix", "missed")
        setLockedState(true)
        resetFocusDisciplineState()
    }

    fun enableDndMode() {
        silencePhone()
    }

    fun disableDndMode() {
        unsilencePhone()
        restoreAudioMode()
    }

    private fun silencePhone() {
        try {
            _notificationPolicyAccessGranted.value = hasNotificationPolicyAccess()
            initialRingerMode = audioManager.ringerMode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasNotificationPolicyAccess()) {
                val manager = notificationManager()
                initialInterruptionFilter = manager.currentInterruptionFilter
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                focusDndApplied = true
                _focusDndActive.value = true
            } else {
                focusDndApplied = false
                _focusDndActive.value = false
            }
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
        } catch (e: SecurityException) {
            Log.e("AnchorViewModel", "No DND permission to mute phone: ${e.message}")
            focusDndApplied = false
            _focusDndActive.value = false
        }
    }
    private fun wakeUpScreen() {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val wl = pm.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "AnalogAnchor:PickupWake"
            )
            wl.acquire(3000)
        } catch (e: Exception) {
            android.util.Log.w("AnchorViewModel", "Could not wake up screen: ${e.message}")
        }
    }

    private fun unsilencePhone() {
        try {
            if (focusDndApplied) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && hasNotificationPolicyAccess()) {
                    val manager = notificationManager()
                    initialInterruptionFilter?.let {
                        manager.setInterruptionFilter(it)
                    }
                }
                focusDndApplied = false
                _focusDndActive.value = false
            }
            initialRingerMode?.let {
                audioManager.ringerMode = it
            }
        } catch (e: SecurityException) {
            android.util.Log.e("AnchorViewModel", "No DND permission to restore phone: ${e.message}")
        }
    }

    private fun restoreAudioMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && focusDndApplied) {
                initialInterruptionFilter?.let { filter ->
                    notificationManager().setInterruptionFilter(filter)
                }
            }
            audioManager.ringerMode = initialRingerMode
        } catch (e: Exception) {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        } finally {
            focusDndApplied = false
            initialInterruptionFilter = null
            _focusDndActive.value = false
        }
    }

    private fun unregisterSensors() {
        sensorManager.unregisterListener(this)
        // Keep step listener registered
        registerStepCounterSensor()
    }

    private fun resetFocusDisciplineState() {
        _focusPickupCount.value = 0
        _focusPhoneFaceDown.value = false
        _focusDisciplineWarning.value = null
        focusFaceDownArmed = false
        focusWasFaceDown = false
        focusScreenLockedForCurrentFaceDown = false
        lastFocusPickupMs = 0L
    }

    private fun acquireFocusWakeLock(countdownSeconds: Int) {
        releaseFocusWakeLock()
        try {
            val timeoutMs = ((countdownSeconds.coerceAtLeast(1) + 90).toLong()) * 1000L
            focusWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AnalogAnchor:FocusSession"
            ).apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
        } catch (e: Exception) {
            Log.w("AnchorViewModel", "Could not acquire focus wake lock: ${e.message}")
            focusWakeLock = null
        }
    }

    private fun releaseFocusWakeLock() {
        try {
            focusWakeLock?.takeIf { it.isHeld }?.release()
        } catch (e: Exception) {
            Log.w("AnchorViewModel", "Could not release focus wake lock: ${e.message}")
        } finally {
            focusWakeLock = null
        }
    }

    private fun lockScreenForFaceDownIfPossible() {
        // Pomodoro timer displays full-screen without forcing the phone to lock
    }

    private fun handleFocusOrientation(values: FloatArray) {
        if (!_isSessionActive.value || values.size < 3) return

        val x = values[0]
        val y = values[1]
        val z = values[2]
        val isFaceDown = abs(z) >= 7.0f &&
            abs(x) < LIFTED_TILT_THRESHOLD &&
            abs(y) < LIFTED_TILT_THRESHOLD
        val isLifted = abs(z) < 7.0f ||
            abs(x) >= LIFTED_TILT_THRESHOLD ||
            abs(y) >= LIFTED_TILT_THRESHOLD

        if (isFaceDown) {
            focusFaceDownArmed = true
            focusWasFaceDown = true
            _focusPhoneFaceDown.value = true
            return
        }

        if (focusFaceDownArmed && isLifted) {
            _focusPhoneFaceDown.value = false
            focusScreenLockedForCurrentFaceDown = false
            wakeUpScreen()

            if (focusWasFaceDown) {
                val now = System.currentTimeMillis()
                if (now - lastFocusPickupMs >= PICKUP_DEBOUNCE_MS) {
                    recordFocusPickup(now)
                }
                focusWasFaceDown = false
            }
        }
    }

    private fun recordFocusPickup(now: Long) {
        lastFocusPickupMs = now
        val updatedCount = _focusPickupCount.value + 1
        _focusPickupCount.value = updatedCount
        // Motion and gyro pickups do NOT void/cancel focus sessions.
    }

    fun refreshStepSensorRegistration() {
        registerStepCounterSensor()
    }

    private fun registerStepCounterSensor() {
        listOfNotNull(stepCounterSensor, stepDetectorSensor).forEach { sensor ->
            try {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            } catch (e: SecurityException) {
                Log.w("AnchorViewModel", "Step permission unavailable: ${e.message}")
            } catch (e: Exception) {
                Log.e("AnchorViewModel", "Failed to register step sensor: ${e.message}")
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            lastGyroX = x
            lastGyroY = y
            lastGyroZ = z
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            handleFocusOrientation(event.values)
        } else if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val stepsValue = event.values[0]
            lastStepCounterValue = stepsValue
            if (initialStepCount == -1f) {
                initialStepCount = stepsValue
            } else {
                val difference = (stepsValue - initialStepCount).toInt()
                if (difference > _stepsCount.value) {
                    addSteps(difference - _stepsCount.value)
                }
            }
            updateFajrWakeStepVerification(stepsValue)
        } else if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            recordFajrWakeDetectorStep()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    fun startEmergencyCoolDown() {
        // Reflection window starts IMMEDIATELY when user presses the button.
        // Duration: TEST_MODE_COOLDOWN = true → 10 sec, false → 25 min
        activeUnlockJob?.cancel()
        graceTimerJob?.cancel()
        clipboardRescueJob?.cancel()
        clearClipboard()
        clearSecureAutofillState()
        _emergencyState.value = "COOLING"
        _emergencyCoolDownRemaining.value = COOLDOWN_DURATION_SEC
        _activeUnlockRemaining.value = 0
        _showReEntryShield.value = false
        // Bug #2 fix: timestamp belongs at requestEmergencyUnlockPayload(), not here
        // Bug #6 fix: persist state + epoch so it survives process death
        val epochNow = System.currentTimeMillis()
        val count = prefs.getInt("emergency_unlock_count", 0) + 1
        prefs.edit()
            .putString("emergency_state", "COOLING")
            .putLong("emergency_cooldown_start_epoch", epochNow)
            .remove("emergency_token_timestamp")
            .remove("emergency_active_unlock_expires_epoch")
            .remove("emergency_grace_expires_epoch")
            .putInt("emergency_unlock_count", count)
            .apply()
        emergencyRequestStartTimestamp = 0L
        _emergencyUnlockRequestCount.value = count

        val durationLabel = if (TEST_MODE_COOLDOWN) "10-SECOND (TEST MODE)" else "25-MINUTE"
        Log.i("AnchorTelemetry", "Emergency access window started — $durationLabel.")

        val targetEpoch = epochNow + COOLDOWN_DURATION_SEC * 1000L
        com.analoganchor.app.service.FajrAlarmScheduler.scheduleReflectionWindowAlarm(context, targetEpoch)
        launchCooldownCountdown(epochNow)
    }

    fun cancelEmergencyCoolDown() {
        coolDownJob?.cancel()
        com.analoganchor.app.service.FajrAlarmScheduler.cancelReflectionWindowAlarm(context)
        com.analoganchor.app.service.FajrAlarmReceiver.cancelReflectionNotification(context)
        lockEmergencyState()
    }

    fun requestEmergencyUnlockPayload(): String {
        _emergencyState.value = "REQUESTED"
        // Bug #2 fix: the 60-min token-validity window starts HERE (when user has the code)
        // not at startEmergencyCoolDown() (which is the reflection window)
        emergencyRequestStartTimestamp = System.currentTimeMillis()
        val reqs = prefs.getInt("unlock_requests_this_week", 0) + 1
        prefs.edit()
            .putInt("unlock_requests_this_week", reqs)
            .putString("emergency_state", "REQUESTED")
            .putLong("emergency_token_timestamp", emergencyRequestStartTimestamp)
            .remove("emergency_cooldown_start_epoch")
            .remove("emergency_active_unlock_expires_epoch")
            .remove("emergency_grace_expires_epoch")
            .apply()
        return getClientName()
    }

    /**
     * Token formats:
     * - First request: AA-ANCHOR-[cover]-[payload]-[cover]-[cover]-[cover]-[checksum]-EXP60
     * - Follow-up request: same shell, with U/V before the payload length code for code 2/3.
     * - Legacy: AA-[ENCODED_PIN]-EXP60
     *
     * The current token is intentionally long and serial-number-like so it is
     * difficult to memorize. The PIN digits are still decoded locally only.
     * Encoding: each digit d -> lookup char in TOKEN_ENCODE_MAP
     * Decoding: reverse lookup.
     * The token also becomes invalid if entered more than 60 minutes after the request started.
     */
    fun authorizeMaskedToken(token: String): Boolean {
        val cleanToken = normalizeMaskedToken(token)

        if (cleanToken.endsWith("-DEC60") || cleanToken.endsWith("DEC60")) {
            // It's a decoy! Give the illusion of unlock but provide a fake PIN or no PIN.
            clearClipboard()
            Log.d("AnchorTelemetry", "Decoy token authorized. Illusion armed.")
            startActiveSessionCountdown()
            return true
        }

        // Decode the PIN using reverse substitution
        val decodedPin = decodeAuthorizedPin(cleanToken) ?: return false

        // Arm secure auto-fill only. Do not write the raw PIN to clipboard.
        if (!setupBypassAutofill(decodedPin)) {
            Log.w("AnchorTelemetry", "Token accepted but secure auto-fill could not be armed.")
            return false
        }
        if (!PendingAutofillPinStore.rememberReEntryPin(context, decodedPin, RE_ENTRY_PIN_CUSTODY_MS)) {
            Log.w("AnchorTelemetry", "Token accepted but re-entry auto-fill custody could not be stored.")
            clearSecureAutofillState()
            return false
        }

        clearClipboard()
        Log.d("AnchorTelemetry", "Token authorized. Secure auto-fill armed.")
        startActiveSessionCountdown()
        return true
    }

    fun isMaskedTokenUsable(token: String): Boolean {
        return decodeAuthorizedPin(token) != null
    }

    fun isAccessTokenShape(token: String): Boolean {
        val normalized = normalizeMaskedToken(token)
        if (normalized.endsWith("-DEC60") || normalized.endsWith("DEC60")) {
            val serialMatch = Regex(
                """^AA-ANCHOR-([23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5})-((?:[KLMNP][23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8})|(?:[UV][KLMNP][23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}))-([23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5})-([23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5})-([23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5})-([23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5})-DEC60$"""
            ).matchEntire(normalized)
            if (serialMatch != null) return true
        }
        return parseEncodedPinFromToken(normalized) != null
    }

    fun getDecodedEmergencyPin(token: String): String? {
        val cleanToken = normalizeMaskedToken(token)
        if (cleanToken.endsWith("-DEC60") || cleanToken.endsWith("DEC60")) {
            return "4829"
        }
        val parsedToken = parseAccessTokenFromToken(cleanToken) ?: return null
        return decodeTokenPin(parsedToken.encodedPin, parsedToken.requestNumber)
    }

    fun authorizeMaskedTokenWithManualReveal(token: String): String? {
        val cleanToken = normalizeMaskedToken(token)
        if (cleanToken.endsWith("-DEC60") || cleanToken.endsWith("DEC60")) {
            Log.w("AnchorTelemetry", "Decoy manual rescue reveal armed.")
            startActiveSessionCountdown(initialClipboardClearDelayMs = 30_000)
            return "4829"
        }
        val decodedPin = decodeAuthorizedPin(cleanToken) ?: return null
        writeClipboardPinTemporarily(decodedPin)
        PendingAutofillPinStore.rememberReEntryPin(context, decodedPin, RE_ENTRY_PIN_CUSTODY_MS)
        Log.w("AnchorTelemetry", "Manual rescue reveal armed.")
        startActiveSessionCountdown(initialClipboardClearDelayMs = 30_000)
        return decodedPin
    }

    fun authorizeMaskedTokenWithClipboardRescue(token: String): Boolean {
        val cleanToken = normalizeMaskedToken(token)
        if (cleanToken.endsWith("-DEC60") || cleanToken.endsWith("DEC60")) {
            Log.w("AnchorTelemetry", "Decoy last-resort rescue armed for 30 seconds.")
            startActiveSessionCountdown(initialClipboardClearDelayMs = 30_000)
            return true
        }
        val decodedPin = decodeAuthorizedPin(cleanToken) ?: return false
        if (!writeClipboardPinTemporarily(decodedPin)) return false
        PendingAutofillPinStore.rememberReEntryPin(context, decodedPin, RE_ENTRY_PIN_CUSTODY_MS)

        Log.w("AnchorTelemetry", "Last-resort clipboard rescue armed for 30 seconds.")
        startActiveSessionCountdown(initialClipboardClearDelayMs = 30_000)
        return true
    }

    private data class ParsedAccessToken(
        val encodedPin: String,
        val requestNumber: Int
    )

    private fun decodeAuthorizedPin(token: String): String? {
        if (!isEmergencyTokenFresh()) {
            lockEmergencyState()
            return null
        }

        val cleanToken = normalizeMaskedToken(token)
        val parsedToken = parseAccessTokenFromToken(cleanToken) ?: return null
        return decodeTokenPin(parsedToken.encodedPin, parsedToken.requestNumber)
    }

    /**
     * Decodes the encoded portion of the token back to the raw PIN.
     * Must match the encoding in the support-tool.html exactly.
     * Code 1 map: 0->Q, 1->W, 2->E, 3->R, 4->T, 5->A, 6->S, 7->D, 8->F, 9->G
     * Code 2 map: 0->Z, 1->Y, 2->X, 3->C, 4->V, 5->B, 6->N, 7->M, 8->H, 9->J
     * Code 3 map: 0->P, 1->U, 2->L, 3->K, 4->A, 5->G, 6->F, 7->D, 8->S, 9->E
     */
    private fun decodeTokenPin(encoded: String, requestNumber: Int = 1): String? {
        val alphabet = tokenEncodeAlphabet(requestNumber) ?: return null
        return encoded.map { ch ->
            val digit = alphabet.indexOf(ch)
            if (digit == -1) return null
            digit.digitToChar()
        }.joinToString("")
    }

    private fun tokenEncodeAlphabet(requestNumber: Int): String? {
        return when (requestNumber) {
            1 -> "QWERTASDFG"
            2 -> "ZYXCVBNMHJ"
            3 -> "PULKAGFDSE"
            else -> null
        }
    }

    fun normalizeAccessTokenInput(token: String): String = normalizeMaskedToken(token)

    private fun normalizeMaskedToken(token: String): String {
        val compactToken = normalizeAsciiDigits(Normalizer.normalize(token, Normalizer.Form.NFKC))
            .uppercase(Locale.US)
            .replace('\u2010', '-')
            .replace('\u2011', '-')
            .replace('\u2012', '-')
            .replace('\u2013', '-')
            .replace('\u2014', '-')
            .replace('\u2212', '-')
            .replace('\uFE63', '-')
            .replace('\uFF0D', '-')
            .replace(Regex("[\\s\\u00A0\\u061C\\u200B\\u200C\\u200D\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]"), "")

        val serialTokenMatch = Regex(
            """AA-?ANCHOR-?([23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5})-?((?:[KLMNP][23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8})|(?:[UV][KLMNP][23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}))-?([23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5})-?([23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5})-?([23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5})-?([23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5})-?EXP60"""
        ).find(compactToken)
        if (serialTokenMatch != null) {
            return buildSerialToken(
                serialTokenMatch.groupValues[1],
                serialTokenMatch.groupValues[2],
                serialTokenMatch.groupValues[3],
                serialTokenMatch.groupValues[4],
                serialTokenMatch.groupValues[5],
                serialTokenMatch.groupValues[6]
            )
        }

        val legacyTokenMatch = Regex("""AA-?([QWERTASDFG]{4,8})-?EXP60""").find(compactToken)
        return legacyTokenMatch?.let { match -> "AA-${match.groupValues[1]}-EXP60" } ?: compactToken
    }

    private fun parseEncodedPinFromToken(cleanToken: String): String? {
        return parseAccessTokenFromToken(cleanToken)?.encodedPin
    }

    private fun parseAccessTokenFromToken(cleanToken: String): ParsedAccessToken? {
        val legacyMatch = Regex("""^AA-([QWERTASDFG]{4,8})-EXP60$""").matchEntire(cleanToken)
        if (legacyMatch != null) return ParsedAccessToken(legacyMatch.groupValues[1], requestNumber = 1)

        val serialMatch = Regex(
            """^AA-ANCHOR-([23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5})-((?:[KLMNP][23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8})|(?:[UV][KLMNP][23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}))-([23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5})-([23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5})-([23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5})-([23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5})-EXP60$"""
        ).matchEntire(cleanToken) ?: return null

        val coverA = serialMatch.groupValues[1]
        val payload = serialMatch.groupValues[2]
        val coverB = serialMatch.groupValues[3]
        val coverC = serialMatch.groupValues[4]
        val coverD = serialMatch.groupValues[5]
        val checksum = serialMatch.groupValues[6]
        val expectedChecksum = serialChecksum(coverA + payload + coverB + coverC + coverD)
        if (checksum != expectedChecksum) return null

        return parseSerialPayload(payload)
    }

    private fun parseSerialPayload(payload: String): ParsedAccessToken? {
        val firstChar = payload.firstOrNull() ?: return null
        val requestNumber = when (firstChar) {
            'U' -> 2
            'V' -> 3
            else -> 1
        }
        val lengthIndex = if (requestNumber == 1) 0 else 1
        val lengthCode = payload.getOrNull(lengthIndex) ?: return null
        val pinLength = when (lengthCode) {
            'K' -> 4
            'L' -> 5
            'M' -> 6
            'N' -> 7
            'P' -> 8
            else -> return null
        }
        val encodedPin = payload.drop(lengthIndex + 1).take(pinLength)
        val alphabet = tokenEncodeAlphabet(requestNumber) ?: return null
        return encodedPin
            .takeIf { value -> value.length == pinLength && value.all { it in alphabet } }
            ?.let { ParsedAccessToken(it, requestNumber) }
    }

    private fun buildSerialToken(
        coverA: String,
        payload: String,
        coverB: String,
        coverC: String,
        coverD: String,
        checksum: String
    ): String = "AA-ANCHOR-$coverA-$payload-$coverB-$coverC-$coverD-$checksum-EXP60"

    private fun serialChecksum(value: String): String {
        return buildString {
            repeat(5) { round ->
                var acc = 17L + round * 101L
                value.forEachIndexed { index, char ->
                    acc = (acc * 33L + char.code + index * (round + 7)) % 1_000_000_007L
                }
                append(SERIAL_ALPHABET[(acc % SERIAL_ALPHABET.length).toInt()])
            }
        }
    }

    private fun normalizeAsciiDigits(value: String): String {
        return buildString(value.length) {
            value.forEach { char ->
                val numericValue = Character.getNumericValue(char)
                if (numericValue in 0..9) {
                    append(numericValue.digitToChar())
                } else {
                    append(char)
                }
            }
        }
    }

    private fun isEmergencyTokenFresh(): Boolean {
        if (emergencyRequestStartTimestamp <= 0L) {
            emergencyRequestStartTimestamp = prefs.getLong("emergency_token_timestamp", 0L)
        }
        if (emergencyRequestStartTimestamp <= 0L) return false
        return System.currentTimeMillis() - emergencyRequestStartTimestamp <= TOKEN_VALIDITY_MS
    }

    private fun enterReEntryState() {
        prefs.edit()
            .putString("emergency_state", "RE_ENTRY")
            .remove("emergency_active_unlock_expires_epoch")
            .apply()
        _activeUnlockRemaining.value = 0
        _emergencyState.value = "RE_ENTRY"
        _showReEntryShield.value = true
        startGraceTimer()
    }

    private fun lockEmergencyState() {
        coolDownJob?.cancel()
        activeUnlockJob?.cancel()
        graceTimerJob?.cancel()
        clipboardRescueJob?.cancel()
        com.analoganchor.app.service.FajrAlarmScheduler.cancelReflectionWindowAlarm(context)
        com.analoganchor.app.service.FajrAlarmReceiver.cancelReflectionNotification(context)
        _emergencyCoolDownRemaining.value = 0
        _activeUnlockRemaining.value = 0
        _showReEntryShield.value = false
        _emergencyState.value = "LOCKED"
        emergencyRequestStartTimestamp = 0L
        clearSecureAutofillState()
        prefs.edit()
            .putString("emergency_state", "LOCKED")
            .remove("emergency_cooldown_start_epoch")
            .remove("emergency_token_timestamp")
            .remove("emergency_active_unlock_expires_epoch")
            .remove("emergency_grace_expires_epoch")
            .apply()
    }

    fun startActiveSessionCountdown(initialClipboardClearDelayMs: Long = 3_000) {
        val expiresAt = System.currentTimeMillis() + ACTIVE_UNLOCK_DURATION_SEC * 1000L
        prefs.edit()
            .putString("emergency_state", "ACTIVE_UNLOCKED")
            .putLong("emergency_active_unlock_expires_epoch", expiresAt)
            .remove("emergency_cooldown_start_epoch")
            .remove("emergency_token_timestamp")
            .remove("emergency_grace_expires_epoch")
            .apply()
        emergencyRequestStartTimestamp = 0L
        launchActiveUnlockCountdown(expiresAt, initialClipboardClearDelayMs)
    }

    private fun launchActiveUnlockCountdown(expiresAt: Long, initialClipboardClearDelayMs: Long) {
        _emergencyState.value = "ACTIVE_UNLOCKED"
        _showReEntryShield.value = false
        _activeUnlockRemaining.value = remainingSecondsUntil(expiresAt)
        activeUnlockJob?.cancel()

        viewModelScope.launch {
            delay(initialClipboardClearDelayMs.coerceAtLeast(0L))
            clearClipboard()
        }

        activeUnlockJob = viewModelScope.launch {
            var warnedAtThirty = false
            var warnedAtFifteen = false
            val initialRemaining = remainingSecondsUntil(expiresAt)
            val shouldWarn = initialRemaining > 30 * 60
            while (remainingSecondsUntil(expiresAt) > 0) {
                val remaining = remainingSecondsUntil(expiresAt)
                _activeUnlockRemaining.value = remaining
                if (shouldWarn) {
                    when {
                        !warnedAtFifteen && remaining <= 15 * 60 -> {
                            warnedAtFifteen = true
                            warnedAtThirty = true
                            showTemporaryUnlockReminder(minutesLeft = 15)
                        }
                        !warnedAtThirty && remaining <= 30 * 60 -> {
                            warnedAtThirty = true
                            showTemporaryUnlockReminder(minutesLeft = 30)
                        }
                    }
                }
                delay(1000)
            }
            _activeUnlockRemaining.value = 0
            clearClipboard()
            playTemporaryUnlockExpiredAlarm()
            enterReEntryState()
        }
    }

    private fun showFajrWakeMovementNotification() {
        ensureAnchorAlertsChannel()
        val notification = NotificationCompat.Builder(context, EMERGENCY_UNLOCK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Wake proof armed")
            .setContentText("Take the phone with you. $FAJR_WAKE_STEP_GOAL real steps will verify wake-up.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(0)
            .setSilent(true)
            .setSound(null)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(FAJR_WAKE_MOVEMENT_ID))
            .build()

        notifySafely(FAJR_WAKE_MOVEMENT_ID, notification)
    }

    private fun showFajrWakeVerifiedNotification() {
        ensureAnchorAlertsChannel()
        val notification = NotificationCompat.Builder(context, EMERGENCY_UNLOCK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Wake protocol verified")
            .setContentText("$FAJR_WAKE_STEP_GOAL real steps detected after Fajr alarm dismissal. +25 reserve points.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(FAJR_WAKE_MOVEMENT_DONE_ID))
            .build()

        notifySafely(FAJR_WAKE_MOVEMENT_DONE_ID, notification)
    }

    private fun showTemporaryUnlockReminder(minutesLeft: Int) {
        val urgent = minutesLeft <= 15
        vibrateTemporaryUnlockReminder(urgent)
        ensureAnchorAlertsChannel()

        val notification = NotificationCompat.Builder(context, EMERGENCY_UNLOCK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(if (urgent) "Re-arm now: 15 minutes left" else "30 minutes left")
            .setContentText(
                if (urgent) {
                    "Urgent: re-arm the blocking rule before the temporary unlock expires."
                } else {
                    "Temporary unlock is halfway done. Finish the change and re-arm blocking."
                }
            )
            .setPriority(if (urgent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(3000 + minutesLeft))
            .build()

        notifySafely(
            if (urgent) TEMP_UNLOCK_REMINDER_15_ID else TEMP_UNLOCK_REMINDER_30_ID,
            notification
        )
    }

    private fun playTemporaryUnlockExpiredAlarm() {
        showTemporaryUnlockExpiredNotification()
        vibrateTemporaryUnlockReminder(urgent = true)
        try {
            emergencyExpiryRingtone?.stop()
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
            val ringtone = RingtoneManager.getRingtone(context, alarmUri) ?: return
            emergencyExpiryRingtone = ringtone
            ringtone.play()
            viewModelScope.launch {
                delay(20_000)
                if (emergencyExpiryRingtone == ringtone) {
                    ringtone.stop()
                    emergencyExpiryRingtone = null
                }
            }
        } catch (e: Exception) {
            Log.e("AnchorTelemetry", "Failed to play temporary unlock expiry alarm: ${e.message}")
        }
    }

    private fun showTemporaryUnlockExpiredNotification() {
        ensureAnchorAlertsChannel()
        val notification = NotificationCompat.Builder(context, EMERGENCY_UNLOCK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Temporary unlock expired")
            .setContentText("Re-entry protocol started. Re-arm blocking before the grace timer ends.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(3100))
            .build()

        notifySafely(TEMP_UNLOCK_EXPIRED_ID, notification)
    }

    private fun ensureAnchorAlertsChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            EMERGENCY_UNLOCK_CHANNEL_ID,
            "Analog Anchor Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Wake proof and temporary unlock reminders"
            enableLights(true)
            enableVibration(true)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun mainActivityPendingIntent(requestCode: Int): PendingIntent {
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        try {
            notificationManager().notify(id, notification)
        } catch (e: SecurityException) {
            Log.w("AnchorTelemetry", "Notification permission unavailable: ${e.message}")
        } catch (e: Exception) {
            Log.e("AnchorTelemetry", "Failed to show notification: ${e.message}")
        }
    }

    private fun notificationManager(): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Suppress("DEPRECATION")
    private fun vibrateTemporaryUnlockReminder(urgent: Boolean) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        val pattern = if (urgent) {
            longArrayOf(0, 450, 140, 450, 140, 650)
        } else {
            longArrayOf(0, 250, 120, 250)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.e("AnchorTelemetry", "Failed to vibrate reminder: ${e.message}")
        }
    }

    fun clearClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("cleared", "")
            clipboard.setPrimaryClip(clip)
            Log.d("AnchorTelemetry", "Clipboard secure-wiped successfully.")
        } catch (e: Exception) {
            Log.e("AnchorTelemetry", "Failed to clear clipboard: ${e.message}")
        }
    }

    private fun clearSecureAutofillState() {
        PendingAutofillPinStore.clearAll(context)
        com.analoganchor.app.service.ZenithAccessibilityService.setPendingPin(null)
    }

    private fun writeClipboardPinTemporarily(pin: String): Boolean {
        return try {
            clipboardRescueJob?.cancel()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("manual_rescue", pin)
            clipboard.setPrimaryClip(clip)
            clipboardRescueJob = viewModelScope.launch {
                delay(30_000)
                clearClipboard()
                Log.d("AnchorTelemetry", "Last-resort clipboard rescue wiped after 30 seconds.")
            }
            true
        } catch (e: Exception) {
            Log.e("AnchorTelemetry", "Failed to arm clipboard rescue: ${e.message}")
            false
        }
    }

    fun confirmBlockingRuleRearmed() {
        graceTimerJob?.cancel()
        lockEmergencyState()
        clearClipboard()

        Log.d("AnchorTelemetry", "Completed Session Re-entry: blocking rule re-armed.")
    }

    fun confirmFocusScheduleReactivated(sentiment: String) {
        confirmBlockingRuleRearmed()
    }

    fun triggerReEntryScreen() {
        clearClipboard()
        activeUnlockJob?.cancel()
        enterReEntryState()
    }

    fun armBlockingAppReEntryAutofill(): Boolean {
        val rememberedPin = PendingAutofillPinStore.getReEntryPin(context)
        if (rememberedPin == null) {
            Log.w("AnchorTelemetry", "Re-entry auto-fill could not be armed because no remembered PIN is available.")
            return false
        }
        return setupBypassAutofill(rememberedPin)
    }

    fun getReEntryPinForManualRescue(): String? {
        return PendingAutofillPinStore.getReEntryPin(context)
    }

    /** Bug #10 fix: Grace timer lives in ViewModel so it survives recomposition */
    fun startGraceTimer(remainingSec: Int = GRACE_DURATION_SEC, persistDeadline: Boolean = true) {
        if (remainingSec <= 0) {
            failBlockingRuleRearm()
            return
        }

        _graceTimerRemaining.value = remainingSec
        graceTimerJob?.cancel()
        if (persistDeadline) {
            prefs.edit()
                .putLong("emergency_grace_expires_epoch", System.currentTimeMillis() + remainingSec * 1000L)
                .apply()
        }
        graceTimerJob = viewModelScope.launch {
            while (_graceTimerRemaining.value > 0) {
                delay(1000)
                _graceTimerRemaining.value -= 1
            }
            // Grace period expired — log breach
            failBlockingRuleRearm()
        }
    }

    fun failBlockingRuleRearm() {
        graceTimerJob?.cancel()
        lockEmergencyState()
        clearClipboard()
        deductPoints(100, "Security Lockout Breach: Blocking rule was not re-armed", "breach")
        Log.e("AnchorTelemetry", "BREACH ALERT: ${getClientName()} did not re-arm the blocking rule.")
    }

    fun failFocusReactivation() {
        failBlockingRuleRearm()
    }

    private fun setupBypassAutofill(pin: String): Boolean {
        val cleanPin = pin.trim()
        if (cleanPin.length !in 4..8 || cleanPin.any { !it.isDigit() }) {
            Log.w("AnchorTelemetry", "Secure gesture unlock refused an invalid PIN shape.")
            return false
        }

        if (!isAccessibilityServiceEnabled()) {
            Log.w("AnchorTelemetry", "Secure auto-fill is disabled.")
            return false
        }

        if (!PendingAutofillPinStore.armPin(context, cleanPin, ttlMs = AUTOFILL_ARM_TTL_MS)) {
            Log.w("AnchorTelemetry", "Secure gesture unlock could not store the pending PIN.")
            return false
        }

        com.analoganchor.app.service.ZenithAccessibilityService.setPendingPin(cleanPin)
        Log.d("AnchorTelemetry", "Accessibility gesture unlock armed.")
        return true
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val expectedService = android.content.ComponentName(context, com.analoganchor.app.service.ZenithAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.contains(expectedService.flattenToString())
        } catch (e: Exception) {
            return false
        }
    }

    fun isDrawOverlaysEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (instance == this) instance = null
        tickerJob?.cancel()
        coolDownJob?.cancel()
        activeUnlockJob?.cancel()
        graceTimerJob?.cancel() // Bug #10 fix
        clipboardRescueJob?.cancel()
        handler.removeCallbacks(countdownRunnable)
        sensorManager.unregisterListener(this)
    }
}
