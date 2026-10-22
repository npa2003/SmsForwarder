package com.example.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_PACKAGE_REPLACED) {
            
            Log.d(TAG, "Event received: $action")
            startService(context, action)
        }
    }

    private fun startService(context: Context, action: String?) {
        try {
            val serviceIntent = Intent(context, ForwarderService::class.java)
            context.startService(serviceIntent)
            Log.i(TAG, "Service started after $action")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service", e)
        }
    }
}