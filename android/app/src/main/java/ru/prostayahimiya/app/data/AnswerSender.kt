package ru.prostayahimiya.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * Отправляет ответы ученика учителю через Formspree, ту же форму, что и на сайте.
 * Поля name и answer те же, что на сайте, поэтому ответы из приложения приходят туда же.
 */
object AnswerSender {

    suspend fun send(name: String, answer: String, subject: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val fields = mapOf(
                "name" to name,
                "answer" to answer,
                "_subject" to "Ответ из приложения: $subject",
            )
            val body = fields.entries.joinToString("&") { (key, value) ->
                URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
            }
            val connection = URI(Content.ANSWERS_ENDPOINT).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                check(code in 200..299) { "Сервер ответил кодом $code" }
            } finally {
                connection.disconnect()
            }
        }
    }
}
