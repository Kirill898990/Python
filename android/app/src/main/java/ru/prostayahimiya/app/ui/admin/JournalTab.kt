package ru.prostayahimiya.app.ui.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.prostayahimiya.app.data.JournalCell
import ru.prostayahimiya.app.data.JournalTable
import ru.prostayahimiya.app.data.PracticeStats
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.data.buildJournal
import ru.prostayahimiya.app.data.classesOf
import ru.prostayahimiya.app.data.formatAverage
import ru.prostayahimiya.app.data.practiceStats
import ru.prostayahimiya.app.data.toCsv
import ru.prostayahimiya.app.ui.components.EmptyBox
import ru.prostayahimiya.app.ui.components.ErrorBox
import ru.prostayahimiya.app.ui.components.GradeBadge
import ru.prostayahimiya.app.ui.components.Load
import ru.prostayahimiya.app.ui.components.LoadingBox
import ru.prostayahimiya.app.ui.components.SectionLabel
import ru.prostayahimiya.app.ui.components.cardBorder
import ru.prostayahimiya.app.ui.components.cardColors
import ru.prostayahimiya.app.ui.components.rememberLoader

private val NAME_WIDTH = 140.dp
private val CELL_WIDTH = 84.dp

/** Журнал: оценки класса по практикам, выгрузка для Excel и статистика. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalTab(
    repository: Repository,
    snackbar: SnackbarHostState,
    onOpenSubmission: (String) -> Unit,
    modifier: Modifier,
    bottom: Dp,
) {
    val content by repository.content.collectAsState()
    val loader = rememberLoader(Unit) { repository.journal() }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCsv by remember { mutableStateOf("") }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(pendingCsv.toByteArray(Charsets.UTF_8)) } != null
                }.getOrDefault(false)
            }
            snackbar.showSnackbar(if (ok) "Журнал сохранён" else "Не получилось сохранить файл")
        }
    }

    PullToRefreshBox(isRefreshing = loader.refreshing && loader.state is Load.Ok, onRefresh = loader::reload, modifier = modifier) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val state = loader.state) {
                Load.Loading -> item { LoadingBox() }
                is Load.Failed -> item { ErrorBox(state.message, loader::reload) }
                is Load.Ok -> {
                    val (students, submissions) = state.value
                    val groups = classesOf(students)
                    if (groups.isEmpty()) {
                        item { EmptyBox("👥", "Журнал появится, когда ученики зарегистрируются") }
                        return@LazyColumn
                    }
                    val group = selected?.takeIf { it in groups } ?: groups.first()
                    val table = buildJournal(group, students, submissions, content.practices)
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            groups.forEach { g -> FilterChip(selected = g == group, onClick = { selected = g }, label = { Text(g) }) }
                        }
                    }
                    if (table.practices.isEmpty()) {
                        item { EmptyBox("📝", "Для $group пока нет опубликованных практик") }
                        return@LazyColumn
                    }
                    item { JournalTableView(table, onOpenSubmission) }
                    item {
                        Text(
                            "Цифра — оценка, ✓ — сдано и ждёт проверки, ⏰ — сдано с опозданием, красный прочерк — не сдано после срока. " +
                                "Нажмите на клетку, чтобы открыть работу.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        OutlinedButton(onClick = {
                            pendingCsv = table.toCsv()
                            saveFile.launch("Журнал $group.csv")
                        }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить для Excel") }
                    }
                    item { SectionLabel("Статистика · $group") }
                    val kids = students.filter { it.group == group }
                    items(table.practices, key = { it.id }) { practice ->
                        StatsCard(practiceStats(practice, kids, submissions))
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalTableView(table: JournalTable, onOpenSubmission: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(colors = cardColors(), border = cardBorder()) {
        // Таблица шире экрана, если практик много: её можно листать вбок.
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(vertical = 8.dp)) {
                HeaderCell("Ученик", NAME_WIDTH, TextAlign.Start)
                table.practices.forEach { HeaderCell("${it.grade} кл. · ${it.title}", CELL_WIDTH) }
                HeaderCell("Средний", CELL_WIDTH)
            }
            table.rows.forEach { row ->
                HorizontalDivider(color = scheme.outlineVariant, modifier = Modifier.width(NAME_WIDTH + CELL_WIDTH * (table.practices.size + 1)))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(52.dp)) {
                    Text(
                        row.student.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(NAME_WIDTH).padding(horizontal = 12.dp),
                    )
                    row.cells.forEach { cell ->
                        val id = when (cell) {
                            is JournalCell.Grade -> cell.submissionId
                            is JournalCell.Submitted -> cell.submissionId
                            else -> null
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(CELL_WIDTH)
                                .height(52.dp)
                                .then(if (id != null) Modifier.clickable { onOpenSubmission(id) } else Modifier),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when (cell) {
                                    is JournalCell.Grade -> GradeBadge(cell.grade, size = 32.dp)
                                    is JournalCell.Submitted -> Surface(shape = RoundedCornerShape(50), color = scheme.surfaceContainerHighest) {
                                        Text("✓", modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
                                    }
                                    JournalCell.Missed -> Text("—", color = scheme.error, fontWeight = FontWeight.Bold)
                                    JournalCell.Empty -> Unit
                                }
                                val late = (cell as? JournalCell.Grade)?.late == true || (cell as? JournalCell.Submitted)?.late == true
                                if (late) Text("⏰", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text(
                        formatAverage(row.average),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(CELL_WIDTH),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp, align: TextAlign = TextAlign.Center) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).padding(horizontal = 12.dp),
    )
}

@Composable
private fun StatsCard(stats: PracticeStats) {
    val scheme = MaterialTheme.colorScheme
    Card(colors = cardColors(), border = cardBorder()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stats.practice.fullTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            StatLine("Сдали", "${stats.submitted} из ${stats.students}" + if (stats.late > 0) " (с опозданием: ${stats.late})" else "")
            StatLine("Средняя оценка", formatAverage(stats.averageGrade).ifEmpty { "—" })
            if (stats.hardest.isNotEmpty()) {
                Text("Самые трудные задания", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                stats.hardest.forEach { t ->
                    Row {
                        Text(t.prompt, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(
                            "верно у ${t.right} из ${t.total}" +
                                if (t.averageAttempts > 0) " · попыток ≈ ${formatAverage(t.averageAttempts)}" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.error,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            } else if (stats.allSolved) {
                Text("Все задания с автопроверкой решены верно 🎉", style = MaterialTheme.typography.bodyMedium, color = scheme.primary)
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
