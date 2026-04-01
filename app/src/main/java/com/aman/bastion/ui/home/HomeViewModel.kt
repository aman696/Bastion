package com.aman.bastion.ui.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aman.bastion.data.url.dao.UrlRuleDao
import com.aman.bastion.data.url.entity.UrlRuleEntity
import com.aman.bastion.domain.catalog.InAppRuleCatalog
import com.aman.bastion.domain.model.AppRule
import com.aman.bastion.domain.model.InAppRule
import com.aman.bastion.domain.repository.AppRuleRepository
import com.aman.bastion.domain.repository.InAppRuleRepository
import com.aman.bastion.service.engine.UrlBlockEngine
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
    val blockedUrls: List<String> = emptyList(),
    val adultSitesEnabled: Boolean = false,
    val isLoading: Boolean = true,
    val addPanelExpanded: Boolean = false,
    val addSearchQuery: String = "",
    val urlDraft: String = "",
    val protectionStatus: ProtectionStatus = ProtectionStatus.None
)

private data class BaseSnapshot(
    val rules: List<AppRule>,
    val inAppRules: List<InAppRule>,
    val urlRules: List<UrlRuleEntity>,
    val cache: Map<String, Pair<String, ImageBitmap>>,
    val packages: List<String>
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val ruleRepo: AppRuleRepository,
    private val inAppRuleRepo: InAppRuleRepository,
    private val urlRuleDao: UrlRuleDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _addPanelExpanded = MutableStateFlow(false)
    private val _addSearchQuery   = MutableStateFlow("")
    private val _urlDraft         = MutableStateFlow("")

    private val allAppInfoCache   = MutableStateFlow<Map<String, Pair<String, ImageBitmap>>>(emptyMap())
    private val allPackagesSorted = MutableStateFlow<List<String>>(emptyList())

    // Combine rules + inApp rules + cache + sorted packages into a base snapshot,
    // then combine that with the panel UI state.
    private val _baseSnapshot = combine(
        ruleRepo.getAll(),
        inAppRuleRepo.getAll(),
        urlRuleDao.getAll(),
        allAppInfoCache,
        allPackagesSorted
    ) { rules, inAppRules, urlRules, cache, packages ->
        BaseSnapshot(
            rules = rules,
            inAppRules = inAppRules,
            urlRules = urlRules,
            cache = cache,
            packages = packages
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        _baseSnapshot,
        _addPanelExpanded,
        _addSearchQuery,
        _urlDraft
    ) { snap, expanded, query, urlDraft ->
        val (rules, inAppRules, urlRules, cache, packages) = snap
        if (cache.isEmpty()) return@combine HomeUiState(isLoading = true)
        val enabledUrlRules = urlRules.filter { it.isEnabled }
        val adultSitesEnabled = enabledUrlRules.any { it.id == UrlBlockEngine.BUILTIN_ADULT_SITES_RULE_ID }
        val manualUrlRules = enabledUrlRules
            .filterNot { it.id == UrlBlockEngine.BUILTIN_ADULT_SITES_RULE_ID }
            .map { it.pattern }

        val ruleMap    = rules.associateBy { it.packageName }
        val inAppByPkg = inAppRules
            .filter { it.isEnabled && InAppRuleCatalog.hasFeatures(it.packageName) }
            .groupBy { it.packageName }
        val protectedPackages = packages.filter { pkg ->
            ruleMap[pkg] != null || inAppByPkg[pkg]?.isNotEmpty() == true
        }

        val ruledApps = protectedPackages.mapNotNull { pkg ->
            val rule = ruleMap[pkg]
            val (name, icon) = cache[pkg] ?: return@mapNotNull null
            buildAppListItem(pkg, name, icon, rule, inAppByPkg[pkg])
        }

        val filteredAll = packages.mapNotNull { pkg ->
            val (name, icon) = cache[pkg] ?: return@mapNotNull null
            if (query.isNotBlank() && !name.contains(query, ignoreCase = true)) return@mapNotNull null
            val rule = ruleMap[pkg]
            buildAppListItem(pkg, name, icon, rule, inAppByPkg[pkg])
                ?: AppListItem(pkg, name, icon, false, 0L)
        }

        HomeUiState(
            ruledApps        = ruledApps,
            filteredAllApps  = filteredAll,
            blockedUrls      = manualUrlRules,
            adultSitesEnabled = adultSitesEnabled,
            isLoading        = false,
            addPanelExpanded = expanded,
            addSearchQuery   = query,
            urlDraft         = urlDraft,
            protectionStatus = computeProtectionStatus(ruledApps, manualUrlRules, adultSitesEnabled)
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
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val installed = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
                .mapNotNull { it.activityInfo?.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != context.packageName }
                .filter { it.enabled }
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
        rule: AppRule?,
        enabledInApp: List<InAppRule>?
    ): AppListItem? {
        val count      = enabledInApp?.size ?: 0
        val firstLabel = enabledInApp?.firstOrNull()?.let { inApp ->
            InAppRuleCatalog.featuresFor(pkg)
                .firstOrNull { it.featureId == inApp.featureId }?.shortLabel
        }
        val hasActiveFullAppRule = rule?.let { hasActiveFullAppRule(it, count) } ?: false
        if (!hasActiveFullAppRule && count == 0) return null

        return AppListItem(
            packageName              = pkg,
            appName                  = name,
            icon                     = icon,
            isBlocked                = hasActiveFullAppRule && count == 0 && rule?.isHardcoreActive != true,
            hardcoreUntilMs          = rule?.hardcoreUntilMs ?: 0L,
            activeInAppRuleCount     = count,
            firstInAppRuleShortLabel = firstLabel
        )
    }

    private fun hasActiveFullAppRule(rule: AppRule, enabledInAppCount: Int): Boolean {
        val now = System.currentTimeMillis()
        if (rule.isHardBlocked) {
            if (rule.hardcoreUntilMs == 0L) return true
            return rule.hardcoreUntilMs > now
        }
        if (rule.dailyLimitMs > 0L) return true
        if (rule.categoryId != null) return true
        return false
    }

    private fun computeProtectionStatus(apps: List<AppListItem>): ProtectionStatus {
        return computeProtectionStatus(apps, emptyList(), false)
    }

    private fun computeProtectionStatus(
        apps: List<AppListItem>,
        blockedUrls: List<String>,
        adultSitesEnabled: Boolean
    ): ProtectionStatus {
        val hardcoreApp = apps.firstOrNull { it.isHardcoreActive }
        if (hardcoreApp != null) {
            return ProtectionStatus.HardcoreActive(
                appName = hardcoreApp.appName,
                appIcon = hardcoreApp.icon,
                untilMs = hardcoreApp.hardcoreUntilMs
            )
        }
        if (apps.isEmpty() && blockedUrls.isEmpty() && !adultSitesEnabled) return ProtectionStatus.None
        val appChips = apps.map { app ->
            val label = when {
                app.activeInAppRuleCount > 1  -> "${app.activeInAppRuleCount} RULES"
                app.firstInAppRuleShortLabel != null -> "${app.firstInAppRuleShortLabel} BLOCKED"
                else -> "HARD"
            }
            app.appName to label
        }
        val urlChips = blockedUrls.map { pattern -> pattern to "URL" }
        val builtInUrlChips = if (adultSitesEnabled) {
            listOf("Adult Sites" to "URL")
        } else {
            emptyList()
        }
        return ProtectionStatus.Active(
            blockCount = apps.size + blockedUrls.size + builtInUrlChips.size,
            chips = appChips + builtInUrlChips + urlChips
        )
    }

    fun onAddPanelToggle() { _addPanelExpanded.value = !_addPanelExpanded.value }
    fun onAddSearchQuery(q: String) { _addSearchQuery.value = q }
    fun onUrlDraftChange(q: String) { _urlDraft.value = q }

    fun onAddUrlRule() {
        viewModelScope.launch(Dispatchers.IO) {
            val normalized = normalizeUrlPattern(_urlDraft.value)
            if (normalized.isBlank()) return@launch
            urlRuleDao.upsert(
                UrlRuleEntity(
                    id = normalized,
                    pattern = normalized,
                    createdAt = System.currentTimeMillis(),
                    isEnabled = true
                )
            )
            _urlDraft.value = ""
        }
    }

    fun onToggleAdultSites() {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = urlRuleDao.getByIdSync(UrlBlockEngine.BUILTIN_ADULT_SITES_RULE_ID)
            val nextEnabled = !(existing?.isEnabled ?: false)
            urlRuleDao.upsert(
                UrlRuleEntity(
                    id = UrlBlockEngine.BUILTIN_ADULT_SITES_RULE_ID,
                    pattern = UrlBlockEngine.BUILTIN_ADULT_SITES_RULE_ID,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    isEnabled = nextEnabled
                )
            )
        }
    }

    fun onRemoveUrlRule(pattern: String) {
        viewModelScope.launch(Dispatchers.IO) {
            urlRuleDao.delete(pattern)
        }
    }

    private fun normalizeUrlPattern(raw: String): String {
        return raw.trim()
            .lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trim('/')
            .substringBefore('#')
            .substringBefore('?')
    }
}
