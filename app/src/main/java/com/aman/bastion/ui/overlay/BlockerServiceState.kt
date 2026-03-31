package com.aman.bastion.ui.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton contract between BlockerForegroundService (writer)
 * and BlockOverlayContent (reader).
 * The overlay composable must NOT depend on a ViewModel — it reads this directly.
 */
object BlockerServiceState {
    private val _overlay = MutableStateFlow<OverlayState>(OverlayState.Hidden)
    val overlay: StateFlow<OverlayState> = _overlay.asStateFlow()

    fun show(state: OverlayState) { _overlay.value = state }
    fun hide() { _overlay.value = OverlayState.Hidden }
}
