package com.example.smsforwarder

// SmsData.kt
data class SmsData(
    val sender: String,        // от кого
    val body: String,          // текст сообщения
    val timestamp: Long,       // время получения
    val simSlot: Int,          // слот SIM (0 или 1)
    val simDisplayName: String // имя SIM (оператор)
)