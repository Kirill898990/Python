package ru.prostayahimiya.app.ui.subject

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import ru.prostayahimiya.app.data.Practice
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.data.Subject
import ru.prostayahimiya.app.data.Submission
import ru.prostayahimiya.app.ui.components.BackTopBar
import ru.prostayahimiya.app.ui.components.GradeBadge
import ru.prostayahimiya.app.ui.components.cardBorder
import ru.prostayahimiya.app.ui.components.cardColors
import ru.prostayahimiya.app.ui.components.colors
import ru.prostayahimiya.app.ui.components.openLink
import ru.prostayahimiya.app.ui.components.tasksWord

/** Классы по предмету: практики каждого класса и ссылка на теорию. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectScreen(
    subject: Subject,
    repository: Repository,
    onBack: () -> Unit,
    onOpenPractice: (String) -> Unit,
) {
    val content by repository.content.collectAsState()
    val user by repository.user.collectAsState()
    val submissions by repository.mySubmissionsState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val title = when (subject) {
        Subject.CHEMISTRY -> "Практики по химии"
        Subject.BIOLOGY -> "Практики по биологии"
    }
    val practices = content.practicesOf(subject)

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
            items(Content.grades(subject, content), key = { it }) { grade ->
                GradeCard(
                    subject = subject,
                    grade = grade,
                    practices = practices.filter { it.grade == grade },
                    taskCount = { practice -> content.tasksOf(practice.id).size },
                    // Последняя сданная работа по практике (список уже отсортирован: новые сверху).
                    lastSubmission = { practice -> submissions.firstOrNull { it.practice == practice.id } },
                    showStatus = user?.isTeacher != true,
                    onOpenPractice = onOpenPractice,
                )
            }
        }
    }
}

@Composable
private fun GradeCard(
    subject: Subject,
    grade: Int,
    practices: List<Practice>,
    taskCount: (Practice) -> Int,
    lastSubmission: (Practice) -> Submission?,
    showStatus: Boolean,
    onOpenPractice: (String) -> Unit,
) {
    val context = LocalContext.current
    val colors = subject.colors()
    val theoryUrl = Content.theoryUrl(subject, grade)

    Card(colors = cardColors(), border = cardBorder()) {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                Surface(shape = CircleShape, color = colors.container, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            grade.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onContainer,
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text("$grade класс", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (practices.isEmpty()) {
                        Text(
                            "Задания скоро появятся",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            practices.forEach { practice ->
                val total = taskCount(practice)
                val last = lastSubmission(practice)
                HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPractice(practice.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(practice.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            if (!practice.published) {
                                Spacer(Modifier.width(8.dp))
                                DraftChip()
                            }
                        }
                        Text(
                            when {
                                !showStatus || last == null -> "$total ${tasksWord(total)}" + if (showStatus) " · не сдано" else ""
                                last.isGraded -> "Сдано · оценка ${last.grade}"
                                else -> "Сдано · на проверке"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showStatus && last != null && last.isGraded) {
                        GradeBadge(last.grade, size = 36.dp)
                    }
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }

            if (theoryUrl != null) {
                TextButton(onClick = { context.openLink(theoryUrl) }, modifier = Modifier.padding(horizontal = 4.dp)) {
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

/** Метка «Черновик» — эту практику видит только учитель. */
@Composable
fun DraftChip() {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            "Черновик",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
