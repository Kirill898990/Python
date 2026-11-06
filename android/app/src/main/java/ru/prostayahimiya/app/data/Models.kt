package ru.prostayahimiya.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.time.Duration
import ru.prostayahimiya.app.chem.Equation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Данные, которые хранятся на сервере PocketBase. Названия полей совпадают с полями в базе
 * (см. server/pb_migrations).
 */

@Serializable
data class User(
    val id: String,
    val username: String = "",
    val name: String = "",
    /** Класс ученика, например «8Б». */
    val group: String = "",
    val isTeacher: Boolean = false,
    /** Когда ученик зарегистрировался: по этому порядку раздаются варианты. */
    val created: String = "",
    /** Учитель ученика. */
    val teacher: String = "",
    /** Зарегистрировался как учитель и ждёт подтверждения администратора. */
    val wantsTeacher: Boolean = false,
    /** Выпускник: класса больше нет (ставит кнопка «Новый учебный год»). */
    val graduated: Boolean = false,
    /** Из какого класса выпустился, например «11А». */
    val graduatedFrom: String = "",
)

/** Задания от нейросети: проверенные сервером задания (ещё не сохранены), сколько отброшено и сколько запросов осталось. */
@Serializable
data class GeneratedTasks(val tasks: List<Task> = emptyList(), val dropped: Int = 0, val left: Int? = null)

/**
 * Класс в одном виде: без пробелов, большими буквами, латинские буквы-двойники заменены русскими.
 * «8 б» → «8Б», «8A» (латиница) → «8А». Так же делают сайт и сервер.
 */
fun normalizeGroup(value: String): String {
    val twins = mapOf('A' to 'А', 'B' to 'В', 'C' to 'С', 'E' to 'Е', 'H' to 'Н', 'K' to 'К', 'M' to 'М', 'O' to 'О', 'P' to 'Р', 'T' to 'Т', 'X' to 'Х', 'Y' to 'У')
    return value.filterNot { it.isWhitespace() }.uppercase().map { twins[it] ?: it }.joinToString("")
}

/** Похоже ли на класс: цифра (1–11) и буква без пробела — «8Б», «10А». */
fun isGroup(value: String): Boolean {
    val m = Regex("^(\\d{1,2})[А-ЯЁ]{0,2}$").find(value) ?: return false
    return m.groupValues[1].toInt() in 1..11
}

/** Итог «Нового учебного года». */
@Serializable
data class NewYearResult(val moved: Int = 0, val graduated: Int = 0, val deadlinesCleared: Int = 0, val messagesDeleted: Int = 0)

/**
 * В какой класс ученик перейдёт в новом учебном году: «8А» → «9А», «11Б» → null (выпускник),
 * класс без номера («Кружок») не меняется. Так же считает сервер.
 */
fun nextGroup(group: String): String? {
    val m = Regex("^(\\d{1,2})(.*)$").find(group.trim()) ?: return group
    val grade = m.groupValues[1].toInt()
    return when {
        grade >= 11 -> null
        grade >= 1 -> "${grade + 1}${m.groupValues[2]}"
        else -> group
    }
}

@Serializable
data class AuthResponse(val token: String, val record: User)

@Serializable
data class Page<T>(val page: Int = 1, val totalPages: Int = 1, val totalItems: Int = -1, val items: List<T> = emptyList())

