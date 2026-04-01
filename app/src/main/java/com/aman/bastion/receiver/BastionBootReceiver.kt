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
        ContextCompat.startForegroundService(
            context,
            Intent(context, BastionForegroundService::class.java)
        )

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            scheduleEngine.scheduleAlarms()
        }
    }
}
