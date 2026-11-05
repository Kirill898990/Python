package ru.prostayahimiya.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.prostayahimiya.app.chem.Chem
import ru.prostayahimiya.app.chem.Equation
import ru.prostayahimiya.app.chem.Trainer
import ru.prostayahimiya.app.chem.TrainerData
import ru.prostayahimiya.app.chem.TrainerTask
import ru.prostayahimiya.app.chem.TrainerTopic
import kotlin.random.Random

/** Задания тренажёра: все формулы разбираются, массы считаются, уравнения уравнены (как server/tools/check_trainer.js). */
class TrainerTest {
    @Test fun allFormulasAreRight() {
        TrainerData.formulas.forEach { (name, formula, valences) ->
            val r = Trainer.check(TrainerTask.Formula(name, formula, valences), formula)
            assertTrue("$name $formula: ${r.message}", r.isCorrect)
        }
    }

    @Test fun allMolarMassesWork() {
        TrainerData.molar.forEach { assertTrue(it, Chem.molarMass(it) is Chem.MolarResult.Ok) }
        assertEquals("M(H₂SO₄) = 2·1 + 32 + 4·16 = 98 г/моль", Trainer.molarExplain("H2SO4"))
    }

    @Test fun allEquationsAreBalanced() {
        TrainerData.equations.forEach { (text, coefficients) ->
            assertNotNull(text, Equation.parse(text))
            val eq = Equation.parse(text)!!
            val r = Trainer.check(TrainerTask.Balance(eq, ""), "", coefficients.map { it.toString() })
            assertTrue("$text $coefficients: ${r.message}", r.isCorrect)
        }
    }

    @Test fun wrongAnswersAreExplained() {
        val al = TrainerTask.Formula("оксид алюминия", "Al2O3", "")
        assertEquals("wrong", Trainer.check(al, "AlO").kind)
        assertEquals("hint", Trainer.check(al, "O3Al2").kind)
        val classes = TrainerTask.Classify("H2SO4", 2, "")
        assertTrue(Trainer.check(classes, "2").isCorrect)
        assertEquals("wrong", Trainer.check(classes, "4").kind)
    }

    @Test fun noRepeatsInARow() {
        val trainer = Trainer(Random(1))
        val seen = List(10) { trainer.next(TrainerTopic.FORMULA) }
        assertEquals(10, seen.toSet().size)
    }
}
