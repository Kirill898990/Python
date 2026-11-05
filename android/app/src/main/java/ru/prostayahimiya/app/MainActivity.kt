package ru.prostayahimiya.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.prostayahimiya.app.data.Notifier
import ru.prostayahimiya.app.ui.AppNavigation
import ru.prostayahimiya.app.ui.theme.HimiyaTheme

class MainActivity : ComponentActivity() {
    private val app get() = application as HimiyaApp

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repository = app.repository
        if (savedInstanceState == null) openFrom(intent)
        setContent {
            HimiyaTheme {
                AppNavigation(repository)
            }
        }
    }

    /** Нажали на уведомление, когда приложение уже открыто. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFrom(intent)
    }

    override fun onStart() {
        super.onStart()
        app.onScreenShown()
    }

    override fun onStop() {
        app.onScreenHidden()
        super.onStop()
    }

    private fun openFrom(intent: Intent?) {
        intent?.getStringExtra(Notifier.EXTRA_OPEN)?.takeIf { it.isNotBlank() }?.let { app.repository.openRequest.value = it }
    }
}
