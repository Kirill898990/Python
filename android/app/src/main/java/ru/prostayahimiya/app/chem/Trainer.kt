package ru.prostayahimiya.app.chem

import kotlin.math.abs
import kotlin.random.Random

/*
 * Тренажёр: бесконечные задания, которые приложение придумывает само, и их проверка прямо на телефоне.
 * Учителю ничего не отправляется. Задания — из TrainerData (его делает server/tools/make_trainer.py),
 * проверка и тексты подсказок — те же, что у заданий учителя. Так же работает тренажёр на сайте.
 */

enum class TrainerTopic(val title: String) {
    MIX("🎲 Всё вперемешку"),
    FORMULA("🧪 Формулы"),
    MOLAR("⚖️ Молярная масса"),
    EQUATION("⚗️ Уравнения"),
    CLASSES("🗂️ Классы веществ"),
    BIOLOGY("🧬 Биология"),
    ;

    val label: String
        get() = when (this) {
            FORMULA -> "Составь формулу по валентности"
            MOLAR -> "Вычисли молярную массу"
            EQUATION -> "Расставь коэффициенты"
            CLASSES -> "Определи класс вещества"
            BIOLOGY -> "Ответь на вопрос по биологии"
            MIX -> ""
        }
}

/** Задание тренажёра. [explain] — правильный ответ с объяснением (по кнопке «Показать ответ»). */
sealed interface TrainerTask {
    val topic: TrainerTopic
    val explain: String

    data class Formula(val name: String, val formula: String, override val explain: String) : TrainerTask {
        override val topic get() = TrainerTopic.FORMULA
    }

    data class Molar(val formula: String, override val explain: String) : TrainerTask {
        override val topic get() = TrainerTopic.MOLAR
    }

    data class Balance(val equation: Equation, override val explain: String) : TrainerTask {
        override val topic get() = TrainerTopic.EQUATION
    }

    /** Вопрос по биологии с вариантами ответа. [answer] — номер верного варианта (с 1). */
    data class Quiz(val question: String, val options: List<String>, val answer: Int, override val explain: String) : TrainerTask {
        override val topic get() = TrainerTopic.BIOLOGY
    }

    data class Classify(val formula: String, val answer: Int, override val explain: String) : TrainerTask {
        override val topic get() = TrainerTopic.CLASSES
        val question: String get() = "К какому классу веществ относится ${Chem.pretty(Chem.normalize(formula))}?"
        val options: List<String> get() = TrainerData.classNames
    }
}

/** correct — верно, hint — почти или не так записано, wrong — неверно. */
data class TrainerResult(val kind: String, val message: String) {
    val isCorrect: Boolean get() = kind == "correct"
}

class Trainer(private val random: Random = Random.Default) {
    private val recent = ArrayDeque<String>()

    private fun <T> pick(kind: String, list: List<T>): T {
        var i = random.nextInt(list.size)
        var tries = 0
        while ("$kind$i" in recent && tries++ < 20) i = random.nextInt(list.size)
        recent.addLast("$kind$i")
        if (recent.size > 12) recent.removeFirst()
        return list[i]
    }

    fun next(topic: TrainerTopic): TrainerTask {
        val kind = if (topic == TrainerTopic.MIX) TrainerTopic.entries.filter { it != TrainerTopic.MIX }.random(random) else topic
        return when (kind) {
            TrainerTopic.FORMULA -> {
                val (name, formula, valences) = pick("f", TrainerData.formulas)
                TrainerTask.Formula(name, formula, "${Chem.pretty(formula)} ($valences)")
            }
            TrainerTopic.MOLAR -> pick("m", TrainerData.molar).let { TrainerTask.Molar(it, molarExplain(it)) }
            TrainerTopic.EQUATION -> {
                val (text, coefficients) = pick("e", TrainerData.equations)
                val equation = Equation.parse(text)!!
                TrainerTask.Balance(equation, equation.pretty(coefficients))
            }
            TrainerTopic.BIOLOGY -> {
                // Первый вариант в данных — верный; показываем варианты в случайном порядке.
                val (question, options, explain) = pick("b", TrainerData.biology)
                val order = options.indices.shuffled(random)
                TrainerTask.Quiz(question, order.map { options[it] }, order.indexOf(0) + 1, "${options[0]}. $explain")
            }
            else -> {
                val (formula, cls) = pick("c", TrainerData.classes)
                TrainerTask.Classify(formula, cls, TrainerData.classHints[cls - 1])
            }
        }
    }

