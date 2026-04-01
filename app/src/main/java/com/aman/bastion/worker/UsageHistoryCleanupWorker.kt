package com.aman.bastion.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aman.bastion.data.usage.dao.UsageHistoryDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

@HiltWorker
class UsageHistoryCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val usageHistoryDao: UsageHistoryDao
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val cutoff = LocalDate.now().minusDays(90).toString()
        usageHistoryDao.deleteOlderThan(cutoff)
        return Result.success()
    }
}
