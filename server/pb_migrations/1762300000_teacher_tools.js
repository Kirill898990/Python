/// <reference path="../pb_data/types.d.ts" />

// Инструменты учителя: сроки сдачи, практики для отдельных классов, коды классов для регистрации.
//
// practices.classes  — для каких классов практика (например ["8А", "8Б"]); пусто — для всех.
// practices.deadline — до какого времени сдать (необязательно).
// class_codes        — коды, по которым ученики регистрируются в свой класс. Пока ни одного кода нет,
//                      регистрация свободная (ученик сам пишет класс). Как только учитель создаст код,
//                      без кода зарегистрироваться нельзя, а класс берётся из кода (server/pb_hooks).

const TEACHER = "@request.auth.isTeacher = true"

migrate((app) => {
  const practices = app.findCollectionByNameOrId("practices")
  practices.fields.add(new JSONField({ name: "classes", maxSize: 2000 }))
  practices.fields.add(new DateField({ name: "deadline" }))
  app.save(practices)

  const codes = new Collection({ type: "base", name: "class_codes" })
  codes.fields.add(new TextField({ name: "group", required: true, max: 10 }))
  codes.fields.add(new TextField({ name: "code", required: true, min: 4, max: 20, pattern: "^[A-Z0-9-]+$" }))
  codes.fields.add(new BoolField({ name: "active" }))
  codes.fields.add(new AutodateField({ name: "created", onCreate: true }))
  codes.fields.add(new AutodateField({ name: "updated", onCreate: true, onUpdate: true }))
  codes.addIndex("idx_class_codes_code", true, "code", "")
  codes.listRule = TEACHER
  codes.viewRule = TEACHER
  codes.createRule = TEACHER
  codes.updateRule = TEACHER
  codes.deleteRule = TEACHER
  app.save(codes)
}, (app) => {
  try {
    app.delete(app.findCollectionByNameOrId("class_codes"))
  } catch (_) {}
  const practices = app.findCollectionByNameOrId("practices")
  practices.fields.removeByName("classes")
  practices.fields.removeByName("deadline")
  app.save(practices)
})
