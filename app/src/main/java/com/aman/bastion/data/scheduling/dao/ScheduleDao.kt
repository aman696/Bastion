package com.aman.bastion.data.scheduling.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.aman.bastion.data.scheduling.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Upsert
    suspend fun upsert(schedule: ScheduleEntity)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM schedules")
    fun getAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE is_active = 1")
    fun getActive(): Flow<List<ScheduleEntity>>
}
