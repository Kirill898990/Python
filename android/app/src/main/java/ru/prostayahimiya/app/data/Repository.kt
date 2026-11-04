package ru.prostayahimiya.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import ru.prostayahimiya.app.BuildConfig
import java.io.File
import java.time.Instant

/**
 * Единая точка доступа к данным: вход и регистрация, задания с сервера (с копией на телефоне),
 * сдача работ, оценки и всё, что нужно панели учителя.
 */
class Repository(private val context: Context) {

    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private var token: String? = prefs.getString(KEY_TOKEN, null)
    private val api = PocketBase(BuildConfig.SERVER_URL) { token }

    private val _user = MutableStateFlow(loadUser())
    /** Текущий пользователь или null, если никто не вошёл. */
    val user: StateFlow<User?> = _user.asStateFlow()

    private val cacheFile = File(context.filesDir, "content.json")
    private val _content = MutableStateFlow(loadCache())
    /** Практики и задания. Сначала берутся с телефона, потом обновляются с сервера. */
    val content: StateFlow<ContentData> = _content.asStateFlow()

    private val _contentError = MutableStateFlow<String?>(null)
    /** Ошибка последней загрузки заданий (например, нет интернета). */
    val contentError: StateFlow<String?> = _contentError.asStateFlow()

    private val _mySubmissions = MutableStateFlow<List<Submission>>(emptyList())
    /** Сданные работы текущего ученика (новые сверху). По ним считается прогресс на всех устройствах. */
    val mySubmissionsState: StateFlow<List<Submission>> = _mySubmissions.asStateFlow()

    private var progressStore: ProgressStore? = null

    /** Прогресс и черновики текущего ученика. */
    val progress: ProgressStore
        get() {
            val id = _user.value?.id ?: "guest"
            return progressStore?.takeIf { progressUserId == id }
                ?: ProgressStore(context, id).also {
                    progressStore = it
                    progressUserId = id
                }
        }
    private var progressUserId: String? = null

    // ---------- Вход и регистрация ----------

    suspend fun login(username: String, password: String) {
        val response = api.call(
            "POST",
            api.url("api/collections/users/auth-with-password"),
            AuthResponse.serializer(),
            buildJsonObject {
                put("identity", normalizeUsername(username))
                put("password", password)
            },
        )
        saveSession(response)
        refreshContent()
    }

    suspend fun register(name: String, group: String, username: String, password: String, passwordConfirm: String) {
        api.send(
            "POST",
            api.url("api/collections/users/records"),
            buildJsonObject {
                put("name", name.trim())
                put("group", group.trim().uppercase())
                put("username", normalizeUsername(username))
                put("password", password)
                put("passwordConfirm", passwordConfirm)
            },
        )
        login(username, password)
    }

    /** При запуске продлевает вход и обновляет данные профиля (например, если учителю дали права). */
    suspend fun refreshSession() {
        if (token == null) return
        try {
            val response = api.call("POST", api.url("api/collections/users/auth-refresh"), AuthResponse.serializer())
            saveSession(response)
        } catch (e: ApiException) {
            if (e.status == 401 || e.status == 404) logout()
            // Без интернета просто продолжаем работать с сохранённым входом.
        }
    }

    fun logout() {
        token = null
        prefs.edit { clear() }
        cacheFile.delete()
        _content.value = ContentData()
        _mySubmissions.value = emptyList()
        _unreadChats.value = 0
        _user.value = null
    }

    // ---------- Задания ----------

    suspend fun refreshContent(): Boolean = guard {
        val practices = api.listAll("practices", Practice.serializer(), sort = "grade,position,title")
        val tasks = api.listAll("tasks", Task.serializer(), sort = "position")
        val data = ContentData(practices, tasks)
        _content.value = data
        _contentError.value = null
        cacheFile.writeText(api.json.encodeToString(ContentData.serializer(), data))
        true
    }.getOrElse {
        _contentError.value = it.message
        false
    }

    // ---------- Работы учеников ----------

    /** Обновляет список сданных работ ученика. Без интернета оставляет прежний. */
    suspend fun refreshMySubmissions() {
        runCatching { mySubmissions() }.onSuccess { _mySubmissions.value = it }
    }

    suspend fun submit(practice: Practice, answers: List<AnswerItem>, solved: Int, total: Int): Submission = guard {
        val student = _user.value ?: throw ApiException("Нужно войти в аккаунт", 401)
        val saved = api.call(
            "POST",
            api.url("api/collections/submissions/records"),
            Submission.serializer(),
            buildJsonObject {
                put("student", student.id)
                put("practice", practice.id)
                put("practiceTitle", practice.fullTitle)
                put("answers", api.json.encodeToJsonElement(ListSerializer(AnswerItem.serializer()), answers))
                put("solved", solved)
                put("total", total)
            },
        )
        _mySubmissions.value = listOf(saved) + _mySubmissions.value
        saved
    }.getOrThrow()

