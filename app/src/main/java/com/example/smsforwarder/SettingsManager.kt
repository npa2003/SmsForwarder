package com.example.smsforwarder

import android.content.Context
import android.content.SharedPreferences
import com.example.smsforwarder.BuildConfig

object SettingsManager {

    private const val PREFS_NAME = "sms_forwarder_prefs"
    private const val KEY_FORWARD_NUMBER = "forward_number"
    private const val KEY_MAX_BOT_TOKEN = "max_bot_token"
    private const val KEY_MAX_CHAT_ID = "max_chat_id"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Номер для пересылки — по умолчанию из BuildConfig
    var forwardNumber: String
        get() = prefs.getString(KEY_FORWARD_NUMBER, BuildConfig.FORWARD_NUMBER) ?: BuildConfig.FORWARD_NUMBER
        set(value) = prefs.edit().putString(KEY_FORWARD_NUMBER, value).apply()

    // Токен MAX-бота
    var maxBotToken: String?
        get() = prefs.getString(KEY_MAX_BOT_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_MAX_BOT_TOKEN, value).apply()

    // Chat ID для MAX
    var maxChatId: Long
        get() = prefs.getLong(KEY_MAX_CHAT_ID, 0L)
        set(value) = prefs.edit().putLong(KEY_MAX_CHAT_ID, value).apply()

    // Проверка: настроен ли MAX
    fun isMaxConfigured(): Boolean = !maxBotToken.isNullOrEmpty() && maxChatId > 0
}
