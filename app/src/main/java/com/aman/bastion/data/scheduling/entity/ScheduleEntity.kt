package com.aman.bastion.data.scheduling.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "target_packages")
    val targetPackages: List<String>,

    @ColumnInfo(name = "target_category_ids")
    val targetCategoryIds: List<String>,

    @ColumnInfo(name = "start_time_minutes")
    val startTimeMinutes: Int,

    @ColumnInfo(name = "end_time_minutes")
    val endTimeMinutes: Int,

    // bit 0=Mon, bit 1=Tue, bit 2=Wed, bit 3=Thu, bit 4=Fri, bit 5=Sat, bit 6=Sun
    @ColumnInfo(name = "days_of_week_bitmask")
    val daysOfWeekBitmask: Int,

    // "HARD" or "SOFT"
    @ColumnInfo(name = "block_type")
    val blockType: String,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean
)
