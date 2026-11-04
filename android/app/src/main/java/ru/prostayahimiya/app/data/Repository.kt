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

    // ---------- Внутреннее ----------

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
