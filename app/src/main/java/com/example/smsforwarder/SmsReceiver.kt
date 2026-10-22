package com.example.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) {
            return
        }

        val fullMessage = messages.joinToString("") { it.messageBody ?: "" }
        val sender = messages[0].originatingAddress ?: "Unknown"

        Log.d(TAG, "SMS from $sender: $fullMessage")

        // Форматируем номер: убираем +7/8 и код страны
        val cleanNumber = cleanPhoneNumber(sender)

        // Формируем текст: номер дата|время\ntext
        val forwardNumber = SettingsManager.forwardNumber
        val forwardText = buildForwardText(cleanNumber, fullMessage)

        Log.d(TAG, "Forwarding to $forwardNumber: $forwardText")

        // Отправляем SMS
        sendSms(context, forwardNumber, forwardText)

        // Удаляем отправленное сообщение из Sent
        deleteSentMessage(context, forwardNumber, forwardText)
    }

    private fun cleanPhoneNumber(phone: String): String {
        // Убираем всё кроме цифр
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.startsWith("7") && digits.length == 11 -> digits.substring(1)  // +7...
            digits.startsWith("8") && digits.length == 11 -> digits.substring(1)  // 8...
            digits.startsWith("7") && digits.length == 10 -> digits.substring(1)  // 7...
            else -> digits  // оставляем как есть
        }
    }

    private fun buildForwardText(cleanNumber: String, body: String): String {
        val dateFormat = java.text.SimpleDateFormat("dd.MM|HH:mm", java.util.Locale.getDefault())
        return "$cleanNumber ${dateFormat.format(java.util.Date(System.currentTimeMillis()))}\n$body"
    }

    private fun sendSms(context: Context, phoneNumber: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            Log.i(TAG, "SMS forwarded to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS to $phoneNumber", e)
        }
    }

    private fun deleteSentMessage(context: Context, phoneNumber: String, body: String) {
        try {
            val uri = Telephony.Sms.Sent.CONTENT_URI
            val projection = arrayOf("_id")
            val selection = "address = ? AND body = ?"
            val selectionArgs = arrayOf(phoneNumber, body)

            context.contentResolver.query(uri, projection, selection, selectionArgs, "date DESC")?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex("_id")
                    if (idIndex >= 0) {
                        val id = cursor.getLong(idIndex)
                        val msgUri = Uri.parse("content://sms/$id")
                        context.contentResolver.delete(msgUri, null, null)
                        Log.d(TAG, "Deleted sent message: $msgUri")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete sent message", e)
        }
    }
}