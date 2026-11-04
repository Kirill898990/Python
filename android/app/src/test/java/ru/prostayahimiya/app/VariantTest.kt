package ru.prostayahimiya.app

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.prostayahimiya.app.data.ContentData
import ru.prostayahimiya.app.data.Task
import ru.prostayahimiya.app.data.User

/** Варианты раздаются по очереди внутри класса в порядке регистрации — так же, как считает сервер. */
class VariantTest {
    private val tasks = listOf(Task("a", "p", variant = 0), Task("b", "p", variant = 1), Task("c", "p", variant = 2))

    @Test
    fun teacherSeesVariantsInTurn() {
        val students = listOf(
            User("m", name = "Маша", group = "8А", created = "2026-11-01 10:00:00.000Z"),
            User("p", name = "Петя", group = "8А", created = "2026-11-01 10:05:00.000Z"),
            User("v", name = "Вася", group = "8А", created = "2026-11-02 09:00:00.000Z"),
            User("k", name = "Коля", group = "8Б", created = "2026-11-01 11:00:00.000Z"),
        )
        val variants = ContentData(tasks = tasks).variantsOf("p", students.shuffled())
        assertEquals(mapOf("m" to 1, "p" to 2, "v" to 1, "k" to 1), variants)
    }

    @Test
    fun studentVariantComesFromServer() {
        assertEquals(2, ContentData(tasks = tasks, variants = mapOf("p" to 2)).variantFor("p"))
        assertEquals(1, ContentData(tasks = tasks).variantFor("p")) // сервер ещё не ответил
        assertEquals(1, ContentData(tasks = tasks, variants = mapOf("p" to 7)).variantFor("p")) // вариант удалили
        assertEquals(0, ContentData(tasks = listOf(Task("a", "p"))).variantFor("p"))
    }

    @Test
    fun tasksForVariant() {
        assertEquals(listOf("a", "c"), ContentData(tasks = tasks).tasksForVariant("p", 2).map { it.id })
    }
}
