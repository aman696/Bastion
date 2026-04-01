package com.aman.bastion.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aman.bastion.data.usage.dao.DailyUsageRecordDao
import com.aman.bastion.data.usage.dao.UsageHistoryDao
import com.aman.bastion.data.usage.entity.UsageHistoryEntity
import com.aman.bastion.service.engine.CategoryUsageAccumulator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import kotlinx.coroutines.flow.first

@HiltWorker
class DailyResetWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dailyUsageRecordDao: DailyUsageRecordDao,
    private val usageHistoryDao: UsageHistoryDao,
    private val categoryAccumulator: CategoryUsageAccumulator
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val yesterday = LocalDate.now().minusDays(1).toString()
        val yesterdayRecords = dailyUsageRecordDao.getForDate(yesterday).first()

        yesterdayRecords.forEach { record ->
            usageHistoryDao.insert(
                UsageHistoryEntity(
                    packageName = record.packageName,
                    date = record.date,
                    elapsedMs = record.elapsedMs
                )
            )
        }

        dailyUsageRecordDao.deleteForDate(yesterday)

        val cutoff = LocalDate.now().minusDays(90).toString()
        usageHistoryDao.deleteOlderThan(cutoff)
        categoryAccumulator.resetAllAccumulators()

        return Result.success()
    }
}
