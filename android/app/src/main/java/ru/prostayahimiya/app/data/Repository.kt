package ru.prostayahimiya.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import okhttp3.Request
import ru.prostayahimiya.app.BuildConfig
import java.io.ByteArrayOutputStream
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
    private val _content = MutableStateFlow(forUser(loadCache(), loadUser()))
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

    /** Имя учителя ученика (для личного кабинета). */
    suspend fun teacherName(id: String): String = registrationInfo().teachers.firstOrNull { it.id == id }?.name.orEmpty()

    /** Подтверждённые учителя для регистрации и нужен ли каждому код класса. Без связи — пустой список. */
    suspend fun registrationInfo(): RegistrationInfo =
        runCatching { api.call("GET", api.url("api/registration-info"), RegistrationInfo.serializer()) }.getOrDefault(RegistrationInfo())

    /**
     * Регистрация. Ученик указывает учителя (и код класса, если учитель его требует),
     * учитель — wantsTeacher = true и ждёт подтверждения администратора.
     */
    suspend fun register(
        name: String,
        group: String,
        username: String,
        password: String,
        passwordConfirm: String,
        classCode: String = "",
        teacher: String = "",
        wantsTeacher: Boolean = false,
    ) {
        api.send(
            "POST",
            api.url("api/collections/users/records"),
            buildJsonObject {
                put("name", name.trim())
                put("group", group.trim().uppercase())
                put("classCode", classCode.trim().uppercase())
                put("teacher", teacher)
                put("wantsTeacher", wantsTeacher)
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
        val notes = runCatching { api.listAll("notes", Note.serializer(), sort = "subject,grade") }.getOrDefault(emptyList())
        val isStudent = _user.value?.isTeacher == false
        val variants = if (isStudent) {
            runCatching { api.call("GET", api.url("api/variants"), VariantsResponse.serializer()).variants }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
        val examStarts = if (isStudent) {
            runCatching { api.listAll("exam_starts", ExamStart.serializer()) }.getOrDefault(emptyList())
                .filter { it.started.isNotBlank() }.associate { it.practice to it.started }
        } else {
            emptyMap()
        }
        // Задания начатых контрольных сервер отдаёт только через /api/exam/start — оставляем уже полученные.
        val kept = if (isStudent) _content.value.tasks.filter { it.practice in examStarts } else emptyList()
        val data = ContentData(practices, tasks + kept, notes, variants, examStarts)
        _content.value = forUser(data, _user.value)
        _contentError.value = null
        cacheFile.writeText(api.json.encodeToString(ContentData.serializer(), data))
        true
    }.getOrElse {
        _contentError.value = it.message
        false
    }

    // ---------- Контрольная ----------

    private val _examSessions = MutableStateFlow<Map<String, ExamSession>>(emptyMap())

    /** Контрольные, которые идут сейчас: id практики → время. */
    val examSessions: StateFlow<Map<String, ExamSession>> = _examSessions.asStateFlow()

    /**
     * Начать контрольную (begin = true) или продолжить начатую. Сервер запоминает время начала и только теперь
     * отдаёт задания. null — контрольная ещё не начата (без begin) или уже сдана.
     */
    suspend fun startExam(practice: Practice, begin: Boolean): ExamSession? = guard {
        val r = api.call(
            "POST",
            api.url("api/exam/start"),
            ExamStartResponse.serializer(),
            buildJsonObject {
                put("practice", practice.id)
                put("begin", begin)
            },
        )
        val current = _content.value
        val starts = if (r.started.isNotBlank()) current.examStarts + (practice.id to r.started) else current.examStarts - practice.id
        _content.value = current.copy(tasks = current.tasks.filter { it.practice != practice.id } + r.tasks, examStarts = starts)
        runCatching { cacheFile.writeText(api.json.encodeToString(ContentData.serializer(), _content.value)) }
        if (r.submitted) {
            refreshMySubmissions()
            return@guard null
        }
        val started = parseServerInstant(r.started) ?: return@guard null
        val serverNow = parseServerInstant(r.now)?.toEpochMilli() ?: System.currentTimeMillis()
        ExamSession(
            endsAt = if (r.timeLimit > 0) started.plusSeconds(r.timeLimit * 60L) else null,
            offsetMillis = serverNow - System.currentTimeMillis(),
        ).also { _examSessions.value = _examSessions.value + (practice.id to it) }
    }.getOrThrow()

    /** Контрольная сдана — таймер больше не нужен. */
    fun endExam(practiceId: String) {
        _examSessions.value = _examSessions.value - practiceId
    }

    /** Учитель разрешает ученику написать контрольную ещё раз: сданная работа удаляется. */
    suspend fun resetExam(practiceId: String, studentId: String) = guard {
        api.send(
            "POST",
            api.url("api/exam/reset"),
            buildJsonObject {
                put("practice", practiceId)
                put("student", studentId)
            },
        )
    }.getOrThrow()

    // ---------- Проверка ответов ----------

    /**
     * Проверить ответ на сервере: правильных ответов у ученика на телефоне нет.
     * Сервер же считает попытки, их потом видит учитель.
     */
    suspend fun check(task: Task, input: String): CheckResult = guard {
        val response = api.call(
            "POST",
            api.url("api/check"),
            CheckResponse.serializer(),
            buildJsonObject {
                put("items", buildJsonArray {
                    add(buildJsonObject {
                        put("task", task.id)
                        put("input", input)
                    })
                })
            },
        )
        response.results.firstOrNull() ?: CheckResult(task.id, "error", "Сервер не ответил на проверку")
    }.getOrThrow()

    // ---------- Картинки к заданиям ----------

    private val imageCache = LruCache<String, ImageBitmap>(24)

    /** Картинка задания (уменьшенная копия с сервера). null — картинки нет или нет связи. */
    suspend fun taskImage(task: Task): ImageBitmap? {
        if (task.image.isBlank()) return null
        val url = api.fileUrl("tasks", task.id, task.image, "800x0") ?: return null
        imageCache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                api.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) null else BitmapFactory.decodeStream(response.body.byteStream())?.asImageBitmap()
                }
            }.getOrNull()
        }?.also { imageCache.put(url, it) }
    }

    /** Загрузить картинку к заданию (uri — фото из галереи) или убрать её (uri = null). */
    suspend fun setTaskImage(taskId: String, uri: Uri?): Task = guard {
        val saved = if (uri == null) {
            api.call(
                "PATCH",
                api.url("api/collections/tasks/records/$taskId"),
                Task.serializer(),
                buildJsonObject { put("image", JsonNull) },
            )
        } else {
            val bytes = withContext(Dispatchers.IO) { prepareImage(uri) }
                ?: throw ApiException("Не получилось открыть картинку")
            api.upload("PATCH", api.url("api/collections/tasks/records/$taskId"), Task.serializer(), "image", "image.jpg", bytes, "image/jpeg")
        }
        _content.value = _content.value.copy(tasks = _content.value.tasks.filter { it.id != saved.id } + saved)
        saved
    }.getOrThrow()

    /** Большие фото с телефона уменьшаем до 1600 точек по длинной стороне и сжимаем в JPEG. */
    private fun prepareImage(uri: Uri): ByteArray? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = minOf(1f, MAX_IMAGE_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val width = (decoded.width * scale).toInt().coerceAtLeast(1)
        val height = (decoded.height * scale).toInt().coerceAtLeast(1)
        // Белый фон: у PNG с прозрачностью иначе получится чёрный.
        val result = createBitmap(width, height)
        Canvas(result).apply {
            drawColor(Color.WHITE)
            drawBitmap(decoded.scale(width, height), 0f, 0f, null)
        }
        return ByteArrayOutputStream().use { out ->
            result.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }

    // ---------- Работы учеников ----------

    /** Обновляет список сданных работ ученика. Без интернета оставляет прежний. */
    suspend fun refreshMySubmissions() {
        runCatching { mySubmissions() }.onSuccess { _mySubmissions.value = it }
    }

    suspend fun submit(practice: Practice, answers: List<AnswerItem>, solved: Int, total: Int, variant: Int = 0): Submission = guard {
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
                put("variant", variant)
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
            put("classes", buildJsonArray { practice.classes.forEach { add(it) } })
            put("deadline", practice.deadline)
            put("exam", practice.exam)
            put("timeLimit", if (practice.exam) practice.timeLimit.coerceIn(0, 600) else 0)
            // Новая практика принадлежит этому учителю.
            if (practice.id.isEmpty()) put("owner", _user.value?.id.orEmpty())
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
            // Правильный ответ скрыт от учеников, поэтому сервер принимает его в поле answerText.
            put("answerText", task.answer.trim())
            put("options", buildJsonArray { task.options.forEach { add(it.trim()) } })
            put("multiple", task.multiple)
            put("variant", task.variant)
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

    // ---------- Новая версия приложения ----------

    private val _update = MutableStateFlow<AppRelease?>(null)
    /** Вышедшая новая версия (если приложение поставлено не из RuStore — там обновления приходят сами). */
    val update: StateFlow<AppRelease?> = _update.asStateFlow()

    /** Адрес страницы приложения на сайте, где можно скачать новую версию. */
    val appPageUrl: String get() = BuildConfig.SERVER_URL.trimEnd('/') + "/app/"

    suspend fun checkForUpdate() {
        if (installedFromRuStore()) return
        runCatching {
            api.call(
                "GET",
                api.url("api/collections/app_releases/records", mapOf("sort" to "-versionCode", "perPage" to "1", "skipTotal" to "1")),
                Page.serializer(AppRelease.serializer()),
            ).items.firstOrNull()
        }.onSuccess { latest ->
            _update.value = latest?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
        }
    }

    private fun installedFromRuStore(): Boolean = runCatching {
        val pm = context.packageManager
        val installer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
        installer == "ru.vk.store"
    }.getOrDefault(false)

    // ---------- Заметки учителя к теории ----------

    /** Сохранить заметку (пустой текст — удалить её). */
    suspend fun saveNote(subject: Subject, grade: Int, text: String) = guard {
        val existing = _content.value.noteFor(subject, grade)
        val clean = text.trim()
        val others = _content.value.notes.filter { !(it.subject == subject.key && it.grade == grade) }
        val notes = when {
            clean.isEmpty() -> {
                if (existing != null) api.send("DELETE", api.url("api/collections/notes/records/${safeId(existing.id)}"))
                others
            }
            existing != null -> others + api.call(
                "PATCH",
                api.url("api/collections/notes/records/${safeId(existing.id)}"),
                Note.serializer(),
                buildJsonObject { put("text", clean) },
            )
            else -> others + api.call(
                "POST",
                api.url("api/collections/notes/records"),
                Note.serializer(),
                buildJsonObject {
                    put("subject", subject.key)
                    put("grade", grade)
                    put("text", clean)
                    put("owner", _user.value?.id.orEmpty())
                },
            )
        }
        _content.value = _content.value.copy(notes = notes)
    }.getOrThrow()

    // ---------- Коды классов и журнал ----------

    suspend fun classCodes(): List<ClassCode> = guard {
        api.listAll("class_codes", ClassCode.serializer(), sort = "group,created")
    }.getOrThrow()

    /** Новый код для класса: 6 букв и цифр, без похожих друг на друга (0 и O, 1 и I). */
    suspend fun createClassCode(group: String): ClassCode = guard {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = java.security.SecureRandom()
        val code = (1..6).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        api.call(
            "POST",
            api.url("api/collections/class_codes/records"),
            ClassCode.serializer(),
            buildJsonObject {
                put("group", group.trim().uppercase())
                put("code", code)
                put("active", true)
                put("owner", _user.value?.id.orEmpty())
            },
        )
    }.getOrThrow()

    suspend fun setClassCodeActive(id: String, active: Boolean): ClassCode = guard {
        api.call(
            "PATCH",
            api.url("api/collections/class_codes/records/${safeId(id)}"),
            ClassCode.serializer(),
            buildJsonObject { put("active", active) },
        )
    }.getOrThrow()

    suspend fun deleteClassCode(id: String) = guard {
        api.send("DELETE", api.url("api/collections/class_codes/records/${safeId(id)}"))
    }.getOrThrow()

    /** Всё для журнала: ученики и все сданные работы. */
    suspend fun journal(): Pair<List<User>, List<Submission>> = students() to allSubmissions(onlyUnchecked = false)

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
                    put("teacher", if (me.isTeacher) me.id else me.teacher)
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

    /** Ученик видит только практики для своего класса, учитель — все. */
    private fun forUser(data: ContentData, user: User?): ContentData {
        if (user == null || user.isTeacher) return data
        val practices = data.practices.filter { it.isFor(user.group) }
        val ids = practices.map { it.id }.toSet()
        return data.copy(practices = practices, tasks = data.tasks.filter { it.practice in ids })
    }

    private fun loadCache(): ContentData = runCatching {
        api.json.decodeFromString(ContentData.serializer(), cacheFile.readText())
    }.getOrDefault(ContentData())

    private fun normalizeUsername(value: String) = value.trim().lowercase()

    private companion object {
        const val MAX_IMAGE_SIDE = 1600
        const val KEY_TOKEN = "token"
        const val KEY_USER = "user"
    }
}
