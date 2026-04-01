package com.aman.bastion.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object BlockerServiceStarter {
    fun start(context: Context) {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BastionForegroundService::class.java)
            )
        }
    }
}
