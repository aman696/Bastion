package com.aman.bastion.domain.model

data class AppCategory(
    val id: String,
    val name: String,
    val dailyLimitMs: Long,
    val colorHex: String
)
