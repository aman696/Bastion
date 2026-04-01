package com.aman.bastion.data.inapp.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inapp_signatures")
data class InAppSignatureEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "rule_id")
    val ruleId: String,

    @ColumnInfo(name = "screen_state")
    val screenState: String,

    @ColumnInfo(name = "tier")
    val tier: Int,

    @ColumnInfo(name = "match_type")
    val matchType: String,

    @ColumnInfo(name = "match_value")
    val matchValue: String,

    @ColumnInfo(name = "is_regex")
    val isRegex: Int,

    @ColumnInfo(name = "min_version_code")
    val minVersionCode: Int?,

    @ColumnInfo(name = "max_version_code")
    val maxVersionCode: Int?
)
