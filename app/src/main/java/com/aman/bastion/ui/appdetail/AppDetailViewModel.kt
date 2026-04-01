package com.aman.bastion.ui.appdetail

import android.content.Intent
import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aman.bastion.domain.catalog.InAppFeature
import com.aman.bastion.domain.catalog.InAppRuleCatalog
import com.aman.bastion.domain.catalog.InAppRuleCatalog.INSTAGRAM_GUARD_FEATURE_ID
import com.aman.bastion.domain.model.AppRule
import com.aman.bastion.domain.model.InAppRule
import com.aman.bastion.domain.model.RuleType
import com.aman.bastion.domain.repository.AppRuleRepository
import com.aman.bastion.domain.repository.InAppRuleRepository
import com.aman.bastion.service.BastionServiceBridge
import com.aman.bastion.service.BlockerServiceStarter
import com.aman.bastion.service.DeviceAdminManager
import com.aman.bastion.service.StrictModeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BlockModeSelection { HARDCORE }
enum class ProtectionScopeSelection { FULL_APP, SELECTED_FEATURES }

data class FeatureRowState(
    val feature: InAppFeature,
    val isEnabled: Boolean,
    val ruleId: String?
)

data class AppDetailUiState(
    val appName: String                    = "",
    val icon: ImageBitmap?                 = null,
    val packageName: String                = "",
    val currentRule: AppRule?              = null,
    val featureRows: List<FeatureRowState> = emptyList(),
    val selectedScope: ProtectionScopeSelection = ProtectionScopeSelection.FULL_APP,
    val hasFeatureRestrictions: Boolean    = false,
    val selectedMode: BlockModeSelection   = BlockModeSelection.HARDCORE,
    val selectedDurationMs: Long           = 3_600_000L,
    val blockNote: String                  = "",
    val requiresAdminActivation: Boolean   = false,
    val pendingPostActivationMode: BlockModeSelection? = null,
    val isLoading: Boolean                 = true
)

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val ruleRepo: AppRuleRepository,
    private val inAppRuleRepo: InAppRuleRepository,
    private val strictModeManager: StrictModeManager,
    private val deviceAdminManager: DeviceAdminManager,
    @ApplicationContext private val context: Context,
    savedState: SavedStateHandle
) : ViewModel() {

    private val packageName: String = savedState["packageName"]!!

    private val _selectedMode     = MutableStateFlow(BlockModeSelection.HARDCORE)
    private val _selectedScope    = MutableStateFlow<ProtectionScopeSelection?>(null)
    private val _selectedDuration = MutableStateFlow(3_600_000L)
    private val _blockNote        = MutableStateFlow("")
    private val _pendingPostActivationMode = MutableStateFlow<BlockModeSelection?>(null)

    // Combine the two repo flows first to stay within the 5-arg limit
    private val _ruleAndInApp = combine(
        ruleRepo.getByPackage(packageName),
        inAppRuleRepo.getByPackage(packageName)
    ) { rule, inApp -> rule to inApp }

    private val _modeAndScope = combine(
        _selectedMode,
        _selectedScope
    ) { mode, scope ->
        mode to scope
    }

    private val _selectionState = combine(
        _modeAndScope,
        _selectedDuration,
        _blockNote,
        strictModeManager.requiresAdminActivation
    ) { modeAndScope, duration, note, requiresAdminActivation ->
        val (mode, scope) = modeAndScope
        SelectionUiState(
            mode = mode,
            scope = scope,
            duration = duration,
            note = note,
            requiresAdminActivation = requiresAdminActivation
        )
    }

    val uiState: StateFlow<AppDetailUiState> = combine(
        _ruleAndInApp,
        _selectionState,
        _pendingPostActivationMode
    ) { (rule, savedInApp), selection, pendingMode ->
        val pm      = context.packageManager
        val appInfo = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
        val appName = appInfo?.loadLabel(pm)?.toString() ?: packageName
        val icon    = appInfo?.loadIcon(pm)?.toBitmap()?.asImageBitmap()

        val features  = InAppRuleCatalog.featuresFor(packageName)
        val savedMap  = savedInApp.associateBy { it.featureId }
        val featureRows = features.map { feat ->
            val saved = savedMap[feat.featureId]
            val canonicalId = canonicalRuleId(packageName, feat.featureId)
            FeatureRowState(
                feature   = feat,
                isEnabled = isFeatureEnabledForRow(packageName, feat.featureId, savedInApp),
                ruleId    = saved?.id ?: canonicalId
            )
        }
        val hasFeatureRestrictions = featureRows.any { it.isEnabled }
        val derivedScope = selection.scope ?: when {
            featureRows.isEmpty() -> ProtectionScopeSelection.FULL_APP
            hasFeatureRestrictions -> ProtectionScopeSelection.SELECTED_FEATURES
            else -> ProtectionScopeSelection.FULL_APP
        }

        AppDetailUiState(
            appName            = appName,
            icon               = icon,
            packageName        = packageName,
            currentRule        = rule,
            featureRows        = featureRows,
            selectedScope      = derivedScope,
            hasFeatureRestrictions = hasFeatureRestrictions,
            selectedMode       = selection.mode,
            selectedDurationMs = selection.duration,
            blockNote          = selection.note,
            requiresAdminActivation = selection.requiresAdminActivation,
            pendingPostActivationMode = pendingMode,
            isLoading          = false
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppDetailUiState())

    fun onSelectMode(mode: BlockModeSelection)     { _selectedMode.value = mode }
    fun onSelectScope(scope: ProtectionScopeSelection) { _selectedScope.value = scope }
    fun onSelectDuration(ms: Long)                 { _selectedDuration.value = ms }
    fun onBlockNoteChange(note: String)            { _blockNote.value = note }

    fun onToggleFeatureRule(row: FeatureRowState) {
        viewModelScope.launch {
            _selectedScope.value = ProtectionScopeSelection.SELECTED_FEATURES
            if (packageName == "com.instagram.android" &&
                row.feature.featureId == INSTAGRAM_GUARD_FEATURE_ID
            ) {
                for (legacyRuleId in legacyRuleIdsFor(packageName, row.feature.featureId)) {
                    inAppRuleRepo.delete(legacyRuleId)
                }
                if (!row.isEnabled) {
                    inAppRuleRepo.save(
                        InAppRule(
                            id = canonicalRuleId(packageName, "REELS"),
                            packageName = packageName,
                            featureId = "REELS",
                            ruleName = "Reels & Explore",
                            isEnabled = true,
                            ruleType = RuleType.OVERLAY_BLOCK
                        )
                    )
                    inAppRuleRepo.save(
                        InAppRule(
                            id = canonicalRuleId(packageName, "DM"),
                            packageName = packageName,
                            featureId = "DM",
                            ruleName = "Direct Messages",
                            isEnabled = true,
                            ruleType = RuleType.NAVIGATION_INTERCEPT
                        )
                    )
                }
                BastionServiceBridge.signatureCacheInvalidated.value = true
                return@launch
            }

            val canonicalId = canonicalRuleId(packageName, row.feature.featureId)
            val rule = InAppRule(
                id          = canonicalId,
                packageName = packageName,
                featureId   = row.feature.featureId,
                ruleName    = row.feature.displayName,
                isEnabled   = !row.isEnabled,
                ruleType    = row.feature.ruleType
            )
            inAppRuleRepo.save(rule)
            legacyRuleIdsFor(packageName, row.feature.featureId)
                .filter { it != canonicalId }
                .forEach { legacyRuleId ->
                    inAppRuleRepo.delete(legacyRuleId)
                }
            if (row.ruleId != null && row.ruleId != canonicalId) {
                inAppRuleRepo.delete(row.ruleId)
            }
            BastionServiceBridge.signatureCacheInvalidated.value = true
        }
    }

    fun onActivateBlock() {
        viewModelScope.launch {
            val now  = System.currentTimeMillis()
            val mode = _selectedMode.value
            val hasFeatureRestrictions = inAppRuleRepo.getByPackage(packageName)
                .first()
                .any { it.isEnabled }
            val selectedScope = _selectedScope.value ?: if (hasFeatureRestrictions) {
                ProtectionScopeSelection.SELECTED_FEATURES
            } else {
                ProtectionScopeSelection.FULL_APP
            }

            _selectedScope.value = selectedScope
            if (selectedScope == ProtectionScopeSelection.FULL_APP) {
                inAppRuleRepo.deleteByPackage(packageName)
            }
            ruleRepo.save(
                AppRule(
                    packageName     = packageName,
                    dailyLimitMs    = 0L,
                    isHardBlocked   = selectedScope == ProtectionScopeSelection.FULL_APP ||
                        (packageName == "com.instagram.android" &&
                            selectedScope == ProtectionScopeSelection.SELECTED_FEATURES),
                    categoryId      = null,
                    createdAt       = now,
                    hardcoreUntilMs = now + _selectedDuration.value,
                    unlockCondition = null,
                    blockNote       = _blockNote.value.takeIf { it.isNotBlank() }
                )
            )
            if (selectedScope == ProtectionScopeSelection.FULL_APP) {
                strictModeManager.activate(packageName, _selectedDuration.value)
            }
            BastionServiceBridge.signatureCacheInvalidated.value = true
            BlockerServiceStarter.start(context)
            _pendingPostActivationMode.value = mode
        }
    }

    fun onDeactivateBlock() {
        viewModelScope.launch {
            ruleRepo.delete(packageName)
            inAppRuleRepo.deleteByPackage(packageName)
            BastionServiceBridge.signatureCacheInvalidated.value = true
        }
    }

    fun buildAdminActivationIntent(): Intent = deviceAdminManager.getActivationIntent()

    fun onAdminPromptHandled() {
        strictModeManager.requiresAdminActivation.value = false
    }

    fun consumePostActivation() {
        _pendingPostActivationMode.value = null
    }

    private fun canonicalRuleId(packageName: String, featureId: String): String = when (packageName) {
        "com.instagram.android" -> when (featureId) {
            INSTAGRAM_GUARD_FEATURE_ID -> "instagram_guard_block"
            "REELS" -> "instagram_reels_block"
            "DM" -> "instagram_dm_allow"
            else -> "${packageName}_${featureId.lowercase()}"
        }

        "com.google.android.youtube" -> when (featureId) {
            "SHORTS" -> "youtube_shorts_block"
            "HOME_FEED" -> "youtube_home_block"
            "EXPLORE" -> "youtube_explore_block"
            else -> "${packageName}_${featureId.lowercase()}"
        }

        else -> "${packageName}_${featureId.lowercase()}"
    }

    private fun isFeatureEnabledForRow(
        packageName: String,
        featureId: String,
        savedRules: List<InAppRule>
    ): Boolean {
        if (packageName == "com.instagram.android" && featureId == INSTAGRAM_GUARD_FEATURE_ID) {
            return savedRules.any { rule ->
                rule.isEnabled && rule.featureId in setOf(INSTAGRAM_GUARD_FEATURE_ID, "REELS", "DM")
            }
        }
        return savedRules.any { rule -> rule.featureId == featureId && rule.isEnabled }
    }

    private fun legacyRuleIdsFor(packageName: String, featureId: String): List<String> {
        if (packageName == "com.instagram.android" && featureId == INSTAGRAM_GUARD_FEATURE_ID) {
            return listOf(
                canonicalRuleId(packageName, INSTAGRAM_GUARD_FEATURE_ID),
                canonicalRuleId(packageName, "REELS"),
                canonicalRuleId(packageName, "DM")
            )
        }
        return listOf(canonicalRuleId(packageName, featureId))
    }

    private data class SelectionUiState(
        val mode: BlockModeSelection,
        val scope: ProtectionScopeSelection?,
        val duration: Long,
        val note: String,
        val requiresAdminActivation: Boolean
    )
}
