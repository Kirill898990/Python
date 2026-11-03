package ru.prostayahimiya.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import ru.prostayahimiya.app.R
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.data.Subject
import ru.prostayahimiya.app.data.User
import ru.prostayahimiya.app.ui.cabinet.SubmissionCard
import ru.prostayahimiya.app.ui.components.BackTopBar
import ru.prostayahimiya.app.ui.components.EmptyBox
import ru.prostayahimiya.app.ui.components.ErrorBox
import ru.prostayahimiya.app.ui.components.Load
import ru.prostayahimiya.app.ui.components.LoadingBox
import ru.prostayahimiya.app.ui.components.SectionLabel
import ru.prostayahimiya.app.ui.components.cardBorder
import ru.prostayahimiya.app.ui.components.cardColors
import ru.prostayahimiya.app.ui.components.rememberLoader
import ru.prostayahimiya.app.ui.subject.DraftChip

/** Панель учителя. Доступна только пользователям с правами учителя (это проверяет и сервер). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    repository: Repository,
    onBack: () -> Unit,
    onOpenSubmission: (String) -> Unit,
    onEditPractice: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            Column {
                BackTopBar("Панель учителя", onBack)
                PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    listOf("Работы", "Задания", "Ученики").forEachIndexed { index, title ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                    }
                }
            }
        },
        floatingActionButton = {
            if (tab == 1) {
                ExtendedFloatingActionButton(onClick = { onEditPractice(NEW) }) { Text("+ Новая практика") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
        val bottom = padding.calculateBottomPadding()
        when (tab) {
            0 -> WorksTab(repository, onOpenSubmission, modifier, bottom)
            1 -> PracticesTab(repository, onEditPractice, modifier, bottom)
            else -> StudentsTab(repository, snackbar, modifier, bottom)
        }
    }
}

const val NEW = "new"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorksTab(
    repository: Repository,
    onOpenSubmission: (String) -> Unit,
    modifier: Modifier,
    bottom: androidx.compose.ui.unit.Dp,
) {
    var onlyUnchecked by rememberSaveable { mutableStateOf(true) }
    val loader = rememberLoader(onlyUnchecked) { repository.allSubmissions(onlyUnchecked) }

    PullToRefreshBox(isRefreshing = loader.refreshing && loader.state is Load.Ok, onRefresh = loader::reload, modifier = modifier) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = onlyUnchecked, onClick = { onlyUnchecked = true }, label = { Text("На проверке") })
                    FilterChip(selected = !onlyUnchecked, onClick = { onlyUnchecked = false }, label = { Text("Все работы") })
                }
            }
            when (val state = loader.state) {
                Load.Loading -> item { LoadingBox() }
                is Load.Failed -> item { ErrorBox(state.message, loader::reload) }
                is Load.Ok -> if (state.value.isEmpty()) {
                    item { EmptyBox("🎉", if (onlyUnchecked) "Все работы проверены" else "Ученики пока не сдавали работ") }
                } else {
                    items(state.value, key = { it.id }) { submission ->
                        SubmissionCard(submission, showStudent = true) { onOpenSubmission(submission.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticesTab(
    repository: Repository,
    onEditPractice: (String) -> Unit,
    modifier: Modifier,
    bottom: androidx.compose.ui.unit.Dp,
) {
    val content by repository.content.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom + 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        item {
            Text(
                "Здесь можно добавлять и менять практики и задания. Практика с меткой «Черновик» видна только вам, " +
                    "пока вы её не опубликуете.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (subject in listOf(Subject.CHEMISTRY, Subject.BIOLOGY)) {
            val practices = content.practicesOf(subject)
            item(key = "label-${subject.key}") { SectionLabel(subject.title) }
            if (practices.isEmpty()) {
                item(key = "empty-${subject.key}") {
                    Text(
                        "Пока нет практик",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(practices, key = { it.id }) { practice ->
                val count = content.tasksOf(practice.id).size
                Card(onClick = { onEditPractice(practice.id) }, colors = cardColors(), border = cardBorder()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${practice.grade} класс · ${practice.title}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (!practice.published) {
                                    Spacer(Modifier.width(8.dp))
                                    DraftChip()
                                }
                            }
                            Text(
                                "Заданий: $count",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentsTab(
    repository: Repository,
    snackbar: SnackbarHostState,
    modifier: Modifier,
    bottom: androidx.compose.ui.unit.Dp,
) {
    val loader = rememberLoader(Unit) { repository.students() }
    var selected by remember { mutableStateOf<User?>(null) }

    PullToRefreshBox(isRefreshing = loader.refreshing && loader.state is Load.Ok, onRefresh = loader::reload, modifier = modifier) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val state = loader.state) {
                Load.Loading -> item { LoadingBox() }
                is Load.Failed -> item { ErrorBox(state.message, loader::reload) }
                is Load.Ok -> if (state.value.isEmpty()) {
                    item { EmptyBox("👥", "Пока никто из учеников не зарегистрировался") }
                } else {
                    item {
                        Text(
                            "Учеников: ${state.value.size}. Нажмите на ученика, чтобы сбросить ему пароль.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(state.value, key = { it.id }) { student ->
                        Card(onClick = { selected = student }, colors = cardColors(), border = cardBorder()) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(student.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOfNotNull(student.group.takeIf { it.isNotBlank() }?.let { "$it класс" }, "логин ${student.username}")
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { student ->
        ResetPasswordDialog(student, repository, onDismiss = { selected = null }) { message ->
            selected = null
            snackbar.showSnackbar(message)
        }
    }
}

@Composable
private fun ResetPasswordDialog(
    student: User,
    repository: Repository,
    onDismiss: () -> Unit,
    onDone: suspend (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 16.dp),
        title = { Text("Новый пароль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Для ученика ${student.name} (логин ${student.username}). Сообщите ему новый пароль.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Новый пароль") },
                    supportingText = { Text(error ?: "Не меньше 6 символов") },
                    isError = error != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.length >= 6 && !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            repository.resetPassword(student.id, password)
                            onDone("Пароль для ${student.name} изменён")
                        } catch (e: Exception) {
                            error = e.message
                        }
                        saving = false
                    }
                },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
