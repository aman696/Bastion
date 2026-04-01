package com.aman.bastion.service.engine

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.aman.bastion.data.inapp.dao.InAppRuleDao
import com.aman.bastion.data.inapp.dao.InAppSignatureDao
import com.aman.bastion.data.inapp.entity.InAppSignatureEntity
import com.aman.bastion.service.BastionServiceBridge
import com.aman.bastion.service.InAppBlockAction
import com.aman.bastion.service.InAppScreenState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InAppDetectionEngine @Inject constructor(
    private val inAppSignatureDao: InAppSignatureDao,
    private val inAppRuleDao: InAppRuleDao
) {

    private data class DetectionResult(
        val screenState: String,
        val featureId: String? = screenState,
        val blockAction: InAppBlockAction = InAppBlockAction.AUTO,
        val overlayBounds: List<Rect> = emptyList(),
        val isExcludedFromLimit: Boolean = false
    )

    private val signatureCache = mutableMapOf<String, List<InAppSignatureEntity>>()
    private val enabledFeatureCache = mutableMapOf<String, Set<String>>()
    private var lastCacheRefreshMs = 0L

    suspend fun refreshCacheIfNeeded(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastCacheRefreshMs > 30 * 60 * 1_000L || !signatureCache.containsKey(packageName)) {
            signatureCache[packageName] = inAppSignatureDao.getByPackage(packageName)
            enabledFeatureCache[packageName] = inAppRuleDao.getByPackageSync(packageName)
                .filter { it.isEnabled }
                .map { it.featureId }
                .toSet()
            lastCacheRefreshMs = now
        }
    }

    fun evaluate(packageName: String, rootNode: AccessibilityNodeInfo) {
        try {
            val detectedState = when (packageName) {
                INSTAGRAM_PACKAGE -> detectInstagramState(rootNode)
                    ?: detectScreenState(rootNode, signatureCache[packageName])

                YOUTUBE_PACKAGE -> detectYoutubeState(rootNode)
                    ?: detectScreenState(rootNode, signatureCache[packageName])

                else -> detectScreenState(rootNode, signatureCache[packageName])
            }

            publishState(
                packageName,
                filterDetection(packageName, detectedState, enabledFeatureCache[packageName].orEmpty())
            )
        } finally {
            rootNode.recycle()
        }
    }

    private fun filterDetection(
        packageName: String,
        detection: DetectionResult?,
        enabledFeatures: Set<String>
    ): DetectionResult? {
        detection ?: return null
        if (enabledFeatures.isEmpty()) return null

        val aliases = featureAliasesFor(packageName, detection)
        if (aliases.none(enabledFeatures::contains)) {
            return null
        }

        return detection.copy(
            isExcludedFromLimit = packageName == INSTAGRAM_PACKAGE &&
                detection.screenState in setOf("DM", "DM_REEL_EXCEPTION") &&
                "DM" in enabledFeatures
        )
    }

    private fun publishState(packageName: String, detection: DetectionResult?) {
        val current = BastionServiceBridge.inAppScreenState.value
        if (detection == null) {
            if (current?.packageName == packageName) {
                BastionServiceBridge.inAppScreenState.value = null
            }
            return
        }

        val nextState = InAppScreenState(
            packageName = packageName,
            screenState = detection.screenState,
            featureId = detection.featureId,
            blockAction = detection.blockAction,
            overlayBounds = detection.overlayBounds,
            isExcludedFromLimit = detection.isExcludedFromLimit
        )

        val isSameState = current?.packageName == nextState.packageName &&
            current?.screenState == nextState.screenState &&
            current?.featureId == nextState.featureId &&
            current?.blockAction == nextState.blockAction &&
            current?.overlayBounds == nextState.overlayBounds &&
            current?.isExcludedFromLimit == nextState.isExcludedFromLimit

        if (!isSameState) {
            BastionServiceBridge.inAppScreenState.value = nextState
        }
    }

    private fun detectScreenState(
        root: AccessibilityNodeInfo,
        signatures: List<InAppSignatureEntity>?
    ): DetectionResult? {
        if (signatures.isNullOrEmpty()) return null

        for (tier in 1..4) {
            for (sig in signatures.filter { it.tier == tier }) {
                val match: AccessibilityNodeInfo? = when (sig.matchType) {
                    "VIEW_ID" -> findFirstByViewId(root, sig.matchValue)
                    "CONTENT_DESC" -> {
                        if (sig.isRegex == 1) {
                            findNodeByContentDescRegex(root, sig.matchValue)
                        } else {
                            findNodeByContentDescExact(root, sig.matchValue)
                        }
                    }

                    "TEXT" -> findNodeByText(root, sig.matchValue)
                    "CHILD_INDEX" -> findBottomNavChildByIndex(root, sig.matchValue.toInt())
                    else -> null
                }

                if (match != null) {
                    match.recycle()
                    return DetectionResult(
                        screenState = sig.screenState,
                        featureId = sig.screenState,
                        blockAction = InAppBlockAction.AUTO
                    )
                }
            }
        }
        return null
    }

    private fun detectYoutubeState(root: AccessibilityNodeInfo): DetectionResult? {
        val isShorts = hasViewId(root, "com.google.android.youtube:id/reel_watch_fragment_root") ||
            hasSelectedContentDescription(root, "Shorts")
        if (isShorts) {
            return DetectionResult(
                screenState = "SHORTS",
                featureId = "SHORTS",
                blockAction = InAppBlockAction.BACK
            )
        }

        val isHomeFeed = hasViewId(
            root,
            "com.google.android.youtube:id/browse_fragment_layout_coordinator_layout"
        ) &&
            hasViewId(root, "com.google.android.youtube:id/results") &&
            hasSelectedContentDescription(root, "Home")

        if (isHomeFeed) {
            return DetectionResult(
                screenState = "HOME_FEED",
                featureId = "HOME_FEED",
                blockAction = InAppBlockAction.FULL_OVERLAY
            )
        }

        return null
    }

    private fun detectInstagramState(root: AccessibilityNodeInfo): DetectionResult? {
        val hasClips = hasViewId(root, "com.instagram.android:id/root_clips_layout")
        if (hasClips) {
            val hasDmBar = hasViewId(root, "com.instagram.android:id/reel_viewer_message_composer")
            val hasSender = hasViewId(root, "com.instagram.android:id/sender_username_or_fullname")

            if (hasDmBar || hasSender) {
                return DetectionResult(
                    screenState = "DM_REEL_EXCEPTION",
                    featureId = "REELS",
                    blockAction = InAppBlockAction.NONE,
                    isExcludedFromLimit = true
                )
            }

            return DetectionResult(
                screenState = "REELS",
                featureId = "REELS",
                blockAction = InAppBlockAction.FULL_OVERLAY
            )
        }

        if (hasViewId(root, "com.instagram.android:id/direct_inbox_action_bar")) {
            return DetectionResult(
                screenState = "DM",
                featureId = "DM",
                blockAction = InAppBlockAction.NONE,
                isExcludedFromLimit = true
            )
        }

        val hasExplore = hasViewId(root, "com.instagram.android:id/explore_action_bar") &&
            isSelectedViewId(root, "com.instagram.android:id/search_tab")
        if (hasExplore) {
            return DetectionResult(
                screenState = "EXPLORE",
                featureId = "EXPLORE",
                blockAction = InAppBlockAction.FULL_OVERLAY
            )
        }

        val hasHome = hasViewId(root, "com.instagram.android:id/main_feed_action_bar")
        if (hasHome) {
            val videoBounds = findVisibleBoundsByViewIds(
                root,
                listOf(
                    "com.instagram.android:id/video_container",
                    "com.instagram.android:id/clips_video_container"
                )
            )
            val hasVisibleVideoPost = hasVisibleContentDescRegex(
                root,
                ".*posted a (video|reel).*"
            )
            val fallbackBounds = if (videoBounds.isEmpty() && hasVisibleVideoPost) {
                findVisibleBoundsByViewIds(
                    root,
                    listOf(
                        "com.instagram.android:id/media_group",
                        "com.instagram.android:id/video_container",
                        "com.instagram.android:id/clips_video_container"
                    )
                )
            } else {
                emptyList()
            }
            val targetBounds = (videoBounds + fallbackBounds)
                .normalizeBounds()
                .take(MAX_HOME_REEL_BOUNDS)
            if (targetBounds.isNotEmpty()) {
                return DetectionResult(
                    screenState = "HOME",
                    featureId = "HOME",
                    blockAction = InAppBlockAction.BOUNDED_OVERLAY,
                    overlayBounds = targetBounds
                )
            }
        }

        return null
    }

    private fun findFirstByViewId(
        root: AccessibilityNodeInfo,
        viewId: String
    ): AccessibilityNodeInfo? {
        val matches = root.findAccessibilityNodeInfosByViewId(viewId)
        if (matches.isEmpty()) return null
        val first = matches.first()
        matches.drop(1).forEach { it.recycle() }
        return first
    }

    private fun hasViewId(root: AccessibilityNodeInfo, viewId: String): Boolean {
        val matches = root.findAccessibilityNodeInfosByViewId(viewId)
        val exists = matches.isNotEmpty()
        matches.forEach { it.recycle() }
        return exists
    }

    private fun isSelectedViewId(root: AccessibilityNodeInfo, viewId: String): Boolean {
        val matches = root.findAccessibilityNodeInfosByViewId(viewId)
        val isSelected = matches.any { it.isSelected || it.isChecked }
        matches.forEach { it.recycle() }
        return isSelected
    }

    private fun findVisibleBoundsByViewIds(
        root: AccessibilityNodeInfo,
        viewIds: List<String>
    ): List<Rect> {
        val collectedBounds = mutableListOf<Rect>()
        viewIds.forEach { viewId ->
            val matches = root.findAccessibilityNodeInfosByViewId(viewId)
            matches.forEach { node ->
                if (!node.isVisibleToUser) {
                    node.recycle()
                    return@forEach
                }
                val rect = Rect().also(node::getBoundsInScreen)
                if (!rect.isEmpty) {
                    collectedBounds += Rect(rect)
                }
                node.recycle()
            }
        }

        return collectedBounds
    }

    private fun findNodeByText(
        root: AccessibilityNodeInfo,
        text: String
    ): AccessibilityNodeInfo? {
        val matches = root.findAccessibilityNodeInfosByText(text)
        if (matches.isEmpty()) return null
        val first = matches.first()
        matches.drop(1).forEach { it.recycle() }
        return first
    }

    private fun findNodeByContentDescExact(
        root: AccessibilityNodeInfo,
        contentDesc: String
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.contentDescription?.toString() == contentDesc &&
                (node.isSelected || node.isChecked)
            ) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return null
    }

    private fun findNodeByContentDescRegex(
        root: AccessibilityNodeInfo,
        pattern: String
    ): AccessibilityNodeInfo? {
        val regex = pattern.toRegex()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.contentDescription?.toString()?.let(regex::matches) == true &&
                (node.isSelected || node.isChecked)
            ) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return null
    }

    private fun hasVisibleContentDescRegex(
        root: AccessibilityNodeInfo,
        pattern: String
    ): Boolean {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val contentDesc = node.contentDescription?.toString()
            if (node.isVisibleToUser && contentDesc?.let(regex::matches) == true) {
                return true
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return false
    }

    private fun hasSelectedContentDescription(
        root: AccessibilityNodeInfo,
        contentDesc: String
    ): Boolean {
        val match = findNodeByContentDescExact(root, contentDesc) ?: return false
        match.recycle()
        return true
    }

    private fun findBottomNavChildByIndex(
        root: AccessibilityNodeInfo,
        index: Int
    ): AccessibilityNodeInfo? {
        val navBar = root.findAccessibilityNodeInfosByViewId(
            "com.instagram.android:id/tab_bar"
        ).firstOrNull() ?: return null
        return if (index < navBar.childCount) navBar.getChild(index) else null
    }

    fun invalidateCache() {
        signatureCache.clear()
        enabledFeatureCache.clear()
        lastCacheRefreshMs = 0L
    }

    private fun featureAliasesFor(
        packageName: String,
        detection: DetectionResult
    ): List<String> = when (packageName) {
        INSTAGRAM_PACKAGE -> when (detection.screenState) {
            "REELS" -> listOf("REELS")
            "EXPLORE" -> listOf("REELS", "EXPLORE")
            "HOME" -> listOf("REELS", "HOME")
            "DM" -> listOf("DM")
            "DM_REEL_EXCEPTION" -> listOf("REELS", "DM")
            else -> listOfNotNull(detection.featureId)
        }

        else -> listOfNotNull(detection.featureId)
    }

    private fun List<Rect>.normalizeBounds(): List<Rect> {
        val sorted = this
            .filter { !it.isEmpty }
            .map { rect ->
                Rect(
                    snapDown(rect.left),
                    snapDown(rect.top),
                    snapUp(rect.right),
                    snapUp(rect.bottom)
                )
            }
            .sortedByDescending { rect -> rect.width() * rect.height() }
        val unique = mutableListOf<Rect>()
        sorted.forEach { rect ->
            val overlapsExisting = unique.any { existing ->
                val intersection = Rect(existing)
                val intersects = intersection.intersect(rect)
                if (!intersects) return@any false
                val intersectionArea = intersection.width() * intersection.height()
                val smallerArea = minOf(
                    existing.width() * existing.height(),
                    rect.width() * rect.height()
                ).coerceAtLeast(1)
                intersectionArea >= (smallerArea * 0.85f).toInt()
            }
            if (!overlapsExisting) {
                unique += Rect(rect)
            }
        }
        return unique.sortedWith(compareBy<Rect> { it.top }.thenBy { it.left })
    }

    private fun snapDown(value: Int): Int = (value / BOUNDS_SNAP_STEP_PX) * BOUNDS_SNAP_STEP_PX

    private fun snapUp(value: Int): Int =
        ((value + BOUNDS_SNAP_STEP_PX - 1) / BOUNDS_SNAP_STEP_PX) * BOUNDS_SNAP_STEP_PX

    companion object {
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val MAX_HOME_REEL_BOUNDS = 4
        private const val BOUNDS_SNAP_STEP_PX = 16
    }
}