    suspend fun mySubmissions(practiceId: String? = null): List<Submission> = guard {
        val me = _user.value ?: return@guard emptyList()
        val filter = buildString {
            append("student = \"${me.id}\"")
            if (practiceId != null) append(" && practice = \"$practiceId\"")
        }
        api.listAll("submissions", Submission.serializer(), filter = filter, sort = "-created")
    }.getOrThrow()

    suspend fun submission(id: String): Submission = guard {
        api.call(
            "GET",
            api.url("api/collections/submissions/records/$id", mapOf("expand" to "student")),
            Submission.serializer(),
        )
    }.getOrThrow()

    // ---------- Панель учителя ----------

    suspend fun allSubmissions(onlyUnchecked: Boolean): List<Submission> = guard {
        api.listAll(
            "submissions",
            Submission.serializer(),
            filter = if (onlyUnchecked) "grade = 0" else null,
            sort = "-created",
            expand = "student",
        )
    }.getOrThrow()

    suspend fun grade(submissionId: String, grade: Int, comment: String): Submission = guard {
        api.call(
            "PATCH",
            api.url("api/collections/submissions/records/$submissionId", mapOf("expand" to "student")),
            Submission.serializer(),
            buildJsonObject {
                put("grade", grade)
                put("comment", comment.trim())
                put("gradedAt", Instant.now().toString())
            },
        )
    }.getOrThrow()

    suspend fun savePractice(practice: Practice): Practice = guard {
        val body = buildJsonObject {
            put("title", practice.title.trim())
            put("subject", practice.subject)
            put("grade", practice.grade)
            put("position", practice.position)
            put("published", practice.published)
        }
        val saved = save("practices", practice.id, body, Practice.serializer())
        _content.value = _content.value.copy(practices = _content.value.practices.filter { it.id != saved.id } + saved)
        saved
    }.getOrThrow()

    suspend fun deletePractice(id: String) = guard {
        api.send("DELETE", api.url("api/collections/practices/records/$id"))
        _content.value = _content.value.copy(
            practices = _content.value.practices.filter { it.id != id },
            tasks = _content.value.tasks.filter { it.practice != id },
        )
    }.getOrThrow()

    suspend fun saveTask(task: Task): Task = guard {
        val body = buildJsonObject {
            put("practice", task.practice)
            put("type", task.type)
            put("position", task.position)
            put("text", task.text.trim())
            put("answer", task.answer.trim())
            put("hint", task.hint.trim())
        }
        val saved = save("tasks", task.id, body, Task.serializer())
        _content.value = _content.value.copy(tasks = _content.value.tasks.filter { it.id != saved.id } + saved)
        saved
    }.getOrThrow()

    suspend fun deleteTask(id: String) = guard {
        api.send("DELETE", api.url("api/collections/tasks/records/$id"))
        _content.value = _content.value.copy(tasks = _content.value.tasks.filter { it.id != id })
    }.getOrThrow()

    suspend fun students(): List<User> = guard {
        api.listAll("users", User.serializer(), filter = "isTeacher = false", sort = "group,name")
    }.getOrThrow()

    suspend fun resetPassword(userId: String, newPassword: String) = guard {
        api.send(
            "PATCH",
            api.url("api/collections/users/records/$userId"),
            buildJsonObject {
                put("password", newPassword)
                put("passwordConfirm", newPassword)
            },
        )
    }.getOrThrow()

    // ---------- Чат ----------

    private val _unreadChats = MutableStateFlow(0)
    /** В скольких чатах есть новые сообщения (значок на главной). */
    val unreadChats: StateFlow<Int> = _unreadChats.asStateFlow()

    private fun seenPrefs() = context.getSharedPreferences("chat_seen_${_user.value?.id ?: "guest"}", Context.MODE_PRIVATE)

    /** Запоминает, что пользователь видел сообщения комнаты до [created] включительно. */
    fun markSeen(room: ChatRoom, created: String) {
        val prefs = seenPrefs()
        if (created > prefs.getString(room.id, "").orEmpty()) prefs.edit { putString(room.id, created) }
    }

    private fun roomFilter(room: ChatRoom): String =
        if (room.isClass) "kind = \"class\" && group = ${quote(room.key)}"
        else "kind = \"private\" && student = \"${safeId(room.key)}\""

    /** Последние [limit] сообщений комнаты (старые сверху) и есть ли более ранние. */
    suspend fun messages(room: ChatRoom, limit: Int): Pair<List<Message>, Boolean> = guard {
        val page = api.call(
            "GET",
            api.url(
                "api/collections/messages/records",
                mapOf("filter" to roomFilter(room), "sort" to "-created,-id", "perPage" to limit.toString(), "page" to "1"),
            ),
            Page.serializer(Message.serializer()),
        )
        page.items.reversed() to (page.totalItems > page.items.size)
    }.getOrThrow()

