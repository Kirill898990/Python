package ru.prostayahimiya.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.prostayahimiya.app.R
import ru.prostayahimiya.app.data.ChatRoom
import ru.prostayahimiya.app.data.ChatThread
import ru.prostayahimiya.app.data.Message
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.data.parseServerInstant
import ru.prostayahimiya.app.ui.components.BackTopBar
import ru.prostayahimiya.app.ui.components.EmojiBadge
import ru.prostayahimiya.app.ui.components.EmptyBox
import ru.prostayahimiya.app.ui.components.ErrorBox
import ru.prostayahimiya.app.ui.components.Load
import ru.prostayahimiya.app.ui.components.LoadingBox
import ru.prostayahimiya.app.ui.components.SectionLabel
import ru.prostayahimiya.app.ui.components.cardBorder
import ru.prostayahimiya.app.ui.components.cardColors
import ru.prostayahimiya.app.ui.components.rememberLoader
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RU = Locale.forLanguageTag("ru")
private val TIME = DateTimeFormatter.ofPattern("HH:mm", RU)
private val DAY = DateTimeFormatter.ofPattern("d MMMM", RU)
private val SHORT_DAY = DateTimeFormatter.ofPattern("d MMM", RU)

private fun localDate(created: String): LocalDate? =
    parseServerInstant(created)?.atZone(ZoneId.systemDefault())?.toLocalDate()

private fun timeOf(created: String): String =
    parseServerInstant(created)?.atZone(ZoneId.systemDefault())?.format(TIME).orEmpty()

private fun dayOf(created: String): String {
    val date = localDate(created) ?: return ""
    val today = LocalDate.now()
    return when (date) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> date.format(DAY)
    }
}

/** «14:05» сегодня, «вчера», «3 нояб.» раньше. */
private fun shortWhen(created: String): String {
    val date = localDate(created) ?: return ""
    val today = LocalDate.now()
    return when (date) {
        today -> timeOf(created)
        today.minusDays(1) -> "вчера"
        else -> date.format(SHORT_DAY)
    }
}

