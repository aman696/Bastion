# Foundation Module Design

**Date:** 2026-03-30
**Project:** Bastion (com.aman.bastion)
**Module:** Foundation — Project Setup, Dependency Wiring, Package Structure, Room Data Layer
**Status:** Approved

---

## 1. Scope

This spec covers everything required before any feature logic is written:

- Removing Android Studio boilerplate
- Renaming application ID to `com.aman.bastion`
- Wiring all project dependencies via `libs.versions.toml` + `build.gradle.kts`
- Establishing the Option C layered+feature package structure
- Implementing all Room entities, DAOs, TypeConverters, and `AppDatabase`
- Providing all DAOs via a Hilt `DatabaseModule`
- Setting up `BastionApp` (`@HiltAndroidApp`) and a clean `MainActivity` (Compose host only)

No feature logic, no UI screens, no services are in scope for this module.

---

## 2. Project Setup

### 2.1 Application ID & Name

| Field | Value |
|---|---|
| applicationId | `com.aman.bastion` |
| App name (strings.xml) | `Bastion` |
| minSdk | 33 |
| targetSdk | 35 |
| compileSdk | 35 |

### 2.2 Boilerplate Removal

Delete entirely:
- `FirstFragment.kt`, `SecondFragment.kt`
- `res/layout/activity_main.xml`, `res/layout/content_main.xml`
- `res/layout/fragment_first.xml`, `res/layout/fragment_second.xml`
- `res/menu/menu_main.xml`
- `res/navigation/nav_graph.xml`
- `res/values/dimens.xml` and all dimens variants

Replace:
- `MainActivity.kt` → bare Compose host with `setContent { BastionTheme { } }`

### 2.3 Dependencies

All versions pinned in `gradle/libs.versions.toml`. No `+` wildcards.

| Alias | Library | Purpose |
|---|---|---|
| `compose-bom` | `androidx.compose:compose-bom` | Compose version alignment |
| `compose-ui` | `androidx.compose.ui:ui` | Compose core |
| `compose-material3` | `androidx.compose.material3:material3` | Material Design 3 |
| `compose-ui-tooling` | `androidx.compose.ui:ui-tooling-preview` | Preview support |
| `activity-compose` | `androidx.activity:activity-compose` | `ComponentActivity.setContent` |
| `lifecycle-viewmodel-compose` | `androidx.lifecycle:lifecycle-viewmodel-compose` | ViewModel in Compose |
| `lifecycle-runtime-compose` | `androidx.lifecycle:lifecycle-runtime-compose` | `collectAsStateWithLifecycle` |
| `navigation-compose` | `androidx.navigation:navigation-compose` | Screen routing |
| `hilt-android` | `com.google.dagger:hilt-android` | DI framework |
| `hilt-compiler` | `com.google.dagger:hilt-android-compiler` | Hilt annotation processor (kapt) |
| `hilt-navigation-compose` | `androidx.hilt:hilt-navigation-compose` | `hiltViewModel()` in Compose |
| `room-runtime` | `androidx.room:room-runtime` | Room core |
| `room-ktx` | `androidx.room:room-ktx` | Coroutines + Flow support |
| `room-compiler` | `androidx.room:room-compiler` | Room annotation processor (kapt) |
| `work-runtime-ktx` | `androidx.work:work-runtime-ktx` | WorkManager with coroutines |
| `hilt-work` | `androidx.hilt:hilt-work` | Hilt injection into Workers |
| `hilt-work-compiler` | `androidx.hilt:hilt-compiler` | Hilt-Work annotation processor |
| `security-crypto` | `androidx.security:security-crypto` | EncryptedSharedPreferences |
| `coroutines-android` | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | Coroutines on Android |
| `kotlinx-serialization-json` | `org.jetbrains.kotlinx:kotlinx-serialization-json` | JSON TypeConverters for Room |

Plugins to apply:
- `com.android.application`
- `org.jetbrains.kotlin.android`
- `com.google.dagger.hilt.android`
- `org.jetbrains.kotlin.kapt`
- `org.jetbrains.kotlin.plugin.serialization`

