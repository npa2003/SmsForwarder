package com.example.smsforwarder

import android.content.Context
import android.content.SharedPreferences
import com.example.smsforwarder.BuildConfig

object SettingsManager {

    private const val PREFS_NAME = "sms_forwarder_prefs"
    private const val KEY_FORWARD_NUMBER = "forward_number"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Номер для пересылки — по умолчанию из BuildConfig
    var forwardNumber: String
        get() = prefs.getString(KEY_FORWARD_NUMBER, BuildConfig.FORWARD_NUMBER) ?: BuildConfig.FORWARD_NUMBER
        set(value) = prefs.edit().putString(KEY_FORWARD_NUMBER, value).apply()
}
