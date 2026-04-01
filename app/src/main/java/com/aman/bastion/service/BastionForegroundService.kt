package com.aman.bastion.service

import android.app.AlarmManager
import android.app.AppOpsManager
import android.media.AudioManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.aman.bastion.MainActivity
import com.aman.bastion.R
import com.aman.bastion.data.blocking.dao.AppCategoryDao
import com.aman.bastion.data.blocking.dao.AppRuleDao
import com.aman.bastion.data.hardcorelock.dao.HardcoreLockDao
import com.aman.bastion.data.hardcorelock.entity.HardcoreLockEntity
import com.aman.bastion.data.inapp.dao.InAppRuleDao
import com.aman.bastion.data.service.dao.ServiceStateDao
import com.aman.bastion.data.service.entity.ServiceStateEntity
import com.aman.bastion.data.usage.dao.DailyUsageRecordDao
import com.aman.bastion.service.engine.InAppDetectionEngine
import com.aman.bastion.service.engine.ScheduleEngine
import com.aman.bastion.service.engine.UsageTrackingEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

@AndroidEntryPoint
class BastionForegroundService : Service() {

    @Inject
    lateinit var strictModeManager: StrictModeManager

    @Inject
    lateinit var appRuleDao: AppRuleDao

    @Inject
    lateinit var appCategoryDao: AppCategoryDao

    @Inject
    lateinit var hardcoreLockDao: HardcoreLockDao

    @Inject
    lateinit var serviceStateDao: ServiceStateDao

    @Inject
    lateinit var overlayEngine: OverlayEngine

    @Inject
    lateinit var inAppRuleDao: InAppRuleDao

    @Inject
    lateinit var inAppDetectionEngine: InAppDetectionEngine

    @Inject
    lateinit var dailyUsageRecordDao: DailyUsageRecordDao

    @Inject
    lateinit var usageTrackingEngine: UsageTrackingEngine

    @Inject
    lateinit var scheduleEngine: ScheduleEngine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val alarmManager by lazy { getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Volatile
    private var activeLocks: List<HardcoreLockEntity> = emptyList()
    private var lastForegroundOverlayKey: String? = null
    private var lastFeatureOverlayKeys: Set<String> = emptySet()
    private var lastFeatureActionKey: String? = null
    private var pendingBoundedSyncJob: Job? = null
    private var pendingFeatureClearJob: Job? = null
    private var lastFeatureActionAtMs: Long = 0L
    private var previousMediaVolume: Int? = null
    private var mutedByBastion = false

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildInitialNotification())

        serviceScope.launch {
            strictModeManager.checkAndRestoreOnBoot()
            val strictActive = BastionServiceBridge.strictModeActive.value
            val a11yEnabled = isA11yEnabled()
            if (strictActive && !a11yEnabled) {
                showCriticalProtectionNotification()
            }
        }

        scheduleRestart()
        overlayEngine.start(serviceScope)
        usageTrackingEngine.start(serviceScope)

        serviceScope.launch {
            while (isActive) {
                updateNotification()
                delay(1_000)
            }
        }

        serviceScope.launch {
            hardcoreLockDao.observeActive().collect { locks ->
                activeLocks = locks
                val now = System.currentTimeMillis()
                locks.forEach { lock ->
                    if (lock.lockedUntilMs <= now) {
                        hardcoreLockDao.deactivateExpired(now)
                    }
                }
            }
        }

        serviceScope.launch {
            while (isActive) {
                clearExpiredHardcoreRestrictions()
                hardcoreLockDao.deactivateExpired(System.currentTimeMillis())
                strictModeManager.deactivateIfExpired()
                delay(1_000)
            }
        }

        serviceScope.launch {
            while (isActive) {
                checkComponentState()
                delay(30_000)
            }
        }

        serviceScope.launch {
            BastionServiceBridge.foregroundPackage.collect { pkg ->
                if (pkg == null) {
                    BastionServiceBridge.inAppScreenState.value = null
                    lastForegroundOverlayKey?.let(overlayEngine::hideOverlay)
                    lastForegroundOverlayKey = null
                    return@collect
                }

                val currentInAppState = BastionServiceBridge.inAppScreenState.value
                if (currentInAppState != null && currentInAppState.packageName != pkg) {
                    BastionServiceBridge.inAppScreenState.value = null
                }

                val currentKey = "block_$pkg"
                lastForegroundOverlayKey
                    ?.takeIf { it != currentKey }
                    ?.let(overlayEngine::hideOverlay)

                val blockOverlay = buildForegroundBlockOverlay(pkg)
                if (blockOverlay != null) {
                    overlayEngine.hideOverlay(currentKey)
                    lastForegroundOverlayKey = null
                    BastionServiceBridge.navigationCommand.value = NavigationCommand.HOME
                    BastionServiceBridge.foregroundPackage.value = null
                } else {
                    overlayEngine.hideOverlay(currentKey)
                    lastForegroundOverlayKey = null
                }
            }
        }

