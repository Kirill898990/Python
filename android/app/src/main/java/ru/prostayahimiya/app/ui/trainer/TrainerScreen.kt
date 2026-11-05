package ru.prostayahimiya.app.ui.trainer

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import ru.prostayahimiya.app.R
import ru.prostayahimiya.app.chem.Chem
import ru.prostayahimiya.app.chem.Trainer
import ru.prostayahimiya.app.chem.TrainerResult
import ru.prostayahimiya.app.chem.TrainerTask
import ru.prostayahimiya.app.chem.TrainerTopic
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.ui.components.BackTopBar
import ru.prostayahimiya.app.ui.components.EmojiBadge
import ru.prostayahimiya.app.ui.components.cardBorder
import ru.prostayahimiya.app.ui.components.cardColors

/** Счёт тренажёра — только на этом телефоне. */
private class TrainerStats(context: Context, userId: String) {
    private val prefs = context.getSharedPreferences("trainer_$userId", Context.MODE_PRIVATE)
    var total: Int
        get() = prefs.getInt("total", 0)
        set(v) = prefs.edit { putInt("total", v) }
    var best: Int
        get() = prefs.getInt("best", 0)
        set(v) = prefs.edit { putInt("best", v) }
}

/** Тренажёр: задания придумываются сами и проверяются сразу. Учителю ничего не отправляется. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrainerScreen(repository: Repository, onBack: () -> Unit, onOpenReference: () -> Unit) {
    val context = LocalContext.current
    val user by repository.user.collectAsState()
    val stats = remember(user?.id) { TrainerStats(context, user?.id ?: "guest") }
    val trainer = remember { Trainer() }
    var topic by rememberSaveable { mutableStateOf(TrainerTopic.MIX) }
    var task by remember { mutableStateOf(trainer.next(topic)) }
    // Номер задания: по нему сбрасываются поля ввода и результат.
    var round by remember { mutableIntStateOf(0) }
    var streak by rememberSaveable { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(stats.total) }
    var best by remember { mutableIntStateOf(stats.best) }

    fun next() {
        task = trainer.next(topic)
        round++
    }

    Scaffold(
        topBar = {
            BackTopBar("Тренажёр", onBack) {
                IconButton(onClick = onOpenReference) {
                    Icon(painterResource(R.drawable.ic_book), contentDescription = "Справочник")
                }
            }
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text(
                "Задания придумываются сами и проверяются сразу. Учителю ничего не отправляется — тренируйся сколько хочешь.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrainerTopic.entries.forEach { t ->
                    FilterChip(selected = topic == t, onClick = {
                        topic = t
                        next()
                    }, label = { Text(t.title) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("$streak", "подряд 🔥", Modifier.weight(1f))
                StatBox("$best", "рекорд", Modifier.weight(1f))
                StatBox("$total", "решено всего", Modifier.weight(1f))
            }
            key(round) {
                TaskCard(
                    task = task,
                    onCorrect = {
                        streak++
                        total++
                        stats.total = total
                        if (streak > best) {
                            best = streak
                            stats.best = best
                        }
                    },
                    onMiss = { streak = 0 },
                    onNext = ::next,
                )
            }
        }
    }
}

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier) {
    Card(colors = cardColors(), border = cardBorder(), modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskCard(task: TrainerTask, onCorrect: () -> Unit, onMiss: () -> Unit, onNext: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val coefficients = remember { mutableStateOf(List((task as? TrainerTask.Balance)?.equation?.substances?.size ?: 0) { "" }) }
    var result by remember { mutableStateOf<TrainerResult?>(null) }
    var solved by remember { mutableStateOf(false) }
    var wrongOnce by remember { mutableStateOf(false) }

    fun check() {
        if (solved) return onNext()
        val r = Trainer.check(task, text, coefficients.value)
        result = r
        when {
            r.isCorrect -> {
                solved = true
                onCorrect()
            }
            r.kind == "wrong" -> {
                wrongOnce = true
                onMiss()
            }
        }
    }
    val changed = { result = null }

    Card(colors = cardColors(), border = cardBorder()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge("🎯", MaterialTheme.colorScheme.primary, size = 40.dp)
                Text(
                    task.topic.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            when (task) {
                is TrainerTask.Formula -> {
                    Text(task.name.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; changed() },
                        label = { Text("Формула") },
                        singleLine = true,
                        supportingText = if (text.isNotBlank()) ({ Text("Получается: ${Chem.pretty(Chem.normalize(text))}") }) else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { check() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is TrainerTask.Molar -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("M(${Chem.pretty(Chem.normalize(task.formula))}) =", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter { it.isDigit() || it == ',' || it == '.' }.take(8); changed() },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { check() }),
                        modifier = Modifier.padding(horizontal = 8.dp).size(width = 110.dp, height = 56.dp)
                            .semantics { contentDescription = "Молярная масса, г/моль" },
                    )
                    Text("г/моль", style = MaterialTheme.typography.titleMedium)
                }
                is TrainerTask.Balance -> {
                    Text("Пустая клеточка — коэффициент 1.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val eq = task.equation
                    FlowRow(verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        eq.substances.forEachIndexed { k, formula ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (k > 0) {
                                    Text(
                                        if (k == eq.reactants.size) "→" else "+",
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                    )
                                }
                                Coefficient(
                                    value = coefficients.value[k],
                                    description = "Коэффициент перед ${Chem.pretty(formula)}",
                                    onValueChange = { v ->
                                        coefficients.value = coefficients.value.toMutableList().also { it[k] = v.filter(Char::isDigit).take(2) }
                                        changed()
                                    },
                                )
                                Text(Chem.pretty(formula), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
                is TrainerTask.Classify -> {
                    Text(task.question, style = MaterialTheme.typography.bodyLarge)
                    task.options.forEachIndexed { i, option ->
                        val n = (i + 1).toString()
                        val selected = text == n
                        val shape = RoundedCornerShape(12.dp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
                                .selectable(selected = selected, role = Role.RadioButton) { text = n; changed() }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
                            Text(option, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = ::check) { Text(if (solved) "Дальше →" else "Проверить") }
                if (!solved && wrongOnce) {
                    TextButton(onClick = {
                        solved = true
                        onMiss()
                        result = TrainerResult("hint", "Ответ: ${task.explain}")
                    }) { Text("Показать ответ") }
                }
                if (!solved) {
                    TextButton(onClick = {
                        onMiss()
                        onNext()
                    }) { Text("Пропустить") }
                }
            }
            result?.let { Feedback(it) }
        }
    }
}

/** Клеточка для коэффициента. */
@Composable
private fun Coefficient(value: String, description: String, onValueChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(color = colors.onSurface, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = Modifier
            .size(width = 40.dp, height = 48.dp)
            .border(1.dp, colors.outline, RoundedCornerShape(8.dp))
            .semantics { contentDescription = description },
        decorationBox = { inner -> Box(contentAlignment = Alignment.Center) { inner() } },
    )
}

@Composable
private fun Feedback(result: TrainerResult) {
    val scheme = MaterialTheme.colorScheme
    val (container, content, prefix) = when (result.kind) {
        "correct" -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, "✅ ")
        "hint" -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, "🤔 ")
        else -> Triple(scheme.errorContainer, scheme.onErrorContainer, "❌ ")
    }
    Surface(shape = RoundedCornerShape(12.dp), color = container, modifier = Modifier.fillMaxWidth()) {
        Text(prefix + result.message, style = MaterialTheme.typography.bodyMedium, color = content, modifier = Modifier.padding(12.dp))
    }
}
