package ru.prostayahimiya.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.prostayahimiya.app.chem.Chem
import ru.prostayahimiya.app.chem.joinBlanks
import ru.prostayahimiya.app.chem.parseChoice
import ru.prostayahimiya.app.chem.splitBlanks
import ru.prostayahimiya.app.chem.validateTask

/** Новые виды заданий: молярная масса, пропуски, тест. Ожидания совпадают с chem.js на сайте. */
class TaskKindsTest {

    private fun molar(formula: String) = (Chem.molarMass(formula) as Chem.MolarResult.Ok).value

    @Test
    fun molarMassUsesSchoolAtomicMasses() {
        assertEquals(98.0, molar("H2SO4"), 0.001)
        assertEquals(36.5, molar("HCl"), 0.001)
        assertEquals(310.0, molar("Ca3(PO4)2"), 0.001)
        assertEquals(250.0, molar("CuSO4·5H2O"), 0.001)
        assertEquals(250.0, molar("CuSO4*5H2O"), 0.001)
        assertTrue(Chem.molarMass("H2Xx") is Chem.MolarResult.Error)
        assertEquals("98", Chem.formatNumber(98.0))
        assertEquals("36,5", Chem.formatNumber(36.5))
    }

    @Test
    fun prettyKeepsHydrateCoefficient() {
        assertEquals("CuSO₄·5H₂O", Chem.pretty("CuSO4·5H2O"))
        assertEquals("Fe(OH)₃", Chem.pretty("Fe(OH)3"))
        assertEquals("CO₂", Chem.prettyIfFormula("CO2"))
        assertEquals("водород", Chem.prettyIfFormula("водород"))
        assertEquals("3.1", Chem.prettyIfFormula("3.1"))
    }

    @Test
    fun blanksSplitAndJoin() {
        val (text, answer) = splitBlanks("Вода состоит из [водорода / H] и [кислорода].")
        assertEquals("Вода состоит из ___ и ___.", text)
        assertEquals("водорода/H|кислорода", answer)
        assertEquals("из [водорода/H] и [кислорода]", joinBlanks("из ___ и ___", answer))
    }

    @Test
    fun choiceNumbers() {
        assertEquals(listOf(1, 3), parseChoice("3, 1"))
        assertEquals(listOf(2), parseChoice("2"))
        assertEquals(emptyList<Int>(), parseChoice(""))
    }

    @Test
    fun validatesNewTaskTypes() {
        assertNull(validateTask("molar", "H2SO4", ""))
        assertTrue(validateTask("molar", "H2Xx", "")!!.contains("Xx"))
        val (text, answer) = splitBlanks("Вода из [водорода] и [кислорода]")
        assertNull(validateTask("fill", text, answer))
        assertTrue(validateTask("fill", "Нет пропусков", "") != null)
        assertNull(validateTask("choice", "Вопрос", "1,3", listOf("CO2", "NaCl", "CaO")))
        assertTrue(validateTask("choice", "Вопрос", "", listOf("CO2", "NaCl"))!!.contains("галочкой"))
        assertTrue(validateTask("choice", "Вопрос", "1", listOf("CO2"))!!.contains("два варианта"))
    }
}
