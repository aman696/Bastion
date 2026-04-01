package com.aman.bastion.ui.hardcorelock

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aman.bastion.domain.repository.AppRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HardcoreLockUiState(
    val appName: String      = "",
    val icon: ImageBitmap?   = null,
    val packageName: String  = "",
    val hardcoreUntilMs: Long = 0L,
    val createdAt: Long      = 0L,
    val blockNote: String?   = null,
    val showNote: Boolean    = false,
    val isLoading: Boolean   = true
)

@HiltViewModel
class HardcoreLockViewModel @Inject constructor(
    private val ruleRepo: AppRuleRepository,
    @ApplicationContext private val context: Context,
    savedState: SavedStateHandle
) : ViewModel() {

    private val packageName: String = savedState["packageName"]!!
    private val _showNote = MutableStateFlow(false)

    val uiState: StateFlow<HardcoreLockUiState> = combine(
        ruleRepo.getByPackage(packageName),
        _showNote
    ) { rule, showNote ->
        val pm      = context.packageManager
        val appInfo = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
        HardcoreLockUiState(
            appName         = appInfo?.loadLabel(pm)?.toString() ?: packageName,
            icon            = appInfo?.loadIcon(pm)?.toBitmap()?.asImageBitmap(),
            packageName     = packageName,
            hardcoreUntilMs = rule?.hardcoreUntilMs ?: 0L,
            createdAt       = rule?.createdAt ?: 0L,
            blockNote       = rule?.blockNote,
            showNote        = showNote,
            isLoading       = false
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HardcoreLockUiState())

    fun toggleShowNote() { _showNote.value = !_showNote.value }
}
