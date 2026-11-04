package ru.prostayahimiya.app

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.prostayahimiya.app.data.ContentData
import ru.prostayahimiya.app.data.Task

/** Вариант ученика в приложении должен совпадать с сайтом (числа посчитаны функцией variantFor из api.js). */
class VariantTest {
    private fun check(userId: String, practiceId: String, count: Int, expected: Int) {
        val tasks = (1..count).map { Task(id = "t$it", practice = practiceId, variant = it) }
        assertEquals("$userId / $practiceId / $count", expected, ContentData(tasks = tasks).variantFor(practiceId, userId))
    }

    @Test
    fun sameAsSite() {
        check("govis1q0htz2b63", "k7q2xm8a1b2c3d4", 2, 2)
        check("govis1q0htz2b63", "k7q2xm8a1b2c3d4", 3, 3)
        check("govis1q0htz2b63", "k7q2xm8a1b2c3d4", 4, 2)
        check("abc123def456ghi", "p1", 2, 2)
        check("abc123def456ghi", "p1", 3, 2)
        check("abc123def456ghi", "p1", 4, 2)
        check("zzzzzzzzzzzzzzz", "yyyyyyyyyyyyyyy", 2, 1)
        check("zzzzzzzzzzzzzzz", "yyyyyyyyyyyyyyy", 3, 3)
        check("zzzzzzzzzzzzzzz", "yyyyyyyyyyyyyyy", 4, 1)
    }

    @Test
    fun tasksForVariant() {
        val data = ContentData(tasks = listOf(Task("a", "p", variant = 0), Task("b", "p", variant = 1), Task("c", "p", variant = 2)))
        assertEquals(listOf("a", "c"), data.tasksForVariant("p", 2).map { it.id })
        assertEquals(0, ContentData(tasks = listOf(Task("a", "p"))).variantFor("p", "u"))
    }
}
