package ru.prostayahimiya.app.ui.course

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import ru.prostayahimiya.app.ui.components.openLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.prostayahimiya.app.R
import ru.prostayahimiya.app.data.ChatRoom
import ru.prostayahimiya.app.data.Enrollment
import ru.prostayahimiya.app.data.Practice
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.data.Subject
import ru.prostayahimiya.app.data.Submission
import ru.prostayahimiya.app.data.formatDay
import ru.prostayahimiya.app.data.formatServerDate
import ru.prostayahimiya.app.ui.cabinet.SubmissionCard
import ru.prostayahimiya.app.ui.components.BackTopBar
import ru.prostayahimiya.app.ui.components.EmojiBadge
import ru.prostayahimiya.app.ui.components.EmptyBox
import ru.prostayahimiya.app.ui.components.ErrorBox
import ru.prostayahimiya.app.ui.components.GradeBadge
import ru.prostayahimiya.app.ui.components.Load
import ru.prostayahimiya.app.ui.components.LoadingBox
import ru.prostayahimiya.app.ui.components.SectionLabel
import ru.prostayahimiya.app.ui.components.cardBorder
import ru.prostayahimiya.app.ui.components.cardColors
import ru.prostayahimiya.app.ui.components.rememberLoader
import ru.prostayahimiya.app.ui.subject.DraftChip

/*
 * Личные курсы подготовки к ОГЭ и ЕГЭ. Купить курс можно в магазине beemei на сайте, а заниматься —
 * и на сайте, и здесь: у ученика «Подготовка» (практики только для него и чат с учителем курса),
 * у учителя — вкладка «Курсы» в панели учителя.
 */

private fun Subject.emoji() = if (this == Subject.BIOLOGY) "🧬" else "⚗️"

