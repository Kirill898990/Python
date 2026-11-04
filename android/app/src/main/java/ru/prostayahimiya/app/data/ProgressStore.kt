package ru.prostayahimiya.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Хранит на телефоне прогресс ученика: решённые задания и черновики ответов.
 * У каждого аккаунта свой прогресс, даже если на одном телефоне входят разные ученики.
 */
class ProgressStore(context: Context, userId: String) {

    private val prefs = context.getSharedPreferences("progress_$userId", Context.MODE_PRIVATE)

    private val _solved = MutableStateFlow(prefs.getStringSet(KEY_SOLVED, emptySet()).orEmpty().toSet())

    /** id решённых заданий. */
    val solved: StateFlow<Set<String>> = _solved.asStateFlow()

    fun markSolved(taskId: String) {
        if (taskId in _solved.value) return
        _solved.value += taskId
        prefs.edit { putStringSet(KEY_SOLVED, _solved.value) }
    }

    fun reset(taskIds: Collection<String>) {
        _solved.value -= taskIds.toSet()
        prefs.edit {
            putStringSet(KEY_SOLVED, _solved.value)
            taskIds.forEach {
                remove(KEY_DRAFT + it)
                remove(KEY_ATTEMPTS + it)
            }
        }
    }

    /** Сколько раз ученик проверял задание. */
    fun attempts(taskId: String): Int = prefs.getInt(KEY_ATTEMPTS + taskId, 0)

    fun addAttempt(taskId: String) = prefs.edit { putInt(KEY_ATTEMPTS + taskId, attempts(taskId) + 1) }

    fun draft(taskId: String): String = prefs.getString(KEY_DRAFT + taskId, "").orEmpty()

    fun saveDraft(taskId: String, text: String) = prefs.edit { putString(KEY_DRAFT + taskId, text) }

    private companion object {
        const val KEY_SOLVED = "solved"
        const val KEY_DRAFT = "draft_"
        const val KEY_ATTEMPTS = "attempts_"
    }
}
