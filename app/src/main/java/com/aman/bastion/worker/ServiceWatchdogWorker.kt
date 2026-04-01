package com.aman.bastion.worker

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aman.bastion.service.BastionForegroundService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ServiceWatchdogWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val activityManager =
            applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        val isRunning = activityManager
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == BastionForegroundService::class.java.name }

        if (!isRunning) {
            ContextCompat.startForegroundService(
                applicationContext,
                Intent(applicationContext, BastionForegroundService::class.java)
            )
        }

        return Result.success()
    }
}
