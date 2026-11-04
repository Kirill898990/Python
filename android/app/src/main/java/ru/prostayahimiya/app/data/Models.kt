package ru.prostayahimiya.app.data

import kotlinx.serialization.Serializable
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
)

@Serializable
data class AuthResponse(val token: String, val record: User)

@Serializable
data class Page<T>(val page: Int = 1, val totalPages: Int = 1, val items: List<T> = emptyList())

@Serializable
data class Practice(
    val id: String = "",
    val title: String = "",
    /** chemistry или biology */
    val subject: String = Subject.CHEMISTRY.key,
    val grade: Int = 8,
    val position: Int = 0,
    val published: Boolean = false,
) {
    val subjectEnum: Subject get() = Subject.fromKey(subject)

    /** «Химия, 8 класс · Практика по химии» — так работа подписывается в кабинете. */
    val fullTitle: String get() = "${subjectEnum.title}, $grade класс · $title"
}

enum class TaskType(val key: String, val title: String) {
    QUESTION("question", "Вопрос по теории"),
    FORMULA("formula", "Составь формулу"),
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
    /** Вопрос, название вещества или уравнение без коэффициентов. */
    val text: String = "",
    /** Формула («Ca(OH)2») или коэффициенты («4 3 2»). У вопроса пусто. */
    val answer: String = "",
    val hint: String = "",
) {
    val taskType: TaskType get() = TaskType.fromKey(type)

    /** Задания, которые приложение проверяет само. */
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
    /** Для заданий с автопроверкой: решено ли верно. Для вопросов null. */
    val correct: Boolean? = null,
    /** Сколько раз ученик нажимал «Проверить» (видит учитель). */
    val attempts: Int? = null,
)

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
) {
    fun practice(id: String): Practice? = practices.firstOrNull { it.id == id }

    fun tasksOf(practiceId: String): List<Task> =
        tasks.filter { it.practice == practiceId }.sortedWith(compareBy({ it.position }, { it.id }))

    fun practicesOf(subject: Subject): List<Practice> =
        practices.filter { it.subjectEnum == subject }.sortedWith(compareBy({ it.grade }, { it.position }, { it.title }))

    val checkableTaskIds: List<String> get() = tasks.filter { it.isCheckable }.map { it.id }
}

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
