package ru.prostayahimiya.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import ru.prostayahimiya.app.ui.components.tasksWord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: Repository,
    onOpenSubject: (Subject) -> Unit,
    onOpenTheory: () -> Unit,
    onOpenCabinet: () -> Unit,
    onOpenAdmin: () -> Unit,
) {
    val user by repository.user.collectAsState()
    val content by repository.content.collectAsState()
    val contentError by repository.contentError.collectAsState()
    val solved by repository.progress.solved.collectAsState()
    val allTasks = content.checkableTaskIds
    val solvedCount = allTasks.count { it in solved }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            refreshing = true
            repository.refreshContent()
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
                if (allTasks.isNotEmpty() && user?.isTeacher != true) {
                    item { ProgressCard(solvedCount, allTasks.size) }
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

@Composable
private fun Header(grades: String, user: User?, onOpenCabinet: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        EmojiBadge("🔬", MaterialTheme.colorScheme.primary, size = 56.dp)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text("Химия и биология", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    "Теория и практика",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        "$grades классы",
                        style = MaterialTheme.typography.labelMedium,
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
private fun ProgressCard(solved: Int, total: Int) {
    Card(colors = cardColors(), border = cardBorder()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                if (solved == total) "Все задания решены! 🎉" else "Твой прогресс",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Решено $solved из $total ${tasksWord(total)}",
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
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
