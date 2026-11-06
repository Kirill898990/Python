/// <reference path="../pb_data/types.d.ts" />

// Согласие с политикой конфиденциальности при регистрации.
// users.privacyAcceptedAt — когда человек поставил галочку «Я соглашаюсь с политикой конфиденциальности»
// (ставит сервер, pb_hooks/main.pb.js). Сам пользователь поменять это поле не может.

migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  users.fields.add(new DateField({ name: "privacyAcceptedAt" }))
  users.createRule = `(${users.createRule}) && @request.body.privacyAcceptedAt:isset = false`
  users.updateRule = `(${users.updateRule}) && @request.body.privacyAcceptedAt:isset = false`
  app.save(users)
}, (app) => {
  const users = app.findCollectionByNameOrId("users")
  users.fields.removeByName("privacyAcceptedAt")
  users.createRule = users.createRule.replace(" && @request.body.privacyAcceptedAt:isset = false", "").replace(/^\((.*)\)$/, "$1")
  users.updateRule = users.updateRule.replace(" && @request.body.privacyAcceptedAt:isset = false", "").replace(/^\((.*)\)$/, "$1")
  app.save(users)
})
