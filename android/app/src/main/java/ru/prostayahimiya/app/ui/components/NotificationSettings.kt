package ru.prostayahimiya.app.ui.components

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ru.prostayahimiya.app.data.Notifier
import ru.prostayahimiya.app.data.Repository

/** Что будет приходить в уведомлениях — для ученика и для учителя. */
private fun noticeWhat(teacher: Boolean) =
    if (teacher) "новые работы на проверку и сообщения учеников"
    else "новые задания и контрольные, оценки, сообщения в чате и напоминания о сроках сдачи"

/** Разрешение на уведомления: на Android 13+ его спрашивает система, раньше оно есть всегда. */
@Composable
private fun rememberNotificationAccess(onResult: (Boolean) -> Unit = {}): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    // Вернулись из настроек телефона — перепроверяем.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        tick++
        onResult(granted)
    }
    val permitted = remember(tick) { Notifier.permitted(context) }
    val request = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }
    return permitted to request
}

/** Карточка на главной: один раз предлагает включить уведомления. */
@Composable
fun NotificationsPrompt(repository: Repository, teacher: Boolean) {
    var asked by remember { mutableStateOf(repository.notificationsAsked) }
    val (permitted, request) = rememberNotificationAccess { asked = true; repository.notificationsAsked = true }
    if (asked || permitted || !repository.notificationsEnabled) return
    Card(colors = cardColors(), border = cardBorder()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge("🔔", MaterialTheme.colorScheme.primary)
                Text(
                    "Включить уведомления?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(
                "Приложение подскажет: ${noticeWhat(teacher)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = request) { Text("Включить") }
                TextButton(onClick = {
                    asked = true
                    repository.notificationsAsked = true
                }) { Text("Не сейчас") }
            }
        }
    }
}

/** Переключатель в личном кабинете. */
@Composable
fun NotificationsSettingCard(repository: Repository, teacher: Boolean) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(repository.notificationsEnabled) }
    val (permitted, request) = rememberNotificationAccess()
    Card(colors = cardColors(), border = cardBorder()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🔔 Уведомления", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            !enabled -> "Выключены"
                            !permitted -> "Нужно разрешить уведомления для приложения"
                            else -> "Приходят: ${noticeWhat(teacher)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled && !permitted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled && permitted,
                    onCheckedChange = { on ->
                        enabled = on
                        repository.notificationsEnabled = on
                        if (on) {
                            Notifier.schedule(context)
                            if (!permitted) request()
                        }
                    },
                )
            }
            if (enabled && !permitted) {
                TextButton(onClick = request, modifier = Modifier.padding(top = 4.dp)) { Text("Разрешить уведомления") }
            }
            if (enabled && permitted) {
                Text(
                    "Приложение проверяет новости примерно раз в 15 минут, когда есть интернет.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
