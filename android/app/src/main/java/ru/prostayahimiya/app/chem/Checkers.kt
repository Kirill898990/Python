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

    companion object {
        private val ARROW = Regex("→|->|=>|=")

        /**
         * Разбирает запись учителя «Al + O2 = Al2O3» (вместо «=» можно «->» или «→»).
         * Возвращает null, если записано не так или в формуле ошибка.
         */
        fun parse(text: String): Equation? {
            val sides = text.split(ARROW)
            if (sides.size != 2) return null
            fun side(s: String) = s.split('+').map { Chem.normalize(it) }
            val left = side(sides[0])
            val right = side(sides[1])
            val all = left + right
            if (all.any { it.isEmpty() || Chem.parse(it) !is Chem.ParseResult.Ok }) return null
            return Equation(left, right)
        }
    }

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

/**
 * Проверяет задание, которое учитель создаёт в панели учителя.
 * Возвращает текст ошибки или null, если всё правильно.
 */
fun validateTask(type: String, text: String, answer: String): String? {
    if (text.isBlank()) {
        return when (type) {
            "formula" -> "Впиши название вещества"
            "equation" -> "Впиши уравнение"
            else -> "Впиши текст вопроса"
        }
    }
    return when (type) {
        "formula" -> when (val r = Chem.parse(answer)) {
            is Chem.ParseResult.Error -> if (answer.isBlank()) "Впиши правильную формулу" else "Формула: ${r.message}"
            is Chem.ParseResult.Ok -> null
        }
        "equation" -> validateEquation(text, answer)
        else -> null
    }
}

private fun validateEquation(text: String, answer: String): String? {
    val sides = text.split(Regex("→|->|=>|="))
    if (sides.size != 2) return "Запиши уравнение так: Al + O2 = Al2O3"
    for (formula in sides.flatMap { it.split('+') }) {
        val r = Chem.parse(formula)
        if (r is Chem.ParseResult.Error) return "«${formula.trim()}»: ${r.message}"
    }
    val equation = Equation.parse(text) ?: return "Запиши уравнение так: Al + O2 = Al2O3"
    val parts = answer.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
    if (parts.size != equation.substances.size) {
        return "Нужно столько коэффициентов, сколько веществ в уравнении (${equation.substances.size}), через пробел"
    }
    return when (val r = checkEquation(equation, parts)) {
        EquationCheck.Correct -> null
        is EquationCheck.NotReduced -> "Коэффициенты можно сократить в ${r.divisor} раза"
        is EquationCheck.Invalid -> r.message
        is EquationCheck.Unbalanced -> "Коэффициенты не уравнивают реакцию: " +
            r.mismatches.joinToString("; ") { "${it.element} слева ${it.left}, справа ${it.right}" }
    }
}
