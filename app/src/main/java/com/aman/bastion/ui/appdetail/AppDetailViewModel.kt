package com.aman.bastion.ui.appdetail

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aman.bastion.domain.catalog.InAppFeature
import com.aman.bastion.domain.catalog.InAppRuleCatalog
import com.aman.bastion.domain.model.AppRule
import com.aman.bastion.domain.model.InAppRule
import com.aman.bastion.domain.model.RuleType
import com.aman.bastion.domain.model.UnlockCondition
import com.aman.bastion.domain.repository.AppRuleRepository
import com.aman.bastion.domain.repository.InAppRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class BlockModeSelection { NONE, SOFT, HARDCORE }

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
    val selectedMode: BlockModeSelection   = BlockModeSelection.NONE,
    val selectedDurationMs: Long           = 3_600_000L,
    val selectedUnlock: UnlockCondition?   = null,
    val blockNote: String                  = "",
    val isLoading: Boolean                 = true
)

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val ruleRepo: AppRuleRepository,
    private val inAppRuleRepo: InAppRuleRepository,
    @ApplicationContext private val context: Context,
    savedState: SavedStateHandle
) : ViewModel() {

    private val packageName: String = savedState["packageName"]!!

    private val _selectedMode     = MutableStateFlow(BlockModeSelection.NONE)
    private val _selectedDuration = MutableStateFlow(3_600_000L)
    private val _selectedUnlock   = MutableStateFlow<UnlockCondition?>(null)
    private val _blockNote        = MutableStateFlow("")

    // Combine the two repo flows first to stay within the 5-arg limit
    private val _ruleAndInApp = combine(
        ruleRepo.getByPackage(packageName),
        inAppRuleRepo.getByPackage(packageName)
    ) { rule, inApp -> rule to inApp }

    val uiState: StateFlow<AppDetailUiState> = combine(
        _ruleAndInApp,
        _selectedMode,
        _selectedDuration,
        _selectedUnlock,
        _blockNote
    ) { (rule, savedInApp), mode, duration, unlock, note ->
        val pm      = context.packageManager
        val appInfo = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
        val appName = appInfo?.loadLabel(pm)?.toString() ?: packageName
        val icon    = appInfo?.loadIcon(pm)?.toBitmap()?.asImageBitmap()

        val features  = InAppRuleCatalog.featuresFor(packageName)
        val savedMap  = savedInApp.associateBy { it.featureId }
        val featureRows = features.map { feat ->
            val saved = savedMap[feat.featureId]
            FeatureRowState(
                feature   = feat,
                isEnabled = saved?.isEnabled ?: false,
                ruleId    = saved?.id
            )
        }

        AppDetailUiState(
            appName            = appName,
            icon               = icon,
            packageName        = packageName,
            currentRule        = rule,
            featureRows        = featureRows,
            selectedMode       = mode,
            selectedDurationMs = duration,
            selectedUnlock     = unlock,
            blockNote          = note,
            isLoading          = false
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppDetailUiState())

    fun onSelectMode(mode: BlockModeSelection)     { _selectedMode.value = mode }
    fun onSelectDuration(ms: Long)                 { _selectedDuration.value = ms }
    fun onSelectUnlock(condition: UnlockCondition) { _selectedUnlock.value = condition }
    fun onBlockNoteChange(note: String)            { _blockNote.value = note }

    fun onToggleFeatureRule(row: FeatureRowState) {
        viewModelScope.launch {
            val rule = InAppRule(
                id          = row.ruleId ?: UUID.randomUUID().toString(),
                packageName = packageName,
                featureId   = row.feature.featureId,
                ruleName    = row.feature.displayName,
                isEnabled   = !row.isEnabled,
                ruleType    = row.feature.ruleType
            )
            inAppRuleRepo.save(rule)
        }
    }

    fun onActivateBlock() {
        viewModelScope.launch {
            val now  = System.currentTimeMillis()
            val mode = _selectedMode.value
            ruleRepo.save(
                AppRule(
                    packageName     = packageName,
                    dailyLimitMs    = 0L,
                    isHardBlocked   = mode == BlockModeSelection.HARDCORE,
                    categoryId      = null,
                    createdAt       = now,
                    hardcoreUntilMs = if (mode == BlockModeSelection.HARDCORE)
                        now + _selectedDuration.value else 0L,
                    unlockCondition = if (mode == BlockModeSelection.SOFT)
                        _selectedUnlock.value else null,
                    blockNote       = _blockNote.value.takeIf { it.isNotBlank() }
                )
            )
        }
    }

    fun onDeactivateBlock() {
        viewModelScope.launch {
            ruleRepo.delete(packageName)
        }
    }
}
