package com.aman.bastion.domain.model

enum class UnlockCondition {
    COMPLETE_TASK,
    WAIT_10_MIN,
    DEEP_BREATHS,
    STEP_GOAL;

    fun displayLabel(): String = when (this) {
        COMPLETE_TASK -> "Complete a task"
        WAIT_10_MIN   -> "10 min wait"
        DEEP_BREATHS  -> "5 deep breaths"
        STEP_GOAL     -> "Step goal"
    }
}
