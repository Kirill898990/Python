package ru.prostayahimiya.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.prostayahimiya.app.ui.components.openLink
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.TextButton
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
import ru.prostayahimiya.app.ui.components.EmojiBadge

/** Вход и регистрация. После успешного входа навигация сама откроет главный экран. */
@Composable
fun AuthScreen(repository: Repository) {
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("") }
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
            if (registerMode && group.isBlank()) put("group", "Впиши класс, например 8Б")
            if (username.trim().length < 3) put("username", "Логин: не меньше 3 символов")
            if (password.length < 6) put("password", "Пароль: не меньше 6 символов")
            if (registerMode && password2 != password) put("passwordConfirm", "Пароли не совпадают")
        }
        if (fieldErrors.isNotEmpty()) return
        loading = true
        scope.launch {
            try {
                if (registerMode) {
                    repository.register(name, group, username, password, password2)
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
                    Field(
                        value = name, onChange = { name = it }, label = "Имя и фамилия", placeholder = "Петя Иванов",
                        error = fieldErrors["name"], capitalization = KeyboardCapitalization.Words,
                    )
                    Field(
                        value = group, onChange = { group = it.uppercase() }, label = "Класс", placeholder = "8Б",
                        error = fieldErrors["group"], capitalization = KeyboardCapitalization.Characters,
                    )
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
                        Text(if (registerMode) "Зарегистрироваться" else "Войти")
                    }
                }
                Text(
                    if (registerMode) {
                        "Учителю нужно зарегистрироваться так же, а потом администратор сервера даст ему права учителя."
                    } else {
                        "Забыл пароль? Попроси учителя сбросить его."
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
