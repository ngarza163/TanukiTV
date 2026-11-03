# Survey Assignment: Explorer 3 — UI Audio, Animations & Mascot Loader

## Mission
Investigate R3 (TV Sound Effects & UI Animations) and R4 (Animated Tanuki Mascot Loading Component).
Authoritative request: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md` (section `## 2026-10-28T04:24:53Z`).

## Scope of Investigation
1. Synthesized Low-Latency TV Sound Effects (`TvSoundManager`):
   - Zero-asset procedural audio synthesizer using `AudioTrack` / `ToneGenerator` / PCM wave synthesizer in Kotlin/Android.
   - Distinct sound generation for:
     * D-pad Focus Tick (soft high-tech micro-click)
     * Primary Action / Select (warm harmonic chord tap on `DPAD_CENTER` / `ENTER`)
     * Navigation / Tab Switch (subtle whoosh/chime)
     * Back / Dismiss (soft descending tone)
     * Watchlist Add / Favorite (bright confirmation chime)
   - Integration with `UserSettings`, `AppStorage`, and `SettingsScreen` ("UI Sound Effects" toggle, enabled by default).
   - Hooking into focusable elements, remote navigation, and actions across the app.
2. Screen & Card Animations:
   - `AnimatedContent` crossfade + vertical/horizontal slide transitions for tab switching in `MainActivity` and opening `DetailsScreen`.
   - Focus glow, glassmorphic badges, and shelf entrance animations.
3. Custom Animated Tanuki Loading Component (`TvTanukiLoadingIndicator`):
   - Locations of generic `CircularProgressIndicator` across `HomeScreen`, `SearchScreen`, `ScheduleScreen`, `DetailsScreen`, and `TvPlayerScreen`.
   - Design of `TvTanukiLoadingIndicator` using Compose `Canvas` / multi-layered vector drawing + `rememberInfiniteTransition` (bobbing, wagging tail, blinking eyes, glowing leaf/lantern aura, pulsing orbital ring, contextual status subtitles).
4. Build & Test Environment:
   - Check current Gradle test tasks, emulator connectivity (`emulator-5554`), and build verification workflow.

Produce a detailed `handoff.md` with file paths, line numbers, existing code snippets, required changes, and exact implementation recommendations.
