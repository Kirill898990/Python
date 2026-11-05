/// <reference path="../pb_data/types.d.ts" />

// Серверная часть проверки ответов. PocketBase подключает этот файл сам (папка pb_hooks).
//
// 1. Правильные ответы к заданиям ученикам не отдаются (поле answer скрыто), видит их только учитель.
// 2. POST /api/check — проверить ответ ученика и посчитать попытку.
// 3. Когда ученик сдаёт работу, сервер сам перепроверяет ответы: подделать «верно» нельзя.

onRecordEnrich((e) => {
  const info = e.requestInfo
  const auth = info && info.auth
  const userAgent = (info && info.headers && info.headers["user_agent"]) || ""
  // Приложение версии 1.0 проверяет ответы само и без них не покажет формулы и уравнения.
  // Когда все ученики обновят приложение до 1.1, это условие можно убрать.
  const oldApp = userAgent.indexOf("okhttp/") === 0
  if ((auth && auth.getBool("isTeacher")) || oldApp) {
    e.record.unhide("answer", "hint")
  }
  e.next()
}, "tasks")

routerAdd("POST", "/api/check", (e) => {
  const { Chem, taskData, hasInput } = require(`${__hooks}/lib.js`)
  const info = e.requestInfo()
  const auth = info.auth
  const body = info.body || {}
  const items = Array.isArray(body.items) ? body.items.slice(0, 50) : []
  const tasks = e.app.findCollectionByNameOrId("tasks")
  const checks = e.app.findCollectionByNameOrId("checks")
  const isTeacher = auth.getBool("isTeacher")

  const results = items.map((item) => {
    const id = String((item && item.task) || "").replace(/[^a-z0-9]/gi, "")
    let task
    try {
      task = e.app.findRecordById(tasks, id)
    } catch (_) {
      return { task: id, kind: "error", message: "Задание не найдено. Обнови страницу." }
    }
    if (!isTeacher) {
      try {
        if (e.app.findRecordById("practices", task.getString("practice")).getBool("exam")) {
          return { task: id, kind: "error", message: "Это контрольная: ответы проверятся после сдачи" }
        }
      } catch (_) {}
    }
    if (!e.app.canAccessRecord(task, info, tasks.viewRule)) {
      return { task: id, kind: "error", message: "Нет доступа к заданию" }
    }
    const data = taskData(task)
    if (!Chem.isCheckableType(data.type)) {
      return { task: id, kind: "error", message: "Это задание проверяет учитель" }
    }
    const input = String(item.input == null ? "" : item.input).slice(0, 1000)
    const r = Chem.checkTask(data, input)

    // Попытки считаем только у учеников и только если что-то введено.
    let attempts = 0
    if (!isTeacher && hasInput(input)) {
      let rec
      try {
        rec = e.app.findFirstRecordByFilter(checks, "student = {:s} && task = {:t}", { s: auth.id, t: task.id })
      } catch (_) {
        rec = new Record(checks)
        rec.set("student", auth.id)
        rec.set("task", task.id)
        rec.set("count", 0)
      }
      rec.set("count", rec.getInt("count") + 1)
      if (r.kind === "correct") rec.set("solved", true)
      e.app.save(rec)
      attempts = rec.getInt("count")
    }
    return { task: id, kind: r.kind, message: r.message || "", blanks: r.blanks || null, attempts }
  })
  return e.json(200, { results })
}, $apis.requireAuth("users"))

