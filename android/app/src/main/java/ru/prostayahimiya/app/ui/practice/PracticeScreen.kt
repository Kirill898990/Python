package ru.prostayahimiya.app.ui.practice

import androidx.compose.foundation.BorderStroke
import kotlin.coroutines.cancellation.CancellationException
import ru.prostayahimiya.app.data.Submission
import ru.prostayahimiya.app.data.Practice
import ru.prostayahimiya.app.data.ExamSession
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.prostayahimiya.app.R
import ru.prostayahimiya.app.chem.BLANK
import ru.prostayahimiya.app.chem.Chem
import ru.prostayahimiya.app.data.CheckResult
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.data.Task
import ru.prostayahimiya.app.data.TaskType
import ru.prostayahimiya.app.data.formatDay
import ru.prostayahimiya.app.data.formatServerDate
import ru.prostayahimiya.app.ui.components.BackTopBar
import ru.prostayahimiya.app.ui.components.EmptyBox
import ru.prostayahimiya.app.ui.components.GradeBadge
import ru.prostayahimiya.app.ui.components.PASTE_LIMIT_FORMULA
import ru.prostayahimiya.app.ui.components.PASTE_LIMIT_TEXT
import ru.prostayahimiya.app.ui.components.PASTE_LIMIT_WORD
import ru.prostayahimiya.app.ui.components.ProtectedFromCopy
import ru.prostayahimiya.app.ui.components.looksLikePaste
import ru.prostayahimiya.app.ui.components.showPasteBlocked
import ru.prostayahimiya.app.ui.components.tasksWord

/** Заголовок и пояснение раздела для каждого вида заданий. */
private fun TaskType.sectionTitle() = when (this) {
    TaskType.QUESTION -> "Вопросы по теории"
    TaskType.CHOICE -> "Тест"
    TaskType.FILL -> "Вставь пропущенные слова"
    TaskType.FORMULA -> "Составь формулы по валентности"
    TaskType.MOLAR -> "Вычисли молярную массу"
    TaskType.EQUATION -> "Расставь коэффициенты"
}

private fun TaskType.sectionCaption() = when (this) {
    TaskType.QUESTION -> "Ответь своими словами. Черновик сохраняется сам, а в конце сдай работу учителю."
    TaskType.CHOICE -> "Выбери верный ответ и нажми «Проверить»."
    TaskType.FILL -> "Впиши в пропуски подходящие слова."
    TaskType.FORMULA -> "Пиши латинскими буквами, индексы обычными цифрами: например, H2O."
    TaskType.MOLAR -> "Ответ в г/моль. Атомные массы бери из таблицы Менделеева, хлор — 35,5."
    TaskType.EQUATION -> "Впиши коэффициенты в клеточки. Пустая клеточка означает коэффициент 1."
}

