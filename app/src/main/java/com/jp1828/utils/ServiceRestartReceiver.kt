package com.jp1828.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.jp1828.utils.RESTART_SERVICE") {
            val serviceIntent = Intent(context, MainService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