@Serializable
data class Practice(
    val id: String = "",
    val title: String = "",
    /** chemistry или biology */
    val subject: String = Subject.CHEMISTRY.key,
    val grade: Int = 8,
    val position: Int = 0,
    val published: Boolean = false,
    /** Для каких классов практика («8А», «8Б»). Пусто — для всех. */
    val classes: List<String> = emptyList(),
    /** Срок сдачи (время сервера) или пусто. */
    val deadline: String = "",
    /** Контрольная: ответы сразу не проверяются, сдать можно один раз, задания — только после «Начать». */
    val exam: Boolean = false,
    /** Минут на контрольную (0 — без ограничения). */
    val timeLimit: Int = 0,
    /** Практика для одного ученика купленного курса (id записи курса) или пусто. */
    val enrollment: String = "",
) {
    val subjectEnum: Subject get() = Subject.fromKey(subject)

    /** «Контрольная · 40 мин» */
    val examLabel: String get() = "Контрольная" + if (timeLimit > 0) " · $timeLimit мин" else ""

    /** Когда ученик увидит результаты контрольной. Так же пишет сайт. */
    val examResultsText: String
        get() {
            val d = deadlineInstant
            return if (d != null && d.isAfter(Instant.now())) "Результаты появятся после срока сдачи — ${formatDay(d)}"
            else "Результаты появятся, когда учитель проверит работу"
        }

    val deadlineInstant: Instant? get() = parseServerInstant(deadline)

    /** Видна ли практика ученику этого класса. */
    fun isFor(group: String): Boolean = classes.isEmpty() || group in classes

    /**
     * Задана ли практика этому классу (для прогресса, сроков и журнала).
     * Если учитель выбрал классы — только им. Если практика «для всех классов» — ученикам своей параллели:
     * биология 5 класса засчитывается 5А и 5Б, но не девятиклассникам. Так же считает сайт.
     */
    fun isAssignedTo(group: String): Boolean {
        if (enrollment.isNotBlank()) return false // практика ученика курса — в «Подготовке», а не в классе
        if (classes.isNotEmpty()) return group in classes
        val g = gradeOf(group)
        return g == 0 || grade == g
    }

    /** «Химия, 8 класс · Практика по химии» — так работа подписывается в кабинете. */
    val fullTitle: String get() = "${subjectEnum.title}, $grade класс · $title"
}

/** Виды заданий в том порядке, в каком разделы идут в практике. */
enum class TaskType(val key: String, val title: String) {
    QUESTION("question", "Вопрос по теории"),
    CHOICE("choice", "Тест"),
    FILL("fill", "Вставь пропущенные слова"),
    FORMULA("formula", "Составь формулу"),
    MOLAR("molar", "Молярная масса"),
    EQUATION("equation", "Расставь коэффициенты"),
    ;

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: QUESTION
    }
}

@Serializable
data class Task(
    val id: String = "",
    val practice: String = "",
    val type: String = TaskType.QUESTION.key,
    val position: Int = 0,
    /** Вопрос, название вещества, уравнение без коэффициентов, текст с пропусками «___» или формула для молярной массы. */
    val text: String = "",
    /**
     * Правильный ответ. Его видит только учитель: ученику сервер ответы не присылает
     * и проверяет их сам (server/pb_hooks).
     */
    val answer: String = "",
    val hint: String = "",
    /** Варианты ответа в тесте. */
    val options: List<String> = emptyList(),
    /** В тесте несколько верных ответов (галочки вместо кружков). */
    val multiple: Boolean = false,
    /** Имя файла картинки к заданию или пусто. */
    val image: String = "",
    /** 0 — задание для всех; 1–4 — только для этого варианта. */
    val variant: Int = 0,
) {
    val taskType: TaskType get() = TaskType.fromKey(type)

    /** Задания с автопроверкой (её делает сервер). */
    val isCheckable: Boolean get() = taskType != TaskType.QUESTION

    val equation: Equation? get() = if (taskType == TaskType.EQUATION) Equation.parse(text) else null

    val coefficients: List<Int>? get() = parseCoefficients(answer)
}

fun parseCoefficients(text: String): List<Int>? {
    val parts = text.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return null
    return parts.map { it.toIntOrNull() ?: return null }
}