onRecordCreateRequest((e) => {
  const { Chem, taskData, hasInput, timeOf } = require(`${__hooks}/lib.js`)
  let answers = []
  try {
    answers = JSON.parse(e.record.getString("answers") || "[]") || []
  } catch (_) {}

  // Ту же самую работу прислали ещё раз в течение 2 минут (несколько нажатий, медленный интернет) —
  // новую копию не создаём, а отвечаем уже сохранённой работой.
  if (!e.hasSuperuserAuth()) {
    const sig = (list) => (Array.isArray(list) ? list : [])
      .map((a) => (a && (a.task || a.prompt || "")) + "\u0001" + (a ? String(a.input == null ? a.answer || "" : a.input) : ""))
      .join("\u0002")
    try {
      const last = e.app.findRecordsByFilter("submissions", "student = {:s} && practice = {:p}", "-created", 1, 0,
        { s: e.record.getString("student"), p: e.record.getString("practice") })[0]
      let lastAnswers = []
      try {
        lastAnswers = JSON.parse(last.getString("answers") || "[]") || []
      } catch (_) {}
      const fresh = Date.now() - timeOf(last.getString("created")) < 2 * 60 * 1000
      if (fresh && sig(lastAnswers) === sig(answers)) return e.json(200, last)
    } catch (_) {}
  }
  // Старое приложение (1.0) не присылает номера заданий — тогда оставляем его подсчёт.
  if (Array.isArray(answers) && answers.some((a) => a && a.task)) {
    const student = e.record.getString("student")
    let solved = 0
    let total = 0
    answers.forEach((a) => {
      if (!a || !a.task) return
      let task
      try {
        task = e.app.findRecordById("tasks", String(a.task))
      } catch (_) {
        delete a.correct
        return
      }
      const data = taskData(task)
      if (!Chem.isCheckableType(data.type)) {
        delete a.correct
        return
      }
      total++
      const input = String(a.input == null ? "" : a.input)
      a.correct = hasInput(input) && Chem.checkTask(data, input).kind === "correct"
      if (a.correct) solved++
      try {
        a.attempts = e.app.findFirstRecordByFilter("checks", "student = {:s} && task = {:t}", { s: student, t: task.id }).getInt("count")
      } catch (_) {
        a.attempts = 0
      }
    })
    e.record.set("answers", answers)
    e.record.set("solved", solved)
    e.record.set("total", total)
  }
  e.next()
}, "submissions")

// Поле answer скрыто, поэтому PocketBase убирает его из запроса. Учитель присылает правильный ответ
// в поле answerText, и сервер сам записывает его в answer. Если answerText нет, ответ не меняется.
function keepTeacherAnswer(e) {
  const info = e.requestInfo()
  const body = info.body || {}
  if (info.auth && info.auth.getBool("isTeacher") && body.answerText !== undefined) {
    e.record.set("answer", String(body.answerText == null ? "" : body.answerText).trim().slice(0, 1000))
  }
  e.next()
}
onRecordCreateRequest(keepTeacherAnswer, "tasks")
onRecordUpdateRequest(keepTeacherAnswer, "tasks")

// Файлы сайта браузер должен каждый раз сверять с сервером (это быстро: если файл не менялся,
// сервер отвечает «не изменилось»). Иначе после обновления сайта у учеников открывается старая версия.
routerUse((e) => {
  const path = e.request.url.path
  if (!path.startsWith("/api/") && !path.startsWith("/_/")) {
    e.response.header().set("Cache-Control", "no-cache")
  }
  return e.next()
})

// ---------- Коды классов при регистрации ----------

// Для регистрации: список учителей (только подтверждённых) и нужен ли каждому код класса.
routerAdd("GET", "/api/registration-info", (e) => {
  const teachers = e.app.findRecordsByFilter("users", "isTeacher = true", "name", 0, 0).map((t) => ({
    id: t.id,
    name: t.getString("name"),
    codeRequired: e.app.countRecords("class_codes", $dbx.hashExp({ active: true, owner: t.id })) > 0,
  }))
  // codeRequired оставлен для приложений и сайта старой версии.
  return e.json(200, { teachers, codeRequired: teachers.some((t) => t.codeRequired) })
})

