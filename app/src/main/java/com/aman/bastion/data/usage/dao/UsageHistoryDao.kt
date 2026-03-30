package com.aman.bastion.data.usage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aman.bastion.data.usage.entity.UsageHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: UsageHistoryEntity)

    @Query("SELECT * FROM usage_history WHERE package_name = :packageName ORDER BY date DESC")
    fun getForPackage(packageName: String): Flow<List<UsageHistoryEntity>>

    @Query("SELECT * FROM usage_history WHERE date >= :from AND date <= :to ORDER BY date DESC")
    fun getForDateRange(from: String, to: String): Flow<List<UsageHistoryEntity>>

    @Query("DELETE FROM usage_history WHERE date < :date")
    suspend fun deleteOlderThan(date: String)
}
