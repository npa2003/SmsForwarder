package com.example.smsforwarder

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executors

class MaxBotClient {

    companion object {
        private const val TAG = "MaxBotClient"
        private const val API_URL = "https://platform-api2.max.ru/messages"
        private const val CONTENT_TYPE = "application/json"
        private val MEDIA_TYPE = CONTENT_TYPE.toMediaType()

        // Пул потоков для асинхронных вызовов
        private val executor = Executors.newFixedThreadPool(2)

        fun sendAsync(
            token: String,
            chatId: Long,
            text: String,
            onSuccess: (() -> Unit)? = null,
            onError: ((Exception) -> Unit)? = null
        ) {
            executor.execute {
                try {
                    sendMessage(token, chatId, text)
                    onSuccess?.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send to MAX", e)
                    onError?.invoke(e)
                }
            }
        }

        @Throws(IOException::class)
        private fun sendMessage(token: String, chatId: Long, text: String) {
            val body = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
            }

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", token)
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(MEDIA_TYPE))
                .build()

            OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No body"
                    throw IOException("MAX API error: ${response.code} - $errorBody")
                }
                Log.i(TAG, "Message sent to MAX successfully")
            }
        }
    }
}
