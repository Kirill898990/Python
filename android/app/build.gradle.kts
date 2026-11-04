import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ===== АДРЕС СЕРВЕРА =====
// https://урокискириллом.рф (русские домены в программах пишутся так, в виде «punycode»).
val releaseServerUrl = "https://xn--h1aaafbiaikhnfkx.xn--p1ai"
// Отладочная версия по умолчанию тоже работает с сервером в интернете.
// Чтобы проверить с сервером на своём компьютере, добавьте в файл local.properties строку
//   server.url=http://10.0.2.2:8090          (для эмулятора)
//   server.url=http://IP-компьютера:8090     (для телефона в той же Wi-Fi сети)
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val debugServerUrl = localProperties.getProperty("server.url") ?: releaseServerUrl

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
        // 36, а не 37: приложения под Android 17 без отдельного разрешения не могут подключаться
        // к компьютеру в локальной сети (например, к серверу на своём ПК). RuStore это не мешает.
        targetSdk = 36
        // Перед каждой новой загрузкой в RuStore увеличивайте versionCode на 1.
        versionCode = 3
        versionName = "1.2"
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
