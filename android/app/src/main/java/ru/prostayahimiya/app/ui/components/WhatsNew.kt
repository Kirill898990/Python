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

/** Что появилось в этой версии: значок, заголовок и пояснение. */
private val WHATS_NEW = listOf(
    Triple("📞", "Звонки с учителем", "В курсе «Подготовка» можно позвонить учителю голосом или по видео прямо из приложения."),
    Triple("⏳", "Напоминание о конце курса", "Приложение предупредит, когда курс скоро закончится, и подскажет, как его продлить."),
    Triple("🛍️", "Мои покупки", "В личном кабинете — все заказы из магазина beemei и до какого дня открыт курс."),
    Triple("⭐", "Отзывы о курсе", "Если вы купили курс, можно поставить ему оценку и написать отзыв."),
    Triple("✅", "Без лишних копий", "Одну и ту же работу больше нельзя случайно отправить учителю много раз."),
)

@Composable
fun WhatsNewDialog(repository: Repository) {
    var show by remember { mutableStateOf(repository.whatsNewToShow()) }
    if (!show) return
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
                WHATS_NEW.forEach { (emoji, title, text) ->
                    Row {
                        Text(emoji, fontSize = 22.sp, modifier = Modifier.padding(end = 12.dp))
                        Column {
                            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = close) { Text("Понятно") } },
    )
}
