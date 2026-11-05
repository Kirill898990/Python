/// <reference path="../pb_data/types.d.ts" />

// Контрольная работа.
//
// practices.exam      — это контрольная: ответы сразу не проверяются, сдать можно один раз.
// practices.timeLimit — сколько минут даётся (0 — без ограничения).
// exam_starts         — когда ученик нажал «Начать» (started). Время считает сервер, поэтому обновление страницы
//                       или переустановка приложения таймер не сбрасывает. Если учитель разрешил пересдать,
//                       started очищается: ученик может начать снова, даже если срок уже прошёл.
// submissions.overtime — работа пришла после того, как время вышло (учитель это видит).
//
// Задания контрольной ученик получает только после «Начать» (POST /api/exam/start в pb_hooks),
// поэтому заранее посмотреть вопросы нельзя. Результаты ученик увидит после срока сдачи
// или когда учитель поставит оценку.

const TEACHER = "@request.auth.isTeacher = true"
const SIGNED_IN = '@request.auth.id != ""'

const tasksRule = (examFilter) => `${SIGNED_IN} && (practice.owner = @request.auth.id ||` +
  ` (practice.published = true${examFilter} && practice.owner = @request.auth.teacher && practice.owner != ""))`

migrate((app) => {
  const practices = app.findCollectionByNameOrId("practices")
  practices.fields.add(new BoolField({ name: "exam" }))
  practices.fields.add(new NumberField({ name: "timeLimit", min: 0, max: 600, onlyInt: true }))
  app.save(practices)

  const tasks = app.findCollectionByNameOrId("tasks")
  tasks.listRule = tasksRule(" && practice.exam = false")
  tasks.viewRule = tasks.listRule
  app.save(tasks)

  const users = app.findCollectionByNameOrId("users")
  const starts = new Collection({ type: "base", name: "exam_starts" })
  starts.fields.add(new RelationField({ name: "student", required: true, maxSelect: 1, collectionId: users.id, cascadeDelete: true }))
  starts.fields.add(new RelationField({ name: "practice", required: true, maxSelect: 1, collectionId: practices.id, cascadeDelete: true }))
  starts.fields.add(new DateField({ name: "started" }))
  // Работа уже сдана. Ставится одним запросом к базе, поэтому две одновременные отправки не пройдут обе.
  starts.fields.add(new BoolField({ name: "submitted" }))
  starts.fields.add(new AutodateField({ name: "created", onCreate: true }))
  starts.addIndex("idx_exam_starts_student_practice", true, "student, practice", "")
  starts.listRule = `student = @request.auth.id || (${TEACHER} && student.teacher = @request.auth.id)`
  starts.viewRule = starts.listRule
  // Создаёт и удаляет только сервер (/api/exam/start и /api/exam/reset).
  starts.createRule = null
  starts.updateRule = null
  starts.deleteRule = null
  app.save(starts)

  const submissions = app.findCollectionByNameOrId("submissions")
  submissions.fields.add(new BoolField({ name: "overtime" }))
  submissions.createRule = `${SIGNED_IN} && @request.body.student = @request.auth.id` +
    " && (@request.body.grade:isset = false || @request.body.grade = 0)" +
    " && @request.body.comment:isset = false && @request.body.gradedAt:isset = false" +
    " && @request.body.overtime:isset = false"
  app.save(submissions)
}, (app) => {
  try {
    app.delete(app.findCollectionByNameOrId("exam_starts"))
  } catch (_) {}
  const tasks = app.findCollectionByNameOrId("tasks")
  tasks.listRule = tasksRule("")
  tasks.viewRule = tasks.listRule
  app.save(tasks)
  const practices = app.findCollectionByNameOrId("practices")
  practices.fields.removeByName("exam")
  practices.fields.removeByName("timeLimit")
  app.save(practices)
  const submissions = app.findCollectionByNameOrId("submissions")
  submissions.fields.removeByName("overtime")
  submissions.createRule = `${SIGNED_IN} && @request.body.student = @request.auth.id` +
    " && (@request.body.grade:isset = false || @request.body.grade = 0)" +
    " && @request.body.comment:isset = false && @request.body.gradedAt:isset = false"
  app.save(submissions)
})
