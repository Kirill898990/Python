// Общие настройки для всех модулей проекта.
plugins {
    alias(libs.plugins.android.application) apply false
    // Kotlin встроен в Android Gradle Plugin 9, эта строка только закрепляет версию Kotlin.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
