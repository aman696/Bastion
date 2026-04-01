package com.aman.bastion.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aman.bastion.service.BastionForegroundService
import com.aman.bastion.service.engine.ScheduleEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BastionBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduleEngine: ScheduleEngine

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in ALLOWED_ACTIONS) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, BastionForegroundService::class.java)
                )
                scheduleEngine.scheduleAlarms()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val ALLOWED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
