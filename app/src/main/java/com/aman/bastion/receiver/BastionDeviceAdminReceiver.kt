package com.aman.bastion.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            "bastion_audit",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        prefs.edit()
            .putLong("admin_disabled_at", System.currentTimeMillis())
            .putBoolean("admin_active", false)
            .apply()
    }

    override fun onEnabled(context: Context, intent: Intent) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            "bastion_audit",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        prefs.edit()
            .putBoolean("admin_active", true)
            .putLong("admin_enabled_at", System.currentTimeMillis())
            .apply()
    }
}
