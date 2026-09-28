# DEBUGGING GUIDE

Analog Anchor now uses the accessibility tree only to discover AppBlock's live keypad bounds. It does not use hardcoded screen coordinates, Shizuku, root, or runtime ADB.

## Confirm The AppBlock Package

From a development machine with the phone connected:

```powershell
adb shell pm list packages | findstr /i appblock
adb shell dumpsys package | findstr /i appblock
```

The app currently listens for these exact package names:

- `cz.mobilesoft.appblock`
- `com.appblock.app`
- `cz.droidsonroids.appblock`

If the installed package is different, add it to:

- `app/src/main/java/com/example/util/AppBlockIntegration.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/anchor_accessibility_service_config.xml`

## Capture The AppBlock Tree

1. Enable the Analog Anchor accessibility service in Android Settings.
2. In Analog Anchor, authorize a valid `AA-...-EXP60` token.
3. Let Analog Anchor open AppBlock.
4. Watch Logcat:

```powershell
adb logcat -c
adb logcat -s AnchorAccessibility PendingPinStore AnchorTelemetry
```

When AppBlock is detected, the service logs `TREE[...]` chunks. Each node includes:

- `class`
- `text`
- `desc`
- `viewId`
- `clickable`
- `editable`
- `bounds`

## What To Look For

For normal keypad gesture unlock, the tree must expose digit labels through either `text` or `desc`. Good examples:

- `text="1"`
- `desc="1"`
- `desc="Digit 1"`
- `desc="Number 1 button"`

For an emergency code popup, the tree should expose either:

- an editable node with `class=android.widget.EditText`
- a node where `editable=true`

If the logs say `Missing AppBlock digit nodes`, inspect the tree dump and note how AppBlock names its digit buttons. The matching logic is in `AnchorAccessibilityService.matchedDigit`.

## Success Signals

Expected Logcat flow:

```text
Pending secure AppBlock unlock armed: true
AppBlock window detected
Dispatching dynamic AppBlock keypad gesture with ...
Secure AppBlock unlock complete
```

If Android rejects or cancels the gesture, the PIN remains armed until the 90-second pending window expires, so another AppBlock window event can retry.
