package ru.prostayahimiya.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.prostayahimiya.app.R
import ru.prostayahimiya.app.data.ClassCode
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.ui.components.ErrorBox
import ru.prostayahimiya.app.ui.components.Load
import ru.prostayahimiya.app.ui.components.LoadingBox
import ru.prostayahimiya.app.ui.components.cardBorder
import ru.prostayahimiya.app.ui.components.cardColors
import ru.prostayahimiya.app.ui.components.rememberLoader

/**
 * Коды классов. Пока кодов нет, ученики регистрируются сами и пишут класс.
 * Если создать код, без кода зарегистрироваться нельзя, а класс подставится сам.
 */
@Composable
fun ClassCodesSection(repository: Repository, groups: List<String>, snackbar: SnackbarHostState) {
    val loader = rememberLoader(Unit) { repository.classCodes() }
    val scope = rememberCoroutineScope()
    var newGroup by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<ClassCode?>(null) }

    fun action(message: String? = null, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                message?.let { snackbar.showSnackbar(it) }
                loader.reload()
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "Ошибка")
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (val state = loader.state) {
            Load.Loading -> LoadingBox()
            is Load.Failed -> ErrorBox(state.message, loader::reload)
            is Load.Ok -> {
                val codes = state.value
                Text(
                    if (codes.any { it.active }) {
                        "Регистрация только по коду: ученик вводит код, и класс подставляется сам. " +
                            "Выключите все коды, чтобы снова разрешить регистрацию без кода."
                    } else {
                        "Сейчас ученики регистрируются сами и пишут свой класс. Если создать код класса, " +
                            "зарегистрироваться можно будет только по коду — так в класс не попадут чужие."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                codes.forEach { code ->
                    Card(colors = cardColors(), border = cardBorder()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${code.group} класс" + if (code.active) "" else " · выключен",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    code.code,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    letterSpacing = 2.sp,
                                    textDecoration = if (code.active) null else TextDecoration.LineThrough,
                                    color = if (code.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { action { repository.setClassCodeActive(code.id, !code.active) } }) {
                                Text(if (code.active) "Выключить" else "Включить")
                            }
                            IconButton(onClick = { deleting = code }) {
                                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Удалить код ${code.code}")
                            }
                        }
                    }
                }
            }
        }

        Card(colors = cardColors(), border = cardBorder()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Новый код", style = MaterialTheme.typography.labelLarge)
                if (groups.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        groups.forEach { g -> FilterChip(selected = newGroup == g, onClick = { newGroup = g }, label = { Text(g) }) }
                    }
                }
                OutlinedTextField(
                    value = newGroup,
                    onValueChange = { newGroup = it.uppercase().take(10) },
                    label = { Text("Класс") },
                    placeholder = { Text("8А") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        val g = newGroup.trim()
                        action("Код для $g создан") { repository.createClassCode(g) }
                    },
                    enabled = newGroup.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Создать код") }
            }
        }
    }

    deleting?.let { code ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить код?") },
            text = { Text("Код ${code.code} для ${code.group} перестанет работать. Уже зарегистрированные ученики останутся.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    action("Код удалён") { repository.deleteClassCode(code.id) }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
}
