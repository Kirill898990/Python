package ru.prostayahimiya.app.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import ru.prostayahimiya.app.data.ChatRoom
import ru.prostayahimiya.app.data.Message
import ru.prostayahimiya.app.data.Repository
import kotlin.coroutines.cancellation.CancellationException

/** Переписка в одной комнате чата. Новые сообщения подгружаются, пока экран открыт (см. ChatScreen). */
class ChatViewModel(private val repository: Repository, val room: ChatRoom) : ViewModel() {

    var messages by mutableStateOf<List<Message>>(emptyList())
        private set
    var hasMore by mutableStateOf(false)
        private set
    /** Ошибка первой загрузки. Если сообщения уже есть, ошибки обновления не показываем: попробуем снова. */
    var error by mutableStateOf<String?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set
    var sending by mutableStateOf(false)
        private set
    var draft by mutableStateOf("")

    private var limit by mutableIntStateOf(PAGE)

    /** Загружает последние сообщения. Вызывается при открытии экрана и потом каждые несколько секунд. */
    suspend fun refresh() {
        try {
            val (items, more) = repository.messages(room, limit)
            messages = items
            hasMore = more
            error = null
            loaded = true
            items.lastOrNull()?.let { repository.markSeen(room, it.created) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!loaded) error = e.message ?: "Ошибка"
        }
    }

    fun retry() {
        error = null
        viewModelScope.launch { refresh() }
    }

    fun loadOlder() {
        limit += PAGE
        viewModelScope.launch { refresh() }
    }

    fun send(onError: (String) -> Unit) {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        sending = true
        viewModelScope.launch {
            try {
                val saved = repository.sendMessage(room, text)
                if (messages.none { it.id == saved.id }) messages = messages + saved
                draft = ""
                repository.markSeen(room, saved.created)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.message ?: "Не получилось отправить")
            }
            sending = false
        }
    }

    fun delete(message: Message, onDone: (String) -> Unit) {
        viewModelScope.launch {
            try {
                repository.deleteMessage(message.id)
                messages = messages.filter { it.id != message.id }
                onDone("Сообщение удалено")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onDone(e.message ?: "Не получилось удалить")
            }
        }
    }

    companion object {
        const val PAGE = 50
        const val MAX_LENGTH = 1000

        fun factory(repository: Repository, room: ChatRoom) = viewModelFactory {
            initializer { ChatViewModel(repository, room) }
        }
    }
}
