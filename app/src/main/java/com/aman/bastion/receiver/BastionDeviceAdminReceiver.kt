package com.aman.bastion.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.aman.bastion.service.StrictModeManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BastionDeviceAdminReceiver : DeviceAdminReceiver() {

    @Inject
    lateinit var strictModeManager: StrictModeManager

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Bastion is actively protecting you. " +
            "Disabling this will remove your uninstall protection. " +
            "Your hardcore lock will still be active but easier to bypass."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("bastion_audit", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("admin_disabled_at", System.currentTimeMillis())
            .putBoolean("admin_active", false)
            .apply()
    }

    override fun onEnabled(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("bastion_audit", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("admin_active", true)
            .putLong("admin_enabled_at", System.currentTimeMillis())
            .apply()
    }
}
