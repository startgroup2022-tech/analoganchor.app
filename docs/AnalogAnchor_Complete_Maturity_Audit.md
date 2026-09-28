# AnalogAnchor_Complete_Maturity_Audit

Generated from the live Android workspace at `C:\get-Analog-Anchor-Android`.

This document is the Android-to-iOS preservation blueprint for the current Analog Anchor Android codebase. It is intentionally granular. The goal is not to describe the product in general terms; the goal is to preserve the active mechanics, storage contracts, event triggers, edge cases, and platform-specific constraints so the iOS migration does not accidentally delete behavior that currently exists.

## 0. Audit Scope And Source Files

Primary source files scanned:

- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/res/xml/anchor_accessibility_service_config.xml`
- `app/src/main/res/xml/analog_anchor_device_admin.xml`
- `app/src/main/res/xml/file_paths.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/main/java/com/example/AnalogAnchorApplication.kt`
- `app/src/main/java/com/example/viewmodel/AnchorViewModel.kt`
- `app/src/main/java/com/example/service/AnchorAccessibilityService.kt`
- `app/src/main/java/com/example/service/FajrAlarmReceiver.kt`
- `app/src/main/java/com/example/service/AnalogAnchorDeviceAdminReceiver.kt`
- `app/src/main/java/com/example/util/AppBlockIntegration.kt`
- `app/src/main/java/com/example/util/PendingAutofillPinStore.kt`
- `app/src/main/java/com/example/util/ProofFileStore.kt`
- `app/src/main/java/com/example/util/ReviewCardRenderer.kt`
- `app/src/main/java/com/example/util/UsageStatsCollector.kt`
- `app/src/main/java/com/example/data/AppDatabase.kt`
- `app/src/main/java/com/example/data/PrayerTimesCalculator.kt`
- `app/src/main/java/com/example/data/SessionLog.kt`
- `app/src/main/java/com/example/data/ProofMission.kt`
- `app/src/main/java/com/example/data/UnlockSession.kt`
- `app/src/main/java/com/example/data/RuleFlag.kt`
- `app/src/main/java/com/example/data/RecoveryRecord.kt`
- `app/src/main/java/com/example/data/*Dao.kt`
- `app/src/main/java/com/example/data/SessionLogRepository.kt`
- `app/src/main/java/com/example/engine/BehaviorRuleEngine.kt`
- `app/src/main/java/com/example/engine/DailyRuleEngineRunner.kt`
- `app/src/main/java/com/example/ui/screens/DashboardScreen.kt`
- `app/src/main/java/com/example/ui/screens/AccountabilityScreens.kt`
- `app/src/main/java/com/example/ui/screens/FounderScreen.kt`
- `app/src/main/java/com/example/ui/screens/OnboardingScreen.kt`
- `app/src/main/java/com/example/ui/screens/ProtocolScreen.kt`
- `app/src/main/java/com/example/ui/screens/SurvivalScreen.kt`
- `app/src/main/java/com/example/ui/screens/EmbeddedMapComponent.kt`
- `app/src/main/java/com/example/ui/components/GlassCard.kt`
- `app/src/main/java/com/example/ui/components/PremiumButton.kt`
- `app/src/main/java/com/example/ui/components/PremiumTextField.kt`
- `app/src/main/java/com/example/ui/components/TactileToken.kt`
- `app/src/main/java/com/example/ui/theme/Color.kt`
- `app/src/main/java/com/example/ui/theme/Theme.kt`
- `app/src/main/java/com/example/ui/theme/Type.kt`
- `support-tool.html`
- `docs/APPBLOCK_GESTURE_DEBUGGING.md`

Official Apple platform references checked for the iOS handoff:

- [Screen Time Technology Frameworks](https://developer.apple.com/documentation/ScreenTimeAPIDocumentation)
- [Family Controls](https://developer.apple.com/documentation/familycontrols)
- [AuthorizationCenter](https://developer.apple.com/documentation/familycontrols/authorizationcenter)
- [FamilyActivityPicker](https://developer.apple.com/documentation/familycontrols/familyactivitypicker)
- [DeviceActivityMonitor](https://developer.apple.com/documentation/DeviceActivity/DeviceActivityMonitor)
- [DeviceActivityCenter.startMonitoring](https://developer.apple.com/documentation/deviceactivity/deviceactivitycenter/startmonitoring%28_%3Aduring%3Aevents%3A%29)
- [Managed Settings](https://developer.apple.com/documentation/ManagedSettings)
- [ShieldSettings](https://developer.apple.com/documentation/managedsettings/shieldsettings)
- [Creating a Custom Keyboard](https://developer.apple.com/documentation/uikit/creating-a-custom-keyboard)
- [Configuring a Custom Keyboard Interface](https://developer.apple.com/documentation/uikit/configuring-a-custom-keyboard-interface)
- [Apple App Extension Programming Guide: Custom Keyboard](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html)

## 1. Project Footprint And Build Configuration

### 1.1 Gradle Project

Root project:

- `settings.gradle.kts`
  - Uses plugin `org.gradle.toolchains.foojay-resolver-convention` version `1.0.0`.
  - Includes Android application module `:app`.
- `build.gradle.kts`
  - Applies but does not activate app plugins at root:
    - `com.android.application`
    - `org.jetbrains.kotlin.plugin.compose`
    - `com.google.devtools.ksp`
    - `io.github.takahirom.roborazzi`
    - `com.google.android.libraries.mapsplatform.secrets-gradle-plugin`
- `app/build.gradle.kts`
  - Active Android app module.
  - Namespace: `com.example`.
  - Application ID: `com.aistudio.analoganchor.dmvqr`.
  - `compileSdk`: 36, minor API level 1.
  - `minSdk`: 24.
  - `targetSdk`: 36.
  - Version code/name: `1` / `1.0`.
  - Java source/target compatibility: Java 11.
  - Compose enabled.
  - BuildConfig enabled.
  - Unit tests include Android resources.
  - Secrets plugin reads `.env` and `.env.example`.
  - Release signing reads `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`, defaulting keystore file to `${rootDir}/my-upload-key.jks`.
  - Debug signing uses `${rootDir}/debug.keystore`, password `android`, alias `androiddebugkey`.
  - Release build has `isMinifyEnabled=false` and PNG crunching disabled.

### 1.2 Active Compiled Dependencies

Active app plugins:

- Android Gradle Plugin `com.android.application` version `9.1.1`.
- Kotlin Compose plugin `org.jetbrains.kotlin.plugin.compose` version `2.2.10`.
- KSP `com.google.devtools.ksp` version `2.3.5`.
- Roborazzi `io.github.takahirom.roborazzi` version `1.59.0`.
- Google Maps Platform Secrets plugin `2.0.1`.

Active production dependencies:

- AndroidX Compose BOM `2024.09.00`.
- Firebase BOM `34.12.0` is included as a platform, but no Firebase library is actively implemented because `firebase-ai` is commented out.
- `androidx.activity:activity-compose` `1.10.1`.
- `androidx.compose.material:material-icons-core`.
- `androidx.compose.material:material-icons-extended`.
- `androidx.compose.material3:material3`.
- `androidx.compose.ui:ui`.
- `androidx.compose.ui:ui-graphics`.
- `androidx.compose.ui:ui-tooling-preview`.
- `androidx.core:core-ktx` `1.18.0`.
- `androidx.lifecycle:lifecycle-runtime-compose` `2.8.7`.
- `androidx.lifecycle:lifecycle-runtime-ktx` `2.8.7`.
- `androidx.lifecycle:lifecycle-viewmodel-compose` `2.8.7`.
- `androidx.navigation:navigation-compose` `2.8.9`.
- `androidx.room:room-ktx` `2.7.0`.
- `androidx.room:room-runtime` `2.7.0`.
- `androidx.security:security-crypto` `1.1.0`.
- `com.squareup.retrofit2:converter-moshi` `2.12.0`.
- `org.jetbrains.kotlinx:kotlinx-coroutines-android` `1.10.2`.
- `org.jetbrains.kotlinx:kotlinx-coroutines-core` `1.10.2`.
- `com.squareup.okhttp3:logging-interceptor` `4.10.0`.
- `com.squareup.moshi:moshi-kotlin` `1.15.2`.
- `com.squareup.okhttp3:okhttp` `4.10.0`.
- `com.squareup.retrofit2:retrofit` `2.12.0`.

Active KSP processors:

- `androidx.room:room-compiler` `2.7.0`.
- `com.squareup.moshi:moshi-kotlin-codegen` `1.15.2`.

Active test dependencies:

- `androidx.compose.ui:ui-test-junit4`.
- `androidx.test:core` `1.6.1`.
- `androidx.test.ext:junit` `1.3.0`.
- `junit:junit` `4.13.2`.
- `org.jetbrains.kotlinx:kotlinx-coroutines-test` `1.10.2`.
- `org.robolectric:robolectric` `4.16.1`.
- `io.github.takahirom.roborazzi:roborazzi` `1.59.0`.
- `io.github.takahirom.roborazzi:roborazzi-compose` `1.59.0`.
- `io.github.takahirom.roborazzi:roborazzi-junit-rule` `1.59.0`.
- Android test Compose BOM `2024.09.00`.
- Android test `androidx.compose.ui:ui-test-junit4`.
- Android test `androidx.test.espresso:espresso-core` `3.7.0`.
- Android test `androidx.test.ext:junit` `1.3.0`.
- Android test `androidx.test:runner` `1.6.2`.
- Debug `androidx.compose.ui:ui-test-manifest`.
- Debug `androidx.compose.ui:ui-tooling`.

Commented but not compiled:

- Accompanist permissions.
- CameraX camera2/core/lifecycle/view.
- AndroidX DataStore preferences.
- Coil Compose.
- Firebase AI.
- Play Services Location.

### 1.3 Manifest Permissions

Active Android permissions:

- `android.permission.NFC`
- `android.permission.ACCESS_FINE_LOCATION`
- `android.permission.ACCESS_COARSE_LOCATION`
- `android.permission.CAMERA`
- `android.permission.PACKAGE_USAGE_STATS` with `tools:ignore="ProtectedPermissions"`
- `android.permission.SCHEDULE_EXACT_ALARM`
- `android.permission.USE_EXACT_ALARM`
- `android.permission.WAKE_LOCK`
- `android.permission.VIBRATE`
- `android.permission.POST_NOTIFICATIONS`
- `android.permission.ACTIVITY_RECOGNITION`

Declared optional hardware:

- NFC optional: `android.hardware.nfc`, required `false`.
- Camera optional: `android.hardware.camera`, required `false`.

Queried third-party AppBlock package names:

- `cz.mobilesoft.appblock`
- `com.appblock.app`
- `cz.droidsonroids.appblock`

### 1.4 Application Declaration

The `<application>` declaration:

- `android:name=".AnalogAnchorApplication"`
- `android:allowBackup="false"`
- `android:dataExtractionRules="@xml/data_extraction_rules"`
- `android:fullBackupContent="@xml/backup_rules"`
- `android:icon="@mipmap/ic_launcher_custom"`
- `android:roundIcon="@mipmap/ic_launcher_custom_round"`
- `android:label="@string/app_name"`
- `android:supportsRtl="false"`
- `android:theme="@style/Theme.MyApplication"`

## 2. Active Runtime Components

### 2.1 Activity Inventory

#### `com.example.MainActivity`

File: `app/src/main/java/com/example/MainActivity.kt`

Manifest status:

- Exported: `true`.
- Launcher: `android.intent.action.MAIN` plus `android.intent.category.LAUNCHER`.
- NFC text intent filter: `android.nfc.action.NDEF_DISCOVERED`, default category, MIME `text/plain`.
- Config changes handled: `orientation|screenSize|keyboardHidden`.

Lifecycle hooks:

- `onCreate`
  - Forces LTR layout via `window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR`.
  - Calls `enableEdgeToEdge()`.
  - Checks intent extra `open_anchor_lock_decoy`; if true, sets `viewModel.setShowDecoyShortcutScreen(true)`.
  - Creates Compose content with `MyApplicationTheme`.
  - Collects `viewModel.appThemeMode`; sets global `com.example.ui.theme.isDarkThemeGlobal`.
  - Uses `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)`.
  - Builds a `Scaffold` with transparent container and `SnackbarHost`.
  - Selects the first app surface by persistent setup state:
    - If `!isSealed`: `FounderScreen`.
    - Else if `!isOnboarded`: `OnboardingScreen`.
    - Else: `DashboardScreen`.
- `onNewIntent`
  - Calls `setIntent(intent)`.
  - Reads `open_anchor_lock_decoy` and opens the decoy shortcut screen.
- `onResume`
  - Calls `viewModel.checkHardwarePermissions()`.
  - Calls `viewModel.onAppResumed()` for app PIN relock and Fajr alarm-ring sync.
- `onStop`
  - Calls `viewModel.onAppBackgrounded()` to timestamp app backgrounding for the app PIN autolock.

### 2.2 Application Inventory

#### `com.example.AnalogAnchorApplication`

File: `app/src/main/java/com/example/AnalogAnchorApplication.kt`

Lifecycle:

- Creates `applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
- In `onCreate`, launches `DailyRuleEngineRunner.runIfDue(applicationContext)`.

This means behavior flags are evaluated at process start, but only if the once-per-day guard allows execution and Usage Access is granted.

### 2.3 Service Inventory

#### `com.example.service.AnchorAccessibilityService`

File: `app/src/main/java/com/example/service/AnchorAccessibilityService.kt`

Manifest status:

- Declared as an AccessibilityService.
- Permission: `android.permission.BIND_ACCESSIBILITY_SERVICE`.
- Exported: `true`.
- Metadata config: `@xml/anchor_accessibility_service_config`.

Accessibility config:

- Event types: `typeWindowContentChanged|typeWindowStateChanged`.
- Feedback type: `feedbackGeneric`.
- Flags:
  - `flagDefault`
  - `flagRetrieveInteractiveWindows`
  - `flagReportViewIds`
  - `flagIncludeNotImportantViews`
- Can retrieve window content: `true`.
- Can perform gestures: `true`.
- Notification timeout: `100`.
- Package filter:
  - `cz.mobilesoft.appblock`
  - `com.appblock.app`
  - `cz.droidsonroids.appblock`

Runtime role:

- Performs secure PIN entry into AppBlock only.
- Filters all accessibility events by known AppBlock package names.
- Has two injection paths:
  - `ACTION_SET_TEXT` for a standard editable field.
  - Sequential `dispatchGesture` taps for custom keypad nodes.

Static service state:

- `isServiceActive`: true while connected.
- `pendingPin`: in-memory pending PIN buffer.
- `hasTyped`: prevents repeated entry after success.
- `isTyping`: prevents concurrent entry attempts.

Important timing constants:

- `TAP_DURATION_MS = 100`
- `DIGIT_SPACING_MS = 260`
- `CONFIRM_DELAY_AFTER_DIGITS_MS = 360`
- `ENTRY_SETTLE_DELAY_MS = 260`
- `TREE_LOG_THROTTLE_MS = 2_000`
- `LOG_CHUNK_SIZE = 3_500`

Accepted exact confirm labels:

- `ok`
- `done`
- `enter`
- `confirm`
- `submit`
- `verify`
- `verify code`
- `unlock`
- `continue`
- `next`
- `go`

Confirm partial label matches:

- `confirm`
- `submit`
- `verify`
- `check`
- `next`
- `go`

Deleted/backspace exclusion terms for fallback confirm:

- `delete`
- `backspace`
- `clear`
- `remove`
- `erase`

### 2.4 Broadcast Receiver Inventory

#### `com.example.service.FajrAlarmReceiver`

File: `app/src/main/java/com/example/service/FajrAlarmReceiver.kt`

Manifest status:

- Enabled: `true`.
- Exported: `false`.
- Actions:
  - `com.example.action.FAJR_ALARM`
  - `com.example.action.FAJR_5HOUR_WARNING`

Runtime role:

- Receives scheduled Fajr warning and alarm broadcasts from `AlarmManager`.
- Sets `analog_anchor_prefs.fajr_alarm_ringing = true` when the Fajr alarm fires.
- Builds high-priority notifications.
- Plays custom alarm URI if configured; otherwise plays built-in looping tone.
- Vibrates strongly for the 5-hour warning.

Receiver actions:

- `ACTION_FAJR_5_HOUR_WARNING`: calls `showFiveHourWarning`.
- `ACTION_FAJR_ALARM` or `null` or unknown: calls `triggerFajrAlarm`.

Notification channel:

- ID: `fajr_alarm_channel`.
- Name: `Fajr Alarm Alerts`.
- Importance: high.
- Description: `Sleep discipline warnings and Fajr alarms`.
- Lights and vibration enabled.

Notification IDs:

- Alarm: `2002`.
- Warning: `2003`.

Alarm playback:

- Custom URI path:
  - Reads `fajr_alarm_ringtone_uri` from `analog_anchor_prefs`.
  - Uses `MediaPlayer`.
  - For API 21+, sets audio attributes `USAGE_ALARM` and `CONTENT_TYPE_SONIFICATION`.
  - For older APIs, uses `STREAM_ALARM`.
  - Sets `isLooping = true`.
  - Stops after 60 seconds via `Handler(Looper.getMainLooper()).postDelayed(stopLoop, 60_000L)`.
  - On error, stops and returns true from error listener.
- Built-in fallback:
  - Uses `ToneGenerator(AudioManager.STREAM_ALARM, 100)`.
  - Loops `ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD` for 650 ms every 950 ms.
  - Stops after 60 seconds.
- Static `stopRingtone()` clears handler callbacks, stops/releases `ToneGenerator`, stops/releases `MediaPlayer`, and nulls all active sound references.

#### `com.example.service.AnalogAnchorDeviceAdminReceiver`

File: `app/src/main/java/com/example/service/AnalogAnchorDeviceAdminReceiver.kt`

Manifest status:

- Exported: `true`.
- Permission: `android.permission.BIND_DEVICE_ADMIN`.
- Metadata: `@xml/analog_anchor_device_admin`.
- Intent filter: `android.app.action.DEVICE_ADMIN_ENABLED`.
- Device-admin policy XML includes `<force-lock />`.

Runtime role:

- Empty subclass of `DeviceAdminReceiver`.
- Used by `AnchorViewModel.deviceAdminComponentName()`, `isDeviceAdminActive()`, and `deviceAdminActivationIntent()`.
- Device Admin is currently used as uninstall friction and setup completion gate. The code does not currently call `DevicePolicyManager.lockNow()`.

### 2.5 Content Provider Inventory

#### `androidx.core.content.FileProvider`

Manifest status:

- Authority: `com.analoganchor.fileprovider`.
- Exported: `false`.
- Grants URI permissions: `true`.
- File paths config: `@xml/file_paths`.

Path mapping:

- `cache-path name="proof_images" path="proof/"`
- `cache-path name="card_images" path="cards/"`

Runtime users:

- `ProofFileStore.uriForFile`.
- Camera capture with `ActivityResultContracts.TakePicture`.
- Review card sharing with `Intent.ACTION_SEND`.

## 3. Codebase Structural Inventory

### 3.1 Root And Build Files

- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle.properties`
- `gradle/libs.versions.toml`
- `gradlew.bat`
- `README.md`
- `metadata.json`
- `support-tool.html`
- `docs/APPBLOCK_GESTURE_DEBUGGING.md`
- `licenses/Outfit-OFL.txt`

### 3.2 Android Resources

- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/xml/anchor_accessibility_service_config.xml`
- `app/src/main/res/xml/analog_anchor_device_admin.xml`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/main/res/xml/file_paths.xml`
- `app/src/main/res/font/outfit_black.ttf`
- `app/src/main/res/font/outfit_bold.ttf`
- `app/src/main/res/font/outfit_medium.ttf`
- `app/src/main/res/font/outfit_regular.ttf`
- `app/src/main/res/font/outfit_semibold.ttf`
- `app/src/main/res/drawable/ic_anchor_lock.xml`
- `app/src/main/res/drawable/ic_launcher_background.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Social drawable PNGs:
  - `ic_facebook.png`
  - `ic_instagram.png`
  - `ic_linkedin.png`
  - `ic_tiktok.png`
  - `ic_twitter.png`
  - `ic_youtube.png`
- Launcher icons under `mipmap-mdpi`, `mipmap-hdpi`, `mipmap-xhdpi`, `mipmap-xxhdpi`, `mipmap-xxxhdpi`, and adaptive icon XMLs under `mipmap-anydpi-v26`.

### 3.3 Kotlin Package Structure

Top-level:

- `com.example`
  - `MainActivity`
  - `AnalogAnchorApplication`

Data:

- `com.example.data.AppDatabase`
- `com.example.data.PrayerTimesCalculator`
- `com.example.data.SessionLog`
- `com.example.data.SessionLogDao`
- `com.example.data.SessionLogRepository`
- `com.example.data.ProofMission`
- `com.example.data.ProofMissionDao`
- `com.example.data.UnlockSession`
- `com.example.data.UnlockSessionDao`
- `com.example.data.RuleFlag`
- `com.example.data.RuleFlagDao`
- `com.example.data.RecoveryRecord`
- `com.example.data.RecoveryRecordDao`

Engine:

- `com.example.engine.AppBehaviorSnapshot`
- `com.example.engine.BehaviorRuleEngine`
- `com.example.engine.DailyRuleEngineRunner`

Service:

- `com.example.service.AnchorAccessibilityService`
- `com.example.service.FajrAlarmReceiver`
- `com.example.service.AnalogAnchorDeviceAdminReceiver`

Utility:

- `com.example.util.AppBlockIntegration`
- `com.example.util.PendingAutofillPinStore`
- `com.example.util.ProofFileStore`
- `com.example.util.ReviewCardData`
- `com.example.util.ReviewCardRenderer`
- `com.example.util.UsageStatsCollector`

ViewModel:

- `com.example.viewmodel.AnchorViewModel`

UI components:

- `com.example.ui.components.GlassCard`
- `com.example.ui.components.PremiumButton`
- `com.example.ui.components.PremiumTextField`
- `com.example.ui.components.TactileToken`

UI screens:

- `com.example.ui.screens.DashboardScreen`
- `com.example.ui.screens.AnchorTabContent`
- `com.example.ui.screens.ReserveTabContent`
- `com.example.ui.screens.MosqueTabContent`
- `com.example.ui.screens.RunawayTabContent`
- `com.example.ui.screens.AnchorLockDecoyContent`
- `com.example.ui.screens.ReEntryLockoutOverlayContent`
- `com.example.ui.screens.AccountabilityDashboardScreen`
- `com.example.ui.screens.ReturnProofScreen`
- `com.example.ui.screens.RecoveryScreen`
- `com.example.ui.screens.FounderScreen`
- `com.example.ui.screens.OnboardingScreen`
- `com.example.ui.screens.ProtocolScreen`
- `com.example.ui.screens.SurvivalScreen`
- `com.example.ui.screens.EmbeddedInteractiveMapCanvas`
- `com.example.ui.screens.MapPin`

Theme:

- `com.example.ui.theme.Color`
- `com.example.ui.theme.Theme`
- `com.example.ui.theme.Type`

## 4. Room Database Inventory

Database:

- File: `app/src/main/java/com/example/data/AppDatabase.kt`
- Database name: `analog_anchor_db`.
- Version: `3`.
- Export schema: `false`.
- Singleton builder: `Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "analog_anchor_db")`.
- Migrations: `MIGRATION_1_2`, `MIGRATION_2_3`.

### 4.1 `session_logs`

Entity: `SessionLog`

Fields:

- `id: Int`, primary key, auto-generate, default `0`.
- `timestamp: Long`, default `System.currentTimeMillis()`.
- `action: String`.
- `pointsChange: Int`.
- `status: String`.
- `startTime: Long?`.
- `endTime: Long?`.
- `duration: Int?`.
- `nfcTagId: String?`.
- `photoStatus: String?`.
- `userNote: String?`.
- `imagePath: String?`.

DAO:

- `getLatestLogs(limit: Int)`: `SELECT * FROM session_logs ORDER BY timestamp DESC LIMIT :limit`.
- `getWhiteboardSessions()`: `SELECT * FROM session_logs WHERE action LIKE '%Eraser%' OR userNote IS NOT NULL ORDER BY timestamp DESC`.
- `insertLog(log)`: `@Insert(onConflict = REPLACE)`.
- `clearLogs()`: deletes all rows.

Repository:

- `SessionLogRepository.getLatestLogs(limit)`.
- `SessionLogRepository.getWhiteboardSessions()`.
- `SessionLogRepository.insertLog(log)`.
- `SessionLogRepository.clearLogs()`.

### 4.2 `proof_missions`

Entity: `ProofMission`

Fields:

- `id: Long`, primary key, auto-generate.
- `date: String`.
- `createdAt: Long`, default `System.currentTimeMillis()`.
- `proofType: String`.
- `imagePath: String?`.
- `note: String?`.
- `status: String`, default `COMPLETED`.

DAO:

- `observeLatestCompletedForDate(date)`: latest completed proof mission for date.
- `getById(id)`.
- `insert(proofMission)`.

Current writer:

- `AnchorViewModel.recordManualProof(imagePath, note)` inserts `proofType = "MANUAL_IMAGE"` and updates anchor streak.

### 4.3 `unlock_sessions`

Entity: `UnlockSession`

Fields:

- `id: Long`, primary key, auto-generate.
- `date: String`.
- `requestedAt: Long`, default `System.currentTimeMillis()`.
- `proofMissionId: Long?`.
- `beforeInsightsPath: String?`.
- `reviewCardPath: String?`.
- `sharedAt: Long?`.
- `returnDueAt: Long?`.
- `returnProofPath: String?`.
- `afterNote: String?`.
- `outcome: String`, default `DRAFT`.
- `completedAt: Long?`.

Outcome constants:

- `DRAFT`
- `AWAITING_RETURN`
- `COMPLIANT`
- `BREACH`
- `RECOVERY_REQUIRED`

DAO:

- `observeRequestCountForDate(date)`: non-draft count for a date.
- `observeRequestCountSince(weekStartMillis)`: non-draft count for week.
- `observePendingReturnSession()`: latest `AWAITING_RETURN`.
- `observeLatestBreachSession()`: latest `BREACH` or `RECOVERY_REQUIRED`.
- `getById(id)`.
- `getExpiredPendingSessions(now)`.
- `insert(unlockSession)`.
- `update(unlockSession)`.

Current writers:

- `createUnlockReview(beforeInsightsPath)`: creates `DRAFT`.
- `markReviewShared(sessionId, reviewCardPath)`: sets `AWAITING_RETURN`, `sharedAt`, `returnDueAt = now + 20 minutes`.
- `submitReturnProof(sessionId, returnProofPath, note)`: sets `COMPLIANT` if within return window, else `BREACH`; opens recovery on breach.
- `refreshExpiredUnlockSessions()`: changes expired `AWAITING_RETURN` sessions to `RECOVERY_REQUIRED` and opens recovery.

### 4.4 `rule_flags`

Entity: `RuleFlag`

Fields:

- `id: Long`, primary key, auto-generate.
- `flagType: String`.
- `packageName: String?`.
- `severity: String`.
- `message: String`.
- `createdAt: Long`, default `System.currentTimeMillis()`.
- `acknowledged: Boolean`, default `false`.

DAO:

- `observeUnacknowledgedCount()`.
- `observeUnacknowledged()`.
- `countFlagsSince(flagType, since)`.
- `insert(ruleFlag)`.
- `insertAll(ruleFlags)`.
- `update(ruleFlag)`.
- `acknowledge(id)`.

Writers:

- `DailyRuleEngineRunner.runIfDue()`.
- `BehaviorRuleEngine.analyzeAll()`.

Behavior flag types:

- `FLAG_COMPULSIVE_CHECKER`
- `FLAG_LATE_NIGHT_PATTERN`
- `FLAG_HEAVY_USAGE`
- `FLAG_STREAK_REWARD`

Tracked package names:

- `com.instagram.android`
- `com.zhiliaoapp.musically`
- `com.tiktok.android`
- `com.google.android.youtube`
- `com.facebook.katana`
- `com.twitter.android`
- `com.whatsapp`

### 4.5 `recovery_records`

Entity: `RecoveryRecord`

Fields:

- `id: Long`, primary key, auto-generate.
- `createdAt: Long`, default `System.currentTimeMillis()`.
- `resolvedAt: Long?`.
- `reason: String`.
- `proofImagePath: String?`.
- `status: String`, default `OPEN`.

Status constants:

- `OPEN`
- `RESOLVED`

DAO:

- `observeOpenRecovery()`.
- `getById(id)`.
- `insert(recoveryRecord)`.
- `update(recoveryRecord)`.

Writers:

- `openRecovery(reason)`: opens one recovery record if none is currently open.
- `resolveRecoveryWithProof(imagePath, note)`: resolves open recovery or creates resolved record if no open record exists.

### 4.6 Migrations

`MIGRATION_1_2`:

- Adds nullable session log columns if missing:
  - `startTime INTEGER`
  - `endTime INTEGER`
  - `duration INTEGER`
  - `nfcTagId TEXT`
  - `photoStatus TEXT`
  - `userNote TEXT`
  - `imagePath TEXT`
- Creates `proof_missions` if missing.
- Creates `unlock_sessions` if missing.
- Creates `rule_flags` if missing.
- Creates `recovery_records` if missing.

`MIGRATION_2_3`:

- Adds `session_logs.imagePath TEXT` if missing.

Column add helper:

- Queries `PRAGMA table_info(session_logs)`.
- Checks column name before executing `ALTER TABLE`.

## 5. SharedPreferences And Local Storage Keys

### 5.1 Normal Preferences: `analog_anchor_prefs`

Primary owner: `AnchorViewModel`.

Also used by:

- `FajrAlarmReceiver`
- `DailyRuleEngineRunner`
- `FounderScreen`
- `SurvivalScreen`
- `PendingAutofillPinStore` only for clearing legacy plain autofill keys.

Setup and identity keys:

- `is_sealed: Boolean`
  - Founder configuration has been sealed.
  - MainActivity shows `FounderScreen` when false.
- `is_onboarded: Boolean`
  - Client onboarding has completed.
  - MainActivity shows `OnboardingScreen` when false and sealed true.
- `client_name: String`
  - Stored by `saveCustodyContact`.
  - Defaults to `Client`.
- `support_phone: String`
  - Stored by `saveCustodyContact`.
  - Also returned by `getFounderEmail()` for backward compatibility.
- `uninstall_pin_hash: String`
  - SHA-256 hash of a local 4-digit uninstall PIN.
  - Set by `setUninstallPin`.
- `appblock_hidden_confirmed: Boolean`
  - UI setup confirmation in `FounderScreen`.
  - Indicates support manually hid AppBlock from launcher.

Dashboard app PIN keys:

- `app_pin_enabled: Boolean`
  - Controls app PIN gate.
- `app_pin_hash: String`
  - SHA-256 hash of a 4-digit dashboard PIN.
- `app_theme_mode: String`
  - Values: `DARK` or `LIGHT`.

Reserve and lock keys:

- `reserve_points: Int`
  - Default at init: `120`.
  - Initial StateFlow default before prefs load: `100`.
- `is_locked: Boolean`
  - Stored by `setLockedState`.
- `is_override_active: Boolean`
  - Set true by `bypassLockWithCredits`, `verifyMosquePresence`, and step goal completion.
  - Cleared by founder reset.

Location and sanctuary keys:

- `sanctuary_type: String`
  - Values seen: `mosque`, `runaway`, `park`.
- `sanctuary_lat: Float`
- `sanctuary_lng: Float`
- `mosque_name: String`
- `mosque_address: String`
- `user_location_name: String`
- `user_lat: Float`
- `user_lng: Float`
- `home_lat: Float`
- `home_lng: Float`

Prayer and Fajr keys:

- `fajr_alarm_enabled: Boolean`
- `fajr_alarm_ringing: Boolean`
- `fajr_alarm_ayah_constraint: Boolean`
- `fajr_alarm_accountability_pin_enabled: Boolean`
- `fajr_accountability_pin_hash: String`
- `fajr_alarm_ringtone_uri: String?`
- `fajr_alarm_ringtone_label: String?`
- `last_fajr_claim_date: String`
- `fajr_verified_$todayStr: Boolean`
- `fajr_wake_verified_$todayStr: Boolean`
- `maghrib_verified_$todayStr: Boolean`

Anchor session keys:

- `nfc_tag_id: String`
- `evening_protocol_time: String`
- `is_session_active: Boolean`
- `last_anchor_proof_date: String`
- `last_breach_date: String`

Behavior and habit keys:

- `mosque_streak: Int`
- `running_consistency_score: Int`
- `protocol_streak: Int`
- `anchor_streak: Int`
- `workout_mode: String`
  - Values: `run`, `walk`.
- `last_rule_engine_run: Long`

Emergency access keys:

- `emergency_state: String`
  - Values:
    - `LOCKED`
    - `COOLING`
    - `READY_TO_REQUEST`
    - `REQUESTED`
    - `ACTIVE_UNLOCKED`
    - `RE_ENTRY`
- `emergency_cooldown_start_epoch: Long`
- `emergency_token_timestamp: Long`
- `emergency_active_unlock_expires_epoch: Long`
- `emergency_grace_expires_epoch: Long`
- `emergency_unlock_count: Int`
- `unlock_requests_this_week: Int`

Anchor Lock shortcut key:

- `anchor_lock_shortcut_done: Boolean`
  - Set by onboarding if pinned shortcut request succeeds or if launcher does not support pinned shortcuts.

Legacy autofill keys cleared from normal prefs:

- `pending_autofill_pin`
- `pending_autofill_expires_epoch`

These keys should not be used as normal storage anymore. `PendingAutofillPinStore` explicitly removes them from `analog_anchor_prefs`.

### 5.2 Encrypted Preferences: `analog_anchor_secure_autofill`

Owner: `PendingAutofillPinStore`.

Crypto:

- `EncryptedSharedPreferences`.
- `MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM)`.
- Preference key encryption: `AES256_SIV`.
- Preference value encryption: `AES256_GCM`.

Keys:

- `pending_autofill_pin: String`
  - The current AppBlock PIN to inject.
  - TTL set by `AUTOFILL_ARM_TTL_MS = 90_000`.
- `pending_autofill_expires_epoch: Long`
  - Expiry for `pending_autofill_pin`.
- `reentry_autofill_pin: String`
  - The remembered AppBlock PIN for re-entry.
  - Used after the temporary unlock window ends, so the user does not have to type the blocking-app PIN manually again.
- `reentry_autofill_expires_epoch: Long`
  - Expiry for `reentry_autofill_pin`.
  - TTL set by `RE_ENTRY_PIN_CUSTODY_MS = 75 * 60 * 1000`.

Validation:

- All stored PINs must be trimmed, 4 to 8 characters long, and digits-only.

Clear paths:

- `clear(context)`: removes pending pin and expiry; also clears legacy normal prefs.
- `clearReEntryPin(context)`: removes remembered re-entry pin and expiry.
- `clearAll(context)`: removes pending and re-entry keys; also clears legacy normal prefs.

### 5.3 File Storage

Proof images:

- Directory: `context.cacheDir/proof`.
- File name shape: `${prefix}_${System.currentTimeMillis()}.jpg`.
- Default prefix: `proof`.
- Whiteboard prefix: `whiteboard_reflection`.
- Return proof prefix: `appblock_after`.
- Recovery proof prefix: `recovery_proof`.
- Manual upload prefix: `proof_upload`.

Review card images:

- Directory: `context.cacheDir/cards`.
- File name shape: `${prefix}_${System.currentTimeMillis()}.jpg`.
- Default prefix: `review`.
- Rendered JPEG quality: 92.

FileProvider authority:

- `com.analoganchor.fileprovider`.

### 5.4 Support Tool Token Contract

File: `support-tool.html`.

The support tool is a local HTML page that generates tokens for the Android app to decode.

Token format:

- `AA-[ENCODED_PIN]-EXP60`

Encoded PIN length:

- 4 to 8 characters.

Digit substitution:

- `0 -> Q`
- `1 -> W`
- `2 -> E`
- `3 -> R`
- `4 -> T`
- `5 -> A`
- `6 -> S`
- `7 -> D`
- `8 -> F`
- `9 -> G`

Token regex:

- `^AA-([QWERTASDFG]{4,8})-EXP60$`

Support tool behaviors:

- Generates token from numeric PIN.
- Auto-copies token with `navigator.clipboard.writeText` or a textarea fallback using `document.execCommand('copy')`.
- Can build WhatsApp message text.
- Can open `https://wa.me/{phone}?text={encoded}`.
- Can decode and verify token back to PIN for support-side checking.

## 6. ViewModel State Inventory

Main state owner: `AnchorViewModel`, file `app/src/main/java/com/example/viewmodel/AnchorViewModel.kt`.

Inheritance:

- Extends `AndroidViewModel`.
- Implements `SensorEventListener`.

### 6.1 Constants

- `RETURN_PROOF_WINDOW_MS = 20 * 60 * 1000L`.
- `TEST_MODE_COOLDOWN = true`.
  - Current behavior: emergency reflection window is 2 minutes.
  - Production target: set false for 60 minutes.
- `ANCHOR_TAP_ONE_TEST_MODE = true`.
  - Current behavior: Anchor tab Tap 1 session runs for 30 seconds.
  - Production target: set false for real duration.
- `ANCHOR_TAP_ONE_TEST_DURATION_SEC = 30`.
- `COOLDOWN_DURATION_SEC = if (TEST_MODE_COOLDOWN) 120 else 3600`.
- `TOKEN_VALIDITY_MS = 60 * 60 * 1000L`.
- `ACTIVE_UNLOCK_DURATION_SEC = 60 * 60`.
- `GRACE_DURATION_SEC = 10 * 60`.
- `AUTOFILL_ARM_TTL_MS = 90_000L`.
- `RE_ENTRY_PIN_CUSTODY_MS = 75 * 60 * 1000L`.
- `APP_PIN_AUTO_LOCK_MS = 60_000L`.
- `FAJR_ALARM_ACTION = "com.example.action.FAJR_ALARM"`.
- `FAJR_5_HOUR_WARNING_ACTION = "com.example.action.FAJR_5HOUR_WARNING"`.

### 6.2 Room StateFlows

- `sessionLogs: StateFlow<List<SessionLog>>`
  - Latest 5 logs.
  - Source: `repository.getLatestLogs(5)`.
- `whiteboardSessions: StateFlow<List<SessionLog>>`
  - Source: `repository.getWhiteboardSessions()`.
  - Query includes `action LIKE '%Eraser%' OR userNote IS NOT NULL`.
- `todayProofMission: StateFlow<ProofMission?>`
  - Latest completed proof mission for init-time current date.
- `requestsToday: StateFlow<Int>`
  - Non-draft unlock requests for init-time current date.
- `requestsThisWeek: StateFlow<Int>`
  - Non-draft unlock requests since Monday of current week.
- `pendingReturnSession: StateFlow<UnlockSession?>`
  - Latest `AWAITING_RETURN`.
- `latestBreachSession: StateFlow<UnlockSession?>`
  - Latest `BREACH` or `RECOVERY_REQUIRED`.
- `openRecoveryRecord: StateFlow<RecoveryRecord?>`
  - Latest open recovery.
- `activeRuleFlagCount: StateFlow<Int>`
  - Count of unacknowledged behavior flags.
- `activeRuleFlags: StateFlow<List<RuleFlag>>`
  - List of unacknowledged behavior flags.

All Room flows use `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialValue)`.

### 6.3 Public UI StateFlows

Reserve, lock, setup:

- `reservePoints`
  - Backing: `_reservePoints = MutableStateFlow(100)`.
  - Loaded from `reserve_points`, default `120`.
- `isLocked`
  - Backing: `_isLocked = MutableStateFlow(false)`.
  - Loaded from `is_locked`.
- `isSealed`
  - Loaded from `is_sealed`.
- `isOnboarded`
  - Loaded from `is_onboarded`.

App PIN and theme:

- `appPinEnabled`
  - Loaded from `app_pin_enabled`.
- `appPinSet`
  - True if `app_pin_hash` is non-empty.
- `appThemeMode`
  - Defaults to `DARK`.
  - Valid values enforced by setter: `DARK`, `LIGHT`.
- `appPinUnlocked`
  - Initial value is inverse of `app_pin_enabled`.
  - Set false on resume after 60 seconds background if PIN enabled.

Fajr and sleep:

- `isFajrAlarmRinging`
  - Loaded from `fajr_alarm_ringing`.
  - Synced in `onAppResumed`.
- `sleepPressureStatus`
  - Values: `NORMAL`, `NIGHT_WINDOW`, `LAST_WINDOW`, `MINIMUM`.
- `sleepPressureTimeLeftMs`
  - Milliseconds to next Fajr.
- `fajrAlarmAyahConstraint`
  - Loaded from `fajr_alarm_ayah_constraint`.
- `fajrAlarmAccountabilityPinEnabled`
  - Loaded from `fajr_alarm_accountability_pin_enabled`.
- `fajrAccountabilityPinSet`
  - True if `fajr_accountability_pin_hash` is non-empty.
- `fajrAlarmRingtoneUri`
  - Loaded from `fajr_alarm_ringtone_uri`.
- `fajrAlarmRingtoneLabel`
  - Loaded from `fajr_alarm_ringtone_label`.
  - Default when no URI: `Built-in Analog tone`.
  - Default when URI but no label: `Custom ringtone`.

Sanctuary and GPS:

- `sanctuaryType`
  - Default `mosque`.
- `mosqueName`
  - Default `Al Fateh Grand Mosque`.
- `mosqueAddress`
  - Default `Manama, Bahrain`.
- `sanctuaryLatitude`
  - Default `26.2285`.
- `sanctuaryLongitude`
  - Default `50.5860`.
- `userLocationName`
  - Default `Busaiteen, Bahrain`.
- `userLatitude`
  - Default `26.2285`.
- `userLongitude`
  - Default `50.5860`.

Prayer timers:

- `nextPrayerCountdown`
  - Default string `-04:52:10`.
  - Updates every second.
- `nextPrayerName`
  - Default `AL-FAJR` plus Arabic in source.
- `prayerTimesMap`
  - Map keys: `fajr`, `sunrise`, `dhuhr`, `asr`, `maghrib`, `isha`.
- `isFriday`
  - True on Fridays.

Steps and behavior:

- `stepsCount`
- `mosqueComplianceStreak`
- `runningConsistencyScore`
- `protocolComplianceStreak`
- `habitScore`
- `anchorStreak`

Hardware:

- `deviceAdminActive`
- `accessibilityActive`
- `uninstallPinSet`

Daily verification:

- `fajrVerified`
- `fajrWakeVerified`
- `maghribVerified`

Decoy and emergency:

- `showDecoyShortcutScreen`
- `emergencyCoolDownRemaining`
- `activeUnlockRemaining`
- `showReEntryShield`
- `emergencyState`
- `emergencyUnlockRequestCount`
- `graceTimerRemaining`

Home and workout:

- `homeLatitude`
- `homeLongitude`
- `homeBaseSaved`
- `workoutMode`

Anchor session:

- `isSessionActive`
- `sessionTimeLeftSeconds`
- `sessionTotalDurationMinutes`
- `sessionVoidedMessage`
- `sessionStartTime`

### 6.4 Private Jobs, Handlers, Sensors, And Managers

Coroutine jobs:

- `coolDownJob`
- `activeUnlockJob`
- `graceTimerJob`
- `clipboardRescueJob`
- `tickerJob`

Handler:

- `handler = Handler(Looper.getMainLooper())`.
- `countdownRunnable` decrements `_sessionTimeLeftSeconds` once per second.

Sensors:

- `sensorManager`
- `gyroSensor = TYPE_GYROSCOPE`
- `accelSensor = TYPE_ACCELEROMETER`
- `stepCounterSensor = TYPE_STEP_COUNTER`
- `lastGyroX`
- `lastGyroY`
- `lastGyroZ`
- `initialStepCount`

Audio:

- `audioManager`
- `initialRingerMode`
- `previewRingtone`

Other:

- `lastBackgroundTime`
- `emergencyRequestStartTimestamp`
- `database`
- `repository`
- `prefs`
- `context`

## 7. Core Feature Mechanics

### 7.1 Initial App Flow

1. `MainActivity.onCreate` creates one `AnchorViewModel`.
2. It reads `isSealed` and `isOnboarded`.
3. If not sealed, user is sent to `FounderScreen`.
4. If sealed but not onboarded, user is sent to `OnboardingScreen`.
5. If both sealed and onboarded, user is sent to `DashboardScreen`.
6. If intent extra `open_anchor_lock_decoy = true` is present, `showDecoyShortcutScreen` is set and the dashboard immediately shows `AnchorLockDecoyContent`.
7. If `showReEntryShield` is true, the dashboard immediately shows `ReEntryLockoutOverlayContent` and returns.
8. If `showDecoyShortcutScreen` is true, the dashboard immediately shows `AnchorLockDecoyContent` and returns.
9. If `isFajrAlarmRinging` is true, the dashboard immediately shows `FajrAlarmOverlayContent` and returns.
10. If dashboard PIN is enabled and locked, the dashboard shows `AppPinGateContent` and returns.
11. Otherwise the normal dashboard tabs render.

Overlay priority in `DashboardScreen`:

1. Re-entry shield.
2. Anchor Lock decoy shortcut / emergency portal.
3. Fajr alarm overlay.
4. App PIN gate.
5. Normal dashboard.

### 7.2 Founder Setup

Screen: `FounderScreen`.

Authentication:

- Default input field starts with `5222`.
- Accepted passcodes:
  - `arrazi`
  - `8888`
  - `5222`
- Failed passcode sets `loginError = true`.

Polling:

- `LaunchedEffect(Unit)` loops every second.
- Calls `viewModel.checkHardwarePermissions()`.
- Refreshes `usageAccessGranted`.
- Refreshes `accessibilityAccessGranted`.

Setup gates:

- Device Admin must be granted.
- Usage Access must be granted.
- Accessibility Gesture Unlock must be granted.
- AppBlock hidden confirmation must be true.
- Uninstall PIN must be set.

Device Admin setup:

- Opens `viewModel.deviceAdminActivationIntent()`.
- Fallback opens `Settings.ACTION_SECURITY_SETTINGS`.
- Failure toast: `Could not open Device Admin settings.`

Usage Access setup:

- Opens `Settings.ACTION_USAGE_ACCESS_SETTINGS`.
- Failure toast: `Could not open settings.`

Accessibility setup:

- Opens `Settings.ACTION_ACCESSIBILITY_SETTINGS`.
- Supplies fragment args for component `com.example.service.AnchorAccessibilityService`.
- Fallback opens plain accessibility settings.
- Failure toast: `Could not open accessibility settings.`

AppBlock hide setup:

- Stored key: `appblock_hidden_confirmed`.
- Tries Samsung launcher settings action `com.sec.android.app.launcher.settings`.
- Fallback opens `Settings.ACTION_HOME_SETTINGS`.
- Last fallback toast instructs user to hide AppBlock from launcher.
- Confirmation button writes `appblock_hidden_confirmed = true`.
- Confirmation toast: `AppBlock marked as hidden. ...`

Uninstall PIN:

- 4 digits only.
- Stored as SHA-256 in `uninstall_pin_hash`.
- Toasts:
  - `Uninstall PIN saved.`
  - `Use exactly 4 digits.`

Seal:

- `SEAL THE ANCHOR` calls `viewModel.sealTheAnchor()`.
- Writes `is_sealed = true`.
- Shows snackbar `Anchor sealed. Begin client portal.`
- Calls onboarding navigation callback.

### 7.3 Onboarding

Screen: `OnboardingScreen`.

Steps:

- Step 1: NFC Tag Registration.
- Step 2: Sanctuary Setup.
- Step 3: Home baseline setup.
- Step 4: Evening Protocol Time.
- Step 5: Support Setup: Anchor Lock.

Step state:

- `currentStep` starts at 1.
- Progress indicator uses `currentStep / 5f`.
- Animated step content uses `AnimatedContent` with fade in/out and `SizeTransform(clip = false)`.

Step 1:

- Displays NFC registration card.
- Button `SCAN PHYSICAL TAG` calls `viewModel.registerMockNfcTag("ANCHOR_NFC_09X88")`.
- `nfcTagId` must be non-empty to continue.

Step 2:

- Sanctuary mode selector:
  - `mosque`
  - `runaway`
- Mosque mode:
  - Hardcoded Busaiteen mosque list with names, addresses, approximate distances, and coordinates.
  - Uses `EmbeddedInteractiveMapCanvas` with pins.
  - Selecting a mosque updates `selectedMosqueIndex`.
  - Saving uses `viewModel.saveDesignatedMosque(...)`.
- Runaway mode:
  - Saves sanctuary type and coordinates for workout route.

Step 3:

- Home baseline setup.
- Uses `EmbeddedInteractiveMapCanvas` in home-selection mode.
- Save center:
  - Calls `viewModel.setHomeBaselineManual(homeMapCenterLat, homeMapCenterLng)`.
  - Toast: `Home coordinates locked at center.`
- Skip:
  - Calls `viewModel.setHomeBaselineManual(26.2285, 50.5860)`.
  - Toast: `Using default Busaiteen coordinates.`

Step 4:

- Evening protocol cutoff.
- `selectedHour` starts at 22.
- `selectedMinute` starts at 0.
- Hour decrements only if `selectedHour > 18`.
- Hour increments only if `selectedHour < 23`.
- Minute decrements by 15 only if `selectedMinute >= 15`.
- Minute increments by 15 only if `selectedMinute <= 30`.
- On continue, writes `evening_protocol_time` using `String.format("%02d:%02d", selectedHour, selectedMinute)`.

Step 5:

- Anchor Lock setup.
- Calls `AppBlockIntegration.requestAnchorLockShortcut(context)`.
- If requested:
  - Calls `viewModel.markAnchorLockShortcutDone()`.
  - Toast: `Anchor Lock shortcut requested.`
- If AppBlock missing:
  - Toast: `The strict blocking layer is not installed yet.`
- If not supported:
  - Still calls `markAnchorLockShortcutDone()`.
  - Toast: `Pinned shortcuts are not supported on this launcher. Continue setup.`
- If AppBlock not installed, exposes `OPEN PLAY STORE`.

Final onboarding:

- `BEGIN PLATFORM` enabled only when `shortcutStepDone` is true.
- Calls `viewModel.completeOnboarding()` through MainActivity callback.
- Writes `is_onboarded = true`.

### 7.4 Main Dashboard Tabs

Dashboard bottom nav tabs:

- `RESERVE` labeled `SLEEP`.
- `ANCHOR` labeled `ANCHOR`.
- Sanctuary tab:
  - `MOSQUE` labeled `MOSQUE` when `sanctuaryType != "runaway"`.
  - `RUNAWAY` labeled `RUNAWAY` when `sanctuaryType == "runaway"`.
- `PROOF` labeled `RESERVE`.

Important naming mismatch:

- UI tab id `PROOF` hosts `AccountabilityDashboardScreen`, which is labeled as reserve/proof dashboard.
- UI label `RESERVE` appears on bottom nav for that tab.
- UI tab id `RESERVE` hosts sleep/fajr content and is labeled `SLEEP`.

Header:

- Hidden settings opens by visible settings icon in header.
- `selectedTab != "PROOF"` shows header.

Language:

- `isEnglish` is a local Compose state.
- Several strings branch between English and Arabic labels.

Theme:

- `appThemeMode` controls `isDarkThemeGlobal`.
- `Color.kt` uses dynamic getters based on global theme flag.

### 7.5 App PIN Gate

Settings:

- In dashboard settings sheet, tab `ACCESS`.
- A 4-digit app PIN can be saved and enabled.
- `setAppPin` stores hash and enables app PIN.
- `setAppPinEnabled` refuses to enable if no PIN is set.
- `clearAppPin` removes hash and disables app PIN.

Runtime lock:

- `onAppBackgrounded` records `lastBackgroundTime` only if app PIN enabled.
- `onAppResumed`:
  - Updates Fajr ringing state.
  - If app PIN enabled and `lastBackgroundTime == 0`, locks.
  - If elapsed since background >= 60 seconds, locks.
  - If app PIN disabled, unlocks.

Gate:

- `AppPinGateContent` collects input, verifies against SHA-256 hash, and unlocks ViewModel state.

### 7.6 Prayer Timing And Sleep Pressure

Prayer calculation:

- `PrayerTimesCalculator` computes Fajr, sunrise, Dhuhr, Asr, Maghrib, and Isha.
- Uses Julian date, solar declination, right ascension, equation of time, hour angles.
- Fajr altitude: -18 degrees.
- Isha altitude: -17 degrees.
- Sunrise/Maghrib altitude: -0.833 degrees.
- Asr uses shadow length calculation for Shafi/Maliki/Hanbali style.

Ticker:

- `startTicker()` launches loop every 1 second.
- `updateTimeTickers()`:
  - Updates Friday status.
  - Builds `PrayerTimesCalculator` from current latitude, longitude, timezone offset.
  - Calculates today and tomorrow prayer times.
  - Updates `prayerTimesMap`.
  - Picks next prayer target and sets `nextPrayerName`.
  - Formats countdown as `-%02d:%02d:%02d`.
  - Computes time to next Fajr.
  - Computes sleep pressure status.
  - Calls `scheduleFajrAlarm()` every tick.

Sleep pressure states:

- `NORMAL`: outside night discipline window.
- `NIGHT_WINDOW`: after Isha or before Fajr, and more than 6 hours remain.
- `LAST_WINDOW`: 5 to 6 hours remain.
- `MINIMUM`: 5 hours or less remain.

Fajr alarm scheduling:

- If `fajrAlarmEnabled` false, does nothing.
- Computes next Fajr.
- Warning: next Fajr minus 5 hours.
- Alarm: next Fajr minus 5 minutes.
- Schedules warning request code `1002`.
- Schedules alarm request code `1001`.
- Uses `setExactAndAllowWhileIdle` on API 23+.
- Uses `set` below API 23.
- Cancel uses matching request codes and actions.

Potential migration caution:

- Because `scheduleFajrAlarm()` is called every second inside `updateTimeTickers()`, Android currently repeatedly overwrites the same exact alarms. iOS should schedule idempotently and avoid excessive rescheduling.

### 7.7 Fajr Overlay And Wake Proof

Trigger:

- `FajrAlarmReceiver.triggerFajrAlarm()` writes `fajr_alarm_ringing = true`.
- `MainActivity.onResume()` calls `viewModel.onAppResumed()`, which reads `fajr_alarm_ringing`.
- If `isFajrAlarmRinging` true, Dashboard shows `FajrAlarmOverlayContent`.

Overlay state:

- Local `ayahInput`.
- Local `soundSilenced`.
- `ayahMatched = ayahInput.trim() == FAJR_WAKE_AYAH`.

Ayah constant:

- `FAJR_WAKE_AYAH` is stored as Unicode escape text in `DashboardScreen.kt`:
  - `\u062A\u064E\u062A\u064E\u062C\u064E\u0627\u0641\u064E\u0649\u0670 \u062C\u064F\u0646\u064F\u0648\u0628\u064F\u0647\u064F\u0645\u0652 \u0639\u064E\u0646\u0650 \u0627\u0644\u0652\u0645\u064E\u0636\u064E\u0627\u062C\u0650\u0639\u0650`

Buttons:

- `SILENCE`
  - Calls `viewModel.silenceCurrentFajrAlarm()`.
  - Sets `soundSilenced = true`.
  - Does not dismiss alarm state.
- `DISMISS`
  - Enabled if no ayah constraint or exact ayah match.
  - Calls `viewModel.dismissFajrAlarm()`.
  - `dismissFajrAlarm()` calls `markFajrWakeProof()`, writes `fajr_alarm_ringing = false`, updates state, and stops current Fajr alarm.
  - `markFajrWakeProof()`:
    - Builds key `fajr_wake_verified_$todayStr`.
    - If already true, returns false.
    - If not true, writes key true, sets `fajrWakeVerified`, and adds +25 points with action `Fajr Wake Proof`, status `sleep`.
  - Toast:
    - If awarded: `Wake proof complete. +25 Reserve Points.`
    - Else: `Fajr alarm dismissed.`

Reserve tab integration:

- Fajr row label is `Fajr Wake Proof`.
- If `fajrWakeVerified`, displays `WAKE VERIFIED +25`.
- If not, displays `Dismiss alarm to earn wake credit`.

Proof dashboard integration:

- Today's Pillars sleep row is titled `WAKE PROTOCOL`.
- It is complete only when `fajrWakeVerified` is true.

### 7.8 Fajr Settings

Location:

- Dashboard settings sheet, tab `FAJR`.

Controls:

- Fajr discipline alarm toggle.
- Scheduled alarm sound card.
- Import custom ringtone.
- Preview ringtone.
- Reset to built-in.
- Ayah wake challenge toggle.
- Enable/disable accountability PIN.

OpenDocument:

- Uses `ActivityResultContracts.OpenDocument()`.
- Launches with MIME `audio/*`.
- Attempts `takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)`.
- Gets display name via `OpenableColumns.DISPLAY_NAME`.
- Calls `viewModel.setFajrAlarmRingtone(uri.toString(), label)`.
- Toast: `Fajr ringtone imported.`

Accountability PIN:

- Separate from app PIN.
- 4 digits only.
- Stored as SHA-256 in `fajr_accountability_pin_hash`.
- If enabled, changes to alarm or ayah challenge require successful `verifyFajrAccountabilityPin`.
- `toggleFajrAlarm()` refuses direct toggle and toasts `Fajr enforcement is protected by the accountability PIN.`

### 7.9 Anchor Session Canonical Flow

Canonical current screen:

- `DashboardScreen.AnchorTabContent`.

Compiled legacy screen:

- `ProtocolScreen` still exists and compiles, but MainActivity currently passes empty `onNavigateToProtocol` and does not route into it. It should be treated as legacy/alternate code, not the canonical current product path.

Dashboard Anchor stages:

- `DURATION`
- `PRE_FLIGHT`
- `TAP_1`
- `SESSION`
- `TAP_2`
- `CLOSING`
- `VERIFIED`

Stage sync:

- `LaunchedEffect(isSessionActive, timeLeftSeconds, sessionVoidedMessage)`:
  - If `sessionVoidedMessage != null`, sets `anchorStep = "DURATION"`.
  - If `isSessionActive`, sets `anchorStep = "SESSION"`.
  - If was `SESSION` and time left is 0, sets `anchorStep = "TAP_2"`.

Duration choices:

- 25 minutes: `Short`, one pomodoro / planning sprint.
- 45 minutes: `Standard`, deep block / most days.
- 55 minutes: `Anchor`, full ritual / max reward.
- All currently reward +25 points.

Previous whiteboard gallery:

- Data source: `whiteboardSessions`.
- Header: `PREVIOUS WHITEBOARD`.
- Count label: `${whiteboardSessions.size} THREADS`.
- Empty state says no whiteboard thread yet and explains tomorrow starts from the photographed sentence.
- Gallery cards:
  - Date.
  - `PHOTO` if `photoStatus != "skipped"`, else `NOTE`.
  - `WhiteboardPhotoFrame(imagePath = session.imagePath, note = session.userNote or fallback)`.
  - Duration compliance label.
- Dialog:
  - Title: `THE THREAD YOU LEFT`.
  - Narrative: `Yesterday handed you this line. Continue from here, not from zero.`
  - Shows photo frame or grid/note fallback.
  - Metadata: duration, +25 points, NFC tag.
  - Confirm button copies `session.userNote` to clipboard.
  - Toast: `Copied incomplete sentence!`

Pre-flight:

- Heading: `Place phone face-down.`
- Large emphasis: `THINK ON PAPER.`
- Checklist:
  - `Phone face-down on the Anchor`
  - `Whiteboard or notebook open`
  - `Physical timer set`
- Continue button enabled only when all three checklist items are true.

Tap 1:

- Button test tag: `simulate_tap1_btn`.
- Resets closing note and whiteboard capture temp state.
- Calls `viewModel.startAnchorTabTapOneSession(chosenDuration)`.
- Current ViewModel test mode:
  - `ANCHOR_TAP_ONE_TEST_MODE = true`.
  - `ANCHOR_TAP_ONE_TEST_DURATION_SEC = 30`.
  - Session display says real session would be the chosen duration.
- Toast:
  - Test mode: `Test session registered: 30 seconds. Phone muted.`
  - Production mode: `Session registered! Phone muted.`

Session:

- Displays `SESSION IN PROGRESS`.
- Large monospaced timer.
- Progress line uses 30 seconds in test mode or chosen duration in production mode.
- Warns `PHONE LOCKED TO ANCHOR`.
- Any physical movement above gyroscope threshold fails the session.

Session engine:

- `startFocusSession(durationMinutes)` calls private `startFocusSession(durationMinutes, countdownSeconds)`.
- Sets `sessionStartTime = System.currentTimeMillis()`.
- Writes `is_session_active = true`.
- Stores total duration minutes.
- Sets countdown seconds.
- Sets active true.
- Clears `sessionVoidedMessage`.
- Calls `silencePhone()`.
- Registers gyroscope and accelerometer listeners with `SENSOR_DELAY_NORMAL`.
- Starts handler countdown.

Movement void:

- `onSensorChanged` checks gyroscope only when session active.
- Threshold: `0.35f`.
- If abs x, y, or z exceeds threshold, calls `failFocusSession("Session voided. Physical movement occurred")`.
- Accelerometer is registered but not currently used for void logic.

Tap 2:

- Shows `VALIDATION WINDOW`.
- Local `tap2Timer` starts at 60.
- `LaunchedEffect(Unit)` decrements once per second.
- When it reaches 0, calls `viewModel.failFocusSession("TAP_2 window expired")`.
- It also calls `onStepChange("INITIAL")`; note `INITIAL` is not one of the canonical current stages, so this is a minor mismatched fallback state.
- Button test tag: `simulate_tap2_btn`.
- Button moves to `CLOSING`.

Closing protocol:

- Requires a photo before sealing.
- Local state:
  - `closingNote`
  - `capturedWhiteboardPath`
  - `pendingWhiteboardPath`
  - `pendingWhiteboardUri`
- Camera:
  - Uses `ActivityResultContracts.TakePicture()`.
  - Uses `ActivityResultContracts.RequestPermission()` for `Manifest.permission.CAMERA`.
  - Creates file through `ProofFileStore.createProofImageFile(context, "whiteboard_reflection")`.
  - Gets URI through `ProofFileStore.uriForFile`.
  - If capture succeeds, sets `capturedWhiteboardPath = pendingWhiteboardPath`.
  - If capture fails, toast: `Whiteboard photo was not captured.`
  - If permission denied, toast: `Camera permission is required for the whiteboard photo.`
- Text field:
  - Placeholder asks for exact unfinished sentence left on the whiteboard.
  - Max lines 3.
- Preview:
  - `WhiteboardPhotoFrame` with actual photo if captured.
  - Fallback grid with note.
- Seal:
  - Button `SEAL WHITEBOARD SESSION` enabled only when `capturedWhiteboardPath != null`.
  - Calls `viewModel.completeFocusSession(photoStatus = "archived", userNote = cleanNote, imagePath = capturedWhiteboardPath)`.
  - Moves to `VERIFIED`.

Completion:

- `completeFocusSession`:
  - Writes `is_session_active = false`.
  - Stops handler countdown.
  - Unregisters sensors.
  - Restores audio mode.
  - Adds +25 points.
  - SessionLog action: `Completed ${duration}m Eraser Protocol`.
  - SessionLog status: `complete`.
  - Logs start/end/duration/NFC/photo status/user note/image path.
  - Increments `protocol_streak`.
  - Recalculates habit compliance.

Failure:

- `failFocusSession(reason)`:
  - Writes `is_session_active = false`.
  - Sets active false.
  - Stops countdown.
  - Unregisters sensors.
  - Restores audio.
  - Sets `sessionVoidedMessage = reason`.
  - Deducts 35 points with action `Failed Protocol: $reason`, status `missed`.
  - Sets `is_locked = true`.

### 7.10 Legacy `ProtocolScreen`

Status:

- Compiled.
- Not currently routed by MainActivity/Dashboard normal callbacks.

Stages:

- `TAP_1`
- `ACTIVE_COUNTDOWN`
- `CLOSURE_SCREEN`
- `CAMERA_VIEWPORT`
- `PHOTO_PREVIEW`
- `COMPLETED`

Mechanics:

- Chooses 25/45/55 minutes.
- Requires `nfcTagId` to start; otherwise toasts no eraser registered.
- Starts focus session via `viewModel.startFocusSession(selectedDuration)`.
- Has `ABORT` path that calls `failFocusSession("Session aborted by user request.")`.
- Has debug `ACCELERATE TIME` button that uses reflection to access private `_sessionTimeLeftSeconds` and set it to 3.
- On session completion, plays default alarm or notification ringtone.
- Has mock camera viewport with grid, focus brackets, shutter flash, photo preview, and privacy options:
  - Save privately on this device.
  - Continue without saving photo.
  - Save to session archive.
- Completion can call `completeFocusSession` with `photoStatus` based on privacy selection.

Preservation note:

- The current Anchor tab has replaced the mock camera with real `TakePicture`; however, this legacy screen still contains useful UI ideas and a reflection-based test acceleration path that should not be mistaken for the production canonical path.

### 7.11 Mosque Sanctuary

Screen: `MosqueTabContent`.

State:

- `fajrVerified`
- `maghribVerified`
- `activeMosqueSelection`
- `activeNavigationMosqueName`
- `isRetreatActive`
- `retreatSecondsRemaining`

Sanctuary toggle:

- Can switch sanctuary type between `mosque` and `runaway`.
- Mosque selection saves default Busaiteen coordinates.
- Runaway selection saves runaway coordinates.

Nearby mosques:

- Hardcoded Busaiteen list with mosque name, address, coordinate pair.
- Uses `EmbeddedInteractiveMapCanvas`.
- Selecting a mosque opens an in-app selection dialog/sheet.
- Can pin a designated mosque via `viewModel.saveDesignatedMosque`.
- Can calculate a route in-app by setting active navigation mosque name.
- Toasts include:
  - Designated mosque pinned.
  - Route calculated.
  - Navigation stopped.

Fajr check-in:

- If not verified, `VERIFY` opens GPS verification flow with type `FAJR`.
- On completion, `verifyFajrPresence()` writes `fajr_verified_$today` and adds +40 points.

Maghrib-to-Isha retreat:

- Duration is computed from current prayer times `maghrib` and `isha`; fallback 70 minutes.
- Starting retreat sets `isRetreatActive`.
- Local loop decrements `retreatSecondsRemaining` every second.
- When it reaches 0, adds +40 points with action `Completed Maghrib-Isha Retreat`, status `retreat`.
- `SIMULATE BREAK`:
  - Stops retreat.
  - Calls `viewModel.failFocusSession("Left Mosque Geofence during Retreat")`.
  - Toast: `Retreat broken. Phone locked!`
- Completion toast: `Retreat Complete! +40 pts`.

### 7.12 GPS Verification Sheet

Location:

- Dashboard animated bottom sheet.

State:

- `loadingStage`: `LOCATING`, `RESOLVED`, `GPS_OFF`.
- `gpsVerificationType`: `FAJR` or `MAGHRIB`.
- `mockLat`, `mockLng`.
- `satLockProgress`.
- `currentStatus`.
- `isGpsActive`.

When shown:

- Checks GPS/network provider enabled via `LocationManager`.
- If active:
  - Runs 25 animation ticks, each delayed 80 ms.
  - Perturbs mock lat/lng slightly.
  - Updates progress.
  - Changes status text across cellular nodes, atomic clock sync, differential distance, geofence lock acquired.
  - Sets `loadingStage = RESOLVED`.
- If inactive:
  - Sets `loadingStage = GPS_OFF`.

GPS off path:

- Shows warning.
- Button opens `Settings.ACTION_LOCATION_SOURCE_SETTINGS`.
- Re-check button checks providers again.
- If still off, toast: `GPS is still off. Please enable it in Settings.`

Resolved path:

- Calculates distance with `viewModel.calculateDistance`.
- Completion enabled when distance <= 100 meters.
- Note: one UI text says must be under 200m while code uses 100m; the effective behavior is 100m.
- On complete:
  - If type `FAJR`, calls `viewModel.verifyFajrPresence()`.
  - Else calls `viewModel.verifyMaghribPresence()`.
  - Toast: `Verification complete! +40 Reserve Credits.`

### 7.13 Runaway Workout

Screen: `RunawayTabContent`.

Status:

- Only shown when `sanctuaryType == "runaway"` because dashboard bottom nav switches the sanctuary tab ID.
- Comment says it is implemented but not shown when no tab button; current nav can show it if sanctuary type changes.

State:

- `reservePoints`
- `stepsCount`
- `workoutMode`
- `stepGoal`
- `workoutStep`

Workout mode:

- `run`: 2,000 steps.
- `walk`: 4,000 steps.
- Changing mode writes `workout_mode`, resets steps, and resets `initialStepCount`.

Step counter:

- `TYPE_STEP_COUNTER` listener is registered in init with `SENSOR_DELAY_UI`.
- On first event, records `initialStepCount`.
- On later events, computes difference from baseline and calls `addSteps` for delta beyond current.
- When step goal reached:
  - Adds +30 points.
  - Sets lock false.
  - Writes `is_override_active = true`.
  - Increases `runningConsistencyScore` by 5 up to max 100.
  - Recalculates habit compliance.

UI:

- Shows hard block warning if reserve points <= 0.
- Embedded map shows workout loop if `isRunawayMode = true`.
- Progress bar has three segments: start, midpoint, goal.
- `WAITING FOR PHONE MOTION SENSOR` until complete.
- Complete state says +30 points earned.

### 7.14 Survival Screen

Status:

- Compiled.
- Navigation callback exists in MainActivity but currently Dashboard passes empty `onNavigateToSurvival`.
- Should be treated as compiled fallback/hard-block surface, not primary currently routed surface.

Mechanics:

- Displays hard boundary warning.
- Reads sanctuary coordinates directly from `analog_anchor_prefs`.
- Calculates distance from current location to sanctuary.
- If reserve points <= 0, shows hard lock warning.
- Verify location button calls `viewModel.verifyMosquePresence()`.
- If verified:
  - Toast: `Sanctuary verified! +50 credits. Access restored.`
  - Calls dashboard navigation callback.
- If not:
  - Toast includes current distance and 100m requirement.
- Shows analog replacement actions:
  - Journal prompt.
  - Physical book.
  - Mosque prayer.
- Opens survival kit URL: `https://get-analog-anchor.com/survivalkit`.

### 7.15 Reserve / Proof Dashboard

Screen: `AccountabilityDashboardScreen`, rendered inside dashboard `PROOF` tab.

State collected:

- `anchorStreak`
- `pendingReturnSession`
- `openRecoveryRecord`
- `activeRuleFlagCount`
- `activeRuleFlags`
- `reservePoints`
- `isLocked`
- `emergencyUnlockRequestCount`
- `sessionLogs`
- `stepsCount`
- `fajrVerified`
- `fajrWakeVerified`
- `maghribVerified`
- `workoutMode`
- `mosqueComplianceStreak`
- `protocolComplianceStreak`
- `habitScore`

On launch:

- Calls `viewModel.refreshExpiredUnlockSessions()`.
- Refreshes `usageAccessGranted`.

Today's pillars:

- Anchor Session:
  - Complete if any session log for today has `status == "complete"`.
- Mosque:
  - Complete if `fajrVerified || maghribVerified || stepsCount >= stepGoal`.
- Wake Protocol:
  - Complete if `fajrWakeVerified`.

Reserve balance:

- Shows `reservePoints`.
- Shows 16 segment bar using `reservePoints / 12.5`, clamped 1 to 16.
- Shows metrics:
  - Streak: protocol compliance streak.
  - Access: `Closed` if locked, else `Open`.
  - Last Fajr: mosque compliance streak.
- Behavior habit index: `habitScore`.

History:

- Shows latest session logs.
- Empty state: no events logged.

Behavior alerts:

- Hidden if no flags.
- Expand/collapse by tapping card.
- Lists each flag message.
- Severity indicator:
  - `WARN`: `!`
  - `BLOCK`: `?`
  - Else: `i`
- Each flag can be dismissed via `viewModel.dismissRuleFlag(flag.id)`.

Usage Access offline:

- If not granted, shows card and `OPEN USAGE ACCESS`.

Pending return checkpoint:

- If `pendingReturnSession != null`, shows return checkpoint card.
- Button calls `onNavigateToReturn`.

Recovery required:

- If open recovery exists, shows recovery card.
- Button calls `onNavigateToRecovery`.

Hidden emergency portal:

- Text `Need emergency access?`.
- Each tap increments `hiddenAccessClicks`.
- After 3 taps, `showHiddenButton = true`.
- Reveals `ACCESS EMERGENCY PORTAL`.
- Button resets hidden state and calls `viewModel.setShowDecoyShortcutScreen(true)`.

### 7.16 Return Proof Screen

Screen: `ReturnProofScreen`.

Status:

- Compiled.
- Navigation callbacks currently exist in `AccountabilityDashboardScreen`, but dashboard local `proofFlowScreen` must route to it for it to be visible. Confirm current Dashboard routing before relying on it.

Mechanics:

- Reads `pendingReturnSession`.
- If none, shows no active review message and back button.
- If pending:
  - Shows deadline.
  - Uses `ProofCaptureControls` with `prefix = "appblock_after"` and `cameraEnabled = false`.
  - This means upload/gallery is available, but camera capture is intentionally disabled in this screen.
  - Optional note limited to 180 chars.
  - Submit calls `viewModel.submitReturnProof`.
  - Compliant outcome calls done callback.
  - Breach outcome calls recovery callback.

### 7.17 Recovery Screen

Screen: `RecoveryScreen`.

Mechanics:

- Reads `openRecoveryRecord`.
- Shows recovery reason or clear state.
- Uses `ProofCaptureControls` with `prefix = "recovery_proof"` and `cameraEnabled = false`.
- Optional note limited to 180 chars.
- Complete calls `viewModel.resolveRecoveryWithProof(path, note)` and done callback.

### 7.18 Proof Capture Controls

Composable: `ProofCaptureControls`.

State:

- `pendingCameraPath`
- `pendingCameraUri`

Camera path:

- Uses `ActivityResultContracts.TakePicture`.
- Uses `ActivityResultContracts.RequestPermission`.
- Creates file with `ProofFileStore.createProofImageFile(context, prefix)`.
- Gets URI through FileProvider.
- If CAMERA granted, launches TakePicture.
- If permission denied, toast: `Camera permission is required for capture.`
- Only visible if `cameraEnabled = true`.

Gallery path:

- Uses `ActivityResultContracts.GetContent`.
- Launches with `image/*`.
- Copies selected URI to proof file.
- If copy fails, toast: `Could not import image.`

Preview:

- If image path decodes, shows image cropped.
- Else shows no image selected placeholder.

### 7.19 Review Card Renderer

File: `ReviewCardRenderer.kt`.

Data:

- `clientName`
- `requestedAt`
- `proofImagePath`
- `beforeInsightsPath`
- `proofNote`
- `streakDays`
- `requestsThisWeek`
- `recoveryStatus`
- `activeFlags`

Rendering:

- Bitmap size: 1200 x 1720.
- Background: dark.
- Card: rounded rectangle.
- Header:
  - `ANALOG ANCHOR`.
  - `Unlock review context`.
  - Client name.
  - Requested date.
- Sections:
  - Proof Submitted.
  - Strict-Mode Insights - Before.
  - Compliance Summary.
  - Support Action.
- Images:
  - If bitmap path decodes, draws thumbnail.
  - Else placeholder text `IMAGE`.
- Saves via `ProofFileStore.saveBitmapToCardFile`.

Share:

- `shareReviewCard` sends `Intent.ACTION_SEND`.
- `viaWhatsApp = true` uses MIME `image/jpeg` and package `com.whatsapp`.
- Else uses MIME `message/rfc822`.
- Adds subject, text, stream URI.
- Adds grant read URI flag.
- Catches `ActivityNotFoundException`, toast: `No compatible app found for sharing.`

## 8. Auto-Fill And Injection Lifecycle

This is the most platform-specific Android mechanism. It has three layers:

1. Token decode and custody in `AnchorViewModel`.
2. Encrypted pending/re-entry PIN storage in `PendingAutofillPinStore`.
3. AppBlock entry through `AnchorAccessibilityService` using either `ACTION_SET_TEXT` or gesture taps.

There is also a last-resort clipboard rescue path that is intentionally less safe.

### 8.1 Trigger Surface: Anchor Lock Shortcut And Hidden Portal

Shortcut creation:

- `AppBlockIntegration.requestAnchorLockShortcut(context)`.
- Requires API 26+.
- Requires `ShortcutManager.isRequestPinShortcutSupported`.
- Requires a known AppBlock package installed.
- Builds shortcut:
  - ID: `anchor_lock_shortcut`.
  - Short label: `Anchor Lock`.
  - Long label: `Anchor Lock - Strict Mode`.
  - Icon: `R.drawable.ic_anchor_lock`.
  - Intent:
    - `MainActivity`
    - action `Intent.ACTION_RUN`
    - extra `open_anchor_lock_decoy = true`
    - flags `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`

Shortcut launch:

- `MainActivity.onCreate` or `onNewIntent` sees `open_anchor_lock_decoy`.
- Calls `viewModel.setShowDecoyShortcutScreen(true)`.
- `DashboardScreen` returns immediately into `AnchorLockDecoyContent`.

Hidden portal:

- In `AccountabilityDashboardScreen`, triple tap `Need emergency access?`.
- Revealed button calls `viewModel.setShowDecoyShortcutScreen(true)`.
- Same `AnchorLockDecoyContent` path.

### 8.2 Emergency State Machine

States:

- `LOCKED`
- `COOLING`
- `READY_TO_REQUEST`
- `REQUESTED`
- `ACTIVE_UNLOCKED`
- `RE_ENTRY`

Restore:

- `AnchorViewModel.init` calls `restoreEmergencyState()`.
- Reads `emergency_state`.
- If `COOLING`, calculates remaining from `emergency_cooldown_start_epoch`.
- If `REQUESTED`, checks token freshness from `emergency_token_timestamp`.
- If `ACTIVE_UNLOCKED`, calculates remaining from `emergency_active_unlock_expires_epoch`.
- If `RE_ENTRY`, calculates remaining from `emergency_grace_expires_epoch`.
- If expired or invalid, locks or moves forward appropriately.

Start cooldown:

- UI button in `LOCKED` state calls `startEmergencyCoolDown()`.
- Cancels active unlock, grace, and clipboard rescue jobs.
- Clears clipboard.
- Clears secure autofill state.
- Sets state `COOLING`.
- Sets remaining to `COOLDOWN_DURATION_SEC`.
- Current test value: 120 seconds.
- Writes:
  - `emergency_state = COOLING`
  - `emergency_cooldown_start_epoch = now`
  - removes token timestamp, active unlock expiry, grace expiry
  - increments `emergency_unlock_count`
- Coroutine decrements once per second.
- When remaining reaches 0:
  - Writes `emergency_state = READY_TO_REQUEST`
  - Removes cooldown epoch.
  - Sets state `READY_TO_REQUEST`.

Cancel cooldown:

- Calls `lockEmergencyState()`.

Request code:

- UI button in `READY_TO_REQUEST` calls `requestEmergencyUnlockPayload()`.
- Sets `emergencyState = REQUESTED`.
- Sets `emergencyRequestStartTimestamp = now`.
- Increments `unlock_requests_this_week`.
- Writes:
  - `emergency_state = REQUESTED`
  - `emergency_token_timestamp = now`
  - removes cooldown, active, grace expiries
- Returns `getClientName()`.

Token freshness:

- Token window starts when the code is requested, not when the cooldown starts.
- `TOKEN_VALIDITY_MS = 60 minutes`.
- If stale, `decodeAuthorizedPin` calls `lockEmergencyState()` and returns null.

### 8.3 Token Entry UI Mechanics

Screen: `AnchorLockDecoyContent`, state `REQUESTED`.

Local state:

- `clipboardToken`
- `tokenInput`
- `showManualRescue`
- `manualRescueArmed`
- `accessTokenPattern = Regex("^AA-[QWERTASDFG]{4,8}-EXP60$")`

Clipboard detection:

- `LaunchedEffect(emergencyState)` runs when state changes.
- If `emergencyState == "REQUESTED"`:
  - Reads Compose `LocalClipboardManager.getText()?.text`.
  - Normalizes through `viewModel.normalizeAccessTokenInput`.
  - If normalized text matches token pattern, stores in `clipboardToken`.
  - Else clears `clipboardToken`.
- If state not requested:
  - Clears `clipboardToken`.
  - Hides manual rescue.
  - Clears manual rescue armed flag.

Clipboard stale wipe:

- `LaunchedEffect(clipboardToken)`.
- If token detected:
  - Waits 60 seconds.
  - Reads current clipboard and normalizes.
  - If clipboard still equals detected token, calls `viewModel.clearClipboard()`.
  - On exception, also clears clipboard.
  - Clears `clipboardToken` if still same.

Paste chip:

- Visible when `clipboardToken` not blank.
- On click:
  - Copies normalized clipboard token into `tokenInput`.
  - Calls `viewModel.clearClipboard()`.
  - Clears clipboard token.
  - Hides manual rescue and clears armed state.

Manual text field:

- `onValueChange` normalizes input immediately through `normalizeAccessTokenInput`.
- If normalized input matches token pattern and current clipboard equals normalized input, clears clipboard.
- Hides manual rescue and clears armed state.
- Keyboard options:
  - Capitalization characters.
  - ASCII keyboard.
- Placeholder: `AA-AEEE-EXP60`.

Trailing paste icon:

- Reads clipboard.
- Normalizes and sets `tokenInput`.
- If normalized token matches pattern, clears clipboard.
- On exception, toast: `Clipboard empty or inaccessible`.

Authorize button:

- Enabled when `tokenInput` is not blank.
- Calls `viewModel.authorizeMaskedToken(tokenInput)`.
- If success:
  - Hides manual rescue.
  - Toast: `Access authorized. Secure auto-fill armed.`
  - Launches AppBlock through `AppBlockIntegration.appBlockLaunchIntent`.
- If failure:
  - Calls `viewModel.isMaskedTokenUsable(tokenInput)`.
  - If token decodes but secure autofill not ready:
    - Shows manual rescue.
    - Toast: `Secure auto-fill is not ready. Manual rescue is available as last resort.`
  - Else toast: `Invalid or expired Analog Anchor token.`

Manual rescue card:

- Only visible after secure autofill arming fails but token is otherwise usable.
- Explains clipboard risk.
- Button calls `authorizeMaskedTokenWithClipboardRescue(tokenInput)`.
- If success:
  - Sets `manualRescueArmed = true`.
  - Toast: `PIN copied for 30 seconds. Paste once, then return.`
  - Launches AppBlock.
- Else toast: `Manual rescue failed. Token expired or invalid.`

### 8.4 Token Normalization And Decode

`authorizeMaskedToken(token)`:

- Normalizes token with `normalizeMaskedToken`.
- Requires regex `^AA-([QWERTASDFG]{4,8})-EXP60$`.
- Decodes PIN.
- Calls `setupBypassAutofill(decodedPin)`.
- Calls `PendingAutofillPinStore.rememberReEntryPin(context, decodedPin, RE_ENTRY_PIN_CUSTODY_MS)`.
- Clears clipboard.
- Starts active unlock countdown with default initial clipboard clear delay of 3 seconds.

`normalizeMaskedToken(token)`:

- Applies Unicode normalization `NFKC`.
- Converts all Unicode digit characters to ASCII digits.
- Uppercases using `Locale.US`.
- Replaces Unicode dash variants with ASCII `-`:
  - `\u2010`
  - `\u2011`
  - `\u2012`
  - `\u2013`
  - `\u2014`
  - `\u2212`
  - `\uFE63`
  - `\uFF0D`
- Removes whitespace and invisible formatting characters matching:
  - `\s`
  - `\u00A0`
  - `\u061C`
  - `\u200B`
  - `\u200C`
  - `\u200D`
  - `\u200E`
  - `\u200F`
  - `\u202A-\u202E`
  - `\u2066-\u2069`
  - `\uFEFF`
- Finds loose token pattern `AA-?([QWERTASDFG]{4,8})-?EXP60`.
- Returns canonical `AA-${encoded}-EXP60` if found; otherwise returns compact input.

`decodeTokenPin(encoded)`:

- Reverse map:
  - `Q -> 0`
  - `W -> 1`
  - `E -> 2`
  - `R -> 3`
  - `T -> 4`
  - `A -> 5`
  - `S -> 6`
  - `D -> 7`
  - `F -> 8`
  - `G -> 9`
- Returns null if any char not in map.

### 8.5 Data Passage From Token Screen To Accessibility Service

Main success path:

1. User enters or pastes support token into `AnchorLockDecoyContent`.
2. UI calls `viewModel.authorizeMaskedToken(tokenInput)`.
3. ViewModel normalizes token.
4. ViewModel validates token shape.
5. ViewModel checks token freshness.
6. ViewModel decodes encoded characters to real PIN.
7. ViewModel calls `setupBypassAutofill(decodedPin)`.
8. `setupBypassAutofill` validates PIN shape: 4 to 8 digits.
9. It verifies accessibility service is enabled via `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`.
10. It calls `PendingAutofillPinStore.armPin(context, cleanPin, ttlMs = 90_000)`.
11. `PendingAutofillPinStore` writes encrypted:
    - `pending_autofill_pin`
    - `pending_autofill_expires_epoch`
12. It clears legacy plain keys from `analog_anchor_prefs`.
13. ViewModel calls `AnchorAccessibilityService.setPendingPin(cleanPin)`.
14. Static service memory gets `pendingPin`.
15. ViewModel calls `PendingAutofillPinStore.rememberReEntryPin(context, decodedPin, 75 minutes)`.
16. Encrypted re-entry custody keys are written.
17. ViewModel clears clipboard.
18. ViewModel starts 1-hour active unlock countdown.
19. UI launches AppBlock.
20. Accessibility service sees AppBlock window/content change event.
21. Service reads `pendingPin`; if process memory lost, reads encrypted `PendingAutofillPinStore.getPendingPin(applicationContext)`.
22. Service injects PIN through `ACTION_SET_TEXT` or gesture taps.
23. On success, service clears encrypted pending PIN and in-memory pending PIN.

Re-entry path:

1. Active unlock countdown ends or user manually triggers re-entry.
2. ViewModel enters `RE_ENTRY` and shows re-entry shield.
3. User presses `OPEN BLOCKING APP`.
4. UI calls `viewModel.clearClipboard()`.
5. UI calls `viewModel.armBlockingAppReEntryAutofill()`.
6. ViewModel reads `PendingAutofillPinStore.getReEntryPin(context)`.
7. If present and not expired, ViewModel calls `setupBypassAutofill(rememberedPin)`.
8. Pending PIN is re-armed with 90-second TTL.
9. UI launches AppBlock.
10. Accessibility service injects the same PIN again.

### 8.6 Clipboard Layer

Clipboard is not the primary path. It has two functions:

- Clear support token or PIN from clipboard.
- Last-resort rescue if accessibility autofill is not ready.

`clearClipboard()`:

- Gets Android `ClipboardManager`.
- Sets primary clip to `ClipData.newPlainText("cleared", "")`.
- Logs success or failure.

Auto-clears:

- After token success: immediate clear.
- In active unlock countdown: clear after initial delay (3 seconds normal, 30 seconds rescue).
- When active unlock expires: clear.
- On re-entry trigger: clear.
- On lock state reset: clear secure autofill state and clears clipboard from callers.
- On failed rearm: clear.
- On successful rearm confirmation: clear.
- Token screen clipboard-token detection clears token after 60 seconds if still present.

Manual rescue:

- `authorizeMaskedTokenWithClipboardRescue(token)`:
  - Decodes token.
  - Calls `writeClipboardPinTemporarily(decodedPin)`.
  - Stores re-entry PIN custody.
  - Logs last-resort rescue.
  - Starts active countdown with `initialClipboardClearDelayMs = 30_000`.
- `writeClipboardPinTemporarily(pin)`:
  - Cancels existing clipboard rescue job.
  - Sets clipboard to `ClipData.newPlainText("manual_rescue", pin)`.
  - Launches coroutine to wait 30 seconds.
  - Clears clipboard and logs wipe.

### 8.7 Accessibility Event Listener Lifecycle

`onServiceConnected()`:

- Sets `isServiceActive = true`.
- Logs connected.

`onDestroy()`:

- Cancels service coroutine scope.
- Sets `isServiceActive = false`.

`onAccessibilityEvent(event)`:

1. Reads `event.packageName`.
2. Returns immediately if package is not a known AppBlock package.
3. Reads `rootInActiveWindow`; returns if null.
4. If event type is `TYPE_WINDOW_STATE_CHANGED`, dumps accessibility tree via `logTree`.
5. Gets PIN from in-memory `pendingPin` or encrypted persisted pending pin.
6. Returns if no pin, `hasTyped`, or `isTyping`.
7. Accepts only:
   - `TYPE_WINDOW_STATE_CHANGED`
   - `TYPE_WINDOW_CONTENT_CHANGED`
8. Sets `isTyping = true`.
9. Launches coroutine:
   - Delays `ENTRY_SETTLE_DELAY_MS = 260`.
   - Reads latest `rootInActiveWindow`.
   - If root disappeared, logs and clears `isTyping`.
   - Checks `looksLikePinEntryScreen`.
   - If not a pin screen, logs and clears `isTyping`.
   - Calls `enterPin(latestRoot, pin)`.
   - If entry did not start/complete, clears `isTyping`.

`loadPersistedPendingPin()`:

- Reads `PendingAutofillPinStore.getPendingPin(applicationContext)`.
- If stored, sets static `pendingPin`, resets `hasTyped` and `isTyping`.

`clearPendingPin()`:

- Clears in-memory pending pin.
- Calls `PendingAutofillPinStore.clear(applicationContext)`.

### 8.8 Pin Screen Detection

`looksLikePinEntryScreen(root, pin)`:

- Standard edit path:
  - `findStandardEditTextNode(root) != null`
  - and `hasCodeEntryHint(root)`.
- If standard edit path true, returns true.
- Else:
  - Gets digit targets.
  - Checks all unique digits in the pin are present.
  - Checks `digitTargets.size >= 8`.
  - Returns true if needed digits present and either keypad-like or code hint present.

`hasCodeEntryHint(root)` scans text and content descriptions for:

- `enter code`
- `pin`
- `password`
- `unlock`
- `verify code`

`findStandardEditTextNode(root)`:

- Finds first node where `node.isEditable` or class name is `android.widget.EditText`.

### 8.9 ACTION_SET_TEXT Layer

`enterPin(root, pin)` first tries:

1. `findStandardEditTextNode(root)`.
2. If found, calls `trySetEditText(editTextNode, pin)`.
3. `trySetEditText`:
   - Performs `ACTION_FOCUS`.
   - Creates `Bundle`.
   - Puts `AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE = pin`.
   - Performs `ACTION_SET_TEXT`.
4. If filled:
   - Looks for confirm node.
   - If no confirm button exposed, calls `markCompleted("standard EditText accepted PIN and no confirm button was exposed")`.
   - If confirm exists, creates tap stroke and dispatches sequential one-tap gesture for confirm.

Failure behavior:

- Catches exceptions and logs `EditText ACTION_SET_TEXT failed`.
- Falls through to dynamic keypad gesture path.

### 8.10 Gesture Keypad Layer

Digit discovery:

- `findDigitTargets(root)` traverses full tree.
- For every node, `matchedDigit(node)` checks text and content description.
- Match cases:
  - Label trimmed length is 1 and char is digit.
  - Or label contains exactly one numeric character and also contains keypad hints:
    - `digit`
    - `number`
    - `key`
    - `button`
- For each digit, picks the best-scoring target.

Target node:

- `tappableSelfOrParent(node)` climbs parent chain to find clickable node with non-empty bounds.
- If none, returns last node with usable bounds.

Digit score:

- +8 if tap node clickable.
- +4 if enabled.
- +4 if tap node class contains `Button`.
- +1 if label node class contains `TextView`.
- +2 if bounds non-empty.
- +1 if width and height both between 40 and 500.

Plan:

- `buildDigitGesturePlan(root, pin)`:
  - Ensures all digits required by pin exist.
  - For each pin character, gets target and center coordinates.
  - Adds a `TapStroke` with `startOffsetMs = index * DIGIT_SPACING_MS`.
  - Finds confirm node or fallback confirm node.
  - If confirm exists, appends confirm tap at:
    - `(pin.length - 1) * DIGIT_SPACING_MS + TAP_DURATION_MS + CONFIRM_DELAY_AFTER_DIGITS_MS`
  - If no confirm, assumes AppBlock auto-submits.

Sequential dispatch:

- `dispatchTapSequence(strokes, reason)` logs count and calls `dispatchNextTap`.
- `dispatchNextTap`:
  - If no stroke remains, calls `markCompleted`.
  - Dispatches exactly one tap gesture at a time.
  - If tap fails:
    - `hasTyped = false`
    - `isTyping = false`
    - Logs failure.
    - Leaves PIN armed for retry.
  - If final tap completes:
    - Calls `markCompleted`.
  - Else waits:
    - `CONFIRM_DELAY_AFTER_DIGITS_MS` if previous label contains confirm.
    - Else `DIGIT_SPACING_MS`.
    - Dispatches next tap.

Single tap:

- Builds `GestureDescription`.
- Path moves to x,y and line-to same x,y.
- Stroke starts at `stroke.startOffsetMs`, duration `TAP_DURATION_MS`.
- Calls `dispatchGesture`.
- `GestureResultCallback.onCompleted` calls result true.
- `onCancelled` calls result false.
- If Android rejects dispatch immediately, logs and calls result false.

Completion:

- `markCompleted(reason)`:
  - `hasTyped = true`.
  - `isTyping = false`.
  - Clears pending pin.
  - Logs complete.

### 8.11 Confirm Button Detection

Primary confirm:

- Traverses nodes.
- Gets text/contentDescription labels.
- First label matching confirm logic is scored.
- Best score must be >= 4.

Confirm score:

- +8 if tap node clickable.
- +4 if tap node class contains `Button`.
- +4 if label node class contains `Button`.
- +3 if exact confirm label.
- +2 if label contains `confirm`, `submit`, or `verify`.
- +1 if bounds non-empty.

Fallback confirm:

- Used if no primary confirm and digit target count >= 8.
- Calculates keypad bounds from digit target rectangles.
- Excludes:
  - Nodes with same bounds as digit nodes.
  - Nodes matching digits.
  - Nodes not clickable and not button class.
  - Nodes labeled delete/backspace/clear/remove/erase.
- Scores lower-right keypad candidates:
  - +8 if centerX >= keypad center X.
  - +8 if centerY >= keypad center Y.
  - +5 if near bottom.
  - +4 if clickable.
  - +3 if enabled.
  - +2 if inside/right of keypad bounds.
- Requires score >= 14.
- Logs `Using lower-right keypad fallback as confirm button.`

### 8.12 Re-entry Blocking Lifecycle

Trigger:

- Active unlock countdown reaches zero, or user presses `RE-ARM BLOCKING RULE`.

`enterReEntryState()`:

- Writes:
  - `emergency_state = RE_ENTRY`
  - removes `emergency_active_unlock_expires_epoch`
- Sets active unlock remaining to 0.
- Sets emergency state `RE_ENTRY`.
- Sets `showReEntryShield = true`.
- Starts grace timer.

Re-entry screen:

- `GuidedReEntryLockoutOverlayContent`.
- Displays:
  - `RE-ENTRY PROTOCOL`
  - `Re-arm Blocking`
  - `Do this before the grace timer ends.`
  - Large `GRACE REMAINING` timer.
- Grace timer formatting: `%02d:%02d`.

Open blocking app:

- Clears clipboard.
- Calls `armBlockingAppReEntryAutofill()`.
- Sets local `blockingAppOpened = true`.
- Toast:
  - If armed: `Secure auto-fill armed. Make your rule ACTIVE.`
  - Else: `Make your rule ACTIVE, then return before grace ends.`
- Launches AppBlock.

Confirm rearmed:

- Button enabled only after blocking app opened.
- Calls `confirmBlockingRuleRearmed()`.
- Toast: `Blocking rule re-armed.`

Report not rearmed:

- Calls `failBlockingRuleRearm()`.
- Toast: `Breach logged locally.`

`confirmBlockingRuleRearmed()`:

- Cancels grace timer.
- Calls `lockEmergencyState()`.
- Clears clipboard.
- Logs completed re-entry.

`failBlockingRuleRearm()`:

- Cancels grace timer.
- Calls `lockEmergencyState()`.
- Clears clipboard.
- Deducts 100 points:
  - Action: `Security Lockout Breach: Blocking rule was not re-armed`
  - Status: `breach`
- Logs breach with client name.

Grace timer:

- Default 10 minutes.
- Stored as `emergency_grace_expires_epoch`.
- Decrements once per second.
- On expiry calls `failBlockingRuleRearm()`.

## 9. Behavior Rule Engine

Runner:

- `AnalogAnchorApplication.onCreate` launches `DailyRuleEngineRunner.runIfDue`.

Run guard:

- Pref key: `last_rule_engine_run`.
- If less than 24 hours since last run, return.
- Writes last run timestamp before checking usage access.
- If Usage Access is not granted, return.

Usage access:

- Uses `AppOpsManager.checkOpNoThrow(OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)`.
- Requires `MODE_ALLOWED`.

Usage collector:

- Aggregated usage:
  - `UsageStatsManager.queryAndAggregateUsageStats(start, end)`.
- Open events:
  - `UsageStatsManager.queryEvents(start, end)`.
  - Filters `UsageEvents.Event.ACTIVITY_RESUMED`.

Analysis:

- For each tracked package, builds `AppBehaviorSnapshot`:
  - `openCountLast24h`.
  - `totalMinutesLast7d`.
  - `lateNightOpens`.
  - `compulsiveCheckCount`.
- Compulsive check:
  - Adjacent open events within 1 to 29,999 ms.
- Late night:
  - Hour >= 22 or hour < 2.

Rules:

- `FLAG_COMPULSIVE_CHECKER`
  - If opened >= 8 times last 24h and rapid repeat checks >= 3.
  - Severity `WARN`.
- `FLAG_LATE_NIGHT_PATTERN`
  - If late-night opens >= 3 in last 3 days.
  - Severity `WARN`.
- `FLAG_HEAVY_USAGE`
  - If daily average over 7 days > 90 minutes.
  - Severity `INFO`.
- `FLAG_STREAK_REWARD`
  - If current streak >= 7 and no recent streak reward in last week.
  - Severity `INFO`.

Friendly package names:

- Instagram.
- TikTok.
- YouTube.
- Facebook.
- X.
- WhatsApp.
- Else last package segment title-cased.

## 10. UI And Design Features To Preserve

### 10.1 Theme System

Color source:

- `Color.kt` uses dynamic getter properties based on global `isDarkThemeGlobal`.

Dark palette:

- `ObsidianBg`: `#0B1012`.
- `ObsidianSurface`: `#11191D`.
- `DeepPanel`: `#172126`.
- `ObsidianBorder`: `#2B3A40`.
- `SteelShadow`: `#4E5C62`.
- `MutedSilver`: `#A2AFB6`.
- `WarmOffWhite`: `#F3F8FA`.
- `WarmSilverText`: `#D1DCE1`.
- `BrushedSteel`: `#C4D0D5`.
- `RefinedGreen`: `#76E8F7`.
- `GlowGreen`: `#7DEEFF`.
- `CyanAura`: `#1FE6FF`.
- `SignalAmber`: `#F3B65C`.

Light palette:

- Uses white/off-white background and deep cyan primary.

Theme:

- `MyApplicationTheme` sets `isDarkThemeGlobal`.
- Uses Material3 dark/light color schemes.
- Wraps content in a full-screen `Box` with `PremiumDarkRadialBrush`.

Typography:

- Font family: Outfit.
- Weights:
  - regular
  - medium
  - semibold
  - bold
  - black
- Letter spacing in core typography is 0.

### 10.2 Component Animations

`PremiumButton`:

- Uses `animateFloatAsState`.
- Pressed scale: `0.97f`.
- No stock ripple.
- Gradient background when enabled.
- Disabled uses flat disabled color.
- Letter spacing: 1 sp inside button text.

`PremiumTextField`:

- Uses `animateColorAsState` for border focus color.
- Uses `animateDpAsState` for focus glow elevation.
- Cursor is `GlowGreen`.

`TactileToken`:

- Circular token with radial gradient and glass gloss.
- Uses shadow glow.

`GlassCard`:

- Surface with translucent background alpha 0.85.
- Gradient border.
- Tonal elevation 6 dp.

`EmbeddedInteractiveMapCanvas`:

- Pulsing user-dot animation.
- Pinch zoom.
- Pan gestures.
- Map center callback.
- Offline mock map roads, water, parks, pins, route lines, workout loop, reticle, metadata pill, zoom controls.

### 10.3 Toast And Feedback Inventory

Important toasts by feature:

- Founder:
  - Could not open Device Admin settings.
  - Could not open settings.
  - Could not open accessibility settings.
  - Launcher hide instruction fallback.
  - AppBlock marked as hidden.
  - Uninstall PIN saved.
  - Use exactly 4 digits.
- Onboarding:
  - Home coordinates locked at center.
  - Using default Busaiteen coordinates.
  - Anchor Lock shortcut requested.
  - The strict blocking layer is not installed yet.
  - Pinned shortcuts are not supported on this launcher. Continue setup.
- Dashboard settings:
  - System configuration unlocked & reset.
  - Set a PIN first.
  - PIN lock enabled.
  - Use exactly 4 digits.
  - PIN lock cleared.
- GPS:
  - Could not open Location settings.
  - GPS is still off. Please enable it in Settings.
  - Verification complete! +40 Reserve Credits.
- Fajr:
  - Could not preview this ringtone.
  - Fajr enforcement is protected by the accountability PIN.
  - Fajr Alarm activated! Playing preview.
  - Fajr Alarm deactivated.
  - Fajr ringtone imported.
  - Built-in Fajr tone restored.
  - Fajr alarm updated / Enter accountability PIN first.
  - Ayah wake challenge updated / Enter accountability PIN first.
  - Accountability PIN enabled / Use exactly 4 digits.
  - Accountability PIN disabled / Incorrect accountability PIN.
  - Wake proof complete. +25 Reserve Points.
  - Fajr alarm dismissed.
- Anchor:
  - Whiteboard photo was not captured.
  - Camera permission is required for the whiteboard photo.
  - Test session registered: 30 seconds. Phone muted.
  - Session registered! Phone muted.
  - Copied incomplete sentence.
- Mosque/Runaway:
  - Designated mosque sanctuary pinned successfully.
  - Route calculated in-app.
  - Navigation stopped.
  - Retreat broken. Phone locked.
  - Retreat Complete! +40 pts.
  - Sanctuary verified! +50 credits. Access restored.
  - Proximity check failed with distance.
- Emergency access:
  - Clipboard empty or inaccessible.
  - Access authorized. Secure auto-fill armed.
  - Secure auto-fill is not ready. Manual rescue is available as last resort.
  - Invalid or expired Analog Anchor token.
  - PIN copied for 30 seconds. Paste once, then return.
  - Manual rescue failed. Token expired or invalid.
  - Secure auto-fill armed. Make your rule ACTIVE.
  - Make your rule ACTIVE, then return before grace ends.
  - Blocking rule re-armed.
  - Breach logged locally.
- Protocol legacy:
  - Protocol aborted. Penalty applied.
  - No Eraser registered.
  - Eraser NFC Validated.
  - Timer accelerated.
  - Completed! +25 Reserve Points.
- Proof:
  - Camera permission is required for capture.
  - Could not import image.
  - No compatible app found for sharing.
- Fajr receiver:
  - Wake up. 5 mins until Fajr adhan.

### 10.4 Hidden Or Non-Obvious Behaviors

- Dashboard settings title triple tap:
  - In settings bottom sheet, tapping the `SETTINGS` title three times resets founder panel.
  - Calls `viewModel.resetFounderPanel()`.
  - Calls `onNavigateToFounderReset()`.
- Reserve/proof hidden emergency portal:
  - Triple tap `Need emergency access?`.
  - Reveals `ACCESS EMERGENCY PORTAL`.
- Anchor Lock shortcut:
  - Separate home-screen entry into same emergency portal.
- AppBlock hide confirmation:
  - Visible setup gate in FounderScreen.
  - One hidden transparent instruction line is present in the AppBlock hide card, using color transparent and 0 sp text.
- Token clipboard auto-detection:
  - Detects valid token already in clipboard and shows paste chip.
- Token clipboard wipe:
  - Auto clears detected token after 60 seconds.
- Manual rescue:
  - Only appears after secure autofill fails but token decodes.
- Emergency token:
  - Token validity starts when support code is requested, not when cooldown starts.
- Re-entry remembered PIN:
  - Encrypted custody lasts 75 minutes, independent from 90-second pending PIN TTL.
- App PIN:
  - Emergency portal remains reachable through shortcut/overlay path before normal dashboard renders.
- Fajr wake proof:
  - Dismissing alarm awards +25 once per day.
- Fajr silence:
  - Silence stops sound but does not dismiss alarm state.
- Fajr accountability PIN:
  - Separate from app PIN.
- Fajr ringtone:
  - Imported document URI is persisted with read permission if provider supports it.
- Behavior engine:
  - Writes last-run timestamp before usage access check; a no-permission run still consumes the daily run slot.
- Room date caveat:
  - Some flows use init-time current date rather than automatically switching at midnight because the date is captured at ViewModel init.
- Anchor test mode:
  - Active in current code: Tap 1 runs 30 seconds.
- Emergency cooldown test mode:
  - Active in current code: cooldown is 2 minutes, not 60.
- Tap 2 expiry:
  - Calls `onStepChange("INITIAL")` even though canonical current stages do not include `INITIAL`.
- Runaway tab:
  - Available only when sanctuary type changes to `runaway`.
- SurvivalScreen:
  - Compiled but not actively routed in current dashboard callbacks.
- ProtocolScreen:
  - Compiled but not actively routed in current dashboard callbacks.
- Device Admin:
  - Setup gate only; force-lock policy exists, but active code does not call force-lock.
- Accelerometer:
  - Registered during focus sessions, but current void logic only uses gyroscope.
- Audio:
  - Focus session tries to force ringer mode silent and restore previous mode.
- Location:
  - Uses last known location first, then one single update.
- Reverse location naming:
  - Recognizes Busaiteen, Mecca, London within 8 km, else uses formatted GPS string.

## 11. iOS Technical Handoff

This section is addressed to the future iOS/SwiftUI development agent.

The Android app currently depends on OS powers that do not have direct iOS equivalents:

- Android AccessibilityService can inspect another app's accessibility tree and dispatch gestures.
- Android can use `ACTION_SET_TEXT` on accessible fields in another app.
- Android can launch a third-party blocking app package directly.
- Android can use exact alarms and a BroadcastReceiver to wake the app receiver.
- Android can use Device Admin as uninstall friction.

iOS must not be implemented as a one-to-one injection clone. The closest faithful iOS migration is to replace the third-party AppBlock rearm model with native Screen Time shielding and rearm by setting/clearing Managed Settings.

### 11.1 DeviceActivity / FamilyControls / ManagedSettings

Apple's Screen Time API suite consists of:

- Family Controls for authorization and privacy-preserving app/category selection.
- Device Activity for schedules and usage threshold monitoring.
- Managed Settings for applying shields and restrictions.

Relevant Apple mechanics:

- `AuthorizationCenter.shared.requestAuthorization(for:)` requests authorization.
- Family Controls capability and entitlement are required.
- `FamilyActivityPicker` lets the user select apps/categories/web domains without revealing raw identities to the app; selections are opaque tokens.
- `DeviceActivityCenter.startMonitoring(_:during:events:)` starts a schedule and optional threshold events.
- `DeviceActivityMonitor` extension receives interval and threshold callbacks.
- `ManagedSettingsStore().shield.applications` can shield selected app tokens.
- Shielding dims app icons and covers launches with a system shield UI that the app can configure through Screen Time extensions.

Android behavior to translate:

- AppBlock installed/hidden/setup:
  - Replace with Family Controls authorization and app selection.
- AppBlock PIN unlock:
  - Replace with clearing shields for a temporary active unlock window.
- Re-arm blocking:
  - Replace with reapplying shields through `ManagedSettingsStore`.
- Behavior usage flags:
  - Use DeviceActivity reports/thresholds where possible, but respect Apple's privacy model. Raw package names and exact events are not available the same way as Android UsageStats.

Recommended iOS access-control model:

1. On setup, ask for Family Controls authorization.
2. Present a `FamilyActivityPicker`.
3. Store the opaque `FamilyActivitySelection`.
4. Apply shields for selected apps/categories/web domains through `ManagedSettingsStore`.
5. Emergency token flow:
   - Keep the `AA-...-EXP60` token contract if support-tool continuity is important.
   - Decode token inside the app.
   - If token is valid and fresh, temporarily clear shields for 60 minutes.
6. Re-entry:
   - When the active window expires, show the same urgent re-entry UI.
   - Reapply shields directly from the app/extension instead of asking the user to open a third-party app.
   - Require the user to confirm rearm after the app verifies shield state.
7. Breach:
   - If shields are not reapplied by grace expiry, deduct points and log breach.

### 11.2 Custom Keyboard Evaluation

A Custom Keyboard Extension is not a faithful replacement for Android AccessibilityService.

Why:

- It only provides text input where the user has selected that keyboard and where third-party keyboards are supported.
- It cannot inspect another app's arbitrary UI tree.
- It cannot press a confirm button in another app.
- It cannot run as a hidden rearm automator.
- Apple documentation says secure text input replaces custom keyboards with the system keyboard.
- Apps can reject third-party keyboards entirely.
- Keyboard extensions interact through `textDocumentProxy`, not through direct access to the target field.

Use a Custom Keyboard only if:

- Analog Anchor wants a visible, user-selected keyboard for entering an Analog token in normal text fields.
- The behavior is explicitly user-controlled and not framed as automation.

Do not use a Custom Keyboard for:

- AppBlock PIN injection.
- Re-entry autofill.
- Secure text fields.
- Confirm-button automation.
- Hidden background control.

### 11.3 iOS Alarm And Fajr Wake Proof

Android behavior:

- Exact alarm scheduled at Fajr minus 5 minutes.
- Receiver sets ringing pref.
- Notification appears.
- Looping alarm sound for 60 seconds.
- In-app overlay appears after app opens/resumes.
- Dismiss awards +25 once/day.
- Silence stops sound without awarding dismissal.

iOS translation:

- Use `UNUserNotificationCenter` for local notifications.
- Schedule daily/next Fajr notifications from calculated prayer times.
- Exactness is not guaranteed the same way Android `setExactAndAllowWhileIdle` works.
- Continuous alarm audio in background is constrained.
- Critical Alerts require special Apple entitlement and approval; do not assume it is available.
- The app can show the full Fajr overlay when opened from the notification.
- Notification actions can be used for `Silence` or `Wake Proof`, but awarding points should be carefully guarded in local persistence.

Recommended iOS behavior:

- `FajrScheduleService` computes next warning and next alarm.
- Schedule:
  - Fajr minus 5 hours warning.
  - Fajr minus 5 minutes alarm.
- On notification tap, show `FajrWakeOverlayView`.
- If ayah challenge enabled, require exact text in app before awarding.
- Award +25 once/day through a `fajrWakeVerified_yyyy-MM-dd` local key.

### 11.4 iOS Sensors And Anchor Session

Android behavior:

- Phone must stay still.
- Gyroscope threshold `0.35f` voids session.
- Ringer mode is set to silent and restored.
- Step counter tracks runaway/walk goals.

iOS translation:

- Use CoreMotion:
  - `CMMotionManager` for gyroscope/device motion.
  - Mirror threshold behavior with calibration on real devices.
- Use HealthKit or CoreMotion pedometer:
  - `CMPedometer` for live step counts where available.
  - HealthKit if longitudinal history or more trusted steps are required.
- Ringer mode cannot be programmatically forced the way Android attempts it. Replace with visible pre-flight instruction and optional Focus mode integration if supported by future app capabilities.

### 11.5 iOS Location And Sanctuary

Android behavior:

- Reads last known location and one single update.
- Geofence success threshold 100m.
- Reverse labels hardcoded for Busaiteen, Mecca, London.
- In-app map is custom Canvas, not Google/OSM.

iOS translation:

- Use CoreLocation for current location.
- Use `CLLocation.distance(from:)`.
- Preserve 100m threshold.
- Store sanctuary and home baseline in local persistence.
- Use SwiftUI Canvas/MapKit:
  - If preserving exact visual language, implement custom SwiftUI Canvas map.
  - If production location clarity matters more, use MapKit with custom overlays.

### 11.6 iOS Persistence

Android persistence:

- SharedPreferences for scalar state.
- EncryptedSharedPreferences for PIN custody.
- Room for proof/session/recovery ledger.
- Cache directory for proof and review card images.

iOS equivalent:

- `UserDefaults` for non-sensitive scalar state.
- Keychain for sensitive PIN/token custody.
- SwiftData or CoreData for ledger tables.
- FileManager in Application Support or Caches for proof images.
- App Group container if Screen Time extensions need shared access to selected tokens or shield state.

Sensitive iOS storage:

- Store the support PIN only in Keychain with an expiry timestamp.
- Prefer not to store a raw AppBlock-equivalent PIN at all if using native Screen Time shields.
- Store re-entry custody only if keeping the token flow; clear aggressively.

### 11.7 Proposed SwiftUI Workspace Structure

Recommended Xcode project:

```text
AnalogAnchorIOS/
  AnalogAnchorApp.swift
  App/
    AppRouter.swift
    AppTheme.swift
    AppTypography.swift
    AppEnvironment.swift
  Models/
    SessionLog.swift
    ProofMission.swift
    UnlockSession.swift
    RuleFlag.swift
    RecoveryRecord.swift
    PrayerTimes.swift
    AnchorState.swift
  Persistence/
    AnchorStore.swift
    SecureTokenStore.swift
    LedgerStore.swift
    ProofFileStore.swift
    AppGroupStore.swift
  Services/
    PrayerTimesCalculator.swift
    FajrScheduleService.swift
    FajrNotificationDelegate.swift
    TokenService.swift
    LocationService.swift
    MotionSessionService.swift
    StepTrackingService.swift
    ReviewCardRenderer.swift
  ScreenTime/
    ScreenTimeAuthorizationService.swift
    ActivitySelectionStore.swift
    ShieldControlService.swift
    DeviceActivityScheduleService.swift
  Features/
    FounderSetup/
      FounderSetupView.swift
      FounderSetupViewModel.swift
    Onboarding/
      OnboardingView.swift
      SanctuarySetupView.swift
      HomeBaselineView.swift
      AnchorLockSetupView.swift
    Dashboard/
      DashboardView.swift
      BottomTabBar.swift
      SettingsSheet.swift
      AppPinGateView.swift
    Sleep/
      ReserveSleepView.swift
      FajrSettingsView.swift
      FajrWakeOverlayView.swift
      CircadianClockGauge.swift
    AnchorSession/
      AnchorTabView.swift
      PreFlightView.swift
      ActiveAnchorSessionView.swift
      TapTwoValidationView.swift
      ClosingProtocolView.swift
      PreviousWhiteboardView.swift
    Sanctuary/
      MosqueSanctuaryView.swift
      RunawayWorkoutView.swift
      GPSVerificationSheet.swift
      EmbeddedMapCanvas.swift
    EmergencyAccess/
      AnchorLockPortalView.swift
      ReEntryProtocolView.swift
      TokenEntryView.swift
      ManualRescueNoticeView.swift
    Accountability/
      AccountabilityDashboardView.swift
      ReturnProofView.swift
      RecoveryView.swift
      BehaviorAlertsView.swift
    Components/
      GlassCard.swift
      PremiumButton.swift
      PremiumTextField.swift
      TactileToken.swift
      StatusNotice.swift
  Extensions/
    AnalogAnchorDeviceActivityMonitor/
      DeviceActivityMonitorExtension.swift
      Info.plist
    AnalogAnchorShieldConfiguration/
      ShieldConfigurationExtension.swift
      Info.plist
    AnalogAnchorShieldAction/
      ShieldActionExtension.swift
      Info.plist
```

### 11.8 iOS Feature Mapping Table

Android feature:

- FounderScreen setup gates.

iOS equivalent:

- Founder setup should gate:
  - Family Controls authorization.
  - Screen Time selection configured.
  - Notification permission.
  - Location permission.
  - Motion/fitness permission if needed.
  - App PIN set.
  - Support contact saved.

Android feature:

- AppBlock installed and hidden.

iOS equivalent:

- Remove. Replace with Screen Time native setup and shield selection.

Android feature:

- Accessibility Auto-Typer.

iOS equivalent:

- Remove. Replace with Managed Settings shield toggling.

Android feature:

- ACTION_SET_TEXT.

iOS equivalent:

- No app-to-app equivalent. Do not attempt.

Android feature:

- Clipboard rescue.

iOS equivalent:

- Prefer remove. If token clipboard UX remains, clear internal token state; do not place sensitive PIN on pasteboard.

Android feature:

- Re-entry opens blocking app.

iOS equivalent:

- Re-entry reapplies native shields and verifies `ShieldControlService` state.

Android feature:

- Fajr exact alarm receiver.

iOS equivalent:

- Local notifications, possible notification actions, in-app overlay on launch.

Android feature:

- Room ledger.

iOS equivalent:

- SwiftData/CoreData ledger with matching tables and statuses.

Android feature:

- FileProvider proof sharing.

iOS equivalent:

- FileManager image storage plus `ShareLink` or `UIActivityViewController`.

Android feature:

- UsageStats behavior flags.

iOS equivalent:

- DeviceActivity thresholds/reports. Expect less raw detail due privacy tokens.

Android feature:

- Device Admin uninstall friction.

iOS equivalent:

- Family Controls authorization may prevent deleting the parental-control app in some authorized contexts; do not assume general uninstall lock for normal individual app.

Android feature:

- App PIN after 60 seconds background.

iOS equivalent:

- Use local app lock state, scene phase timestamps, and optional biometrics/PIN.

Android feature:

- Anchor physical stillness via gyroscope.

iOS equivalent:

- CoreMotion gyroscope/device motion.

Android feature:

- Step goal.

iOS equivalent:

- `CMPedometer` / HealthKit.

### 11.9 Recommended iOS Migration Order

1. Build persistence first:
   - SwiftData/CoreData schema matching Room entities.
   - UserDefaults keys matching non-sensitive Android state.
   - Keychain for sensitive token/PIN custody.
2. Build theme and core components:
   - `GlassCard`, `PremiumButton`, `PremiumTextField`, `TactileToken`.
3. Build setup flow:
   - Founder setup.
   - Onboarding.
   - Screen Time authorization and picker.
4. Build native shield control:
   - `ShieldControlService`.
   - App Group storage.
   - ManagedSettingsStore shield apply/clear.
5. Build emergency token flow:
   - Preserve `AA-...-EXP60` decoding.
   - Use shield clearing instead of external AppBlock launch.
6. Build re-entry:
   - Urgent grace UI.
   - Reapply shields.
   - Verify local shield state.
   - Deduct breach points if expiry.
7. Build Fajr flow:
   - Prayer calculator.
   - Notifications.
   - Overlay and wake proof.
8. Build Anchor session:
   - Pre-flight.
   - 30-second test mode flag.
   - CoreMotion stillness.
   - Closing camera and previous whiteboard gallery.
9. Build sanctuary:
   - Location.
   - 100m verification.
   - Mosque and runaway views.
10. Build accountability dashboard:
   - Ledger.
   - Pillars.
   - Return proof and recovery.
   - Behavior flags where Screen Time APIs permit.

### 11.10 iOS Non-Negotiable Behavioral Equivalents

Do not lose these:

- The support token is not the raw PIN.
- The user should not paste the support token into the blocking system.
- Test mode currently makes Anchor Tap 1 run 30 seconds.
- Production must restore real 25/45/55 minute sessions.
- Emergency cooldown currently test mode 2 minutes; production target 60 minutes.
- Active unlock window is 60 minutes.
- Re-entry grace timer is 10 minutes.
- Re-entry timer must be visually urgent and large.
- Re-entry failure deducts 100 points and logs breach.
- Fajr alarm dismissal is the wake proof and awards +25 once per day.
- Fajr silence is not dismissal.
- Anchor closing requires real camera capture before session seal.
- Previous whiteboard gallery is a continuity/storytelling surface, not a generic note list.
- Movement during Anchor session voids and deducts points.
- Tap 2 validation window is 60 seconds.
- Geofence is 100 meters in actual code.
- Hidden emergency portal exists through triple tap.
- App PIN relocks after 60 seconds in background.
- Behavior flags are local accountability context, not public analytics.
- AppBlock accessibility injection is Android-only and must become native shield control on iOS.

## 12. Final Android Freeze Notes

Current Android test/development flags:

- `TEST_MODE_COOLDOWN = true`
  - 2-minute emergency cooldown.
- `ANCHOR_TAP_ONE_TEST_MODE = true`
  - 30-second Anchor Tap 1 session.

Current Android platform dependencies that cannot be directly copied to iOS:

- `AccessibilityService`
- `dispatchGesture`
- `ACTION_SET_TEXT` into another app
- `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`
- Android `ShortcutManager.requestPinShortcut`
- Android `AlarmManager.setExactAndAllowWhileIdle`
- Android `BroadcastReceiver`
- Android `DeviceAdminReceiver`
- Android `UsageStatsManager`
- Android `ClipboardManager` behavior
- Android `AudioManager.ringerMode`

Current Android behaviors that should be preserved by intent, not by implementation mechanism:

- Strict blocking.
- Emergency friction.
- Support-held token authorization.
- Temporary unlock.
- Required rearm.
- Breach logging.
- Fajr wake proof.
- Physical Anchor session.
- Previous whiteboard continuity.
- Mosque/sanctuary verification.
- Reserve points and pillar ledger.

## 13. Second-Pass Preservation Addendum

This addendum records a continuation scan against the same live workspace. Its purpose is to capture the smaller migration surfaces that are not always obvious from the product-level flow: UI test tags, Activity Result contracts, Compose-local state, exact feedback surfaces, and Android-specific helper files.

### 13.1 Second-Pass Repository State

At the second pass, the only untracked file was this audit document:

- `docs/AnalogAnchor_Complete_Maturity_Audit.md`

That means the audit did not discover unrelated dirty Android code changes that would alter the behavior being documented.

### 13.2 Full Test Tag Inventory

These `Modifier.testTag(...)` strings are currently live in Compose code and should be preserved or deliberately renamed in iOS UI tests:

- `verify_gps_btn`
  - File: `app/src/main/java/com/example/ui/screens/SurvivalScreen.kt`
  - Purpose: GPS verification action in the survival screen.
- `exit_protocol_setup`
  - File: `app/src/main/java/com/example/ui/screens/ProtocolScreen.kt`
  - Purpose: Legacy protocol exit affordance.
- `simulate_tap1_btn`
  - Files:
    - `app/src/main/java/com/example/ui/screens/ProtocolScreen.kt`
    - `app/src/main/java/com/example/ui/screens/DashboardScreen.kt`
  - Purpose: Simulated first anchor tap / session start.
- `accelerate_timer_btn`
  - File: `app/src/main/java/com/example/ui/screens/ProtocolScreen.kt`
  - Purpose: Legacy test acceleration button that forces the session timer down to 3 seconds through reflection.
- `trigger_gyro_movement_btn`
  - File: `app/src/main/java/com/example/ui/screens/ProtocolScreen.kt`
  - Purpose: Legacy manual movement-failure trigger.
- `simulate_tap2_btn`
  - Files:
    - `app/src/main/java/com/example/ui/screens/ProtocolScreen.kt`
    - `app/src/main/java/com/example/ui/screens/DashboardScreen.kt`
  - Purpose: Simulated second tap / closing protocol entry.
- `tab_$tabId`
  - File: `app/src/main/java/com/example/ui/screens/DashboardScreen.kt`
  - Runtime values are derived from dashboard tab ids:
    - `tab_RESERVE`
    - `tab_ANCHOR`
    - `tab_MOSQUE` or `tab_RUNAWAY`
    - `tab_PROOF`
- `settings_icon`
  - File: `app/src/main/java/com/example/ui/screens/DashboardScreen.kt`
  - Purpose: Settings entry button.
- `continue_to_preflight_btn`
  - File: `app/src/main/java/com/example/ui/screens/DashboardScreen.kt`
  - Purpose: Anchor duration to pre-flight transition.
- `continue_to_tap1_btn`
  - File: `app/src/main/java/com/example/ui/screens/DashboardScreen.kt`
  - Purpose: Pre-flight checklist to Tap 1 transition.
- `take_whiteboard_photo_btn`
  - File: `app/src/main/java/com/example/ui/screens/DashboardScreen.kt`
  - Purpose: Closing protocol camera action for the whiteboard reflection.
- `seal_whiteboard_session_btn`
  - File: `app/src/main/java/com/example/ui/screens/DashboardScreen.kt`
  - Purpose: Final seal action that completes the Anchor session and awards points.
- `scan_nfc_onboarding_btn`
  - File: `app/src/main/java/com/example/ui/screens/OnboardingScreen.kt`
  - Purpose: Simulated NFC registration in onboarding.
- `set_gps_sanctuary_btn`
  - File: `app/src/main/java/com/example/ui/screens/OnboardingScreen.kt`
  - Purpose: Save sanctuary GPS during onboarding.
- `begin_onboarding_btn`
  - File: `app/src/main/java/com/example/ui/screens/OnboardingScreen.kt`
  - Purpose: Final onboarding completion action.

### 13.3 Activity Result Contract Inventory

These are the current Android runtime contracts that launch external capture or picker flows:

- `ActivityResultContracts.TakePicture()`
  - `AccountabilityScreens.kt`
    - Used by `ProofCaptureControls`.
    - Saves proof images for return proof and recovery flows when camera capture is enabled.
  - `DashboardScreen.kt`
    - Used by the Anchor closing protocol whiteboard capture.
    - Requires a `FileProvider` URI from `ProofFileStore.createProofImageUri(...)`.
- `ActivityResultContracts.RequestPermission()`
  - `AccountabilityScreens.kt`
    - Requests camera permission for proof capture.
  - `DashboardScreen.kt`
    - Requests camera permission for whiteboard reflection capture.
- `ActivityResultContracts.GetContent()`
  - `AccountabilityScreens.kt`
    - Imports proof images from gallery or external document providers.
    - Copies source content into Analog Anchor cache storage before saving the proof path.
- `ActivityResultContracts.OpenDocument()`
  - `DashboardScreen.kt`
    - Imports custom Fajr ringtone audio with MIME type `audio/*`.
    - Attempts to take persistable read permission.
    - Stores URI and display label in SharedPreferences.

iOS migration equivalent:

- Whiteboard and proof capture should use a camera wrapper such as `UIImagePickerController`, `PHPickerViewController`, or modern SwiftUI `PhotosPicker` plus an AVFoundation camera path if full camera control is required.
- Ringtone import does not translate directly to arbitrary alarm-sound playback on iOS. Imported audio can be stored in the app, but local notifications cannot freely use arbitrary external audio unless bundled or copied into supported locations and within iOS notification-sound constraints.
- Every imported or captured image should be copied into the app container immediately, matching the Android behavior where the app does not trust external URI lifetime.

### 13.4 Compose-Local State And Effect Inventory

These local state variables are active UI behavior. They are not stored in Room or SharedPreferences, but they affect navigation, temporary UI state, and test flows.

Main app shell:

- `MainActivity.kt`
  - `snackbarHostState`
    - Provides snackbar feedback to `FounderScreen`.

Dashboard shell:

- `isEnglish`
  - Toggles language label state in the dashboard header.
- `selectedTab`
  - Active dashboard tab.
- `proofFlowScreen`
  - Switches the proof/reserve flow between dashboard, return proof, and recovery.
- `showSettingsDialog`
  - Controls settings bottom sheet visibility.
- `settingsTab`
  - Active settings sheet tab: `ACCESS`, `STYLE`, or `FAJR`.
- `showGpsVerificationDialog`
  - Controls sanctuary GPS verification modal.
- `gpsVerificationType`
  - GPS verification type: `FAJR` or `MAGHRIB`.
- `systemTime`
  - Updated once per second with a `LaunchedEffect(Unit)`.
- `anchorStep`
  - Current Anchor tab stage: duration, pre-flight, tap 1, session, tap 2, close, or verified.
- `chosenDuration`
  - Selected Anchor duration, initially 25.
- `checklistPhoneFaceDown`
  - Pre-flight checklist gate.
- `checklistWhiteboardOpen`
  - Pre-flight checklist gate.
- `checklistTimerSet`
  - Pre-flight checklist gate.
- `isRetreatActive`
  - Maghrib-to-Isha retreat timer active flag.
- `retreatSecondsRemaining`
  - Retreat countdown state.
- `setupStep`
  - Sanctuary setup step state used in dashboard setup flow.

Dashboard settings sheet:

- `settingsTapCount`
  - Triple-tap counter on settings title.
  - Unlocks founder reset behavior.
- `appPinInput`
  - Temporary app PIN input.
- `fajrAccountabilityPinInput`
  - Temporary Fajr accountability PIN setup value.
- `fajrAccountabilityVerifyInput`
  - Temporary Fajr accountability PIN unlock value.

GPS verification modal:

- `loadingStage`
  - Values: `LOCATING`, `RESOLVED`, `GPS_OFF`.
- `mockLat`
  - Animated verification latitude display.
- `mockLng`
  - Animated verification longitude display.
- `satLockProgress`
  - Fake satellite progress percentage.
- `currentStatus`
  - Rotating satellite acquisition text.
- `isGpsActive`
  - Result of checking `LocationManager`.

App PIN gate:

- `pinInput`
  - Local entered app PIN.
- `showError`
  - Error display state after invalid PIN.

Fajr alarm overlay:

- `ayahInput`
  - User answer for the Fajr wake ayah challenge.
- `soundSilenced`
  - Tracks whether the user pressed silence without earning wake proof.

Fajr settings:

- `ringtonePickerLauncher`
  - Activity result launcher for imported audio.

Anchor tab canonical flow:

- `selectedSessionForDetail`
  - Opens previous whiteboard detail dialog.
- `closingNote`
  - Incomplete sentence / continuity note at session close.
- `capturedWhiteboardPath`
  - Local file path required before `SEAL WHITEBOARD SESSION` is enabled.
- `pendingWhiteboardPath`
  - Temporary target path before camera result completes.
- `pendingWhiteboardUri`
  - Temporary `FileProvider` URI passed to the camera.
- `tap2Timer`
  - 60-second second-tap countdown.

Mosque tab:

- `activeMosqueSelection`
  - Selected mosque from the in-app list/map.
- `activeNavigationMosqueName`
  - Current simulated route target.
- `nearbyMosques`
  - Remembered list of Busaiteen mosque choices.
- `mosquePins`
  - Remembered map pin projection data.
- `radarScale`
  - Pulse animation scale.

Reserve/sleep tab:

- `sleepDialFocus`
  - Cycles circadian gauge center information.

Anchor Lock decoy:

- `clipboardToken`
  - Detected support token copied to clipboard.
- `tokenInput`
  - Manual token text field state.
- `showManualRescue`
  - Shows the fallback raw-PIN clipboard rescue card.
- `manualRescueArmed`
  - Tracks whether manual rescue was just armed.
- `accessTokenPattern`
  - Regex: `^AA-[QWERTASDFG]{4,8}-EXP60$`.

Re-entry shield:

- `blockingAppOpened`
  - Unlocks the `I RE-ARMED MY BLOCK` confirmation after the user presses `OPEN BLOCKING APP`.

Onboarding:

- `currentStep`
  - 1 through 5 onboarding wizard step.
- `isNfcScanning`
  - Simulated NFC loading state.
- `locationSaved`
  - Sanctuary location completion gate.
- `sanctuaryMapCenterLat`
  - In-app map center for sanctuary selection.
- `sanctuaryMapCenterLng`
  - In-app map center for sanctuary selection.
- `homeMapCenterLat`
  - In-app map center for home baseline selection.
- `homeMapCenterLng`
  - In-app map center for home baseline selection.
- `selectedSanctuary`
  - `mosque` or `runaway`.
- `selectedHour`
  - Evening protocol hour.
- `selectedMinute`
  - Evening protocol minute.
- `shortcutStepDone`
  - Anchor Lock shortcut setup completion gate.
- `isMosqueGuy`
  - Local UI toggle mirroring sanctuary selection.
- `busaiteenMosques`
  - Local mosque list.
- `busaiteenPins`
  - Local map pin list.
- `selectedMosqueIndex`
  - Active mosque choice in onboarding.

Founder setup:

- `inputPasscode`
  - Defaults to `5222`.
- `isGatewayAuthenticated`
  - Founder panel access gate.
- `loginError`
  - Invalid passcode state.
- `usageAccessGranted`
  - Polled every second.
- `accessibilityAccessGranted`
  - Polled every second.
- `uninstallPin`
  - Temporary uninstall PIN input.
- `appBlockHiddenConfirmed`
  - Mirrors `appblock_hidden_confirmed`.

Accountability screens:

- `usageAccessGranted`
  - Polled by dashboard to show usage-access prompts.
- `hiddenAccessClicks`
  - Triple-tap counter for hidden emergency access button.
- `showHiddenButton`
  - Hidden emergency portal reveal state.
- `expanded`
  - Behavior flag card expansion.
- `proofPath`
  - Return proof and recovery proof local path.
- `note`
  - Return proof and recovery note text.
- `pendingCameraPath`
  - Proof capture temporary file path.
- `pendingCameraUri`
  - Proof capture temporary URI.

Legacy `ProtocolScreen`:

- `stageState`
  - Values: `TAP_1`, `ACTIVE_COUNTDOWN`, `CLOSURE_SCREEN`, `CAMERA_VIEWPORT`, `PHOTO_PREVIEW`, `COMPLETED`.
- `selectedDuration`
  - Legacy duration selector.
- `userNote`
  - Legacy reflection note.
- `selectedPrivacyOption`
  - Values in comments: `A` save privately, `C` session archive.
- `isShutterFlashing`
  - Simulated camera flash.

Embedded map:

- `mapCenterLat`
  - Current map center latitude.
- `mapCenterLng`
  - Current map center longitude.
- `zoomLevel`
  - Map zoom between constrained values.

### 13.5 Feedback Surface Inventory From Second Pass

These user-visible feedback surfaces were confirmed in the continuation scan. Preserve their intent in iOS, even if exact wording changes:

- Fajr receiver:
  - Wake-up toast when the receiver triggers the alarm.
- ViewModel Fajr settings:
  - Ringtone preview failure.
  - Accountability PIN protection warning.
  - Alarm activation preview confirmation.
  - Alarm deactivation confirmation.
- Survival screen:
  - Sanctuary verified, access restored.
  - Distance failure feedback.
- Legacy protocol:
  - Session voided message.
  - Protocol aborted and penalty applied.
  - No eraser registered warning.
  - Eraser NFC validated.
  - Timer accelerated.
  - Completion plus points.
- Onboarding:
  - Home coordinates locked at center.
  - Default Busaiteen coordinates used.
  - Anchor Lock shortcut requested.
  - Strict blocking layer missing.
  - Pinned shortcuts unsupported.
- Founder setup:
  - Device Admin settings could not open.
  - Settings could not open.
  - Accessibility settings could not open.
  - AppBlock hidden confirmation.
  - Uninstall PIN saved.
  - Invalid uninstall PIN length.
  - Snackbar: `Anchor sealed. Begin client portal.`
- Accountability:
  - Camera permission required for capture.
  - Could not import image.
  - No compatible app found for sharing.
- Dashboard settings:
  - System configuration unlocked and reset.
  - Set a PIN first.
  - PIN lock enabled.
  - Use exactly 4 digits.
  - PIN lock cleared.
- GPS verification:
  - Could not open Location settings.
  - GPS still off.
  - Verification complete with +40 reserve credits.
- Fajr UI:
  - Fajr ringtone imported.
  - Built-in Fajr tone restored.
  - Other PIN/accountability messages controlled by the settings UI.
- Anchor tab:
  - Whiteboard photo not captured.
  - Camera permission required for whiteboard photo.
  - Test session registered in 30-second mode.
  - Copied incomplete sentence.
- Mosque tab:
  - Navigation stopped.
  - Designated mosque pinned successfully.
  - Route calculated in-app.
  - Retreat broken and phone locked.
  - Retreat complete with +40 points.
- Emergency access:
  - Clipboard empty or inaccessible.
  - Access authorized and secure auto-fill armed.
  - Token valid but accessibility auto-fill unavailable.
  - Invalid or expired token.
  - Manual rescue active.
  - Manual rescue failed.
  - Blocking app opened with auto-fill armed or unavailable.
  - Blocking rule re-armed.
  - Breach logged locally.

### 13.6 Dynamic Preference Key Families

The static key list in section 5 should be read with these dynamic families:

- `fajr_verified_$todayStr`
  - One boolean per local date.
  - Set by Fajr sanctuary verification.
- `fajr_wake_verified_$todayStr`
  - One boolean per local date.
  - Set by dismissing the Fajr alarm.
  - This is the current "Wake Protocol" pillar completion source.
- `maghrib_verified_$todayStr`
  - One boolean per local date.
  - Set by Maghrib sanctuary verification.
- `last_anchor_proof_date`
  - Tracks daily anchor streak updates.
- `last_breach_date`
  - Tracks breach day for streak reset and context.
- `last_fajr_claim_date`
  - Prevents duplicate legacy Fajr claim awarding.

The iOS persistence layer should treat these as date-scoped facts, not as an endlessly growing arbitrary key-value surface. A better iOS migration can model them as dated records while still preserving Android import compatibility.

### 13.7 Continuation QA Commands

Continuation pass commands used:

```powershell
git status --short
rg --files app docs support-tool.html
rg -n "ActivityResultContracts\.[A-Za-z0-9_]+" app/src/main/java
rg -n "var [A-Za-z0-9_]+ by remember|val [A-Za-z0-9_]+ = remember|LaunchedEffect\(|DisposableEffect\(" app/src/main/java/com/example/ui app/src/main/java/com/example/MainActivity.kt
rg -n "testTag\(" app/src/main/java
rg -n "Toast\.makeText|SnackbarHostState|snackbar|Toast" app/src/main/java
rg -n "getSharedPreferences\(|getString\(|getBoolean\(|getInt\(|getLong\(|putString\(|putBoolean\(|putInt\(|putLong\(|remove\(" app/src/main/java/com/example/viewmodel/AnchorViewModel.kt app/src/main/java/com/example/service/FajrAlarmReceiver.kt app/src/main/java/com/example/ui/screens/FounderScreen.kt app/src/main/java/com/example/ui/screens/SurvivalScreen.kt app/src/main/java/com/example/util/PendingAutofillPinStore.kt
```
