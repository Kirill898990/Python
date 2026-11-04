package ru.prostayahimiya.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import ru.prostayahimiya.app.chem.Chem
import ru.prostayahimiya.app.chem.Equation
import ru.prostayahimiya.app.chem.validateTask
import ru.prostayahimiya.app.data.Practice
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.data.Subject
import ru.prostayahimiya.app.data.Task
import ru.prostayahimiya.app.data.TaskType
import ru.prostayahimiya.app.data.parseCoefficients
import ru.prostayahimiya.app.ui.components.BackTopBar
import ru.prostayahimiya.app.ui.components.SectionLabel
import ru.prostayahimiya.app.ui.components.cardBorder
import ru.prostayahimiya.app.ui.components.cardColors

/** Создание и редактирование практики и её заданий (только для учителя). */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PracticeEditorScreen(practiceId: String, repository: Repository, onBack: () -> Unit) {
    val content by repository.content.collectAsState()
    var currentId by rememberSaveable { mutableStateOf(if (practiceId == NEW) "" else practiceId) }
    val original = content.practice(currentId) ?: Practice(grade = 8)

    var title by rememberSaveable(currentId) { mutableStateOf(original.title) }
    var subject by rememberSaveable(currentId) { mutableStateOf(original.subject) }
    var grade by rememberSaveable(currentId) { mutableIntStateOf(original.grade) }
    var published by rememberSaveable(currentId) { mutableStateOf(original.published) }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var deletingTask by remember { mutableStateOf<Task?>(null) }
    var deletingPractice by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tasks = if (currentId.isEmpty()) emptyList() else content.tasksOf(currentId)
    val changed = currentId.isEmpty() || title != original.title || subject != original.subject ||
        grade != original.grade || published != original.published

    /** Выполняет действие с сервером и показывает ошибку, если что-то пошло не так. */
    fun serverAction(success: String? = null, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
                success?.let { snackbar.showSnackbar(it) }
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "Ошибка")
            } finally {
                busy = false
            }
        }
    }

    fun savePractice() = serverAction("Практика сохранена") {
        val saved = repository.savePractice(
            original.copy(id = currentId, title = title, subject = subject, grade = grade, published = published),
        )
        currentId = saved.id
    }

    /** Меняет порядок заданий и сохраняет новые номера. */
    fun move(from: Int, to: Int) = serverAction {
        val reordered = tasks.toMutableList().apply { add(to, removeAt(from)) }
        reordered.forEachIndexed { index, task ->
            if (task.position != index + 1) repository.saveTask(task.copy(position = index + 1))
        }
    }

    Scaffold(
        topBar = {
            BackTopBar(if (currentId.isEmpty()) "Новая практика" else "Редактор практики", onBack) {
                TextButton(onClick = ::savePractice, enabled = changed && title.isNotBlank() && !busy) { Text("Сохранить") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = cardColors(), border = cardBorder()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Название практики") },
                            placeholder = { Text("Практика по химии") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Предмет", style = MaterialTheme.typography.labelLarge)
                        val subjects = listOf(Subject.CHEMISTRY, Subject.BIOLOGY)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            subjects.forEachIndexed { index, s ->
                                SegmentedButton(
                                    selected = subject == s.key,
                                    onClick = { subject = s.key },
                                    shape = SegmentedButtonDefaults.itemShape(index, subjects.size),
                                ) { Text("${s.emoji} ${s.title}") }
                            }
                        }
                        Text("Класс", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (5..11).forEach { g ->
                                FilterChip(selected = grade == g, onClick = { grade = g }, label = { Text("$g") })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Опубликовано", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (published) "Ученики видят эту практику" else "Черновик: ученики не видят",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = published, onCheckedChange = { published = it })
                        }
                        if (changed) {
                            Button(
                                onClick = ::savePractice,
                                enabled = title.isNotBlank() && !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (busy) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                } else {
                                    Text(if (currentId.isEmpty()) "Создать практику" else "Сохранить изменения")
                                }
                            }
                        }
                    }
                }
            }

            item { SectionLabel("Задания (${tasks.size})") }
            if (currentId.isEmpty()) {
                item {
                    Text(
                        "Сначала создайте практику, потом можно будет добавить задания.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                itemsIndexed(tasks, key = { _, t -> t.id }) { index, task ->
                    TaskRow(
                        task = task,
                        number = index + 1,
                        canMoveUp = index > 0,
                        canMoveDown = index < tasks.lastIndex,
                        onEdit = { editing = task },
                        onUp = { move(index, index - 1) },
                        onDown = { move(index, index + 1) },
                        onDelete = { deletingTask = task },
                    )
                }
                item {
                    Text("Добавить задание:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        TaskType.entries.forEach { type ->
                            OutlinedButton(onClick = {
                                editing = Task(
                                    practice = currentId,
                                    type = type.key,
                                    position = (tasks.maxOfOrNull { it.position } ?: 0) + 1,
                                )
                            }) { Text("+ ${type.shortTitle()}") }
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { deletingPractice = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    ) { Text("Удалить практику") }
                }
            }
        }
    }

    editing?.let { task ->
        TaskEditorDialog(
            task = task,
            onDismiss = { editing = null },
            onSave = { updated ->
                serverAction(if (task.id.isEmpty()) "Задание добавлено" else "Задание сохранено") {
                    repository.saveTask(updated)
                    editing = null
                }
            },
        )
    }

    deletingTask?.let { task ->
        AlertDialog(
            onDismissRequest = { deletingTask = null },
            title = { Text("Удалить задание?") },
            text = { Text(task.text, maxLines = 4, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = {
                    deletingTask = null
                    serverAction("Задание удалено") { repository.deleteTask(task.id) }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deletingTask = null }) { Text("Отмена") } },
        )
    }

    if (deletingPractice) {
        AlertDialog(
            onDismissRequest = { deletingPractice = false },
            title = { Text("Удалить практику?") },
            text = { Text("Удалятся практика и все её задания. Уже сданные работы и оценки учеников сохранятся.") },
            confirmButton = {
                TextButton(onClick = {
                    deletingPractice = false
                    serverAction {
                        repository.deletePractice(currentId)
                        onBack()
                    }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deletingPractice = false }) { Text("Отмена") } },
        )
    }
}

