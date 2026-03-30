package com.aman.bastion.data.inapp.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inapp_rules")
data class InAppRuleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "feature_id")
    val featureId: String,

    @ColumnInfo(name = "rule_name")
    val ruleName: String,

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean,

    // "NAVIGATION_INTERCEPT" or "OVERLAY_BLOCK"
    @ColumnInfo(name = "rule_type")
    val ruleType: String
)
