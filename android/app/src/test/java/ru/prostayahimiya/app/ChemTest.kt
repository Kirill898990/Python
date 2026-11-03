package ru.prostayahimiya.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.prostayahimiya.app.chem.AtomBalance
import ru.prostayahimiya.app.chem.Chem
import ru.prostayahimiya.app.chem.Equation
import ru.prostayahimiya.app.chem.EquationCheck
import ru.prostayahimiya.app.chem.FormulaCheck
import ru.prostayahimiya.app.chem.checkEquation
import ru.prostayahimiya.app.chem.checkFormula
import ru.prostayahimiya.app.chem.validateTask
import ru.prostayahimiya.app.data.parseCoefficients
import org.junit.Assume.assumeTrue
import java.io.File

class ChemTest {

    private fun atoms(formula: String) = (Chem.parse(formula) as Chem.ParseResult.Ok).atoms

    @Test
    fun parsesFormulasWithBrackets() {
        assertEquals(mapOf("Fe" to 1, "O" to 3, "H" to 3), atoms("Fe(OH)3"))
        assertEquals(mapOf("Al" to 2, "S" to 3, "O" to 12), atoms("Al2(SO4)3"))
        assertEquals(mapOf("H" to 2, "O" to 1), atoms("H2O"))
    }

    @Test
    fun understandsSubscriptsSpacesAndRussianLetters() {
        assertEquals(atoms("H2O"), atoms("H₂O"))
        assertEquals(atoms("H2O"), atoms(" H2 O "))
        assertEquals("H2O", Chem.normalize("Н2О")) // русские Н и О
    }

    @Test
    fun explainsMistakes() {
        assertTrue(Chem.parse("nacl") is Chem.ParseResult.Error)
        assertTrue(Chem.parse("Xx2") is Chem.ParseResult.Error)
        assertTrue(Chem.parse("2H2O") is Chem.ParseResult.Error)
        assertTrue(Chem.parse("Ca(OH2") is Chem.ParseResult.Error)
        assertTrue(Chem.parse("CaOH)2") is Chem.ParseResult.Error)
        assertTrue(Chem.parse("H0") is Chem.ParseResult.Error)
        assertTrue(Chem.parse("") is Chem.ParseResult.Error)
    }

    @Test
    fun checksFormulaAnswers() {
        assertEquals(FormulaCheck.Correct, checkFormula("N2O5", "N2O5"))
        assertEquals(FormulaCheck.Correct, checkFormula("N₂O₅", "N2O5"))
        assertTrue(checkFormula("CaO2H2", "Ca(OH)2") is FormulaCheck.Hint)
        assertTrue(checkFormula("NO2", "N2O5") is FormulaCheck.Wrong)
        assertTrue(checkFormula("NaCl", "AlCl3") is FormulaCheck.Wrong)
        assertTrue(checkFormula("alcl3", "AlCl3") is FormulaCheck.Hint)
    }

    @Test
    fun checksEquations() {
        val eq = Equation(listOf("Al", "O2"), listOf("Al2O3"))
        assertEquals(EquationCheck.Correct, checkEquation(eq, listOf("4", "3", "2")))
        assertEquals(EquationCheck.NotReduced(2), checkEquation(eq, listOf("8", "6", "4")))
        assertEquals(
            EquationCheck.Unbalanced(listOf(AtomBalance("Al", 1, 2), AtomBalance("O", 2, 3))),
            checkEquation(eq, listOf("", "", "")),
        )
        assertTrue(checkEquation(eq, listOf("0", "3", "2")) is EquationCheck.Invalid)
        assertEquals("4Al + 3O₂ → 2Al₂O₃", eq.pretty(listOf(4, 3, 2)))
    }

    @Test
    fun parsesTeacherEquations() {
        val eq = Equation.parse("Al + O2 = Al2O3")!!
        assertEquals(listOf("Al", "O2"), eq.reactants)
        assertEquals(listOf("Al2O3"), eq.products)
        assertEquals(eq, Equation.parse("Al+O2 -> Al2O3"))
        assertEquals(eq, Equation.parse("Al + O₂ → Al₂O₃"))
        assertEquals(null, Equation.parse("Al + O2"))
        assertEquals(null, Equation.parse("Al + Xx = AlXx"))
    }

    @Test
    fun validatesTeacherTasks() {
        assertEquals(null, validateTask("question", "Что такое валентность?", ""))
        assertTrue(validateTask("question", " ", "") != null)
        assertEquals(null, validateTask("formula", "вода", "H2O"))
        assertTrue(validateTask("formula", "вода", "h2o") != null)
        assertEquals(null, validateTask("equation", "Al + O2 = Al2O3", "4 3 2"))
        assertEquals(null, validateTask("equation", "Al + O2 = Al2O3", "4, 3, 2"))
        assertTrue(validateTask("equation", "Al + O2 = Al2O3", "4 3")!!.contains("сколько веществ"))
        assertTrue(validateTask("equation", "Al + O2 = Al2O3", "8 6 4")!!.contains("сократить"))
        assertTrue(validateTask("equation", "Al + O2 = Al2O3", "1 1 1")!!.contains("не уравнивают"))
        assertTrue(validateTask("equation", "Al + O2", "1 1")!!.contains("Al + O2 = Al2O3"))
    }

    @Test
    fun parsesCoefficients() {
        assertEquals(listOf(4, 3, 2), parseCoefficients("4 3 2"))
        assertEquals(listOf(2, 1), parseCoefficients(" 2,1 "))
        assertEquals(null, parseCoefficients("2 x"))
        assertEquals(null, parseCoefficients(""))
    }

    /**
     * Стартовые задания лежат в миграции сервера (server/pb_migrations). Проверяем, что в них нет ошибок.
     * Если папки server рядом нет, тест пропускается.
     */
    @Test
    fun serverSeedTasksAreCorrect() {
        val dir = File("../../server/pb_migrations")
        assumeTrue(dir.isDirectory)
        val row = Regex("""\["(question|formula|equation)", "((?:[^"\\]|\\.)*)", "((?:[^"\\]|\\.)*)", """)
        var checked = 0
        dir.listFiles { f -> f.extension == "js" }!!.forEach { file ->
            row.findAll(file.readText()).forEach { m ->
                val (type, text, answer) = m.destructured
                assertEquals("Ошибка в задании «$text»", null, validateTask(type, text, answer))
                checked++
            }
        }
        assertTrue("Не найдено ни одного задания", checked > 0)
    }
}
