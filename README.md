# Bastion

> Your fortress against digital distraction.

Bastion is a privacy-first, on-device Android app blocker built for deep focus. It intercepts distracting apps at the OS level using Android's Accessibility and Overlay APIs, enforces time-based and schedule-driven blocking rules, and survives aggressive OEM background killers — all without sending a single byte of your data off your device.

---

## Core Features

- **App Blocking** — Block any installed app instantly or on a schedule
- **Schedule-Based Rules** — Define time windows and day patterns for automatic blocking
- **Persistent Foreground Service** — Survives Doze, OEM killers (Samsung, Xiaomi, BBK), and device reboot
- **System Overlay** — Full-screen block screen rendered via `WindowManager` + Jetpack Compose
- **PIN-Protected Unlock** — Encrypted admin PIN to temporarily bypass the blocker
- **Offline & Private** — Zero network requests, zero telemetry, all processing on-device

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 100% |
| UI | Jetpack Compose + Material Design 3 |
| Architecture | MVVM + Clean Architecture |
| DI | Dagger Hilt |
| Async | Kotlin Coroutines + StateFlow / SharedFlow |
| Storage | Room Database + EncryptedSharedPreferences |
| Background | ForegroundService + WorkManager watchdog |
| OS Hook | AccessibilityService + SYSTEM_ALERT_WINDOW overlay |

---

## Project Guidelines

All contributors must follow these documents before writing any code:

| Document | Purpose |
|---|---|
| [`coding_rules.md`](coding_rules.md) | Kotlin conventions, concurrency, architecture, service survival |
| [`ui_rules.md`](ui_rules.md) | Compose-only UI, UDF, Material 3, overlay implementation |
| [`security_rules.md`](security_rules.md) | AccessibilityService boundaries, encryption, obfuscation, permission audit |
| [`testing_rules.md`](testing_rules.md) | Unit, UI, and manual device testing protocols |
| [`git_rules.md`](git_rules.md) | Conventional Commits, branching strategy, atomic commits |
| [`workflow_rules.md`](workflow_rules.md) | Dev loop, pre-coding checklist, blocked implementation protocol |

---

## Requirements

- Android 13 (API 33) minimum
- Tested on Android 13 / 14 / 15
- Permissions required: `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `RECEIVE_BOOT_COMPLETED`
- AccessibilityService must be manually enabled in device Settings

---

## Architecture Overview

```
UI Layer          →  Jetpack Compose Screens + WindowManager Overlays
ViewModel Layer   →  StateFlow<UiState>, SharedFlow<UiEvent>, Hilt ViewModels
Domain Layer      →  Use Cases, AppForegroundDetector (pure Kotlin)
Data Layer        →  Room DAOs (Flow/suspend), EncryptedSharedPreferences
Service Layer     →  BlockerForegroundService, BlockerAccessibilityService
Watchdog          →  WorkManager PeriodicWorkRequest
```

---

## Privacy

Bastion does not collect, store, or transmit any personal data.
The AccessibilityService reads only foreground app package names — never screen content, text, or usage patterns.
All data remains on-device at all times.

---

## License

MIT
