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
  const { Chem, taskData, hasInput } = require(`${__hooks}/lib.js`)
  let answers = []
  try {
    answers = JSON.parse(e.record.getString("answers") || "[]") || []
  } catch (_) {}
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
