/// <reference path="../pb_data/types.d.ts" />

// Новые виды заданий и картинки, проверка ответов на сервере.
//
// Виды заданий (поле type):
//   question — вопрос по теории, ответ своими словами (проверяет учитель);
//   formula  — составить формулу по названию (answer — правильная формула);
//   equation — расставить коэффициенты (answer — коэффициенты через пробел);
//   choice   — тест с выбором ответа (options — варианты, answer — номера верных через запятую);
//   fill     — вставить пропущенные слова (в text пропуски «___», answer — слова через «|», варианты через «/»);
//   molar    — вычислить молярную массу (text — формула вещества, ответ сервер считает сам).
//
// Правильные ответы (answer) скрыты: ученик их не получает, а ответ проверяет сервер
// (server/pb_hooks/main.pb.js). Учитель ответы видит и меняет.

migrate((app) => {
  const tasks = app.findCollectionByNameOrId("tasks")

  const type = tasks.fields.getByName("type")
  type.values = ["question", "formula", "equation", "choice", "fill", "molar"]

  const answer = tasks.fields.getByName("answer")
  answer.max = 1000
  answer.hidden = true
  tasks.fields.getByName("hint").hidden = true

  tasks.fields.add(new JSONField({ name: "options", maxSize: 20000 }))
  // Несколько верных ответов в тесте: ученик выбирает галочками, а не одним кружком.
  tasks.fields.add(new BoolField({ name: "multiple" }))
  tasks.fields.add(new FileField({
    name: "image",
    maxSelect: 1,
    maxSize: 10 * 1024 * 1024,
    mimeTypes: ["image/jpeg", "image/png", "image/webp", "image/gif"],
    thumbs: ["800x0"],
  }))
  app.save(tasks)

  // Сколько раз ученик проверял ответ в задании. Считает сервер, поэтому число честное.
  const users = app.findCollectionByNameOrId("users")
  const checks = new Collection({ type: "base", name: "checks" })
  checks.fields.add(new RelationField({ name: "student", required: true, maxSelect: 1, collectionId: users.id, cascadeDelete: true }))
  checks.fields.add(new RelationField({ name: "task", required: true, maxSelect: 1, collectionId: tasks.id, cascadeDelete: true }))
  checks.fields.add(new NumberField({ name: "count", onlyInt: true, min: 0 }))
  checks.fields.add(new BoolField({ name: "solved" }))
  checks.fields.add(new AutodateField({ name: "created", onCreate: true }))
  checks.fields.add(new AutodateField({ name: "updated", onCreate: true, onUpdate: true }))
  checks.addIndex("idx_checks_student_task", true, "student, task", "")
  checks.listRule = '@request.auth.isTeacher = true || student = @request.auth.id'
  checks.viewRule = checks.listRule
  // Записи создаёт и меняет только сервер при проверке ответа.
  checks.createRule = null
  checks.updateRule = null
  checks.deleteRule = "@request.auth.isTeacher = true"
  app.save(checks)
}, (app) => {
  try {
    app.delete(app.findCollectionByNameOrId("checks"))
  } catch (_) {}
  const tasks = app.findCollectionByNameOrId("tasks")
  tasks.fields.getByName("type").values = ["question", "formula", "equation"]
  const answer = tasks.fields.getByName("answer")
  answer.max = 200
  answer.hidden = false
  tasks.fields.getByName("hint").hidden = false
  for (const name of ["options", "multiple", "image"]) tasks.fields.removeByName(name)
  app.save(tasks)
})
