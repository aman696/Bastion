package com.aman.bastion.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.aman.bastion.service.engine.InAppDetectionEngine
import com.aman.bastion.service.engine.SettingsThreatDetector
import com.aman.bastion.service.engine.UrlBlockEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BastionA11yService : AccessibilityService() {

    @Inject
    lateinit var inAppDetectionEngine: InAppDetectionEngine

    @Inject
    lateinit var settingsThreatDetector: SettingsThreatDetector

    @Inject
    lateinit var urlBlockEngine: UrlBlockEngine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.samsung.android.settings",
            "com.miui.securitycenter",
            "com.oppo.settings",
            "com.oneplus.settings",
            "com.huawei.systemmanager"
        )

        val UNINSTALL_PACKAGES = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.samsung.android.packageinstaller"
        )

        const val PLAYSTORE_PACKAGE = "com.android.vending"

        val MONITORED_APP_PACKAGES = setOf(
            "com.instagram.android",
            "com.google.android.youtube"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        BastionServiceBridge.a11yRunning.value = true
        serviceScope.launch {
            BastionServiceBridge.navigationCommand.collect { command ->
                when (command) {
                    NavigationCommand.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
                    NavigationCommand.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
                    null -> Unit
                }
                if (command != null) {
                    BastionServiceBridge.navigationCommand.value = null
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            BastionServiceBridge.foregroundPackage.value = pkg
        }

        if (BastionServiceBridge.injectBackNavigation.value) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            BastionServiceBridge.injectBackNavigation.value = false
            return
        }

        when {
            pkg in SETTINGS_PACKAGES ||
                pkg in UNINSTALL_PACKAGES ||
                pkg == PLAYSTORE_PACKAGE ->
                settingsThreatDetector.evaluate(pkg, event)

            pkg in UrlBlockEngine.SUPPORTED_BROWSER_PACKAGES -> {
                val rootNode = rootInActiveWindow ?: event.source ?: return
                serviceScope.launch {
                    urlBlockEngine.evaluate(pkg, rootNode)
                }
            }

            pkg in MONITORED_APP_PACKAGES -> {
                // Extract root node on the binder thread BEFORE launching —
                // the event is recycled after onAccessibilityEvent returns,
                // so event.source would return null inside the coroutine.
                val rootNode = rootInActiveWindow ?: event.source ?: return
                serviceScope.launch {
                    inAppDetectionEngine.refreshCacheIfNeeded(pkg)
                    inAppDetectionEngine.evaluate(pkg, rootNode)
                }
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        BastionServiceBridge.a11yRunning.value = false
        serviceScope.cancel()
        return super.onUnbind(intent)
    }
}
