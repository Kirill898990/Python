package ru.prostayahimiya.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.prostayahimiya.app.data.Practice
import ru.prostayahimiya.app.data.User
import ru.prostayahimiya.app.data.buildJournal
import ru.prostayahimiya.app.data.gradeOf

/** Прогресс и журнал считают только практики своей параллели (или явно заданные классу). */
class GradeProgressTest {
    private val bio5 = Practice("b5", "Клетка", "biology", 5, published = true)
    private val chem9 = Practice("c9", "Растворы", "chemistry", 9, published = true)
    private val extra = Practice("x", "Олимпиада", "chemistry", 11, published = true, classes = listOf("9А"))

    @Test
    fun gradeFromGroup() {
        assertEquals(9, gradeOf("9А"))
        assertEquals(10, gradeOf("10Б"))
        assertEquals(0, gradeOf("А"))
        assertEquals(0, gradeOf(""))
    }

    @Test
    fun assignedPractices() {
        assertFalse("5 класс не входит в прогресс 9А", bio5.isAssignedTo("9А"))
        assertTrue(bio5.isAssignedTo("5Б"))
        assertTrue(chem9.isAssignedTo("9А"))
        assertTrue("явно задано 9А", extra.isAssignedTo("9А"))
        assertFalse(extra.isAssignedTo("11А"))
        assertTrue("класс без цифры — считаем всё, как раньше", bio5.isAssignedTo("А"))
    }

    @Test
    fun journalShowsOnlyOwnGrade() {
        val table = buildJournal("9А", listOf(User("u", name = "Петя", group = "9А")), emptyList(), listOf(bio5, chem9, extra))
        assertEquals(listOf("c9", "x"), table.practices.map { it.id })
    }
}

class NextGroupTest {
    @org.junit.Test fun nextYear() {
        org.junit.Assert.assertEquals("9А", ru.prostayahimiya.app.data.nextGroup("8А"))
        org.junit.Assert.assertEquals("11Б", ru.prostayahimiya.app.data.nextGroup("10Б"))
        org.junit.Assert.assertEquals(null, ru.prostayahimiya.app.data.nextGroup("11А"))
        org.junit.Assert.assertEquals("Кружок", ru.prostayahimiya.app.data.nextGroup("Кружок"))
        org.junit.Assert.assertEquals("6", ru.prostayahimiya.app.data.nextGroup("5"))
    }
}
