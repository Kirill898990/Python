/// <reference path="../pb_data/types.d.ts" />

// Контрольная работа (поля и правила — в pb_migrations/1762800000_exam.js).
//
// POST /api/exam/start — ученик начинает контрольную ({practice, begin: true}) или продолжает начатую:
//                        сервер запоминает время начала и отдаёт задания. Раньше задания ученику не видны.
// POST /api/exam/reset — учитель разрешает ученику пересдать: сданная работа удаляется.
// Сдача: только один раз и только после «Начать»; если время вышло — отметка overtime.
// Ответы ученика: пока результаты закрыты, ученик не видит, что верно, а что нет.

routerAdd("POST", "/api/exam/start", (e) => {
  const { timeOf } = require(`${__hooks}/lib.js`)
  const auth = e.auth
  const body = e.requestInfo().body || {}
  if (auth.getBool("isTeacher")) throw new BadRequestError("Учителю начинать контрольную не нужно")
  const id = String(body.practice || "").replace(/[^a-z0-9]/gi, "")
  let practice
  try {
    practice = e.app.findRecordById("practices", id)
  } catch (_) {
    throw new NotFoundError("Контрольная не найдена")
  }
  const owner = practice.getString("owner")
  let classes = []
  try {
    classes = JSON.parse(practice.getString("classes") || "[]") || []
  } catch (_) {}
  // Контрольная своего учителя для своего класса — или курсовая, для этого ученика курса.
  let course = false
  if (practice.getString("enrollment")) {
    try {
      const en = e.app.findRecordById("enrollments", practice.getString("enrollment"))
      course = en.getString("student") === auth.id && en.getBool("active")
    } catch (_) {}
  }
  const visible = practice.getBool("published") && (course || (!practice.getString("enrollment") && owner !== "" &&
    owner === auth.getString("teacher") && (!classes.length || classes.indexOf(auth.getString("group")) >= 0)))
  if (!visible || !practice.getBool("exam")) throw new NotFoundError("Контрольная не найдена")

  const find = () => {
    try {
      return e.app.findFirstRecordByFilter("exam_starts", "student = {:s} && practice = {:p}", { s: auth.id, p: id })
    } catch (_) {
      return null
    }
  }
  const submitted = e.app.countRecords("submissions", $dbx.hashExp({ student: auth.id, practice: id })) > 0
  let start = find()
  if (!start || !start.getString("started")) {
    if (submitted) throw new BadRequestError("Контрольная уже сдана")
    // Таймер запускается только по кнопке «Начать» (begin). Без неё просто сообщаем, что ещё не начата.
    if (!body.begin) {
      return e.json(200, { started: "", now: new Date().toISOString(), timeLimit: practice.getInt("timeLimit"), submitted, tasks: [] })
    }
    // Срок проверяем только при первом начале. Если учитель разрешил пересдать — можно и после срока.
    const deadline = timeOf(practice.getString("deadline"))
    if (!start && !isNaN(deadline) && deadline < Date.now()) {
      throw new BadRequestError("Срок контрольной прошёл. Если нужно написать её позже, попроси учителя.")
    }
    if (!start) {
      start = new Record(e.app.findCollectionByNameOrId("exam_starts"))
      start.set("student", auth.id)
      start.set("practice", id)
    }
    start.set("started", new Date().toISOString())
    try {
      e.app.save(start)
    } catch (err) {
      // Нажали «Начать» два раза подряд — берём запись, которую уже сохранил первый запрос.
      start = find()
      if (!start) throw err
    }
  }
  const tasks = e.app.findRecordsByFilter("tasks", "practice = {:p}", "position", 0, 0, { p: id })
  return e.json(200, {
    started: start.getString("started"),
    now: new Date().toISOString(),
    timeLimit: practice.getInt("timeLimit"),
    submitted,
    tasks,
  })
}, $apis.requireAuth("users"))

