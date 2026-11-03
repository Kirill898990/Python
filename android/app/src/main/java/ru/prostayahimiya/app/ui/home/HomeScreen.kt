package ru.prostayahimiya.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.prostayahimiya.app.R
import ru.prostayahimiya.app.data.Content
import ru.prostayahimiya.app.data.ProgressStore
import ru.prostayahimiya.app.data.Subject
import ru.prostayahimiya.app.ui.components.EmojiBadge
import ru.prostayahimiya.app.ui.components.SectionLabel
import ru.prostayahimiya.app.ui.components.colors
import ru.prostayahimiya.app.ui.components.openLink
import ru.prostayahimiya.app.ui.components.tasksWord

@Composable
fun HomeScreen(
    store: ProgressStore,
    onOpenSubject: (Subject) -> Unit,
    onOpenTheory: () -> Unit,
) {
    val solved by store.solved.collectAsState()
    val allTasks = Content.practices().flatMap { it.checkableTaskIds }
    val solvedCount = allTasks.count { it in solved }
    val context = LocalContext.current

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header() }
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
            if (allTasks.isNotEmpty()) {
                item { ProgressCard(solvedCount, allTasks.size) }
            }
            item { SectionLabel("Материалы к занятиям") }
            item {
                MenuCard(
                    emoji = Subject.BIOLOGY.emoji,
                    badgeColor = Subject.BIOLOGY.colors().container,
                    title = "Практики по биологии",
                    subtitle = "${Content.gradeRange(Subject.BIOLOGY)} класс",
                    onClick = { onOpenSubject(Subject.BIOLOGY) },
                )
            }
            item {
                MenuCard(
                    emoji = Subject.CHEMISTRY.emoji,
                    badgeColor = Subject.CHEMISTRY.colors().container,
                    title = "Практики по химии",
                    subtitle = "${Content.gradeRange(Subject.CHEMISTRY)} класс",
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
                    Text(
                        Content.AUTHOR,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
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
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        EmojiBadge("🔬", MaterialTheme.colorScheme.primary, size = 56.dp)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                "Химия и биология",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
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
                        "${Content.allGradesRange()} классы",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(solved: Int, total: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
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
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else solved.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
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
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            EmojiBadge(emoji, badgeColor)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
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
