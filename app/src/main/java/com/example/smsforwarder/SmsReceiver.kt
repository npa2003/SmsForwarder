package com.example.smsforwarder // замени на свой package!

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import android.util.Log
import com.example.smsforwarder.BuildConfig

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        // 0 - первая SIM, 1 - вторая SIM (меняй под себя)
        val TARGET_SIM_SLOT = 0
        // Номер для пересылки
        val FORWARD_NUMBER = BuildConfig.FORWARD_NUMBER
    }

    /**
     * Определяет слот SIM-карты из Intent
     * Проверяет разные ключи, которые используют производители
     */
    private fun getSimSlotFromIntent(intent: Intent): Int {
        val bundle = intent.extras ?: return -1

        // Для Android 5.1+ есть стандартные ключи
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            intent.getIntExtra(SubscriptionManager.EXTRA_SLOT_INDEX, -1).takeIf { it != -1 }?.let { return it }
            intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1).takeIf { it != -1 }?.let { return it }
        }

        // Проверяем все возможные ключи, которые используют производители
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

    /**
     * Альтернативный метод определения SIM
     */
    private fun getSimSlotAlternative(context: Context, intent: Intent): Int {
        val bundle = intent.extras ?: return -1

        // Перебираем все ключи и смотрим их значения
        bundle.keySet().forEach { key ->
            val value = bundle.get(key)
            Log.d(TAG, "Intent extra: $key = $value (${value?.javaClass?.simpleName})")
        }

        // Пробуем найти subscription_id или другие индикаторы
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subscriptionId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1)
                if (subscriptionId != -1) {
                    Log.d(TAG, "Found subscription ID: $subscriptionId")
                    // Конвертируем subscription ID в slot index - теперь передаем context
                    return getSlotFromSubscriptionId(context, subscriptionId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting subscription ID", e)
        }

        return -1
    }

    /**
     * Получает слот SIM по subscription ID
     */
    private fun getSlotFromSubscriptionId(context: Context, subId: Int): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return -1

        return try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subscriptionInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                subscriptionManager.activeSubscriptionInfoList
            } else {
                @Suppress("DEPRECATION")
                subscriptionManager.activeSubscriptionInfoList
            }

            subscriptionInfoList?.forEach { info ->
                if (info.subscriptionId == subId) {
                    Log.d(TAG, "Found slot ${info.simSlotIndex} for subscription $subId")
                    return info.simSlotIndex
                }
            }
            -1
        } catch (e: Exception) {
            Log.e(TAG, "Error getting slot from subscription", e)
            -1
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "========== SMS RECEIVED ==========")
        Log.d(TAG, "Intent action: ${intent.action}")

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d(TAG, "Not SMS_RECEIVED action, ignoring")
            return
        }

        // Определяем SIM-карту
        val simSlot = getSimSlotFromIntent(intent)
        Log.d(TAG, "Detected SIM slot: $simSlot (target is $TARGET_SIM_SLOT)")

        // Если не удалось определить SIM, пробуем альтернативный метод
        var finalSimSlot = simSlot
        if (finalSimSlot == -1) {
            finalSimSlot = getSimSlotAlternative(context, intent)
            Log.d(TAG, "Alternative detection result: $finalSimSlot")
        }

        // Извлекаем сообщения
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        Log.d(TAG, "Number of message parts: ${messages.size}")

        if (messages.isEmpty()) {
            Log.d(TAG, "No messages found")
            return
        }

        // Информация о первой части
        val firstMessage = messages[0]
        Log.d(TAG, "First message - Sender: ${firstMessage.originatingAddress}")
        Log.d(TAG, "First message - Body: ${firstMessage.messageBody}")

        // Если SMS не на нужной SIM - игнорируем
        if (finalSimSlot != TARGET_SIM_SLOT) {
            Log.d(TAG, "❌ Not target SIM (target: $TARGET_SIM_SLOT, actual: $finalSimSlot), ignoring")
            return
        }

        Log.d(TAG, "✅ Target SIM matched, processing SMS...")

        // Собираем полный текст
        val fullMessage = messages.joinToString("") { it.messageBody }
        val sender = messages[0].originatingAddress ?: "Unknown"

        Log.d(TAG, "Full message from $sender: $fullMessage")

        // Получаем информацию о SIM-карте
        val simInfo = getSimInfo(context, finalSimSlot)
        Log.d(TAG, "SIM info: $simInfo")

        // Создаем объект с данными
        val smsData = SmsData(
            sender = sender,
            body = fullMessage,
            timestamp = System.currentTimeMillis(),
            simSlot = finalSimSlot,
            simDisplayName = simInfo ?: "SIM ${finalSimSlot + 1}"
        )

        // Отправляем данные в уже запущенный сервис через Intent
        try {
            val serviceIntent = Intent(context, ForwarderService::class.java).apply {
                action = "FORWARD_SMS"
                putExtra("sms_sender", smsData.sender)
                putExtra("sms_body", smsData.body)
                putExtra("sms_timestamp", smsData.timestamp)
                putExtra("sim_slot", smsData.simSlot)
                putExtra("sim_name", smsData.simDisplayName)
            }

            Log.d(TAG, "Sending data to existing service...")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending data to service", e)
        }

        Log.d(TAG, "========== SMS PROCESSING COMPLETE ==========")
    }

    /**
     * Получает информацию о SIM-карте по слоту
     */
    private fun getSimInfo(context: Context, simSlot: Int): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return null

        return try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

            val subscriptionInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                subscriptionManager.activeSubscriptionInfoList
            } else {
                @Suppress("DEPRECATION")
                subscriptionManager.activeSubscriptionInfoList
            }

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