import java.io.FileInputStream
import java.util.Properties

// Загружаем переменные из .env файла (ищем в корне проекта)
val envFile = rootProject.file(".env")
val envProperties = Properties()
if (envFile.exists()) {
    envProperties.load(FileInputStream(envFile))
}

// Получаем номер или используем значение по умолчанию
val forwardNumber = envProperties.getProperty("FORWARD_NUMBER")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.smsforwarder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.smsforwarder"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true  // ЭТО ДОЛЖНО БЫТЬ!
    }

    defaultConfig {
        // ... другие настройки
        buildConfigField("String", "FORWARD_NUMBER", "\"+79169641310\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}