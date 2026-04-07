# Zeno Dialer

**Stable release: v1.0.0** — Native Android dialer for the **Zinwa Q25**, optimized for square displays (720×720) with a physical keyboard and D-pad.

**Repository:** [github.com/faisal-ops/zeno-dialer](https://github.com/faisal-ops/zeno-dialer)

---

[![Release](https://img.shields.io/github/v/release/faisal-ops/zeno-dialer?style=flat-square&label=release)](https://github.com/faisal-ops/zeno-dialer/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/faisal-ops/zeno-dialer/total?style=flat-square&label=downloads)](https://github.com/faisal-ops/zeno-dialer/releases)

[![Releases](https://img.shields.io/badge/Releases-1.0.0-green?style=for-the-badge)](https://github.com/faisal-ops/zeno-dialer/releases)

---

## Features

- **3-tab navigation** — Favorites, Home (call log), and Keypad
- **Adaptive dialpad** — Layout that fits square screens
- **Haptic feedback** — Light vibration on keypad presses
- **Inline call details** — Tap to expand entries (Call, Message, History)
- **Bottom sheet actions** — Long-press for edit contact, favorites, block, copy, history, delete
- **Call history detail** — Per-number history with date grouping
- **Favorites** — Pin/unpin and suggestions from recents
- **Hardware keys** — Call / End, D-pad, QWERTY input; background Call opens app without auto-dial
- **Default dialer** — `ROLE_DIALER` flow and setup prompts
- **AccessibilityService** — Reliable toolbar Call/End routing
- **Missed-call notification** — Ignored/rejected incoming calls show in the notification shade
- **Voice search** — Speech-to-text for contact search
- **Blocked numbers** — Block list management

## Technical details

| | |
|---|---|
| **Language** | Kotlin |
| **UI** | Jetpack Compose + Material 3 |
| **Min / target SDK** | 34 (Android 14) |
| **Package** | `com.zeno.dialer` |
| **Image loading** | Coil |

## Permissions

| Permission | Purpose |
|---|---|
| `READ_CALL_LOG` / `WRITE_CALL_LOG` | Call history |
| `READ_CONTACTS` | Contacts & search |
| `CALL_PHONE` | Place calls |
| `READ_PHONE_STATE` | Call state |
| `POST_NOTIFICATIONS` | Ongoing / missed-call notifications |
| `MANAGE_OWN_CALLS` | In-call control |
| `BIND_INCALL_SERVICE` | Active calls |
| `BIND_SCREENING_SERVICE` | Call screening |

## Build

### Debug

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk` (not committed).

### Release (minified, signed)

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk` — **release** build (ProGuard + shrink resources).

**Signing**

- With **`keystore.properties`** and a valid `storeFile` (see `keystore.properties.example`), the APK is signed with your **upload / release keystore** (use this for Play Console and public “stable” distribution).
- Without that file, Gradle signs the release APK with the **debug** keystore so `assembleRelease` still succeeds locally; replace with your keystore before publishing.

## Project structure

```
zeno-dialer/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/zeno/dialer/
│       │   ├── MainActivity.kt
│       │   ├── DialerViewModel.kt
│       │   ├── KeyHandler.kt
│       │   ├── CallHistoryDetailActivity.kt
│       │   ├── InCallActivity.kt
│       │   ├── InCallViewModel.kt
│       │   ├── CallStateHolder.kt
│       │   ├── SettingsActivity.kt
│       │   ├── data/          # Contact, repos, search
│       │   ├── service/       # InCall, accessibility, screening
│       │   └── ui/            # DialerScreen, ResultsList, theme
│       └── res/
│           ├── drawable/      # Launcher PNG, notification icon
│           ├── values/
│           └── xml/
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── keystore.properties.example
├── .gitignore
└── README.md
```

## Tested devices

| Device | Display | Status |
|---|---|---|
| Zinwa Q25 | 720×720 | Primary target |

## License

All rights reserved.
