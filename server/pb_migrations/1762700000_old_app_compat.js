/// <reference path="../pb_data/types.d.ts" />

// Совместимость с приложением 1.1 и старым сайтом в кэше браузера: они не присылают
// owner (практики, заметки, коды классов) и teacher (сообщения чата).
// Правила разрешают пустое поле, а хук в main.pb.js сразу подставляет его: учителю — он сам,
// ученику — его учитель. Чужого владельца или чужого учителя указать по-прежнему нельзя.

const TEACHER = "@request.auth.isTeacher = true"
const SIGNED_IN = '@request.auth.id != ""'
const OWN_OR_EMPTY = '(owner = @request.auth.id || owner = "")'

migrate((app) => {
  for (const name of ["practices", "notes", "class_codes"]) {
    const c = app.findCollectionByNameOrId(name)
    c.createRule = `${TEACHER} && ${OWN_OR_EMPTY}`
    app.save(c)
  }

  const messages = app.findCollectionByNameOrId("messages")
  messages.createRule = `${SIGNED_IN}` +
    " && author = @request.auth.id && authorName = @request.auth.name && fromTeacher = @request.auth.isTeacher" +
    ' && ((kind = "class" && group != "" && student = "") || (kind = "private" && group = "" && student != "" && student.isTeacher = false))' +
    ` && ((${TEACHER} && (teacher = @request.auth.id || teacher = "") && (kind = "class" || student.teacher = @request.auth.id))` +
    ' || (@request.auth.isTeacher = false && @request.auth.teacher != "" && (teacher = @request.auth.teacher || teacher = "")' +
    ' && ((kind = "class" && group = @request.auth.group) || (kind = "private" && student = @request.auth.id))))'
  app.save(messages)
}, (app) => {
  for (const name of ["practices", "notes", "class_codes"]) {
    const c = app.findCollectionByNameOrId(name)
    c.createRule = `${TEACHER} && owner = @request.auth.id`
    app.save(c)
  }
  const messages = app.findCollectionByNameOrId("messages")
  messages.createRule = `${SIGNED_IN}` +
    " && author = @request.auth.id && authorName = @request.auth.name && fromTeacher = @request.auth.isTeacher" +
    ' && ((kind = "class" && group != "" && student = "") || (kind = "private" && group = "" && student != "" && student.isTeacher = false))' +
    ` && ((${TEACHER} && teacher = @request.auth.id && (kind = "class" || student.teacher = @request.auth.id))` +
    ` || (@request.auth.isTeacher = false && teacher = @request.auth.teacher && teacher != ""` +
    ' && ((kind = "class" && group = @request.auth.group) || (kind = "private" && student = @request.auth.id))))'
  app.save(messages)
})
