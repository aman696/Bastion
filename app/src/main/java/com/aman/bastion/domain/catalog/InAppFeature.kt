package com.aman.bastion.domain.catalog

import com.aman.bastion.domain.model.RuleType

data class InAppFeature(
    val packageName: String,
    val featureId: String,
    val displayName: String,
    val isBlockable: Boolean,
    val isExcludedFromLimit: Boolean,
    val shortLabel: String,   // used in badge: "$shortLabel BLOCKED"
    val description: String,
    val ruleType: RuleType
)
