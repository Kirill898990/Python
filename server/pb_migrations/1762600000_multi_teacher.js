/// <reference path="../pb_data/types.d.ts" />

// Несколько учителей. У каждого учителя свои классы, ученики, практики, заметки, коды классов и чат.
//
// users.teacher      — учитель ученика (ученик выбирает его при регистрации).
// users.wantsTeacher — человек зарегистрировался как учитель и ждёт подтверждения администратора.
//                      Администратор ставит галочку isTeacher в веб-панели — и учитель появляется в списке.
// owner              — учитель, которому принадлежит практика, заметка или код класса.
// messages.teacher   — к какому учителю относится сообщение (чат класса и личные сообщения).
//
// Всё, что было до этого, переходит к первому учителю (тому, кто стал учителем раньше всех).

const TEACHER = "@request.auth.isTeacher = true"
const SIGNED_IN = '@request.auth.id != ""'

/** Ученик этого учителя (для правил коллекции users). */
const MY_STUDENT = `${TEACHER} && teacher = @request.auth.id`

migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  users.fields.add(new RelationField({ name: "teacher", maxSelect: 1, collectionId: users.id }))
  users.fields.add(new BoolField({ name: "wantsTeacher" }))
  users.listRule = `id = @request.auth.id || (${MY_STUDENT})`
  users.viewRule = users.listRule
  // Учителем сам себя не сделаешь; если указан учитель — это должен быть настоящий учитель.
  users.createRule = "(@request.body.isTeacher:isset = false || @request.body.isTeacher = false)" +
    ' && (teacher = "" || teacher.isTeacher = true)'
  users.updateRule = "(id = @request.auth.id && @request.body.isTeacher:isset = false && @request.body.group:isset = false" +
    " && @request.body.name:isset = false && @request.body.teacher:isset = false && @request.body.wantsTeacher:isset = false)" +
    ` || (${MY_STUDENT} && @request.body.isTeacher:isset = false && @request.body.teacher:isset = false && @request.body.wantsTeacher:isset = false)`
  users.deleteRule = MY_STUDENT
  users.manageRule = MY_STUDENT
  app.save(users)

  const practices = app.findCollectionByNameOrId("practices")
  practices.fields.add(new RelationField({ name: "owner", maxSelect: 1, collectionId: users.id }))
  practices.addIndex("idx_practices_owner", false, "owner", "")
  practices.listRule = `${SIGNED_IN} && (owner = @request.auth.id || (published = true && owner = @request.auth.teacher && owner != ""))`
  practices.viewRule = practices.listRule
  practices.createRule = `${TEACHER} && owner = @request.auth.id`
  practices.updateRule = `${TEACHER} && owner = @request.auth.id && (@request.body.owner:isset = false || @request.body.owner = @request.auth.id)`
  practices.deleteRule = `${TEACHER} && owner = @request.auth.id`
  app.save(practices)

  const tasks = app.findCollectionByNameOrId("tasks")
  tasks.listRule = `${SIGNED_IN} && (practice.owner = @request.auth.id ||` +
    ` (practice.published = true && practice.owner = @request.auth.teacher && practice.owner != ""))`
  tasks.viewRule = tasks.listRule
  tasks.createRule = `${TEACHER} && practice.owner = @request.auth.id`
  // Переносить задание в чужую практику нельзя: практика у задания не меняется.
  tasks.updateRule = `${TEACHER} && practice.owner = @request.auth.id && (@request.body.practice:isset = false || @request.body.practice = practice)`
  tasks.deleteRule = `${TEACHER} && practice.owner = @request.auth.id`
  app.save(tasks)

  const submissions = app.findCollectionByNameOrId("submissions")
  submissions.listRule = `student = @request.auth.id || (${TEACHER} && student.teacher = @request.auth.id)`
  submissions.viewRule = submissions.listRule
  submissions.updateRule = `${TEACHER} && student.teacher = @request.auth.id`
  submissions.deleteRule = `${TEACHER} && student.teacher = @request.auth.id`
  app.save(submissions)

  const messages = app.findCollectionByNameOrId("messages")
  messages.fields.add(new RelationField({ name: "teacher", maxSelect: 1, collectionId: users.id }))
  const studentSees = 'teacher = @request.auth.teacher && teacher != "" &&' +
    ' ((kind = "class" && group = @request.auth.group && group != "") || (kind = "private" && student = @request.auth.id))'
  messages.listRule = `${SIGNED_IN} && ((${TEACHER} && teacher = @request.auth.id) || (${studentSees}))`
  messages.viewRule = messages.listRule
  messages.createRule = `${SIGNED_IN}` +
    " && author = @request.auth.id && authorName = @request.auth.name && fromTeacher = @request.auth.isTeacher" +
    ' && ((kind = "class" && group != "" && student = "") || (kind = "private" && group = "" && student != "" && student.isTeacher = false))' +
    ` && ((${TEACHER} && teacher = @request.auth.id && (kind = "class" || student.teacher = @request.auth.id))` +
    ` || (@request.auth.isTeacher = false && teacher = @request.auth.teacher && teacher != ""` +
    ' && ((kind = "class" && group = @request.auth.group) || (kind = "private" && student = @request.auth.id))))'
  messages.deleteRule = `${TEACHER} && teacher = @request.auth.id`
  app.save(messages)

  const checks = app.findCollectionByNameOrId("checks")
  checks.listRule = `student = @request.auth.id || (${TEACHER} && student.teacher = @request.auth.id)`
  checks.viewRule = checks.listRule
  checks.deleteRule = `${TEACHER} && student.teacher = @request.auth.id`
  app.save(checks)

  const codes = app.findCollectionByNameOrId("class_codes")
  codes.fields.add(new RelationField({ name: "owner", maxSelect: 1, collectionId: users.id }))
  const ownCode = `${TEACHER} && owner = @request.auth.id`
  codes.listRule = ownCode
  codes.viewRule = ownCode
  codes.createRule = ownCode
  codes.updateRule = `${ownCode} && (@request.body.owner:isset = false || @request.body.owner = @request.auth.id)`
  codes.deleteRule = ownCode
  app.save(codes)

  const notes = app.findCollectionByNameOrId("notes")
  notes.fields.add(new RelationField({ name: "owner", maxSelect: 1, collectionId: users.id }))
  notes.removeIndex("idx_notes_subject_grade")
  notes.addIndex("idx_notes_owner_subject_grade", true, "owner, subject, grade", "")
  notes.listRule = `${SIGNED_IN} && (owner = @request.auth.id || (owner = @request.auth.teacher && owner != ""))`
  notes.viewRule = notes.listRule
  notes.createRule = `${TEACHER} && owner = @request.auth.id`
  notes.updateRule = `${TEACHER} && owner = @request.auth.id && (@request.body.owner:isset = false || @request.body.owner = @request.auth.id)`
  notes.deleteRule = `${TEACHER} && owner = @request.auth.id`
  app.save(notes)

  // Всё, что было раньше, — первому учителю.
  const teachers = app.findRecordsByFilter("users", "isTeacher = true", "created,id", 1, 0)
  if (teachers.length) {
    const first = teachers[0].id
    const assign = (collection, field, filter) => {
      app.findRecordsByFilter(collection, filter, "", 0, 0).forEach((r) => {
        r.set(field, first)
        app.saveNoValidate(r)
      })
    }
    assign("practices", "owner", 'owner = ""')
    assign("notes", "owner", 'owner = ""')
    assign("class_codes", "owner", 'owner = ""')
    assign("messages", "teacher", 'teacher = ""')
    assign("users", "teacher", 'isTeacher = false && teacher = "" && wantsTeacher = false')
  }
}, (app) => {
  const users = app.findCollectionByNameOrId("users")
  for (const [name, field] of [["practices", "owner"], ["notes", "owner"], ["class_codes", "owner"], ["messages", "teacher"]]) {
    const c = app.findCollectionByNameOrId(name)
    c.fields.removeByName(field)
    app.save(c)
  }
  users.fields.removeByName("teacher")
  users.fields.removeByName("wantsTeacher")
  app.save(users)
})
