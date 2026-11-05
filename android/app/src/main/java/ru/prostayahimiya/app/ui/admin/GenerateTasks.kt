package ru.prostayahimiya.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import ru.prostayahimiya.app.chem.Chem
import ru.prostayahimiya.app.chem.parseChoice
import ru.prostayahimiya.app.data.GeneratedTasks
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.data.TaskType
import ru.prostayahimiya.app.ui.components.cardBorder
import ru.prostayahimiya.app.ui.components.cardColors
import ru.prostayahimiya.app.ui.components.tasksWord
import kotlin.coroutines.cancellation.CancellationException

/**
 * «Сгенерировать нейросетью»: учитель пишет запрос, YandexGPT составляет задания, сервер их проверяет,
 * учитель снимает галочки с ненужных и добавляет остальные в практику. Так же работает сайт.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenerateTasksDialog(
    practiceId: String,
    nextPosition: Int,
    repository: Repository,
    onDismiss: () -> Unit,
    onAdded: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var prompt by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableIntStateOf(10) }
    var types by remember { mutableStateOf(setOf(TaskType.CHOICE, TaskType.FILL, TaskType.FORMULA, TaskType.MOLAR, TaskType.EQUATION)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<GeneratedTasks?>(null) }
    var chosen by remember { mutableStateOf(emptySet<Int>()) }
    var saving by remember { mutableStateOf(false) }

    fun generate() {
        if (prompt.trim().length < 3) {
            error = "Напишите, какие задания нужны: тему и что проверить"
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                val r = repository.generateTasks(practiceId, prompt, count, types.sortedBy { it.ordinal })
                result = r
                chosen = r.tasks.indices.toSet()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Не получилось"
            }
            loading = false
        }
    }

    fun add() {
        val r = result ?: return
        saving = true
        scope.launch {
            var added = 0
            try {
                chosen.sorted().forEachIndexed { i, index ->
                    repository.saveTask(r.tasks[index].copy(practice = practiceId, position = nextPosition + i))
                    added++
                }
                onAdded(added)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = (e.message ?: "Ошибка") + if (added > 0) " (добавлено $added)" else ""
            }
            saving = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!loading && !saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 16.dp),
        title = { Text("✨ Задания от нейросети") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            ) {
                val r = result
                when {
                    loading -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                        CircularProgressIndicator()
                        Text(
                            "Нейросеть составляет задания… Обычно это 10–40 секунд.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    r != null -> {
                        Text(
                            "Нейросеть предложила ${r.tasks.size} ${tasksWord(r.tasks.size)}." +
                                (if (r.dropped > 0) " Ещё ${r.dropped} с ошибками приложение убрало само." else "") +
                                " Снимите галочки с ненужных.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        r.tasks.forEachIndexed { i, task ->
                            val checked = i in chosen
                            Card(colors = cardColors(), border = cardBorder()) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.fillMaxWidth()
                                        .toggleable(checked) { chosen = if (it) chosen + i else chosen - i }
                                        .padding(8.dp),
                                ) {
                                    Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(4.dp))
                                    Column(Modifier.weight(1f).padding(start = 4.dp, top = 2.dp)) {
                                        Text(
                                            "${task.taskType.emoji()} ${task.taskType.shortTitle()}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            when (task.taskType) {
                                                TaskType.EQUATION -> task.equation?.pretty() ?: task.text
                                                TaskType.MOLAR -> "M(${Chem.pretty(task.text)})"
                                                else -> task.text
                                            },
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        if (task.taskType == TaskType.CHOICE) {
                                            val right = parseChoice(task.answer).toSet()
                                            task.options.forEachIndexed { k, o ->
                                                Text(
                                                    (if (k + 1 in right) "✅ " else "▫️ ") + Chem.prettyIfFormula(o),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                            }
                                        } else {
                                            task.answerSummary()?.let {
                                                Text("Ответ: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        r.left?.let {
                            Text("Осталось запросов к нейросети на сегодня: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> {
                        Text(
                            "Напишите тему и что проверить — нейросеть YandexGPT составит задания, а приложение проверит формулы, " +
                                "уравнения и ответы. Ничего не добавится, пока вы сами не выберете.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it.take(1000); error = null },
                            label = { Text("Что нужно") },
                            placeholder = { Text("Например: оксиды — классификация, формулы по названию, уравнения получения") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Сколько заданий", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 10, 15).forEach { n -> FilterChip(selected = count == n, onClick = { count = n }, label = { Text("$n") }) }
                        }
                        Text("Какие задания", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TaskType.entries.forEach { type ->
                                FilterChip(
                                    selected = type in types,
                                    onClick = { types = if (type in types && types.size > 1) types - type else types + type },
                                    label = { Text("${type.emoji()} ${type.shortTitle()}") },
                                )
                            }
                        }
                    }
                }
                error?.let {
                    Box(Modifier.fillMaxWidth()) {
                        Text("❌ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            when {
                loading -> Unit
                result != null -> TextButton(onClick = ::add, enabled = chosen.isNotEmpty() && !saving) {
                    Text(if (saving) "Добавляем…" else "Добавить выбранные (${chosen.size})")
                }
                else -> TextButton(onClick = ::generate) { Text("✨ Сгенерировать") }
            }
        },
        dismissButton = {
            if (!loading) {
                if (result != null && !saving) {
                    TextButton(onClick = { result = null; error = null }) { Text("Ещё раз") }
                } else {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Отмена") }
                }
            }
        },
    )
}
