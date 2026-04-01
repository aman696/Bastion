package com.aman.bastion.domain.catalog

import com.aman.bastion.data.inapp.entity.InAppSignatureEntity

object SignatureCatalog {

    val ALL_SIGNATURES: List<InAppSignatureEntity> = listOf(
        InAppSignatureEntity(
            id = "ig_reels_v1_1",
            ruleId = "instagram_reels_block",
            screenState = "REELS",
            tier = 1,
            matchType = "VIEW_ID",
            matchValue = "com.instagram.android:id/root_clips_layout",
            isRegex = 0,
            minVersionCode = null,
            maxVersionCode = null
        ),
        InAppSignatureEntity(
            id = "ig_reels_v1_2",
            ruleId = "instagram_reels_block",
            screenState = "REELS",
            tier = 2,
            matchType = "CONTENT_DESC",
            matchValue = "Reels",
            isRegex = 0,
            minVersionCode = null,
            maxVersionCode = null
        ),
        InAppSignatureEntity(
            id = "ig_dm_v1_1",
            ruleId = "instagram_dm_allow",
            screenState = "DM",
            tier = 1,
            matchType = "VIEW_ID",
            matchValue = "com.instagram.android:id/direct_inbox_action_bar",
            isRegex = 0,
            minVersionCode = null,
            maxVersionCode = null
        ),
        InAppSignatureEntity(
            id = "ig_dm_v1_2",
            ruleId = "instagram_dm_allow",
            screenState = "DM",
            tier = 2,
            matchType = "CONTENT_DESC",
            matchValue = "Direct",
            isRegex = 0,
            minVersionCode = null,
            maxVersionCode = null
        ),
        InAppSignatureEntity(
            id = "yt_shorts_v1_1",
            ruleId = "youtube_shorts_block",
            screenState = "SHORTS",
            tier = 1,
            matchType = "VIEW_ID",
            matchValue = "com.google.android.youtube:id/reel_watch_fragment_root",
            isRegex = 0,
            minVersionCode = null,
            maxVersionCode = null
        ),
        InAppSignatureEntity(
            id = "yt_shorts_v1_2",
            ruleId = "youtube_shorts_block",
            screenState = "SHORTS",
            tier = 2,
            matchType = "CONTENT_DESC",
            matchValue = "Shorts",
            isRegex = 0,
            minVersionCode = null,
            maxVersionCode = null
        ),
        InAppSignatureEntity(
            id = "yt_shorts_v1_3",
            ruleId = "youtube_shorts_block",
            screenState = "SHORTS",
            tier = 1,
            matchType = "VIEW_ID",
            matchValue = "com.google.android.youtube:id/reel_watch_fragment_root",
            isRegex = 0,
            minVersionCode = null,
            maxVersionCode = null
        ),
        InAppSignatureEntity(
            id = "yt_home_v1_1",
            ruleId = "youtube_home_block",
            screenState = "HOME_FEED",
            tier = 1,
            matchType = "VIEW_ID",
            matchValue = "com.google.android.youtube:id/browse_fragment_layout_coordinator_layout",
            isRegex = 0,
            minVersionCode = null,
            maxVersionCode = null
        ),
        InAppSignatureEntity(
            id = "yt_home_v1_2",
            ruleId = "youtube_home_block",
            screenState = "HOME_FEED",
            tier = 2,
            matchType = "CONTENT_DESC",
            matchValue = "Home",
            isRegex = 0,
            minVersionCode = null,
            maxVersionCode = null
        ),
        InAppSignatureEntity(
            id = "yt_explore_v1_1",
            ruleId = "youtube_explore_block",
            screenState = "EXPLORE",
            tier = 2,
            matchType = "CONTENT_DESC",
            matchValue = "Explore",
            isRegex = 0,
            minVersionCode = null,
            maxVersionCode = null
        )
    )
}
