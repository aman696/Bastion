package com.aman.bastion.data.usage.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.aman.bastion.data.usage.entity.DailyUsageRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyUsageRecordDao {

    @Upsert
    suspend fun upsert(record: DailyUsageRecordEntity)

    @Upsert
    suspend fun upsertAll(records: List<DailyUsageRecordEntity>)

    @Query("SELECT * FROM daily_usage WHERE date = :date")
    fun getForDate(date: String): Flow<List<DailyUsageRecordEntity>>

    @Query("SELECT * FROM daily_usage")
    suspend fun getAllSync(): List<DailyUsageRecordEntity>

    @Query("SELECT * FROM daily_usage WHERE package_name = :packageName AND date = :date LIMIT 1")
    suspend fun getForPackageAndDate(packageName: String, date: String): DailyUsageRecordEntity?

    @Query("DELETE FROM daily_usage WHERE date = :date")
    suspend fun deleteForDate(date: String)

    @Query("UPDATE daily_usage SET elapsed_ms = elapsed_ms + :deltaMs WHERE package_name = :packageName AND date = :date")
    suspend fun incrementElapsed(packageName: String, date: String, deltaMs: Long)

    @Query(
        """
        UPDATE daily_usage
        SET exclusion_ms = exclusion_ms + :deltaMs
        WHERE package_name = :packageName AND date = :date
        """
    )
    suspend fun incrementExclusion(packageName: String, date: String, deltaMs: Long)
}