/** Ответ ученика на одно задание внутри сданной работы. */
@Serializable
data class AnswerItem(
    val kind: String,
    val prompt: String,
    val answer: String,
    /** Для заданий с автопроверкой: решено ли верно (считает сервер). Для вопросов null. */
    val correct: Boolean? = null,
    /** Сколько раз ученик нажимал «Проверить» (считает сервер, видит учитель). */
    val attempts: Int? = null,
    /** id задания: по нему сервер перепроверяет ответ. */
    val task: String? = null,
    /** То, что ввёл ученик, как есть (для уравнения «4|3|2», для теста «1,3», для пропусков «слово|слово»). */
    val input: String? = null,
)

/** Ответ сервера на «Проверить». kind: correct, hint (почти), wrong или error. */
@Serializable
data class CheckResult(
    val task: String = "",
    val kind: String = "error",
    val message: String = "",
    /** Для пропусков: какие заполнены верно. */
    val blanks: List<Boolean>? = null,
    val attempts: Int = 0,
) {
    val isCorrect: Boolean get() = kind == "correct"
}

@Serializable
data class CheckResponse(val results: List<CheckResult> = emptyList())

@Serializable
data class Submission(
    val id: String = "",
    val student: String = "",
    val practice: String = "",
    val practiceTitle: String = "",
    val answers: List<AnswerItem> = emptyList(),
    val solved: Int = 0,
    val total: Int = 0,
    /** 0 = ещё не проверено, иначе оценка 1–5. */
    val grade: Int = 0,
    val comment: String = "",
    val gradedAt: String = "",
    val created: String = "",
    val expand: SubmissionExpand? = null,
    /** Какой вариант решал ученик (0 — вариантов не было). */
    val variant: Int = 0,
    /** Контрольная пришла после того, как время вышло. */
    val overtime: Boolean = false,
    /** Контрольная: результаты ещё закрыты (до срока сдачи или оценки), «верно/неверно» сервер не прислал. */
    val resultsHidden: Boolean = false,
) {
    val isGraded: Boolean get() = grade > 0
    val studentUser: User? get() = expand?.student
}

@Serializable
data class SubmissionExpand(val student: User? = null)

/** Практики и задания, загруженные с сервера. Сохраняются на телефоне, чтобы работать без интернета. */
@Serializable
data class ContentData(
    val practices: List<Practice> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val notes: List<Note> = emptyList(),
    /** Варианты текущего ученика: id практики → номер варианта (считает сервер, /api/variants). */
    val variants: Map<String, Int> = emptyMap(),
    /** Начатые контрольные ученика: id практики → когда начата (время сервера). */
    val examStarts: Map<String, String> = emptyMap(),
) {
    fun practice(id: String): Practice? = practices.firstOrNull { it.id == id }

    fun noteFor(subject: Subject, grade: Int): Note? = notes.firstOrNull { it.subject == subject.key && it.grade == grade }

    /** Сколько вариантов в практике (0 — вариантов нет, все решают одно и то же). */
    fun variantCount(practiceId: String): Int = tasksOf(practiceId).maxOfOrNull { it.variant }?.coerceAtLeast(0) ?: 0

    /**
     * Вариант текущего ученика в практике. Варианты раздаются по очереди внутри класса в порядке
     * регистрации (1, 2, 1, 2…) — это считает сервер, потому что ученик не видит список одноклассников.
     */
    fun variantFor(practiceId: String): Int {
        val count = variantCount(practiceId)
        if (count == 0) return 0
        return variants[practiceId]?.takeIf { it in 1..count } ?: 1
    }

    /** Для учителя: вариант каждого ученика по тому же правилу, что на сервере (id ученика → вариант). */
    fun variantsOf(practiceId: String, students: List<User>): Map<String, Int> {
        val count = variantCount(practiceId)
        if (count == 0) return emptyMap()
        return students.sortedWith(compareBy({ it.created }, { it.id }))
            .groupBy { it.group }
            .values
            .flatMap { list -> list.mapIndexed { i, st -> st.id to (i % count) + 1 } }
            .toMap()
    }

    /** Задания, которые видит ученик этого варианта: общие и своего варианта. */
    fun tasksForVariant(practiceId: String, variant: Int): List<Task> =
        tasksOf(practiceId).filter { it.variant == 0 || it.variant == variant }

    /** Задания практики для пользователя (учитель по умолчанию видит первый вариант). */
    fun tasksFor(practiceId: String, user: User?): List<Task> {
        val variant = if (user != null && !user.isTeacher) variantFor(practiceId) else minOf(1, variantCount(practiceId))
        return tasksForVariant(practiceId, variant)
    }

    fun tasksOf(practiceId: String): List<Task> =
        tasks.filter { it.practice == practiceId }.sortedWith(compareBy({ it.position }, { it.id }))

    /** Практики предмета для классов (практики учеников курсов — отдельно, см. [coursePractices]). */
    fun practicesOf(subject: Subject): List<Practice> =
        practices.filter { it.subjectEnum == subject && it.enrollment.isBlank() }
            .sortedWith(compareBy({ it.grade }, { it.position }, { it.title }))

    /** Практики одного ученика курса. */
    fun coursePractices(enrollmentId: String): List<Practice> =
        practices.filter { it.enrollment == enrollmentId }.sortedWith(compareBy({ it.position }, { it.title }))

    val checkableTaskIds: List<String> get() = tasks.filter { it.isCheckable }.map { it.id }
}

