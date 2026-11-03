package ru.prostayahimiya.app.ui.cabinet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.prostayahimiya.app.data.AnswerItem
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.data.Submission
import ru.prostayahimiya.app.data.formatServerDate
import ru.prostayahimiya.app.ui.components.BackTopBar
import ru.prostayahimiya.app.ui.components.ErrorBox
import ru.prostayahimiya.app.ui.components.GradeBadge
import ru.prostayahimiya.app.ui.components.Load
import ru.prostayahimiya.app.ui.components.LoadingBox
import ru.prostayahimiya.app.ui.components.SectionLabel
import ru.prostayahimiya.app.ui.components.cardBorder
import ru.prostayahimiya.app.ui.components.cardColors
import ru.prostayahimiya.app.ui.components.rememberLoader

/**
 * Одна сданная работа: все ответы ученика, оценка и комментарий учителя.
 * Учитель на этом же экране ставит оценку.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionScreen(submissionId: String, repository: Repository, onBack: () -> Unit) {
    val user by repository.user.collectAsState()
    val isTeacher = user?.isTeacher == true
    val loader = rememberLoader(submissionId) { repository.submission(submissionId) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        topBar = { BackTopBar(if (isTeacher) "Проверка работы" else "Моя работа", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (val state = loader.state) {
            Load.Loading -> LoadingBox(Modifier.padding(padding))
            is Load.Failed -> ErrorBox(state.message, loader::reload, Modifier.padding(padding))
            is Load.Ok -> SubmissionContent(
                submission = state.value,
                isTeacher = isTeacher,
                repository = repository,
                snackbar = snackbar,
                padding = padding,
            )
        }
    }
}

@Composable
private fun SubmissionContent(
    submission: Submission,
    isTeacher: Boolean,
    repository: Repository,
    snackbar: SnackbarHostState,
    padding: PaddingValues,
) {
    var current by remember(submission.id) { mutableStateOf(submission) }
    // Номера вопросов считаем заранее: элементы списка рисуются не по порядку.
    val questionNumbers = remember(current.answers) {
        var n = 0
        current.answers.map { if (it.kind == "question") ++n else null }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                current.studentUser?.takeIf { isTeacher }?.let { student ->
                    Text(
                        if (student.group.isNotBlank()) "${student.name}, ${student.group}" else student.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(current.practiceTitle, style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        append("Сдано ${formatServerDate(current.created)}")
                        if (current.total > 0) append(" · автопроверка: верно ${current.solved} из ${current.total}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            if (isTeacher) {
                GradeEditor(current, repository, snackbar) { current = it }
            } else {
                GradeView(current)
            }
        }

        item { SectionLabel("Ответы") }
        itemsIndexed(current.answers) { index, answer ->
            AnswerCard(answer, questionNumbers[index])
        }
    }
}

@Composable
private fun GradeView(submission: Submission) {
    Card(colors = cardColors(), border = cardBorder()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (submission.isGraded) "Оценка учителя" else "Работа ещё не проверена",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (submission.isGraded && submission.gradedAt.isNotBlank()) {
                        Text(
                            "Проверено ${formatServerDate(submission.gradedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                GradeBadge(submission.grade, size = 56.dp)
            }
            if (submission.comment.isNotBlank()) {
                Text(
                    "💬 ${submission.comment}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun GradeEditor(
    submission: Submission,
    repository: Repository,
    snackbar: SnackbarHostState,
    onSaved: (Submission) -> Unit,
) {
    var grade by rememberSaveable(submission.id) { mutableIntStateOf(submission.grade) }
    var comment by rememberSaveable(submission.id) { mutableStateOf(submission.comment) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            message = null
        }
    }

    Card(colors = cardColors(), border = cardBorder()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Оценка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val options = listOf(2, 3, 4, 5)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, value ->
                    SegmentedButton(
                        selected = grade == value,
                        onClick = { grade = value },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) { Text(value.toString()) }
                }
            }
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Комментарий для ученика") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    saving = true
                    scope.launch {
                        message = try {
                            onSaved(repository.grade(submission.id, grade, comment))
                            "Оценка сохранена"
                        } catch (e: Exception) {
                            e.message ?: "Не получилось сохранить"
                        }
                        saving = false
                    }
                },
                enabled = grade > 0 && !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (saving) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text(if (submission.isGraded) "Изменить оценку" else "Поставить оценку")
                }
            }
        }
    }
}

@Composable
private fun AnswerCard(answer: AnswerItem, questionNumber: Int?) {
    val title = when (answer.kind) {
        "question" -> "Вопрос $questionNumber"
        "formula" -> "Формула: ${answer.prompt}"
        else -> "Уравнение"
    }
    Card(colors = cardColors(), border = cardBorder()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                when (answer.correct) {
                    true -> Text("✅ верно", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    false -> Text(
                        if (answer.answer.isBlank()) "— не решено" else "❌ неверно",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    null -> Unit
                }
            }
            if (answer.kind != "formula") {
                Text(answer.prompt, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            }
            Text(
                answer.answer.ifBlank { "Нет ответа" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (answer.answer.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