/** Карточки «Подготовка» на главной у ученика. Показываются только после покупки курса. */
@Composable
fun HomeCourses(courses: List<Enrollment>, onOpenCourse: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Подготовка")
        courses.forEach { en ->
            Card(
                onClick = { onOpenCourse(en.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (en.isLive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    EmojiBadge(en.subjectEnum.emoji(), MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(en.heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            if (en.isLive) listOfNotNull("Личный курс", en.expand?.teacher?.let { "учитель ${it.name}" }, en.untilText.ifBlank { null }).joinToString(" · ")
                            else "Курс закончился — продлить можно на сайте",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (en.endingSoon) {
                            Text(
                                "⏳ Осталось ${en.leftText} — продлите, чтобы не потерять доступ",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
private fun PracticeRow(practice: Practice, last: Submission?, showStatus: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, colors = cardColors(), border = cardBorder()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(practice.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                    if (!practice.published) {
                        Box(Modifier.padding(start = 8.dp)) { DraftChip() }
                    }
                }
                val parts = listOfNotNull(
                    practice.examLabel.takeIf { practice.exam },
                    when {
                        !showStatus -> null
                        last == null -> "Не сдано"
                        last.isGraded -> "Сдано · оценка ${last.grade}"
                        else -> "Сдано · на проверке"
                    },
                    practice.deadlineInstant?.let { "до ${formatDay(it)}" },
                )
                if (parts.isNotEmpty()) {
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (last?.isGraded == true) GradeBadge(last.grade)
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Курс у ученика: задания от учителя курса, чат с ним и тренажёр. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentCourseScreen(
    enrollmentId: String,
    repository: Repository,
    onBack: () -> Unit,
    onOpenPractice: (String) -> Unit,
    onOpenChat: (ChatRoom, String) -> Unit,
    onOpenTrainer: () -> Unit,
) {
    val content by repository.content.collectAsState()
    val submissions by repository.mySubmissionsState.collectAsState()
    val context = LocalContext.current
    val loader = rememberLoader(enrollmentId) {
        repository.refreshContent()
        repository.refreshMySubmissions()
        repository.enrollment(enrollmentId)
    }
    Scaffold(topBar = { BackTopBar("Подготовка", onBack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        PullToRefreshBox(
            isRefreshing = loader.refreshing && loader.state is Load.Ok,
            onRefresh = loader::reload,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val state = loader.state) {
                    Load.Loading -> item { LoadingBox() }
                    is Load.Failed -> item { ErrorBox(state.message, loader::reload) }
                    is Load.Ok -> {
                        val en = state.value
                        val list = content.coursePractices(en.id).filter { it.published }
                        item { HomeCourses(listOf(en)) {} }
                        if (!en.isLive) {
                            item {
                                EmptyBox("⏳", "Курс закончился. Продлите его в магазине beemei на сайте — задания и чат снова откроются.")
                            }
                            item {
                                Button(onClick = { context.openLink(repository.shopProductUrl(en.product)) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Продлить курс на сайте")
                                }
                            }
                            return@LazyColumn
                        }
                        if (en.endingSoon) item { RenewBanner(repository, en) }
                        item { CallButtons(repository, en, "учителю") }
                        item {
                            OutlinedButton(onClick = { onOpenChat(ChatRoom.course(en.id), "Чат с учителем курса") }, modifier = Modifier.fillMaxWidth()) {
                                Text("💬 Чат с учителем")
                            }
                        }
                        item { SectionLabel("Задания от учителя") }
                        if (list.isEmpty()) {
                            item { EmptyBox("📝", "Учитель скоро пришлёт первые задания. Пока можно написать ему в чат или потренироваться в тренажёре.") }
                        }
                        items(list, key = { it.id }) { p ->
                            PracticeRow(p, submissions.firstOrNull { it.practice == p.id }, showStatus = true) { onOpenPractice(p.id) }
                        }
                        item { TextButton(onClick = onOpenTrainer) { Text("🎯 Тренажёр") } }
                        item { ReviewCard(repository, en) }
                    }
                }
            }
        }
    }
}

/** Вкладка «Курсы» в панели учителя: ученики, которые купили курс. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherCoursesTab(repository: Repository, onOpenCourse: (String) -> Unit, modifier: Modifier, bottom: Dp) {
    val loader = rememberLoader(Unit) { repository.enrollments() }
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
                    item { EmptyBox("🎓", "Пока никто не купил курс. Когда ученик оплатит курс в магазине beemei на сайте, он появится здесь.") }
                } else {
                    item {
                        Text(
                            "Учеников на курсах: ${state.value.size}. Нажмите на ученика, чтобы дать ему задания и написать в чат.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(state.value, key = { it.id }) { en ->
                        Card(onClick = { onOpenCourse(en.id) }, colors = cardColors(), border = cardBorder()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                EmojiBadge(en.subjectEnum.emoji(), MaterialTheme.colorScheme.secondaryContainer)
                                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                    Text(en.expand?.student?.name ?: "Ученик", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        listOf(en.exam, en.subjectEnum.title, en.untilText.ifBlank { "без срока" }).joinToString(" · ") +
                                            if (en.isLive) "" else " · курс закончился",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Ученик курса у учителя: его практики, работы и чат. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherCourseScreen(
    enrollmentId: String,
    repository: Repository,
    onBack: () -> Unit,
    onOpenChat: (ChatRoom, String) -> Unit,
    onEditPractice: (String) -> Unit,
    onNewPractice: (Enrollment) -> Unit,
    onOpenSubmission: (String) -> Unit,
) {
    val content by repository.content.collectAsState()
    val loader = rememberLoader(enrollmentId) {
        repository.refreshContent()
        repository.enrollment(enrollmentId) to repository.allSubmissions(onlyUnchecked = false)
    }
    Scaffold(topBar = { BackTopBar("Ученик курса", onBack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
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
                when (val state = loader.state) {
                    Load.Loading -> item { LoadingBox() }
                    is Load.Failed -> item { ErrorBox(state.message, loader::reload) }
                    is Load.Ok -> {
                        val (en, subs) = state.value
                        val student = en.expand?.student
                        val list = content.coursePractices(en.id)
                        val ids = list.map { it.id }.toSet()
                        val works = subs.filter { it.practice in ids }
                        item {
                            Card(colors = cardColors(), border = cardBorder()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(student?.name?.firstOrNull()?.uppercase() ?: "?", color = MaterialTheme.colorScheme.onPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                        Text(student?.name ?: "Ученик", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                        Text(en.heading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            listOfNotNull(
                                                student?.let { "логин ${it.username}" },
                                                student?.group?.takeIf { it.isNotBlank() }?.let { "$it класс" },
                                                en.untilText.ifBlank { null },
                                                "курс закончился".takeIf { !en.isLive },
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        if (en.isLive) item { CallButtons(repository, en, "ученику") }
                        item {
                            OutlinedButton(onClick = { onOpenChat(ChatRoom.course(en.id), student?.name ?: "Ученик курса") }, modifier = Modifier.fillMaxWidth()) {
                                Text("💬 Чат с учеником")
                            }
                        }
                        item { SectionLabel("Практики ученика (${list.size})") }
                        if (list.isEmpty()) {
                            item {
                                Text(
                                    "Эти практики видит только этот ученик. Можно задать обычные задания, контрольную со временем или сгенерировать задания нейросетью.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(list, key = { it.id }) { p -> PracticeRow(p, null, showStatus = false) { onEditPractice(p.id) } }
                        item {
                            Button(onClick = { onNewPractice(en) }, modifier = Modifier.fillMaxWidth()) { Text("+ Новая практика для ученика") }
                        }
                        item { SectionLabel("Работы ученика (${works.size})") }
                        if (works.isEmpty()) {
                            item { Text("Ученик пока не сдавал работ.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        items(works, key = { it.id }) { s -> SubmissionCard(s, showStudent = false) { onOpenSubmission(s.id) } }
                    }
                }
            }
        }
    }
}
