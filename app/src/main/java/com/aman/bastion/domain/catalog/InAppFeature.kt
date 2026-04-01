package com.aman.bastion.domain.catalog

import com.aman.bastion.domain.model.RuleType

data class InAppFeature(
    val featureId: String,
    val displayName: String,
    val shortLabel: String,   // used in badge: "$shortLabel BLOCKED"
    val description: String,
    val ruleType: RuleType
)