// Регистрация. Учитель регистрируется с галочкой wantsTeacher и ждёт подтверждения администратора.
// Ученик выбирает учителя; если у учителя есть коды классов — регистрируется только по коду,
// и класс берётся из кода. Старое приложение учителя не присылает — тогда подставляем единственного учителя.
onRecordCreateRequest((e) => {
  if (e.hasSuperuserAuth()) return e.next()
  const body = e.requestInfo().body || {}
  const fail = (field, code, message) => {
    throw new BadRequestError(message, { [field]: new ValidationError(code, message) })
  }
  if (e.record.getBool("wantsTeacher")) {
    e.record.set("teacher", "")
    e.record.set("group", "")
    return e.next()
  }
  // «8 б» → «8Б»: так класс у всех учеников записан одинаково (старые версии приложения пробелы не убирают).
  const { normalizeGroup } = require(`${__hooks}/lib.js`)
  e.record.set("group", normalizeGroup(e.record.getString("group")))
  const teachers = e.app.findRecordsByFilter("users", "isTeacher = true", "created,id", 0, 0)
  if (!teachers.length) fail("teacher", "no_teachers", "Пока нет ни одного учителя. Сначала должен зарегистрироваться учитель")
  let teacherId = e.record.getString("teacher")
  if (!teacherId && teachers.length === 1) teacherId = teachers[0].id
  const teacher = teachers.find((t) => t.id === teacherId)
  if (!teacher) fail("teacher", "teacher_required", "Выбери своего учителя")
  e.record.set("teacher", teacher.id)

  const codes = e.app.countRecords("class_codes", $dbx.hashExp({ active: true, owner: teacher.id }))
  if (codes > 0) {
    const code = String(body.classCode || "").trim().toUpperCase().replace(/\s+/g, "")
    if (!code) fail("classCode", "class_code_required", "Впиши код класса, который дал учитель")
    let record
    try {
      record = e.app.findFirstRecordByFilter("class_codes", "code = {:code} && active = true && owner = {:owner}", { code, owner: teacher.id })
    } catch (_) {
      fail("classCode", "class_code_invalid", "Неверный код класса. Проверь его у учителя")
    }
    e.record.set("group", record.getString("group"))
  }
  e.next()
}, "users")

// Приложение 1.1 и старая версия сайта в кэше браузера не присылают владельца и учителя.
// Подставляем их сами: учителю — он сам, ученику — его учитель. Правила доступа проверяют уже заполненные поля.
function fillOwner(e) {
  const auth = e.auth
  if (auth && auth.getBool("isTeacher") && !e.record.getString("owner")) e.record.set("owner", auth.id)
  e.next()
}
onRecordCreateRequest(fillOwner, "practices", "notes", "class_codes")
onRecordCreateRequest((e) => {
  const auth = e.auth
  if (e.record.getString("kind") === "course") {
    // Чат курса — всегда с учителем курса (а не с учителем ученика по школе).
    try {
      e.record.set("teacher", e.app.findRecordById("enrollments", e.record.getString("enrollment")).getString("teacher"))
    } catch (_) {}
  } else if (auth && !e.record.getString("teacher")) {
    e.record.set("teacher", auth.getBool("isTeacher") ? auth.id : auth.getString("teacher"))
  }
  e.next()
}, "messages")

// Первый подтверждённый учитель забирает себе практики без владельца (стартовую практику новой установки).
onRecordAfterUpdateSuccess((e) => {
  if (e.record.getBool("isTeacher")) {
    e.app.findRecordsByFilter("practices", 'owner = ""', "", 0, 0).forEach((p) => {
      p.set("owner", e.record.id)
      e.app.saveNoValidate(p)
    })
  }
  e.next()
}, "users")

// ---------- Варианты ----------

// Вариант ученика в каждой практике, где есть варианты. Раздаём по очереди внутри класса (у своего учителя) в порядке
// регистрации: 1-й ученик — вариант 1, 2-й — вариант 2, …, потом снова 1. Новый ученик встаёт в конец,
// поэтому у остальных вариант не меняется. Список одноклассников ученик не видит — считает сервер.
routerAdd("GET", "/api/variants", (e) => {
  const auth = e.requestInfo().auth
  const mates = e.app.findRecordsByFilter("users", "isTeacher = false && group = {:g} && teacher = {:t}", "created,id", 0, 0,
    { g: auth.getString("group"), t: auth.getString("teacher") })
  const index = Math.max(0, mates.findIndex((u) => u.id === auth.id))
  const counts = {}
  e.app.findRecordsByFilter("tasks", "variant > 0", "", 0, 0).forEach((t) => {
    const p = t.getString("practice")
    counts[p] = Math.max(counts[p] || 0, t.getInt("variant"))
  })
  const variants = {}
  Object.keys(counts).forEach((p) => { variants[p] = (index % counts[p]) + 1 })
  return e.json(200, { variants })
}, $apis.requireAuth("users"))
