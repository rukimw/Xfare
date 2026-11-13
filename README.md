# LankaFare Native Android Project

Ultra-lightweight native Kotlin + XML Views application for Sri Lankan ride-hailing drivers (PickMe Driver, Uber Driver, HelaGo).

- **Package**: `com.lankafare.app`
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Dependencies**: `androidx.core:core-ktx`, `androidx.appcompat:appcompat` only (zero heavy third-party libraries, R8 minification & resource shrinking enabled for < 3 MB release APK).

## Key Source Files
- `app/src/main/java/com/lankafare/app/MainActivity.kt` — 1-screen setup & driver preferences UI (`SharedPreferences`)
- `app/src/main/java/com/lankafare/app/FareAccessibilityService.kt` — Window monitor restricted to `com.pickme.driver`, `com.ubercab.driver`, `lk.helago.driver` with 2-second cooldown
- `app/src/main/java/com/lankafare/app/FareParser.kt` — Pre-compiled Regex parser for Sri Lankan `Rs.`/`LKR` fares and `km` distances
- `app/src/main/java/com/lankafare/app/OverlayManager.kt` — `WindowManager` (`TYPE_APPLICATION_OVERLAY`) color-coded banner with 8-second auto-dismiss
- `app/src/main/java/com/lankafare/app/PrefsManager.kt` — Persistent driver preferences
- `app/src/main/res/layout/activity_main.xml` & `overlay_fare_banner.xml` — Native XML layouts

## Build Release APK
```bash
gradle assembleRelease
```
Output APK: `app/build/outputs/apk/release/app-release.apk`
