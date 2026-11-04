package ru.prostayahimiya.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import ru.prostayahimiya.app.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Ошибка от сервера с понятным текстом для пользователя. */
class ApiException(
    message: String,
    val status: Int = 0,
    /** Ошибки по отдельным полям формы: «username» → «Этот логин уже занят». */
    val fieldErrors: Map<String, String> = emptyMap(),
) : Exception(message)

/**
 * Небольшой клиент для REST API PocketBase (https://pocketbase.io/docs/api-records/).
 * Все функции выполняются в фоне и бросают [ApiException] с текстом на русском.
 */
class PocketBase(private val baseUrl: String, private val token: () -> String?) {

    /** null, если адрес сервера в app/build.gradle.kts записан с ошибкой. */
    private val base: HttpUrl? = baseUrl.trimEnd('/').toHttpUrlOrNull()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // По этому имени сервер понимает, что приложение новое и правильные ответы ему присылать не нужно.
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    /** Загрузить файл в поле записи (картинку к заданию). */
    suspend fun <T> upload(
        method: String,
        url: HttpUrl,
        serializer: KSerializer<T>,
        field: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
    ): T {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(field, fileName, bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        return json.decodeFromString(serializer, execute(method, url, body))
    }

    /** Абсолютный адрес файла на сервере (картинки задания). thumb — уменьшенная копия, например «800x0». */
    fun fileUrl(collection: String, recordId: String, fileName: String, thumb: String? = null): String? =
        base?.newBuilder()
            ?.addPathSegments("api/files/$collection/$recordId")
            ?.addPathSegment(fileName)
            ?.apply { thumb?.let { addQueryParameter("thumb", it) } }
            ?.build()?.toString()

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun url(path: String, query: Map<String, String> = emptyMap()): HttpUrl =
        (base ?: throw ApiException("Адрес сервера указан с ошибкой: «$baseUrl». Исправьте его в app/build.gradle.kts."))
            .newBuilder().addPathSegments(path.trimStart('/')).apply {
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

    suspend fun <T> call(
        method: String,
        url: HttpUrl,
        serializer: KSerializer<T>,
        body: JsonObject? = null,
    ): T = json.decodeFromString(serializer, raw(method, url, body))

    suspend fun send(method: String, url: HttpUrl, body: JsonObject? = null) {
        raw(method, url, body)
    }

    /** Загружает все записи коллекции, страница за страницей. */
    suspend fun <T> listAll(
        collection: String,
        serializer: KSerializer<T>,
        filter: String? = null,
        sort: String? = null,
        expand: String? = null,
    ): List<T> {
        val result = mutableListOf<T>()
        var page = 1
        while (true) {
            val query = buildMap {
                put("page", page.toString())
                put("perPage", "200")
                filter?.let { put("filter", it) }
                sort?.let { put("sort", it) }
                expand?.let { put("expand", it) }
            }
            val response = call("GET", url("api/collections/$collection/records", query), Page.serializer(serializer))
            result += response.items
            if (page >= response.totalPages) break
            page++
        }
        return result
    }

    private suspend fun raw(method: String, url: HttpUrl, body: JsonObject?): String {
        val requestBody = when {
            body != null -> json.encodeToString(JsonObject.serializer(), body)
            method == "POST" || method == "PATCH" -> "{}"
            else -> null
        }?.toRequestBody("application/json; charset=utf-8".toMediaType())
        return execute(method, url, requestBody)
    }

    private suspend fun execute(method: String, url: HttpUrl, requestBody: RequestBody?): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .method(method, requestBody)
            .apply { token()?.let { header("Authorization", it) } }
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            // В отладочной сборке показываем техническую причину, чтобы было проще разобраться.
            val details = if (BuildConfig.DEBUG) "\n\n[$baseUrl] ${e.javaClass.simpleName}: ${e.message}" else ""
            throw ApiException("Нет связи с сервером. Проверь интернет и попробуй ещё раз.$details")
        }
        response.use {
            val text = it.body.string()
            if (!it.isSuccessful) throw parseError(it.code, text, url)
            text
        }
    }

    private fun parseError(status: Int, text: String, url: HttpUrl): ApiException {
        val fields = mutableMapOf<String, String>()
        try {
            json.parseToJsonElement(text).jsonObject["data"]?.jsonObject?.forEach { (field, value) ->
                val code = value.jsonObject["code"]?.jsonPrimitive?.content.orEmpty()
                fields[field] = fieldMessage(field, code)
            }
        } catch (_: Exception) {
            // Ответ не в формате PocketBase — покажем общее сообщение.
        }
        val isLogin = url.encodedPath.endsWith("auth-with-password")
        val message = when {
            isLogin && status == 400 -> "Неверный логин или пароль"
            fields.isNotEmpty() -> fields.values.first()
            status == 401 -> "Нужно войти в аккаунт заново"
            status == 403 -> "Нет доступа"
            status == 404 -> "Не найдено. Возможно, запись уже удалили."
            status == 429 -> "Слишком много попыток. Подожди минуту."
            status >= 500 -> "Ошибка на сервере. Попробуй позже."
            else -> "Что-то пошло не так (код $status)"
        }
        return ApiException(message, status, fields)
    }

    private fun fieldMessage(field: String, code: String): String = when {
        code == "validation_not_unique" && field == "username" -> "Этот логин уже занят"
        code == "validation_not_unique" -> "Такое значение уже есть"
        code == "validation_required" -> "Заполни это поле"
        code == "validation_values_mismatch" -> "Пароли не совпадают"
        code == "class_code_required" -> "Впиши код класса, который дал учитель"
        code == "class_code_invalid" -> "Неверный код класса. Проверь его у учителя"
        code == "validation_invalid_format" && field == "username" ->
            "Логин: только маленькие латинские буквы, цифры и знаки _ . -"
        field == "username" -> "Логин: от 3 до 32 символов"
        field == "password" -> "Пароль: не меньше 6 символов"
        field == "name" -> "Имя: от 2 до 100 символов"
        else -> "Проверь поле «$field»"
    }
}

/** Имя приложения в запросах к серверу: «ProstayaHimiya-Android/1.1». */
private val USER_AGENT = "ProstayaHimiya-Android/${BuildConfig.VERSION_NAME}"
