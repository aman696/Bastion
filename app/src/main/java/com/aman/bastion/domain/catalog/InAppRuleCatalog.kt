package com.aman.bastion.domain.catalog

import com.aman.bastion.domain.model.RuleType

object InAppRuleCatalog {

    private val catalog: Map<String, List<InAppFeature>> = mapOf(
        "com.instagram.android" to listOf(
            InAppFeature(
                featureId   = "instagram_reels",
                displayName = "Block Reels feed",
                shortLabel  = "REELS",
                description = "Overlay blocks the Reels tab. DMs remain accessible.",
                ruleType    = RuleType.OVERLAY_BLOCK
            ),
            InAppFeature(
                featureId   = "instagram_explore",
                displayName = "Block Explore feed",
                shortLabel  = "EXPLORE",
                description = "Overlay blocks the Explore tab.",
                ruleType    = RuleType.OVERLAY_BLOCK
            ),
            InAppFeature(
                featureId   = "instagram_dm_exclusion",
                displayName = "Exclude DM time from limit",
                shortLabel  = "DM EXCLUSION",
                description = "Time spent in DMs won't count toward your Instagram daily limit.",
                ruleType    = RuleType.NAVIGATION_INTERCEPT
            )
        ),
        "com.zhiliaoapp.musically" to listOf(
            InAppFeature(
                featureId   = "tiktok_fyp",
                displayName = "Block For You feed",
                shortLabel  = "FOR YOU",
                description = "Overlay blocks the main TikTok feed.",
                ruleType    = RuleType.OVERLAY_BLOCK
            )
        ),
        "com.google.android.youtube" to listOf(
            InAppFeature(
                featureId   = "youtube_shorts",
                displayName = "Block Shorts",
                shortLabel  = "SHORTS",
                description = "Overlay blocks the YouTube Shorts tab.",
                ruleType    = RuleType.OVERLAY_BLOCK
            )
        )
    )

    fun featuresFor(packageName: String): List<InAppFeature> =
        catalog[packageName] ?: emptyList()

    fun hasFeatures(packageName: String): Boolean =
        catalog.containsKey(packageName)
}
