package ru.prostayahimiya.app

import android.app.Application
import ru.prostayahimiya.app.data.Notifier
import ru.prostayahimiya.app.data.Repository

class HimiyaApp : Application() {
    /** Одно хранилище данных на всё приложение. */
    val repository: Repository by lazy { Repository(this) }

    /** Сколько экранов приложения сейчас видно. Пока приложение открыто, уведомления не показываем. */
    private var visibleScreens = 0
    val inForeground: Boolean get() = visibleScreens > 0

    fun onScreenShown() { visibleScreens++ }
    fun onScreenHidden() { visibleScreens = (visibleScreens - 1).coerceAtLeast(0) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        Notifier.schedule(this)
    }
}
