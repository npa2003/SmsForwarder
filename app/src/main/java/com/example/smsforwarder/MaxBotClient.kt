package com.example.smsforwarder

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MaxBotClient {

    companion object {
        private const val TAG = "MaxBotClient"
        private const val API_URL = "https://platform-api2.max.ru/messages"
        private const val CONTENT_TYPE = "application/json"
        private val MEDIA_TYPE = CONTENT_TYPE.toMediaType()

        // Отключаем проверку SSL для отладки
        private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        })

        private val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, java.security.SecureRandom())
        }

        private val hostnameVerifier = HostnameVerifier { _, _ -> true }

        private val client = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier(hostnameVerifier)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

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
                    Log.d(TAG, "=== Sending to MAX ===")
                    Log.d(TAG, "Token: $token")
                    Log.d(TAG, "Chat ID: $chatId")
                    Log.d(TAG, "Text: $text")
                    sendMessage(token, chatId, text)
                    Log.i(TAG, "✅ Sent successfully")
                    onSuccess?.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to send to MAX", e)
                    onError?.invoke(e)
                }
            }
        }

        @Throws(IOException::class)
        private fun sendMessage(token: String, chatId: Long, text: String) {
            // Вариант 1: chat_id как строка в body
            try {
                Log.d(TAG, "Trying variant 1: chat_id as string")
                val body = JSONObject().apply {
                    put("chat_id", chatId.toString())
                    put("text", text)
                }
                sendRequest(token, body.toString())
            } catch (e: IOException) {
                Log.e(TAG, "Failed with variant 1", e)
            }

            // Вариант 2: chat_id как строка в query
            try {
                Log.d(TAG, "Trying variant 2: chat_id as string in query")
                val url = "$API_URL?chat_id=$chatId"
                val body = JSONObject().apply {
                    put("text", text)
                }
                sendRequestWithTokenInQuery(url, token, body.toString())
            } catch (e: IOException) {
                Log.e(TAG, "Failed with variant 2", e)
            }

            // Вариант 3: с полем type
            try {
                Log.d(TAG, "Trying variant 3: with type field")
                val body = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", text)
                    put("type", "text")
                }
                sendRequest(token, body.toString())
            } catch (e: IOException) {
                Log.e(TAG, "Failed with variant 3", e)
            }

            // Вариант 4: с полем message_type
            try {
                Log.d(TAG, "Trying variant 4: with message_type field")
                val body = JSONObject().apply {
                    put("chat_id", chatId)
                    put("message", text)
                }
                sendRequest(token, body.toString())
            } catch (e: IOException) {
                Log.e(TAG, "Failed with variant 4", e)
            }

            throw IOException("All variants failed")
        }

        @Throws(IOException::class)
        private fun sendRequest(token: String, body: String) {
            Log.d(TAG, "Request body: $body")

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", token)
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: "No body"
                Log.d(TAG, "Response code: ${response.code}")
                Log.d(TAG, "Response body: $responseBody")

                if (response.isSuccessful) {
                    Log.i(TAG, "✅ Sent successfully")
                } else {
                    throw IOException("MAX API error: ${response.code} - $responseBody")
                }
            }
        }

        @Throws(IOException::class)
        private fun sendRequestWithTokenInQuery(url: String, token: String, body: String) {
            Log.d(TAG, "URL: $url")
            Log.d(TAG, "Request body: $body")

            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: "No body"
                Log.d(TAG, "Response code: ${response.code}")
                Log.d(TAG, "Response body: $responseBody")

                if (response.isSuccessful) {
                    Log.i(TAG, "✅ Sent successfully with query token")
                } else {
                    throw IOException("MAX API error: ${response.code} - $responseBody")
                }
            }
        }
    }
}