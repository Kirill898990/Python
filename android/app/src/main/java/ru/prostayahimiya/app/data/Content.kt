package ru.prostayahimiya.app.data

/*
 * Постоянное содержимое приложения: предметы и ссылки на теорию.
 * Практики и задания хранятся на сервере, их учитель меняет в панели учителя прямо в приложении.
 */

enum class Subject(val key: String, val title: String, val emoji: String) {
    BIOLOGY("biology", "Биология", "🧬"),
    CHEMISTRY("chemistry", "Химия", "⚗️"),
    ;

    companion object {
        fun fromKey(key: String): Subject = entries.firstOrNull { it.key == key } ?: CHEMISTRY
    }
}

/** Ссылка на теорию в ЯКлассе для класса. */
data class TheoryLink(val subject: Subject, val grade: Int, val url: String)

object Content {

    /** Форма для отзывов о сайте и приложении. */
    const val FEEDBACK_URL = "https://forms.yandex.ru/u/6ad4f70a4a052d113bebc2a1"

    const val AUTHOR = "Простая химия с Ириной"

    /** Политика конфиденциальности на сайте (https://урокискириллом.рф/privacy.html). */
    const val PRIVACY_URL = "https://xn--h1aaafbiaikhnfkx.xn--p1ai/privacy.html"

    val theory: List<TheoryLink> = listOf(
        TheoryLink(Subject.CHEMISTRY, 11, "https://www.yaklass.ru/p/himija/11-klass?YklShowAll=1"),
        TheoryLink(Subject.CHEMISTRY, 10, "https://www.yaklass.ru/p/himija/10-klass"),
        TheoryLink(Subject.CHEMISTRY, 9, "https://www.yaklass.ru/p/himija/9-klass"),
        TheoryLink(Subject.CHEMISTRY, 8, "https://www.yaklass.ru/p/himija/8-klass?YklShowAll=1"),
        TheoryLink(Subject.BIOLOGY, 11, "https://www.yaklass.ru/p/biologia/11-klass?YklShowAll=1"),
        TheoryLink(Subject.BIOLOGY, 10, "https://www.yaklass.ru/p/biologia/10-klass"),
        TheoryLink(Subject.BIOLOGY, 9, "https://www.yaklass.ru/p/biologia/9-klass?YklShowAll=1"),
        TheoryLink(Subject.BIOLOGY, 8, "https://www.yaklass.ru/p/biologia/8-klass?YklShowAll=1"),
        TheoryLink(Subject.BIOLOGY, 7, "https://www.yaklass.ru/p/biologia/7-klass?YklShowAll=1"),
        TheoryLink(Subject.BIOLOGY, 6, "https://www.yaklass.ru/p/biologia/6-klass?YklShowAll=1"),
        TheoryLink(Subject.BIOLOGY, 5, "https://www.yaklass.ru/p/biologia/5-klass?YklShowAll=1"),
    )

    /** Классы, которые всегда показываются в разделе предмета, даже если практик пока нет. */
    private val baseGrades = mapOf(
        Subject.CHEMISTRY to listOf(8, 9, 10, 11),
        Subject.BIOLOGY to listOf(5, 6, 7, 8, 9, 10, 11),
    )

    fun theoryUrl(subject: Subject, grade: Int): String? =
        theory.firstOrNull { it.subject == subject && it.grade == grade }?.url

    /** Все классы предмета: постоянные плюс те, для которых учитель добавил практики. Сверху старшие. */
    fun grades(subject: Subject, content: ContentData): List<Int> =
        (baseGrades.getValue(subject) + content.practicesOf(subject).map { it.grade }).distinct().sortedDescending()

    /** «8–11» для подписи на карточке предмета. */
    fun gradeRange(subject: Subject, content: ContentData): String = range(grades(subject, content))

    fun allGradesRange(content: ContentData): String =
        range(Subject.entries.flatMap { grades(it, content) })

    private fun range(numbers: List<Int>): String {
        val min = numbers.min()
        val max = numbers.max()
        return if (min == max) "$min" else "$min–$max"
    }
}
