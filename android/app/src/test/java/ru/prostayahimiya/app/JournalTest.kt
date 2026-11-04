package ru.prostayahimiya.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.prostayahimiya.app.data.AnswerItem
import ru.prostayahimiya.app.data.JournalCell
import ru.prostayahimiya.app.data.Practice
import ru.prostayahimiya.app.data.Submission
import ru.prostayahimiya.app.data.User
import ru.prostayahimiya.app.data.buildJournal
import ru.prostayahimiya.app.data.classesOf
import ru.prostayahimiya.app.data.deadlineStatus
import ru.prostayahimiya.app.data.isLate
import ru.prostayahimiya.app.data.practiceStats
import ru.prostayahimiya.app.data.toCsv
import java.time.Instant

class JournalTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")
    private val masha = User("m1", name = "Маша Смирнова", group = "8А")
    private val vasya = User("v1", name = "Вася Новиков", group = "8А")
    private val kolya = User("k1", name = "Коля Орлов", group = "8Б")
    private val chem = Practice("p1", "Практика по химии", "chemistry", 8, published = true, deadline = "2026-11-05 20:59:00.000Z")
    private val bio = Practice("p2", "Клетка", "biology", 8, published = true, classes = listOf("8Б"))
    private val draft = Practice("p3", "Черновик", "chemistry", 8, published = false)

    @Test
    fun classesAreSortedByNumber() {
        assertEquals(listOf("5А", "8А", "8Б", "10А"), classesOf(listOf(User("1", group = "10А"), User("2", group = "8Б"), User("3", group = "5А"), User("4", group = "8А"), User("5", group = ""))))
    }

    @Test
    fun journalCellsAndCsv() {
        val subs = listOf(
            Submission("s2", "m1", "p1", grade = 4, created = "2026-11-06 10:00:00.000Z"),
            Submission("s1", "m1", "p1", grade = 0, created = "2026-11-04 10:00:00.000Z"),
        )
        val table = buildJournal("8А", listOf(masha, vasya, kolya), subs, listOf(chem, bio, draft), now)
        assertEquals(listOf("p1"), table.practices.map { it.id }) // биология только для 8Б, черновик не считается
        assertEquals(listOf("Вася Новиков", "Маша Смирнова"), table.rows.map { it.student.name })
        assertEquals(JournalCell.Missed, table.rows[0].cells[0])
        assertEquals(JournalCell.Grade(4, "s2", late = true), table.rows[1].cells[0])
        assertEquals(4.0, table.rows[1].average!!, 0.001)
        val csv = table.toCsv()
        assertTrue(csv.startsWith("﻿Ученик;Класс;Химия, 8 класс · Практика по химии;Средний балл"))
        assertTrue(csv.contains("Маша Смирнова;8А;4;4,0"))
        assertTrue(csv.contains("Вася Новиков;8А;не сдано;"))
    }

    @Test
    fun deadlinesAndLateness() {
        assertTrue(chem.deadlineStatus(submitted = false, now = now)!!.overdue)
        assertEquals(null, chem.deadlineStatus(submitted = true, now = now))
        assertTrue(Submission("s", created = "2026-11-06 10:00:00.000Z").isLate(chem))
        assertTrue(!Submission("s", created = "2026-11-04 10:00:00.000Z").isLate(chem))
    }

    @Test
    fun statsFindHardestTasks() {
        val answers = { right: Boolean -> listOf(AnswerItem("formula", "хлорид алюминия", "AlCl3", correct = right, attempts = 2, task = "t1")) }
        val subs = listOf(
            Submission("a", "m1", "p1", answers = answers(true), grade = 5),
            Submission("b", "v1", "p1", answers = answers(false), created = "2026-11-06 10:00:00.000Z"),
        )
        val stats = practiceStats(chem, listOf(masha, vasya), subs)
        assertEquals(2, stats.submitted)
        assertEquals(1, stats.late)
        assertEquals(5.0, stats.averageGrade!!, 0.001)
        assertEquals("хлорид алюминия", stats.hardest.single().prompt)
        assertEquals(1, stats.hardest.single().right)
        assertEquals(2.0, stats.hardest.single().averageAttempts, 0.001)
    }
}