    companion object {
        /** «M(H₂SO₄) = 2·1 + 32 + 4·16 = 98 г/моль» */
        fun molarExplain(formula: String): String {
            val value = (Chem.molarMass(formula) as? Chem.MolarResult.Ok)?.value ?: 0.0
            val name = "M(${Chem.pretty(Chem.normalize(formula))})"
            val atoms = (Chem.parse(Chem.normalize(formula)) as? Chem.ParseResult.Ok)?.atoms
                ?: return "$name = ${Chem.formatNumber(value)} г/моль"
            val parts = atoms.map { (el, n) ->
                val a = Chem.formatNumber(Chem.atomicMass(el) ?: 0.0)
                if (n > 1) "$n·$a" else a
            }
            return "$name = ${parts.joinToString(" + ")} = ${Chem.formatNumber(value)} г/моль"
        }

        /** Проверка ответа. Для уравнения — коэффициенты, для теста — номер ответа. Тексты те же, что на сервере. */
        fun check(task: TrainerTask, input: String, coefficients: List<String> = emptyList()): TrainerResult = when (task) {
            is TrainerTask.Formula -> when {
                input.isBlank() -> TrainerResult("hint", "Впиши формулу")
                else -> when (val r = checkFormula(input, Chem.normalize(task.formula))) {
                    FormulaCheck.Correct -> TrainerResult("correct", "Верно! ${Chem.pretty(Chem.normalize(input))}")
                    is FormulaCheck.Hint -> TrainerResult("hint", r.message)
                    is FormulaCheck.Wrong -> TrainerResult("wrong", r.message)
                }
            }
            is TrainerTask.Molar -> {
                val right = (Chem.molarMass(task.formula) as Chem.MolarResult.Ok).value
                val n = input.trim().replace(',', '.').toDoubleOrNull()
                when {
                    n == null -> TrainerResult("hint", "Впиши число, например 98")
                    abs(n - right) <= maxOf(0.5, right * 0.003) -> TrainerResult("correct", "Верно! ${task.explain}")
                    else -> TrainerResult("wrong", "Неверно. Сложи атомные массы всех атомов: Ar элемента × число атомов")
                }
            }
            is TrainerTask.Balance -> when (val r = checkEquation(task.equation, coefficients)) {
                EquationCheck.Correct -> TrainerResult(
                    "correct",
                    "Верно! " + task.equation.pretty(coefficients.map { it.trim().toIntOrNull() ?: 1 }),
                )
                is EquationCheck.NotReduced -> TrainerResult("hint", "Атомов поровну, но все коэффициенты делятся на ${r.divisor}. Нужны самые маленькие.")
                is EquationCheck.Unbalanced -> TrainerResult(
                    "wrong",
                    "Пока не сходится:\n" + r.mismatches.joinToString("\n") { "• ${it.element}: слева ${it.left}, справа ${it.right}" },
                )
                is EquationCheck.Invalid -> TrainerResult("hint", r.message)
            }
            is TrainerTask.Quiz -> when (input.toIntOrNull()) {
                null -> TrainerResult("hint", "Выбери ответ")
                task.answer -> TrainerResult("correct", "Верно! ${task.explain}")
                else -> TrainerResult("wrong", "Неверно. Подумай ещё")
            }
            is TrainerTask.Classify -> when (input.toIntOrNull()) {
                null -> TrainerResult("hint", "Выбери ответ")
                task.answer -> TrainerResult("correct", "Верно! ${task.explain}")
                else -> TrainerResult("wrong", "Неверно. Подумай ещё")
            }
        }
    }
}
