package com.aman.bastion.domain.model

data class AppRule(
    val packageName: String,
    val dailyLimitMs: Long,
    val isHardBlocked: Boolean,
    val categoryId: String?,
    val createdAt: Long
)
