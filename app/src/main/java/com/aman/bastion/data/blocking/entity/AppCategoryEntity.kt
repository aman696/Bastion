package com.aman.bastion.data.blocking.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_categories")
data class AppCategoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "daily_limit_ms")
    val dailyLimitMs: Long,

    @ColumnInfo(name = "color_hex")
    val colorHex: String
)
