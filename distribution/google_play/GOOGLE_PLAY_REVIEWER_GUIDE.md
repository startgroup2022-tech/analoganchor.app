# Google Play Release & Reviewer Execution Guide
**App**: Analog Anchor - Night Guard & Day Guard  
**Release Version**: `2.0` (`versionCode = 16`)  
**Package Name**: `com.analoganchor.app`

---

## 1. Summary of Key Changes & Architecture in this Release

### A. Interactive GPS Radar & Mosque Action Unification
- **Interchangeable Action Button**: Integrated directly beneath the dynamic radar map:
  - *During Active Prayer Window*: Displays `🕌 Verify [Prayer] Prayer (+5 Pts)` or `✓ [Prayer] Prayer Verified` when proximity to the designated mosque sanctuary is confirmed.
  - *Outside Prayer Window*: Dynamically transitions to `🕌 Pin Current Location as Anchor` allowing users to anchor their designated sanctuary.

### B. Maghrib-to-Isha Sacred Retreat (خلوة المغرب والعشاء) & Anti-Cheat
- **Strict Astronomical Window Enforcement**: Prevents premature prayer check-ins outside calculated astronomical prayer times.
- **Verification First**: Mosque GPS presence verification (within the 200m geofence) must succeed before starting the retreat session.
- **Dedicated Full-Screen Retreat HUD**: Features automated Do Not Disturb (DND) engagement, Quranic reflections, and deep focus isolation during the Maghrib-to-Isha window to eliminate digital distractions.

### C. "Thinking on Paper Ritual" (التدبر اليومي) & Strict NFC Hardware Validation
- **Rebranded Ritual**: Updated from legacy "Pomodoro Focus" to **"Thinking on Paper Ritual"** (Arabic: **"التدبر اليومي"**) emphasizing analog deep work and screen-free thinking.
- **Physical NFC Guard**: Requires exact physical NFC tag contact with the desktop organizer to initiate or verify returns, preventing false check-ins or software bypasses.
- **2-NFC Tag Architecture**: Clear separation between the Main Anchor tag (Day Guard override & ritual start) and the Fajr alarm eraser tag.

### D. Reserve Ledger Balance & Day Guard 150 PTS (30%) Milestone
- **Clean Starting Balance**: Initial reserve points start at `0 PTS` (0% of 500 maximum ceiling).
- **30% Unlock Milestone Nudge**: Accumulating $\ge 150$ PTS unlocks the 1-minute Day Guard protected apps window with a dynamic status banner:
  - English: `🎯 150+ PTS (≥30%): DAY GUARD UNLOCK READY`
  - Arabic (BiDi formatted): `🎯 ١٥٠+ نقطة (≥٣٠٪): مؤهل لفتح الـDay Guard`
- **Updated Metrics Grid**:
  - Left Metric: `PAPER RITUAL` / `التدبر اليومي` streak.
  - Middle Metric: `RETREAT` / `خلوة المغرب` streak (replaces legacy access status).
  - Right Metric: `LAST FAJR` / `الفجر` streak.
  - Bottom Tracker: `WEEKLY MOSQUE PRAYERS: X / 35` / `صلوات المسجد الأسبوعية: X / ٣٥` (tracking 5 daily prayers over 7 days).

### E. Flexible Day Guard & 5:00 PM Daily Pre-Commitment Lockdown
- **One-Time Daily Decision**: Bedtime and accountability cutoffs in the **Day Guard tab** and in **System Configuration** can be freely planned and tailored earlier in the day.
- **Immutable 5:00 PM Lockdown**: Every day at **5:00 PM**, if bedtime hasn't been modified, it locks down and grays out automatically. The user cannot alter bedtime or extend protected apps access until sunrise the following morning.
- **1:00 AM Extreme Hard Limit**: For founders and late-night workers, the system configuration accommodates an extreme late bedtime threshold (up to 1:00 AM max ceiling), preserving flexible customization while maintaining strict anti-procrastination rules once 5:00 PM arrives.

### F. BiDi (RTL) Arabic Typography Formatting
- Applied seamless Arabic Tatweel (`الـDay Guard`) and bidirectional unicode formatting to ensure Arabic text containing English product terms renders in natural Right-to-Left order without punctuation flipping or layout distortion.

---

## 2. Reviewer Step-by-Step Test Plan

### Test 1: Interactive Radar GPS Sync & Interchangeable Action Button
1. Launch the app and navigate to the **Mosque** tab.
2. Grant Location Permission when prompted and observe the radar map synchronizing with your current GPS coordinates.
3. **Outside Prayer Window**:
   - Verify that the button beneath the radar displays `🕌 Pin Current Location as Anchor` (or `🕌 Pin Mosque ([Name])`).
   - Tap the button to verify pinning the location as your designated sanctuary.
