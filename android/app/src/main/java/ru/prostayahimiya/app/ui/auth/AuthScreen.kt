package ru.prostayahimiya.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.prostayahimiya.app.data.ApiException
import ru.prostayahimiya.app.data.Content
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.data.TeacherInfo
import ru.prostayahimiya.app.data.isGroup
import ru.prostayahimiya.app.data.normalizeGroup
import ru.prostayahimiya.app.ui.components.EmojiBadge
import ru.prostayahimiya.app.ui.components.openLink

/** Вход и регистрация. После успешного входа навигация сама откроет главный экран. */
@Composable
fun AuthScreen(repository: Repository) {
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("") }
    var classCode by rememberSaveable { mutableStateOf("") }
    var asTeacher by rememberSaveable { mutableStateOf(false) }
    var teacherId by rememberSaveable { mutableStateOf("") }
    // Учителя появляются в списке, когда администратор их подтвердит. Если у учителя есть коды классов,
    // вместо «Класс» ученик вводит код, а класс сервер возьмёт из кода.
    var teachers by remember { mutableStateOf<List<TeacherInfo>?>(null) }
    LaunchedEffect(registerMode) {
        if (registerMode) {
            teachers = repository.registrationInfo().teachers
            if (teacherId.isEmpty()) teachers?.singleOrNull()?.let { teacherId = it.id }
        }
    }
    val student = registerMode && !asTeacher
    val selectedTeacher = teachers?.firstOrNull { it.id == teacherId }
    val codeRequired = selectedTeacher?.codeRequired == true
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var password2 by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var fieldErrors by remember { mutableStateOf(mapOf<String, String>()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun submit() {
        if (loading) return
        error = null
        fieldErrors = buildMap {
            if (registerMode && name.trim().length < 2) put("name", "Впиши имя и фамилию")
            if (student && teacherId.isEmpty()) put("teacher", "Выбери своего учителя")
            if (student && teacherId.isNotEmpty() && !codeRequired && group.isBlank()) put("group", "Впиши класс, например 8Б")
            else if (student && teacherId.isNotEmpty() && !codeRequired && !isGroup(normalizeGroup(group))) {
                put("group", "Без пробелов, буква на русском: напиши класс, например 8Б")
            }
            if (student && codeRequired && classCode.isBlank()) put("classCode", "Впиши код класса, который дал учитель")
            if (username.trim().length < 3) put("username", "Логин: не меньше 3 символов")
            if (password.length < 6) put("password", "Пароль: не меньше 6 символов")
            if (registerMode && password2 != password) put("passwordConfirm", "Пароли не совпадают")
        }
        if (fieldErrors.isNotEmpty()) return
        loading = true
        scope.launch {
            try {
                if (registerMode) {
                    if (asTeacher) {
                        repository.register(name, "", username, password, password2, wantsTeacher = true)
                    } else {
                        repository.register(
                            name, if (codeRequired) "" else group, username, password, password2,
                            classCode = if (codeRequired) classCode else "",
                            teacher = teacherId,
                        )
                    }
                } else {
                    repository.login(username, password)
                }
            } catch (e: ApiException) {
                error = e.message
                fieldErrors = e.fieldErrors
            } finally {
                loading = false
            }
        }
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            EmojiBadge("🔬", MaterialTheme.colorScheme.primary, size = 72.dp)
            Text(
                "Химия и биология",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                Content.AUTHOR,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !registerMode,
                        onClick = { registerMode = false; error = null; fieldErrors = emptyMap() },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Вход") }
                    SegmentedButton(
                        selected = registerMode,
                        onClick = { registerMode = true; error = null; fieldErrors = emptyMap() },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Регистрация") }
                }

                if (registerMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !asTeacher, onClick = { asTeacher = false; fieldErrors = emptyMap() }, label = { Text("🎒 Я ученик") })
                        FilterChip(selected = asTeacher, onClick = { asTeacher = true; fieldErrors = emptyMap() }, label = { Text("👩‍🏫 Я учитель") })
                    }
                    Field(
                        value = name, onChange = { name = it }, label = "Имя и фамилия",
                        placeholder = if (asTeacher) "Ирина Петровна Смирнова" else "Петя Иванов",
                        error = fieldErrors["name"], capitalization = KeyboardCapitalization.Words,
                    )
                }
                if (student) {
                    TeacherPicker(
                        teachers = teachers,
                        selected = selectedTeacher,
                        error = fieldErrors["teacher"],
                        onSelect = { teacherId = it.id; fieldErrors = fieldErrors - "teacher" },
                    )
                    if (codeRequired) {
                        Field(
                            value = classCode, onChange = { classCode = it.uppercase().filter { c -> !c.isWhitespace() } },
                            label = "Код класса", placeholder = "K7Q2XM",
                            error = fieldErrors["classCode"],
                            supporting = "Код даёт учитель. По нему ты попадёшь в свой класс",
                            capitalization = KeyboardCapitalization.Characters,
                        )
                    } else if (teacherId.isNotEmpty()) {
                        Field(
                            // Пробелы убираем сразу, латинские буквы-двойники меняем на русские: «8 b» → «8В».
                            value = group, onChange = { group = normalizeGroup(it) }, label = "Класс", placeholder = "8Б",
                            supporting = "Без пробелов, буква на русском: например 8Б",
                            error = fieldErrors["group"], capitalization = KeyboardCapitalization.Characters,
                        )
                    }
                }
                Field(
                    value = username,
                    onChange = { username = it.trim().lowercase() },
                    label = "Логин",
                    placeholder = "petya",
                    supporting = if (registerMode) "Латинские буквы и цифры, его ты будешь вводить при входе" else null,
                    error = fieldErrors["username"] ?: fieldErrors["identity"],
                    keyboardType = KeyboardType.Ascii,
                )
                Field(
                    value = password, onChange = { password = it }, label = "Пароль",
                    supporting = if (registerMode) "Не меньше 6 символов" else null,
                    error = fieldErrors["password"], password = true,
                    imeAction = if (registerMode) ImeAction.Next else ImeAction.Done, onDone = ::submit,
                )
                if (registerMode) {
                    Field(
                        value = password2, onChange = { password2 = it }, label = "Повтори пароль",
                        error = fieldErrors["passwordConfirm"], password = true,
                        imeAction = ImeAction.Done, onDone = ::submit,
                    )
                }

                if (error != null && fieldErrors.isEmpty()) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Button(onClick = ::submit, enabled = !loading, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (loading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text(if (!registerMode) "Войти" else if (asTeacher) "Отправить заявку учителя" else "Зарегистрироваться")
                    }
                }
                Text(
                    when {
                        !registerMode -> "Забыл пароль? Попроси учителя сбросить его."
                        asTeacher -> "После регистрации администратор подтвердит, что вы учитель. Тогда вас можно будет выбрать " +
                            "в списке учителей, а у вас появится панель учителя."
                        else -> "Нет своего учителя в списке? Значит, он ещё не зарегистрировался — попроси его."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { context.openLink(Content.PRIVACY_URL) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Политика конфиденциальности", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    supporting: String? = null,
    error: String? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        isError = error != null,
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
}


/** Выбор учителя из списка подтверждённых учителей. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeacherPicker(teachers: List<TeacherInfo>?, selected: TeacherInfo?, error: String?, onSelect: (TeacherInfo) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it && !teachers.isNullOrEmpty() }) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Учитель") },
            placeholder = { Text(if (teachers == null) "Загружаем список…" else if (teachers.isEmpty()) "Учителей пока нет" else "Выбери учителя") },
            supportingText = { Text(error ?: "Учитель, у которого ты учишься") },
            isError = error != null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            teachers.orEmpty().forEach { t ->
                DropdownMenuItem(text = { Text(t.name) }, onClick = { onSelect(t); expanded = false })
            }
        }
    }
}

/**
 * Учитель зарегистрировался, но администратор ещё не подтвердил его.
 * Подтверждение — галочка isTeacher в веб-панели сервера; после неё «Проверить ещё раз» откроет панель учителя.
 */
@Composable
fun PendingTeacherScreen(repository: Repository) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var stillWaiting by remember { mutableStateOf(false) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
        ) {
            EmojiBadge("⏳", MaterialTheme.colorScheme.primary, size = 72.dp)
            Text("Заявка отправлена", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Администратор проверит, что вы учитель, и подтвердит заявку. После этого ученики смогут выбрать вас " +
                    "при регистрации, а у вас появится панель учителя: свои практики, классы, журнал и чат.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 480.dp),
            )
            if (stillWaiting) {
                Text(
                    "Пока не подтверждено. Попробуйте позже.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (checking) return@Button
                    checking = true
                    scope.launch {
                        repository.refreshSession()
                        stillWaiting = repository.user.value?.isTeacher != true
                        checking = false
                    }
                },
                enabled = !checking,
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().height(52.dp),
            ) {
                if (checking) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                } else {
                    Text("Проверить ещё раз")
                }
            }
            TextButton(onClick = repository::logout) { Text("Выйти") }
        }
    }
}
