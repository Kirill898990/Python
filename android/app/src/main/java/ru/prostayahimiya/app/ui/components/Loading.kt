package ru.prostayahimiya.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.coroutines.cancellation.CancellationException

/** Состояние загрузки данных с сервера. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

@Stable
class Loader<T> {
    var state by mutableStateOf<Load<T>>(Load.Loading)
        internal set
    var refreshing by mutableStateOf(false)
        internal set
    internal var attempt by mutableIntStateOf(0)

    /** Загрузить заново (например, после ошибки или потянув список вниз). */
    fun reload() {
        attempt++
    }
}

/** Загружает данные при открытии экрана и при [Loader.reload]. Пока идёт повторная загрузка, старые данные остаются на экране. */
@Composable
fun <T> rememberLoader(vararg keys: Any?, load: suspend () -> T): Loader<T> {
    val loader = remember(*keys) { Loader<T>() }
    LaunchedEffect(loader, loader.attempt) {
        if (loader.state !is Load.Ok) loader.state = Load.Loading
        loader.refreshing = true
        loader.state = try {
            Load.Ok(load())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Failed(e.message ?: "Ошибка")
        }
        loader.refreshing = false
    }
    return loader
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("😕", fontSize = 40.sp)
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onRetry) { Text("Попробовать ещё раз") }
    }
}

@Composable
fun EmptyBox(emoji: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(emoji, fontSize = 40.sp)
        Text(
            text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Кружок с оценкой. 0 — «на проверке». */
@Composable
fun GradeBadge(grade: Int, size: Dp = 44.dp) {
    val scheme = MaterialTheme.colorScheme
    if (grade <= 0) {
        Surface(shape = RoundedCornerShape(50), color = scheme.surfaceContainerHighest) {
            Text(
                "На проверке",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        return
    }
    val (container, content) = when (grade) {
        5 -> scheme.primary to scheme.onPrimary
        4 -> scheme.primaryContainer to scheme.onPrimaryContainer
        3 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.errorContainer to scheme.onErrorContainer
    }
    Surface(shape = CircleShape, color = container, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                grade.toString(),
                color = content,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45f).sp,
            )
        }
    }
}

/** Плашка «Нет связи с сервером» над содержимым. */
@Composable
fun OfflineBanner(message: String, onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                "$message Показаны сохранённые задания.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            androidx.compose.material3.TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
                Text("Обновить")
            }
        }
    }
}
