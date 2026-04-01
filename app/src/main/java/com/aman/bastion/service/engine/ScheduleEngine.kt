package com.aman.bastion.service.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.aman.bastion.data.blocking.dao.AppCategoryDao
import com.aman.bastion.data.blocking.dao.AppRuleDao
import com.aman.bastion.data.blocking.entity.AppRuleEntity
import com.aman.bastion.data.scheduling.dao.ScheduleDao
import com.aman.bastion.data.scheduling.entity.ScheduleEntity
import com.aman.bastion.receiver.ScheduleAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Singleton
class ScheduleEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleDao: ScheduleDao,
    private val appRuleDao: AppRuleDao,
    @Suppress("unused") private val appCategoryDao: AppCategoryDao,
    private val alarmManager: AlarmManager
) {

    fun isScheduleActiveNow(schedule: ScheduleEntity): Boolean {
        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute
        val todayBit = when (LocalDate.now().dayOfWeek) {
            DayOfWeek.MONDAY -> 0
            DayOfWeek.TUESDAY -> 1
            DayOfWeek.WEDNESDAY -> 2
            DayOfWeek.THURSDAY -> 3
            DayOfWeek.FRIDAY -> 4
            DayOfWeek.SATURDAY -> 5
            DayOfWeek.SUNDAY -> 6
        }
        val dayMatches = ((schedule.daysOfWeekBitmask shr todayBit) and 1) == 1
        if (!dayMatches) return false

        return if (schedule.startTimeMinutes <= schedule.endTimeMinutes) {
            nowMinutes in schedule.startTimeMinutes until schedule.endTimeMinutes
        } else {
            nowMinutes >= schedule.startTimeMinutes || nowMinutes < schedule.endTimeMinutes
        }
    }

    suspend fun scheduleAlarms() {
        val schedules = scheduleDao.getActive().first()
        schedules.forEach { schedule ->
            scheduleStartAlarm(schedule)
            scheduleEndAlarm(schedule)
        }
    }

    fun cancelAlarms(scheduleId: String) {
        alarmManager.cancel(schedulePendingIntent(scheduleId, "ACTIVATE"))
        alarmManager.cancel(schedulePendingIntent(scheduleId, "DEACTIVATE"))
    }

    suspend fun applySchedule(scheduleId: String, action: String) {
        val schedule = scheduleDao.getActive().first()
            .firstOrNull { it.id == scheduleId } ?: return

        val affectedPackages = buildAffectedPackages(schedule)
        when (action) {
            "ACTIVATE" -> {
                affectedPackages.forEach { pkg ->
                    val existing = appRuleDao.getByPackageSync(pkg)
                    val updated = if (existing != null) {
                        existing.copy(isHardBlocked = schedule.blockType == "HARD")
                    } else {
                        AppRuleEntity(
                            packageName = pkg,
                            dailyLimitMs = 0L,
                            isHardBlocked = schedule.blockType == "HARD",
                            categoryId = null,
                            createdAt = System.currentTimeMillis()
                        )
                    }
                    appRuleDao.upsert(updated)
                }
                val entity = scheduleDao.getActive().first()
                    .firstOrNull { it.id == scheduleId } ?: return
                scheduleStartAlarm(entity)
            }

            "DEACTIVATE" -> {
                affectedPackages.forEach { pkg ->
                    val existing = appRuleDao.getByPackageSync(pkg) ?: return@forEach
                    if (existing.dailyLimitMs == 0L && existing.hardcoreUntilMs == 0L) {
                        appRuleDao.delete(pkg)
                    } else {
                        appRuleDao.upsert(existing.copy(isHardBlocked = false))
                    }
                }
                val entity = scheduleDao.getActive().first()
                    .firstOrNull { it.id == scheduleId } ?: return
                scheduleEndAlarm(entity)
            }
        }
    }

    suspend fun checkAndEnforceCurrentSchedules() {
        val active = scheduleDao.getActive().first()
        active.forEach { schedule ->
            val shouldBeActive = isScheduleActiveNow(schedule)
            val affectedPackages = buildAffectedPackages(schedule)
            affectedPackages.forEach { pkg ->
                val rule = appRuleDao.getByPackageSync(pkg)
                val currentlyBlocked = rule?.isHardBlocked == true
                if (shouldBeActive && !currentlyBlocked) {
                    applySchedule(schedule.id, "ACTIVATE")
                } else if (!shouldBeActive && currentlyBlocked) {
                    if (rule?.dailyLimitMs == 0L && rule.hardcoreUntilMs == 0L) {
                        applySchedule(schedule.id, "DEACTIVATE")
                    }
                }
            }
        }
    }

    private suspend fun buildAffectedPackages(schedule: ScheduleEntity): List<String> {
        val direct = schedule.targetPackages.toMutableList()
        schedule.targetCategoryIds.forEach { catId ->
            val catApps = appRuleDao.getByCategorySync(catId)
            direct.addAll(catApps.map { it.packageName })
        }
        return direct.distinct()
    }

    private fun scheduleStartAlarm(schedule: ScheduleEntity) {
        val triggerAtMs = nextTriggerMs(schedule.startTimeMinutes, schedule.daysOfWeekBitmask)
        scheduleAlarm(triggerAtMs, schedulePendingIntent(schedule.id, "ACTIVATE"))
    }

    private fun scheduleEndAlarm(schedule: ScheduleEntity) {
        val triggerAtMs = nextTriggerMs(schedule.endTimeMinutes, schedule.daysOfWeekBitmask)
        scheduleAlarm(triggerAtMs, schedulePendingIntent(schedule.id, "DEACTIVATE"))
    }

    private fun scheduleAlarm(triggerAtMs: Long, pendingIntent: PendingIntent) {
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                pendingIntent
            )
        }
    }

    private fun nextTriggerMs(minutesSinceMidnight: Int, daysOfWeekBitmask: Int): Long {
        val cal = Calendar.getInstance()
        val targetHour = minutesSinceMidnight / 60
        val targetMin = minutesSinceMidnight % 60

        for (daysAhead in 0..7) {
            if (daysAhead > 0) cal.add(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, targetHour)
            cal.set(Calendar.MINUTE, targetMin)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            if (daysAhead == 0 && cal.timeInMillis <= System.currentTimeMillis()) continue

            val bit = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                Calendar.SUNDAY -> 6
                else -> continue
            }
            if (((daysOfWeekBitmask shr bit) and 1) == 1) return cal.timeInMillis
        }

        return System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1_000L
    }

    private fun schedulePendingIntent(scheduleId: String, action: String): PendingIntent {
        val requestCodePrefix = if (action == "ACTIVATE") "start_" else "end_"
        return PendingIntent.getBroadcast(
            context,
            "$requestCodePrefix$scheduleId".hashCode(),
            Intent(context, ScheduleAlarmReceiver::class.java).apply {
                putExtra("schedule_id", scheduleId)
                putExtra("action", action)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
