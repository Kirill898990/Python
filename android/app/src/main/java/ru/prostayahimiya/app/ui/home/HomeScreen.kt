package ru.prostayahimiya.app.ui.home

import ru.prostayahimiya.app.data.deadlineStatus
import ru.prostayahimiya.app.data.Practice
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.prostayahimiya.app.R
import ru.prostayahimiya.app.data.Content
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.data.Subject
import ru.prostayahimiya.app.data.User
import ru.prostayahimiya.app.ui.components.EmojiBadge
import ru.prostayahimiya.app.ui.components.OfflineBanner
import ru.prostayahimiya.app.ui.components.SectionLabel
import ru.prostayahimiya.app.ui.components.cardBorder
import ru.prostayahimiya.app.ui.components.cardColors
import ru.prostayahimiya.app.ui.components.colors
import ru.prostayahimiya.app.ui.components.openLink
import ru.prostayahimiya.app.ui.components.practicesWord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: Repository,
    onOpenSubject: (Subject) -> Unit,
    onOpenTheory: () -> Unit,
    onOpenCabinet: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenPractice: (String) -> Unit,
    onOpenReference: () -> Unit,
) {
    val user by repository.user.collectAsState()
    val content by repository.content.collectAsState()
    val contentError by repository.contentError.collectAsState()
    val submissions by repository.mySubmissionsState.collectAsState()
    val unreadChats by repository.unreadChats.collectAsState()
    // Каждый раз, когда главная открывается, проверяем, нет ли новых сообщений.
    LaunchedEffect(user?.id) { repository.refreshUnreadChats() }
    // Прогресс по сданным работам: одинаковый на сайте и в приложении.
    val submittedPractices = submissions.map { it.practice }.filter { content.practice(it) != null }.toSet().size
    val totalPractices = content.practices.size
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            refreshing = true
            repository.refreshContent()
            if (user?.isTeacher != true) repository.refreshMySubmissions()
            repository.refreshUnreadChats()
            refreshing = false
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = ::refresh,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { Header(Content.allGradesRange(content), user, onOpenCabinet) }
                if (contentError != null) {
                    item { OfflineBanner(contentError.orEmpty(), ::refresh) }
                }
                item {
                    Image(
                        painter = painterResource(R.drawable.banner_lab),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3.1f)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                }
                if (user?.isTeacher == true) {
                    item {
                        MenuCard(
                            emoji = "📋",
                            badgeColor = MaterialTheme.colorScheme.primary,
                            title = "Панель учителя",
                            subtitle = "Проверка работ, оценки и задания",
                            onClick = onOpenAdmin,
                            highlighted = true,
                        )
                    }
                }
                item {
                    MenuCard(
                        emoji = "💬",
                        badgeColor = MaterialTheme.colorScheme.secondaryContainer,
                        title = "Чат",
                        subtitle = if (user?.isTeacher == true) "Чаты классов и личные сообщения" else "Чат класса и вопросы учителю",
                        onClick = onOpenChat,
                        badge = when {
                            unreadChats == 1 -> "новое"
                            unreadChats > 1 -> "новые: $unreadChats"
                            else -> null
                        },
                    )
                }
                if (totalPractices > 0 && user?.isTeacher != true) {
                    item { ProgressCard(submittedPractices, totalPractices) }
                }
                // Несданные практики со сроком, ближайшие сверху.
                val upcoming = if (user?.isTeacher == true) emptyList() else content.practices
                    .filter { p -> p.deadlineInstant != null && submissions.none { it.practice == p.id } }
                    .sortedBy { it.deadlineInstant }
                    .take(4)
                if (upcoming.isNotEmpty()) {
                    item { DeadlinesCard(upcoming, onOpenPractice) }
                }
                item { SectionLabel("Материалы к занятиям") }
                item {
                    MenuCard(
                        emoji = Subject.BIOLOGY.emoji,
                        badgeColor = Subject.BIOLOGY.colors().container,
                        title = "Практики по биологии",
                        subtitle = "${Content.gradeRange(Subject.BIOLOGY, content)} класс",
                        onClick = { onOpenSubject(Subject.BIOLOGY) },
                    )
                }
                item {
                    MenuCard(
                        emoji = Subject.CHEMISTRY.emoji,
                        badgeColor = Subject.CHEMISTRY.colors().container,
                        title = "Практики по химии",
                        subtitle = "${Content.gradeRange(Subject.CHEMISTRY, content)} класс",
                        onClick = { onOpenSubject(Subject.CHEMISTRY) },
                    )
                }
                item {
                    MenuCard(
                        emoji = "📚",
                        badgeColor = MaterialTheme.colorScheme.secondaryContainer,
                        title = "Теоретическая часть",
                        subtitle = "Учебники ЯКласса по классам",
                        onClick = onOpenTheory,
                    )
                }
                item {
                    MenuCard(
                        emoji = "🧪",
                        badgeColor = MaterialTheme.colorScheme.primaryContainer,
                        title = "Справочник",
                        subtitle = "Таблица Менделеева и таблица растворимости",
                        onClick = onOpenReference,
                    )
                }
                item {
                    Image(
                        painter = painterResource(R.drawable.scientists),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .aspectRatio(1.55f)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                }
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Text(Content.AUTHOR, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Понравилось приложение? Расскажи, что улучшить",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                        )
                        OutlinedButton(onClick = { context.openLink(Content.FEEDBACK_URL) }) {
                            Icon(painterResource(R.drawable.ic_star), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Оставить отзыв")
                        }
                        TextButton(onClick = { context.openLink(Content.PRIVACY_URL) }, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Политика конфиденциальности", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(grades: String, user: User?, onOpenCabinet: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        EmojiBadge("🔬", MaterialTheme.colorScheme.primary, size = 56.dp)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text("Химия и биология", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            // На узких экранах табличка с классами переносится на следующую строку, а не ломается.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    "Теория и практика",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        "$grades классы",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
        // Кружок с первой буквой имени — вход в личный кабинет.
        Surface(
            onClick = onOpenCabinet,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(44.dp).semantics { contentDescription = "Личный кабинет" },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    user?.name?.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun DeadlinesCard(practices: List<Practice>, onOpenPractice: (String) -> Unit) {
    Card(colors = cardColors(), border = cardBorder()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(
                "⏰ Сроки сдачи",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            practices.forEach { practice ->
                val due = practice.deadlineStatus(submitted = false) ?: return@forEach
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenPractice(practice.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(practice.fullTitle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        due.text,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (due.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(solved: Int, total: Int) {
    Card(colors = cardColors(), border = cardBorder()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                if (solved == total) "Все практики сданы! 🎉" else "Твой прогресс",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Сдано $solved из $total ${practicesWord(total)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            LinearProgressIndicator(progress = { solved.toFloat() / total }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MenuCard(
    emoji: String,
    badgeColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    badge: String? = null,
) {
    Card(
        onClick = onClick,
        colors = if (highlighted) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            cardColors()
        },
        border = if (highlighted) null else cardBorder(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            EmojiBadge(emoji, badgeColor)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (badge != null) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp)) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
