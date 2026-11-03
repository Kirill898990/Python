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
import ru.prostayahimiya.app.chem.Chem
import ru.prostayahimiya.app.chem.EquationCheck
import ru.prostayahimiya.app.chem.FormulaCheck
import ru.prostayahimiya.app.chem.checkEquation
import ru.prostayahimiya.app.chem.checkFormula
import ru.prostayahimiya.app.data.AnswerItem
import ru.prostayahimiya.app.data.Practice
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.data.Submission
import ru.prostayahimiya.app.data.Task
import ru.prostayahimiya.app.data.TaskType

sealed interface SendState {
    data object Idle : SendState
    data object Sending : SendState
    data object Sent : SendState
    data class Error(val message: String) : SendState
}

/**
 * Состояние экрана практики. ViewModel переживает поворот экрана,
 * а все введённые ответы ещё и сохраняются на телефоне как черновик.
 */
class PracticeViewModel(private val repository: Repository, val practice: Practice, tasks: List<Task>) : ViewModel() {

    private val store = repository.progress

    val questions = tasks.filter { it.taskType == TaskType.QUESTION }
    val formulas = tasks.filter { it.taskType == TaskType.FORMULA && it.answer.isNotBlank() }
    val equations = tasks.filter { it.equation != null && it.coefficients != null }
    val checkableIds = (formulas + equations).map { it.id }

    val answers = mutableStateMapOf<String, String>().apply { questions.forEach { put(it.id, store.draft(it.id)) } }
    val formulaInputs = mutableStateMapOf<String, String>().apply { formulas.forEach { put(it.id, store.draft(it.id)) } }
    val equationInputs = mutableStateMapOf<String, List<String>>().apply {
        equations.forEach { task ->
            val size = task.equation!!.substances.size
            val saved = store.draft(task.id).split(SEPARATOR)
            put(task.id, List(size) { saved.getOrElse(it) { "" } })
        }
    }
    val formulaResults = mutableStateMapOf<String, FormulaCheck>()
    val equationResults = mutableStateMapOf<String, EquationCheck>()
    val hintsShown = mutableStateMapOf<String, Boolean>()
    val answersShown = mutableStateMapOf<String, Boolean>()

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

    fun onAnswerChange(task: Task, text: String) {
        answers[task.id] = text
        store.saveDraft(task.id, text)
        if (sendState !is SendState.Sending) sendState = SendState.Idle
    }

    fun onFormulaChange(task: Task, text: String) {
        formulaInputs[task.id] = text
        store.saveDraft(task.id, text)
        formulaResults.remove(task.id)
    }

    fun check(task: Task) {
        when (task.taskType) {
            TaskType.FORMULA -> {
                val input = formulaInputs[task.id].orEmpty()
                if (input.isBlank()) return
                val result = checkFormula(input, task.answer)
                formulaResults[task.id] = result
                if (result is FormulaCheck.Correct) store.markSolved(task.id)
            }
            TaskType.EQUATION -> {
                val equation = task.equation ?: return
                val result = checkEquation(equation, coefficients(task))
                equationResults[task.id] = result
                if (result is EquationCheck.Correct) store.markSolved(task.id)
            }
            TaskType.QUESTION -> Unit
        }
    }

    fun coefficients(task: Task): List<String> = equationInputs[task.id].orEmpty()

    fun onCoefficientChange(task: Task, index: Int, text: String) {
        val updated = coefficients(task).toMutableList().also { it[index] = text.filter(Char::isDigit).take(2) }
        equationInputs[task.id] = updated
        store.saveDraft(task.id, updated.joinToString(SEPARATOR))
        equationResults.remove(task.id)
    }

    fun toggleHint(taskId: String) {
        hintsShown[taskId] = hintsShown[taskId] != true
    }

    fun showAnswer(taskId: String) {
        answersShown[taskId] = true
    }

    /** Сдать работу учителю: ответы на вопросы и всё, что ученик ввёл в заданиях. */
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
                lastSubmission = repository.submit(practice, items, items.count { it.correct == true }, checkableIds.size)
                SendState.Sent
            } catch (e: Exception) {
                SendState.Error(e.message ?: "Не получилось отправить")
            }
        }
    }

    private fun buildAnswers(): List<AnswerItem> = buildList {
        questions.forEach { add(AnswerItem("question", it.text, answers[it.id].orEmpty().trim())) }
        formulas.forEach { task ->
            val input = formulaInputs[task.id].orEmpty()
            add(
                AnswerItem(
                    kind = "formula",
                    prompt = task.text,
                    answer = if (input.isBlank()) "" else Chem.pretty(Chem.normalize(input)),
                    correct = input.isNotBlank() && checkFormula(input, task.answer) == FormulaCheck.Correct,
                ),
            )
        }
        equations.forEach { task ->
            val equation = task.equation!!
            val inputs = coefficients(task)
            val filled = inputs.any { it.isNotBlank() }
            add(
                AnswerItem(
                    kind = "equation",
                    prompt = equation.pretty(),
                    answer = if (filled) equation.pretty(inputs.map { it.toIntOrNull() ?: 1 }) else "",
                    correct = filled && checkEquation(equation, inputs) == EquationCheck.Correct,
                ),
            )
        }
    }

    /** «Начать заново»: стирает отметки и черновики этой практики на телефоне. */
    fun reset() {
        store.reset(questions.map { it.id } + checkableIds)
        questions.forEach { answers[it.id] = "" }
        formulas.forEach { formulaInputs[it.id] = "" }
        equations.forEach { equationInputs[it.id] = List(it.equation!!.substances.size) { "" } }
        formulaResults.clear()
        equationResults.clear()
        hintsShown.clear()
        answersShown.clear()
        sendState = SendState.Idle
    }

    companion object {
        private const val SEPARATOR = "|"

        fun factory(repository: Repository, practice: Practice, tasks: List<Task>) = viewModelFactory {
            initializer { PracticeViewModel(repository, practice, tasks) }
        }
    }
}