        serviceScope.launch {
            BastionServiceBridge.settingsThreatDetected.collect { threatened ->
                if (threatened) {
                    overlayEngine.showOverlay("anticircumvention", OverlayType.AntiCircumvention)
                    BastionServiceBridge.settingsThreatDetected.value = false
                }
            }
        }

        serviceScope.launch {
            BastionServiceBridge.inAppScreenState.collect { state ->
                if (state == null) {
                    pendingBoundedSyncJob?.cancel()
                    scheduleFeatureOverlayClear()
                    return@collect
                }

                pendingFeatureClearJob?.cancel()

                val rules = inAppRuleDao.getByPackageSync(state.packageName)
                val matchingRule = findMatchingFeatureRule(rules, state)

                if (matchingRule == null) {
                    pendingBoundedSyncJob?.cancel()
                    scheduleFeatureOverlayClear()
                    return@collect
                }

                val action = when {
                    state.blockAction != InAppBlockAction.AUTO -> state.blockAction
                    matchingRule.ruleType == "NAVIGATION_INTERCEPT" -> InAppBlockAction.BACK
                    else -> InAppBlockAction.FULL_OVERLAY
                }

                if (shouldMuteBlockedMedia(state, action)) {
                    muteMediaIfNeeded()
                } else {
                    restoreMutedMediaIfNeeded()
                }

                when (action) {
                    InAppBlockAction.NONE -> {
                        pendingBoundedSyncJob?.cancel()
                        scheduleFeatureOverlayClear()
                    }

                    InAppBlockAction.BACK -> {
                        pendingFeatureClearJob?.cancel()
                        pendingBoundedSyncJob?.cancel()
                        syncFeatureOverlays(emptyList())
                        restoreMutedMediaIfNeeded()
                        val actionKey = "${state.packageName}:${matchingRule.featureId}:${state.screenState}"
                        BastionServiceBridge.inAppScreenState.value = null
                        if (!isFeatureActionDebounced(actionKey, 1_000L)) {
                            BastionServiceBridge.navigationCommand.value = NavigationCommand.BACK
                        }
                    }

                    InAppBlockAction.FULL_OVERLAY -> {
                        pendingFeatureClearJob?.cancel()
                        pendingBoundedSyncJob?.cancel()
                        syncFeatureOverlays(
                            buildFeatureOverlays(
                                state = state,
                                featureName = matchingRule.ruleName,
                                action = action
                            )
                        )
                    }

                    InAppBlockAction.BOUNDED_OVERLAY -> {
                        val overlays = buildFeatureOverlays(
                            state = state,
                            featureName = matchingRule.ruleName,
                            action = action
                        )
                        // First appearance: show immediately.
                        // Subsequent updates (scroll moving bounds): debounce 300 ms so
                        // the boxes stay frozen at their last position during fast scroll
                        // and only reposition once the feed settles.
                        if (lastFeatureOverlayKeys.isEmpty()) {
                            pendingBoundedSyncJob?.cancel()
                            syncFeatureOverlays(overlays)
                        } else {
                            pendingBoundedSyncJob?.cancel()
                            pendingBoundedSyncJob = serviceScope.launch {
                                delay(10)
                                syncFeatureOverlays(overlays)
                            }
                        }
                    }

                    InAppBlockAction.AUTO -> Unit
                }
            }
        }

        serviceScope.launch {
            BastionServiceBridge.signatureCacheInvalidated.collect { invalidated ->
                if (invalidated) {
                    inAppDetectionEngine.invalidateCache()
                    BastionServiceBridge.signatureCacheInvalidated.value = false
                }
            }
        }

        serviceScope.launch(Dispatchers.IO) {
            scheduleEngine.scheduleAlarms()
            scheduleEngine.checkAndEnforceCurrentSchedules()
        }

        serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(60_000)
                scheduleEngine.checkAndEnforceCurrentSchedules()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        restoreMutedMediaIfNeeded()
        overlayEngine.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleRestart() {
        val restartIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, BastionForegroundService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMs = SystemClock.elapsedRealtime() + (5 * 60 * 60 * 1_000L)
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMs,
                restartIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMs,
                restartIntent
            )
        }
    }

    private fun buildInitialNotification(): Notification {
        ensureChannel()
        return buildNotification("Bastion is active")
    }

    private fun updateNotification() {
        val contentText = activeLocks
            .filter { it.isActive }
            .minByOrNull { it.lockedUntilMs }
            ?.let { lock ->
                val appLabel = resolveAppLabel(lock.packageName)
                "LOCKED: $appLabel - ${formatRemaining(lock.lockedUntilMs)}"
            }
            ?: "Bastion is active"

        notificationManager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private suspend fun checkComponentState() {
        val now = System.currentTimeMillis()
        val previous = serviceStateDao.get()
        val a11yEnabled = isA11yEnabled()
        val overlayGranted = Settings.canDrawOverlays(this)
        val usageAccessGranted = hasUsageAccess()
        val batteryOptExempt = isBatteryOptimizationIgnored()

        serviceStateDao.upsert(
            ServiceStateEntity(
                id = 1,
                a11yEnabled = a11yEnabled,
                a11yLastSeenAt = if (a11yEnabled) now else previous?.a11yLastSeenAt ?: 0L,
                overlayPermission = overlayGranted,
                usageAccessGranted = usageAccessGranted,
                batteryOptExempt = batteryOptExempt,
                lastCheckedAt = now
            )
        )
    }

    private fun isA11yEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(packageName, ignoreCase = true)
    }

    private fun hasUsageAccess(): Boolean {
        val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Bastion Service",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun showCriticalProtectionNotification() {
        val notification = NotificationCompat.Builder(this, CRITICAL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Bastion protection compromised")
            .setContentText(
                "Your strict mode lock is active but Accessibility is off. Tap to restore protection."
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    1,
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        val channel = NotificationChannel(
            CRITICAL_CHANNEL_ID,
            "Bastion Critical Alerts",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)
        notificationManager.notify(CRITICAL_NOTIFICATION_ID, notification)
    }

    private fun resolveAppLabel(packageName: String): String {
        return runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            ).toString()
        }.getOrElse { packageName }
    }

    private fun formatRemaining(lockedUntilMs: Long): String {
        val remainingMs = (lockedUntilMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val totalSeconds = remainingMs / 1_000
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun findMatchingFeatureRule(
        rules: List<com.aman.bastion.data.inapp.entity.InAppRuleEntity>,
        state: InAppScreenState
    ): com.aman.bastion.data.inapp.entity.InAppRuleEntity? {
        val candidates = featureRuleCandidates(state)
        return candidates.firstNotNullOfOrNull { candidate ->
            rules.firstOrNull { rule ->
                rule.isEnabled && rule.featureId.equals(candidate, ignoreCase = true)
            }
        }
    }

    private fun featureRuleCandidates(state: InAppScreenState): List<String> = when (state.packageName) {
        "com.instagram.android" -> when (state.screenState) {
            "REELS" -> listOf("REELS")
            "EXPLORE" -> listOf("REELS", "EXPLORE")
            "HOME" -> listOf("REELS", "HOME")
            "DM" -> listOf("DM")
            "DM_REEL_EXCEPTION" -> listOf("REELS", "DM")
            else -> listOfNotNull(state.featureId, state.screenState)
        }

        else -> listOfNotNull(state.featureId, state.screenState)
    }

    private fun matchesScreenState(featureId: String, screenState: String): Boolean {
        val normalizedFeatureId = featureId.lowercase()
        val normalizedScreenState = screenState.lowercase()
        if (normalizedScreenState == "dm_reel_exception") return false
        return normalizedFeatureId == normalizedScreenState ||
            normalizedFeatureId.endsWith("_$normalizedScreenState") ||
            normalizedFeatureId.contains(normalizedScreenState)
    }

    private fun isFeatureActionDebounced(actionKey: String, debounceMs: Long): Boolean {
        val now = SystemClock.elapsedRealtime()
        val isDebounced = lastFeatureActionKey == actionKey &&
            now - lastFeatureActionAtMs < debounceMs
        if (!isDebounced) {
            lastFeatureActionKey = actionKey
            lastFeatureActionAtMs = now
        }
        return isDebounced
    }

    private fun buildFeatureOverlays(
        state: InAppScreenState,
        featureName: String,
        action: InAppBlockAction
    ): List<Pair<String, OverlayType.FeatureBlock>> {
        if (action == InAppBlockAction.BOUNDED_OVERLAY) {
            val key = "feature_${state.packageName}_${state.screenState}"
            return listOf(
                key to OverlayType.FeatureBlock(
                    packageName = state.packageName,
                    featureName = featureName,
                    mode = FeatureBlockMode.BOUNDED,
                    boundsInScreen = state.overlayBounds.take(5)
                )
            )
        }

        return listOf(
            "feature_${state.packageName}_${state.screenState}" to OverlayType.FeatureBlock(
                packageName = state.packageName,
                featureName = featureName,
                mode = FeatureBlockMode.FULLSCREEN
            )
        )
    }

    private fun syncFeatureOverlays(
        overlays: List<Pair<String, OverlayType.FeatureBlock>>
    ) {
        val nextKeys = overlays.mapTo(mutableSetOf()) { it.first }
        (lastFeatureOverlayKeys - nextKeys).forEach(overlayEngine::hideOverlay)
        overlays.forEach { (key, overlay) -> overlayEngine.showOverlay(key, overlay) }
        lastFeatureOverlayKeys = nextKeys
    }

    private fun scheduleFeatureOverlayClear() {
        pendingFeatureClearJob?.cancel()
        pendingFeatureClearJob = serviceScope.launch {
            delay(250)
            syncFeatureOverlays(emptyList())
            restoreMutedMediaIfNeeded()
        }
    }

    private fun shouldMuteBlockedMedia(
        state: InAppScreenState,
        action: InAppBlockAction
    ): Boolean {
        if (action !in setOf(InAppBlockAction.FULL_OVERLAY, InAppBlockAction.BOUNDED_OVERLAY)) {
            return false
        }
        return state.packageName == "com.instagram.android" &&
            featureRuleCandidates(state).contains("REELS")
    }

    private fun muteMediaIfNeeded() {
        if (mutedByBastion) return
        previousMediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        mutedByBastion = true
    }

    private fun restoreMutedMediaIfNeeded() {
        if (!mutedByBastion) return
        val restoreVolume = previousMediaVolume
        if (restoreVolume != null) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVolume, 0)
        }
        previousMediaVolume = null
        mutedByBastion = false
    }

    private suspend fun clearExpiredHardcoreRestrictions() {
        val now = System.currentTimeMillis()
        val expiredRules = appRuleDao.getExpiredHardcoreRules(now)
        expiredRules.forEach { rule ->
            val hasFeatureRules = inAppRuleDao.getByPackageSync(rule.packageName).any { it.isEnabled }
            if (hasFeatureRules) {
                inAppRuleDao.deleteByPackage(rule.packageName)
            }
            appRuleDao.delete(rule.packageName)
        }
    }

    private suspend fun buildForegroundBlockOverlay(packageName: String): OverlayType? {
        val appLabel = resolveAppLabel(packageName)
        val now = System.currentTimeMillis()
        val hasEnabledFeatureRules = inAppRuleDao.getByPackageSync(packageName).any { it.isEnabled }

        // 1. Hardcore lock table (written by StrictModeManager / HardcoreLockDao directly)
        val hardLock = hardcoreLockDao.getByPackage(packageName)
        if (hardLock != null && hardLock.isActive) {
            return OverlayType.AppBlock(
                packageName  = packageName,
                appLabel     = appLabel,
                lockedUntilMs = hardLock.lockedUntilMs,
                reason       = "Hardcore lock active"
            )
        }

        if (hasEnabledFeatureRules) return null

        val rule = appRuleDao.getByPackageSync(packageName) ?: return null

        // 2. AppRule hard block / hardcore — written by the UI (AppDetailScreen)
        if (rule.isHardBlocked) {
            return OverlayType.AppBlock(
                packageName  = packageName,
                appLabel     = appLabel,
                lockedUntilMs = if (rule.hardcoreUntilMs > now) rule.hardcoreUntilMs else 0L,
                reason       = if (rule.hardcoreUntilMs > now) "Hardcore lock active" else "Hard blocked"
            )
        }

        val today = LocalDate.now().toString()

        // 3. Per-app daily limit
        if (rule.dailyLimitMs > 0L) {
            val record = dailyUsageRecordDao.getForPackageAndDate(packageName, today)
            val netUsage = maxOf(0L, (record?.elapsedMs ?: 0L) - (record?.exclusionMs ?: 0L))
            if (netUsage >= rule.dailyLimitMs) {
                return OverlayType.AppBlock(
                    packageName  = packageName,
                    appLabel     = appLabel,
                    lockedUntilMs = 0L,
                    reason       = "Daily limit reached"
                )
            }
        }

        // 4. Category daily limit
        val categoryId = rule.categoryId ?: return null
        val category = appCategoryDao.getByIdSync(categoryId) ?: return null
        if (category.dailyLimitMs == 0L) return null

        val appsInCategory = appRuleDao.getByCategorySync(categoryId)
        var totalToday = 0L
        appsInCategory.forEach { catApp ->
            val record = dailyUsageRecordDao.getForPackageAndDate(catApp.packageName, today)
            totalToday += maxOf(0L, (record?.elapsedMs ?: 0L) - (record?.exclusionMs ?: 0L))
        }

        return if (totalToday >= category.dailyLimitMs) {
            OverlayType.AppBlock(
                packageName  = packageName,
                appLabel     = appLabel,
                lockedUntilMs = 0L,
                reason       = "Category limit: ${category.name}"
            )
        } else {
            null
        }
    }

    companion object {
        private const val CHANNEL_ID = "bastion_service"
        private const val CRITICAL_CHANNEL_ID = "bastion_critical"
        private const val NOTIFICATION_ID = 1001
        private const val CRITICAL_NOTIFICATION_ID = 1002
    }
}
