package ru.prostayahimiya.app.chem

/*
 * Задания «пропущенные слова» и «тест». Проверяет их сервер (server/pb_hooks), а здесь только то,
 * что нужно для показа задания и для редактора учителя. Логика та же, что в chem.js на сайте.
 */

/** Пропуск в тексте задания — три и больше подчёркиваний. */
val BLANK = Regex("_{3,}")

/** «Вода состоит из [водорода] и [кислорода]» → текст «… из ___ и ___» и ответ «водорода|кислорода». */
fun splitBlanks(source: String): Pair<String, String> {
    val answers = mutableListOf<String>()
    val text = Regex("\\[([^\\]]*)]").replace(source) { match ->
        answers += match.groupValues[1].split('/').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("/")
        "___"
    }
    return text to answers.joinToString("|")
}

/** Обратно: текст с пропусками и ответы → текст со словами в квадратных скобках (для редактора). */
fun joinBlanks(text: String, answer: String): String {
    val answers = answer.split('|')
    var i = 0
    return BLANK.replace(text) { "[" + answers.getOrElse(i++) { "" } + "]" }
}

fun blankCount(text: String): Int = BLANK.findAll(text).count()

/** «1, 3» → [1, 3]: номера вариантов с единицы, по возрастанию, без повторов. */
fun parseChoice(value: String): List<Int> =
    value.split(Regex("[\\s,;|]+")).mapNotNull { it.toIntOrNull() }.filter { it >= 1 }.distinct().sorted()
