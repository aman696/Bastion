package com.aman.bastion.service.engine

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import com.aman.bastion.data.blocking.dao.AppRuleDao
import com.aman.bastion.data.usage.dao.DailyUsageRecordDao
import com.aman.bastion.data.usage.entity.DailyUsageRecordEntity
import com.aman.bastion.service.BastionServiceBridge
import com.aman.bastion.service.OverlayEngine
import com.aman.bastion.service.OverlayType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

@Singleton
class UsageTrackingEngine @Inject constructor(
    private val dailyUsageRecordDao: DailyUsageRecordDao,
    private val appRuleDao: AppRuleDao,
    private val overlayEngine: OverlayEngine,
    private val usageStatsManager: UsageStatsManager,
    @ApplicationContext private val context: Context,
    private val categoryAccumulator: CategoryUsageAccumulator
) {

    private data class ActiveSession(
        val packageName: String,
        val startMs: Long,
        var excludedMs: Long = 0L
    )

    private var activeSession: ActiveSession? = null
    private var lastPollTimeMs = System.currentTimeMillis()
    private var inExclusionSince: Long? = null

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val today = LocalDate.now().toString()
            val todayRecords = dailyUsageRecordDao.getForDate(today).first()
            categoryAccumulator.rebuildFromDatabase(todayRecords)
        }

        scope.launch(Dispatchers.IO) {
            while (isActive) {
                pollUsageEvents()
                delay(POLL_INTERVAL_MS)
            }
        }

        scope.launch(Dispatchers.IO) {
            BastionServiceBridge.foregroundPackage.collect { pkg ->
                pkg ?: return@collect
                val now = System.currentTimeMillis()
                activeSession?.let { session ->
                    if (session.packageName != pkg) {
                        flushSession(session, now)
                        activeSession = null
                    }
                }
                if (activeSession == null) {
                    activeSession = ActiveSession(pkg, now)
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            BastionServiceBridge.inAppScreenState.collect { state ->
                state ?: return@collect
                val isExcludedState = state.isExcludedFromLimit

                if (isExcludedState && inExclusionSince == null) {
                    inExclusionSince = SystemClock.elapsedRealtime()
                } else if (!isExcludedState && inExclusionSince != null) {
                    val excludedDuration = SystemClock.elapsedRealtime() - inExclusionSince!!
                    activeSession?.excludedMs = (activeSession?.excludedMs ?: 0L) + excludedDuration
                    inExclusionSince = null
                }
            }
        }
    }

    private suspend fun pollUsageEvents() {
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(lastPollTimeMs, now)
        lastPollTimeMs = now

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    onAppResumed(event.packageName, event.timeStamp)

                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED ->
                    onAppPaused(event.packageName, event.timeStamp)
            }
        }
    }

    private suspend fun onAppResumed(packageName: String, timestamp: Long) {
        activeSession?.let {
            if (it.packageName != packageName) {
                flushSession(it, timestamp)
                activeSession = null
            }
        }
        if (activeSession == null) {
            activeSession = ActiveSession(packageName, timestamp)
        }
    }

    private suspend fun onAppPaused(packageName: String, timestamp: Long) {
        activeSession?.let {
            if (it.packageName == packageName) {
                flushSession(it, timestamp)
                activeSession = null
            }
        }
    }

    private suspend fun flushSession(session: ActiveSession, endMs: Long) {
        val rawDuration = endMs - session.startMs
        inExclusionSince?.let { start ->
            val remaining = SystemClock.elapsedRealtime() - start
            session.excludedMs += remaining
            inExclusionSince = null
        }
        val netDuration = maxOf(0L, rawDuration - session.excludedMs)
        if (netDuration == 0L) return

        val date = LocalDate.now().toString()
        val existing = dailyUsageRecordDao.getForPackageAndDate(session.packageName, date)
        if (existing == null) {
            dailyUsageRecordDao.upsert(
                DailyUsageRecordEntity(
                    packageName = session.packageName,
                    date = date,
                    elapsedMs = 0L,
                    exclusionMs = 0L
                )
            )
        }
        dailyUsageRecordDao.incrementElapsed(session.packageName, date, netDuration)
        if (session.excludedMs > 0L) {
            dailyUsageRecordDao.incrementExclusion(session.packageName, date, session.excludedMs)
        }

        checkLimitReached(session.packageName, date)
        categoryAccumulator.onSessionFlushed(session.packageName, netDuration)
    }

    private suspend fun checkLimitReached(packageName: String, date: String) {
        val rule = appRuleDao.getByPackageSync(packageName) ?: return
        if (rule.isHardBlocked) return
        if (rule.dailyLimitMs == 0L) return

        val record = dailyUsageRecordDao.getForPackageAndDate(packageName, date) ?: return
        val netUsage = record.elapsedMs - record.exclusionMs

        if (netUsage >= rule.dailyLimitMs) {
            val appLabel = runCatching {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(
                        packageName,
                        PackageManager.ApplicationInfoFlags.of(0)
                    )
                ).toString()
            }.getOrElse { packageName }

            overlayEngine.showOverlay(
                "block_$packageName",
                OverlayType.AppBlock(
                    packageName = packageName,
                    appLabel = appLabel,
                    lockedUntilMs = 0L,
                    reason = "Daily limit reached"
                )
            )
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 10_000L
    }
}