4. **During Active Prayer Window** (e.g., Dhuhr, Asr, Maghrib, Isha, or Fajr):
   - Verify that the button switches to `🕌 Verify [Prayer] Prayer (+5 Pts)`.
   - When within geofence range (200m), tap to verify prayer presence and confirm that the state transitions to `✓ [Prayer] Prayer Verified` (+5 PTS awarded).

### Test 2: Maghrib-to-Isha Retreat & DND Shield (Anti-Cheat Validation)
1. In the **Mosque** tab, locate the **Maghrib-to-Isha Retreat** (خلوة المغرب والعشاء) section.
2. **Anti-Cheat Validation**:
   - Attempting to start the retreat outside the Maghrib-to-Isha astronomical window or without GPS sanctuary verification will be blocked with a clear prompt requiring physical presence verification first.
3. **Active Retreat Session**:
   - Once inside the Maghrib-to-Isha window and verified at the mosque, tap **Start Sacred Retreat** (`بدء الخلوة المباركة`).
   - Grant Do Not Disturb (DND) access if prompted.
   - Confirm the full-screen retreat HUD activates, displaying the remaining countdown timer, Quranic reflection prompts, and complete isolation from distractions.
   - Verify that completing the retreat awards +40 PTS and increments the `RETREAT` streak.

### Test 3: Thinking on Paper Ritual (التدبر اليومي) NFC Verification
1. Navigate to the **Anchor** tab and locate the **Thinking on Paper Ritual** (`التدبر اليومي`).
2. Verify the rebranded interface emphasizing analog whiteboard and notebook focus.
3. Tap **Start Ritual** / initiate focus:
   - Verify that the app prompts to tap the physical **Main Anchor NFC tag** on the desk organizer.
   - Present the NFC tag to start the session.
4. Verify that session returns and early completion require the exact physical NFC tag contact, preventing accidental or software-only bypasses.
5. Verify the 2-NFC tag architecture:
   - **Main Anchor Tag**: Manages Day Guard overrides and Thinking on Paper ritual initiation.
   - **Fajr Alarm Tag**: Dedicated alarm eraser located away from the bedside.

### Test 4: Reserve Ledger Balance, 150 PTS Day Guard Unlock & Weekly 35-Prayer Counter
1. Open the app on a fresh install:
   - Verify that the starting Reserve Ledger balance is `0 PTS` (0% of 500 ceiling).
2. Earn points through verified rituals, prayer check-ins, or physical activity.
3. **30% Milestone Banner**:
   - When balance reaches $\ge 150$ PTS, verify the prominent status banner appears:
     - English: `🎯 150+ PTS (≥30%): DAY GUARD UNLOCK READY`
     - Arabic: `🎯 ١٥٠+ نقطة (≥٣٠٪): مؤهل لفتح الـDay Guard`
   - Verify that tapping the banner allows unlocking a 1-minute Day Guard protected apps window.
4. **Metrics Grid Verification**:
   - Left Metric: `PAPER RITUAL` / `التدبر اليومي` streak counter.
   - Middle Metric: `RETREAT` / `خلوة المغرب` streak counter.
   - Right Metric: `LAST FAJR` / `الفجر` streak counter.
   - Bottom Tracker: `WEEKLY MOSQUE PRAYERS: X / 35` / `صلوات المسجد الأسبوعية: X / ٣٥` accurately counts weekly verified prayers out of 35.

### Test 5: Flexible Day Guard & 5:00 PM Daily Pre-Commitment Lock (1:00 AM Limit)
1. Open the **Day Guard tab** or navigate to **Settings > System Configuration**.
2. **Before 5:00 PM**:
   - Verify that bedtime and accountability cutoffs can be freely customized (e.g., setting bedtime up to the 1:00 AM hard maximum limit for late-night workflows).
3. **At / After 5:00 PM**:
   - Verify the pre-commitment lock automatically engages: the bedtime selector grays out and becomes immutable.
   - Confirm that attempting to alter bedtime or extend protected apps displays the pre-commitment lock notice.
   - Verify that the lock remains strictly enforced until sunrise the next morning.

### Test 6: BiDi Arabic RTL Typography Verification (`الـDay Guard` & `التدبر اليومي`)
1. Switch app language to **العربية (Arabic)** via the top language toggle.
2. Verify that the UI flawlessly transitions to Right-to-Left (RTL) layout.
3. Inspect mixed Arabic-English terms:
   - Check that `الـDay Guard` displays with proper Arabic Tatweel connection without text fragmentation or flipped hyphens.
   - Verify `🎯 ١٥٠+ نقطة (≥٣٠٪): مؤهل لفتح الـDay Guard` renders in natural RTL reading order.
   - Verify `التدبر اليومي` and `خلوة المغرب والعشاء` headings, descriptions, and metrics align cleanly.

---

## 3. Build & Release Commands
To build the release bundle / APK for Google Play Console:
```bash
# Clean and compile debug
./gradlew assembleDebug

# Build release bundle (AAB)
./gradlew bundleRelease
```
