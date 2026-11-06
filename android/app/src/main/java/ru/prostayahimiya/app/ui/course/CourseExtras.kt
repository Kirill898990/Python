package ru.prostayahimiya.app.ui.course

import android.media.Ringtone
import android.media.RingtoneManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.prostayahimiya.app.call.CallActivity
import ru.prostayahimiya.app.data.Enrollment
import ru.prostayahimiya.app.data.IncomingCall
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.data.Review
import ru.prostayahimiya.app.ui.components.openLink

/*
 * Дополнения к курсам: звонок учителю/ученику, входящий звонок, напоминание о продлении и отзыв о курсе.
 */

/** Кнопки «Позвонить» и «Видеозвонок» (звонок открывается в отдельном окне). */
@Composable
fun CallButtons(repository: Repository, enrollment: Enrollment, whom: String) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { CallActivity.open(context, repository.callUrl(enrollment.id, video = false), video = false) },
            modifier = Modifier.weight(1f).semantics { contentDescription = "Позвонить $whom" },
        ) { Text("📞 Позвонить", maxLines = 1) }
        OutlinedButton(
            onClick = { CallActivity.open(context, repository.callUrl(enrollment.id, video = true), video = true) },
            modifier = Modifier.weight(1f),
        ) { Text("🎥 Видеозвонок", maxLines = 1) }
    }
}

/** Курс скоро закончится: напоминание с кнопкой «Продлить» (купить можно только на сайте). */
@Composable
fun RenewBanner(repository: Repository, enrollment: Enrollment) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "⏳ Курс закончится через ${enrollment.leftText}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "Продлите его на сайте, чтобы задания и чат с учителем не закрылись. Новый срок прибавится к оставшимся дням.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(onClick = { context.openLink(repository.shopProductUrl(enrollment.product)) }, modifier = Modifier.fillMaxWidth()) {
                Text("Продлить курс")
            }
        }
    }
}

/**
 * Входящий звонок. Пока приложение открыто, раз в 4 секунды спрашиваем сервер, не звонит ли кто-нибудь
 * (только тем, у кого есть курс). Если звонят — окно «Ответить / Отклонить» и мелодия звонка.
 */
@Composable
fun IncomingCallWatcher(repository: Repository) {
    val user by repository.user.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var call by remember { mutableStateOf<IncomingCall?>(null) }
    val handled = remember { mutableSetOf<String>() }

    LaunchedEffect(user?.id) {
        if (user == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                if (repository.hasCourses()) {
                    val now = runCatching { repository.incomingCall() }.getOrNull()
                    call = now?.takeIf { it.id !in handled }
                }
                delay(4000)
            }
        }
    }

    val current = call ?: return
    DisposableEffect(current.id) {
        val tone: Ringtone? = runCatching {
            RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.also { it.play() }
        }.getOrNull()
        onDispose { runCatching { tone?.stop() } }
    }
    val who = current.name.ifBlank { if (user?.isTeacher == true) "Ученик курса" else "Учитель" }
    AlertDialog(
        onDismissRequest = {},
        icon = { Text(if (current.video) "🎥" else "📞", fontSize = 36.sp) },
        title = { Text(who) },
        text = { Text(if (current.video) "Видеозвонок по курсу" else "Звонок по курсу") },
        confirmButton = {
            Button(
                onClick = {
                    handled += current.id
                    call = null
                    CallActivity.open(context, repository.callUrl(current.enrollment, current.video, answer = current), current.video)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E9E57), contentColor = Color.White),
            ) { Text("Ответить") }
        },
        dismissButton = {
            Button(
                onClick = {
                    handled += current.id
                    call = null
                    scope.launch { runCatching { repository.declineCall(current) } }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD93025), contentColor = Color.White),
            ) { Text("Отклонить") }
        },
    )
}

/** Отзыв о курсе: оставить может только тот, кто его купил. Отзывы видны всем в магазине на сайте. */
@Composable
fun ReviewCard(repository: Repository, enrollment: Enrollment) {
    if (enrollment.product.isBlank()) return
    val scope = rememberCoroutineScope()
    var review by remember { mutableStateOf<Review?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enrollment.product) {
        review = runCatching { repository.myReview(enrollment.product) }.getOrNull()
        loaded = true
    }
    if (!loaded) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val r = review
            when {
                editing -> ReviewForm(
                    initial = r,
                    onCancel = { editing = false },
                    onSave = { rating, text ->
                        runCatching { repository.saveReview(enrollment.product, rating, text, r?.id.orEmpty()) }
                            .onSuccess { review = it; editing = false }
                            .exceptionOrNull()?.message
                    },
                )
                r == null -> {
                    Text("⭐ Как вам курс?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Оставьте отзыв — его увидят другие ученики в магазине beemei.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { editing = true }) { Text("Оставить отзыв") }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ваш отзыв", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Stars(r.rating)
                    }
                    if (r.text.isNotBlank()) Text(r.text, style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(onClick = { editing = true }) { Text("Изменить") }
                        TextButton(onClick = {
                            scope.launch { runCatching { repository.deleteReview(r.id) }.onSuccess { review = null } }
                        }) { Text("Удалить") }
                    }
                }
            }
        }
    }
}

private val GOLD = Color(0xFFF5A800)

@Composable
fun Stars(rating: Int) {
    Text(
        buildString { repeat(rating) { append('★') } },
        color = GOLD,
        fontSize = 18.sp,
        modifier = Modifier.semantics { contentDescription = "Оценка $rating из 5" },
    )
    Text(buildString { repeat(5 - rating) { append('★') } }, color = MaterialTheme.colorScheme.outlineVariant, fontSize = 18.sp)
}

/** Оценка звёздами и текст отзыва (прямо в карточке курса). */
@Composable
private fun ReviewForm(initial: Review?, onCancel: () -> Unit, onSave: suspend (Int, String) -> String?) {
    val scope = rememberCoroutineScope()
    var rating by rememberSaveable { mutableIntStateOf(initial?.rating ?: 0) }
    var text by rememberSaveable { mutableStateOf(initial?.text.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Text(if (initial == null) "Отзыв о курсе" else "Изменить отзыв", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { n ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .selectable(selected = n == rating, role = Role.RadioButton, onClick = { rating = n; error = null })
                    .semantics { contentDescription = "$n из 5" },
            ) {
                Text("★", fontSize = 32.sp, color = if (n <= rating) GOLD else MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(2000) },
        label = { Text("Ваш отзыв") },
        placeholder = { Text("Что понравилось, что можно сделать лучше") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !busy, onClick = {
            if (rating == 0) {
                error = "Поставьте оценку — от 1 до 5 звёзд"
                return@Button
            }
            busy = true
            scope.launch {
                error = onSave(rating, text)
                busy = false
            }
        }) { Text(if (initial == null) "Отправить" else "Сохранить") }
        TextButton(onClick = onCancel) { Text("Отмена") }
    }
}
