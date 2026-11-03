package ru.prostayahimiya.app

import android.app.Application
import ru.prostayahimiya.app.data.ProgressStore

class HimiyaApp : Application() {
    /** Одно хранилище прогресса на всё приложение. */
    val store: ProgressStore by lazy { ProgressStore(this) }
}
