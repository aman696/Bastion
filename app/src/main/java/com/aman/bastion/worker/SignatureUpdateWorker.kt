package com.aman.bastion.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aman.bastion.data.inapp.dao.InAppSignatureDao
import com.aman.bastion.domain.catalog.SignatureCatalog
import com.aman.bastion.service.BastionServiceBridge
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SignatureUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val inAppSignatureDao: InAppSignatureDao
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        inAppSignatureDao.upsertAll(SignatureCatalog.ALL_SIGNATURES)
        BastionServiceBridge.signatureCacheInvalidated.value = true
        return Result.success()
    }
}
