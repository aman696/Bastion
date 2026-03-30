package com.aman.bastion.domain.model

enum class RuleType { NAVIGATION_INTERCEPT, OVERLAY_BLOCK }

data class InAppRule(
    val id: String,
    val packageName: String,
    val featureId: String,
    val ruleName: String,
    val isEnabled: Boolean,
    val ruleType: RuleType
)
