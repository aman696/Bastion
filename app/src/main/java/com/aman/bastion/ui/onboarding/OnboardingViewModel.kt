package com.aman.bastion.ui.onboarding

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.aman.bastion.service.DeviceAdminManager
import com.aman.bastion.service.BlockerServiceStarter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val currentStep: Int               = 0,
    val notificationGranted: Boolean   = false,
    val usageGranted: Boolean          = false,
    val overlayGranted: Boolean        = false,
    val accessibilityGranted: Boolean  = false,
    val batteryGranted: Boolean        = false,
    val deviceAdminGranted: Boolean    = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceAdminManager: DeviceAdminManager
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init { pollPermissions() }

    private fun pollPermissions() {
        viewModelScope.launch {
            while (true) {
                _state.value = _state.value.copy(
                    notificationGranted  = checkNotification(),
                    usageGranted         = checkUsageAccess(),
                    overlayGranted       = Settings.canDrawOverlays(context),
                    accessibilityGranted = checkAccessibility(),
                    batteryGranted       = checkBatteryOptimization(),
                    deviceAdminGranted   = deviceAdminManager.isAdminActive()
                )
                delay(500L)
            }
        }
    }

    fun advance() {
        val next = _state.value.currentStep + 1
        if (next <= 7) _state.value = _state.value.copy(currentStep = next)
    }

    fun markOnboardingDone() {
        context.getSharedPreferences("bastion_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("onboarding_done", true).apply()
        if (checkUsageAccess() && Settings.canDrawOverlays(context) && checkAccessibility()) {
            BlockerServiceStarter.start(context)
        }
    }

    fun buildDeviceAdminIntent() = deviceAdminManager.getActivationIntent()

    private fun checkNotification(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun checkUsageAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun checkAccessibility(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(context.packageName, ignoreCase = true)
    }

    private fun checkBatteryOptimization(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
