package com.aman.bastion.service

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.aman.bastion.receiver.BastionDeviceAdminReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceAdminManager @Inject constructor(
    private val dpm: DevicePolicyManager,
    @ApplicationContext private val context: Context
) {

    private val adminComponent = ComponentName(context, BastionDeviceAdminReceiver::class.java)

    fun isAdminActive(): Boolean = dpm.isAdminActive(adminComponent)

    fun getActivationIntent(): Intent = Intent(
        DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN
    ).apply {
        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
        putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Bastion needs an extra protection layer while your hardcore lock is active."
        )
    }

    fun ensureAdminActive(activity: Activity): Boolean {
        if (isAdminActive()) return true
        @Suppress("DEPRECATION")
        activity.startActivityForResult(getActivationIntent(), REQUEST_CODE_ADMIN)
        return false
    }

    companion object {
        const val REQUEST_CODE_ADMIN = 9001
    }
}
