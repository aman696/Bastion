package com.aman.bastion.domain.repository

import com.aman.bastion.domain.model.DailyUsageRecord
import kotlinx.coroutines.flow.Flow

interface UsageRepository {
    fun getAllForToday(): Flow<List<DailyUsageRecord>>
    suspend fun getTodayRecord(packageName: String): DailyUsageRecord?
    suspend fun incrementElapsed(packageName: String, date: String, deltaMs: Long)
    suspend fun archiveDay(date: String)
}