/** Заметка учителя к теории для класса по предмету. */
@Serializable
data class Note(
    val id: String = "",
    val subject: String = "",
    val grade: Int = 0,
    val text: String = "",
)

/** «9А» → 9; если класс записан без цифры — 0 (неизвестно). */
fun gradeOf(group: String): Int = group.takeWhile { it.isDigit() }.toIntOrNull()?.takeIf { it in 1..11 } ?: 0

/** Код, по которому ученики регистрируются в свой класс. */
@Serializable
data class ClassCode(
    val id: String = "",
    val group: String = "",
    val code: String = "",
    val active: Boolean = false,
)

/** Учитель в списке при регистрации. codeRequired — регистрироваться к нему можно только по коду класса. */
@Serializable
data class TeacherInfo(val id: String = "", val name: String = "", val codeRequired: Boolean = false)

@Serializable
data class RegistrationInfo(val teachers: List<TeacherInfo> = emptyList(), val codeRequired: Boolean = false)

@Serializable
data class VariantsResponse(val variants: Map<String, Int> = emptyMap())

/** Версия приложения, выложенная на сайте (урокискириллом.рф/app). */
@Serializable
data class AppRelease(val versionCode: Int = 0, val versionName: String = "", val notes: String = "")

/** Что написать про срок сдачи: текст и прошёл ли срок. */
data class DeadlineStatus(val text: String, val overdue: Boolean)

/** «12 ноября» по времени телефона. */
fun formatDay(instant: Instant): String =
    DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru")).withZone(ZoneId.systemDefault()).format(instant)

/** Срок для ученика: «Сдать до 12 ноября» или «Срок прошёл 12 ноября». Если работа сдана — null. */
fun Practice.deadlineStatus(submitted: Boolean, now: Instant = Instant.now()): DeadlineStatus? {
    val d = deadlineInstant ?: return null
    if (submitted) return null
    return if (d.isBefore(now)) DeadlineStatus("Срок прошёл ${formatDay(d)}", true) else DeadlineStatus("Сдать до ${formatDay(d)}", false)
}

/** Сдана ли работа позже срока. */
fun Submission.isLate(practice: Practice?): Boolean {
    val d = practice?.deadlineInstant ?: return false
    val sent = parseServerInstant(created) ?: return false
    return sent.isAfter(d)
}

/** Сообщение в чате (см. server/pb_migrations/1762100000_chat.js). */
@Serializable
data class Message(
    val id: String = "",
    val author: String = "",
    val authorName: String = "",
    val fromTeacher: Boolean = false,
    /** class — чат класса, private — переписка ученика с учителем. */
    val kind: String = ChatRoom.CLASS,
    val group: String = "",
    val student: String = "",
    val text: String = "",
    val created: String = "",
    /** Чат купленного курса: id записи курса. */
    val enrollment: String = "",
)

