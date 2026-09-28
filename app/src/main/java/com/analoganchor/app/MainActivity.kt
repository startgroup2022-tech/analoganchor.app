package com.analoganchor.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Vibrator
import android.os.VibrationEffect
import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.analoganchor.app.ui.screens.DashboardScreen
import com.analoganchor.app.ui.screens.OnboardingScreen
import com.analoganchor.app.ui.screens.ProtocolScreen
import com.analoganchor.app.ui.screens.SurvivalScreen
import com.analoganchor.app.ui.theme.MyApplicationTheme
import com.analoganchor.app.viewmodel.AnchorViewModel

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {
    private val viewModel: AnchorViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null

    companion object {
        /** MIME type written to the main Anchor NFC tag — routes to Day Guard tab */
        const val MIME_ANCHOR = "application/vnd.com.analoganchor.app.anchor"
        /** MIME type written to the Fajr Eraser NFC tag — routes to Fajr lockdown confirmation */
        const val MIME_FAJR = "application/vnd.com.analoganchor.app.fajr"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        enableEdgeToEdge()

        handleIncomingIntent(intent)

        setContent {
            val isDark = true
            com.analoganchor.app.ui.theme.isDarkThemeGlobal = true

            val preferencesRepository = remember { (application as com.analoganchor.app.AnalogAnchorApplication).preferencesRepository }
            val preferences by preferencesRepository.userPreferencesFlow.collectAsState(initial = com.analoganchor.app.data.preferences.UserPreferences())

            MyApplicationTheme(darkTheme = true, compactnessOption = preferences.compactnessOption) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    val snackbarHostState = remember { SnackbarHostState() }

                    // Observe ViewModel live flows
                    val isSealed by viewModel.isSealed.collectAsState()
                    val isOnboarded by viewModel.isOnboarded.collectAsState()
                    var activeScreen by remember { mutableStateOf("AUTO") }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                    ) { innerPadding ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            when {
                                !isOnboarded || activeScreen == "ONBOARDING" -> {
                                    OnboardingScreen(
                                        viewModel = viewModel,
                                        onFinishOnboarding = {
                                            viewModel.completeOnboarding()
                                            activeScreen = "AUTO"
                                        }
                                    )
                                }
                                activeScreen == "PROTOCOL" -> {
                                    ProtocolScreen(
                                        viewModel = viewModel,
                                        onNavigateToDashboard = { activeScreen = "AUTO" }
                                    )
                                }
                                activeScreen == "SURVIVAL" -> {
                                    SurvivalScreen(
                                        viewModel = viewModel,
                                        onNavigateToDashboard = { activeScreen = "AUTO" }
                                    )
                                }
                                else -> {
                                    DashboardScreen(
                                        viewModel = viewModel,
                                        onNavigateToProtocol = { activeScreen = "PROTOCOL" },
                                        onNavigateToSurvival = { activeScreen = "SURVIVAL" },
                                        onNavigateToFounderReset = {
                                            activeScreen = "ONBOARDING"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return
        if (incomingIntent.getBooleanExtra("open_anchor_lock_decoy", false)) {
            viewModel.setShowDecoyShortcutScreen(true)
        }

        val showBufferDialog = incomingIntent.getBooleanExtra("show_prayer_buffer_dialog", false)
        val prayerKey = incomingIntent.getStringExtra("prayer_key")
        val prayerNameEn = incomingIntent.getStringExtra("prayer_name_en")
        val prayerNameAr = incomingIntent.getStringExtra("prayer_name_ar")
        val targetTab = incomingIntent.getStringExtra("target_tab")
        val action = incomingIntent.getStringExtra("action")

        if (showBufferDialog && !prayerKey.isNullOrEmpty()) {
            viewModel.navigateToTab(targetTab ?: "MOSQUE")
            viewModel.triggerPrayerBufferPrompt(
                prayerKey = prayerKey,
                prayerNameEn = prayerNameEn ?: "Prayer",
                prayerNameAr = prayerNameAr ?: "الصلاة"
            )
        } else if (action == "join_group" || action == "join_partner") {
            viewModel.navigateToTab(targetTab ?: "OUTDOOR")
            viewModel.setShowPartnerPairingSheet(true)
        } else if (targetTab != null) {
            viewModel.navigateToTab(targetTab)
        }

        handleNfcIntent(incomingIntent)
    }

    override fun onResume() {
        super.onResume()
        try {
            sendBroadcast(Intent("com.analoganchor.app.action.DISMISS_ALL_OVERLAYS"))
        } catch (_: Exception) {}
        viewModel.setAppForegroundState(true)
        viewModel.checkHardwarePermissions()
        viewModel.onAppResumed()
        
        // Dynamically allow showing on lock screen if the Fajr alarm is active
        val prefs = getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        val isRinging = prefs.getBoolean("fajr_alarm_ringing", false) || prefs.getBoolean("jumuah_alarm_ringing", false)
        if (isRinging) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                try {
                    val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                    keyguardManager.requestDismissKeyguard(this, null)
                } catch (_: Exception) {}
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                )
            }
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(false)
                setTurnScreenOn(false)
            } else {
                @Suppress("DEPRECATION")
                window.clearFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                )
            }
        }
        
        nfcAdapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_NFC_BARCODE or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        viewModel.setAppForegroundState(false)
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag?) {
        tag?.let {
            val tagId = it.id.joinToString(":") { byte -> "%02X".format(byte) }
            val isFajrTag = (viewModel.fajrNfcTagId.value.isNotEmpty() && tagId == viewModel.fajrNfcTagId.value)
            
            // Provide haptic feedback safely: strong double-pulse vibration for Fajr tag, quick tap for others
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true) {
                if (isFajrTag) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 350, 150, 450), -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(longArrayOf(0, 350, 150, 450), -1)
                    }
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
            
            // Determine MIME type from active registration slot or registered tag IDs.
            // Writing a private MIME type eliminates the system chooser —
            // Android dispatches NDEF_DISCOVERED exclusively to us.
            val activeRegSlot = viewModel.activeNfcRegistrationSlot.value
            val mimeType = when {
                activeRegSlot == "anchor" -> MIME_ANCHOR
                activeRegSlot == "fajr" -> MIME_FAJR
                isFajrTag -> MIME_FAJR
                viewModel.nfcTagId.value.isNotEmpty() && tagId == viewModel.nfcTagId.value -> MIME_ANCHOR
                !viewModel.isOnboarded.value && viewModel.nfcTagId.value.isEmpty() -> MIME_ANCHOR
                else -> null // Unknown tag — don't rewrite
            }

            if (mimeType != null) {
                writeTagWithMimeType(it, mimeType)
            }

            viewModel.onNfcTagScanned(tagId, mimeType)
        }
    }

    /**
     * Writes a specific MIME-type NDEF record + AAR to the tag.
     * The private MIME type is unknown to any other app (including "Tags"),
     * so Android dispatches NDEF_DISCOVERED exclusively to us — no chooser.
     */
    private fun writeTagWithMimeType(tag: Tag, mimeType: String) {
        try {
            val mimeRecord = NdefRecord.createMime(mimeType, ByteArray(0))
            val aarRecord = NdefRecord.createApplicationRecord(packageName)
            val ndefMessage = NdefMessage(arrayOf(mimeRecord, aarRecord))

            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                // Skip write if tag already has the correct MIME type
                val existing = try { ndef.cachedNdefMessage } catch (_: Exception) { null }
                val alreadyCorrect = existing?.records?.any { record ->
                    record.tnf == NdefRecord.TNF_MIME_MEDIA && String(record.type) == mimeType
                } == true
                if (!alreadyCorrect && ndef.isWritable) {
                    ndef.writeNdefMessage(ndefMessage)
                    Log.d("NFC", "Written MIME [$mimeType] to tag")
                }
                ndef.close()
            } else {
                val formatable = NdefFormatable.get(tag)
                if (formatable != null) {
                    formatable.connect()
                    formatable.format(ndefMessage)
                    Log.d("NFC", "Formatted and written MIME [$mimeType] to tag")
                    formatable.close()
                }
            }
        } catch (e: Exception) {
            Log.e("NFC", "Failed to write MIME type to tag: ${e.message}")
        }
    }

    /**
     * Handles NFC intents when the app is cold-launched or resumed via a tag tap.
     * Extracts the MIME type from the NDEF payload to route without a chooser.
     */
    private fun handleNfcIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action !in listOf(
                NfcAdapter.ACTION_NDEF_DISCOVERED,
                NfcAdapter.ACTION_TECH_DISCOVERED,
                NfcAdapter.ACTION_TAG_DISCOVERED
            )
        ) return

        @Suppress("DEPRECATION")
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
        val tagId = tag.id.joinToString(":") { byte -> "%02X".format(byte) }

        val registeredFajrTag = viewModel.fajrNfcTagId.value
        val registeredAnchorTag = viewModel.nfcTagId.value

        // Extract MIME type from NDEF payload for routing (reliable even on cold launch)
        val mimeType: String? = run {
            @Suppress("DEPRECATION")
            val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            val ndefMsg = rawMsgs?.firstOrNull() as? NdefMessage
            val fromIntent = ndefMsg?.records?.firstOrNull()?.let { record ->
                if (record.tnf == NdefRecord.TNF_MIME_MEDIA) String(record.type) else null
            }
            if (fromIntent != null) return@run fromIntent

            try {
                val ndef = Ndef.get(tag)
                ndef?.cachedNdefMessage?.records?.firstOrNull()?.let { record ->
                    if (record.tnf == NdefRecord.TNF_MIME_MEDIA) String(record.type) else null
                }
            } catch (_: Exception) { null }
        }

        val isFajrTag = (registeredFajrTag.isNotEmpty() && tagId == registeredFajrTag) ||
            mimeType == MIME_FAJR ||
            mimeType == "application/vnd.com.analoganchor.app.analoganchor.fajr"

        // Provide haptic feedback safely: strong double-pulse vibration for Fajr tag, quick tap for others
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator?.hasVibrator() == true) {
            if (isFajrTag) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 350, 150, 450), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 350, 150, 450), -1)
                }
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }

        if (registeredFajrTag.isNotEmpty() && tagId == registeredFajrTag) {
            val expectedMime = MIME_FAJR
            if (mimeType != expectedMime) {
                Thread { writeTagWithMimeType(tag, expectedMime) }.start()
            }
        } else if (registeredAnchorTag.isNotEmpty() && tagId == registeredAnchorTag) {
            val expectedMime = MIME_ANCHOR
            if (mimeType != expectedMime) {
                Thread { writeTagWithMimeType(tag, expectedMime) }.start()
            }
        }

        viewModel.onNfcTagScanned(tagId, mimeType)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        viewModel.onAppBackgrounded()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onScreenOff()
    }
}
