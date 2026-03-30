# Software Requirements Specification & Technical Design Document
## AppBlocker — Advanced Android Digital Wellbeing Application

**Version:** 1.0
**Date:** 2026-03-30
**Target Platform:** Android 13+ (API Level 33+)
**Classification:** Internal Technical Document

---

## Table of Contents

1. [Introduction & Scope](#1-introduction--scope)
2. [System Overview & Architecture](#2-system-overview--architecture)
3. [Core App Blocking & Time Limits](#3-core-app-blocking--time-limits)
4. [Anti-Circumvention (Strict Admin Mode)](#4-anti-circumvention-strict-admin-mode)
5. [Granular In-App Interceptions](#5-granular-in-app-interceptions)
6. [Security, Privacy & Compliance](#6-security-privacy--compliance)
7. [Data Architecture & Storage](#7-data-architecture--storage)
8. [Background Services & OS Interactions](#8-background-services--os-interactions)
9. [UI/UX Design Specification](#9-uiux-design-specification)
10. [Permissions Manifest](#10-permissions-manifest)
11. [Known Limitations & Risk Register](#11-known-limitations--risk-register)
12. [Testing Strategy](#12-testing-strategy)

---

## 1. Introduction & Scope

### 1.1 Purpose

This document defines the functional requirements, system architecture, technical constraints, and design decisions for **AppBlocker**, an advanced Android application designed to enforce digital wellbeing limits. It is intended as a primary reference for all engineering, QA, and product decisions throughout the development lifecycle.

### 1.2 Problem Statement

Existing Android digital wellbeing solutions — including Google's own Digital Wellbeing app — operate at the coarse level of entire application packages. They cannot distinguish between productive and unproductive use *within* the same app. A user who needs Instagram DMs for work communication cannot block the Reels feed without also blocking their inbox. This document specifies a system that closes this gap.

Furthermore, built-in solutions are trivially bypassed by disabling them through Settings, uninstalling them, or using battery-saver modes to kill the background service. This document specifies an anti-circumvention architecture that makes bypass meaningfully difficult on Android 13+.

### 1.3 Scope

**In Scope:**
- Per-app and per-category daily time limits
- Scheduled blocking windows (Focus modes, Bedtime, custom schedules)
- Granular in-app feature blocking using Accessibility introspection
- Anti-circumvention measures using Accessibility-driven navigation interception
- Local-only, privacy-preserving processing of all screen content
- Google Play Store policy-compliant implementation

**Out of Scope:**
- Website / browser-level content filtering (separate subsystem, not addressed here)
- Parental controls with remote management (future roadmap)
- Cross-device synchronization of rules
- iOS implementation

### 1.4 Definitions & Abbreviations

| Term | Definition |
|------|-----------|
| **USM** | `UsageStatsManager` — Android API for querying app foreground time |
| **A11y** | `AccessibilityService` — Android system service for reading UI tree |
| **ANI** | `AccessibilityNodeInfo` — A node object representing a UI element |
| **SAW** | `SYSTEM_ALERT_WINDOW` — Permission to draw overlays above all apps |
| **DPM** | `DevicePolicyManager` — Android Device Administration API |
| **WM** | `WindowManager` — System service for programmatic window management |
| **Room** | Android Jetpack Room, SQLite abstraction layer |
| **Rule** | A user-defined blocking configuration for an app or in-app feature |
| **Block Screen** | A full-screen overlay drawn by this app preventing interaction with the target |

---

## 2. System Overview & Architecture

### 2.1 High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                        USER INTERFACE                        │
│   (Compose UI: Dashboard, Rule Editor, Schedule Manager)     │
└────────────────────────┬────────────────────────────────────┘
                         │ ViewModel / Repository layer
┌────────────────────────▼────────────────────────────────────┐
│                    ROOM DATABASE                             │
│   Tables: AppRules, InAppRules, UsageSnapshots, Schedules   │
└────────────┬─────────────────────────┬──────────────────────┘
             │                         │
┌────────────▼──────────┐  ┌──────────▼───────────────────────┐
│  FOREGROUND SERVICE   │  │     USAGE TRACKING ENGINE         │
│  (AppBlockerService)  │  │  (UsageStatsManager polling)      │
│  - Orchestrates all   │  │  - Queries every 60s              │
│    subsystems         │  │  - Updates Room snapshots         │
│  - Persistent notif.  │  │  - Fires limit-reached events     │
└────────────┬──────────┘  └───────────────────────────────────┘
             │
┌────────────▼──────────────────────────────────────────────┐
│             ACCESSIBILITY SERVICE ENGINE                    │
│   (AppBlockerA11yService extends AccessibilityService)     │
│                                                            │
│   ┌─────────────────┐    ┌──────────────────────────────┐  │
│   │  SCREEN MONITOR │    │   ANTI-CIRCUMVENTION GUARD   │  │
│   │  - Detects pkg  │    │   - Detects App Info/Settings│  │
│   │  - Evaluates    │    │   - Backs out / overlays     │  │
│   │    in-app rules │    │     block screen             │  │
│   │  - Triggers SAW │    └──────────────────────────────┘  │
│   └─────────────────┘                                      │
└────────────────────────────────────────────────────────────┘
             │
┌────────────▼──────────────────────────────────────────────┐
│              OVERLAY ENGINE (SAW Layer)                    │
│   - Draws TYPE_APPLICATION_OVERLAY windows                 │
│   - Full-screen block screen or partial feature blocker    │
│   - Touch-intercept mode for forbidden regions             │
└────────────────────────────────────────────────────────────┘
```

### 2.2 Process Architecture

The application runs across two OS processes:

1. **UI Process** — The standard application process hosting all Jetpack Compose screens, ViewModels, and the Room database DAO layer.
2. **Persistent Service Process** — A `:blocker` remote process hosting `AppBlockerService` (Foreground Service) and `AppBlockerA11yService` (Accessibility Service). Isolating this in a separate process (`android:process=":blocker"`) ensures the blocking logic survives even if the main UI process is killed by the OS.

### 2.3 Technology Stack

| Layer | Technology |
|-------|------------|
| UI | Jetpack Compose, Material 3 |
| State Management | ViewModel + StateFlow + Hilt DI |
| Local Database | Room 2.x with Kotlin coroutines |
| Background Processing | Foreground Service + WorkManager (for periodic tasks) |
| Screen Monitoring | `AccessibilityService` |
| Usage Tracking | `UsageStatsManager` |
| Overlay Rendering | `WindowManager` with `TYPE_APPLICATION_OVERLAY` |
| Scheduling | `AlarmManager` (exact alarms) + coroutine-based internal scheduler |
| Build System | Gradle with Kotlin DSL |
| Min SDK | 33 (Android 13) |
| Target SDK | 35 (Android 15) |

---

## 3. Core App Blocking & Time Limits

### 3.1 Functional Requirements

| ID | Requirement |
|----|------------|
| BLK-01 | The system shall allow a user to set a daily time limit (in minutes) for any installed application. |
| BLK-02 | The system shall allow grouping apps into categories (Social Media, Games, Entertainment) with a shared group time limit. |
| BLK-03 | When a per-app limit is reached, the system shall immediately overlay a Block Screen on top of the target app. |
| BLK-04 | The system shall support scheduled blocking windows defined by start time, end time, and day-of-week bitmask. |
| BLK-05 | Daily usage totals shall reset at a user-configured reset time (default: midnight). |
| BLK-06 | The system shall provide a "Focus Mode" that immediately blocks a set of apps until the user explicitly ends the session or a timer expires. |
| BLK-07 | Usage time for excluded in-app sections (see Section 5) shall not count toward the parent app's daily limit. |

### 3.2 Usage Tracking via `UsageStatsManager`

#### 3.2.1 API Selection Rationale

Android provides three granularities through `UsageStatsManager`:

- `INTERVAL_DAILY` — Pre-aggregated daily stats per app. **Not used** for live tracking; too coarse and delayed.
- `queryUsageStats()` — Returns per-app total foreground time in a time range. Used for session reconstruction after reboot.
- `queryEvents()` — Returns a raw `UsageEvents` stream of `ACTIVITY_RESUMED`, `ACTIVITY_PAUSED`, `ACTIVITY_STOPPED` events. **Primary mechanism** for real-time detection.

#### 3.2.2 Real-Time Tracking Loop

`UsageTrackingEngine` runs inside `AppBlockerService` on a coroutine dispatcher:

```
Algorithm: Real-Time Usage Polling

Every POLL_INTERVAL_MS (default: 10,000ms):
  1. Call UsageStatsManager.queryEvents(lastPollTime, currentTime)
  2. Iterate events:
     a. ACTIVITY_RESUMED(packageName, timestamp):
        - If packageName has an active Rule:
            - Record sessionStart = timestamp
            - Mark app as "foreground"
     b. ACTIVITY_PAUSED or ACTIVITY_STOPPED(packageName, timestamp):
        - If packageName was "foreground":
            - sessionDuration = timestamp - sessionStart
            - Subtract exclusion time (if InAppExclusionTracker reports it)
            - Accumulate to DailyUsageRecord in Room DB
            - Clear foreground state
  3. For the currently-foreground app (derived from latest RESUMED):
     - Compute liveElapsed = now - sessionStart + accumulatedToday
     - If liveElapsed >= dailyLimitMs:
         - Fire LIMIT_REACHED event → Overlay Engine
```

**Critical Android 13+ constraint:** `UsageStatsManager` requires `PACKAGE_USAGE_STATS` permission, which is a **non-dangerous special permission** granted via the "Usage Access" system settings screen. The app must deep-link the user to `Settings.ACTION_USAGE_ACCESS_SETTINGS`. No runtime dialog exists for this permission.

#### 3.2.3 Handling the USM Polling Gap

`UsageStatsManager` events have a minimum delivery delay of approximately 2–5 seconds. For enforcement of time limits that require near-instant response (e.g., a limit that expires exactly at the boundary), the `AccessibilityService` augments the USM data:

- When the A11y service detects a foreground package change (via `TYPE_WINDOW_STATE_CHANGED`), it notifies `UsageTrackingEngine` immediately.
- This provides <500ms latency for package-switch detection, independent of the USM polling interval.

#### 3.2.4 Daily Reset Logic

A `WorkManager` periodic task runs at the user's configured reset time. It:
1. Archives the current day's `DailyUsageRecord` rows to `UsageHistory`.
2. Inserts fresh `DailyUsageRecord` rows with `elapsed = 0` for all tracked apps.
3. Cancels any active Block Screens for apps that were blocked due to limit exhaustion.

### 3.3 Scheduled Blocking

#### 3.3.1 Schedule Data Model

```
Schedule {
  id: UUID
  name: String                  // e.g., "Work Focus", "Bedtime"
  targetApps: List<String>      // package names
  targetCategories: List<UUID>  // category IDs
  startTime: LocalTime
  endTime: LocalTime
  daysOfWeek: Set<DayOfWeek>
  blockType: HARD_BLOCK | SOFT_BLOCK
}
```

`HARD_BLOCK` — App is completely inaccessible. Block Screen is shown immediately on launch.
`SOFT_BLOCK` — App shows a reminder overlay with a 60-second countdown that the user can dismiss once per session.

#### 3.3.2 Schedule Enforcement

Schedules are enforced via two complementary mechanisms:

1. **`AlarmManager` with `setExactAndAllowWhileIdle()`** — Fires at schedule start/end times to activate/deactivate blocking rules in the Room DB. Required because the Foreground Service may be in a low-activity state.

2. **Foreground Service polling** — Every 60 seconds, `AppBlockerService` checks `LocalTime.now()` against all active schedules. This provides a fallback if an alarm fires while the device is rebooting.

**Android 13+ note:** `SCHEDULE_EXACT_ALARM` permission must be declared and the user must grant it via `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`. Applications targeting API 33+ do not hold this by default.

### 3.4 Category-Based Limits

Categories are user-defined groupings stored in the `AppCategory` table. When a category limit is in effect:

1. The `UsageTrackingEngine` maintains a `CategoryUsageAccumulator` that sums elapsed time across all apps in the category.
2. When the category total reaches its limit, ALL apps in the category are blocked, even if individual app limits are not reached.
3. Individual app limits are enforced independently — a category limit can trigger a block even if the individual app limit is not reached, and vice versa.

---

## 4. Anti-Circumvention (Strict Admin Mode)

### 4.1 Threat Model

A user in "Strict Admin Mode" (typically set by themselves when they want to prevent impulsive decisions to bypass limits) shall be protected against the following bypass vectors:

| Threat | Bypass Method |
|--------|--------------|
| T-01 | Navigate to **Settings > Apps > AppBlocker > Force Stop** |
| T-02 | Navigate to **Settings > Apps > AppBlocker > Uninstall** |
| T-03 | Navigate to **Settings > Apps > AppBlocker > Permissions** and revoke Accessibility |
| T-04 | Navigate to **Settings > Accessibility > AppBlocker** and disable the service |
| T-05 | Long-press app icon → "App Info" → Force Stop |
| T-06 | ADB `am force-stop` via USB (addressable via ADB disable, not in scope) |
| T-07 | Booting into Safe Mode (disables third-party services) |

### 4.2 Legacy Approach: `DevicePolicyManager` (Deprecated for This Use Case)

The `DevicePolicyManager` (DPM) Device Administrator API was historically used to prevent uninstallation. A Device Admin app cannot be uninstalled without first being deactivated.

**Why DPM is no longer sufficient on Android 13+:**

1. **Force Stop is NOT blocked by Device Admin status.** A user can still navigate to App Info and Force Stop a Device Admin app.
2. Google Play Store **prohibits** apps from requesting Device Admin solely for the purpose of preventing removal (policy update 2022).
3. Android 13+ restricts `DevicePolicyManager` APIs significantly for non-enterprise (non-MDM) use cases.
4. DPM does not prevent permission revocation for Accessibility or Overlay permissions.

**Conclusion:** DPM Device Admin is NOT a viable primary anti-circumvention mechanism for a consumer app on Android 13+. The architecture must rely on the `AccessibilityService` as the primary defense layer.

### 4.3 Primary Defense: Accessibility-Based Navigation Interception

#### 4.3.1 Mechanism Overview

When Strict Admin Mode is active, `AppBlockerA11yService` continuously monitors all window events. When it detects that the user has navigated to any Settings screen that could be used to disable or uninstall AppBlocker, it executes one of two countermeasures:

- **Back Navigation Injection** — Programmatically presses the Back button to exit the dangerous screen.
- **Full-Screen Overlay** — Draws a `SYSTEM_ALERT_WINDOW` block screen over the Settings page, making the underlying controls non-interactable.

The overlay approach is preferred over back-injection because it is more robust to Settings UI restructuring across OEM skins (Samsung One UI, Xiaomi MIUI, etc.).

#### 4.3.2 Identifying Target Settings Screens

The A11y service listens for `TYPE_WINDOW_STATE_CHANGED` and `TYPE_WINDOW_CONTENT_CHANGED` events globally. When the `packageName` of a window event is `com.android.settings` (or OEM variants: `com.samsung.android.settings`, `com.miui.securitycenter`, etc.), the following detection logic runs:

```
Algorithm: Settings Threat Detection

On AccessibilityEvent received:
  If event.packageName in SETTINGS_PACKAGES:
    rootNode = getRootInActiveWindow()

    Check 1 — "App Info" page for this app:
      Search for node where:
        node.text == our_app_label  AND
        ancestor.viewId contains "app_detail_header"
      OR:
        node.viewIdResourceName == "com.android.settings:id/entity_header_title"
        AND node.text == our_app_label
      → THREAT_APP_INFO_PAGE

    Check 2 — "Force Stop" button visible:
      Search for node where:
        node.viewIdResourceName == "com.android.settings:id/right_button"
        AND node.text contains "Force stop" (locale-aware)
      → THREAT_FORCE_STOP_VISIBLE

    Check 3 — Accessibility settings for this service:
      Search for node where:
        node.text contains our_a11y_service_label
        AND window is com.android.settings/.accessibility.*
      → THREAT_A11Y_SETTINGS

    Check 4 — Uninstall confirmation:
      Search for node where:
        node.viewIdResourceName == "android:id/button1"
        AND ancestor contains node with text matching our_app_label
      → THREAT_UNINSTALL_DIALOG

    If any THREAT detected:
      Execute countermeasure (see 4.3.3)
```

**OEM Compatibility Note:** Samsung One UI, MIUI, and OPPO ColorOS use different package names for their Settings apps and different view ID hierarchies. The detection engine must maintain a `SettingsVariantDatabase` mapping known OEM packages and view ID patterns to normalized threat classes. This database is bundled with the app and updated via app updates.

#### 4.3.3 Countermeasure Execution

```
Algorithm: Countermeasure Execution

On THREAT detected:
  If countermeasure == OVERLAY:
    1. Inflate "Strict Mode Active" overlay layout
    2. Add view via WindowManager with params:
         type = TYPE_APPLICATION_OVERLAY
         flags = FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN
         width = MATCH_PARENT, height = MATCH_PARENT
    3. Overlay displays:
         - "You've enabled Strict Admin Mode"
         - "To disable: [User-configured exit method, e.g., enter PIN]"
         - Countdown timer if "cooling-off period" is configured

  If countermeasure == BACK_NAVIGATION:
    1. performGlobalAction(GLOBAL_ACTION_BACK)
    2. Schedule re-check 500ms later (threats may persist across back presses)
    3. If still on threat screen after 3 back presses: switch to OVERLAY countermeasure
```

#### 4.3.4 Safe Mode Attack (T-07)

Android Safe Mode disables all third-party apps and services. This is an OS-level bypass that cannot be prevented through software alone.

**Mitigation strategy:**
- On every cold start of `AppBlockerService`, check if the device is in Safe Mode via `ActivityManager.isRunningInUserTestHarness()` (note: safe mode detection is indirect; check `PackageManager.FEATURE_CAMERA` presence as a heuristic alongside checking if A11y service is enabled).
- The primary mitigation is **user education**: inform the user at onboarding that Safe Mode bypasses all app-level protections and is an architectural limitation of Android.
- A secondary mitigation: require a delay before Strict Mode can be deactivated (e.g., a 24-hour cooling-off period configured at setup time). This means even if the user reboots into Safe Mode and disables the app, the block re-engages immediately on next normal boot before they can interact with blocked apps.

#### 4.4 Strict Admin Mode — User Exit Flow

To prevent the anti-circumvention system from being a permanent lock-out, a controlled exit mechanism must exist:

| Exit Method | Description |
|------------|-------------|
| **Unlock PIN** | User sets a PIN at Strict Mode activation time. Entering it in the AppBlocker UI deactivates Strict Mode. |
| **Cooling-Off Period** | Even with correct PIN, deactivation is delayed by a configurable period (15 min – 24 hours). |
| **Uninstall Passphrase** | A random 8-word passphrase is shown at activation. The user must screenshot/write it down. Required for factory reset scenarios. |
| **Recovery Contact** | Optionally: send a deactivation code to a trusted email address (opt-in, requires network). |

**Design principle:** The exit mechanism must be harder than impulsive, but not impossible in genuine emergencies. The cooling-off period is the key mechanism.

---

## 5. Granular In-App Interceptions

### 5.1 The Instagram Use Case — Requirements

This is the primary reference implementation for in-app feature blocking. All other in-app rules follow the same technical pattern.

| ID | Requirement |
|----|------------|
| IAB-01 | When the user opens Instagram and navigates to the **Reels tab** (the movie-clapper icon in the bottom navigation), AppBlocker shall immediately draw an overlay blocking interaction with the Reels feed. |
| IAB-02 | When the user opens Instagram and navigates to the **Explore tab** (the magnifying-glass icon), AppBlocker shall block the Explore feed. |
| IAB-03 | When the user is in the **Direct Messages (DMs) section** (the paper-airplane / inbox icon), the overlay shall NOT be shown and the user shall have full access. |
| IAB-04 | Time spent in the DM section shall be **excluded** from the parent Instagram daily time limit. |
| IAB-05 | Viewing a Reel shared **inside a DM conversation** shall NOT be blocked. The block applies only to the standalone Reels tab. |
| IAB-06 | When the user exits Instagram or navigates to the Home tab, all state is reset. |

### 5.2 Technical Approach: AccessibilityService + AccessibilityNodeInfo

#### 5.2.1 Why `AccessibilityService`?

No public Android API allows querying the internal navigation state of a third-party app. `AccessibilityService` is the only mechanism that exposes the live UI node tree of any foreground app to another app with system permission. This is the same API used by screen readers (TalkBack), switch access tools, and password managers.

#### 5.2.2 Enabling the A11y Service

The service is declared in `AndroidManifest.xml`:

```xml
<service
    android:name=".accessibility.AppBlockerA11yService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

`accessibility_service_config.xml`:
```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagRequestFilterKeyEvents"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"
    android:packageNames="com.instagram.android,com.android.settings,..." />
```

**Critical configuration:** `android:packageNames` restricts the A11y service to only receive events from explicitly listed packages. This is a **hard privacy boundary** — the service never receives events from unlisted apps. This is also a key declaration for Play Store compliance.

`flagReportViewIds` causes Android to populate `AccessibilityNodeInfo.getViewIdResourceName()` with the string resource IDs set by the target app's developers (e.g., `com.instagram.android:id/tab_icon`). Without this flag, view IDs are not available.

#### 5.2.3 The Node Tree Inspection Algorithm

```
Algorithm: Instagram Screen State Detection

Event Received: TYPE_WINDOW_STATE_CHANGED or TYPE_WINDOW_CONTENT_CHANGED
  packageName == "com.instagram.android"

Step 1 — Obtain root node:
  rootNode = service.getRootInActiveWindow()
  If rootNode == null: return (window not ready)

Step 2 — Locate the bottom navigation bar:
  bottomNav = rootNode.findAccessibilityNodeInfosByViewId(
      "com.instagram.android:id/tab_bar"    // or current equivalent
  ).firstOrNull()

  If bottomNav == null:
    // Fallback: search by content description heuristic
    bottomNav = findNodeByContentDesc(rootNode, ".*navigation.*", regex=true)

Step 3 — Identify active tab:
  For each child in bottomNav.children:
    If child.isSelected == true OR child.isChecked == true:
      activeTabDesc = child.contentDescription   // e.g., "Reels", "Messages", "Home"
      activeTabViewId = child.viewIdResourceName // e.g., "com.instagram.android:id/clips_tab"

Step 4 — Map to internal screen state:
  screenState = when {
    activeTabDesc matches REELS_DESCRIPTORS    → SCREEN_REELS
    activeTabDesc matches EXPLORE_DESCRIPTORS  → SCREEN_EXPLORE
    activeTabDesc matches DM_DESCRIPTORS       → SCREEN_DM
    activeTabDesc matches HOME_DESCRIPTORS     → SCREEN_HOME
    else                                       → SCREEN_UNKNOWN
  }

Step 5 — Check for "Reel in DM" exception:
  If screenState == SCREEN_DM:
    // Check if user opened a reel from within a DM thread
    reelPlayer = rootNode.findAccessibilityNodeInfosByViewId(
        "com.instagram.android:id/clips_viewer_video_container"
    ).firstOrNull()
    If reelPlayer != null AND reelPlayer.isVisibleToUser:
      screenState = SCREEN_DM_REEL_EXCEPTION  // Do not block

Step 6 — Apply rule:
  previousState = InAppStateTracker.get("com.instagram.android")
  InAppStateTracker.set("com.instagram.android", screenState)

  If previousState != screenState:
    OnScreenStateChanged(screenState)
```

#### 5.2.4 Identifier Stability and Fallback Strategy

Instagram (and all apps) may change their internal view IDs in any update. The detection system must be resilient:

**Tier 1 — View ID (most reliable when available):**
```
findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_tab")
```

**Tier 2 — Content Description (moderately stable):**
```
nodes where node.contentDescription == "Reels" (locale-sensitive)
```

**Tier 3 — Text label (least stable, locale-dependent):**
```
nodes where node.text == "Reels"
```

**Tier 4 — Position heuristic (last resort):**
```
If bottom nav has 5 children, the 3rd child (index 2) is historically the Reels tab.
```

The detection engine evaluates these tiers in order and uses the first successful match. Rule definitions in the Room DB store identifier configurations across all tiers so updates can be pushed via app updates without OS-level changes.

#### 5.2.5 Resource ID Mapping Database

```sql
CREATE TABLE InAppNodeSignature (
    id TEXT PRIMARY KEY,
    targetPackage TEXT NOT NULL,
    screenState TEXT NOT NULL,         -- "SCREEN_REELS", "SCREEN_DM", etc.
    signatureTier INTEGER NOT NULL,    -- 1=viewId, 2=contentDesc, 3=text, 4=position
    signatureType TEXT NOT NULL,       -- "VIEW_ID", "CONTENT_DESC", "TEXT", "CHILD_INDEX"
    signatureValue TEXT NOT NULL,      -- The actual string or index to match
    minAppVersionCode INTEGER,
    maxAppVersionCode INTEGER,         -- null = no upper bound
    isRegex INTEGER DEFAULT 0          -- 1 = treat signatureValue as regex
);
```

This table is bundled with the app and updated via `AppSignatureUpdateWorker` (a `WorkManager` task) that fetches updated signatures from AppBlocker's own server. **Only signatures are fetched from the network — no screen content is ever transmitted.**

#### 5.2.6 Drawing the Feature Block Overlay

When `OnScreenStateChanged` fires with a blocked state (SCREEN_REELS or SCREEN_EXPLORE):

```
Algorithm: Feature Overlay Activation

1. If overlay already shown: update text only, return.

2. Prepare WindowManager.LayoutParams:
   type    = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
   flags   = FLAG_NOT_FOCUSABLE            // allows taps to reach our overlay
            | FLAG_LAYOUT_IN_SCREEN
            | FLAG_LAYOUT_INSET_DECOR
   width   = MATCH_PARENT
   height  = MATCH_PARENT
   format  = PixelFormat.TRANSLUCENT
   gravity = Gravity.TOP

3. Inflate overlay view (BlockFeatureOverlayView):
   - Semi-transparent dark background (alpha 0.92)
   - AppBlocker icon + "Reels Blocked" heading
   - Rule description: "You've blocked Instagram Reels. DMs are still accessible."
   - Optionally: remaining daily time for Instagram (if time limit set)
   - Button: "Open DMs instead →" (performGlobalAction(BACK) then navigate)

4. windowManager.addView(overlayView, params)

5. Register for touch events on overlay (to prevent bleed-through to Instagram)
```

When `OnScreenStateChanged` fires with an allowed state (SCREEN_DM):

```
1. If overlay is shown: windowManager.removeView(overlayView)
2. Stop accruing time against Instagram's daily limit
3. Begin accruing time to DM-exclusion accumulator (not counted toward limit)
```

#### 5.2.7 Exclusion Time Tracking (DM Time)

`InAppExclusionTracker` maintains a per-app map of excluded section durations:

```
InAppExclusionTracker {
  // Called when user enters an excluded section
  fun onExcludedSectionEntered(pkg: String, section: String):
    exclusionStartTimes[pkg to section] = SystemClock.elapsedRealtime()

  // Called when user leaves the excluded section
  fun onExcludedSectionExited(pkg: String, section: String): Long:
    duration = elapsedRealtime - exclusionStartTimes[pkg to section]
    totalExcludedTime[pkg] += duration
    return duration

  // Called by UsageTrackingEngine when computing elapsed time
  fun getTotalExcludedTime(pkg: String): Long:
    return totalExcludedTime[pkg] ?: 0
}
```

`UsageTrackingEngine` subtracts this value when accumulating session time:

```
netSessionTime = rawSessionTime - InAppExclusionTracker.getTotalExcludedTime(pkg)
accumulatedToday[pkg] += max(0, netSessionTime)
```

### 5.3 Generalizing In-App Rules

The Instagram example is an instance of the general `InAppRule` system:

```
InAppRule {
  id: UUID
  parentPackage: String           // "com.instagram.android"
  ruleName: String                // "Block Reels & Explore"

  blockedScreenStates: List<String>    // ["SCREEN_REELS", "SCREEN_EXPLORE"]
  allowedScreenStates: List<String>    // ["SCREEN_DM", "SCREEN_HOME"]
  excludedFromTimerStates: List<String> // ["SCREEN_DM"]

  detectionSignatures: List<InAppNodeSignature>

  overlayConfig: OverlayConfig {
    title: String
    body: String
    showTimeRemaining: Boolean
    allowedSectionShortcut: String?   // package + intent to open DMs directly
  }
}
```

This model supports any app where UI sections can be distinguished via A11y node inspection, including YouTube (block Shorts), TikTok (complete block), Twitter/X (block For You feed, allow Following), etc.

---

## 6. Security, Privacy & Compliance

### 6.1 Privacy Architecture Principles

**Principle 1 — Local-Only Processing**
All `AccessibilityNodeInfo` evaluation is performed exclusively in-process on the device. Node trees are constructed in memory, evaluated, and immediately discarded. No UI content — text, descriptions, app state — is ever serialized to disk, logged, or transmitted over any network interface.

**Principle 2 — Minimum Necessary Access**
The Accessibility service configuration explicitly restricts event receipt to listed packages via `android:packageNames`. This is a hard OS-enforced boundary. Events from banking apps, messaging apps, and all other packages are invisible to the service.

**Principle 3 — No Persistent Node Storage**
`AccessibilityNodeInfo` objects are ephemeral. After evaluation, they are explicitly recycled (pre-API 33: `node.recycle()`; API 33+: GC-managed). No snapshots, logs, or analytics of node content are retained.

**Principle 4 — Transparency**
The app's onboarding flow presents a clear, plain-language explanation of what the Accessibility service reads and why, before the user is directed to grant access. This explanation is also included in the Play Store listing.

### 6.2 Data Minimization

| Data Type | Collected | Retained | Transmitted |
|-----------|-----------|----------|-------------|
| App usage times (minutes per day) | Yes | 90 days, local Room DB | Never |
| Which app is foreground | Yes (for enforcement) | Only aggregate daily totals | Never |
| UI node content (text, descriptions) | Yes (evaluated in-memory only) | No | Never |
| User-defined rules | Yes | Indefinitely, local only | Never |
| App signature updates | Only signature patterns | Bundled in app/updates | Fetched from server (one-way) |
| Crash reports | Yes (if user opts in) | Per crash report policy | Only if opted in |

### 6.3 Google Play Store Policy Compliance

#### 6.3.1 Accessibility API Policy (as of 2024/2025)

Google's policy prohibits Accessibility APIs from being used to:
- Collect personal data for advertising purposes
- Transmit any data to third parties
- Enable functionality beyond what is declared in the Play listing

**Compliance measures:**

1. **Prominent Disclosure (Required):** The Play Store listing must include a clear disclosure that the app uses `AccessibilityService` for the sole purpose of monitoring and blocking app usage locally on-device. This disclosure appears in the app description's first paragraph.

2. **Service Declaration:** `accessibility_service_config.xml` specifies `android:packageNames` (restricting scope) and `android:description` pointing to a string resource that explains the service purpose in plain language. Android displays this description to the user on the Accessibility settings page.

3. **No Data Exfiltration:** All node inspection code is strictly local. Network access is restricted to: (a) fetching app signature updates (node IDs, no content), and (b) optional anonymous crash reporting. Network calls are auditable via the app's Privacy Policy.

4. **Minimize `canRetrieveWindowContent`:** This flag is set to `true` because the feature fundamentally requires reading window content. However, the `packageNames` restriction ensures this capability is scoped only to listed apps.

#### 6.3.2 `SYSTEM_ALERT_WINDOW` Policy

`SYSTEM_ALERT_WINDOW` (Draw over other apps) is a restricted permission. On Android 13+, it requires the user to explicitly grant it via `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`. The app must:

1. Explain why the overlay permission is needed before directing the user to grant it.
2. Only draw overlays when a blocking rule is actively triggered.
3. Not draw overlays at arbitrary times or for purposes beyond blocking.

#### 6.3.3 Background Usage Access

`PACKAGE_USAGE_STATS` requires `ACTION_USAGE_ACCESS_SETTINGS`. Rationale must be clearly explained to the user before requesting.

### 6.4 Encryption at Rest

While screen content is never stored, the Room database does store user-defined rules and aggregate usage statistics. These are stored using:

- **SQLCipher** for Room encryption (if the user enables "Private Mode" in settings).
- Default mode: standard Room (unencrypted), relying on Android's per-app sandbox for isolation.

### 6.5 Threat: Accessibility Service Revocation by Another App

If another malicious app attempts to read our A11y service state or manipulate it, Android's permission model prevents this — only the Settings app and the system can enable/disable Accessibility services. Our anti-circumvention layer prevents the *user* from navigating to the Settings page to do so manually.

---

## 7. Data Architecture & Storage

### 7.1 Room Database Schema

```sql
-- Installed apps that have rules configured
CREATE TABLE TrackedApp (
    packageName TEXT PRIMARY KEY,
    appLabel TEXT NOT NULL,
    appIconUri TEXT,
    categoryId TEXT,                    -- FK to AppCategory
    dailyLimitMs INTEGER,               -- NULL = no limit
    isStrictlyBlocked INTEGER DEFAULT 0,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
);

-- User-defined app categories
CREATE TABLE AppCategory (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    dailyLimitMs INTEGER,
    colorHex TEXT
);

-- Scheduled blocking windows
CREATE TABLE BlockSchedule (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    blockType TEXT NOT NULL,            -- "HARD" | "SOFT"
    startTimeMinuteOfDay INTEGER NOT NULL,
    endTimeMinuteOfDay INTEGER NOT NULL,
    daysOfWeekBitmask INTEGER NOT NULL,  -- bit 0=Sun, 1=Mon, ..., 6=Sat
    isActive INTEGER DEFAULT 1,
    createdAt INTEGER NOT NULL
);

-- Mapping: which apps are in which schedule
CREATE TABLE ScheduleAppMapping (
    scheduleId TEXT NOT NULL,
    packageName TEXT NOT NULL,
    PRIMARY KEY (scheduleId, packageName)
);

-- Per-app daily usage (current day)
CREATE TABLE DailyUsageRecord (
    packageName TEXT NOT NULL,
    dateEpochDay INTEGER NOT NULL,      -- LocalDate.toEpochDay()
    elapsedMs INTEGER DEFAULT 0,
    excludedMs INTEGER DEFAULT 0,       -- time in excluded sections
    PRIMARY KEY (packageName, dateEpochDay)
);

-- Historical usage (past days, for stats UI)
CREATE TABLE UsageHistory (
    packageName TEXT NOT NULL,
    dateEpochDay INTEGER NOT NULL,
    elapsedMs INTEGER NOT NULL,
    excludedMs INTEGER NOT NULL,
    PRIMARY KEY (packageName, dateEpochDay)
);

-- In-app rules (granular feature blocking)
CREATE TABLE InAppRule (
    id TEXT PRIMARY KEY,
    parentPackage TEXT NOT NULL,
    ruleName TEXT NOT NULL,
    isEnabled INTEGER DEFAULT 1,
    blockedScreenStates TEXT NOT NULL,  -- JSON array of state strings
    allowedScreenStates TEXT NOT NULL,  -- JSON array
    excludedFromTimerStates TEXT NOT NULL,
    overlayTitle TEXT,
    overlayBody TEXT,
    createdAt INTEGER NOT NULL
);

-- Node detection signatures for in-app rules
CREATE TABLE InAppNodeSignature (
    id TEXT PRIMARY KEY,
    ruleId TEXT NOT NULL,               -- FK to InAppRule
    screenState TEXT NOT NULL,
    signatureTier INTEGER NOT NULL,
    signatureType TEXT NOT NULL,
    signatureValue TEXT NOT NULL,
    minAppVersionCode INTEGER,
    maxAppVersionCode INTEGER,
    isRegex INTEGER DEFAULT 0
);

-- Strict Admin Mode configuration
CREATE TABLE StrictAdminConfig (
    id INTEGER PRIMARY KEY DEFAULT 1,   -- Singleton row
    isEnabled INTEGER DEFAULT 0,
    unlockPinHash TEXT,                 -- bcrypt hash of PIN
    coolOffPeriodMs INTEGER DEFAULT 0,
    activatedAt INTEGER,
    scheduledDeactivationAt INTEGER     -- NULL if cooling off not active
);
```

### 7.2 Repository Layer

Each table has a corresponding `Repository` class that exposes `Flow<T>` for reactive UI updates and suspend functions for write operations. All database operations run on `Dispatchers.IO`.

### 7.3 Data Retention Policy

- `DailyUsageRecord`: Rolling current-day data only. Migrated to `UsageHistory` at daily reset.
- `UsageHistory`: Retained for 90 days. A `WorkManager` weekly cleanup task deletes rows older than 90 days.
- `InAppNodeSignature`: Retained indefinitely; updated by app updates and signature sync.
- `StrictAdminConfig`: Retained until user explicitly disables Strict Mode via correct PIN.

---

## 8. Background Services & OS Interactions

### 8.1 `AppBlockerService` — Foreground Service

#### 8.1.1 Service Declaration

```xml
<service
    android:name=".service.AppBlockerService"
    android:foregroundServiceType="dataSync"
    android:exported="false"
    android:process=":blocker" />
```

`android:process=":blocker"` runs the service in a separate process. This means even if the main app UI process is killed (by the OS, or by the user via recent apps), the blocking service survives.

**Android 14+ note:** `foregroundServiceType` is mandatory on API 34+. `dataSync` is appropriate as the service monitors app usage data. On API 34+, if the service type is `dataSync`, it cannot run indefinitely — it has a 6-hour timeout per `JobScheduler` slot. To work around this, the service must be restarted every ~5 hours via a `PendingIntent` alarm.

#### 8.1.2 Persistent Notification

The foreground service notification must be permanent and non-dismissible (Android OS requirement for foreground services). The notification:

- Uses a dedicated `NotificationChannel` with `IMPORTANCE_LOW` (no sound, minimal visual footprint).
- Shows current blocking status: "Blocking active: 3 apps limited" or "Focus Mode: 47 min remaining."
- Provides a quick action: "Pause for 5 min" (requires PIN confirmation in Strict Mode).

#### 8.1.3 Battery Optimization Exemption

Foreground services are partially exempt from Doze mode, but Android 13+ introduces aggressive "Phantom Process" killing and battery optimization that can still interrupt the service on some OEMs. The recommended strategy:

1. On first launch, direct the user to **Settings > Battery > AppBlocker > Unrestricted** via `Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)`.
2. On OEM devices (Samsung, Xiaomi, OnePlus), deep-link to their proprietary battery settings where applicable.
3. Implement a `BroadcastReceiver` for `ACTION_MY_PACKAGE_REPLACED` and `BOOT_COMPLETED` to restart the service if it was killed.

#### 8.1.4 Service Restart on Kill

```xml
<receiver android:name=".receiver.BootReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED"/>
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED"/>
    </intent-filter>
</receiver>
```

The service is also started with `START_STICKY`, which causes Android to restart it automatically after it is killed, passing `null` intent. On restart, the service re-initializes its state from Room DB.

### 8.2 `AppBlockerA11yService` — Accessibility Service

The Accessibility Service is managed by the Android OS, not the app. The OS starts and stops it based on the Accessibility settings toggle. Unlike a regular service, it cannot be restarted programmatically by the app.

**Implication:** The A11y service stopping is the primary bypass vector in Strict Admin Mode. The anti-circumvention layer (Section 4) specifically targets the Settings screen used to disable it.

**Communication with `AppBlockerService`:** Since they run in different process contexts, IPC is done via:
- **Bound Service pattern:** `AppBlockerA11yService` binds to `AppBlockerService` via AIDL or a `Messenger`.
- **SharedPreferences** (with `MODE_MULTI_PROCESS` or via `ContentProvider`): For lightweight state signals.
- **Room DB** (via the `:blocker` process): Both components share the same Room database instance within the `:blocker` process.

### 8.3 `AlarmManager` for Scheduling

```kotlin
// Setting an exact alarm for schedule activation
val alarmManager = context.getSystemService(AlarmManager::class.java)
val intent = PendingIntent.getBroadcast(
    context, scheduleId.hashCode(),
    Intent(context, ScheduleAlarmReceiver::class.java).apply {
        putExtra("schedule_id", scheduleId.toString())
        putExtra("action", "ACTIVATE")
    },
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
// Android 12+: requires SCHEDULE_EXACT_ALARM or USE_EXACT_ALARM permission
alarmManager.setExactAndAllowWhileIdle(
    AlarmManager.RTC_WAKEUP,
    triggerAtMillis,
    intent
)
```

### 8.4 `WorkManager` — Periodic Tasks

| Worker | Period | Task |
|--------|--------|------|
| `DailyResetWorker` | Daily at configured reset time | Archive usage, reset counters |
| `UsageHistoryCleanupWorker` | Weekly | Delete records older than 90 days |
| `SignatureUpdateWorker` | Every 6 hours | Fetch updated app signatures (node IDs) |
| `ServiceWatchdogWorker` | Every 15 min | Ensure `AppBlockerService` is running; restart if not |

---

## 9. UI/UX Design Specification

### 9.1 Design Principles

Given the technical complexity of the app's capabilities, the UI must abstract complexity through progressive disclosure. Users should be able to set a simple time limit in under 30 seconds, while power users can configure granular in-app rules through a deeper configuration flow.

### 9.2 Screen Map

```
AppBlocker App
├── Dashboard (Home)
│   ├── Today's usage summary (top apps)
│   ├── Active blocks/Focus mode status
│   └── Quick-add block card
│
├── App Library
│   ├── Installed apps list (with daily usage)
│   ├── Per-app configuration screen
│   │   ├── Daily time limit picker
│   │   ├── Schedule selector
│   │   ├── In-app rules (if available for this app)
│   │   └── Strict block toggle
│   └── Category management
│
├── Schedules
│   ├── Schedule list (Focus modes, Bedtime, etc.)
│   └── Schedule editor
│       ├── Time range picker
│       ├── Day-of-week selector
│       ├── App/Category selector
│       └── Block type toggle (Hard / Soft)
│
├── In-App Rules
│   ├── Available templates (Instagram, YouTube, TikTok...)
│   ├── Template detail/activation screen
│   └── Custom rule builder (advanced)
│
├── Strict Admin Mode
│   ├── Explanation + warnings
│   ├── Activation flow (set PIN, cooling-off period, passphrase backup)
│   └── Status / Deactivation flow
│
└── Settings
    ├── Daily reset time
    ├── Permission status dashboard
    ├── Data & privacy controls
    └── About / Support
```

### 9.3 In-App Rule Configuration Flow (Key UX Challenge)

The most complex UX challenge is guiding users through configuring in-app rules without overwhelming them. The solution is a **template-first approach:**

```
Flow: Configuring Instagram Reels Blocking

Step 1 — "In-App Rules" screen:
  "We have pre-configured rules for popular apps."
  → User sees card: "Instagram — Reels & Explore Blocker"
  → Tap card to expand details

Step 2 — Template Detail screen:
  Shows visual diagram of Instagram tabs with
  RED indicators on Reels and Explore
  GREEN indicators on DMs and Home
  "Time in DMs is excluded from your Instagram limit."
  → [Enable Rule] button

Step 3 — Optional: Customize overlay text
  "What message should appear when Reels are blocked?"
  Default: "Reels blocked by AppBlocker."

Step 4 — Confirmation:
  "Instagram Reels blocking is now active."
  Summary card on Dashboard updated.
```

For custom in-app rules (advanced users), a "Node Inspector" mode allows the user to open the target app while a floating bubble from AppBlocker overlays it. Tapping elements in the target app surfaces their A11y properties in the bubble, allowing the user to build custom detection signatures without writing code.

### 9.4 Permissions Onboarding Flow

Permissions required in order of request:

```
1. Notification Permission (Android 13+: POST_NOTIFICATIONS)
   → "We need to show a persistent notification to keep blocking active."

2. Usage Access (PACKAGE_USAGE_STATS)
   → "We need Usage Access to measure time spent in apps."
   → [Open Settings] → ACTION_USAGE_ACCESS_SETTINGS

3. Overlay Permission (SYSTEM_ALERT_WINDOW)
   → "We need 'Draw over other apps' to show the block screen."
   → [Open Settings] → ACTION_MANAGE_OVERLAY_PERMISSION

4. Accessibility Service
   → Full-screen explanation of what A11y reads and why.
   → "We read which tab you're on in Instagram to block Reels."
   → "We never read your messages or personal content."
   → [Open Settings] → ACTION_ACCESSIBILITY_SETTINGS
   → Highlight AppBlocker in the list

5. (Strict Mode only) Battery Optimization Exemption
   → "To prevent bypass through battery killers, allow unrestricted background usage."
   → [Open Settings]

6. (Android 12+) Exact Alarms
   → "For precise schedule enforcement, allow exact alarms."
   → [Open Settings] → ACTION_REQUEST_SCHEDULE_EXACT_ALARM
```

Each step includes a "Why we need this" expandable section and a clear skip option (for non-essential permissions). Progress is tracked in Room DB and the user can return to complete permissions later.

---

## 10. Permissions Manifest

### 10.1 Required Permissions Summary

```xml
<!-- Usage stats tracking -->
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />

<!-- Draw block screen overlays -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- Persistent foreground service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<!-- Notifications (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Exact alarms for schedules (Android 12+) -->
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />

<!-- Wake lock to ensure service runs during Doze transitions -->
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- Boot-completed receiver for service restart -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Battery optimization exemption request -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<!-- Network access for signature updates only -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 10.2 Permissions NOT Requested

The following permissions are deliberately excluded to minimize attack surface and demonstrate good faith to the Play Store review process:

- `READ_CONTACTS` — not needed
- `READ_SMS` — not needed
- `CAMERA` / `RECORD_AUDIO` — not needed
- `ACCESS_FINE_LOCATION` — not needed
- `READ_CALL_LOG` — not needed

---

## 11. Known Limitations & Risk Register

### 11.1 Technical Limitations

| ID | Limitation | Impact | Mitigation |
|----|-----------|--------|------------|
| LIM-01 | A11y service cannot be restarted programmatically if user disables it | Anti-circumvention gap if user reaches Accessibility settings | Anti-circumvention intercepts the path to Accessibility settings |
| LIM-02 | Safe Mode disables all third-party services | Complete bypass possible via Safe Mode | User education; cooling-off period delays effectiveness of bypass |
| LIM-03 | Instagram app updates may change view IDs | In-app rules may stop working until signatures are updated | Tier fallback system; server-side signature updates via WorkManager |
| LIM-04 | Foreground service on API 34+ has 6-hour `dataSync` limit | Service may be stopped by OS after 6 hours | Auto-restart via `AlarmManager` every 5.5 hours |
| LIM-05 | `UsageStatsManager` has ~2-5s reporting lag | Brief window where a blocked app remains accessible | A11y `TYPE_WINDOW_STATE_CHANGED` provides <500ms secondary detection |
| LIM-06 | OEM skins (MIUI, One UI) customize Settings app UI | Anti-circumvention detection may miss OEM-specific screens | OEM variant database maintained and updated with app releases |
| LIM-07 | ADB `am force-stop` bypasses all protections | Technical users with USB debugging enabled can bypass | Inform user; recommend disabling USB debugging in Strict Mode |
| LIM-08 | Child device with parent Google account — restrictions apply | Device with Family Link cannot grant certain permissions | Not supported; document as known limitation |

### 11.2 Play Store Policy Risks

| ID | Risk | Probability | Mitigation |
|----|------|-------------|------------|
| PSR-01 | App rejected for Accessibility API misuse | Medium | Strict `packageNames` restriction; clear disclosure in listing |
| PSR-02 | App rejected for "monitoring" use without explicit user consent | Low | All monitoring is self-monitoring (user monitors their own usage) |
| PSR-03 | Anti-circumvention seen as "deceptive behavior" | Low-Medium | User activates Strict Mode voluntarily; clear opt-in flow with explicit warnings |
| PSR-04 | Policy change invalidating the technical approach | Medium (long-term) | Monitor Google Play policy updates; maintain close reading of [policy changelog](https://support.google.com/googleplay/android-developer/answer/9888170) |

---

## 12. Testing Strategy

### 12.1 Unit Testing

- `UsageTrackingEngine`: Mock `UsageStatsManager` responses; verify accumulation logic, exclusion subtraction, limit detection.
- `InAppStateDetector`: Construct mock `AccessibilityNodeInfo` trees; verify screen state detection for all Instagram tab configurations.
- `SettingsThreatDetector`: Construct mock Settings node trees for AOSP and known OEM skins; verify threat detection accuracy.
- `ScheduleEvaluator`: Test against boundary conditions (midnight crossover, overlapping schedules).

### 12.2 Integration Testing

- `AppBlockerService` lifecycle: Verify service survives process death and restarts correctly.
- Room DB: Verify DAO operations, daily reset migration, and data integrity.
- Overlay rendering: Instrumented tests confirming overlay appears/disappears correctly on screen state changes.

### 12.3 Manual / Exploratory Testing

| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| Instagram Reels Block | Open Instagram, tap Reels tab | Block overlay appears |
| Instagram DM Exception | Open Instagram, tap DMs | No overlay; DMs accessible |
| Reel in DM Exception | Open Instagram DM, open a shared Reel | No overlay |
| Daily Limit Enforcement | Set 1-min limit, use app for 1 min | Block screen appears |
| Force Stop Prevention | Enable Strict Mode, navigate to App Info | Block overlay over Settings |
| Accessibility Disable Prevention | Enable Strict Mode, navigate to Accessibility Settings | Block overlay over Settings |
| Schedule Enforcement | Set schedule starting in 1 min, wait | App blocked at start time |
| Service Restart | Kill `:blocker` process via ADB; open blocked app | Block reactivates within 15 seconds |
| OEM Compatibility | Run on Samsung One UI device; attempt App Info | Threat detected and blocked |

### 12.4 Device Test Matrix

Testing must cover at minimum:
- **Stock Android:** Pixel 7+ running Android 13, 14, 15
- **Samsung One UI 6.x:** Galaxy S23 / S24 series
- **Xiaomi MIUI 14 / HyperOS:** Redmi Note series
- **OnePlus OxygenOS 14:** OnePlus 12

---

*End of Document*

---

**Document Control**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-03-30 | Engineering Team | Initial draft |
