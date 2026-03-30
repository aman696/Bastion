package com.aman.bastion.data.usage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "daily_usage", primaryKeys = ["package_name", "date"])
data class DailyUsageRecordEntity(
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "elapsed_ms")
    val elapsedMs: Long,

    @ColumnInfo(name = "exclusion_ms")
    val exclusionMs: Long
)
