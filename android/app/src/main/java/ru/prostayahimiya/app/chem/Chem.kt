package ru.prostayahimiya.app.chem

/**
 * Химические формулы: разбор, подсчёт атомов и красивая запись с нижними индексами.
 * Здесь нет ничего от Android, поэтому этот код проверяется обычными тестами (папка src/test).
 */
object Chem {

    /** Все химические элементы таблицы Менделеева. */
    private val ELEMENTS: Set<String> = (
        "H He Li Be B C N O F Ne Na Mg Al Si P S Cl Ar K Ca Sc Ti V Cr Mn Fe Co Ni Cu Zn " +
            "Ga Ge As Se Br Kr Rb Sr Y Zr Nb Mo Tc Ru Rh Pd Ag Cd In Sn Sb Te I Xe Cs Ba " +
            "La Ce Pr Nd Pm Sm Eu Gd Tb Dy Ho Er Tm Yb Lu Hf Ta W Re Os Ir Pt Au Hg Tl Pb " +
            "Bi Po At Rn Fr Ra Ac Th Pa U Np Pu Am Cm Bk Cf Es Fm Md No Lr Rf Db Sg Bh Hs " +
            "Mt Ds Rg Cn Nh Fl Mc Lv Ts Og"
        ).split(" ").toSet()

    private const val SUBSCRIPTS = "₀₁₂₃₄₅₆₇₈₉"

    /**
     * Русские буквы, которые выглядят как латинские. Ученики часто пишут формулы
     * в русской раскладке («Н2О» вместо «H2O»), поэтому заменяем их сами.
     */
    private val LOOKALIKES = mapOf(
        'А' to 'A', 'В' to 'B', 'Е' to 'E', 'К' to 'K', 'М' to 'M', 'Н' to 'H', 'О' to 'O',
        'Р' to 'P', 'С' to 'C', 'Т' to 'T', 'Х' to 'X', 'а' to 'a', 'е' to 'e', 'о' to 'o',
        'р' to 'p', 'с' to 'c', 'у' to 'y', 'х' to 'x',
    )

    /** «Fe(OH)3» → «Fe(OH)₃». */
    fun pretty(formula: String): String =
        formula.map { if (it in '0'..'9') SUBSCRIPTS[it - '0'] else it }.joinToString("")

    /** Приводит ввод ученика к обычной записи: без пробелов, с латинскими буквами и цифрами. */
    fun normalize(input: String): String = buildString {
        for (ch in input) {
            when {
                ch.isWhitespace() -> Unit
                ch in SUBSCRIPTS -> append('0' + SUBSCRIPTS.indexOf(ch))
                else -> append(LOOKALIKES[ch] ?: ch)
            }
        }
    }

    sealed interface ParseResult {
        /** Сколько атомов каждого элемента, в порядке их появления в формуле. */
        data class Ok(val atoms: Map<String, Int>) : ParseResult
        data class Error(val message: String) : ParseResult
    }

    /** Считает атомы в формуле. Понимает скобки: «Al2(SO4)3». */
    fun parse(formula: String): ParseResult {
        val text = normalize(formula)
        if (text.isEmpty()) return ParseResult.Error("Впиши формулу")
        return try {
            val parser = Parser(text)
            val atoms = parser.parseGroup()
            if (parser.pos < text.length) throw FormulaException("Лишняя закрывающая скобка")
            ParseResult.Ok(atoms)
        } catch (e: FormulaException) {
            ParseResult.Error(e.message ?: "Не получилось разобрать формулу")
        }
    }

    private class FormulaException(message: String) : Exception(message)

    private class Parser(val s: String) {
        var pos = 0

        fun parseGroup(): Map<String, Int> {
            val atoms = LinkedHashMap<String, Int>()
            while (pos < s.length) {
                val ch = s[pos]
                when {
                    ch == '(' -> {
                        pos++
                        val inner = parseGroup()
                        if (pos >= s.length || s[pos] != ')') throw FormulaException("Не хватает закрывающей скобки")
                        pos++
                        if (inner.isEmpty()) throw FormulaException("Пустые скобки")
                        val count = readCount()
                        inner.forEach { (el, n) -> atoms.merge(el, n * count, Int::plus) }
                    }
                    ch == ')' -> return atoms
                    ch in 'A'..'Z' -> {
                        pos++
                        var symbol = ch.toString()
                        if (pos < s.length && s[pos] in 'a'..'z') {
                            symbol += s[pos]
                            pos++
                        }
                        if (symbol !in ELEMENTS) throw FormulaException("Нет такого элемента: $symbol")
                        atoms.merge(symbol, readCount(), Int::plus)
                    }
                    ch in 'a'..'z' -> throw FormulaException(
                        "Символ элемента начинается с заглавной буквы: ${ch.uppercaseChar()}, а не $ch",
                    )
                    ch in '0'..'9' -> throw FormulaException(
                        if (pos == 0) "Число в начале не нужно: здесь пишется только формула" else "Индекс стоит не на месте",
                    )
                    else -> throw FormulaException("Непонятный символ «$ch». Пиши латинскими буквами")
                }
            }
            return atoms
        }

        private fun readCount(): Int {
            val start = pos
            while (pos < s.length && s[pos] in '0'..'9') pos++
            if (start == pos) return 1
            val n = s.substring(start, pos).toInt()
            if (n == 0) throw FormulaException("Индекс не может быть равен нулю")
            return n
        }
    }
}
