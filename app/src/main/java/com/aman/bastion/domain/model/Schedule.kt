package com.aman.bastion.domain.model

enum class BlockType { HARD, SOFT }

data class Schedule(
    val id: String,
    val name: String,
    val targetPackages: List<String>,
    val targetCategoryIds: List<String>,
    val startTimeMinutes: Int,
    val endTimeMinutes: Int,
    val daysOfWeekBitmask: Int,
    val blockType: BlockType,
    val isActive: Boolean
)
