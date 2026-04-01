package com.aman.bastion

import android.app.Application
import android.os.Process
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aman.bastion.worker.DailyResetWorker
import com.aman.bastion.worker.SignatureUpdateWorker
import com.aman.bastion.worker.ServiceWatchdogWorker
import com.aman.bastion.worker.SeedSignaturesWorker
import com.aman.bastion.worker.UsageHistoryCleanupWorker
import dagger.hilt.android.HiltAndroidApp
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import androidx.work.Constraints
import androidx.work.NetworkType

@HiltAndroidApp
class BastionApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (!isMainProcess()) return

        val seedWork = OneTimeWorkRequestBuilder<SeedSignaturesWorker>().build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "seed_signatures",
            ExistingWorkPolicy.KEEP,
            seedWork
        )

        val watchdogWork = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
            15,
            TimeUnit.MINUTES
        )
            .addTag("bastion_watchdog")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "bastion_watchdog",
            ExistingPeriodicWorkPolicy.KEEP,
            watchdogWork
        )

        val resetRequest = PeriodicWorkRequestBuilder<DailyResetWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(millisUntilMidnight(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_reset",
            ExistingPeriodicWorkPolicy.KEEP,
            resetRequest
        )

        val signatureWork = PeriodicWorkRequestBuilder<SignatureUpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "signature_update",
            ExistingPeriodicWorkPolicy.KEEP,
            signatureWork
        )

        val cleanupWork = PeriodicWorkRequestBuilder<UsageHistoryCleanupWorker>(7, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresDeviceIdle(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "usage_history_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupWork
        )
    }

    private fun millisUntilMidnight(): Long {
        val now = LocalDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
        return ChronoUnit.MILLIS.between(now, nextMidnight)
    }

    private fun isMainProcess(): Boolean {
        val currentProcess = Process.myProcessName()
        return currentProcess == packageName
    }
}
