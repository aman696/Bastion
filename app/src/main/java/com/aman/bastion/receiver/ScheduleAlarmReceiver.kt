package com.aman.bastion.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aman.bastion.service.engine.ScheduleEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScheduleAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduleEngine: ScheduleEngine

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra("schedule_id") ?: return
        val action = intent.getStringExtra("action") ?: return

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            scheduleEngine.applySchedule(scheduleId, action)
        }
    }
}
