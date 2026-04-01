package com.aman.bastion.domain.catalog

import com.aman.bastion.domain.model.RuleType

object InAppRuleCatalog {
    const val INSTAGRAM_GUARD_FEATURE_ID = "INSTAGRAM_GUARD"

    private val catalog: Map<String, List<InAppFeature>> = mapOf(
        "com.instagram.android" to listOf(
            InAppFeature(
                packageName = "com.instagram.android",
                featureId = INSTAGRAM_GUARD_FEATURE_ID,
                displayName = "Instagram Guard",
                isBlockable = true,
                isExcludedFromLimit = false,
                shortLabel = "IG GUARD",
                description = "One switch for Instagram: blocks Reels and Explore while still allowing Home, normal DMs, and shared DM reels.",
                ruleType = RuleType.OVERLAY_BLOCK
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
