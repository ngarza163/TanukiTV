# Survey Investigation Report: TV Remote Sound Effects, UI Animations & Tanuki Mascot Loader (R3 & R4)

**Agent**: `explorer_upgrade_survey_3`  
**Working Directory**: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\explorer_upgrade_survey_3`  
**Target Milestone**: `upgrade_survey` — R3 & R4 Implementation Blueprint  

---

## 1. Observation

### 1.1 Existing Loading Indicators (`CircularProgressIndicator`)
A codebase-wide search across `app/src/main/` revealed exactly five full-screen/major loading state locations using bare `CircularProgressIndicator(color = TvPrimaryRed)`:

1. **`HomeScreen.kt:112`**:
   ```kotlin
   105:     if (isLoading && trendingList.isEmpty()) {
   106:         Box(
   107:             modifier = modifier
   108:                 .fillMaxSize()
   109:                 .background(Color.Transparent),
   110:             contentAlignment = Alignment.Center
   111:         ) {
   112:             CircularProgressIndicator(color = TvPrimaryRed)
   113:         }
   114:         return
   115:     }
   ```
2. **`SearchScreen.kt:551`**:
   ```kotlin
   544:         if (isSearching) {
   545:             Box(
   546:                 modifier = Modifier
   547:                     .fillMaxWidth()
   548:                     .weight(1f),
   549:                 contentAlignment = Alignment.Center
   550:             ) {
   551:                 CircularProgressIndicator(color = TvPrimaryRed)
   552:             }
   ```
3. **`ScheduleScreen.kt:254`**:
   ```kotlin
   253:                     Column(horizontalAlignment = Alignment.CenterHorizontally) {
   254:                         CircularProgressIndicator(color = TvPrimaryRed)
   255:                         Spacer(modifier = Modifier.height(12.dp))
   256:                         Text(
   257:                             text = "Loading $selectedDay Simulcast Schedule...",
   258:                             color = TvTextSecondary,
   259:                             fontSize = 13.sp
   260:                         )
   261:                     }
   ```
4. **`DetailsScreen.kt:495`**:
   ```kotlin
   488:             if (isLoading && episodes.isEmpty()) {
   489:                 Box(
   490:                     modifier = Modifier
   491:                         .fillMaxWidth()
   492:                         .height(140.dp),
   493:                     contentAlignment = Alignment.Center
   494:                 ) {
   495:                     CircularProgressIndicator(color = TvPrimaryRed)
   496:                 }
   ```
5. **`TvPlayerScreen.kt:786`**:
   ```kotlin
   778:         if (isLoadingStreams || isBuffering) {
   779:             Box(
   780:                 modifier = Modifier
   781:                     .align(Alignment.Center)
   782:                     .background(Color(0xB308090E), RoundedCornerShape(16.dp))
   783:                     .padding(horizontal = 24.dp, vertical = 18.dp)
   784:             ) {
   785:                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
   786:                     CircularProgressIndicator(color = TvPrimaryRed)
   787:                     Spacer(modifier = Modifier.height(10.dp))
   788:                     Text(
   789:                         text = if (isLoadingStreams) "Resolving High-Speed Stream..." else "Buffering...",
   790:                         color = Color.White,
   791:                         fontSize = 14.sp,
   792:                         fontWeight = FontWeight.SemiBold
   793:                     )
   794:                 }
   795:             }
   796:         }
   ```
*(Note: `SourcesScreen.kt:279` and `411` contain micro inline spinners for benchmark rows and submit buttons, which can remain minimal or use a compact mascot spinner).*

### 1.2 Tanuki Mascot Vector Characteristics (`tanukitv_logo.svg`)
The root vector asset `tanukitv_logo.svg:34-108` defines the official Tanuki mascot anatomy:
- **Tail** (`lines 36-45`): Fluffy curved striped tail (`#8F4813` base fur, `#382119` dark stripes and circular tip).
- **Antenna Ears** (`lines 47-53`): Cyber-tanuki antennas curving upward with glowing cyan tips (`stroke="#00F2FE"`, `circle r="4" fill="#00F2FE"`).
- **Outer Ears** (`lines 55-62`): Pointed rounded ears (`#8F4813` outer fur, `#FFA570` peach inner ear).
- **Face / Head** (`lines 63-65`): Ellipse (`cx="43" cy="62" rx="36" ry="30"`), fur gradient from `#BC6C25` (amber) down to `#8F4813`.
- **Mask Patches** (`lines 66-71`): Distinctive raccoon dog dark eye surrounds (`fill="#382119"` to `#23140F`).
- **Muzzle & Cheeks** (`lines 72-76`): Light cream muzzle (`#FFF3E0`) and cheeks (`#FFE0B2`).
- **Anime Eyes** (`lines 77-86`): Glossy dark ellipse (`#111111`) with dual white highlights (`r="2.2"` top-left, `r="1.1"` bottom-right).
- **Blush Cheeks** (`lines 87-90`): Cute pink blush ellipses (`#FF708F`, opacity `0.65`).
- **Nose & Mouth** (`lines 91-96`): Rounded triangular nose (`#2E1C14`) and happy curved smile.
- **Paws** (`lines 97-107`): Front paws resting forward (`#FFF3E0` with `#DDB892` claw dividers).

### 1.3 Audio Infrastructure & Focus Modifiers
- Currently, **zero** procedural UI sound synthesizer exists in the application.
- `app/src/main/res/raw` and `app/src/main/assets` contain no audio media files.
- `player/audio/AudioEffectsManager.kt` exists exclusively for active ExoPlayer sessions (Equalizer, LoudnessEnhancer for DRC and Clear Voice).
- `FocusModifiers.kt:39-97` defines `Modifier.tvFocusableItem(...)`. This is the universal focus modifier used across all cards, buttons, chips, and shelves.
  - Line 70: `.onFocusChanged { focusState -> isFocused = focusState.isFocused ... }`
  - Line 74: `.onKeyEvent { keyEvent -> if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP && (keyCode == KEYCODE_DPAD_CENTER || keyCode == KEYCODE_ENTER)) { onClick?.invoke() ... } }`
  - Line 88: `.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)`
- Focus glow: `FocusModifiers.kt:65-69` currently only renders a single 1-layer border (`.border(width = if (isFocused) borderWidth else 1.dp, color = ...)`). There is **no focus glow or ambient halo**.

### 1.4 Settings & Storage
- `UserSettings.kt:6-34`: Contains display, playback, and focus options (`focusRingColor`, `focusScale`), but does **not** yet have `uiSoundEffects: Boolean`.
- `AppDatabase.kt:204-258`: `AppStorage.getUserSettings()` and `saveUserSettings()` persist preferences via `SharedPreferences("animetv_settings")`.
- `SettingsScreen.kt:829-866`: Under `SettingsCategory.APPEARANCE`, options exist for "TV Remote Focus Border Color", "Remote Focus Zoom Scale", and "Hardware Video Acceleration", but no toggle for "UI Sound Effects".

### 1.5 Screen Transitions & Navigation (`MainActivity.kt`)
- `MainActivity.kt:481-540`: Tab content switches using a bare `when (selectedNavTab)` inside `Box` with no animation (instant cut).
- `MainActivity.kt:312-326`: Details drill-down uses raw `if (selectedAnimeForDetails != null) DetailsScreen(...) else ...` with no transition.
- `MainActivity.kt:171-173`: `BackHandler` closes details with no audio feedback.
- `DetailsScreen.kt:415-425`: Watchlist toggle updates `storage` without audio feedback.

### 1.6 Build & Test Environment
- Gradle 8.9 with JVM 21.0.10.
- `.\gradlew.bat testDebugUnitTest` ran and passed in 19 seconds (all 25 actionable tasks succeeded).
- `.\gradlew.bat assembleDebug` ran and passed in 9 seconds (APK built at `app\build\outputs\apk\debug\app-debug.apk`).
- `adb devices` detected `emulator-5554` running Android 16 with package `com.darknet.animetv.debug` actively running `MainActivity`.

---

## 2. Logic Chain

```
[Observation 1.3: Zero sound assets in project, zero procedural UI sound class]
  └──> [Logic Step 1]: To satisfy R3 with zero asset dependencies and zero latency, create a procedural PCM wave synthesizer using Android AudioTrack in MODE_STATIC.
       Pre-generating raw PCM 16-bit mono 44.1kHz wave byte arrays into in-memory AudioTrack buffers eliminates disk I/O, prevents asset missing bugs, and guarantees <5ms playback latency.

[Observation 1.3: FocusModifiers.tvFocusableItem is used by all cards, chips, buttons]
  └──> [Logic Step 2]: Hooking TvSoundManager.playFocusTick() on focus change (when !isFocused && focusState.isFocused) and playActionSelect() on DPAD_CENTER/ENTER inside tvFocusableItem centrally provides remote sound effects across the entire app with zero boilerplate.

[Observation 1.5: MainActivity uses instant cuts for tab switching and DetailsScreen drill-down]
  └──> [Logic Step 3]: Replacing raw when(selectedNavTab) with AnimatedContent provides directional slide (horizontal offset +/- 8%) + crossfade.
       Wrapping DetailsScreen drill-down with AnimatedContent provides vertical slide (+/- 6% height) + crossfade, and plays TvSoundManager.playTabSwitch() on open and playBackDismiss() on BackHandler.

[Observation 1.4: UserSettings and SettingsScreen lack UI sound toggle]
  └──> [Logic Step 4]: Add `val uiSoundEffects: Boolean = true` to UserSettings, persist via SharedPreferences in AppDatabase, and add a SettingToggleRow under SettingsCategory.APPEARANCE. TvSoundManager.setEnabled() directly binds to this setting.

[Observation 1.1 & 1.2: Bare CircularProgressIndicator across 5 screens, official mascot SVG present in root]
  └──> [Logic Step 5]: Build a pure Compose Canvas TvTanukiLoadingIndicator using rememberInfiniteTransition:
       - Bobbing: vertical float oscillation (-6f to +6f).
       - Tail wagging: rotational sweep (-18° to +18°).
       - Blinking eyes: scaleY animation keyframed (open 95% of time, quick 150ms blink).
       - Glowing leaf/lantern aura: pulsing radial alpha (0.4f to 0.95f) in cyan/emerald.
       - Pulsing orbital ring: continuous 360° rotating arc sweep and radial expansion.
       - Contextual status subtitles: animated text with pulsing ellipsis.
       Replacing CircularProgressIndicator in HomeScreen, SearchScreen, ScheduleScreen, DetailsScreen, and TvPlayerScreen satisfies R4.
```

---

## 3. Caveats

1. **Robolectric & Headless Unit Test Environments**:
   In headless JVM tests, Android `AudioTrack.Builder().build()` or native audio hardware can throw `UnsupportedOperationException` or `IllegalArgumentException`.
   *Design mitigation*: Wrap `AudioTrack` initialization and playback in `try { ... } catch (e: Throwable) { ... }` while updating atomic state tracking (`lastPlayedSound: SoundType?`, `playCount: Long`). This guarantees tests run cleanly without crashes while verifying sound trigger logic.
2. **Audio Track Replay Race Conditions**:
   Rapid D-pad scrolling could trigger `playFocusTick()` 10 times in 500ms.
   *Design mitigation*: A static `AudioTrack` can be quickly reset with `pause()`, `reloadStaticData()`, and `play()`, or alternate between a ping-pong pair of two static tracks so fast ticks never clip.
3. **Focus Preservation during `AnimatedContent` Transitions**:
   When switching tabs via `AnimatedContent`, the content is replaced dynamically. To prevent focus from being lost into the void, `LaunchedEffect` or `onNavigateUp` must restore focus to `navTabFocusRequesters[selectedNavTab]` or the first focusable element of the incoming screen.
4. **Android TV OS Accessibility / Mute**:
   If system sound effects are turned off or if the user toggles "UI Sound Effects" to false in `SettingsScreen`, `TvSoundManager.setEnabled(false)` immediately silences all audio playback.

---

## 4. Conclusion & Implementation Blueprint

### 4.1 Architecture Component 1: `TvSoundManager.kt`
Create `app/src/main/java/com/darknet/animetv/audio/TvSoundManager.kt`:

```kotlin
package com.darknet.animetv.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Zero-asset procedural audio synthesizer for Android TV living room remote navigation.
 * Generates low-latency (<5ms) synthesized sound effects in memory via AudioTrack in MODE_STATIC.
 */
object TvSoundManager {
    private const val TAG = "TvSoundManager"
    private const val SAMPLE_RATE = 44100

    enum class SoundType {
        FOCUS_TICK,
        ACTION_SELECT,
        TAB_SWITCH,
        BACK_DISMISS,
        WATCHLIST_ADD
    }

    @Volatile
    private var isEnabled: Boolean = true

    // Diagnostic tracking for unit testing
    var lastPlayedSound: SoundType? = null
        private set
    var playCount: Long = 0L
        private set

    private val trackMap = ConcurrentHashMap<SoundType, AudioTrack>()

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun isEnabled(): Boolean = isEnabled

    fun resetStats() {
        lastPlayedSound = null
        playCount = 0L
    }

    fun init(context: Context? = null) {
        try {
            // Pre-synthesize all sound buffers
            trackMap[SoundType.FOCUS_TICK] = createStaticTrack(generateFocusTickPcm())
            trackMap[SoundType.ACTION_SELECT] = createStaticTrack(generateActionSelectPcm())
            trackMap[SoundType.TAB_SWITCH] = createStaticTrack(generateTabSwitchPcm())
            trackMap[SoundType.BACK_DISMISS] = createStaticTrack(generateBackDismissPcm())
            trackMap[SoundType.WATCHLIST_ADD] = createStaticTrack(generateWatchlistAddPcm())
        } catch (e: Throwable) {
            Log.w(TAG, "AudioTrack init skipped (hardware or test runner): ${e.message}")
        }
    }

    private fun play(type: SoundType) {
        if (!isEnabled) return
        lastPlayedSound = type
        playCount++

        val track = trackMap[type] ?: return
        try {
            track.pause()
            track.reloadStaticData()
            track.play()
        } catch (e: Throwable) {
            // Graceful fallback
        }
    }

    fun playFocusTick() = play(SoundType.FOCUS_TICK)
    fun playActionSelect() = play(SoundType.ACTION_SELECT)
    fun playTabSwitch() = play(SoundType.TAB_SWITCH)
    fun playBackDismiss() = play(SoundType.BACK_DISMISS)
    fun playWatchlistAdd() = play(SoundType.WATCHLIST_ADD)

    fun release() {
        trackMap.values.forEach {
            try {
                it.stop()
                it.release()
            } catch (_: Throwable) {}
        }
        trackMap.clear()
    }

    private fun createStaticTrack(pcmData: ByteArray): AudioTrack {
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcmData.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        audioTrack.write(pcmData, 0, pcmData.size)
        return audioTrack
    }

    // --- Waveform Synthesizers (PCM 16-bit Mono, Little Endian) ---

    /** Soft high-tech micro-click: 26ms burst, 1800Hz + 3600Hz overtone, sharp exponential decay */
    fun generateFocusTickPcm(): ByteArray {
        val durationMs = 26
        val sampleCount = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ByteArray(sampleCount * 2)

        for (i in 0 until sampleCount) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-t * 220.0) // Steep decay
            val wave = 0.8 * sin(2.0 * PI * 1800.0 * t) + 0.2 * sin(2.0 * PI * 3600.0 * t)
            val sample = (wave * envelope * 0.18 * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            buffer[i * 2] = (sample.toInt() and 0xFF).toByte()
            buffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return buffer
    }

    /** Warm harmonic chord tap: 120ms, C5 (523Hz) + E5 (659Hz) + G5 (784Hz) + C6 (1046Hz) */
    fun generateActionSelectPcm(): ByteArray {
        val durationMs = 120
        val sampleCount = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ByteArray(sampleCount * 2)

        for (i in 0 until sampleCount) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = (t / 0.005).coerceAtMost(1.0)
            val decay = exp(-t * 28.0)
            val envelope = attack * decay
            val wave = 0.40 * sin(2.0 * PI * 523.25 * t) +
                       0.30 * sin(2.0 * PI * 659.25 * t) +
                       0.20 * sin(2.0 * PI * 783.99 * t) +
                       0.10 * sin(2.0 * PI * 1046.50 * t)
            val sample = (wave * envelope * 0.30 * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            buffer[i * 2] = (sample.toInt() and 0xFF).toByte()
            buffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return buffer
    }

    /** Upward navigation whoosh/chime: 90ms frequency sweep from 480Hz to 1100Hz */
    fun generateTabSwitchPcm(): ByteArray {
        val durationMs = 90
        val sampleCount = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ByteArray(sampleCount * 2)

        for (i in 0 until sampleCount) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / (durationMs / 1000.0)
            val freq = 480.0 + (620.0 * progress)
            val envelope = sin(PI * progress) // Bell curve
            val wave = sin(2.0 * PI * freq * t)
            val sample = (wave * envelope * 0.24 * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            buffer[i * 2] = (sample.toInt() and 0xFF).toByte()
            buffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return buffer
    }

    /** Soft descending dismiss tone: 110ms pitch slide from 784Hz down to 440Hz */
    fun generateBackDismissPcm(): ByteArray {
        val durationMs = 110
        val sampleCount = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ByteArray(sampleCount * 2)

        for (i in 0 until sampleCount) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / (durationMs / 1000.0)
            val freq = 784.0 - (344.0 * progress)
            val envelope = exp(-t * 22.0)
            val wave = sin(2.0 * PI * freq * t)
            val sample = (wave * envelope * 0.22 * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            buffer[i * 2] = (sample.toInt() and 0xFF).toByte()
            buffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return buffer
    }

    /** Bright confirmation chime: 200ms ascending arpeggio with high sparkle (D5 -> G5 -> D6) */
    fun generateWatchlistAddPcm(): ByteArray {
        val durationMs = 200
        val sampleCount = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ByteArray(sampleCount * 2)

        for (i in 0 until sampleCount) {
            val t = i.toDouble() / SAMPLE_RATE
            val note = when {
                t < 0.06 -> 587.33  // D5
                t < 0.12 -> 783.99  // G5
                else     -> 1174.66 // D6
            }
            val noteTime = when {
                t < 0.06 -> t
                t < 0.12 -> t - 0.06
                else     -> t - 0.12
            }
            val envelope = exp(-noteTime * 18.0)
            val wave = 0.75 * sin(2.0 * PI * note * t) + 0.25 * sin(2.0 * PI * note * 2.0 * t)
            val sample = (wave * envelope * 0.32 * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            buffer[i * 2] = (sample.toInt() and 0xFF).toByte()
            buffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return buffer
    }
}
```

### 4.2 Architecture Component 2: `TvTanukiLoadingIndicator.kt`
Create `app/src/main/java/com/darknet/animetv/ui/components/TvTanukiLoadingIndicator.kt`:
- **Canvas-rendered mascot**: Draws head, striped tail, outer ears, cyan antennas, raccoon dark eye patches, anime glossy eyes with double specular reflections, cute pink blush cheeks, nose, smiling mouth, paws, and a glowing magical leaf on forehead.
- **`rememberInfiniteTransition` animations**:
  - `bobbing`: `animateFloat(initialValue = -5f, targetValue = 5f, animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse))`
  - `tailWag`: `animateFloat(initialValue = -16f, targetValue = 16f, animationSpec = infiniteRepeatable(tween(650, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Reverse))`
  - `eyeBlink`: Keyframed scaleY (`0ms -> 1f, 2600ms -> 1f, 2750ms -> 0.1f, 2900ms -> 1f, 3000ms -> 1f`)
  - `auraPulse`: Pulsing radial glow (`0.35f to 0.90f`, 1200ms)
  - `ringRotation`: `0f to 360f` continuous rotating orbital particle ring (3000ms)
- **Contextual Subtitles**:
  - Title message: `fontSize = 15.sp`, bold white text with animated cycling ellipsis dots (`.`, `..`, `...`).
  - Subtitle: `fontSize = 12.sp`, `TvTextSecondary` contextual task status.
- **Compact mode**: Scaled down (55% dimensions) for inline player buffering and smaller shelf loading.

### 4.3 Settings & Persistence Modifications
1. **`UserSettings.kt`**:
   Add line 16:
   ```kotlin
   val uiSoundEffects: Boolean = true, // TV remote audio feedback (Focus tick, Select, Tab switch, Back, Watchlist)
   ```
2. **`AppDatabase.kt`**:
   In `getUserSettings()`:
   ```kotlin
   uiSoundEffects = prefs.getBoolean("pref_ui_sound_effects", true),
   ```
   In `saveUserSettings()`:
   ```kotlin
   .putBoolean("pref_ui_sound_effects", settings.uiSoundEffects)
   ```
3. **`SettingsScreen.kt`**:
   In `SettingsCategory.APPEARANCE` (after `SettingOptionSelector` for `focusScale`):
   ```kotlin
   item {
       SettingToggleRow(
           title = "UI Sound Effects",
           description = "Play subtle low-latency audio feedback when navigating with the TV remote",
           isChecked = currentSettings.uiSoundEffects,
           onToggle = { enabled ->
               val updated = currentSettings.copy(uiSoundEffects = enabled)
               updateSettings(updated, if (enabled) "UI Sound Effects enabled" else "UI Sound Effects muted")
               TvSoundManager.setEnabled(enabled)
               if (enabled) TvSoundManager.playActionSelect()
           }
       )
   }
   ```
4. **`AnimeTvApplication.kt`**:
   In `onCreate()`:
   ```kotlin
   val settings = storage.getUserSettings()
   TvSoundManager.setEnabled(settings.uiSoundEffects)
   TvSoundManager.init(applicationContext)
   ```

### 4.4 Focus Modifiers & Focus Glow Modifications (`FocusModifiers.kt`)
1. **Focus Glow**:
   Add multi-pass `drawBehind` in `tvFocusableItem`:
   ```kotlin
   val glowAlpha by animateFloatAsState(
       targetValue = if (isFocused) 0.55f else 0f,
       animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
       label = "focus_glow_alpha"
   )
   ```
   Draw layered soft bloom around the element:
   ```kotlin
   .drawBehind {
       if (glowAlpha > 0.01f) {
           val glowColor = (if (focusedBorderColor == Color.White) TvFocusGlow else focusedBorderColor).copy(alpha = glowAlpha)
           // Outer bloom
           drawRoundRect(
               color = glowColor.copy(alpha = glowAlpha * 0.35f),
               topLeft = Offset(-6.dp.toPx(), -6.dp.toPx()),
               size = Size(size.width + 12.dp.toPx(), size.height + 12.dp.toPx()),
               cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
           )
           // Inner bloom
           drawRoundRect(
               color = glowColor.copy(alpha = glowAlpha * 0.70f),
               topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
               size = Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()),
               cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
           )
       }
   }
   ```
2. **Audio Hooks**:
   ```kotlin
   .onFocusChanged { focusState ->
       if (!isFocused && focusState.isFocused) {
           TvSoundManager.playFocusTick()
       }
       isFocused = focusState.isFocused
       onFocusChanged?.invoke(focusState.isFocused)
   }
   .onKeyEvent { keyEvent ->
       if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP &&
           (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                   keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                   keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
       ) {
           TvSoundManager.playActionSelect()
           onClick?.invoke()
           true
       } else {
           false
       }
   }
   ```

### 4.5 Navigation & Screen Animation Modifications (`MainActivity.kt`)
1. **Horizontal Tab Navigation Transitions**:
   Replace lines 480-541 with:
   ```kotlin
   AnimatedContent(
       targetState = selectedNavTab,
       transitionSpec = {
           if (targetState > initialState) {
               (slideInHorizontally(initialOffsetX = { (it * 0.08f).toInt() }, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(220)))
                   .togetherWith(slideOutHorizontally(targetOffsetX = { (-it * 0.08f).toInt() }, animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(180)))
           } else {
               (slideInHorizontally(initialOffsetX = { (-it * 0.08f).toInt() }, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(220)))
                   .togetherWith(slideOutHorizontally(targetOffsetX = { (it * 0.08f).toInt() }, animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(180)))
           }
       },
       label = "tab_navigation_animated_content"
   ) { tabIndex ->
       when (tabIndex) {
           0 -> HomeScreen(...)
           1 -> SearchScreen(...)
           2 -> ScheduleScreen(...)
           3 -> WatchlistScreen(...)
           4 -> SettingsScreen(...)
       }
   }
   ```
2. **Drill-down to DetailsScreen**:
   Wrap `selectedAnimeForDetails` with:
   ```kotlin
   AnimatedContent(
       targetState = selectedAnimeForDetails,
       transitionSpec = {
           if (targetState != null) {
               (slideInVertically(initialOffsetY = { (it * 0.06f).toInt() }, animationSpec = tween(260, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(220)))
                   .togetherWith(fadeOut(animationSpec = tween(180)))
           } else {
               fadeIn(animationSpec = tween(200))
                   .togetherWith(slideOutVertically(targetOffsetY = { (it * 0.06f).toInt() }, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(180)))
           }
       },
       label = "details_drilldown_transition"
   ) { animeForDetails ->
       if (animeForDetails != null) {
           DetailsScreen(...)
       } else {
           // Tab Header + Active Tab Content
       }
   }
   ```
3. **Sound Triggers**:
   - Tab switch button click (`MainActivity.kt:440`):
     ```kotlin
     onClick = {
         if (selectedNavTab != index) {
             TvSoundManager.playTabSwitch()
             selectedNavTab = index
         }
     }
     ```
   - Opening Details (`selectedAnimeForDetails = it`):
     ```kotlin
     TvSoundManager.playTabSwitch()
     selectedAnimeForDetails = it
     ```
   - BackHandler (`MainActivity.kt:171`):
     ```kotlin
     BackHandler(enabled = selectedAnimeForDetails != null && activePlaybackSession == null) {
         TvSoundManager.playBackDismiss()
         selectedAnimeForDetails = null
     }
     ```
   - Watchlist Add (`DetailsScreen.kt:415`):
     ```kotlin
     if (isFavorite) {
         storage.removeFavorite(displayAnime.id)
         isFavorite = false
         TvSoundManager.playBackDismiss()
     } else {
         storage.addFavorite(displayAnime)
         isFavorite = true
         TvSoundManager.playWatchlistAdd()
     }
     ```

### 4.6 Loading Screen Replacements
1. **`HomeScreen.kt:112`**:
   ```kotlin
   TvTanukiLoadingIndicator(
       message = "Summoning anime catalog...",
       subMessage = "Curating trending series & fresh releases"
   )
   ```
2. **`SearchScreen.kt:551`**:
   ```kotlin
   TvTanukiLoadingIndicator(
       message = "Searching the anime multiverse...",
       subMessage = "Querying live sources and metadata"
   )
   ```
3. **`ScheduleScreen.kt:254`**:
   ```kotlin
   TvTanukiLoadingIndicator(
       message = "Checking broadcast satellites...",
       subMessage = "Loading $selectedDay Simulcast Schedule"
   )
   ```
4. **`DetailsScreen.kt:495`**:
   ```kotlin
   TvTanukiLoadingIndicator(
       message = "Unrolling episode scrolls...",
       subMessage = "Resolving episode catalog & streams",
       compact = true
   )
   ```
5. **`TvPlayerScreen.kt:786`**:
   ```kotlin
   TvTanukiLoadingIndicator(
       message = if (isLoadingStreams) "Resolving High-Speed Streams..." else "Buffering Stream...",
       subMessage = if (isLoadingStreams) "Fetching SUB & DUB feeds" else "Puffing up video cache buffer",
       compact = true
   )
   ```

---

## 5. Verification Method

### 5.1 Unit Test Suite (`TvSoundManagerTest.kt`)
Create `app/src/test/java/com/darknet/animetv/audio/TvSoundManagerTest.kt`:
1. Verify procedural wave generators return valid non-empty byte arrays with 16-bit PCM alignment:
   - `assertNotNull(TvSoundManager.generateFocusTickPcm())`
   - `assertTrue(TvSoundManager.generateFocusTickPcm().size > 0)`
   - `assertEquals(0, TvSoundManager.generateFocusTickPcm().size % 2)`
2. Verify sound effect dispatch and tracking:
   - `TvSoundManager.playFocusTick()` -> `assertEquals(SoundType.FOCUS_TICK, TvSoundManager.lastPlayedSound)`
   - `TvSoundManager.playActionSelect()` -> `assertEquals(SoundType.ACTION_SELECT, TvSoundManager.lastPlayedSound)`
   - `TvSoundManager.playTabSwitch()` -> `assertEquals(SoundType.TAB_SWITCH, TvSoundManager.lastPlayedSound)`
   - `TvSoundManager.playBackDismiss()` -> `assertEquals(SoundType.BACK_DISMISS, TvSoundManager.lastPlayedSound)`
   - `TvSoundManager.playWatchlistAdd()` -> `assertEquals(SoundType.WATCHLIST_ADD, TvSoundManager.lastPlayedSound)`
3. Verify mute toggle:
   - `TvSoundManager.setEnabled(false)`
   - Call `playFocusTick()` -> verify `playCount` did not increment.
4. Verify `UserSettings` and `AppStorage` persistence for `uiSoundEffects`:
   - Toggle `uiSoundEffects = false`, save to `AppStorage`, reload, assert `assertFalse(loaded.uiSoundEffects)`.

### 5.2 Verification Commands
Execute the following verification commands from the project root:
```powershell
# 1. Run unit test suite
.\gradlew.bat testDebugUnitTest

# 2. Build debug APK
.\gradlew.bat assembleDebug

# 3. Install on live Android TV emulator
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk

# 4. Launch main activity
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 shell am start -n com.darknet.animetv.debug/com.darknet.animetv.MainActivity

# 5. D-pad navigation key events (verify smooth transitions, no focus traps)
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 shell input keyevent 20 # DPAD_DOWN
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 shell input keyevent 22 # DPAD_RIGHT
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 shell input keyevent 23 # DPAD_CENTER (Select)
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 shell input keyevent 4  # BACK
```

### 5.3 Invalidation Conditions
- Any compilation or linking errors during `.\gradlew.bat testDebugUnitTest assembleDebug`.
- Audio initialization throwing unhandled runtime exceptions on devices or test runners.
- Focus traps occurring during `AnimatedContent` tab switches or details drill-down.
- Any screen still displaying generic `CircularProgressIndicator` instead of `TvTanukiLoadingIndicator`.
