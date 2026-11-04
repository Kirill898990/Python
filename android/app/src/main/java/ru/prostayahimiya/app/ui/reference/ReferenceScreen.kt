package ru.prostayahimiya.app.ui.reference

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.prostayahimiya.app.chem.ElementInfo
import ru.prostayahimiya.app.chem.Reference
import ru.prostayahimiya.app.ui.components.BackTopBar

/** Цвета семейств, как в школьной таблице: s — розовые, p — жёлтые, d — голубые, f — зелёные. */
private fun blockColor(block: Char) = when (block) {
    's' -> Color(0xFFF6C1CF)
    'p' -> Color(0xFFF7E39B)
    'd' -> Color(0xFFA9D4F5)
    else -> Color(0xFFB9E5B0)
}

private fun blockName(block: Char) = "$block-элемент"

private val INK = Color(0xFF1A1C1B)

/** Масса для задач: как в школьной таблице (хлор — 35,5, остальные округляем до целых). */
private fun schoolMass(e: ElementInfo): String = when {
    e.mass.startsWith("[") -> e.mass
    e.symbol == "Cl" -> "35,5"
    else -> Math.round(e.mass.toDouble()).toString()
}

/** Справочник: таблица Менделеева и таблица растворимости. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceScreen(onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = { BackTopBar("Справочник", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val tabs = listOf("Таблица Менделеева", "Растворимость")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, title ->
                    SegmentedButton(
                        selected = tab == index,
                        onClick = { tab = index },
                        shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                    ) { Text(title, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium) }
                }
            }
            if (tab == 0) PeriodicTable() else SolubilityTable()
        }
    }
}

private val CELL = 46.dp
private val GAP = 3.dp

@Composable
private fun PeriodicTable() {
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val byPosition = Reference.elements.associateBy { it.row to it.col }
    val scheme = MaterialTheme.colorScheme

    // Таблица шире экрана телефона: её можно листать вбок.
    Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(GAP)) {
        Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
            Spacer(Modifier.width(18.dp))
            (1..18).forEach { col ->
                Text("$col", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.width(CELL), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        (1..10).forEach { row ->
            if (row == 8) {
                Spacer(Modifier.height(6.dp))
                return@forEach
            }
            Row(horizontalArrangement = Arrangement.spacedBy(GAP), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (row <= 7) "$row" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.width(18.dp),
                )
                (1..18).forEach { col ->
                    val element = byPosition[row to col]
                    when {
                        element != null -> ElementCell(element) { selected = element.z }
                        row == 6 && col == 3 -> GapCell("La–Lu")
                        row == 7 && col == 3 -> GapCell("Ac–Lr")
                        else -> Spacer(Modifier.size(CELL))
                    }
                }
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf('s', 'p', 'd', 'f').forEach { block ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(4.dp), color = blockColor(block), modifier = Modifier.size(14.dp)) {}
                Text(" ${blockName(block)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
    Text(
        "Нажмите на элемент, чтобы увидеть подробности. Под символом — атомная масса, как её берут в задачах.",
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
    )

    selected?.let { z ->
        val e = Reference.elements.first { it.z == z }
        val period = if (e.row >= 9) e.row - 3 else e.row
        val position = when (e.row) {
            9 -> "лантаноид, 3 группа"
            10 -> "актиноид, 3 группа"
            else -> "${e.col} группа"
        }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("${e.symbol} — ${e.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = blockColor(e.block), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${e.z}", color = INK, modifier = Modifier.align(Alignment.Start))
                            Text(e.symbol, color = INK, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                            Text(e.mass.replace('.', ','), color = INK)
                        }
                    }
                    InfoLine("Порядковый номер", "${e.z}")
                    InfoLine("Относительная атомная масса Ar", e.mass.replace('.', ','))
                    InfoLine("Для задач обычно берут", schoolMass(e))
                    InfoLine("Период", "$period")
                    InfoLine("Положение", position)
                    InfoLine("Семейство", blockName(e.block))
                    if (e.mass.startsWith("[")) {
                        Text(
                            "В квадратных скобках — массовое число самого устойчивого изотопа: элемент радиоактивный.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Закрыть") } },
        )
    }
}

@Composable
private fun ElementCell(e: ElementInfo, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = blockColor(e.block),
        modifier = Modifier
            .size(width = CELL, height = 54.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${e.z} ${e.name}" },
    ) {
        Column(Modifier.padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${e.z}", fontSize = 9.sp, color = INK.copy(alpha = 0.75f), modifier = Modifier.align(Alignment.Start).padding(start = 2.dp))
            Text(e.symbol, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = INK)
            Text(schoolMass(e), fontSize = 9.sp, color = INK.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun GapCell(text: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = CELL, height = 54.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
    ) { Text(text, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private val SOL_COLORS = mapOf('Р' to Color(0xFFB9E5B0), 'М' to Color(0xFFF7E39B), 'Н' to Color(0xFFF6C1CF), '—' to Color(0xFFD9DCDA))
private val SOL_TEXT = mapOf('Р' to "растворимо", 'М' to "малорастворимо", 'Н' to "нерастворимо", '—' to "не существует или разлагается водой")

@Composable
private fun SolubilityTable() {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Spacer(Modifier.width(68.dp))
            Reference.cations.forEach { c ->
                Text(c, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, modifier = Modifier.width(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Reference.anions.forEachIndexed { i, anion ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(anion, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, modifier = Modifier.width(68.dp))
                Reference.solubility[i].forEachIndexed { k, v ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = SOL_COLORS[v] ?: scheme.surfaceVariant,
                        modifier = Modifier
                            .size(36.dp, 28.dp)
                            .semantics { contentDescription = "${Reference.cations[k]} и $anion: ${SOL_TEXT[v]}" },
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text("$v", color = INK, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SOL_TEXT.forEach { (k, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(4.dp), color = SOL_COLORS.getValue(k), modifier = Modifier.size(22.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("$k", color = INK, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
                Text(" $text", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
    Text(
        "Р — больше 1 г вещества в 100 г воды, М — от 0,001 до 1 г, Н — меньше 0,001 г.",
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
    )
}