---

## 3. Package Structure

```
com.aman.bastion/
├── BastionApp.kt
├── MainActivity.kt
│
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt
│   │   └── Converters.kt
│   ├── blocking/
│   │   ├── entity/
│   │   │   ├── AppRuleEntity.kt
│   │   │   └── AppCategoryEntity.kt
│   │   └── dao/
│   │       ├── AppRuleDao.kt
│   │       └── AppCategoryDao.kt
│   ├── scheduling/
│   │   ├── entity/
│   │   │   └── ScheduleEntity.kt
│   │   └── dao/
│   │       └── ScheduleDao.kt
│   ├── usage/
│   │   ├── entity/
│   │   │   ├── DailyUsageRecordEntity.kt
│   │   │   └── UsageHistoryEntity.kt
│   │   └── dao/
│   │       ├── DailyUsageRecordDao.kt
│   │       └── UsageHistoryDao.kt
│   └── inapp/
│       ├── entity/
│       │   └── InAppRuleEntity.kt
│       └── dao/
│           └── InAppRuleDao.kt
│
├── domain/
│   ├── model/
│   │   ├── AppRule.kt
│   │   ├── AppCategory.kt
│   │   ├── Schedule.kt
│   │   ├── DailyUsageRecord.kt
│   │   └── InAppRule.kt
│   └── repository/
│       ├── AppRuleRepository.kt
│       ├── ScheduleRepository.kt
│       └── UsageRepository.kt
│
├── service/
├── ui/
└── di/
    └── DatabaseModule.kt
```

---

## 4. Room Data Layer

### 4.1 TypeConverters (`Converters.kt`)

Handles non-primitive types that Room cannot store natively:

| Kotlin Type | Stored As | Converter |
|---|---|---|
| `List<String>` | `String` (JSON array) | `kotlinx.serialization` |
| `BlockType` (enum) | `String` | enum name |

### 4.2 Entities

#### `AppRuleEntity`
```
@Entity(tableName = "app_rules")
packageName: String       @PrimaryKey — app package identifier
dailyLimitMs: Long        — 0 means no time limit (hard block only)
isHardBlocked: Boolean    — if true, blocked regardless of time
categoryId: String?       — nullable FK to app_categories
createdAt: Long           — epoch millis
```

#### `AppCategoryEntity`
```
@Entity(tableName = "app_categories")
id: String                @PrimaryKey — UUID
name: String              — user-defined label e.g. "Social Media"
dailyLimitMs: Long        — shared limit across all apps in this category
colorHex: String          — hex color string for UI e.g. "#FF5733"
```

#### `ScheduleEntity`
```
@Entity(tableName = "schedules")
id: String                @PrimaryKey — UUID
name: String              — e.g. "Work Focus", "Bedtime"
targetPackages: List<String>      — TypeConverter, JSON
targetCategoryIds: List<String>   — TypeConverter, JSON
startTimeMinutes: Int     — minutes since midnight (0–1439)
endTimeMinutes: Int       — minutes since midnight (0–1439)
daysOfWeekBitmask: Int    — bit 0=Mon, bit 1=Tue … bit 6=Sun
blockType: String         — "HARD" or "SOFT"
isActive: Boolean         — user can pause a schedule without deleting
```

#### `DailyUsageRecordEntity`
```
@Entity(tableName = "daily_usage", primaryKeys = ["packageName", "date"])
packageName: String       — composite PK part 1
date: String              — ISO-8601 date "2026-03-30", composite PK part 2
elapsedMs: Long           — total foreground time today
exclusionMs: Long         — time in allowed in-app sections, subtracted from limit
```

#### `UsageHistoryEntity`
```
@Entity(tableName = "usage_history")
id: Long                  @PrimaryKey(autoGenerate = true)
packageName: String
date: String              — ISO-8601 date
elapsedMs: Long           — final elapsed time for that day (archived)
```

