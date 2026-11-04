package ru.prostayahimiya.app.ui.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import ru.prostayahimiya.app.data.formatServerDate
import ru.prostayahimiya.app.ui.components.BackTopBar
import ru.prostayahimiya.app.ui.components.EmptyBox
import ru.prostayahimiya.app.ui.components.GradeBadge
import ru.prostayahimiya.app.ui.components.PASTE_LIMIT_FORMULA
import ru.prostayahimiya.app.ui.components.PASTE_LIMIT_WORD
import ru.prostayahimiya.app.ui.components.PASTE_LIMIT_TEXT
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(practiceId: String, repository: Repository, onBack: () -> Unit, onOpenCabinet: () -> Unit) {
    val content by repository.content.collectAsState()
    val user by repository.user.collectAsState()
    val practice = content.practice(practiceId)
    if (practice == null) {
        Scaffold(topBar = { BackTopBar("Практика", onBack) }) { padding ->
            EmptyBox("🗂️", "Эта практика больше недоступна", Modifier.padding(padding))
        }
        return
    }
    val vm: PracticeViewModel = viewModel(
        key = "practice-${practice.id}-${content.tasksOf(practice.id).hashCode()}",
        factory = PracticeViewModel.factory(repository, practice, content.tasksOf(practice.id)),
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
                    title = "${practice.subjectEnum.title} · ${practice.grade} класс",
                    onBack = onBack,
                    scrollBehavior = scrollBehavior,
                ) {
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
                if (vm.sections.isEmpty()) {
                    item { EmptyBox("📝", "В этой практике пока нет заданий") }
                }

                vm.sections.forEachIndexed { sectionIndex, (type, list) ->
                    val number = sectionIndex + 1
                    item(key = "section-${type.key}") { SectionHeader("$number. ${type.sectionTitle()}", type.sectionCaption()) }
                    list.forEachIndexed { i, task ->
                        item(key = task.id) {
                            if (type == TaskType.QUESTION) {
                                QuestionCard("Вопрос ${i + 1}", task, vm, repository, protect)
                            } else {
                                CheckableCard("Задание $number.${i + 1}", task, task.id in solved, vm, repository) {
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
    content: @Composable ColumnScope.() -> Unit,
) {
    TaskCard(label, solved) {
        TaskImage(task, repository)
        content()
        CheckButton(checking = vm.checking[task.id] == true) { vm.check(task) }
        vm.results[task.id]?.let { ResultFeedback(it) }
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
            onClick = vm::submit,
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
