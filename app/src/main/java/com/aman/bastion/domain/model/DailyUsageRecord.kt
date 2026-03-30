package com.aman.bastion.domain.model

data class DailyUsageRecord(
    val packageName: String,
    val date: String,
    val elapsedMs: Long,
    val exclusionMs: Long
)