routerAdd("POST", "/api/exam/reset", (e) => {
  const auth = e.auth
  if (!auth.getBool("isTeacher")) throw new ForbiddenError("Это может только учитель")
  const body = e.requestInfo().body || {}
  const clean = (v) => String(v || "").replace(/[^a-z0-9]/gi, "")
  let practice
  let student
  try {
    practice = e.app.findRecordById("practices", clean(body.practice))
    student = e.app.findRecordById("users", clean(body.student))
  } catch (_) {
    throw new NotFoundError("Не найдено")
  }
  if (practice.getString("owner") !== auth.id || student.getString("teacher") !== auth.id) throw new ForbiddenError("Нет доступа")
  const params = { s: student.id, p: practice.id }
  e.app.runInTransaction((tx) => {
    tx.findRecordsByFilter("submissions", "student = {:s} && practice = {:p}", "", 0, 0, params).forEach((r) => tx.delete(r))
    tx.findRecordsByFilter("exam_starts", "student = {:s} && practice = {:p}", "", 0, 0, params).forEach((r) => {
      r.set("started", "")
      r.set("submitted", false)
      tx.save(r)
    })
  })
  return e.json(200, { ok: true })
}, $apis.requireAuth("users"))

onRecordCreateRequest((e) => {
  if (e.hasSuperuserAuth()) return e.next()
  const { timeOf } = require(`${__hooks}/lib.js`)
  let practice
  try {
    practice = e.app.findRecordById("practices", e.record.getString("practice"))
  } catch (_) {
    return e.next()
  }
  if (!practice.getBool("exam")) return e.next()
  const student = e.record.getString("student")
  let start = null
  try {
    start = e.app.findFirstRecordByFilter("exam_starts", "student = {:s} && practice = {:p}", { s: student, p: practice.id })
  } catch (_) {}
  const began = start ? timeOf(start.getString("started")) : NaN
  if (isNaN(began)) throw new BadRequestError("Сначала нажми «Начать контрольную»")
  const once = "Контрольную можно сдать только один раз"
  if (e.app.countRecords("submissions", $dbx.hashExp({ student, practice: practice.id })) > 0) throw new BadRequestError(once)
  // Отметка «сдано» одним запросом: если две отправки пришли одновременно, пройдёт только одна.
  const lock = e.app.db().newQuery("UPDATE exam_starts SET submitted = 1 WHERE id = {:id} AND submitted = 0").bind({ id: start.id }).execute()
  if (lock.rowsAffected() !== 1) throw new BadRequestError(once)
  const limit = practice.getInt("timeLimit")
  // 2 минуты запаса: на медленный интернет и автоматическую отправку, когда время кончилось.
  e.record.set("overtime", limit > 0 && Date.now() > began + (limit * 60 + 120) * 1000)
  try {
    e.next()
  } catch (err) {
    // Работа не сохранилась — снимаем отметку, чтобы можно было отправить ещё раз.
    e.app.db().newQuery("UPDATE exam_starts SET submitted = 0 WHERE id = {:id}").bind({ id: start.id }).execute()
    throw err
  }
}, "submissions")

onRecordEnrich((e) => {
  const info = e.requestInfo
  const auth = info && info.auth
  if (!auth || auth.isSuperuser() || auth.getBool("isTeacher")) return e.next()
  const { examResultsOpen } = require(`${__hooks}/lib.js`)
  let practice
  try {
    practice = e.app.findRecordById("practices", e.record.getString("practice"))
  } catch (_) {
    return e.next()
  }
  if (practice.getBool("exam") && !examResultsOpen(practice, e.record)) {
    let answers = []
    try {
      answers = JSON.parse(e.record.getString("answers") || "[]") || []
    } catch (_) {}
    if (Array.isArray(answers)) {
      answers.forEach((a) => {
        if (a && typeof a === "object") {
          delete a.correct
          delete a.attempts
        }
      })
      e.record.set("answers", answers)
    }
    e.record.set("solved", 0)
    e.record.withCustomData(true)
    e.record.set("resultsHidden", true)
  }
  e.next()
}, "submissions")
