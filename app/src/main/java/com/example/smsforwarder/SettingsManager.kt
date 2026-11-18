package com.example.smsforwarder

import android.content.Context
import android.content.SharedPreferences
import com.example.smsforwarder.BuildConfig

object SettingsManager {

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("sms_forwarder_prefs", Context.MODE_PRIVATE)
    }

    // Токен MAX-бота
    val maxBotToken: String?
        get() = BuildConfig.MAX_BOT_TOKEN.takeIf { it.isNotEmpty() }

    // Chat ID для MAX
    val maxChatId: Long
        get() = BuildConfig.MAX_CHAT_ID.toLongOrNull() ?: 0L

    // Проверка: настроен ли MAX
    fun isMaxConfigured(): Boolean = !maxBotToken.isNullOrEmpty() && maxChatId > 0
}