#### `InAppRuleEntity`
```
@Entity(tableName = "inapp_rules")
id: String                @PrimaryKey — UUID
packageName: String       — parent app e.g. "com.instagram.android"
featureId: String         — internal key e.g. "instagram_reels"
ruleName: String          — human-readable e.g. "Instagram Reels"
isEnabled: Boolean
ruleType: String          — "NAVIGATION_INTERCEPT" or "OVERLAY_BLOCK"
```

### 4.3 DAOs

Each DAO exposes only `suspend` functions or `Flow<T>` — no synchronous queries.

**`AppRuleDao`**
- `upsert(rule: AppRuleEntity)`
- `delete(packageName: String)`
- `getAll(): Flow<List<AppRuleEntity>>`
- `getByPackage(packageName: String): Flow<AppRuleEntity?>`
- `getByCategory(categoryId: String): Flow<List<AppRuleEntity>>`

**`AppCategoryDao`**
- `upsert(category: AppCategoryEntity)`
- `delete(id: String)`
- `getAll(): Flow<List<AppCategoryEntity>>`
- `getById(id: String): Flow<AppCategoryEntity?>`

**`ScheduleDao`**
- `upsert(schedule: ScheduleEntity)`
- `delete(id: String)`
- `getAll(): Flow<List<ScheduleEntity>>`
- `getActive(): Flow<List<ScheduleEntity>>`

**`DailyUsageRecordDao`**
- `upsert(record: DailyUsageRecordEntity)`
- `getForDate(date: String): Flow<List<DailyUsageRecordEntity>>`
- `getForPackageAndDate(packageName: String, date: String): DailyUsageRecordEntity?` (suspend)
- `deleteForDate(date: String)`
- `incrementElapsed(packageName: String, date: String, deltaMs: Long)` (@Query UPDATE)

**`UsageHistoryDao`**
- `insert(record: UsageHistoryEntity)`
- `getForPackage(packageName: String): Flow<List<UsageHistoryEntity>>`
- `getForDateRange(from: String, to: String): Flow<List<UsageHistoryEntity>>`
- `deleteOlderThan(date: String)`

**`InAppRuleDao`**
- `upsert(rule: InAppRuleEntity)`
- `delete(id: String)`
- `getByPackage(packageName: String): Flow<List<InAppRuleEntity>>`
- `getAll(): Flow<List<InAppRuleEntity>>`

### 4.4 AppDatabase

```kotlin
@Database(
    entities = [
        AppRuleEntity::class,
        AppCategoryEntity::class,
        ScheduleEntity::class,
        DailyUsageRecordEntity::class,
        UsageHistoryEntity::class,
        InAppRuleEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase()
```

- `exportSchema = true` — schema JSON exported to `schemas/` directory and committed to git for migration tracking.
- `fallbackToDestructiveMigration()` is **prohibited**. All future migrations use explicit `Migration` objects.

### 4.5 Hilt DatabaseModule (`di/DatabaseModule.kt`)

`@InstallIn(SingletonComponent::class)`, `@Singleton` scope for `AppDatabase`.
Individual DAOs provided as `@Provides` functions from the singleton database instance.

---

## 5. Domain Layer (Interfaces Only)

Repository interfaces defined in `domain/repository/`. Implementations deferred to next module.

**`AppRuleRepository`**
- `getAll(): Flow<List<AppRule>>`
- `getByPackage(packageName: String): Flow<AppRule?>`
- `save(rule: AppRule)`
- `delete(packageName: String)`

**`ScheduleRepository`**
- `getAll(): Flow<List<Schedule>>`
- `getActive(): Flow<List<Schedule>>`
- `save(schedule: Schedule)`
- `delete(id: String)`

**`UsageRepository`**
- `getTodayRecord(packageName: String): DailyUsageRecord?`
- `incrementElapsed(packageName: String, deltaMs: Long)`
- `getAllForToday(): Flow<List<DailyUsageRecord>>`
- `archiveDay(date: String)`

---

## 6. Out of Scope for This Module

- Repository implementations (data → domain mapping)
- ViewModels, UI screens
- ForegroundService, AccessibilityService
- WorkManager workers
- EncryptedSharedPreferences setup
- Navigation graph