/**
 * Комната чата: [key] — класс («8Б») для чата класса, id ученика для личной переписки
 * или id записи курса для чата курса.
 */
data class ChatRoom(val kind: String, val key: String) {
    val isClass: Boolean get() = kind == CLASS
    val isCourse: Boolean get() = kind == COURSE
    val id: String get() = "$kind:$key"

    fun contains(message: Message): Boolean = message.kind == kind && when (kind) {
        CLASS -> message.group == key
        COURSE -> message.enrollment == key
        else -> message.student == key
    }

    companion object {
        const val CLASS = "class"
        const val PRIVATE = "private"
        const val COURSE = "course"
        fun ofClass(group: String) = ChatRoom(CLASS, group)
        fun private(studentId: String) = ChatRoom(PRIVATE, studentId)
        fun course(enrollmentId: String) = ChatRoom(COURSE, enrollmentId)
        fun of(kind: String, key: String) = ChatRoom(if (kind == PRIVATE || kind == COURSE) kind else CLASS, key)
    }
}

/** Купленный курс (магазин beemei на сайте): ученик, учитель, предмет и экзамен. */
@Serializable
data class Enrollment(
    val id: String = "",
    val student: String = "",
    val teacher: String = "",
    val title: String = "",
    val subject: String = "",
    val exam: String = "",
    val active: Boolean = false,
    /** До какого времени открыт курс (время сервера). */
    val expiresAt: String = "",
    val product: String = "",
    val created: String = "",
    val expand: EnrollmentExpand? = null,
) {
    val subjectEnum: Subject get() = Subject.fromKey(subject)
    val expiresInstant: Instant? get() = parseServerInstant(expiresAt)
    /** Курс открыт: оплачен и срок ещё не кончился. */
    val isLive: Boolean get() = active && expiresInstant?.isAfter(Instant.now()) == true
    /** «до 12 ноября» или «закончился 12 ноября». */
    val untilText: String
        get() = expiresInstant?.let { (if (it.isAfter(Instant.now())) "до " else "закончился ") + formatDay(it) }.orEmpty()
    /** «Подготовка к ЕГЭ · Биология» */
    val heading: String get() = "Подготовка к $exam · ${subjectEnum.title}"
    /** ОГЭ сдают в 9 классе, ЕГЭ — в 11. */
    val grade: Int get() = if (exam == "ОГЭ") 9 else 11
    /** Сколько часов осталось до конца курса (null — срок не указан). */
    val hoursLeft: Double? get() = expiresInstant?.let { Duration.between(Instant.now(), it).toMinutes() / 60.0 }
    /** Курс открыт, но закончится меньше чем через 3 дня — пора продлить. */
    val endingSoon: Boolean get() = isLive && (hoursLeft ?: 1000.0) < 72
    /** «2 дня», «5 ч» — сколько осталось. */
    val leftText: String
        get() {
            val h = hoursLeft ?: return ""
            if (h <= 0) return ""
            return if (h < 24) "${kotlin.math.ceil(h).toInt().coerceAtLeast(1)} ч" else daysText(kotlin.math.ceil(h / 24).toInt())
        }
}

/** «1 день», «2 дня», «5 дней» */
fun daysText(n: Int): String {
    val d = n % 10
    val word = if (n % 100 in 11..14) "дней" else if (d == 1) "день" else if (d in 2..4) "дня" else "дней"
    return "$n $word"
}

/** Заказ в магазине beemei («Мои покупки»). */
@Serializable
data class Order(
    val id: String = "",
    /** pending — ждёт оплаты, paid — оплачен, canceled — отменён, error — платёж не создался. */
    val status: String = "",
    val total: Double = 0.0,
    val discount: Double = 0.0,
    val promo: String = "",
    val created: String = "",
    val items: List<OrderItem> = emptyList(),
)

