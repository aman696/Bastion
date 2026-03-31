package com.aman.bastion.domain.model

data class AppRule(
    val packageName: String,
    val dailyLimitMs: Long,
    val isHardBlocked: Boolean,
    val categoryId: String?,
    val createdAt: Long,
    val hardcoreUntilMs: Long = 0L,
    val unlockCondition: UnlockCondition? = null,
    val blockNote: String? = null
) {
    val isHardcoreActive: Boolean
        get() = hardcoreUntilMs > System.currentTimeMillis()
}