private fun TaskType.shortTitle() = when (this) {
    TaskType.QUESTION -> "Вопрос"
    TaskType.FORMULA -> "Формула"
    TaskType.EQUATION -> "Уравнение"
}

private fun TaskType.emoji() = when (this) {
    TaskType.QUESTION -> "❓"
    TaskType.FORMULA -> "🧪"
    TaskType.EQUATION -> "⚖️"
}

@Composable
private fun TaskRow(
    task: Task,
    number: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = onEdit, colors = cardColors(), border = cardBorder()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$number. ${task.taskType.emoji()} ${task.taskType.shortTitle()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when (task.taskType) {
                        TaskType.EQUATION -> task.equation?.pretty() ?: task.text
                        else -> task.text
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.taskType != TaskType.QUESTION) {
                    Text(
                        "Ответ: " + when (task.taskType) {
                            TaskType.FORMULA -> Chem.pretty(task.answer)
                            else -> task.equation?.pretty(task.coefficients) ?: task.answer
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column {
                IconButton(onClick = onUp, enabled = canMoveUp) { Text("▲") }
                IconButton(onClick = onDown, enabled = canMoveDown) { Text("▼") }
            }
            IconButton(onClick = onDelete) { Text("🗑️") }
        }
    }
}

/** Окно добавления и изменения задания. Ответ проверяется сразу, чтобы в задание не попала ошибка. */
@Composable
private fun TaskEditorDialog(task: Task, onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    val type = task.taskType
    var text by rememberSaveable(task.id) { mutableStateOf(task.text) }
    var answer by rememberSaveable(task.id) { mutableStateOf(task.answer) }
    val error = validateTask(type.key, text, answer)

    AlertDialog(
        onDismissRequest = onDismiss,
        // Окно на всю ширину экрана: так удобнее печатать, и ширина не «прыгает» вслед за полями ввода.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 16.dp),
        title = { Text((if (task.id.isEmpty()) "Новое задание: " else "Задание: ") + type.shortTitle().lowercase()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (type) {
                    TaskType.QUESTION -> OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Текст вопроса") },
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TaskType.FORMULA -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Название вещества") },
                            placeholder = { Text("хлорид алюминия") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = answer,
                            onValueChange = { answer = it },
                            label = { Text("Правильная формула") },
                            placeholder = { Text("AlCl3") },
                            supportingText = { if (answer.isNotBlank()) Text("Получается: ${Chem.pretty(Chem.normalize(answer))}") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    TaskType.EQUATION -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Уравнение без коэффициентов") },
                            placeholder = { Text("Al + O2 = Al2O3") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = answer,
                            onValueChange = { answer = it },
                            label = { Text("Коэффициенты по порядку") },
                            placeholder = { Text("4 3 2") },
                            supportingText = {
                                val equation = Equation.parse(text)
                                val coefficients = parseCoefficients(answer)
                                if (equation != null && coefficients?.size == equation.substances.size) {
                                    Text("Получается: ${equation.pretty(coefficients)}")
                                } else {
                                    Text("Через пробел, для каждого вещества. 1 тоже пишите.")
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (error != null && (text.isNotBlank() || answer.isNotBlank())) {
                    Text("⚠️ $error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = error == null,
                onClick = { onSave(task.copy(text = text, answer = answer, hint = "").normalized()) },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Приводит задание к единому виду перед сохранением: «al + o2 -> al2o3» не пройдёт проверку, а «Al+O2→Al2O3» станет «Al + O2 = Al2O3». */
private fun Task.normalized(): Task = when (taskType) {
    TaskType.QUESTION -> copy(answer = "", hint = "")
    TaskType.FORMULA -> copy(answer = Chem.normalize(answer))
    TaskType.EQUATION -> {
        val equation = Equation.parse(text)
        copy(
            text = equation?.let { it.reactants.joinToString(" + ") + " = " + it.products.joinToString(" + ") } ?: text,
            answer = parseCoefficients(answer)?.joinToString(" ") ?: answer,
        )
    }
}
