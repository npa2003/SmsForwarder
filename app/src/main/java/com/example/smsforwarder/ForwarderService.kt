package com.example.smsforwarder // замени на свой package!

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

class ForwarderService : Service() {

    companion object {
        private const val TAG = "ForwarderService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sms_forwarder_channel"
        // Для Android 10+ тип сервиса
        private const val FOREGROUND_SERVICE_TYPE_DATA_SYNC = 8 // android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "========== SERVICE onStartCommand ==========")
        Log.d(TAG, "Thread: ${Thread.currentThread().name}")
        Log.d(TAG, "Intent action: ${intent?.action}")

        // Сначала обрабатываем данные, потом запускаем foreground
        intent?.let {
            val action = it.action

            if (action == "FORWARD_SMS") {
                // Получаем данные для пересылки
                val sender = it.getStringExtra("sms_sender") ?: "Unknown"
                val body = it.getStringExtra("sms_body") ?: ""
                val timestamp = it.getLongExtra("sms_timestamp", System.currentTimeMillis())
                val simSlot = it.getIntExtra("sim_slot", -1)
                val simName = it.getStringExtra("sim_name") ?: "SIM ${simSlot + 1}"

                Log.d(TAG, "Received FORWARD_SMS action")
                Log.d(TAG, "  sender: $sender")
                Log.d(TAG, "  body: $body")
                Log.d(TAG, "  simName: $simName")

                // Формируем текст для пересылки
                val forwardText = buildString {
                    append("${simName.take(3)}")
                    append("$sender")
                    append("⏱${SimpleDateFormat("dd.MM.yy|HH:mm", Locale.getDefault()).format(Date(timestamp))}\n")
                    append("$body")
                }

                // Отправляем пересланное SMS
                val success = sendSms(SmsReceiver.FORWARD_NUMBER, forwardText)

                if (success) {
                    Log.i(TAG, "✅ SMS forwarded successfully")
                    showNotification("✅ SMS переслано", "От $sender на ${SmsReceiver.FORWARD_NUMBER}")
                } else {
                    Log.e(TAG, "❌ Failed to forward SMS")
                    showNotification("❌ Ошибка пересылки", "Не удалось отправить SMS")
                }
            }
        }

        // Теперь запускаем foreground service (после обработки данных)
        try {
            Log.d(TAG, "Attempting to startForeground with ID: $NOTIFICATION_ID")
            val notification = createNotification()

            // Важно! Сначала делаем сервис foreground, потом уже можем показывать уведомления
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Для Android 10+ нужно указывать тип
                startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            Log.d(TAG, "✅ startForeground called successfully")

            // Показываем уведомление о запуске
            if (intent?.action == "START_FOREGROUND") {
                showNotification("🟢 Сервис запущен", "Отслеживание SMS активно")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to startForeground", e)
        }

        Log.d(TAG, "========== SERVICE onStartCommand COMPLETE ==========")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Отправляет SMS
     */
    private fun sendSms(phoneNumber: String, message: String): Boolean {
        return try {
            val smsManager = SmsManager.getDefault()

            // Разбиваем длинное сообщение на части
            val parts = smsManager.divideMessage(message)

            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS", e)
            false
        }
    }

    /**
     * Создает канал уведомлений (для Android 8+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Forwarder Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Постоянный сервис для отслеживания SMS"
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Создает уведомление для foreground service
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Forwarder")
            .setContentText("Отслеживание входящих SMS...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Показывает уведомление о результате пересылки
     */
    private fun showNotification(title: String, content: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")

        // Перезапускаем сервис если его убили
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, ForwarderService::class.java))
        } else {
            startService(Intent(this, ForwarderService::class.java))
        }
    }
}