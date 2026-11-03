package ru.prostayahimiya.app.ui.subject

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.prostayahimiya.app.R
import ru.prostayahimiya.app.data.Content
import ru.prostayahimiya.app.data.Grade
import ru.prostayahimiya.app.data.ProgressStore
import ru.prostayahimiya.app.data.Subject
import ru.prostayahimiya.app.ui.components.BackTopBar
import ru.prostayahimiya.app.ui.components.colors
import ru.prostayahimiya.app.ui.components.openLink
import ru.prostayahimiya.app.ui.components.tasksWord

/** Список классов по предмету: практика, если она есть, и ссылка на теорию. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectScreen(
    subject: Subject,
    store: ProgressStore,
    onBack: () -> Unit,
    onOpenPractice: (String) -> Unit,
) {
    val solved by store.solved.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val title = when (subject) {
        Subject.CHEMISTRY -> "Практики по химии"
        Subject.BIOLOGY -> "Практики по биологии"
    }

    Scaffold(
        topBar = { BackTopBar(title, onBack, scrollBehavior) },
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
            items(Content.grades(subject), key = { it.number }) { grade ->
                GradeCard(
                    grade = grade,
                    solvedCount = grade.practice?.checkableTaskIds?.count { it in solved } ?: 0,
                    onOpenPractice = onOpenPractice,
                )
            }
        }
    }
}

@Composable
private fun GradeCard(grade: Grade, solvedCount: Int, onOpenPractice: (String) -> Unit) {
    val context = LocalContext.current
    val colors = grade.subject.colors()
    val practice = grade.practice

    val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    // Карточку можно нажать, только если у класса есть практика.
    val content: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = colors.container, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            grade.number.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onContainer,
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(
                        "${grade.number} класс",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val total = practice?.checkableTaskIds?.size ?: 0
                    Text(
                        when {
                            practice == null -> "Задания скоро появятся"
                            solvedCount == total -> "Практика пройдена ✓"
                            else -> "Решено $solvedCount из $total ${tasksWord(total)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (practice != null) {
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (practice != null) {
                LinearProgressIndicator(
                    progress = { solvedCount.toFloat() / practice.checkableTaskIds.size },
                    color = colors.accent,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                if (grade.theoryUrl != null) {
                    TextButton(onClick = { context.openLink(grade.theoryUrl) }) {
                        Text("Теория в ЯКлассе")
                        Spacer(Modifier.width(6.dp))
                        Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                } else {
                    Spacer(Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
    if (practice != null) {
        Card(onClick = { onOpenPractice(practice.id) }, colors = cardColors, border = border, content = content)
    } else {
        Card(colors = cardColors, border = border, content = content)
    }
}
