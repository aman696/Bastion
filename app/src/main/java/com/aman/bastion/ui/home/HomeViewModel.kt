package com.aman.bastion.ui.home

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aman.bastion.domain.catalog.InAppRuleCatalog
import com.aman.bastion.domain.model.AppRule
import com.aman.bastion.domain.model.InAppRule
import com.aman.bastion.domain.repository.AppRuleRepository
import com.aman.bastion.domain.repository.InAppRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI models ────────────────────────────────────────────────────────────────

data class AppListItem(
    val packageName: String,
    val appName: String,
    val icon: ImageBitmap,
    val isBlocked: Boolean,
    val hardcoreUntilMs: Long,
    val activeInAppRuleCount: Int = 0,
    val firstInAppRuleShortLabel: String? = null
) {
    val isHardcoreActive: Boolean
        get() = hardcoreUntilMs > System.currentTimeMillis()
}

sealed class ProtectionStatus {
    object None : ProtectionStatus()
    data class Active(
        val blockCount: Int,
        val chips: List<Pair<String, String>>  // (appName, blockTypeLabel)
    ) : ProtectionStatus()
    data class HardcoreActive(
        val appName: String,
        val appIcon: ImageBitmap,
        val untilMs: Long
    ) : ProtectionStatus()
}

data class HomeUiState(
    val ruledApps: List<AppListItem> = emptyList(),
    val filteredAllApps: List<AppListItem> = emptyList(),
    val isLoading: Boolean = true,
    val addPanelExpanded: Boolean = false,
    val addSearchQuery: String = "",
    val protectionStatus: ProtectionStatus = ProtectionStatus.None
)

private data class BaseSnapshot(
    val rules: List<AppRule>,
    val inAppRules: List<InAppRule>,
    val cache: Map<String, Pair<String, ImageBitmap>>,
    val packages: List<String>
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val ruleRepo: AppRuleRepository,
    private val inAppRuleRepo: InAppRuleRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _addPanelExpanded = MutableStateFlow(false)
    private val _addSearchQuery   = MutableStateFlow("")

    private val allAppInfoCache   = MutableStateFlow<Map<String, Pair<String, ImageBitmap>>>(emptyMap())
    private val allPackagesSorted = MutableStateFlow<List<String>>(emptyList())

    // Combine rules + inApp rules + cache + sorted packages into a base snapshot,
    // then combine that with the panel UI state.
    private val _baseSnapshot = combine(
        ruleRepo.getAll(),
        inAppRuleRepo.getAll(),
        allAppInfoCache,
        allPackagesSorted
    ) { rules, inAppRules, cache, packages ->
        BaseSnapshot(rules, inAppRules, cache, packages)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        _baseSnapshot,
        _addPanelExpanded,
        _addSearchQuery
    ) { snap, expanded, query ->
        val (rules, inAppRules, cache, packages) = snap
        if (cache.isEmpty()) return@combine HomeUiState(isLoading = true)

        val ruleMap    = rules.associateBy { it.packageName }
        val inAppByPkg = inAppRules.filter { it.isEnabled }.groupBy { it.packageName }

        val ruledApps = packages.mapNotNull { pkg ->
            val rule = ruleMap[pkg] ?: return@mapNotNull null
            val (name, icon) = cache[pkg] ?: return@mapNotNull null
            buildAppListItem(pkg, name, icon, rule, inAppByPkg[pkg])
        }

        val filteredAll = if (!expanded) emptyList() else {
            packages.mapNotNull { pkg ->
                val (name, icon) = cache[pkg] ?: return@mapNotNull null
                if (query.isNotBlank() && !name.contains(query, ignoreCase = true)) return@mapNotNull null
                val rule = ruleMap[pkg]
                if (rule == null) AppListItem(pkg, name, icon, false, 0L)
                else buildAppListItem(pkg, name, icon, rule, inAppByPkg[pkg])
            }
        }

        HomeUiState(
            ruledApps        = ruledApps,
            filteredAllApps  = filteredAll,
            isLoading        = false,
            addPanelExpanded = expanded,
            addSearchQuery   = query,
            protectionStatus = computeProtectionStatus(ruledApps)
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.Eagerly,
        initialValue = HomeUiState()
    )

    init { loadAppInfoCache() }

    private fun loadAppInfoCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .filter { it.packageName != context.packageName }
                .sortedBy { it.loadLabel(pm).toString().lowercase() }

            val cache = installed.associate { info ->
                info.packageName to Pair(
                    info.loadLabel(pm).toString(),
                    info.loadIcon(pm).toBitmap().asImageBitmap()
                )
            }
            allAppInfoCache.value   = cache
            allPackagesSorted.value = installed.map { it.packageName }
        }
    }

    private fun buildAppListItem(
        pkg: String,
        name: String,
        icon: ImageBitmap,
        rule: AppRule,
        enabledInApp: List<InAppRule>?
    ): AppListItem {
        val count      = enabledInApp?.size ?: 0
        val firstLabel = enabledInApp?.firstOrNull()?.let { inApp ->
            InAppRuleCatalog.featuresFor(pkg)
                .firstOrNull { it.featureId == inApp.featureId }?.shortLabel
        }
        return AppListItem(
            packageName              = pkg,
            appName                  = name,
            icon                     = icon,
            isBlocked                = !rule.isHardcoreActive,
            hardcoreUntilMs          = rule.hardcoreUntilMs,
            activeInAppRuleCount     = count,
            firstInAppRuleShortLabel = firstLabel
        )
    }

    private fun computeProtectionStatus(apps: List<AppListItem>): ProtectionStatus {
        val hardcoreApp = apps.firstOrNull { it.isHardcoreActive }
        if (hardcoreApp != null) {
            return ProtectionStatus.HardcoreActive(
                appName = hardcoreApp.appName,
                appIcon = hardcoreApp.icon,
                untilMs = hardcoreApp.hardcoreUntilMs
            )
        }
        if (apps.isEmpty()) return ProtectionStatus.None
        val chips = apps.map { app ->
            val label = when {
                app.activeInAppRuleCount > 1  -> "${app.activeInAppRuleCount} RULES"
                app.firstInAppRuleShortLabel != null -> "${app.firstInAppRuleShortLabel} BLOCKED"
                else -> "HARD"
            }
            app.appName to label
        }
        return ProtectionStatus.Active(blockCount = apps.size, chips = chips)
    }

    fun onAddPanelToggle() { _addPanelExpanded.value = !_addPanelExpanded.value }
    fun onAddSearchQuery(q: String) { _addSearchQuery.value = q }
}
