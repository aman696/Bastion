package com.aman.bastion.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
import kotlinx.coroutines.delay
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

    // Single-threaded scope for monitored-app detection: ensures accessibility events are
    // published in the order they arrive, preventing a stale null (from a transition frame)
    // from overwriting a freshly-published HOME/REELS state that ran faster in parallel.
    private val serialDetectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

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
                    NavigationCommand.BACK_THEN_HOME -> {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        delay(180)
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                    NavigationCommand.BACK_TO_APP_HOME -> performGlobalAction(GLOBAL_ACTION_BACK)
                    NavigationCommand.INSTAGRAM_OPEN_DM -> {
                        inAppDetectionEngine.resetInstagramHomeScrollBudget()
                        openInstagramDmInbox()
                    }
                    NavigationCommand.INSTAGRAM_GO_HOME_THEN_EXIT -> {
                        returnInstagramToHome()
                    }
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

        if (pkg != "com.instagram.android") {
            inAppDetectionEngine.onForegroundPackageChanged(pkg)
        }

        if (pkg == "com.instagram.android") {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                BastionServiceBridge.foregroundPackage.value = pkg
            }
        } else if (pkg in MONITORED_APP_PACKAGES) {
            BastionServiceBridge.foregroundPackage.value = pkg
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
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

            urlBlockEngine.supportsPackage(pkg) -> {
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
                if (pkg == "com.instagram.android") {
                    serviceScope.launch {
                        inAppDetectionEngine.refreshCacheIfNeeded(pkg)
                        inAppDetectionEngine.evaluate(pkg, rootNode)
                    }
                } else {
                    serialDetectionScope.launch {
                        inAppDetectionEngine.refreshCacheIfNeeded(pkg)
                        inAppDetectionEngine.evaluate(pkg, rootNode, event.eventType)
                    }
                }
            }

            else -> Unit
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        BastionServiceBridge.a11yRunning.value = false
        serviceScope.cancel()
        serialDetectionScope.cancel()
        return super.onUnbind(intent)
    }

    private suspend fun openInstagramDmInbox() {
        if (isInstagramInboxVisible()) return

        if (tapInstagramDmEntryPoint()) {
            return
        }

        performGlobalAction(GLOBAL_ACTION_BACK)
        delay(240)

        if (isInstagramInboxVisible() || tapInstagramDmEntryPoint()) {
            return
        }

        findClickableNodeByViewId("com.instagram.android:id/feed_tab")?.let { node ->
            performClick(node)
            delay(180)
        }

        tapInstagramDmEntryPoint()
    }

    private suspend fun returnInstagramToHome() {
        BastionServiceBridge.inAppScreenState.value = null

        if (isInstagramHomeVisible()) {
            return
        }

        if (tapInstagramHomeEntryPoint()) {
            delay(220)
            return
        }

        if (!isInstagramHomeVisible()) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(220)
            if (!isInstagramHomeVisible()) {
                tapInstagramHomeEntryPoint()
            }
        }
    }

    private fun isInstagramHomeVisible(): Boolean {
        val root = rootInActiveWindow ?: return false
        return hasViewId(root, "com.instagram.android:id/main_feed_action_bar") ||
            hasViewId(root, "com.instagram.android:id/reels_tray_container") ||
            isTabSelected(root, "com.instagram.android:id/feed_tab")
    }

    private fun findClickableNodeByViewId(viewId: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val matches = root.findAccessibilityNodeInfosByViewId(viewId)
        val match = matches.firstOrNull()
        matches.drop(1).forEach { it.recycle() }
        return match
    }

    private fun findClickableNodeByContentDescription(vararg patterns: Regex): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return breadthFirstSearch(root) { node ->
            val description = node.contentDescription?.toString() ?: return@breadthFirstSearch false
            node.isVisibleToUser &&
                node.isClickable &&
                patterns.any { pattern -> pattern.containsMatchIn(description) }
        }
    }

    private fun isInstagramInboxVisible(): Boolean {
        val root = rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByViewId(
            "com.instagram.android:id/direct_inbox_action_bar"
        )
        val isVisible = matches.isNotEmpty()
        matches.forEach { it.recycle() }
        return isVisible
    }

    private fun tapInstagramDmEntryPoint(): Boolean {
        val directTab = findClickableNodeByViewId("com.instagram.android:id/direct_tab")
            ?: findClickableNodeByContentDescription(
                Regex("messages?", RegexOption.IGNORE_CASE),
                Regex("direct", RegexOption.IGNORE_CASE),
                Regex("messenger", RegexOption.IGNORE_CASE),
                Regex("inbox", RegexOption.IGNORE_CASE)
            )

        if (directTab != null) {
            performClick(directTab)
            return true
        }

        return false
    }

    private fun tapInstagramHomeEntryPoint(): Boolean {
        val homeTab = findClickableNodeByViewId("com.instagram.android:id/feed_tab")
            ?: findClickableNodeByContentDescription(
                Regex("^home$", RegexOption.IGNORE_CASE),
                Regex("home tab", RegexOption.IGNORE_CASE),
                Regex("feed", RegexOption.IGNORE_CASE)
            )

        if (homeTab != null) {
            performClick(homeTab)
            return true
        }

        return false
    }

    private fun hasViewId(root: AccessibilityNodeInfo, viewId: String): Boolean {
        val matches = root.findAccessibilityNodeInfosByViewId(viewId)
        val exists = matches.isNotEmpty()
        matches.forEach { it.recycle() }
        return exists
    }

    private fun isTabSelected(root: AccessibilityNodeInfo, viewId: String): Boolean {
        val matches = root.findAccessibilityNodeInfosByViewId(viewId)
        val selected = matches.any(::nodeOrDescendantIsSelected)
        matches.forEach { it.recycle() }
        return selected
    }

    private fun nodeOrDescendantIsSelected(node: AccessibilityNodeInfo?): Boolean {
        node ?: return false
        if (node.isSelected || node.isChecked) return true
        for (index in 0 until node.childCount) {
            if (nodeOrDescendantIsSelected(node.getChild(index))) {
                return true
            }
        }
        return false
    }

    private fun performClick(node: AccessibilityNodeInfo) {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return
            }
            current = current.parent
        }
    }

    private fun breadthFirstSearch(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (predicate(node)) {
                return node
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::add)
            }
        }
        return null
    }
}
