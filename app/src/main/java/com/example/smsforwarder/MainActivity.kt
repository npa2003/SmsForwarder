package com.example.smsforwarder

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            it + Manifest.permission.POST_NOTIFICATIONS
        } else it
    }

    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.init(this)

        setContentView(R.layout.activity_main)

        val serviceStatusText = findViewById<TextView>(R.id.serviceStatusText)
        val statusText = findViewById<TextView>(R.id.statusText)
        val settingsText = findViewById<TextView>(R.id.settingsText)
        val startBtn = findViewById<Button>(R.id.startBtn)
        val stopBtn = findViewById<Button>(R.id.stopBtn)
        val checkPermsBtn = findViewById<Button>(R.id.checkPermsBtn)
        val saveNumberBtn = findViewById<Button>(R.id.saveNumberBtn)
        val forwardNumberEdit = findViewById<EditText>(R.id.forwardNumberEdit)

        // Загружаем текущий номер в поле ввода
        forwardNumberEdit.setText(SettingsManager.forwardNumber)

        // Обновляем статус сервиса
        updateServiceStatus(serviceStatusText)

        // Отображаем настройки
        updateSettingsDisplay(settingsText)

        // Проверяем разрешения при запуске
        updateStatus(statusText)

        // Кнопка запуска сервиса
        startBtn.setOnClickListener {
            startForwarderService()
            updateServiceStatus(serviceStatusText)
            Toast.makeText(this, "Сервис запущен", Toast.LENGTH_SHORT).show()
        }

        // Кнопка остановки сервиса
        stopBtn.setOnClickListener {
            stopForwarderService()
            updateServiceStatus(serviceStatusText)
            Toast.makeText(this, "Сервис остановлен", Toast.LENGTH_SHORT).show()
        }

        checkPermsBtn.setOnClickListener {
            checkPermissions()
            updateStatus(statusText)
        }

        saveNumberBtn.setOnClickListener {
            val newNumber = forwardNumberEdit.text.toString().trim()
            if (newNumber.isNotEmpty()) {
                SettingsManager.forwardNumber = newNumber
                updateSettingsDisplay(settingsText)
                Toast.makeText(this, "Номер сохранён: $newNumber", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Введите номер телефона", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateServiceStatus(textView: TextView) {
        if (isServiceRunning(ForwarderService::class.java)) {
            textView.text = "Сервис: запущен"
            textView.setTextColor(0xFF4CAF50.toInt())
        } else {
            textView.text = "Сервис: не запущен"
            textView.setTextColor(0xFFf44336.toInt())
        }
    }

    private fun updateSettingsDisplay(textView: TextView) {
        val forwardNum = SettingsManager.forwardNumber
        textView.text = buildString {
            append("Пересылка на: $forwardNum\n\n")
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

    private fun startForwarderService() {
        val intent = Intent(this, ForwarderService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopForwarderService() {
        val intent = Intent(this, ForwarderService::class.java)
        stopService(intent)
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val services = activityManager.getRunningServices(Int.MAX_VALUE)
        for (service in services) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        // Обновляем статус при возвращении в приложение
        updateServiceStatus(findViewById(R.id.serviceStatusText))
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