@Serializable
data class OrderItem(
    val product: String = "",
    val title: String = "",
    val price: Double = 0.0,
    val days: Int = 0,
    val subject: String = "",
    val exam: String = "",
    val teacher: String = "",
    val teacherName: String = "",
) {
    /** «Химия · ЕГЭ · 7 дней · учитель Ирина Петровна» */
    val optionsText: String
        get() = listOfNotNull(
            subject.takeIf { it.isNotBlank() }?.let { Subject.fromKey(it).title },
            exam.takeIf { it.isNotBlank() },
            days.takeIf { it > 0 }?.let { daysText(it) },
            teacherName.takeIf { it.isNotBlank() }?.let { "учитель $it" },
        ).joinToString(" · ")
}

/** «7 000 ₽» */
fun rub(value: Double): String = "%,d ₽".format(Locale.forLanguageTag("ru"), Math.round(value)).replace('\u00A0', ' ').replace('\u202F', ' ')

/** Отзыв о товаре магазина. Писать может только тот, кто купил товар. */
@Serializable
data class Review(
    val id: String = "",
    val product: String = "",
    val author: String = "",
    val authorName: String = "",
    val rating: Int = 0,
    val text: String = "",
    val created: String = "",
)

/** Кто сейчас звонит (ответ /api/call/incoming). */
@Serializable
data class IncomingCall(
    val id: String = "",
    val enrollment: String = "",
    val from: String = "",
    val name: String = "",
    val video: Boolean = false,
    val created: String = "",
)

@Serializable
data class IncomingResponse(val call: IncomingCall? = null)

/** Служебное сообщение звонка (для «пропущенный звонок»). */
@Serializable
data class CallSignal(
    val id: String = "",
    val call: String = "",
    val from: String = "",
    val to: String = "",
    val enrollment: String = "",
    val type: String = "",
    val data: JsonObject? = null,
    val created: String = "",
) {
    val callerName: String get() = (data?.get("name") as? JsonPrimitive)?.content.orEmpty()
    val video: Boolean get() = (data?.get("video") as? JsonPrimitive)?.booleanOrNull == true
}

@Serializable
data class EnrollmentExpand(val student: User? = null, val teacher: User? = null)

/** Строка в списке чатов: комната, её название и последнее сообщение. */
data class ChatThread(
    val room: ChatRoom,
    val title: String,
    val subtitle: String = "",
    val last: Message? = null,
    val unread: Boolean = false,
)

/** Время сервера → момент времени, или null, если строка пустая или битая. */
fun parseServerInstant(value: String): Instant? =
    runCatching { Instant.parse(value.trim().replace(' ', 'T')) }.getOrNull()

/** «2026-11-03 16:44:58.123Z» → «3 ноября, 19:44» (по времени телефона). */
fun formatServerDate(value: String): String {
    if (value.isBlank()) return ""
    return try {
        val instant = Instant.parse(value.trim().replace(' ', 'T'))
        DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru"))
            .withZone(ZoneId.systemDefault())
            .format(instant)
    } catch (_: Exception) {
        value
    }
}

/** Запись о начатой контрольной (коллекция exam_starts). */
@Serializable
data class ExamStart(val practice: String = "", val started: String = "")

/** Ответ /api/exam/start: когда начата, сколько минут и задания (раньше «Начать» ученик их не видит). */
@Serializable
data class ExamStartResponse(
    val started: String = "",
    val now: String = "",
    val timeLimit: Int = 0,
    val submitted: Boolean = false,
    val tasks: List<Task> = emptyList(),
)

/** Идущая контрольная: когда кончится время (null — без ограничения) и насколько часы сервера впереди телефона. */
data class ExamSession(val endsAt: Instant?, val offsetMillis: Long) {
    /** Сколько миллисекунд осталось по часам сервера. */
    fun leftMillis(nowMillis: Long = System.currentTimeMillis()): Long? = endsAt?.let { it.toEpochMilli() - (nowMillis + offsetMillis) }
}
