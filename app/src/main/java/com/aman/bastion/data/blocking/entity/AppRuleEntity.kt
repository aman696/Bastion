package com.aman.bastion.data.blocking.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_rules")
data class AppRuleEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "daily_limit_ms")
    val dailyLimitMs: Long,

    @ColumnInfo(name = "is_hard_blocked")
    val isHardBlocked: Boolean,

    @ColumnInfo(name = "category_id")
    val categoryId: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "hardcore_until_ms")
    val hardcoreUntilMs: Long = 0L,

    @ColumnInfo(name = "unlock_condition")
    val unlockCondition: String? = null,

    @ColumnInfo(name = "block_note")
    val blockNote: String? = null
)
