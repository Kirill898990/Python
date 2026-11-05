package ru.prostayahimiya.app.ui.cabinet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.data.Submission
import ru.prostayahimiya.app.data.User
import ru.prostayahimiya.app.data.formatServerDate
import ru.prostayahimiya.app.ui.components.BackTopBar
import ru.prostayahimiya.app.ui.components.EmptyBox
import ru.prostayahimiya.app.ui.components.ErrorBox
import ru.prostayahimiya.app.ui.components.GradeBadge
import ru.prostayahimiya.app.ui.components.Load
import ru.prostayahimiya.app.ui.components.LoadingBox
import ru.prostayahimiya.app.ui.components.NotificationsSettingCard
import ru.prostayahimiya.app.ui.components.SectionLabel
import ru.prostayahimiya.app.ui.components.cardBorder
import ru.prostayahimiya.app.ui.components.cardColors
import ru.prostayahimiya.app.ui.components.rememberLoader

/** Личный кабинет: профиль, сданные работы и оценки. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CabinetScreen(repository: Repository, onBack: () -> Unit, onOpenSubmission: (String) -> Unit) {
    val user by repository.user.collectAsState()
    val loader = rememberLoader(user?.id) { repository.mySubmissions() }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    // Имя учителя ученика — из списка учителей на сервере.
    var teacherName by remember { mutableStateOf("") }
    LaunchedEffect(user?.teacher) {
        teacherName = user?.teacher?.takeIf { it.isNotEmpty() && user?.isTeacher != true }?.let { repository.teacherName(it) }.orEmpty()
    }

    Scaffold(
        topBar = { BackTopBar("Личный кабинет", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = loader.refreshing && loader.state is Load.Ok,
            onRefresh = loader::reload,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                user?.let { item { ProfileCard(it, teacherName) } }
                item { NotificationsSettingCard(repository, teacher = user?.isTeacher == true) }

                item { SectionLabel("Мои работы") }
                when (val state = loader.state) {
                    Load.Loading -> item { LoadingBox() }
                    is Load.Failed -> item { ErrorBox(state.message, loader::reload) }
                    is Load.Ok -> {
                        if (state.value.isEmpty()) {
                            item { EmptyBox("📝", "Ты ещё не сдавал работ. Реши практику и нажми «Сдать работу учителю».") }
                        } else {
                            item { Summary(state.value) }
                            items(state.value, key = { it.id }) { submission ->
                                SubmissionCard(submission, showStudent = false) { onOpenSubmission(submission.id) }
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { confirmLogout = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    ) { Text("Выйти из аккаунта") }
                }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Выйти из аккаунта?") },
            text = { Text("Сданные работы и оценки сохранятся, их можно будет посмотреть после входа.") },
            confirmButton = { TextButton(onClick = repository::logout) { Text("Выйти") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ProfileCard(user: User, teacherName: String) {
    Card(colors = cardColors(), border = cardBorder()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        user.name.firstOrNull()?.uppercase() ?: "?",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(user.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        when {
                            user.isTeacher -> append("Учитель")
                            user.graduated -> append("Выпускник ${user.graduatedFrom}".trim())
                            user.group.isNotBlank() -> append("${user.group} класс")
                        }
                        append(" · логин ${user.username}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (teacherName.isNotEmpty()) {
                    Text(
                        "Учитель: $teacherName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Средний балл и число проверенных работ. */
@Composable
private fun Summary(submissions: List<Submission>) {
    val graded = submissions.filter { it.isGraded }
    if (graded.isEmpty()) return
    val average = graded.map { it.grade }.average()
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            "Средний балл: ${"%.1f".format(average).replace('.', ',')} · проверено ${graded.size} из ${submissions.size}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
    }
}

/** Карточка сданной работы. Используется и в кабинете, и в панели учителя. */
@Composable
fun SubmissionCard(submission: Submission, showStudent: Boolean, late: Boolean = false, onClick: () -> Unit) {
    Card(onClick = onClick, colors = cardColors(), border = cardBorder()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                if (showStudent) {
                    val student = submission.studentUser
                    Text(
                        student?.let { if (it.group.isNotBlank()) "${it.name}, ${it.group}" else it.name } ?: "Ученик удалён",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    submission.practiceTitle,
                    style = if (showStudent) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                    fontWeight = if (showStudent) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(formatServerDate(submission.created))
                        if (submission.resultsHidden) append(" · результаты позже")
                        else if (submission.total > 0) append(" · решено ${submission.solved} из ${submission.total}")
                        if (submission.variant > 0) append(" · вариант ${submission.variant}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (late || submission.overtime) {
                    Text(
                        listOfNotNull("с опозданием".takeIf { late }, "время вышло".takeIf { submission.overtime }).joinToString(" · ", prefix = "⏰ "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            GradeBadge(submission.grade)
        }
    }
}
