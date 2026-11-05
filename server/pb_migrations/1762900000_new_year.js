/// <reference path="../pb_data/types.d.ts" />

// Новый учебный год: учитель переводит своих учеников в следующий класс (8А → 9А),
// а одиннадцатиклассники становятся выпускниками (POST /api/new-year в pb_hooks).
//
// users.graduated     — выпускник: класса больше нет, при входе он видит поздравление.
// users.graduatedFrom — из какого класса выпустился (например, «11А»).
// Сами себе (и учитель вручную) эти поля не ставят — только кнопка «Новый учебный год».

const TEACHER = "@request.auth.isTeacher = true"
const MY_STUDENT = `${TEACHER} && teacher = @request.auth.id`
const LOCKED = "@request.body.isTeacher:isset = false && @request.body.teacher:isset = false && @request.body.wantsTeacher:isset = false"

migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  users.fields.add(new BoolField({ name: "graduated" }))
  users.fields.add(new TextField({ name: "graduatedFrom", max: 10 }))
  const graduation = " && @request.body.graduated:isset = false && @request.body.graduatedFrom:isset = false"
  users.createRule = "(@request.body.isTeacher:isset = false || @request.body.isTeacher = false)" +
    ' && (teacher = "" || teacher.isTeacher = true)' + graduation
  users.updateRule = `(id = @request.auth.id && @request.body.group:isset = false && @request.body.name:isset = false && ${LOCKED}${graduation})` +
    ` || (${MY_STUDENT} && ${LOCKED}${graduation})`
  app.save(users)
}, (app) => {
  const users = app.findCollectionByNameOrId("users")
  users.fields.removeByName("graduated")
  users.fields.removeByName("graduatedFrom")
  users.createRule = "(@request.body.isTeacher:isset = false || @request.body.isTeacher = false)" +
    ' && (teacher = "" || teacher.isTeacher = true)'
  users.updateRule = `(id = @request.auth.id && @request.body.group:isset = false && @request.body.name:isset = false && ${LOCKED})` +
    ` || (${MY_STUDENT} && ${LOCKED})`
  app.save(users)
})
