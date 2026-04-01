package com.aman.bastion.service.engine

import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import com.aman.bastion.data.url.dao.UrlRuleDao
import com.aman.bastion.service.BastionServiceBridge
import com.aman.bastion.service.NavigationCommand
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UrlBlockEngine @Inject constructor(
    private val urlRuleDao: UrlRuleDao
) {

    private var cachedPatterns: List<String> = emptyList()
    private var lastCacheRefreshMs: Long = 0L
    private var lastBlockedKey: String? = null
    private var lastBlockedAtMs: Long = 0L

    suspend fun evaluate(packageName: String, rootNode: AccessibilityNodeInfo) {
        try {
            refreshCacheIfNeeded()
            if (cachedPatterns.isEmpty()) return

            val currentUrl = extractCurrentUrl(packageName, rootNode) ?: return
            val normalizedUrl = normalizeValue(currentUrl)
            if (normalizedUrl.isBlank()) return

            val matchedPattern = cachedPatterns.firstOrNull { matches(normalizedUrl, it) } ?: return
            val key = "$packageName|$matchedPattern|$normalizedUrl"
            val now = System.currentTimeMillis()
            if (lastBlockedKey == key && now - lastBlockedAtMs < 1_500L) return

            lastBlockedKey = key
            lastBlockedAtMs = now
            BastionServiceBridge.navigationCommand.value = NavigationCommand.HOME
            BastionServiceBridge.foregroundPackage.value = null
        } finally {
            rootNode.recycle()
        }
    }

    private suspend fun refreshCacheIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastCacheRefreshMs > 10_000L) {
            cachedPatterns = urlRuleDao.getEnabledSync().map { it.pattern }
            lastCacheRefreshMs = now
        }
    }

    private fun extractCurrentUrl(packageName: String, rootNode: AccessibilityNodeInfo): String? {
        val knownIds = ADDRESS_BAR_IDS[packageName].orEmpty()
        for (viewId in knownIds) {
            val matches = rootNode.findAccessibilityNodeInfosByViewId(viewId)
            val value = matches.firstNotNullOfOrNull { node ->
                node.text?.toString()
                    ?: node.contentDescription?.toString()
                    ?: node.hintText?.toString()
            }
            matches.forEach { it.recycle() }
            if (!value.isNullOrBlank()) return value
        }
        return findPotentialUrl(rootNode)
    }

    private fun findPotentialUrl(rootNode: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val viewId = node.viewIdResourceName?.lowercase().orEmpty()
            val candidates = listOfNotNull(
                node.text?.toString(),
                node.contentDescription?.toString(),
                node.hintText?.toString()
            )
            val looksLikeAddressBar = node.isEditable ||
                viewId.contains("url") ||
                viewId.contains("address") ||
                viewId.contains("location")
            if (looksLikeAddressBar) {
                val possibleUrl = candidates.firstOrNull(::looksLikeUrl)
                if (possibleUrl != null) return possibleUrl
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::add)
            }
        }

        return null
    }

    private fun looksLikeUrl(value: String): Boolean {
        val trimmed = value.trim().lowercase()
        return trimmed.startsWith("http://") ||
            trimmed.startsWith("https://") ||
            trimmed.startsWith("www.") ||
            (trimmed.contains('.') && !trimmed.contains(' '))
    }

    private fun matches(normalizedUrl: String, pattern: String): Boolean {
        if (normalizedUrl.contains(pattern)) return true
        val host = Uri.parse("https://$normalizedUrl").host
            ?.removePrefix("www.")
            ?.lowercase()
            ?: return false
        return host == pattern || host.endsWith(".$pattern")
    }

    private fun normalizeValue(raw: String): String {
        return raw.trim()
            .lowercase()
            .substringBefore(' ')
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trim('/')
            .substringBefore('?')
            .substringBefore('#')
    }

    companion object {
        val SUPPORTED_BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "com.brave.browser",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "com.microsoft.emmx",
            "com.sec.android.app.sbrowser",
            "com.sec.android.app.sbrowser.beta",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.kiwibrowser.browser",
            "com.vivaldi.browser"
        )

        private val ADDRESS_BAR_IDS = mapOf(
            "com.android.chrome" to listOf(
                "com.android.chrome:id/url_bar",
                "com.android.chrome:id/search_box_text"
            ),
            "com.chrome.beta" to listOf(
                "com.chrome.beta:id/url_bar",
                "com.chrome.beta:id/search_box_text"
            ),
            "com.chrome.dev" to listOf(
                "com.chrome.dev:id/url_bar",
                "com.chrome.dev:id/search_box_text"
            ),
            "com.chrome.canary" to listOf(
                "com.chrome.canary:id/url_bar",
                "com.chrome.canary:id/search_box_text"
            ),
            "com.brave.browser" to listOf("com.brave.browser:id/url_bar"),
            "org.mozilla.firefox" to listOf(
                "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
                "org.mozilla.firefox:id/mozac_browser_toolbar_edit_url_view"
            ),
            "org.mozilla.firefox_beta" to listOf(
                "org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view",
                "org.mozilla.firefox_beta:id/mozac_browser_toolbar_edit_url_view"
            ),
            "com.microsoft.emmx" to listOf("com.microsoft.emmx:id/url_bar"),
            "com.sec.android.app.sbrowser" to listOf(
                "com.sec.android.app.sbrowser:id/location_bar_edit_text"
            ),
            "com.sec.android.app.sbrowser.beta" to listOf(
                "com.sec.android.app.sbrowser.beta:id/location_bar_edit_text"
            ),
            "com.opera.browser" to listOf("com.opera.browser:id/url_field"),
            "com.opera.mini.native" to listOf("com.opera.mini.native:id/url_field"),
            "com.kiwibrowser.browser" to listOf("com.kiwibrowser.browser:id/url_bar"),
            "com.vivaldi.browser" to listOf("com.vivaldi.browser:id/url_bar")
        )
    }
}