// ======================= Список чатов =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(repository: Repository, onBack: () -> Unit, onOpenRoom: (ChatRoom, String) -> Unit) {
    val user by repository.user.collectAsState()
    val isTeacher = user?.isTeacher == true
    val loader = rememberLoader(Unit) { repository.chatThreads() }
    // Вернулись из переписки — обновляем список, чтобы пропали отметки о новых сообщениях.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        var first = true
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!first) loader.reload()
            first = false
        }
    }

    Scaffold(
        topBar = { BackTopBar("Чат", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = loader.refreshing && loader.state is Load.Ok,
            onRefresh = loader::reload,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val state = loader.state) {
                    Load.Loading -> item { LoadingBox() }
                    is Load.Failed -> item { ErrorBox(state.message, loader::reload) }
                    is Load.Ok -> {
                        val threads = state.value
                        if (!isTeacher) {
                            item {
                                Text(
                                    "Пиши вежливо: сообщения видит учитель. Лично можно написать только учителю.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            items(threads, key = { it.room.id }) { ThreadCard(it, user?.id.orEmpty(), isTeacher, onOpenRoom) }
                        } else {
                            val classes = threads.filter { it.room.isClass }
                            val private = threads.filter { !it.room.isClass }
                            item { SectionLabel("Чаты классов") }
                            if (classes.isEmpty()) {
                                item { EmptyBox("👥", "Классы появятся, когда ученики зарегистрируются") }
                            }
                            items(classes, key = { it.room.id }) { ThreadCard(it, user?.id.orEmpty(), true, onOpenRoom) }
                            item { SectionLabel("Личные сообщения") }
                            if (private.isEmpty()) {
                                item {
                                    Text(
                                        "Ученики ещё не писали вам лично. Написать ученику можно из вкладки «Ученики» в панели учителя.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            items(private, key = { it.room.id }) { ThreadCard(it, user?.id.orEmpty(), true, onOpenRoom) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadCard(thread: ChatThread, myId: String, isTeacher: Boolean, onOpenRoom: (ChatRoom, String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val last = thread.last
    val preview = when {
        last == null -> "Сообщений пока нет"
        last.author == myId -> "Вы: ${last.text}"
        thread.room.isClass -> "${last.authorName}: ${last.text}"
        else -> last.text
    }
    val fullTitle = if (isTeacher && !thread.room.isClass && thread.subtitle.isNotBlank()) {
        "${thread.title}, ${thread.subtitle.removeSuffix(" класс")}"
    } else {
        thread.title
    }
    Card(onClick = { onOpenRoom(thread.room, fullTitle) }, colors = cardColors(), border = cardBorder()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            when {
                thread.room.isClass -> EmojiBadge("👥", scheme.primaryContainer)
                isTeacher -> Surface(shape = CircleShape, color = scheme.secondaryContainer, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            thread.title.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSecondaryContainer,
                        )
                    }
                }
                else -> EmojiBadge("👩‍🏫", scheme.secondaryContainer)
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        thread.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (thread.unread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (last != null) {
                        Text(shortWhen(last.created), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                }
                if (thread.subtitle.isNotBlank()) {
                    Text(thread.subtitle, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (thread.unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (thread.unread) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (thread.unread) {
                Surface(shape = CircleShape, color = scheme.primary, modifier = Modifier.size(10.dp)) {}
            }
        }
    }
}

// ======================= Переписка =======================

private sealed interface ChatRow {
    data class Day(val label: String) : ChatRow
    data class Item(val message: Message) : ChatRow
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(room: ChatRoom, title: String, repository: Repository, onBack: () -> Unit) {
    val user by repository.user.collectAsState()
    val me = user
    val isTeacher = me?.isTeacher == true
    val vm: ChatViewModel = viewModel(key = room.id, factory = ChatViewModel.factory(repository, room))
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<Message?>(null) }

    // Учитель открыл переписку по ссылке без имени — находим имя ученика.
    var header by remember(title) { mutableStateOf(title) }
    LaunchedEffect(room, title) {
        if (header.isBlank()) {
            header = when {
                room.isClass -> if (isTeacher) "${room.key} класс" else "Чат класса ${room.key}"
                !isTeacher -> "Учитель"
                else -> runCatching { repository.students().firstOrNull { it.id == room.key } }.getOrNull()
                    ?.let { if (it.group.isNotBlank()) "${it.name}, ${it.group}" else it.name } ?: "Ученик"
            }
        }
    }

    // Пока экран открыт, раз в несколько секунд проверяем новые сообщения.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(vm) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                vm.refresh()
                delay(POLL_MS)
            }
        }
    }

    val rows = remember(vm.messages) {
        buildList {
            var day = ""
            vm.messages.forEach { m ->
                val d = dayOf(m.created)
                if (d != day) {
                    day = d
                    add(ChatRow.Day(d))
                }
                add(ChatRow.Item(m))
            }
        }.asReversed() // список перевёрнут: новые сообщения внизу, у поля ввода
    }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.messages.lastOrNull()?.id) {
        if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }

    Scaffold(
        topBar = { BackTopBar(header, onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    vm.error != null -> ErrorBox(vm.error.orEmpty(), vm::retry)
                    !vm.loaded -> LoadingBox()
                    vm.messages.isEmpty() -> EmptyBox(
                        "💬",
                        when {
                            room.isClass -> "Здесь пока пусто. Напиши первое сообщение классу!"
                            isTeacher -> "Напишите ученику первым"
                            else -> "Задай учителю вопрос — его увидит только учитель"
                        },
                        Modifier.align(Alignment.Center),
                    )
                    else -> LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(rows, key = { row -> if (row is ChatRow.Item) row.message.id else "day:${(row as ChatRow.Day).label}" }) { row ->
                            when (row) {
                                is ChatRow.Day -> DayLabel(row.label)
                                is ChatRow.Item -> Bubble(
                                    message = row.message,
                                    mine = row.message.author == me?.id,
                                    showName = row.message.author != me?.id && (room.isClass || !isTeacher),
                                    onDelete = if (isTeacher) ({ toDelete = row.message }) else null,
                                )
                            }
                        }
                        if (vm.hasMore) {
                            item(key = "more") {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    TextButton(onClick = vm::loadOlder) { Text("Показать ранние сообщения") }
                                }
                            }
                        }
                    }
                }
            }
            Composer(
                value = vm.draft,
                onValueChange = { vm.draft = it.take(ChatViewModel.MAX_LENGTH) },
                sending = vm.sending,
                onSend = { vm.send { message -> scope.launch { snackbar.showSnackbar(message) } } },
            )
        }
    }

    toDelete?.let { message ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить сообщение?") },
            text = { Text("«${message.text.take(80)}» — ${message.authorName}") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    vm.delete(message) { text -> scope.launch { snackbar.showSnackbar(text) } }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

private const val POLL_MS = 4_000L

@Composable
private fun DayLabel(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun Bubble(message: Message, mine: Boolean, showName: Boolean, onDelete: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (mine && onDelete != null) DeleteButton(onDelete)
        Card(
            shape = if (mine) RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp),
            colors = if (mine) CardDefaults.cardColors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer) else cardColors(),
            border = when {
                mine -> null
                message.fromTeacher -> BorderStroke(1.dp, scheme.primary.copy(alpha = 0.55f))
                else -> cardBorder()
            },
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)) {
                if (showName) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(message.authorName, style = MaterialTheme.typography.labelLarge, color = scheme.primary, fontWeight = FontWeight.SemiBold)
                        if (message.fromTeacher) {
                            Spacer(Modifier.size(6.dp))
                            Surface(shape = RoundedCornerShape(50), color = scheme.primaryContainer) {
                                Text(
                                    "Учитель",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
                Text(message.text, style = MaterialTheme.typography.bodyLarge)
                Text(
                    timeOf(message.created),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (mine) scheme.onPrimaryContainer.copy(alpha = 0.65f) else scheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
        if (!mine && onDelete != null) DeleteButton(onDelete)
    }
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            painterResource(R.drawable.ic_delete),
            contentDescription = "Удалить сообщение",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun Composer(value: String, onValueChange: (String) -> Unit, sending: Boolean, onSend: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Сообщение") },
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && !sending,
                modifier = Modifier.size(56.dp),
            ) {
                if (sending) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Icon(painterResource(R.drawable.ic_send), contentDescription = "Отправить")
                }
            }
        }
    }
}
