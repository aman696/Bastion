package com.aman.bastion.ui.overlay

import com.aman.bastion.domain.model.UnlockCondition

sealed class OverlayState {
    object Hidden : OverlayState()

    data class Soft(
        val featureName: String,       // e.g. "Reels"
        val appName: String,
        val packageName: String,
        val redirectLabel: String?,    // e.g. "Open DMs instead →"
        val redirectPackage: String?,
        val unlockCondition: UnlockCondition
    ) : OverlayState()

    data class Hardcore(
        val featureName: String,
        val appName: String,
        val untilMs: Long
    ) : OverlayState()
}
