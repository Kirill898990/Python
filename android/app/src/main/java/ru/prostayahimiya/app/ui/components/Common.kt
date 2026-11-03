package ru.prostayahimiya.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import ru.prostayahimiya.app.R
import ru.prostayahimiya.app.data.Subject

/** Открывает ссылку во встроенной вкладке браузера. */
fun Context.openLink(url: String) {
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, url.toUri())
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "Не найден браузер, чтобы открыть ссылку", Toast.LENGTH_LONG).show()
    }
}

/** Заголовок раздела зелёными заглавными буквами, как на сайте. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
    )
}

/** Цвета карточек: химия зелёная, биология сине-зелёная. */
data class AccentColors(val container: Color, val onContainer: Color, val accent: Color)

@Composable
fun Subject.colors(): AccentColors {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        Subject.CHEMISTRY -> AccentColors(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary)
        Subject.BIOLOGY -> AccentColors(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary)
    }
}

/** Квадратик с эмодзи, как логотип на сайте. */
@Composable
fun EmojiBadge(emoji: String, container: Color, size: Dp = 48.dp) {
    Surface(shape = RoundedCornerShape(12.dp), color = container, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = (size.value * 0.5f).sp)
        }
    }
}

/** Карточка с белым фоном и тонкой рамкой, как на сайте. */
@Composable
fun cardColors() = androidx.compose.material3.CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
)

@Composable
fun cardBorder() = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(
    title: String,
    onBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Назад")
            }
        },
        actions = { actions() },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

/** «3 задания», «5 заданий», «1 задание». */
fun tasksWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "заданий"
        mod10 == 1 -> "задание"
        mod10 in 2..4 -> "задания"
        else -> "заданий"
    }
}
