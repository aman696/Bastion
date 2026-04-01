package com.aman.bastion.data.scheduling

import com.aman.bastion.data.scheduling.dao.ScheduleDao
import com.aman.bastion.data.scheduling.entity.ScheduleEntity
import com.aman.bastion.domain.model.BlockType
import com.aman.bastion.domain.model.Schedule
import com.aman.bastion.domain.repository.ScheduleRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val scheduleDao: ScheduleDao
) : ScheduleRepository {

    override fun getAll(): Flow<List<Schedule>> =
        scheduleDao.getAll().map { schedules -> schedules.map(ScheduleEntity::toDomain) }

    override fun getActive(): Flow<List<Schedule>> =
        scheduleDao.getActive().map { schedules -> schedules.map(ScheduleEntity::toDomain) }

    override suspend fun save(schedule: Schedule) {
        scheduleDao.upsert(schedule.toEntity())
    }

    override suspend fun delete(id: String) {
        scheduleDao.delete(id)
    }
}

private fun ScheduleEntity.toDomain() = Schedule(
    id = id,
    name = name,
    targetPackages = targetPackages,
    targetCategoryIds = targetCategoryIds,
    startTimeMinutes = startTimeMinutes,
    endTimeMinutes = endTimeMinutes,
    daysOfWeekBitmask = daysOfWeekBitmask,
    blockType = BlockType.valueOf(blockType),
    isActive = isActive
)

private fun Schedule.toEntity() = ScheduleEntity(
    id = id,
    name = name,
    targetPackages = targetPackages,
    targetCategoryIds = targetCategoryIds,
    startTimeMinutes = startTimeMinutes,
    endTimeMinutes = endTimeMinutes,
    daysOfWeekBitmask = daysOfWeekBitmask,
    blockType = blockType.name,
    isActive = isActive
)
