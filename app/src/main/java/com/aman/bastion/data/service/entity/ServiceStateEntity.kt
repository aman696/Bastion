package com.aman.bastion.data.service.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "service_state")
data class ServiceStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1, // singleton row — always update id=1

    @ColumnInfo(name = "a11y_enabled")
    val a11yEnabled: Boolean,

    @ColumnInfo(name = "a11y_last_seen_at")
    val a11yLastSeenAt: Long,

    @ColumnInfo(name = "overlay_permission")
    val overlayPermission: Boolean,

    @ColumnInfo(name = "usage_access_granted")
    val usageAccessGranted: Boolean,

    @ColumnInfo(name = "battery_opt_exempt")
    val batteryOptExempt: Boolean,

    @ColumnInfo(name = "last_checked_at")
    val lastCheckedAt: Long
)
