package ru.prostayahimiya.app.data

import ru.prostayahimiya.app.chem.Equation

/*
 * ВСЁ СОДЕРЖИМОЕ ПРИЛОЖЕНИЯ ЗДЕСЬ.
 *
 * Чтобы добавить практику для класса, создайте Practice (по образцу chemistry8 ниже)
 * и укажите её в списке grades у нужного класса. Экран с заданиями появится сам.
 * У каждого задания должен быть свой id, по нему сохраняется прогресс ученика.
 */

enum class Subject(val title: String, val emoji: String) {
    BIOLOGY("Биология", "🧬"),
    CHEMISTRY("Химия", "⚗️"),
}

/** Класс (5 класс, 8 класс…) внутри предмета. */
data class Grade(
    val subject: Subject,
    val number: Int,
    /** Ссылка на теорию в ЯКлассе, если есть. */
    val theoryUrl: String? = null,
    /** Практика с заданиями. null значит «задания скоро появятся». */
    val practice: Practice? = null,
)

data class Practice(
    val id: String,
    val title: String,
    /** Вопросы по теории: ученик отвечает своими словами и отправляет ответы учителю. */
    val questions: List<Question>,
    /** Задания «составь формулу», проверяются автоматически. */
    val formulas: List<FormulaTask>,
    /** Задания «расставь коэффициенты», проверяются автоматически. */
    val equations: List<EquationTask>,
) {
    /** id всех заданий, которые приложение проверяет само. По ним считается прогресс. */
    val checkableTaskIds: List<String> get() = formulas.map { it.id } + equations.map { it.id }
}

data class Question(val id: String, val text: String)

data class FormulaTask(
    val id: String,
    /** Название вещества: «хлорид алюминия». */
    val name: String,
    /** Правильная формула обычными цифрами: «AlCl3». */
    val answer: String,
    val hint: String,
)

data class EquationTask(
    val id: String,
    val equation: Equation,
    /** Правильные коэффициенты по порядку веществ. */
    val answer: List<Int>,
    val hint: String,
)

object Content {

    /** Форма для отзывов о сайте и приложении. */
    const val FEEDBACK_URL = "https://forms.yandex.ru/u/6ad4f70a4a052d113bebc2a1"

    /** Сюда отправляются ответы учеников (та же форма, что и на сайте). */
    const val ANSWERS_ENDPOINT = "https://formspree.io/f/mljgvlnp"

    const val AUTHOR = "Простая химия с Ириной"

    private val chemistry8 = Practice(
        id = "chem8",
        title = "Практика по химии",
        questions = listOf(
            Question(
                "chem8-q1",
                "Объясни разницу между понятиями «химический элемент» и «простое вещество». " +
                    "Приведи по одному примеру, где речь идёт об элементе, и где — о веществе.",
            ),
            Question(
                "chem8-q2",
                "Что такое валентность? Как, зная валентность элементов, составить формулу " +
                    "бинарного соединения (например, оксида фосфора или хлорида железа)?",
            ),
            Question(
                "chem8-q3",
                "Сформулируй закон сохранения массы веществ. Как этот закон отражается " +
                    "в записи химических уравнений?",
            ),
            Question(
                "chem8-q4",
                "Назови 4 признака, по которым можно понять, что химическая реакция идёт. " +
                    "Приведи по одному примеру реакции для каждого признака.",
            ),
            Question(
                "chem8-q5",
                "Объясни, как по положению элемента в Периодической таблице определить заряд ядра, " +
                    "число электронных слоёв и число электронов на внешнем уровне.",
            ),
        ),
        formulas = listOf(
            FormulaTask(
                "chem8-f1", "оксид азота(V)", "N2O5",
                "Валентность азота — V, кислорода — II. Наименьшее общее кратное 5 и 2 — это 10.",
            ),
            FormulaTask(
                "chem8-f2", "хлорид алюминия", "AlCl3",
                "Валентность алюминия — III, хлора в хлоридах — I.",
            ),
            FormulaTask(
                "chem8-f3", "сульфид калия", "K2S",
                "Валентность калия — I, серы в сульфидах — II.",
            ),
            FormulaTask(
                "chem8-f4", "гидроксид кальция", "Ca(OH)2",
                "Валентность кальция — II, группы OH — I. Группу OH пишут в скобках.",
            ),
        ),
        equations = listOf(
            EquationTask(
                "chem8-e1", Equation(listOf("Al", "O2"), listOf("Al2O3")), listOf(4, 3, 2),
                "Начни с кислорода: слева атомов O по 2, справа по 3. Наименьшее общее кратное — 6.",
            ),
            EquationTask(
                "chem8-e2", Equation(listOf("Fe(OH)3"), listOf("Fe2O3", "H2O")), listOf(2, 1, 3),
                "Справа 2 атома железа, значит слева нужно 2 Fe(OH)₃. Потом посчитай водород.",
            ),
            EquationTask(
                "chem8-e3", Equation(listOf("Zn", "HCl"), listOf("ZnCl2", "H2")), listOf(1, 2, 1, 1),
                "Справа 2 атома хлора, а в HCl он один.",
            ),
            EquationTask(
                "chem8-e4", Equation(listOf("NaOH", "H2SO4"), listOf("Na2SO4", "H2O")), listOf(2, 1, 1, 2),
                "Справа 2 атома натрия. Уравняй натрий, а потом водород.",
            ),
        ),
    )

    val grades: List<Grade> = listOf(
        Grade(Subject.CHEMISTRY, 10),
        Grade(Subject.CHEMISTRY, 9, theoryUrl = "https://www.yaklass.ru/p/himija/9-klass"),
        Grade(
            Subject.CHEMISTRY, 8,
            theoryUrl = "https://www.yaklass.ru/p/himija/8-klass?YklShowAll=1",
            practice = chemistry8,
        ),
        Grade(Subject.BIOLOGY, 9, theoryUrl = "https://www.yaklass.ru/p/biologia/9-klass?YklShowAll=1"),
        Grade(Subject.BIOLOGY, 8, theoryUrl = "https://www.yaklass.ru/p/biologia/8-klass?YklShowAll=1"),
        Grade(Subject.BIOLOGY, 7, theoryUrl = "https://www.yaklass.ru/p/biologia/7-klass?YklShowAll=1"),
        Grade(Subject.BIOLOGY, 6, theoryUrl = "https://www.yaklass.ru/p/biologia/6-klass?YklShowAll=1"),
        Grade(Subject.BIOLOGY, 5, theoryUrl = "https://www.yaklass.ru/p/biologia/5-klass?YklShowAll=1"),
    )

    fun grades(subject: Subject): List<Grade> = grades.filter { it.subject == subject }

    fun practices(): List<Practice> = grades.mapNotNull { it.practice }

    fun gradeOf(practiceId: String): Grade = grades.first { it.practice?.id == practiceId }

    /** «8–10 класс» для подписи на карточке предмета. */
    fun gradeRange(subject: Subject): String = range(grades(subject).map { it.number })

    fun allGradesRange(): String = range(grades.map { it.number })

    private fun range(numbers: List<Int>): String {
        val min = numbers.min()
        val max = numbers.max()
        return if (min == max) "$min" else "$min–$max"
    }
}
