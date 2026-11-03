package ru.prostayahimiya.app.ui.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.prostayahimiya.app.HimiyaApp
import ru.prostayahimiya.app.R
import ru.prostayahimiya.app.chem.Chem
import ru.prostayahimiya.app.chem.EquationCheck
import ru.prostayahimiya.app.chem.FormulaCheck
import ru.prostayahimiya.app.data.Content
import ru.prostayahimiya.app.data.EquationTask
import ru.prostayahimiya.app.data.FormulaTask
import ru.prostayahimiya.app.ui.components.BackTopBar
import ru.prostayahimiya.app.ui.components.tasksWord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(practiceId: String, onBack: () -> Unit) {
    val practice = remember(practiceId) { Content.practices().firstOrNull { it.id == practiceId } } ?: return
    val store = (LocalContext.current.applicationContext as HimiyaApp).store
    val vm: PracticeViewModel = viewModel(factory = PracticeViewModel.factory(store, practice))
    val solved by store.solved.collectAsState()
    val solvedCount = practice.checkableTaskIds.count { it in solved }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BackTopBar(
                title = "${vm.grade.subject.title} · ${vm.grade.number} класс",
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
            item { ProgressHeader(practice.title, solvedCount, practice.checkableTaskIds.size) }

            if (practice.questions.isNotEmpty()) {
                item {
                    SectionHeader(
                        "1. Вопросы по теории",
                        "Ответь своими словами и отправь ответы учителю. Черновик сохраняется сам.",
                    )
                }
                itemsIndexed(practice.questions, key = { _, q -> q.id }) { i, question ->
                    TaskCard("Вопрос ${i + 1}") {
                        Text(question.text, style = MaterialTheme.typography.bodyLarge)
                        OutlinedTextField(
                            value = vm.answers[question.id].orEmpty(),
                            onValueChange = { vm.onAnswerChange(question.id, it) },
                            label = { Text("Твой ответ") },
                            minLines = 3,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    }
                }
                item { SendCard(vm, solvedCount) }
            }

            if (practice.formulas.isNotEmpty()) {
                item {
                    SectionHeader(
                        "2. Составь формулы по валентности",
                        "Пиши латинскими буквами, индексы обычными цифрами: например, H2O.",
                    )
                }
                itemsIndexed(practice.formulas, key = { _, t -> t.id }) { i, task ->
                    FormulaTaskCard(i + 1, task, solved = task.id in solved, vm = vm)
                }
            }

            if (practice.equations.isNotEmpty()) {
                item {
                    SectionHeader(
                        "3. Расставь коэффициенты",
                        "Впиши коэффициенты в клеточки. Пустая клеточка означает коэффициент 1.",
                    )
                }
                itemsIndexed(practice.equations, key = { _, t -> t.id }) { i, task ->
                    EquationTaskCard(i + 1, task, solved = task.id in solved, vm = vm)
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Начать заново?") },
            text = { Text("Отметки о решённых заданиях и черновики ответов по этой практике удалятся.") },
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

@Composable
private fun ProgressHeader(title: String, solved: Int, total: Int) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (total > 0) {
            Text(
                if (solved == total) "Все задания с проверкой решены! 🎉" else "Решено $solved из $total ${tasksWord(total)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            LinearProgressIndicator(progress = { solved.toFloat() / total }, modifier = Modifier.fillMaxWidth())
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
private fun SendCard(vm: PracticeViewModel, solvedCount: Int) {
    TaskCard("Отправка учителю") {
        OutlinedTextField(
            value = vm.studentName,
            onValueChange = vm::onNameChange,
            label = { Text("Имя и класс") },
            placeholder = { Text("Петя, 8Б") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.send(solvedCount) },
            enabled = vm.sendState !is SendState.Sending,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
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
                Text("Отправить ответы учителю")
            }
        }
        when (val state = vm.sendState) {
            is SendState.Error -> Feedback(state.message, FeedbackKind.Error)
            SendState.Sent -> Feedback("Готово! Ответы отправлены учителю.", FeedbackKind.Success)
            else -> if (vm.sentAt > 0) {
                Text(
                    "Последний раз отправлено ${formatDate(vm.sentAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun FormulaTaskCard(number: Int, task: FormulaTask, solved: Boolean, vm: PracticeViewModel) {
    val input = vm.formulaInputs[task.id].orEmpty()
    val result = vm.formulaResults[task.id]

    TaskCard("Задание 2.$number", solved) {
        Text(
            task.name.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = input,
            onValueChange = { vm.onFormulaChange(task, it) },
            label = { Text("Формула") },
            singleLine = true,
            isError = result is FormulaCheck.Wrong,
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
        TaskActions(
            canCheck = input.isNotBlank(),
            onCheck = { vm.check(task) },
            onHint = { vm.toggleHint(task.id) },
        )
        if (vm.hintsShown[task.id] == true) HintBox(task.hint)
        when (result) {
            FormulaCheck.Correct -> Feedback("Верно! ${Chem.pretty(task.answer)}", FeedbackKind.Success)
            is FormulaCheck.Hint -> Feedback(result.message, FeedbackKind.Warning)
            is FormulaCheck.Wrong -> Feedback(result.message, FeedbackKind.Error)
            null -> Unit
        }
        if (result != null && result != FormulaCheck.Correct) {
            ShowAnswer(shown = vm.answersShown[task.id] == true, answer = Chem.pretty(task.answer)) {
                vm.showAnswer(task.id)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EquationTaskCard(number: Int, task: EquationTask, solved: Boolean, vm: PracticeViewModel) {
    val equation = task.equation
    val inputs = vm.coefficients(task)
    val result = vm.equationResults[task.id]

    TaskCard("Задание 3.$number", solved) {
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
                        onValueChange = { vm.onCoefficientChange(task, i, it) },
                        description = "Коэффициент перед ${Chem.pretty(formula)}",
                        isLast = i == equation.substances.lastIndex,
                        onDone = { vm.check(task) },
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(Chem.pretty(formula), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        TaskActions(canCheck = true, onCheck = { vm.check(task) }, onHint = { vm.toggleHint(task.id) })
        if (vm.hintsShown[task.id] == true) HintBox(task.hint)
        when (result) {
            EquationCheck.Correct -> Feedback("Верно! ${equation.pretty(inputs.map { it.toIntOrNull() ?: 1 })}", FeedbackKind.Success)
            is EquationCheck.NotReduced -> Feedback(
                "Атомов поровну, но все коэффициенты делятся на ${result.divisor}. Нужны самые маленькие.",
                FeedbackKind.Warning,
            )
            is EquationCheck.Unbalanced -> Feedback(
                "Пока не сходится:\n" + result.mismatches.joinToString("\n") {
                    "• ${it.element}: слева ${it.left}, справа ${it.right}"
                },
                FeedbackKind.Error,
            )
            is EquationCheck.Invalid -> Feedback(result.message, FeedbackKind.Warning)
            null -> Unit
        }
        if (result != null && result != EquationCheck.Correct) {
            ShowAnswer(shown = vm.answersShown[task.id] == true, answer = equation.pretty(task.answer)) {
                vm.showAnswer(task.id)
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

@Composable
private fun TaskActions(canCheck: Boolean, onCheck: () -> Unit, onHint: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Button(onClick = onCheck, enabled = canCheck) { Text("Проверить") }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onHint) {
            Icon(painterResource(R.drawable.ic_lightbulb), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Подсказка")
        }
    }
}

@Composable
private fun HintBox(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(
            "💡 $text",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun ShowAnswer(shown: Boolean, answer: String, onShow: () -> Unit) {
    if (shown) {
        Text(
            "Ответ: $answer",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp),
        )
    } else {
        TextButton(onClick = onShow, modifier = Modifier.padding(top = 4.dp)) {
            Icon(painterResource(R.drawable.ic_visibility), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Показать ответ")
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

private fun formatDate(time: Long): String =
    SimpleDateFormat("d MMMM, HH:mm", Locale.forLanguageTag("ru")).format(Date(time))
