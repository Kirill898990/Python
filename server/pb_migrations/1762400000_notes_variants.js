/// <reference path="../pb_data/types.d.ts" />

// Заметки учителя к теории, варианты заданий и ежедневные резервные копии.
//
// notes          — заметка учителя для класса по предмету (например, «Химия, 8 класс»). Видят все, кто вошёл.
// tasks.variant  — 0: задание для всех; 1, 2, 3, 4: задание только для этого варианта.
//                  Вариант ученика выбирается сам по его аккаунту, поэтому у соседей по парте разные задания.
// submissions.variant — какой вариант решал ученик (видит учитель).
// Резервные копии базы: каждую ночь в 03:00 по Москве, хранятся последние 7 (в веб-панели: Settings → Backups).

const TEACHER = "@request.auth.isTeacher = true"
const SIGNED_IN = '@request.auth.id != ""'

migrate((app) => {
  const notes = new Collection({ type: "base", name: "notes" })
  notes.fields.add(new SelectField({ name: "subject", required: true, maxSelect: 1, values: ["chemistry", "biology"] }))
  notes.fields.add(new NumberField({ name: "grade", required: true, min: 1, max: 11, onlyInt: true }))
  notes.fields.add(new TextField({ name: "text", required: true, max: 5000 }))
  notes.fields.add(new AutodateField({ name: "created", onCreate: true }))
  notes.fields.add(new AutodateField({ name: "updated", onCreate: true, onUpdate: true }))
  notes.addIndex("idx_notes_subject_grade", true, "subject, grade", "")
  notes.listRule = SIGNED_IN
  notes.viewRule = SIGNED_IN
  notes.createRule = TEACHER
  notes.updateRule = TEACHER
  notes.deleteRule = TEACHER
  app.save(notes)

  const tasks = app.findCollectionByNameOrId("tasks")
  tasks.fields.add(new NumberField({ name: "variant", min: 0, max: 4, onlyInt: true }))
  app.save(tasks)

  const submissions = app.findCollectionByNameOrId("submissions")
  submissions.fields.add(new NumberField({ name: "variant", min: 0, max: 4, onlyInt: true }))
  app.save(submissions)

  // Время сервера — UTC: 00:00 по UTC — это 03:00 по Москве. Если учитель уже настроил копии сам, не трогаем.
  const settings = app.settings()
  if (!settings.backups.cron) {
    settings.backups.cron = "0 0 * * *"
    settings.backups.cronMaxKeep = 7
    app.save(settings)
  }
}, (app) => {
  try {
    app.delete(app.findCollectionByNameOrId("notes"))
  } catch (_) {}
  const tasks = app.findCollectionByNameOrId("tasks")
  tasks.fields.removeByName("variant")
  app.save(tasks)
  const submissions = app.findCollectionByNameOrId("submissions")
  submissions.fields.removeByName("variant")
  app.save(submissions)
})
