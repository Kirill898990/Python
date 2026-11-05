/// <reference path="../pb_data/types.d.ts" />

// POST /api/generate-tasks {practice, prompt, count, types} — задания от YandexGPT по запросу учителя.
// В практику сервер ничего не записывает: учитель смотрит задания и сам выбирает, какие добавить.
// Ключ — в коллекции ai_settings (pb_migrations/1763000000_ai.js), лимит — dailyLimit запросов в сутки на учителя.

routerAdd("POST", "/api/generate-tasks", (e) => {
  const { TYPES, systemPrompt, parseJson, cleanTasks } = require(`${__hooks}/ai_lib.js`)
  const auth = e.auth
  if (!auth.getBool("isTeacher")) throw new ForbiddenError("Это может только учитель")
  const body = e.requestInfo().body || {}

  let settings = null
  try {
    settings = e.app.findFirstRecordByFilter("ai_settings", 'apiKey != "" && folderId != ""')
  } catch (_) {}
  if (!settings) {
    throw new BadRequestError("Нейросеть ещё не подключена. Администратор должен вписать ключ YandexGPT в веб-панели сервера: Collections → ai_settings.")
  }

  let practice = null
  const practiceId = String(body.practice || "").replace(/[^a-z0-9]/gi, "")
  if (practiceId) {
    try {
      practice = e.app.findRecordById("practices", practiceId)
    } catch (_) {}
    if (!practice || practice.getString("owner") !== auth.id) throw new NotFoundError("Практика не найдена")
  }
  const prompt = String(body.prompt || "").trim().slice(0, 1000)
  if (prompt.length < 3) throw new BadRequestError("Напишите, какие задания нужны: тему и что проверить")
  const count = Math.min(15, Math.max(1, parseInt(body.count, 10) || 10))
  let types = (Array.isArray(body.types) ? body.types : []).map(String).filter((t) => TYPES[t])
  if (!types.length) types = Object.keys(TYPES)

  const limit = settings.getInt("dailyLimit") || 30
  const since = new Date(Date.now() - 24 * 3600 * 1000).toISOString().replace("T", " ")
  const used = e.app.countRecords("ai_generations", $dbx.exp("teacher = {:t} AND created > {:s}", { t: auth.id, s: since }))
  if (used >= limit) throw new TooManyRequestsError(`На сегодня лимит исчерпан: ${limit} запросов к нейросети в сутки. Попробуйте завтра.`)

  const logRecord = new Record(e.app.findCollectionByNameOrId("ai_generations"))
  logRecord.set("teacher", auth.id)
  if (practice) logRecord.set("practice", practice.id)
  logRecord.set("prompt", prompt)
  const fail = (message) => {
    logRecord.set("error", message.slice(0, 500))
    e.app.save(logRecord)
    throw new BadRequestError(message)
  }

  const system = systemPrompt({
    subject: practice ? practice.getString("subject") : String(body.subject || "chemistry"),
    grade: practice ? practice.getInt("grade") : parseInt(body.grade, 10) || 8,
    title: practice ? practice.getString("title") : "",
    count,
    types,
  })
  const model = settings.getString("model") || "yandexgpt-5.1"
  let res
  try {
    res = $http.send({
      url: settings.getString("endpoint") || "https://ai.api.cloud.yandex.net/foundationModels/v1/completion",
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: "Api-Key " + settings.getString("apiKey") },
      body: JSON.stringify({
        modelUri: `gpt://${settings.getString("folderId")}/${model}`,
        completionOptions: { stream: false, temperature: 0.4, maxTokens: "6000" },
        messages: [{ role: "system", text: system }, { role: "user", text: prompt }],
        jsonObject: true,
      }),
      timeout: 120,
    })
  } catch (err) {
    fail("Нет связи с YandexGPT. Попробуйте ещё раз через минуту.")
  }
  if (res.statusCode === 401 || res.statusCode === 403) {
    fail("YandexGPT не принял ключ. Проверьте apiKey и folderId в ai_settings (и роль ai.languageModels.user у сервисного аккаунта).")
  }
  if (res.statusCode === 429) fail("YandexGPT сейчас перегружен. Попробуйте через минуту.")
  if (res.statusCode !== 200) fail(`YandexGPT вернул ошибку (код ${res.statusCode}). Попробуйте ещё раз.`)

  const data = res.json || {}
  const result = data.result || data
  const alt = (result.alternatives || [])[0]
  if (!alt || !alt.message) fail("YandexGPT ничего не ответил. Попробуйте ещё раз.")
  if (alt.status === "ALTERNATIVE_STATUS_CONTENT_FILTER") fail("Нейросеть отказалась отвечать на этот запрос. Переформулируйте его.")
  const parsed = parseJson(alt.message.text)
  if (!parsed) fail("Нейросеть ответила не по формату. Попробуйте ещё раз или упростите запрос.")
  const { tasks, dropped } = cleanTasks(parsed, types)
  if (!tasks.length) fail("Нейросеть не смогла составить правильные задания. Попробуйте переформулировать запрос.")

  logRecord.set("tasks", tasks.length)
  logRecord.set("tokens", parseInt((result.usage || {}).totalTokens, 10) || 0)
  e.app.save(logRecord)
  return e.json(200, { tasks, dropped, left: Math.max(0, limit - used - 1) })
}, $apis.requireAuth("users"))
