package com.example.smsforwarder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_PHONE_STATE
    ).let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            it + Manifest.permission.POST_NOTIFICATIONS
        } else it
    }

    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.init(this)

        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val settingsText = findViewById<TextView>(R.id.settingsText)
        val checkPermsBtn = findViewById<Button>(R.id.checkPermsBtn)

        // Отображаем настройки
        updateSettingsDisplay(settingsText)

        // Проверяем разрешения при запуске
        updateStatus(statusText)

        checkPermsBtn.setOnClickListener {
            checkPermissions()
            updateStatus(statusText)
        }
    }

    private fun updateSettingsDisplay(textView: TextView) {
        textView.text = buildString {
            append("MAX-бот настроен: " + if (SettingsManager.isMaxConfigured()) "да" else "нет")
            append("\n\n")
            append("Убедитесь что:\n")
            append("• Разрешения выданы\n")
            append("• Приложение не в спящем режиме\n")
            append("• Дано разрешение на автозапуск")
        }
    }

    private fun updateStatus(textView: TextView) {
        textView.text = if (hasAllPermissions()) {
            "Все разрешения получены\nПриложение готово к работе"
        } else {
            "Нет разрешений\nНажмите кнопку проверки"
        }
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkPermissions() {
        if (!hasAllPermissions()) {
            requestPermissions()
        } else {
            Toast.makeText(this, "Все разрешения уже есть", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE)
    }

    override fun onResume() {
        super.onResume()
        // Обновляем статус при возвращении в приложение
        updateStatus(findViewById(R.id.statusText))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            val statusText = findViewById<TextView>(R.id.statusText)

            if (allGranted) {
                statusText.text = "Все разрешения получены!\nПриложение готово к работе"
                Toast.makeText(this, "Спасибо! Приложение готово", Toast.LENGTH_SHORT).show()
            } else {
                statusText.text = "Некоторые разрешения не получены"
                Toast.makeText(this, "Без разрешений пересылка не будет работать", Toast.LENGTH_LONG).show()
            }
        }
    }
}