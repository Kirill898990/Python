package ru.prostayahimiya.app.ui.admin

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.prostayahimiya.app.R
import ru.prostayahimiya.app.chem.Chem
import ru.prostayahimiya.app.chem.Equation
import ru.prostayahimiya.app.chem.joinBlanks
import ru.prostayahimiya.app.chem.parseChoice
import ru.prostayahimiya.app.chem.splitBlanks
import ru.prostayahimiya.app.chem.validateTask
import ru.prostayahimiya.app.data.Practice
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.data.Subject
import ru.prostayahimiya.app.data.Task
import ru.prostayahimiya.app.data.TaskType
import ru.prostayahimiya.app.data.formatDay
import ru.prostayahimiya.app.data.parseCoefficients
import ru.prostayahimiya.app.data.parseServerInstant
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
    var classes by rememberSaveable(currentId) { mutableStateOf(original.classes) }
    var deadline by rememberSaveable(currentId) { mutableStateOf(original.deadline) }
    var exam by rememberSaveable(currentId) { mutableStateOf(original.exam) }
    var timeLimit by rememberSaveable(currentId) { mutableStateOf(if (original.id.isEmpty()) "40" else original.timeLimit.takeIf { it > 0 }?.toString().orEmpty()) }
    var pickingDate by remember { mutableStateOf(false) }
    // Классы, которые можно выбрать: из списка учеников и уже выбранные.
    var knownClasses by remember { mutableStateOf(original.classes) }
    LaunchedEffect(Unit) {
        runCatching { repository.students() }.onSuccess { list ->
            knownClasses = (knownClasses + list.map { it.group }.filter { it.isNotBlank() }).distinct()
                .sortedWith(compareBy({ it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }, { it }))
        }
    }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var deletingTask by remember { mutableStateOf<Task?>(null) }
    var deletingPractice by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tasks = if (currentId.isEmpty()) emptyList() else content.tasksOf(currentId)
    val changed = currentId.isEmpty() || title != original.title || subject != original.subject ||
        grade != original.grade || published != original.published || classes != original.classes ||
        parseServerInstant(deadline) != original.deadlineInstant ||
        exam != original.exam || (exam && (timeLimit.toIntOrNull() ?: 0) != original.timeLimit)

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
            original.copy(
                id = currentId, title = title, subject = subject, grade = grade, published = published,
                classes = classes, deadline = deadline,
                exam = exam, timeLimit = if (exam) (timeLimit.toIntOrNull() ?: 0).coerceIn(0, 600) else 0,
            ),
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
                        Text("Для каких классов", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = classes.isEmpty(), onClick = { classes = emptyList() }, label = { Text("Все классы") })
                            knownClasses.forEach { g ->
                                FilterChip(
                                    selected = g in classes,
                                    onClick = { classes = if (g in classes) classes - g else (classes + g).sorted() },
                                    label = { Text(g) },
                                )
                            }
                        }
                        Text("Срок сдачи (необязательно)", style = MaterialTheme.typography.labelLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { pickingDate = true }) {
                                Text(parseServerInstant(deadline)?.let { "до ${formatDay(it)}" } ?: "Выбрать дату")
                            }
                            if (deadline.isNotBlank()) {
                                TextButton(onClick = { deadline = "" }) { Text("Без срока") }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Контрольная работа", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Без мгновенной проверки, с таймером",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = exam, onCheckedChange = { exam = it })
                        }
                        if (exam) {
                            OutlinedTextField(
                                value = timeLimit,
                                onValueChange = { v -> timeLimit = v.filter(Char::isDigit).take(3) },
                                label = { Text("Время на работу, минут") },
                                supportingText = { Text("Пусто или 0 — без ограничения") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "Ответы не проверяются сразу, подсказок нет, сдать можно один раз. Задания ученик увидит только после " +
                                    "«Начать». Результаты — после срока сдачи (если срока нет — когда вы поставите оценку).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                    Button(onClick = { generating = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("✨ Сгенерировать нейросетью")
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

    if (generating && currentId.isNotEmpty()) {
        GenerateTasksDialog(
            practiceId = currentId,
            nextPosition = (tasks.maxOfOrNull { it.position } ?: 0) + 1,
            repository = repository,
            onDismiss = { generating = false },
            onAdded = { n ->
                generating = false
                scope.launch { snackbar.showSnackbar("Добавлено заданий: $n") }
            },
        )
    }

    editing?.let { task ->
        TaskEditorDialog(
            task = task,
            repository = repository,
            onDismiss = { editing = null },
            onSave = { updated, image ->
                serverAction(if (task.id.isEmpty()) "Задание добавлено" else "Задание сохранено") {
                    val saved = repository.saveTask(updated)
                    when (image) {
                        is ImageChange.Set -> repository.setTaskImage(saved.id, image.uri)
                        ImageChange.Remove -> if (task.image.isNotBlank()) repository.setTaskImage(saved.id, null)
                        ImageChange.Keep -> Unit
                    }
                    editing = null
                }
            },
        )
    }

    if (pickingDate) {
        // Календарь работает с полуночью по UTC, а срок — до конца выбранного дня по времени телефона.
        val initial = parseServerInstant(deadline)?.atZone(ZoneId.systemDefault())?.toLocalDate()
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        deadline = day.atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant().toString()
                    }
                    pickingDate = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Отмена") } },
        ) { DatePicker(state = state) }
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

internal fun TaskType.shortTitle() = when (this) {
    TaskType.QUESTION -> "Вопрос"
    TaskType.CHOICE -> "Тест"
    TaskType.FILL -> "Пропуски"
    TaskType.FORMULA -> "Формула"
    TaskType.MOLAR -> "Молярная масса"
    TaskType.EQUATION -> "Уравнение"
}

internal fun TaskType.emoji() = when (this) {
    TaskType.QUESTION -> "❓"
    TaskType.CHOICE -> "☑️"
    TaskType.FILL -> "✏️"
    TaskType.FORMULA -> "🧪"
    TaskType.MOLAR -> "🧮"
    TaskType.EQUATION -> "⚖️"
}

/** Что сделать с картинкой задания при сохранении. */
private sealed interface ImageChange {
    data object Keep : ImageChange
    data object Remove : ImageChange
    data class Set(val uri: Uri) : ImageChange
}

/** Правильный ответ задания, как его показать учителю в списке. */
internal fun Task.answerSummary(): String? = when (taskType) {
    TaskType.QUESTION -> null
    TaskType.FORMULA -> Chem.pretty(answer)
    TaskType.EQUATION -> equation?.pretty(coefficients) ?: answer
    TaskType.CHOICE -> parseChoice(answer).mapNotNull { options.getOrNull(it - 1) }.joinToString("; ") { Chem.prettyIfFormula(it) }
    TaskType.FILL -> answer.split('|').joinToString("; ")
    TaskType.MOLAR -> (Chem.molarMass(text) as? Chem.MolarResult.Ok)?.let { "${Chem.formatNumber(it.value)} г/моль" }
}?.takeIf { it.isNotBlank() }

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
                    "$number. ${task.taskType.emoji()} ${task.taskType.shortTitle()}" + if (task.variant > 0) " · вариант ${task.variant}" else "",
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.taskType == TaskType.CHOICE) {
                    Text(
                        task.options.mapIndexed { k, o -> "${k + 1}) ${Chem.prettyIfFormula(o)}" }.joinToString("   "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                task.answerSummary()?.let {
                    Text("Ответ: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (task.image.isNotBlank()) {
                    Text("🖼️ С картинкой", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun TaskEditorDialog(task: Task, repository: Repository, onDismiss: () -> Unit, onSave: (Task, ImageChange) -> Unit) {
    val type = task.taskType
    // Для пропусков учитель пишет текст с правильными словами в квадратных скобках.
    var text by rememberSaveable(task.id) { mutableStateOf(if (type == TaskType.FILL && task.id.isNotEmpty()) joinBlanks(task.text, task.answer) else task.text) }
    var answer by rememberSaveable(task.id) { mutableStateOf(task.answer) }
    val options = remember(task.id) { mutableStateListOf(*(task.options.ifEmpty { listOf("", "") }).toTypedArray()) }
    val correct = remember(task.id) { mutableStateListOf(*parseChoice(task.answer).toTypedArray()) }
    var image by remember(task.id) { mutableStateOf<ImageChange>(ImageChange.Keep) }
    var variant by rememberSaveable(task.id) { mutableIntStateOf(task.variant) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) image = ImageChange.Set(uri)
    }

    /** Задание в том виде, в каком оно сохранится: текст, ответ, варианты. */
    fun current(): Triple<String, String, List<String>> = when (type) {
        TaskType.FILL -> splitBlanks(text).let { (t, a) -> Triple(t, a, emptyList()) }
        TaskType.CHOICE -> Triple(text, correct.sorted().joinToString(","), options.map { it.trim() })
        TaskType.MOLAR, TaskType.QUESTION -> Triple(text, "", emptyList())
        else -> Triple(text, answer, emptyList())
    }
    val (savedText, savedAnswer, savedOptions) = current()
    val error = validateTask(type.key, savedText, savedAnswer, savedOptions)
    val touched = text.isNotBlank() || answer.isNotBlank() || options.any { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        // Окно на всю ширину экрана: так удобнее печатать, и ширина не «прыгает» вслед за полями ввода.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 16.dp),
        title = { Text((if (task.id.isEmpty()) "Новое задание: " else "Задание: ") + type.shortTitle().lowercase()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (type) {
                    TaskType.QUESTION, TaskType.CHOICE -> OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(if (type == TaskType.QUESTION) "Текст вопроса" else "Вопрос") },
                        minLines = if (type == TaskType.QUESTION) 3 else 2,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TaskType.FILL -> OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Текст с пропусками") },
                        placeholder = { Text("Вода состоит из [водорода] и [кислорода].") },
                        supportingText = {
                            Text(
                                if (error == null && text.isNotBlank()) "Ученик увидит: $savedText"
                                else "Правильные слова пишите в квадратных скобках. Несколько вариантов — через «/»: [водорода/H].",
                            )
                        },
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TaskType.MOLAR -> OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Формула вещества") },
                        placeholder = { Text("H2SO4") },
                        supportingText = {
                            val m = Chem.molarMass(text)
                            if (text.isNotBlank() && m is Chem.MolarResult.Ok) {
                                Text("Получается: M(${Chem.pretty(Chem.normalize(text))}) = ${Chem.formatNumber(m.value)} г/моль")
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
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

                if (type == TaskType.CHOICE) {
                    Text("Варианты ответа — отметьте галочкой верные", style = MaterialTheme.typography.labelLarge)
                    options.forEachIndexed { k, option ->
                        val number = k + 1
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = number in correct,
                                onCheckedChange = { on -> if (on) correct.add(number) else correct.remove(number) },
                                modifier = Modifier.semantics { contentDescription = "Вариант $number верный" },
                            )
                            OutlinedTextField(
                                value = option,
                                onValueChange = { options[k] = it },
                                placeholder = { Text("Вариант $number") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                enabled = options.size > 2,
                                onClick = {
                                    options.removeAt(k)
                                    val shifted = correct.filter { it != number }.map { if (it > number) it - 1 else it }
                                    correct.clear()
                                    correct.addAll(shifted)
                                },
                            ) {
                                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Удалить вариант $number")
                            }
                        }
                    }
                    if (options.size < 8) {
                        TextButton(onClick = { options.add("") }) { Text("+ Вариант") }
                    }
                }

                Text("Картинка (необязательно)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                val hasImage = image is ImageChange.Set || (image == ImageChange.Keep && task.image.isNotBlank())
                if (hasImage) ImagePreview(task, (image as? ImageChange.Set)?.uri, repository)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(if (hasImage) "Заменить картинку" else "Добавить картинку") }
                    if (hasImage) TextButton(onClick = { image = ImageChange.Remove }) { Text("Убрать") }
                }

                Text("Кому это задание", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..4).forEach { v ->
                        FilterChip(selected = variant == v, onClick = { variant = v }, label = { Text(if (v == 0) "Для всех" else "Вариант $v") })
                    }
                }
                Text(
                    "Можно сделать до 4 вариантов: каждый ученик получит свой вариант сам, а задания «для всех» решают все.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (error != null && touched) {
                    Text("⚠️ $error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = error == null,
                onClick = {
                    val updated = task.copy(
                        text = savedText,
                        answer = savedAnswer,
                        options = savedOptions,
                        multiple = type == TaskType.CHOICE && correct.size > 1,
                        hint = "",
                        variant = variant,
                    ).normalized()
                    onSave(updated, image)
                },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Маленький предпросмотр картинки: новой (из галереи) или уже загруженной. */
@Composable
private fun ImagePreview(task: Task, uri: Uri?, repository: Repository) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri, task.image) {
        value = if (uri != null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 })
                    }?.asImageBitmap()
                }.getOrNull()
            }
        } else {
            repository.taskImage(task)
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = "Картинка к заданию",
            modifier = Modifier.heightIn(max = 120.dp).clip(RoundedCornerShape(8.dp)),
        )
    }
}

/** Приводит задание к единому виду перед сохранением: «al + o2 -> al2o3» не пройдёт проверку, а «Al+O2→Al2O3» станет «Al + O2 = Al2O3». */
private fun Task.normalized(): Task = when (taskType) {
    TaskType.QUESTION -> copy(answer = "", hint = "")
    TaskType.FORMULA -> copy(answer = Chem.normalize(answer))
    TaskType.MOLAR -> copy(text = Chem.normalize(text), answer = "")
    TaskType.CHOICE, TaskType.FILL -> this
    TaskType.EQUATION -> {
        val equation = Equation.parse(text)
        copy(
            text = equation?.let { it.reactants.joinToString(" + ") + " = " + it.products.joinToString(" + ") } ?: text,
            answer = parseCoefficients(answer)?.joinToString(" ") ?: answer,
        )
    }
}