    suspend fun sendMessage(room: ChatRoom, text: String): Message {
        suspend fun post(): Message {
            val me = _user.value ?: throw ApiException("Нужно войти в аккаунт", 401)
            return api.call(
                "POST",
                api.url("api/collections/messages/records"),
                Message.serializer(),
                buildJsonObject {
                    put("author", me.id)
                    put("authorName", me.name)
                    put("fromTeacher", me.isTeacher)
                    put("kind", room.kind)
                    put("group", if (room.isClass) room.key else "")
                    put("student", if (room.isClass) "" else room.key)
                    put("text", text.trim())
                },
            )
        }
        return guard {
            try {
                post()
            } catch (e: ApiException) {
                // Возможно, учитель поменял имя или класс: обновляем профиль и пробуем ещё раз.
                if (e.status != 400 && e.status != 403) throw e
                refreshSession()
                post()
            }
        }.getOrThrow()
    }

    suspend fun deleteMessage(id: String) = guard {
        api.send("DELETE", api.url("api/collections/messages/records/${safeId(id)}"))
    }.getOrThrow()

    /**
     * Список чатов с последним сообщением. Ученик: чат своего класса и переписка с учителем.
     * Учитель: чаты всех классов и все личные переписки.
     */
    suspend fun chatThreads(): List<ChatThread> = guard {
        val me = _user.value ?: return@guard emptyList()
        val seen = seenPrefs()
        suspend fun last(room: ChatRoom): Message? = api.call(
            "GET",
            api.url(
                "api/collections/messages/records",
                mapOf("filter" to roomFilter(room), "sort" to "-created,-id", "perPage" to "1", "skipTotal" to "1"),
            ),
            Page.serializer(Message.serializer()),
        ).items.firstOrNull()
        fun ChatThread.withUnread() = copy(
            unread = last != null && last.author != me.id && last.created > seen.getString(room.id, "").orEmpty(),
        )

        if (!me.isTeacher) {
            buildList {
                if (me.group.isNotBlank()) {
                    val room = ChatRoom.ofClass(me.group)
                    add(ChatThread(room, "Чат класса ${me.group}", last = last(room)))
                }
                val room = ChatRoom.private(me.id)
                add(ChatThread(room, "Учитель", "Личные сообщения", last = last(room)))
            }.map { it.withUnread() }
        } else {
            val students = students()
            val groups = students.map { it.group }.filter { it.isNotBlank() }.distinct()
                .sortedWith(compareBy({ it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }, { it }))
            val classes = groups.map { group ->
                val room = ChatRoom.ofClass(group)
                ChatThread(room, "$group класс", last = last(room))
            }
            val privateMessages = api.call(
                "GET",
                api.url(
                    "api/collections/messages/records",
                    mapOf("filter" to "kind = \"private\"", "sort" to "-created,-id", "perPage" to "500", "skipTotal" to "1"),
                ),
                Page.serializer(Message.serializer()),
            ).items
            val byId = students.associateBy { it.id }
            val threads = privateMessages.distinctBy { it.student }.map { m ->
                val student = byId[m.student]
                ChatThread(
                    ChatRoom.private(m.student),
                    student?.name ?: "Ученик",
                    student?.group?.takeIf { it.isNotBlank() }?.let { "$it класс" }.orEmpty(),
                    last = m,
                )
            }
            (classes + threads).map { it.withUnread() }
        }
    }.getOrThrow()

    /** Пересчитывает значок «новые сообщения». Без интернета оставляет прежнее число. */
    suspend fun refreshUnreadChats() {
        runCatching { chatThreads() }.onSuccess { list -> _unreadChats.value = list.count { it.unread } }
    }

    // ---------- Внутреннее ----------

    /** Строка в кавычках для фильтра PocketBase (как pb.filter() в официальной библиотеке). */
    private fun quote(value: String) = "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"

    /** id записей PocketBase — только латинские буквы и цифры. */
    private fun safeId(id: String) = id.filter { it.isLetterOrDigit() && it.code < 128 }

    private suspend fun <T> save(collection: String, id: String, body: JsonObject, serializer: kotlinx.serialization.KSerializer<T>): T =
        if (id.isEmpty()) {
            api.call("POST", api.url("api/collections/$collection/records"), serializer, body)
        } else {
            api.call("PATCH", api.url("api/collections/$collection/records/$id"), serializer, body)
        }

    /** Если сервер сказал, что вход устарел, выходим из аккаунта. */
    private suspend fun <T> guard(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: ApiException) {
        if (e.status == 401 && token != null) logout()
        Result.failure(e)
    }

    private fun saveSession(response: AuthResponse) {
        token = response.token
        prefs.edit {
            putString(KEY_TOKEN, response.token)
            putString(KEY_USER, api.json.encodeToString(User.serializer(), response.record))
        }
        _user.value = response.record
    }

    private fun loadUser(): User? = prefs.getString(KEY_USER, null)?.let {
        runCatching { api.json.decodeFromString(User.serializer(), it) }.getOrNull()
    }

    private fun loadCache(): ContentData = runCatching {
        api.json.decodeFromString(ContentData.serializer(), cacheFile.readText())
    }.getOrDefault(ContentData())

    private fun normalizeUsername(value: String) = value.trim().lowercase()

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_USER = "user"
    }
}
