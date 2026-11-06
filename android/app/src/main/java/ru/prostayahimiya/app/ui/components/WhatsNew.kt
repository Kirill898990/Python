package ru.prostayahimiya.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.prostayahimiya.app.BuildConfig
import ru.prostayahimiya.app.data.Repository

/*
 * Окно «Что нового»: один раз после обновления приложения показывает, что появилось в новой версии.
 * После новой установки не показывается. Чтобы рассказать о следующей версии, поменяйте список ниже.
 */

/** Что появилось в новых версиях: номер версии (versionCode), значок, заголовок и пояснение. */
private class News(val version: Int, val emoji: String, val title: String, val text: String)

private val WHATS_NEW = listOf(
    News(8, "🔊", "Звонки стали слышнее", "Звук сразу идёт на громкую связь, а полоски «Вы» и «Собеседник» показывают, что звук доходит."),
    News(7, "📞", "Звонки с учителем", "В курсе «Подготовка» можно позвонить учителю голосом или по видео прямо из приложения."),
    News(7, "⏳", "Напоминание о конце курса", "Приложение предупредит, когда курс скоро закончится, и подскажет, как его продлить."),
    News(7, "🛍️", "Мои покупки", "В личном кабинете — все заказы из магазина beemei и до какого дня открыт курс."),
    News(7, "⭐", "Отзывы о курсе", "Если вы купили курс, можно поставить ему оценку и написать отзыв."),
    News(7, "✅", "Без лишних копий", "Одну и ту же работу больше нельзя случайно отправить учителю много раз."),
)

@Composable
fun WhatsNewDialog(repository: Repository) {
    var show by remember { mutableStateOf(repository.whatsNewToShow()) }
    // Только то, чего человек ещё не видел: обновился с 1.6 — одно новое, со старой версии — всё.
    val items = remember { WHATS_NEW.filter { it.version > repository.whatsNewSeen() && it.version <= BuildConfig.VERSION_CODE } }
    if (!show || items.isEmpty()) return
    val close = {
        repository.markWhatsNewSeen()
        show = false
    }
    AlertDialog(
        onDismissRequest = close,
        icon = { Text("🎉", fontSize = 36.sp) },
        title = { Text("Что нового в версии ${BuildConfig.VERSION_NAME}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items.forEach { item ->
                    Row {
                        Text(item.emoji, fontSize = 22.sp, modifier = Modifier.padding(end = 12.dp))
                        Column {
                            Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(item.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = close) { Text("Понятно") } },
    )
}
