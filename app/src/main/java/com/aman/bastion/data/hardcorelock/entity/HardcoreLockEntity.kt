package com.aman.bastion.data.hardcorelock.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hardcore_locks")
data class HardcoreLockEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "locked_at_ms")
    val lockedAtMs: Long,

    @ColumnInfo(name = "locked_until_ms")
    val lockedUntilMs: Long,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean
)
