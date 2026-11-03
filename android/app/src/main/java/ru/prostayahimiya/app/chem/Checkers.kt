package ru.prostayahimiya.app.chem

/** Результат проверки формулы, которую ввёл ученик. */
sealed interface FormulaCheck {
    data object Correct : FormulaCheck
    data class Hint(val message: String) : FormulaCheck
    data class Wrong(val message: String) : FormulaCheck
}

fun checkFormula(input: String, answer: String): FormulaCheck {
    val typed = Chem.normalize(input)
    if (typed == answer) return FormulaCheck.Correct

    val typedAtoms = when (val r = Chem.parse(typed)) {
        is Chem.ParseResult.Error -> return FormulaCheck.Hint(r.message)
        is Chem.ParseResult.Ok -> r.atoms
    }
    val answerAtoms = (Chem.parse(answer) as Chem.ParseResult.Ok).atoms

    return when {
        typedAtoms == answerAtoms -> FormulaCheck.Hint(
            "Почти! Атомы подобраны верно, но формулу принято записывать по-другому.",
        )
        typedAtoms.keys == answerAtoms.keys -> FormulaCheck.Wrong(
            "Элементы верные, а индексы нет. Проверь валентности.",
        )
        else -> FormulaCheck.Wrong("Проверь, из каких элементов состоит вещество.")
    }
}

/** Уравнение реакции без коэффициентов: «Al + O2 → Al2O3». */
data class Equation(val reactants: List<String>, val products: List<String>) {
    val substances: List<String> get() = reactants + products

    /** Запись с коэффициентами: «4Al + 3O₂ → 2Al₂O₃». Коэффициент 1 не пишется. */
    fun pretty(coefficients: List<Int>? = null): String {
        fun side(list: List<String>, offset: Int) = list.indices.joinToString(" + ") { i ->
            val c = coefficients?.get(offset + i) ?: 1
            (if (c == 1) "" else c.toString()) + Chem.pretty(list[i])
        }
        return side(reactants, 0) + " → " + side(products, reactants.size)
    }
}

/** Сколько атомов элемента слева и справа. */
data class AtomBalance(val element: String, val left: Int, val right: Int)

sealed interface EquationCheck {
    data object Correct : EquationCheck
    data class NotReduced(val divisor: Int) : EquationCheck
    data class Unbalanced(val mismatches: List<AtomBalance>) : EquationCheck
    data class Invalid(val message: String) : EquationCheck
}

/**
 * Проверяет коэффициенты, которые ввёл ученик. Пустое поле означает коэффициент 1.
 * Правильным считается любой набор, при котором атомов слева и справа поровну,
 * а не только один заранее записанный ответ.
 */
fun checkEquation(equation: Equation, inputs: List<String>): EquationCheck {
    val coefficients = inputs.map { raw ->
        val text = raw.trim()
        if (text.isEmpty()) return@map 1
        val n = text.toIntOrNull()
        if (n == null || n !in 1..99) return EquationCheck.Invalid("Коэффициенты — целые числа от 1 до 99")
        n
    }
    val balance = balanceOf(equation, coefficients)
    val mismatches = balance.filter { it.left != it.right }
    if (mismatches.isNotEmpty()) return EquationCheck.Unbalanced(mismatches)
    val divisor = coefficients.reduce(::gcd)
    return if (divisor > 1) EquationCheck.NotReduced(divisor) else EquationCheck.Correct
}

fun balanceOf(equation: Equation, coefficients: List<Int>): List<AtomBalance> {
    val left = LinkedHashMap<String, Int>()
    val right = LinkedHashMap<String, Int>()
    equation.substances.forEachIndexed { i, formula ->
        val atoms = (Chem.parse(formula) as Chem.ParseResult.Ok).atoms
        val target = if (i < equation.reactants.size) left else right
        atoms.forEach { (el, n) -> target.merge(el, n * coefficients[i], Int::plus) }
    }
    return (left.keys + right.keys).map { AtomBalance(it, left[it] ?: 0, right[it] ?: 0) }
}

private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
