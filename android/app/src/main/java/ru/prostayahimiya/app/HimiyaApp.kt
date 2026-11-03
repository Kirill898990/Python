package ru.prostayahimiya.app

import android.app.Application
import ru.prostayahimiya.app.data.Repository

class HimiyaApp : Application() {
    /** Одно хранилище данных на всё приложение. */
    val repository: Repository by lazy { Repository(this) }
}
