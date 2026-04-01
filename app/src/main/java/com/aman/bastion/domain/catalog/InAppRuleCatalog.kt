package com.aman.bastion.domain.catalog

import com.aman.bastion.domain.model.RuleType

object InAppRuleCatalog {

    private val catalog: Map<String, List<InAppFeature>> = mapOf(
        "com.instagram.android" to listOf(
            InAppFeature(
                packageName = "com.instagram.android",
                featureId = "REELS",
                displayName = "Reels & Explore",
                isBlockable = true,
                isExcludedFromLimit = false,
                shortLabel = "REELS",
                description = "Blocks the Reels tab, Explore feed, and inline reel/video content across all surfaces.",
                ruleType = RuleType.OVERLAY_BLOCK
            ),
            InAppFeature(
                packageName = "com.instagram.android",
                featureId = "DM",
                displayName = "Direct Messages",
                isBlockable = false,
                isExcludedFromLimit = true,
                shortLabel = "DM",
                description = "Time spent in Instagram DMs, including shared DM reels, won't count toward your daily limit.",
                ruleType = RuleType.NAVIGATION_INTERCEPT
            )
        ),
        "com.google.android.youtube" to listOf(
            InAppFeature(
                packageName = "com.google.android.youtube",
                featureId = "SHORTS",
                displayName = "Shorts",
                isBlockable = true,
                isExcludedFromLimit = false,
                shortLabel = "SHORTS",
                description = "Detects YouTube Shorts structurally and backs out immediately.",
                ruleType = RuleType.OVERLAY_BLOCK
            ),
            InAppFeature(
                packageName = "com.google.android.youtube",
                featureId = "HOME_FEED",
                displayName = "Home Feed",
                isBlockable = true,
                isExcludedFromLimit = false,
                shortLabel = "HOME",
                description = "Overlay blocks the YouTube home feed.",
                ruleType = RuleType.OVERLAY_BLOCK
            ),
            InAppFeature(
                packageName = "com.google.android.youtube",
                featureId = "EXPLORE",
                displayName = "Explore",
                isBlockable = true,
                isExcludedFromLimit = false,
                shortLabel = "EXPLORE",
                description = "Overlay blocks the YouTube Explore tab.",
                ruleType = RuleType.OVERLAY_BLOCK
            )
        )
    )

    fun featuresFor(packageName: String): List<InAppFeature> =
        catalog[packageName] ?: emptyList()

    fun hasFeatures(packageName: String): Boolean =
        catalog.containsKey(packageName)
}
