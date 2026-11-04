package ru.prostayahimiya.app.ui.practice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import ru.prostayahimiya.app.chem.BLANK
import ru.prostayahimiya.app.chem.Chem
import ru.prostayahimiya.app.chem.parseChoice
import ru.prostayahimiya.app.data.AnswerItem
import ru.prostayahimiya.app.data.ApiException
import ru.prostayahimiya.app.data.CheckResult
import ru.prostayahimiya.app.data.Practice
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.data.Submission
import ru.prostayahimiya.app.data.Task
import ru.prostayahimiya.app.data.TaskType
import kotlin.coroutines.cancellation.CancellationException

sealed interface SendState {
    data object Idle : SendState
    data object Sending : SendState
    data object Sent : SendState
    data class Error(val message: String) : SendState
}

/**
 * Состояние экрана практики. ViewModel переживает поворот экрана,
 * а все введённые ответы ещё и сохраняются на телефоне как черновик.
 *
 * Ответ ученика на любое задание хранится одной строкой — так же, как на сайте:
 * вопрос и формула — как есть, уравнение — коэффициенты через «|», тест — номера через запятую,
 * пропуски — слова через «|», молярная масса — число.
 * Правильные ответы на телефон не приходят: «Проверить» спрашивает сервер.
 */
class PracticeViewModel(
    private val repository: Repository,
    val practice: Practice,
    tasks: List<Task>,
    /** Вариант ученика (0 — вариантов нет). Сохраняется вместе с работой. */
    val variant: Int = 0,
) : ViewModel() {

    private val store = repository.progress

    /** Все задания (уравнения с ошибкой в записи не показываем). */
    val tasks: List<Task> = tasks.filter { it.taskType != TaskType.EQUATION || it.equation != null }

    /** Разделы практики: вид заданий и сами задания, в порядке [TaskType]. */
    val sections: List<Pair<TaskType, List<Task>>> =
        TaskType.entries.mapNotNull { type -> this.tasks.filter { it.taskType == type }.takeIf { it.isNotEmpty() }?.let { type to it } }

    val checkable = this.tasks.filter { it.isCheckable }
    val totalCount: Int get() = tasks.size

    private val inputs = mutableStateMapOf<String, String>().apply { this@PracticeViewModel.tasks.forEach { put(it.id, store.draft(it.id)) } }
    val results = mutableStateMapOf<String, CheckResult>()
    val checking = mutableStateMapOf<String, Boolean>()

    /** Сколько заданий ученик уже заполнил (для полоски прогресса). Правильность не раскрывается. */
    val filledCount: Int get() = tasks.count { isFilled(input(it)) }

    var sendState by mutableStateOf<SendState>(SendState.Idle)
        private set

    /** Последняя сданная работа по этой практике (чтобы показать оценку). */
    var lastSubmission by mutableStateOf<Submission?>(null)
        private set

    init {
        viewModelScope.launch {
            runCatching { repository.mySubmissions(practice.id) }.onSuccess { lastSubmission = it.firstOrNull() }
        }
    }

    fun input(task: Task): String = inputs[task.id].orEmpty()

    fun onInput(task: Task, value: String) {
        inputs[task.id] = value
        store.saveDraft(task.id, value)
        results.remove(task.id)
        if (sendState !is SendState.Sending) sendState = SendState.Idle
    }

    /** Части ответа: клеточки уравнения или пропуски в тексте. */
    fun parts(task: Task, size: Int): List<String> {
        val saved = input(task).split(SEPARATOR)
        return List(size) { saved.getOrElse(it) { "" } }
    }

    fun onPart(task: Task, index: Int, size: Int, value: String) {
        val updated = parts(task, size).toMutableList().also { it[index] = value }
        onInput(task, updated.joinToString(SEPARATOR))
    }

    fun coefficientCount(task: Task): Int = task.equation?.substances?.size ?: 0

    fun blankCount(task: Task): Int = BLANK.findAll(task.text).count()

    fun chosen(task: Task): List<Int> = parseChoice(input(task))

    fun toggleChoice(task: Task, number: Int) {
        val current = chosen(task).toMutableSet()
        if (task.multiple) {
            if (!current.add(number)) current.remove(number)
        } else {
            current.clear()
            current.add(number)
        }
        onInput(task, current.sorted().joinToString(","))
    }

    /** Проверить ответ на сервере. */
    fun check(task: Task) {
        if (checking[task.id] == true) return
        val value = input(task)
        // Пустые клеточки уравнения — это единицы, поэтому уравнение можно проверять и без ввода.
        if (task.taskType != TaskType.EQUATION && !isFilled(value)) {
            results[task.id] = CheckResult(
                task.id,
                "hint",
                if (task.taskType == TaskType.CHOICE) "Выбери ответ" else "Сначала впиши ответ",
            )
            return
        }
        checking[task.id] = true
        viewModelScope.launch {
            val result = try {
                repository.check(task, value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = (e as? ApiException)?.takeIf { it.status != 0 }?.message
                CheckResult(task.id, "error", message ?: "Нет связи с сервером. Проверка работает только с интернетом.")
            }
            results[task.id] = result
            if (result.isCorrect) store.markSolved(task.id)
            checking[task.id] = false
        }
    }

    /** Сдать работу учителю. Сколько верно, сервер посчитает сам. */
    fun submit() {
        if (sendState is SendState.Sending) return
        val items = buildAnswers()
        if (items.none { it.answer.isNotBlank() }) {
            sendState = SendState.Error("Сначала ответь хотя бы на одно задание.")
            return
        }
        sendState = SendState.Sending
        viewModelScope.launch {
            sendState = try {
                lastSubmission = repository.submit(practice, items, 0, checkable.size, variant)
                SendState.Sent
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SendState.Error(e.message ?: "Не получилось отправить")
            }
        }
    }

    private fun buildAnswers(): List<AnswerItem> = tasks.map { task ->
        val value = input(task)
        val empty = !isFilled(value)
        when (task.taskType) {
            TaskType.QUESTION -> AnswerItem("question", task.text, value.trim(), task = task.id)
            TaskType.FORMULA -> AnswerItem(
                "formula", task.text, if (empty) "" else Chem.pretty(Chem.normalize(value)), task = task.id, input = value,
            )
            TaskType.EQUATION -> {
                val equation = task.equation!!
                val values = parts(task, equation.substances.size)
                AnswerItem(
                    "equation",
                    equation.pretty(),
                    if (empty) "" else equation.pretty(values.map { it.toIntOrNull() ?: 1 }),
                    task = task.id,
                    input = values.joinToString(SEPARATOR),
                )
            }
            TaskType.CHOICE -> AnswerItem(
                "choice",
                task.text,
                parseChoice(value).mapNotNull { task.options.getOrNull(it - 1) }.joinToString("; ") { Chem.prettyIfFormula(it) },
                task = task.id,
                input = value,
            )
            TaskType.FILL -> {
                val words = value.split(SEPARATOR)
                var k = 0
                AnswerItem(
                    "fill",
                    task.text,
                    if (empty) "" else BLANK.replace(task.text) { "«" + (words.getOrNull(k++)?.trim()?.ifBlank { null } ?: "…") + "»" },
                    task = task.id,
                    input = value,
                )
            }
            TaskType.MOLAR -> AnswerItem(
                "molar",
                "M(${Chem.pretty(Chem.normalize(task.text))})",
                if (empty) "" else "${value.trim()} г/моль",
                task = task.id,
                input = value.trim(),
            )
        }
    }

    /** «Начать заново»: стирает отметки и черновики этой практики на телефоне. */
    fun reset() {
        store.reset(tasks.map { it.id })
        tasks.forEach { inputs[it.id] = "" }
        results.clear()
        sendState = SendState.Idle
    }

    companion object {
        private const val SEPARATOR = "|"

        private fun isFilled(value: String) = value.replace("|", "").replace(",", "").isNotBlank()

        fun factory(repository: Repository, practice: Practice, tasks: List<Task>, variant: Int) = viewModelFactory {
            initializer { PracticeViewModel(repository, practice, tasks, variant) }
        }
    }
}
