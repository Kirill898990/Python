package ru.prostayahimiya.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Хранит на телефоне прогресс ученика: решённые задания, черновики ответов и имя.
 * Данные остаются только на устройстве.
 */
class ProgressStore(context: Context) {

    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    private val _solved = MutableStateFlow(prefs.getStringSet(KEY_SOLVED, emptySet()).orEmpty().toSet())

    /** id решённых заданий. */
    val solved: StateFlow<Set<String>> = _solved.asStateFlow()

    fun markSolved(taskId: String) {
        if (taskId in _solved.value) return
        _solved.value += taskId
        prefs.edit { putStringSet(KEY_SOLVED, _solved.value) }
    }

    fun resetPractice(practice: Practice) {
        _solved.value -= practice.checkableTaskIds.toSet()
        prefs.edit {
            putStringSet(KEY_SOLVED, _solved.value)
            practice.questions.forEach { remove(KEY_DRAFT + it.id) }
            remove(KEY_SENT + practice.id)
        }
    }

    var studentName: String
        get() = prefs.getString(KEY_NAME, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_NAME, value) }

    fun draft(questionId: String): String = prefs.getString(KEY_DRAFT + questionId, "").orEmpty()

    fun saveDraft(questionId: String, text: String) = prefs.edit { putString(KEY_DRAFT + questionId, text) }

    /** Когда ответы по практике последний раз отправлены учителю (0, если ещё не отправлялись). */
    fun sentAt(practiceId: String): Long = prefs.getLong(KEY_SENT + practiceId, 0L)

    fun markSent(practiceId: String, time: Long) = prefs.edit { putLong(KEY_SENT + practiceId, time) }

    private companion object {
        const val KEY_SOLVED = "solved"
        const val KEY_NAME = "student_name"
        const val KEY_DRAFT = "draft_"
        const val KEY_SENT = "sent_"
    }
}
