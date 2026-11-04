package ru.prostayahimiya.app.data

import java.time.Instant
import java.util.Locale

/*
 * Журнал учителя: оценки класса по практикам, выгрузка для Excel и статистика.
 * Здесь только расчёты (без Android), поэтому они проверяются обычными тестами. Так же считает сайт.
 */

/** Клетка журнала. */
sealed interface JournalCell {
    /** Работа проверена. */
    data class Grade(val grade: Int, val submissionId: String, val late: Boolean) : JournalCell
    /** Сдано, ждёт проверки. */
    data class Submitted(val submissionId: String, val late: Boolean) : JournalCell
    /** Не сдано, а срок уже прошёл. */
    data object Missed : JournalCell
    /** Не сдано, срока нет или он ещё не наступил. */
    data object Empty : JournalCell
}

data class JournalRow(val student: User, val cells: List<JournalCell>, val average: Double?)

data class JournalTable(val group: String, val practices: List<Practice>, val rows: List<JournalRow>)

/** Задание, которое у класса получается хуже всего. */
data class HardTask(val prompt: String, val right: Int, val total: Int, val averageAttempts: Double)

data class PracticeStats(
    val practice: Practice,
    val submitted: Int,
    val students: Int,
    val late: Int,
    val averageGrade: Double?,
    val hardest: List<HardTask>,
    /** Есть задания с автопроверкой, и все решены верно. */
    val allSolved: Boolean,
)

/** Классы учеников по порядку: 5А, 8А, 8Б, 10А. */
fun classesOf(students: List<User>): List<String> =
    students.map { it.group }.filter { it.isNotBlank() }.distinct()
        .sortedWith(compareBy({ it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }, { it }))

/** Практики, которые задавали классу (черновики не считаем). */
fun practicesFor(group: String, practices: List<Practice>): List<Practice> =
    practices.filter { it.published && it.isAssignedTo(group) }
        .sortedWith(compareBy({ it.subject }, { it.grade }, { it.position }, { it.title }))

/** submissions — новые сверху (так их отдаёт сервер). */
fun buildJournal(
    group: String,
    students: List<User>,
    submissions: List<Submission>,
    practices: List<Practice>,
    now: Instant = Instant.now(),
): JournalTable {
    val list = practicesFor(group, practices)
    val kids = students.filter { it.group == group }.sortedBy { it.name.lowercase(Locale.forLanguageTag("ru")) }
    val rows = kids.map { student ->
        val cells = list.map { practice ->
            val subs = submissions.filter { it.student == student.id && it.practice == practice.id }
            val graded = subs.firstOrNull { it.isGraded }
            when {
                graded != null -> JournalCell.Grade(graded.grade, graded.id, graded.isLate(practice))
                subs.isNotEmpty() -> JournalCell.Submitted(subs.first().id, subs.first().isLate(practice))
                practice.deadlineInstant?.isBefore(now) == true -> JournalCell.Missed
                else -> JournalCell.Empty
            }
        }
        val grades = cells.filterIsInstance<JournalCell.Grade>().map { it.grade }
        JournalRow(student, cells, grades.takeIf { it.isNotEmpty() }?.average())
    }
    return JournalTable(group, list, rows)
}

/** «4,5» — как пишут в журнале. */
fun formatAverage(value: Double?): String =
    value?.let { String.format(Locale.forLanguageTag("ru"), "%.1f", it) }.orEmpty()

/** Файл для Excel: точка с запятой и метка UTF-8 в начале, чтобы русские буквы открылись правильно. */
fun JournalTable.toCsv(): String {
    fun esc(v: String) = if (v.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    val header = listOf("Ученик", "Класс") + practices.map { it.fullTitle } + "Средний балл"
    val lines = listOf(header) + rows.map { row ->
        listOf(row.student.name, group) + row.cells.map { cell ->
            when (cell) {
                is JournalCell.Grade -> cell.grade.toString()
                is JournalCell.Submitted -> "сдано"
                JournalCell.Missed -> "не сдано"
                JournalCell.Empty -> ""
            }
        } + formatAverage(row.average)
    }
    return "\uFEFF" + lines.joinToString("\r\n") { line -> line.joinToString(";") { esc(it) } }
}

/** Сводка по практике для класса: кто сдал, средняя оценка, самые трудные задания. */
fun practiceStats(practice: Practice, kids: List<User>, submissions: List<Submission>): PracticeStats {
    val latest = kids.mapNotNull { st -> submissions.firstOrNull { it.student == st.id && it.practice == practice.id } }
    val graded = kids.mapNotNull { st -> submissions.firstOrNull { it.student == st.id && it.practice == practice.id && it.isGraded } }
    data class Acc(val prompt: String, var right: Int = 0, var total: Int = 0, var attempts: Int = 0)
    val perTask = LinkedHashMap<String, Acc>()
    latest.forEach { s ->
        s.answers.forEach { a ->
            val correct = a.correct ?: return@forEach
            val acc = perTask.getOrPut(a.task ?: a.prompt) { Acc(a.prompt) }
            acc.total++
            if (correct) acc.right++
            acc.attempts += a.attempts ?: 0
        }
    }
    val hardest = perTask.values.filter { it.right < it.total }
        .sortedBy { it.right.toDouble() / it.total }
        .take(3)
        .map { HardTask(it.prompt, it.right, it.total, it.attempts.toDouble() / it.total) }
    return PracticeStats(
        practice = practice,
        submitted = latest.size,
        students = kids.size,
        late = latest.count { it.isLate(practice) },
        averageGrade = graded.map { it.grade }.takeIf { it.isNotEmpty() }?.average(),
        hardest = hardest,
        allSolved = perTask.isNotEmpty() && hardest.isEmpty(),
    )
}
