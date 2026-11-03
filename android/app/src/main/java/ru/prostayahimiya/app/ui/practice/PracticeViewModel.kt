package ru.prostayahimiya.app.ui.practice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import ru.prostayahimiya.app.chem.EquationCheck
import ru.prostayahimiya.app.chem.FormulaCheck
import ru.prostayahimiya.app.chem.checkEquation
import ru.prostayahimiya.app.chem.checkFormula
import ru.prostayahimiya.app.data.AnswerSender
import ru.prostayahimiya.app.data.Content
import ru.prostayahimiya.app.data.EquationTask
import ru.prostayahimiya.app.data.FormulaTask
import ru.prostayahimiya.app.data.Practice
import ru.prostayahimiya.app.data.ProgressStore

sealed interface SendState {
    data object Idle : SendState
    data object Sending : SendState
    data object Sent : SendState
    data class Error(val message: String) : SendState
}

/**
 * Состояние экрана практики. ViewModel переживает поворот экрана,
 * а черновики ответов и решённые задания ещё и сохраняются в ProgressStore.
 */
class PracticeViewModel(private val store: ProgressStore, val practice: Practice) : ViewModel() {

    val grade = Content.gradeOf(practice.id)

    // Вопросы по теории
    val answers = mutableStateMapOf<String, String>().apply {
        practice.questions.forEach { put(it.id, store.draft(it.id)) }
    }
    var studentName by mutableStateOf(store.studentName)
        private set
    var sendState by mutableStateOf<SendState>(SendState.Idle)
        private set
    var sentAt by mutableLongStateOf(store.sentAt(practice.id))
        private set

    // Задания с автоматической проверкой
    val formulaInputs = mutableStateMapOf<String, String>()
    val formulaResults = mutableStateMapOf<String, FormulaCheck>()
    val equationInputs = mutableStateMapOf<String, List<String>>()
    val equationResults = mutableStateMapOf<String, EquationCheck>()
    /** Задания, для которых ученик открыл подсказку. */
    val hintsShown = mutableStateMapOf<String, Boolean>()
    /** Задания, для которых ученик посмотрел ответ. */
    val answersShown = mutableStateMapOf<String, Boolean>()

    fun onAnswerChange(questionId: String, text: String) {
        answers[questionId] = text
        store.saveDraft(questionId, text)
        if (sendState !is SendState.Sending) sendState = SendState.Idle
    }

    fun onNameChange(text: String) {
        studentName = text
        store.studentName = text
        if (sendState is SendState.Error) sendState = SendState.Idle
    }

    fun onFormulaChange(task: FormulaTask, text: String) {
        formulaInputs[task.id] = text
        formulaResults.remove(task.id)
    }

    fun check(task: FormulaTask) {
        val input = formulaInputs[task.id].orEmpty()
        if (input.isBlank()) return
        val result = checkFormula(input, task.answer)
        formulaResults[task.id] = result
        if (result is FormulaCheck.Correct) store.markSolved(task.id)
    }

    fun coefficients(task: EquationTask): List<String> =
        equationInputs[task.id] ?: List(task.equation.substances.size) { "" }

    fun onCoefficientChange(task: EquationTask, index: Int, text: String) {
        val digits = text.filter { it.isDigit() }.take(2)
        equationInputs[task.id] = coefficients(task).toMutableList().also { it[index] = digits }
        equationResults.remove(task.id)
    }

    fun check(task: EquationTask) {
        val result = checkEquation(task.equation, coefficients(task))
        equationResults[task.id] = result
        if (result is EquationCheck.Correct) store.markSolved(task.id)
    }

    fun toggleHint(taskId: String) {
        hintsShown[taskId] = hintsShown[taskId] != true
    }

    fun showAnswer(taskId: String) {
        answersShown[taskId] = true
    }

    fun send(solvedCount: Int) {
        if (sendState is SendState.Sending) return
        val name = studentName.trim()
        val hasAnswers = practice.questions.any { answers[it.id].orEmpty().isNotBlank() }
        when {
            name.isEmpty() -> {
                sendState = SendState.Error("Впиши своё имя и класс, чтобы учитель понял, чей это ответ.")
                return
            }
            !hasAnswers -> {
                sendState = SendState.Error("Ответь хотя бы на один вопрос.")
                return
            }
        }
        val text = buildString {
            practice.questions.forEachIndexed { i, q ->
                append(i + 1).append(". ").append(answers[q.id].orEmpty().trim().ifEmpty { "—" }).append("\n\n")
            }
            append("Задания с проверкой: решено $solvedCount из ${practice.checkableTaskIds.size}.")
        }
        sendState = SendState.Sending
        viewModelScope.launch {
            AnswerSender.send(name, text, "${grade.subject.title}, ${grade.number} класс")
                .onSuccess {
                    val now = System.currentTimeMillis()
                    store.markSent(practice.id, now)
                    sentAt = now
                    sendState = SendState.Sent
                }
                .onFailure {
                    sendState = SendState.Error("Не получилось отправить. Проверь интернет и попробуй ещё раз.")
                }
        }
    }

    /** «Начать заново»: стирает прогресс и черновики этой практики. */
    fun reset() {
        store.resetPractice(practice)
        practice.questions.forEach { answers[it.id] = "" }
        formulaInputs.clear()
        formulaResults.clear()
        equationInputs.clear()
        equationResults.clear()
        hintsShown.clear()
        answersShown.clear()
        sentAt = 0L
        sendState = SendState.Idle
    }

    companion object {
        fun factory(store: ProgressStore, practice: Practice) = viewModelFactory {
            initializer { PracticeViewModel(store, practice) }
        }
    }
}
