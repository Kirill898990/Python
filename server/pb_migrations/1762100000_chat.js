/// <reference path="../pb_data/types.d.ts" />

// Чат: общий чат класса и личная переписка ученика с учителем.
//
// Кто что может:
//   ученик  — читать и писать в чат СВОЕГО класса и в свою личную переписку с учителем.
//             Писать лично другому ученику нельзя, чужие классы и чужие личные сообщения не видны.
//   учитель — читать и писать во все чаты классов и во все личные переписки, удалять любые сообщения.
// Имя автора и отметка «учитель» в сообщении проверяются сервером: подделать их нельзя.
// Ученик не может сам поменять себе класс или имя (иначе попал бы в чужой чат) — это делает учитель.

const TEACHER = "@request.auth.isTeacher = true"
const SIGNED_IN = '@request.auth.id != ""'

migrate((app) => {
  const users = app.findCollectionByNameOrId("users")

  const messages = new Collection({ type: "base", name: "messages" })
  messages.fields.add(new RelationField({ name: "author", required: true, maxSelect: 1, collectionId: users.id, cascadeDelete: true }))
  messages.fields.add(new TextField({ name: "authorName", required: true, max: 100 }))
  messages.fields.add(new BoolField({ name: "fromTeacher" }))
  // class — чат класса (поле group), private — переписка ученика (поле student) с учителем.
  messages.fields.add(new SelectField({ name: "kind", required: true, maxSelect: 1, values: ["class", "private"] }))
  messages.fields.add(new TextField({ name: "group", max: 10 }))
  messages.fields.add(new RelationField({ name: "student", maxSelect: 1, collectionId: users.id, cascadeDelete: true }))
  messages.fields.add(new TextField({ name: "text", required: true, max: 1000 }))
  messages.fields.add(new AutodateField({ name: "created", onCreate: true }))
  messages.fields.add(new AutodateField({ name: "updated", onCreate: true, onUpdate: true }))
  messages.addIndex("idx_messages_class", false, "kind, `group`, created", "")
  messages.addIndex("idx_messages_private", false, "kind, student, created", "")

  const canSee = '(kind = "class" && group = @request.auth.group && group != "")' +
    ' || (kind = "private" && student = @request.auth.id)'
  messages.listRule = `${SIGNED_IN} && (${TEACHER} || ${canSee})`
  messages.viewRule = messages.listRule
  messages.createRule = `${SIGNED_IN}` +
    " && author = @request.auth.id && authorName = @request.auth.name && fromTeacher = @request.auth.isTeacher" +
    ` && ((kind = "class" && group != "" && student = "" && (${TEACHER} || group = @request.auth.group))` +
    ` || (kind = "private" && group = "" && student != "" && student.isTeacher = false && (${TEACHER} || student = @request.auth.id)))`
  messages.updateRule = null // сообщения не редактируются
  messages.deleteRule = TEACHER
  app.save(messages)

  users.updateRule = `(id = @request.auth.id && @request.body.isTeacher:isset = false` +
    ` && @request.body.group:isset = false && @request.body.name:isset = false) || ${TEACHER}`
  app.save(users)
}, (app) => {
  try {
    app.delete(app.findCollectionByNameOrId("messages"))
  } catch (_) {}
  const users = app.findCollectionByNameOrId("users")
  users.updateRule = `(id = @request.auth.id && @request.body.isTeacher:isset = false) || ${TEACHER}`
  app.save(users)
})
