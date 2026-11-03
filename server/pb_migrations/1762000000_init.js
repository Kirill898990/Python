/// <reference path="../pb_data/types.d.ts" />

// Структура базы данных приложения «Простая химия» и правила доступа к ней.
// PocketBase применяет этот файл сам при первом запуске сервера.
//
// Кто что может:
//   ученик  — регистрироваться, видеть опубликованные практики, сдавать работы, видеть ТОЛЬКО свои работы и оценки;
//   учитель — всё то же плюс создавать и менять практики и задания, видеть все работы, ставить оценки,
//             видеть список учеников и менять им пароли.
// Учителем пользователь становится, только если ему поставить галочку isTeacher в веб-панели PocketBase
// (сам себе ученик её поставить не может).

const TEACHER = "@request.auth.isTeacher = true"

/** Создаёт обычную коллекцию (таблицу) с полями. */
function baseCollection(name, fields) {
  const collection = new Collection({ type: "base", name: name })
  fields.forEach((field) => collection.fields.add(field))
  return collection
}
const SIGNED_IN = '@request.auth.id != ""'

migrate((app) => {
  // ---------- Пользователи ----------
  const users = app.findCollectionByNameOrId("users")

  users.fields.getByName("email").required = false
  users.fields.getByName("password").min = 6

  const name = users.fields.getByName("name")
  name.required = true
  name.min = 2
  name.max = 100

  users.fields.add(new TextField({
    name: "username",
    required: true,
    min: 3,
    max: 32,
    pattern: "^[a-z0-9_.-]+$",
    presentable: true,
  }))
  users.fields.add(new TextField({ name: "group", max: 10 })) // класс, например «8Б»
  users.fields.add(new BoolField({ name: "isTeacher" }))
  users.addIndex("idx_users_username", true, "username", "")

  users.passwordAuth.identityFields = ["username"] // вход по логину, почта не нужна
  users.authToken.duration = 30 * 24 * 60 * 60 // не разлогинивать 30 дней

  users.listRule = `${TEACHER} || id = @request.auth.id`
  users.viewRule = `${TEACHER} || id = @request.auth.id`
  // Зарегистрироваться может любой, но назначить себя учителем нельзя.
  users.createRule = "@request.body.isTeacher:isset = false || @request.body.isTeacher = false"
  users.updateRule = `(id = @request.auth.id && @request.body.isTeacher:isset = false) || ${TEACHER}`
  users.deleteRule = TEACHER
  // Учитель может сбросить ученику пароль без старого пароля.
  users.manageRule = TEACHER
  app.save(users)

  // ---------- Практики ----------
  const practices = baseCollection("practices", [
    new TextField({ name: "title", required: true, max: 200 }),
    new SelectField({ name: "subject", required: true, maxSelect: 1, values: ["chemistry", "biology"] }),
    new NumberField({ name: "grade", required: true, min: 1, max: 11, onlyInt: true }),
    new NumberField({ name: "position", onlyInt: true }),
    new BoolField({ name: "published" }),
    new AutodateField({ name: "created", onCreate: true }),
    new AutodateField({ name: "updated", onCreate: true, onUpdate: true }),
  ])
  practices.listRule = `${SIGNED_IN} && (published = true || ${TEACHER})`
  practices.viewRule = practices.listRule
  practices.createRule = TEACHER
  practices.updateRule = TEACHER
  practices.deleteRule = TEACHER
  app.save(practices)

  // ---------- Задания внутри практики ----------
  // type = question  → text: вопрос; ученик отвечает своими словами
  // type = formula   → text: название вещества; answer: формула, например Ca(OH)2
  // type = equation  → text: уравнение без коэффициентов «Al + O2 = Al2O3»; answer: коэффициенты «4 3 2»
  const tasks = baseCollection("tasks", [
    new RelationField({
      name: "practice",
      required: true,
      maxSelect: 1,
      collectionId: practices.id,
      cascadeDelete: true,
    }),
    new SelectField({ name: "type", required: true, maxSelect: 1, values: ["question", "formula", "equation"] }),
    new NumberField({ name: "position", onlyInt: true }),
    new TextField({ name: "text", required: true, max: 2000 }),
    new TextField({ name: "answer", max: 200 }),
    new TextField({ name: "hint", max: 1000 }),
    new AutodateField({ name: "created", onCreate: true }),
    new AutodateField({ name: "updated", onCreate: true, onUpdate: true }),
  ])
  tasks.addIndex("idx_tasks_practice", false, "practice", "")
  tasks.listRule = `${SIGNED_IN} && (practice.published = true || ${TEACHER})`
  tasks.viewRule = tasks.listRule
  tasks.createRule = TEACHER
  tasks.updateRule = TEACHER
  tasks.deleteRule = TEACHER
  app.save(tasks)

  // ---------- Сданные работы и оценки ----------
  const submissions = baseCollection("submissions", [
    new RelationField({
      name: "student",
      required: true,
      maxSelect: 1,
      collectionId: users.id,
      cascadeDelete: true,
    }),
    // Если учитель удалит практику, работа останется, а название сохранится в practiceTitle.
    new RelationField({ name: "practice", maxSelect: 1, collectionId: practices.id }),
    new TextField({ name: "practiceTitle", required: true, max: 300 }),
    new JSONField({ name: "answers", maxSize: 200000 }),
    new NumberField({ name: "solved", onlyInt: true, min: 0 }),
    new NumberField({ name: "total", onlyInt: true, min: 0 }),
    new NumberField({ name: "grade", onlyInt: true, min: 0, max: 5 }), // 0 = ещё не проверено
    new TextField({ name: "comment", max: 2000 }),
    new DateField({ name: "gradedAt" }),
    new AutodateField({ name: "created", onCreate: true }),
    new AutodateField({ name: "updated", onCreate: true, onUpdate: true }),
  ])
  submissions.addIndex("idx_submissions_student", false, "student", "")
  submissions.listRule = `${TEACHER} || student = @request.auth.id`
  submissions.viewRule = submissions.listRule
  // Ученик сдаёт работу только от своего имени и не может сам поставить себе оценку.
  submissions.createRule = `${SIGNED_IN} && @request.body.student = @request.auth.id` +
    " && (@request.body.grade:isset = false || @request.body.grade = 0)" +
    " && @request.body.comment:isset = false && @request.body.gradedAt:isset = false"
  // Оценку и комментарий ставит только учитель.
  submissions.updateRule = TEACHER
  submissions.deleteRule = TEACHER
  app.save(submissions)

  // ---------- Стартовые задания: практика по химии для 8 класса (с сайта) ----------
  const practice = new Record(practices)
  practice.set("title", "Практика по химии")
  practice.set("subject", "chemistry")
  practice.set("grade", 8)
  practice.set("position", 1)
  practice.set("published", true)
  app.save(practice)

  const seed = [
    ["question", "Объясни разницу между понятиями «химический элемент» и «простое вещество». Приведи по одному примеру, где речь идёт об элементе, и где — о веществе.", "", ""],
    ["question", "Что такое валентность? Как, зная валентность элементов, составить формулу бинарного соединения (например, оксида фосфора или хлорида железа)?", "", ""],
    ["question", "Сформулируй закон сохранения массы веществ. Как этот закон отражается в записи химических уравнений?", "", ""],
    ["question", "Назови 4 признака, по которым можно понять, что химическая реакция идёт. Приведи по одному примеру реакции для каждого признака.", "", ""],
    ["question", "Объясни, как по положению элемента в Периодической таблице определить заряд ядра, число электронных слоёв и число электронов на внешнем уровне.", "", ""],
    ["formula", "оксид азота(V)", "N2O5", "Валентность азота — V, кислорода — II. Наименьшее общее кратное 5 и 2 — это 10."],
    ["formula", "хлорид алюминия", "AlCl3", "Валентность алюминия — III, хлора в хлоридах — I."],
    ["formula", "сульфид калия", "K2S", "Валентность калия — I, серы в сульфидах — II."],
    ["formula", "гидроксид кальция", "Ca(OH)2", "Валентность кальция — II, группы OH — I. Группу OH пишут в скобках."],
    ["equation", "Al + O2 = Al2O3", "4 3 2", "Начни с кислорода: слева атомов O по 2, справа по 3. Наименьшее общее кратное — 6."],
    ["equation", "Fe(OH)3 = Fe2O3 + H2O", "2 1 3", "Справа 2 атома железа, значит слева нужно 2 Fe(OH)₃. Потом посчитай водород."],
    ["equation", "Zn + HCl = ZnCl2 + H2", "1 2 1 1", "Справа 2 атома хлора, а в HCl он один."],
    ["equation", "NaOH + H2SO4 = Na2SO4 + H2O", "2 1 1 2", "Справа 2 атома натрия. Уравняй натрий, а потом водород."],
  ]
  seed.forEach(([type, text, answer, hint], i) => {
    const task = new Record(tasks)
    task.set("practice", practice.id)
    task.set("type", type)
    task.set("position", i + 1)
    task.set("text", text)
    task.set("answer", answer)
    task.set("hint", hint)
    app.save(task)
  })
}, (app) => {
  for (const name of ["submissions", "tasks", "practices"]) {
    try {
      app.delete(app.findCollectionByNameOrId(name))
    } catch (_) {}
  }
  const users = app.findCollectionByNameOrId("users")
  users.fields.removeByName("username")
  users.fields.removeByName("group")
  users.fields.removeByName("isTeacher")
  users.removeIndex("idx_users_username")
  users.passwordAuth.identityFields = ["email"]
  users.fields.getByName("email").required = true
  users.listRule = "id = @request.auth.id"
  users.viewRule = "id = @request.auth.id"
  users.createRule = ""
  users.updateRule = "id = @request.auth.id"
  users.deleteRule = "id = @request.auth.id"
  users.manageRule = null
  app.save(users)
})
