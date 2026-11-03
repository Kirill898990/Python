package ru.prostayahimiya.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Фирменный зелёный цвет взят с сайта (#1e8e6f), чуть затемнён, чтобы белый текст на кнопках хорошо читался.
private val LightColors = lightColorScheme(
    primary = Color(0xFF187A5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8EFD9),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF4C6358),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEE9DA),
    onSecondaryContainer = Color(0xFF082017),
    tertiary = Color(0xFF2F6577),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDE9F4),
    onTertiaryContainer = Color(0xFF001F28),
    background = Color(0xFFF5F6F5),
    onBackground = Color(0xFF1A1C1B),
    surface = Color(0xFFF5F6F5),
    onSurface = Color(0xFF1A1C1B),
    surfaceVariant = Color(0xFFDCE5E0),
    onSurfaceVariant = Color(0xFF55605B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEFF1F0),
    surfaceContainerHigh = Color(0xFFE9ECEA),
    surfaceContainerHighest = Color(0xFFE3E6E4),
    outline = Color(0xFF707975),
    outlineVariant = Color(0xFFDDE2DF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DD8B5),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF00513C),
    onPrimaryContainer = Color(0xFFB8EFD9),
    secondary = Color(0xFFB3CCBF),
    onSecondary = Color(0xFF1E352B),
    secondaryContainer = Color(0xFF344B41),
    onSecondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFF9ACEE1),
    onTertiary = Color(0xFF003545),
    tertiaryContainer = Color(0xFF124D5E),
    onTertiaryContainer = Color(0xFFCDE9F4),
    background = Color(0xFF111413),
    onBackground = Color(0xFFE1E3E1),
    surface = Color(0xFF111413),
    onSurface = Color(0xFFE1E3E1),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C3),
    surfaceContainerLowest = Color(0xFF0C0F0E),
    surfaceContainerLow = Color(0xFF1A1D1C),
    surfaceContainer = Color(0xFF1E2120),
    surfaceContainerHigh = Color(0xFF282B2A),
    surfaceContainerHighest = Color(0xFF333635),
    outline = Color(0xFF89938E),
    outlineVariant = Color(0xFF3A423F),
)

@Composable
fun HimiyaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
