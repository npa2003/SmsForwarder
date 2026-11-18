package com.example.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        // Инициализируем SettingsManager
        SettingsManager.init(context)

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) {
            return
        }

        val fullMessage = messages.joinToString("") { it.messageBody ?: "" }
        val sender = messages[0].originatingAddress ?: "Unknown"
        val timestamp = System.currentTimeMillis()

        Log.d(TAG, "SMS from $sender: $fullMessage")

        // Определяем SIM-карту
        val simSlot = getSimSlotFromIntent(intent)
        val simInfo = getSimInfo(context, simSlot)
        val simName = simInfo ?: "SIM ${simSlot + 1}"

        // Формируем текст для MAX-бота
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val maxText = buildString {
            append("📱 SIM: $simName\n")
            append("👤 От: $sender\n")
            append("📅 ${dateFormat.format(Date(timestamp))}\n")
            append("💬 $fullMessage")
        }

        Log.d(TAG, "Sending to MAX: $maxText")

        // Отправляем в MAX-бота, если настроен
        Log.d(TAG, "MAX configured: ${SettingsManager.isMaxConfigured()}")
        Log.d(TAG, "Token: ${SettingsManager.maxBotToken?.take(10)}...")
        Log.d(TAG, "Chat ID: ${SettingsManager.maxChatId}")

        if (SettingsManager.isMaxConfigured()) {
            MaxBotClient.sendAsync(
                token = SettingsManager.maxBotToken!!,
                chatId = SettingsManager.maxChatId,
                text = maxText,
                onSuccess = { Log.d(TAG, "MAX message sent successfully") },
                onError = { Log.e(TAG, "Failed to send MAX message", it) }
            )
        } else {
            Log.w(TAG, "MAX not configured, message not sent")
        }
    }

    private fun getSimSlotFromIntent(intent: Intent): Int {
        val bundle = intent.extras ?: return -1

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            intent.getIntExtra(SubscriptionManager.EXTRA_SLOT_INDEX, -1).takeIf { it != -1 }?.let { return it }
            intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1).takeIf { it != -1 }?.let { return it }
        }

        val possibleKeys = setOf(
            "slot", "slot_id", "simSlot", "sim_slot", "simId", "simid",
            "slotId", "slotIdx", "simnum", "phone", "subscription"
        )

        val keySet = bundle.keySet()
        for (key in possibleKeys) {
            if (keySet.contains(key)) {
                try {
                    val value = bundle.getInt(key, -1)
                    if (value in 0..1) return value
                } catch (e: Exception) {
                    // Игнорируем
                }
            }
        }

        return -1
    }

    private fun getSimInfo(context: Context, simSlot: Int): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return null

        return try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subscriptionInfoList = subscriptionManager.activeSubscriptionInfoList

            subscriptionInfoList?.forEach { info ->
                if (info.simSlotIndex == simSlot) {
                    return info.carrierName?.toString() ?: "SIM ${simSlot + 1}"
                }
            }
            null
        } catch (e: SecurityException) {
            Log.e(TAG, "No READ_PHONE_STATE permission", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting SIM info", e)
            null
        }
    }
}