/** В контрольной нет «Проверить», а сдаётся всё одной кнопкой. */
private fun String.examCaption(exam: Boolean) =
    if (!exam) this else replace(" и нажми «Проверить»", "").replace(", а в конце сдай работу учителю", "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    practiceId: String,
    repository: Repository,
    onBack: () -> Unit,
    onOpenCabinet: () -> Unit,
    onOpenReference: () -> Unit,
    onOpenSubmission: (String) -> Unit = {},
) {
    val content by repository.content.collectAsState()
    val user by repository.user.collectAsState()
    val sessions by repository.examSessions.collectAsState()
    val mySubmissions by repository.mySubmissionsState.collectAsState()
    val practice = content.practice(practiceId)
    if (practice == null) {
        Scaffold(topBar = { BackTopBar("Практика", onBack) }) { padding ->
            EmptyBox("🗂️", "Эта практика больше недоступна", Modifier.padding(padding))
        }
        return
    }
    // Контрольная: ответы сразу не проверяются, сдать можно один раз, задания — только после «Начать».
    val exam = practice.exam
    val isStudent = user?.isTeacher == false
    val session = if (exam && isStudent) sessions[practice.id] else null
    if (exam && isStudent) {
        val last = mySubmissions.firstOrNull { it.practice == practice.id }
        if (last != null) {
            ExamDoneScreen(practice, last, repository, onBack, onOpenSubmission)
            return
        }
        if (session == null) {
            ExamIntroScreen(practice, repository, onBack, resume = practice.id in content.examStarts)
            return
        }
    }
    // Варианты: ученику — его вариант, учителю — тот, который он выбрал для просмотра.
    val variantCount = content.variantCount(practice.id)
    var teacherVariant by rememberSaveable(practice.id) { mutableIntStateOf(1) }
    val currentUser = user
    val variant = when {
        variantCount == 0 -> 0
        currentUser == null || currentUser.isTeacher -> teacherVariant.coerceIn(1, variantCount)
        else -> content.variantFor(practice.id)
    }
    val tasks = content.tasksForVariant(practice.id, variant)
    val vm: PracticeViewModel = viewModel(
        key = "practice-${practice.id}-$variant-${tasks.hashCode()}",
        factory = PracticeViewModel.factory(repository, practice, tasks, variant),
    )
    val solved by repository.progress.solved.collectAsState()
    // Ученику нельзя копировать задания и вставлять текст в ответы. У учителя ограничений нет.
    val protect = user?.isTeacher != true
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    ProtectedFromCopy(enabled = protect) {
        Scaffold(
            topBar = {
                BackTopBar(
                    title = if (session != null) "Контрольная" else "${practice.subjectEnum.title} · ${practice.grade} класс",
                    onBack = onBack,
                    scrollBehavior = scrollBehavior,
                ) {
                    if (session?.endsAt != null) ExamTimer(session) { vm.submitExam() }
                    IconButton(onClick = onOpenReference) {
                        Icon(painterResource(R.drawable.ic_book), contentDescription = "Справочник")
                    }
                    if (!exam) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "Ещё")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Начать заново") },
                                onClick = {
                                    menuOpen = false
                                    confirmReset = true
                                },
                            )
                        }
                    }
                }
            },
            // Отступ снизу растёт вместе с клавиатурой, чтобы она не закрывала поля ввода.
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ProgressHeader(practice.title, vm.filledCount, vm.totalCount) }
                if (exam && !isStudent) {
                    item {
                        TaskCard("📝 ${practice.examLabel}") {
                            Text(
                                "Ученики увидят задания только после «Начать». Ответы сразу не проверяются, сдать можно один раз. " +
                                    practice.examResultsText + ".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (variantCount > 0) {
                    item {
                        if (currentUser?.isTeacher == true) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Вариант:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    (1..variantCount).forEach { v ->
                                        FilterChip(selected = variant == v, onClick = { teacherVariant = v }, label = { Text("Вариант $v") })
                                    }
                                }
                                StudentVariants(practice, variantCount, repository)
                            }
                        } else {
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(
                                    "Вариант $variant",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
                practice.deadlineInstant?.let { deadline ->
                    item {
                        val overdue = deadline.isBefore(java.time.Instant.now())
                        Text(
                            "⏰ " + if (overdue) {
                                "Срок сдачи прошёл ${formatDay(deadline)} — сдать ещё можно, учитель увидит опоздание"
                            } else {
                                "Сдать до ${formatDay(deadline)}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (vm.sections.isEmpty()) {
                    item { EmptyBox("📝", "В этой практике пока нет заданий") }
                }

                vm.sections.forEachIndexed { sectionIndex, (type, list) ->
                    val number = sectionIndex + 1
                    item(key = "section-${type.key}") { SectionHeader("$number. ${type.sectionTitle()}", type.sectionCaption().examCaption(exam)) }
                    list.forEachIndexed { i, task ->
                        item(key = task.id) {
                            if (type == TaskType.QUESTION) {
                                QuestionCard("Вопрос ${i + 1}", task, vm, repository, protect)
                            } else {
                                CheckableCard("Задание $number.${i + 1}", task, task.id in solved && !exam, vm, repository, exam) {
                                    when (type) {
                                        TaskType.CHOICE -> ChoiceContent(task, vm)
                                        TaskType.FILL -> FillContent(task, vm, protect)
                                        TaskType.FORMULA -> FormulaContent(task, vm, protect)
                                        TaskType.MOLAR -> MolarContent(task, vm, protect)
                                        TaskType.EQUATION -> EquationContent(task, vm)
                                        TaskType.QUESTION -> Unit
                                    }
                                }
                            }
                        }
                    }
                }

                if (vm.sections.isNotEmpty()) {
                    item {
                        if (user?.isTeacher == true) {
                            Text(
                                "Так практику видят ученики. Сдавать работу учителю не нужно.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else if (exam) {
                            ExamSubmitCard(vm)
                        } else {
                            SubmitCard(vm, onOpenCabinet)
                        }
                    }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Начать заново?") },
            text = { Text("Черновики ответов и отметки о решённых заданиях по этой практике удалятся. Сданные работы останутся у учителя.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.reset()
                    confirmReset = false
                }) { Text("Начать заново") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Отмена") }
            },
        )
    }
}

/** Для учителя: кто из учеников какой вариант решает (раскрывается по нажатию). */
@Composable
private fun StudentVariants(practice: ru.prostayahimiya.app.data.Practice, count: Int, repository: Repository) {
    val content by repository.content.collectAsState()
    var open by rememberSaveable(practice.id) { mutableStateOf(false) }
    val students by produceState<List<ru.prostayahimiya.app.data.User>?>(null, practice.id) {
        value = runCatching { repository.students() }.getOrDefault(emptyList())
    }
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = { open = !open },
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                (if (open) "▾ " else "▸ ") + "Кто какой вариант решает",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (open) {
                val list = students
                when {
                    list == null -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(top = 8.dp).size(20.dp))
                    else -> {
                        val kids = list.filter { practice.isAssignedTo(it.group) }
                        val variants = content.variantsOf(practice.id, list)
                        if (kids.isEmpty()) {
                            Text("Ученики этой параллели ещё не зарегистрировались.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        }
                        kids.map { it.group }.distinct().sorted().forEach { group ->
                            Text(if (group.isBlank()) "Без класса" else "$group класс", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                            (1..count).forEach { v ->
                                val names = kids.filter { it.group == group && variants[it.id] == v }.sortedBy { it.name }.joinToString(", ") { it.name }
                                Row(Modifier.padding(top = 4.dp)) {
                                    Text("Вариант $v: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = scheme.primary)
                                    Text(names.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        Text(
                            "Варианты раздаются по очереди внутри класса в порядке регистрации: 1, 2, 1, 2… У того, кто уже начал, вариант не меняется.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Картинка к заданию. Нажатие показывает её крупнее. */
@Composable
private fun TaskImage(task: Task, repository: Repository) {
    if (task.image.isBlank()) return
    val bitmap by produceState<ImageBitmap?>(null, task.id, task.image) { value = repository.taskImage(task) }
    var zoomed by rememberSaveable(task.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClickLabel = if (zoomed) "Уменьшить" else "Увеличить") { zoomed = !zoomed },
    ) {
        val image = bitmap
        if (image == null) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
        } else {
            Image(
                bitmap = image,
                contentDescription = "Рисунок к заданию",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = if (zoomed) 2000.dp else 240.dp),
            )
        }
    }
}

@Composable
private fun QuestionCard(label: String, task: Task, vm: PracticeViewModel, repository: Repository, protect: Boolean) {
    val context = LocalContext.current
    val value = vm.input(task)
    TaskCard(label) {
        TaskImage(task, repository)
        Text(task.text, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = value,
            onValueChange = { new ->
                if (protect && looksLikePaste(value, new, PASTE_LIMIT_TEXT)) context.showPasteBlocked() else vm.onInput(task, new)
            },
            label = { Text("Твой ответ") },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

/** Карточка задания с кнопкой «Проверить». Ответ проверяет сервер. */
@Composable
private fun CheckableCard(
    label: String,
    task: Task,
    solved: Boolean,
    vm: PracticeViewModel,
    repository: Repository,
    exam: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    TaskCard(label, solved) {
        TaskImage(task, repository)
        content()
        // В контрольной кнопки «Проверить» нет: ответы проверятся после сдачи.
        if (!exam) {
            CheckButton(checking = vm.checking[task.id] == true) { vm.check(task) }
            vm.results[task.id]?.let { ResultFeedback(it) }
        }
    }
}

@Composable
private fun ResultFeedback(result: CheckResult) {
    Feedback(
        result.message.ifBlank { if (result.isCorrect) "Верно!" else "Неверно" },
        when (result.kind) {
            "correct" -> FeedbackKind.Success
            "hint" -> FeedbackKind.Warning
            else -> FeedbackKind.Error
        },
    )
}

@Composable
private fun ChoiceContent(task: Task, vm: PracticeViewModel) {
    val scheme = MaterialTheme.colorScheme
    val chosen = vm.chosen(task)
    Text(task.text, style = MaterialTheme.typography.bodyLarge)
    if (task.multiple) {
        Text(
            "Верных ответов может быть несколько",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
        task.options.forEachIndexed { k, option ->
            val number = k + 1
            val selected = number in chosen
            val shape = RoundedCornerShape(12.dp)
            val rowModifier = if (task.multiple) {
                Modifier.toggleable(value = selected, role = Role.Checkbox) { vm.toggleChoice(task, number) }
            } else {
                Modifier.selectable(selected = selected, role = Role.RadioButton) { vm.toggleChoice(task, number) }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(1.dp, if (selected) scheme.primary else scheme.outlineVariant, shape)
                    .then(rowModifier)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                if (task.multiple) Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                else RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
                Text(Chem.prettyIfFormula(option), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FillContent(task: Task, vm: PracticeViewModel, protect: Boolean) {
    val context = LocalContext.current
    val count = vm.blankCount(task)
    val values = vm.parts(task, count)
    val blanks = vm.results[task.id]?.blanks
    val pieces = task.text.split(BLANK)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        pieces.forEachIndexed { k, piece ->
            // Каждое слово отдельно, чтобы текст переносился по словам, а не целыми кусками.
            piece.split(' ').filter { it.isNotEmpty() }.forEach { word ->
                Text(word, style = MaterialTheme.typography.bodyLarge)
            }
            if (k < count) {
                BlankField(
                    value = values[k],
                    onValueChange = { new ->
                        if (protect && looksLikePaste(values[k], new, PASTE_LIMIT_WORD)) {
                            context.showPasteBlocked()
                        } else {
                            vm.onPart(task, k, count, new.replace("|", ""))
                        }
                    },
                    description = "Пропуск ${k + 1}",
                    correct = blanks?.getOrNull(k),
                    isLast = k == count - 1,
                    onDone = { vm.check(task) },
                )
            }
        }
    }
}

/** Поле для пропущенного слова: подчёркивание, которое растёт вместе с текстом. */
@Composable
private fun BlankField(
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    correct: Boolean?,
    isLast: Boolean,
    onDone: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val line = when {
        correct == true -> colors.primary
        correct == false -> colors.error
        focused -> colors.primary
        else -> colors.outline
    }
    val background = when (correct) {
        true -> colors.primary.copy(alpha = 0.14f)
        false -> colors.error.copy(alpha = 0.12f)
        null -> colors.surfaceContainerHigh
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(colors.primary),
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .width(((value.length + 2).coerceIn(6, 22) * 11).dp)
            .semantics { contentDescription = description },
        decorationBox = { inner ->
            Column {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(background)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                ) { inner() }
                Box(Modifier.fillMaxWidth().height(2.dp).background(line))
            }
        },
    )
}

@Composable
private fun FormulaContent(task: Task, vm: PracticeViewModel, protect: Boolean) {
    val context = LocalContext.current
    val input = vm.input(task)
    val result = vm.results[task.id]
    Text(
        task.text.replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    OutlinedTextField(
        value = input,
        onValueChange = { new ->
            if (protect && looksLikePaste(input, new, PASTE_LIMIT_FORMULA)) context.showPasteBlocked() else vm.onInput(task, new)
        },
        label = { Text("Формула") },
        singleLine = true,
        isError = result?.kind == "wrong",
        supportingText = if (input.isNotBlank()) {
            { Text("Получается: ${Chem.pretty(Chem.normalize(input))}") }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { vm.check(task) }),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun MolarContent(task: Task, vm: PracticeViewModel, protect: Boolean) {
    val context = LocalContext.current
    val input = vm.input(task)
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Text("M(${Chem.pretty(Chem.normalize(task.text))}) =", style = MaterialTheme.typography.titleLarge)
        BasicTextField(
            value = input,
            onValueChange = { new ->
                if (protect && looksLikePaste(input, new, PASTE_LIMIT_FORMULA)) {
                    context.showPasteBlocked()
                } else {
                    vm.onInput(task, new.filter { it.isDigit() || it == ',' || it == '.' }.take(8))
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = colors.onSurface, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(colors.primary),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.check(task) }),
            modifier = Modifier
                .size(width = 96.dp, height = 48.dp)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) colors.primary else colors.outline,
                    shape = RoundedCornerShape(8.dp),
                )
                .semantics { contentDescription = "Молярная масса, г/моль" },
            decorationBox = { inner -> Box(contentAlignment = Alignment.Center) { inner() } },
        )
        Text("г/моль", style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EquationContent(task: Task, vm: PracticeViewModel) {
    val equation = task.equation!!
    val count = equation.substances.size
    val inputs = vm.parts(task, count)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        equation.substances.forEachIndexed { i, formula ->
            // Знак «+» или «→» держится вместе со следующим веществом, чтобы не отрываться при переносе.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (i > 0) {
                    Text(
                        if (i == equation.reactants.size) "→" else "+",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                CoefficientField(
                    value = inputs[i],
                    onValueChange = { vm.onPart(task, i, count, it.filter(Char::isDigit).take(2)) },
                    description = "Коэффициент перед ${Chem.pretty(formula)}",
                    isLast = i == equation.substances.lastIndex,
                    onDone = { vm.check(task) },
                )
                Spacer(Modifier.width(4.dp))
                Text(Chem.pretty(formula), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun ProgressHeader(title: String, filled: Int, total: Int) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (total > 0) {
            Text(
                if (filled == total) "Все задания заполнены — можно сдавать работу" else "Заполнено $filled из $total ${tasksWord(total)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            LinearProgressIndicator(progress = { filled.toFloat() / total }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SectionHeader(title: String, caption: String) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun TaskCard(label: String, solved: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            1.dp,
            if (solved) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (solved) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Решено",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun SubmitCard(vm: PracticeViewModel, onOpenCabinet: () -> Unit) {
    // Работа уже у учителя — переспрашиваем, чтобы случайные нажатия не присылали копии.
    var confirmResend by rememberSaveable { mutableStateOf(false) }
    if (confirmResend) {
        AlertDialog(
            onDismissRequest = { confirmResend = false },
            title = { Text("Сдать ещё раз?") },
            text = { Text("Работа уже у учителя. Если ты исправил ответы, учитель получит новую работу.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmResend = false
                    vm.submit()
                }) { Text("Сдать ещё раз") }
            },
            dismissButton = { TextButton(onClick = { confirmResend = false }) { Text("Отмена") } },
        )
    }
    TaskCard("Сдать работу") {
        val last = vm.lastSubmission
        if (last != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Сдано ${formatServerDate(last.created)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        if (last.isGraded) "Оценка учителя" else "Учитель ещё не проверил работу",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GradeBadge(last.grade)
            }
            if (last.comment.isNotBlank()) {
                Text(
                    "💬 ${last.comment}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        } else {
            Text(
                "Учитель увидит твои ответы и поставит оценку. Её можно будет посмотреть в личном кабинете.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Button(
            onClick = { if (last != null) confirmResend = true else vm.submit() },
            enabled = vm.sendState !is SendState.Sending,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (vm.sendState is SendState.Sending) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(10.dp))
                Text("Отправляем…")
            } else {
                Icon(painterResource(R.drawable.ic_send), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (last == null) "Сдать работу учителю" else "Сдать ещё раз")
            }
        }
        when (val state = vm.sendState) {
            is SendState.Error -> Feedback(state.message, FeedbackKind.Error)
            SendState.Sent -> {
                Feedback("Готово! Работа отправлена учителю.", FeedbackKind.Success)
                TextButton(onClick = onOpenCabinet) { Text("Открыть личный кабинет") }
            }
            else -> Unit
        }
    }
}

/** Сдача контрольной: один раз, с подтверждением. */
@Composable
private fun ExamSubmitCard(vm: PracticeViewModel) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    TaskCard("Сдать контрольную") {
        Text(
            "Проверить ответы до сдачи нельзя. Сдать можно один раз — потом ответы уже не изменить.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Button(
            onClick = { confirm = true },
            enabled = vm.sendState !is SendState.Sending,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (vm.sendState is SendState.Sending) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp))
                Text("Отправляем…")
            } else {
                Icon(painterResource(R.drawable.ic_send), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Сдать контрольную")
            }
        }
        (vm.sendState as? SendState.Error)?.let { Feedback(it.message, FeedbackKind.Error) }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Сдать контрольную?") },
            text = { Text("Изменить ответы после этого будет нельзя.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    vm.submitExam()
                }) { Text("Сдать") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }
}

/** Сколько осталось времени. Когда оно кончается, работа отправляется сама (и повторяет попытку без интернета). */
@Composable
private fun ExamTimer(session: ExamSession, onTimeUp: () -> Unit) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val left = session.leftMillis(now) ?: return
    val timeUp = left <= 0
    val currentOnTimeUp by rememberUpdatedState(onTimeUp)
    LaunchedEffect(timeUp) {
        while (timeUp) {
            currentOnTimeUp()
            delay(10_000)
        }
    }
    val scheme = MaterialTheme.colorScheme
    val warn = left < 5 * 60_000
    val seconds = (left.coerceAtLeast(0) + 999) / 1000
    Surface(
        shape = RoundedCornerShape(50),
        color = if (warn) scheme.errorContainer else scheme.primaryContainer,
        modifier = Modifier.semantics { contentDescription = "Осталось времени" },
    ) {
        Text(
            if (timeUp) "⏱ 0:00" else "⏱ %d:%02d".format(seconds / 60, seconds % 60),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (warn) scheme.onErrorContainer else scheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Правила контрольной и «Начать». Если контрольная уже начата (resume) — сразу продолжаем её. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamIntroScreen(practice: Practice, repository: Repository, onBack: () -> Unit, resume: Boolean) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    fun start(begin: Boolean) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                repository.startExam(practice, begin)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Не получилось начать"
            }
            busy = false
        }
    }
    LaunchedEffect(practice.id, resume) { if (resume) start(false) }
    val deadline = practice.deadlineInstant
    val closed = deadline != null && deadline.isBefore(java.time.Instant.now())
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = { BackTopBar("${practice.subjectEnum.title} · ${practice.grade} класс", onBack) },
        containerColor = scheme.background,
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text(practice.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (resume && error == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            TaskCard("Контрольная работа") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExamRule(if (practice.timeLimit > 0) "⏱ На работу ${practice.timeLimit} мин. Таймер пойдёт, как только ты нажмёшь «Начать»." else "⏱ Время не ограничено.")
                    ExamRule("🔒 Ответы сразу не проверяются, подсказок нет.")
                    ExamRule("📤 Сдать работу можно только один раз.")
                    ExamRule("📊 ${practice.examResultsText}.")
                    if (deadline != null) {
                        ExamRule(
                            if (closed) "⛔ Срок прошёл ${formatDay(deadline)}. Если нужно написать позже — попроси учителя."
                            else "📅 Начать можно до ${formatDay(deadline)}.",
                            error = closed,
                        )
                    }
                }
                Button(
                    onClick = { if (practice.timeLimit > 0) confirm = true else start(true) },
                    enabled = !busy && !closed,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = scheme.onPrimary)
                        Spacer(Modifier.width(10.dp))
                        Text("Открываем…")
                    } else {
                        Icon(painterResource(R.drawable.ic_send), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Начать контрольную")
                    }
                }
                error?.let { Feedback(it, FeedbackKind.Error) }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Начать контрольную?") },
            text = { Text("Таймер на ${practice.timeLimit} мин пойдёт сразу, остановить его нельзя. Когда время закончится, работа отправится сама.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    start(true)
                }) { Text("Начать") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ExamRule(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (error) FontWeight.SemiBold else FontWeight.Normal,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

/** Контрольная сдана: оценка или когда будут результаты. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamDoneScreen(
    practice: Practice,
    submission: Submission,
    repository: Repository,
    onBack: () -> Unit,
    onOpenSubmission: (String) -> Unit,
) {
    // Если учитель разрешил пересдать, работа пропадёт из списка — и снова появится «Начать».
    LaunchedEffect(practice.id) { repository.refreshMySubmissions() }
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        topBar = { BackTopBar("${practice.subjectEnum.title} · ${practice.grade} класс", onBack) },
        containerColor = scheme.background,
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text(practice.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TaskCard("Контрольная") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("✅ Контрольная сдана", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Сдано ${formatServerDate(submission.created)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        if (submission.overtime) {
                            Text("⏰ После окончания времени", style = MaterialTheme.typography.labelMedium, color = scheme.error, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    GradeBadge(submission.grade)
                }
                if (submission.comment.isNotBlank()) {
                    Text("💬 ${submission.comment}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                }
                if (!submission.isGraded) {
                    Text(
                        if (submission.resultsHidden) practice.examResultsText else "Учитель ещё не поставил оценку",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                TextButton(onClick = { onOpenSubmission(submission.id) }, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Посмотреть мою работу")
                }
            }
        }
    }
}

/** Маленькая клеточка для коэффициента. */
@Composable
private fun CoefficientField(
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    isLast: Boolean,
    onDone: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(color = colors.onSurface, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(colors.primary),
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .size(width = 40.dp, height = 48.dp)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.primary else colors.outline,
                shape = RoundedCornerShape(8.dp),
            )
            .semantics { contentDescription = description },
        decorationBox = { inner -> Box(contentAlignment = Alignment.Center) { inner() } },
    )
}

/** Кнопка «Проверить». Подсказок и готовых ответов нет: ученик решает сам. */
@Composable
private fun CheckButton(checking: Boolean, onCheck: () -> Unit) {
    Button(onClick = onCheck, enabled = !checking, modifier = Modifier.padding(top = 8.dp)) {
        if (checking) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(10.dp))
            Text("Проверяем…")
        } else {
            Text("Проверить")
        }
    }
}

private enum class FeedbackKind { Success, Warning, Error }

@Composable
private fun Feedback(text: String, kind: FeedbackKind) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (kind) {
        FeedbackKind.Success -> scheme.primaryContainer to scheme.onPrimaryContainer
        FeedbackKind.Warning -> scheme.secondaryContainer to scheme.onSecondaryContainer
        FeedbackKind.Error -> scheme.errorContainer to scheme.onErrorContainer
    }
    val prefix = when (kind) {
        FeedbackKind.Success -> "✅ "
        FeedbackKind.Warning -> "🤔 "
        FeedbackKind.Error -> "❌ "
    }
    Surface(shape = RoundedCornerShape(12.dp), color = container, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(prefix + text, style = MaterialTheme.typography.bodyMedium, color = content, modifier = Modifier.padding(12.dp))
    }
}
