package com.aman.bastion.service

import android.graphics.Rect
import kotlinx.coroutines.flow.MutableStateFlow

object BastionServiceBridge {
    val foregroundPackage = MutableStateFlow<String?>(null)
    val inAppScreenState = MutableStateFlow<InAppScreenState?>(null)
    val strictModeActive = MutableStateFlow(false)
    val a11yRunning = MutableStateFlow(false)
    val settingsThreatDetected = MutableStateFlow(false)
    val signatureCacheInvalidated = MutableStateFlow(false)
    val injectBackNavigation = MutableStateFlow(false)
    val navigationCommand = MutableStateFlow<NavigationCommand?>(null)
}

enum class NavigationCommand {
    HOME,
    BACK,
    BACK_THEN_HOME,
    BACK_TO_APP_HOME,
    INSTAGRAM_OPEN_DM,
    INSTAGRAM_GO_HOME_THEN_EXIT
}

enum class InAppBlockAction {
    NONE,
    AUTO,
    BACK,
    FULL_OVERLAY,
    BOUNDED_OVERLAY
}

data class InAppScreenState(
    val packageName: String,
    val screenState: String,
    val featureId: String? = null,
    val blockAction: InAppBlockAction = InAppBlockAction.AUTO,
    val overlayBounds: List<Rect> = emptyList(),
    val touchBlockBounds: List<Rect> = emptyList(),
    val isExcludedFromLimit: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)
