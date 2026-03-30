package com.aman.bastion.domain.repository

import com.aman.bastion.domain.model.Schedule
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    fun getAll(): Flow<List<Schedule>>
    fun getActive(): Flow<List<Schedule>>
    suspend fun save(schedule: Schedule)
    suspend fun delete(id: String)
}
