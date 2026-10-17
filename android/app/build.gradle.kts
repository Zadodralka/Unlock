plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// В CI номер версии растёт с каждой сборкой — по нему приложение понимает,
// что на GitHub лежит версия новее установленной
val ciBuild = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

android {
    namespace = "app.unlock"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.unlock"
        minSdk = 28
        targetSdk = 35
        versionCode = ciBuild?.let { 100 + it } ?: 1
        versionName = ciBuild?.let { "0.2.$it" } ?: "0.2-dev"

        // Ядро собрано только под ARM — это все реальные телефоны
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        // Ключ лежит в репозитории, чтобы каждая сборка из CI ставилась поверх
        // предыдущей без удаления. Для публикации нужен отдельный, секретный ключ.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // Сжимаем .so ядра: APK втрое меньше (важно для обновлений по сети),
        // Android распакует их при установке
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(files("libs/libXray.aar"))

    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Сканер QR: Google Code Scanner (через Play Services), ZXing — запасной
    // и для распознавания QR с картинки из галереи
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
