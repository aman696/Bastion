package com.aman.bastion.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aman.bastion.data.inapp.dao.InAppSignatureDao
import com.aman.bastion.domain.catalog.SignatureCatalog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SeedSignaturesWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val inAppSignatureDao: InAppSignatureDao
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        inAppSignatureDao.deleteAll()
        inAppSignatureDao.upsertAll(SignatureCatalog.ALL_SIGNATURES)
        return Result.success()
    }
}
