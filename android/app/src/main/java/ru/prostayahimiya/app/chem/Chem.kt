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

    /** «Fe(OH)3» → «Fe(OH)₃», «CuSO4·5H2O» → «CuSO₄·5H₂O» (число после точки — не индекс). */
    fun pretty(formula: String): String = buildString {
        var index = false
        for (ch in formula) {
            if (ch in '0'..'9') {
                append(if (index) SUBSCRIPTS[ch - '0'] else ch)
            } else {
                append(ch)
                index = !(ch.isWhitespace() || ch in "·*•+=→(-")
            }
        }
    }

    /** Вариант ответа «CO2» показываем как «CO₂», а обычные слова не трогаем. */
    fun prettyIfFormula(text: String): String {
        val s = text.trim()
        return if (Regex("^[A-Za-z][A-Za-z0-9()·*]*$").matches(s) && parse(s) is ParseResult.Ok) pretty(s) else text
    }

    /** Относительные атомные массы, округлённые как в школьной таблице Менделеева (как на сайте, chem.js). */
    private val ATOMIC_MASS = mapOf(
        "H" to 1.0, "He" to 4.0, "Li" to 7.0, "Be" to 9.0, "B" to 11.0, "C" to 12.0, "N" to 14.0, "O" to 16.0,
        "F" to 19.0, "Ne" to 20.0, "Na" to 23.0, "Mg" to 24.0, "Al" to 27.0, "Si" to 28.0, "P" to 31.0, "S" to 32.0,
        "Cl" to 35.5, "Ar" to 40.0, "K" to 39.0, "Ca" to 40.0, "Sc" to 45.0, "Ti" to 48.0, "V" to 51.0, "Cr" to 52.0,
        "Mn" to 55.0, "Fe" to 56.0, "Co" to 59.0, "Ni" to 59.0, "Cu" to 64.0, "Zn" to 65.0, "Ga" to 70.0, "Ge" to 73.0,
        "As" to 75.0, "Se" to 79.0, "Br" to 80.0, "Kr" to 84.0, "Rb" to 85.5, "Sr" to 88.0, "Y" to 89.0, "Zr" to 91.0,
        "Nb" to 93.0, "Mo" to 96.0, "Ru" to 101.0, "Rh" to 103.0, "Pd" to 106.0, "Ag" to 108.0, "Cd" to 112.0,
        "In" to 115.0, "Sn" to 119.0, "Sb" to 122.0, "Te" to 128.0, "I" to 127.0, "Xe" to 131.0, "Cs" to 133.0,
        "Ba" to 137.0, "La" to 139.0, "W" to 184.0, "Pt" to 195.0, "Au" to 197.0, "Hg" to 201.0, "Tl" to 204.0,
        "Pb" to 207.0, "Bi" to 209.0, "Ra" to 226.0, "U" to 238.0,
    )

    sealed interface MolarResult {
        data class Ok(val value: Double) : MolarResult
        data class Error(val message: String) : MolarResult
    }

    /** Молярная масса в г/моль. Понимает кристаллогидраты: «CuSO4·5H2O» (точку можно заменить на *). */
    fun molarMass(formula: String): MolarResult {
        var total = 0.0
        for (raw in normalize(formula).split('·', '*', '•')) {
            val digits = raw.takeWhile { it in '0'..'9' }
            val times = digits.toIntOrNull() ?: 1
            when (val r = parse(raw.drop(digits.length))) {
                is ParseResult.Error -> return MolarResult.Error(r.message)
                is ParseResult.Ok -> for ((element, n) in r.atoms) {
                    val mass = ATOMIC_MASS[element] ?: return MolarResult.Error("Нет атомной массы для $element")
                    total += mass * n * times
                }
            }
        }
        return MolarResult.Ok(Math.round(total * 10) / 10.0)
    }

    /** 98.0 → «98», 36.5 → «36,5». */
    fun formatNumber(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else value.toString().replace('.', ',')

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
