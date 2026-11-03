import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ===== АДРЕС СЕРВЕРА =====
// Перед сборкой для RuStore впишите сюда адрес своего сервера PocketBase (см. server/README.md).
val releaseServerUrl = "https://ВАШ-ДОМЕН.ru"
// Для отладки: эмулятор Android видит ваш компьютер по адресу 10.0.2.2.
// Чтобы проверять на телефоне, добавьте в файл local.properties строку
//   server.url=http://IP-адрес-компьютера:8090
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val debugServerUrl = localProperties.getProperty("server.url") ?: "http://10.0.2.2:8090"

// Данные для подписи release-сборки берутся из файла keystore.properties (его нет в Git).
// Как его создать, описано в README.md.
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply {
    if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}

android {
    namespace = "ru.prostayahimiya.app"
    compileSdk = 37

    defaultConfig {
        // Имя пакета: после первой публикации в RuStore его менять нельзя.
        applicationId = "ru.prostayahimiya.app"
        minSdk = 26
        targetSdk = 37
        // Перед каждой новой загрузкой в RuStore увеличивайте versionCode на 1.
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "SERVER_URL", "\"$debugServerUrl\"")
        }
        release {
            buildConfigField("String", "SERVER_URL", "\"$releaseServerUrl\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.browser)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
