package com.example.smsforwarder // замени на свой package!

import android.Manifest
import android.content.Intent
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
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_PHONE_STATE
    ).let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            it + Manifest.permission.READ_PHONE_NUMBERS
        } else it
    }

    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val settingsText = findViewById<TextView>(R.id.settingsText)
        val checkPermsBtn = findViewById<Button>(R.id.checkPermsBtn)
        val startServiceBtn = findViewById<Button>(R.id.startServiceBtn)

        // Показываем настройки
        updateSettingsDisplay(settingsText)

        // Проверяем разрешения при запуске
        updateStatus(statusText)

        checkPermsBtn.setOnClickListener {
            checkPermissions()
            updateStatus(statusText)
        }

        startServiceBtn.setOnClickListener {
            if (hasAllPermissions()) {
                startForwarderService()
                statusText.text = "✅ Сервис запущен"
                Toast.makeText(this, "Сервис запущен", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Сначала дайте все разрешения", Toast.LENGTH_SHORT).show()
                requestPermissions()
            }
        }
    }

    private fun updateSettingsDisplay(textView: TextView) {
        val simTarget = if (SmsReceiver.TARGET_SIM_SLOT == 0) "ПЕРВАЯ SIM" else "ВТОРАЯ SIM"
        textView.text = """
            Отслеживается: $simTarget
            Пересылка на: ${SmsReceiver.FORWARD_NUMBER}
            
            Убедитесь что:
            • Разрешения выданы
            • Приложение не в спящем режиме
            • Дано разрешение на автозапуск
        """.trimIndent()
    }

    private fun updateStatus(textView: TextView) {
        textView.text = if (hasAllPermissions()) {
            "✅ Статус: Все разрешения получены\nСервис готов к работе"
        } else {
            "❌ Статус: Нет разрешений\nНажмите кнопку проверки"
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
            Toast.makeText(this, "✅ Все разрешения уже есть", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE)
    }

    private fun startForwarderService() {
        val intent = Intent(this, ForwarderService::class.java).apply {
            action = "START_FOREGROUND"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
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
                statusText.text = "✅ Все разрешения получены!"
                Toast.makeText(this, "Спасибо! Сервис готов к работе", Toast.LENGTH_SHORT).show()
            } else {
                statusText.text = "❌ Некоторые разрешения не получены"
                Toast.makeText(this, "Без разрешений пересылка не будет работать", Toast.LENGTH_LONG).show()
            }
        }
    }
}