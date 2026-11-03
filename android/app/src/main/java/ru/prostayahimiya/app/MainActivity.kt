package ru.prostayahimiya.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.prostayahimiya.app.ui.AppNavigation
import ru.prostayahimiya.app.ui.theme.HimiyaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repository = (application as HimiyaApp).repository
        setContent {
            HimiyaTheme {
                AppNavigation(repository)
            }
        }
    }
}
