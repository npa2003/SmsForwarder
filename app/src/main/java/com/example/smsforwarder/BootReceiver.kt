package com.example.smsforwarder // замени на свой package!

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device boot completed, starting foreground service...")

            // Запускаем сервис как foreground
            val serviceIntent = Intent(context, ForwarderService::class.java).apply {
                action = "START_FOREGROUND"
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.i(TAG, "Service started after boot")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service after boot", e)
            }
        }
    }
}