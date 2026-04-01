package com.aman.bastion.service.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aman.bastion.R
import com.aman.bastion.service.BastionA11yService
import com.aman.bastion.service.BastionServiceBridge
import com.aman.bastion.service.OverlayEngine
import com.aman.bastion.service.OverlayType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class ThreatType {
    data object AppInfoPage : ThreatType()
    data object ForceStopVisible : ThreatType()
    data object UninstallDialog : ThreatType()
    data object A11ySettingsPage : ThreatType()
    data object PlayStoreUninstall : ThreatType()
    data object GenericSettings : ThreatType()
}

@Singleton
class SettingsThreatDetector @Inject constructor(
    private val overlayEngine: OverlayEngine,
    @ApplicationContext private val context: Context
) {

    private val ourAppLabel = context.getString(R.string.app_name)

    fun evaluate(pkg: String, event: AccessibilityEvent) {
        if (!BastionServiceBridge.strictModeActive.value) return

        val threat = when {
            pkg in BastionA11yService.UNINSTALL_PACKAGES ->
                detectUninstallDialogThreat(event)

            pkg == BastionA11yService.PLAYSTORE_PACKAGE ->
                detectPlayStoreThreat(event)

            pkg in BastionA11yService.SETTINGS_PACKAGES ->
                detectSettingsThreat(event)

            else -> null
        }

        threat?.let { executeThreatResponse(it) }
    }

    private fun detectSettingsThreat(event: AccessibilityEvent): ThreatType? {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return ThreatType.GenericSettings
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val root = try {
                event.source ?: return ThreatType.GenericSettings
            } catch (_: Exception) {
                return ThreatType.GenericSettings
            }
            val threat = inspectSettingsNodes(root)
            root.recycle()
            return threat ?: ThreatType.GenericSettings
        }

        return null
    }

    private fun inspectSettingsNodes(root: AccessibilityNodeInfo): ThreatType? {
        val appInfoNodes = root.findAccessibilityNodeInfosByText(ourAppLabel)
        if (appInfoNodes.isNotEmpty()) {
            val forceStopNodes = root.findAccessibilityNodeInfosByText("Force stop")
                .ifEmpty { root.findAccessibilityNodeInfosByText("Force Stop") }
            if (forceStopNodes.isNotEmpty()) {
                forceStopNodes.forEach { it.recycle() }
                appInfoNodes.forEach { it.recycle() }
                return ThreatType.ForceStopVisible
            }
            appInfoNodes.forEach { it.recycle() }
            return ThreatType.AppInfoPage
        }

        val a11yServiceNodes = root.findAccessibilityNodeInfosByText("Bastion")
        if (a11yServiceNodes.isNotEmpty()) {
            val classNameStr = root.className?.toString() ?: ""
            if (
                classNameStr.contains("accessibility", ignoreCase = true) ||
                root.findAccessibilityNodeInfosByViewId("com.android.settings:id/switch_widget")
                    .isNotEmpty()
            ) {
                a11yServiceNodes.forEach { it.recycle() }
                return ThreatType.A11ySettingsPage
            }
            a11yServiceNodes.forEach { it.recycle() }
        }

        return null
    }

    private fun detectUninstallDialogThreat(event: AccessibilityEvent): ThreatType? {
        if (
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return null

        val root = try {
            event.source ?: return ThreatType.UninstallDialog
        } catch (_: Exception) {
            return ThreatType.UninstallDialog
        }
        root.recycle()
        return ThreatType.UninstallDialog
    }

    private fun detectPlayStoreThreat(event: AccessibilityEvent): ThreatType? {
        if (
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return null

        val root = try {
            event.source ?: return null
        } catch (_: Exception) {
            return null
        }

        val uninstallNodes = root.findAccessibilityNodeInfosByText("Uninstall")
        val threat = if (uninstallNodes.isNotEmpty()) {
            val appNameNodes = root.findAccessibilityNodeInfosByText(ourAppLabel)
            if (appNameNodes.isNotEmpty()) {
                appNameNodes.forEach { it.recycle() }
                ThreatType.PlayStoreUninstall
            } else {
                null
            }
        } else {
            null
        }

        uninstallNodes.forEach { it.recycle() }
        root.recycle()
        return threat
    }

    private fun executeThreatResponse(threat: ThreatType) {
        when (threat) {
            is ThreatType.UninstallDialog,
            is ThreatType.PlayStoreUninstall -> {
                injectBackNavigation()
                Handler(Looper.getMainLooper()).postDelayed({
                    overlayEngine.showOverlay(
                        "anticircumvention",
                        OverlayType.AntiCircumvention
                    )
                }, 150)
            }

            is ThreatType.ForceStopVisible,
            is ThreatType.AppInfoPage,
            is ThreatType.A11ySettingsPage,
            is ThreatType.GenericSettings -> {
                overlayEngine.showOverlay(
                    "anticircumvention",
                    OverlayType.AntiCircumvention
                )
            }
        }
    }

    // Safe Mode (T-07): Completely bypasses all protections. Mitigated
    // only by re-engagement speed on next normal boot. User education
    // is the primary mitigation.
    // ADB uninstall (T-08): adb shell pm uninstall com.aman.bastion
    // bypasses everything. Only relevant to technical users with
    // USB debugging enabled. Inform user in onboarding to disable
    // USB debugging if using strict mode.
    // Play Store uninstall detection: Relies on "Uninstall" text
    // being visible alongside our app name. Play Store UI changes
    // will break this. Node inspection here is best-effort.
    // Device Admin: Does NOT prevent uninstall - only adds friction.
    // A determined user can deactivate admin from Settings >
    // Security > Device Admin in under 30 seconds. The psychological
    // friction is the entire value, not a hard technical block.
    // Back injection race condition: There is a ~150ms window between
    // back injection and overlay display where the uninstall dialog
    // may still be tappable. This is acceptable - the impulse
    // interruption goal is achieved by the friction, not a hard lock.
    private fun injectBackNavigation() {
        BastionServiceBridge.injectBackNavigation.value = true
    }
}
