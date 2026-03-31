package com.aman.bastion.ui.home

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aman.bastion.domain.model.AppRule
import com.aman.bastion.domain.repository.AppRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppListItem(
    val packageName: String,
    val appName: String,
    val icon: ImageBitmap,
    val isBlocked: Boolean,
    val hardcoreUntilMs: Long
) {
    val isHardcoreActive: Boolean
        get() = hardcoreUntilMs > System.currentTimeMillis()
}

data class HomeUiState(
    val apps: List<AppListItem> = emptyList(),
    val isLoading: Boolean = true,
    val query: String = "",
    val selectedApp: AppListItem? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: AppRuleRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val rawApps = MutableStateFlow<List<AppListItem>>(emptyList())
    private val _query = MutableStateFlow("")
    private val _selectedApp = MutableStateFlow<AppListItem?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        rawApps, _query, _selectedApp
    ) { apps, query, selected ->
        val filtered = if (query.isBlank()) apps
        else apps.filter { it.appName.contains(query, ignoreCase = true) }
        HomeUiState(apps = filtered, isLoading = false, query = query, selectedApp = selected)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState()
    )

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .filter { it.packageName != context.packageName }
                .sortedBy { it.loadLabel(pm).toString().lowercase() }

            // Cache labels and icons once — avoid re-querying on every rule emission
            val appInfoCache: Map<String, Pair<String, ImageBitmap>> = installed.associate { info ->
                info.packageName to Pair(
                    info.loadLabel(pm).toString(),
                    info.loadIcon(pm).toBitmap().asImageBitmap()
                )
            }

            repository.getAll().collect { rules ->
                val ruleMap = rules.associateBy { it.packageName }
                rawApps.value = installed.mapNotNull { info ->
                    val (appName, icon) = appInfoCache[info.packageName] ?: return@mapNotNull null
                    val rule = ruleMap[info.packageName]
                    AppListItem(
                        packageName = info.packageName,
                        appName = appName,
                        icon = icon,
                        isBlocked = rule != null && !rule.isHardBlocked,
                        hardcoreUntilMs = rule?.hardcoreUntilMs ?: 0L
                    )
                }
            }
        }
    }

    fun onQueryChange(query: String) { _query.value = query }

    fun onAppSelected(app: AppListItem) { _selectedApp.value = app }

    fun onDismissSheet() { _selectedApp.value = null }

    fun onBlock(packageName: String) {
        viewModelScope.launch {
            repository.save(
                AppRule(
                    packageName = packageName,
                    dailyLimitMs = 0L,
                    isHardBlocked = false,
                    categoryId = null,
                    createdAt = System.currentTimeMillis(),
                    hardcoreUntilMs = 0L
                )
            )
        }
    }

    fun onHardcoreBlock(packageName: String, durationMs: Long) {
        viewModelScope.launch {
            repository.save(
                AppRule(
                    packageName = packageName,
                    dailyLimitMs = 0L,
                    isHardBlocked = true,
                    categoryId = null,
                    createdAt = System.currentTimeMillis(),
                    hardcoreUntilMs = System.currentTimeMillis() + durationMs
                )
            )
        }
    }

    fun onUnblock(packageName: String) {
        viewModelScope.launch {
            val rule = repository.getByPackage(packageName).firstOrNull()
            if (rule != null && rule.hardcoreUntilMs > System.currentTimeMillis()) return@launch
            repository.delete(packageName)
        }
    }
}
