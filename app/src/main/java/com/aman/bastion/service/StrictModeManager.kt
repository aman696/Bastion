package com.aman.bastion.service

import android.content.SharedPreferences
import com.aman.bastion.data.hardcorelock.dao.HardcoreLockDao
import com.aman.bastion.data.hardcorelock.entity.HardcoreLockEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

@Singleton
class StrictModeManager @Inject constructor(
    private val hardcoreLockDao: HardcoreLockDao,
    private val prefs: SharedPreferences,
    private val deviceAdminManager: DeviceAdminManager
) {

    val requiresAdminActivation = MutableStateFlow(false)

    suspend fun activate(
        packageName: String,
        durationMs: Long,
        coolingOffMs: Long = 15 * 60 * 1_000L
    ) {
        val now = System.currentTimeMillis()
        val until = now + durationMs

        hardcoreLockDao.upsert(
            HardcoreLockEntity(
                packageName = packageName,
                lockedAtMs = now,
                lockedUntilMs = until,
                isActive = true
            )
        )

        BastionServiceBridge.strictModeActive.value = true
        requiresAdminActivation.value = !deviceAdminManager.isAdminActive()
        prefs.edit()
            .putBoolean("strict_active", true)
            .putLong("strict_until", until)
            .putLong("cooling_off_until_ms", now + coolingOffMs)
            .apply()
    }

    suspend fun checkAndRestoreOnBoot() {
        val until = prefs.getLong("strict_until", 0L)
        val now = System.currentTimeMillis()

        if (until > now) {
            BastionServiceBridge.strictModeActive.value = true
        } else {
            BastionServiceBridge.strictModeActive.value = false
            prefs.edit()
                .remove("strict_active")
                .remove("strict_until")
                .remove("cooling_off_until_ms")
                .apply()
            hardcoreLockDao.deactivateExpired(now)
        }
    }

    suspend fun deactivateIfExpired() {
        val until = prefs.getLong("strict_until", 0L)
        if (until in 1 until System.currentTimeMillis()) {
            BastionServiceBridge.strictModeActive.value = false
            prefs.edit()
                .remove("strict_active")
                .remove("strict_until")
                .remove("cooling_off_until_ms")
                .apply()
        }
    }

    fun canDeactivateNow(): Boolean {
        val coolingOffUntil = prefs.getLong("cooling_off_until_ms", 0L)
        return System.currentTimeMillis() >= coolingOffUntil
    }

    fun getCoolingOffRemainingMs(): Long {
        val coolingOffUntil = prefs.getLong("cooling_off_until_ms", 0L)
        return maxOf(0L, coolingOffUntil - System.currentTimeMillis())
    }

    suspend fun deactivate(): DeactivationResult {
        if (!canDeactivateNow()) {
            return DeactivationResult.CoolingOffActive(getCoolingOffRemainingMs())
        }

        val activeLocks = hardcoreLockDao.observeActive().first()
        if (activeLocks.isNotEmpty()) {
            val longestLock = activeLocks.maxByOrNull { it.lockedUntilMs }!!
            return DeactivationResult.ActiveLockExists(longestLock.lockedUntilMs)
        }

        BastionServiceBridge.strictModeActive.value = false
        prefs.edit()
            .remove("strict_active")
            .remove("strict_until")
            .remove("cooling_off_until_ms")
            .apply()
        return DeactivationResult.Success
    }

    sealed class DeactivationResult {
        data object Success : DeactivationResult()
        data class CoolingOffActive(val remainingMs: Long) : DeactivationResult()
        data class ActiveLockExists(val lockedUntilMs: Long) : DeactivationResult()
    }
}
