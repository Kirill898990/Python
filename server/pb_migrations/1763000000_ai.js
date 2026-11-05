/// <reference path="../pb_data/types.d.ts" />

// Задания от нейросети (YandexGPT). Учитель пишет запрос — сервер просит YandexGPT составить задания,
// проверяет их (формулы, уравнения, ответы) и показывает учителю, а тот выбирает, какие добавить.
//
// ai_settings    — ключ YandexGPT. Заполняет только администратор в веб-панели: Collections → ai_settings → New record.
//                  apiKey — секретный ключ сервисного аккаунта, folderId — идентификатор каталога Yandex Cloud.
//                  model, endpoint и dailyLimit можно не трогать.
// ai_generations — журнал запросов (кто, когда, сколько токенов). По нему считается лимит в сутки на учителя.

migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  const practices = app.findCollectionByNameOrId("practices")

  const settings = new Collection({ type: "base", name: "ai_settings" })
  settings.fields.add(new TextField({ name: "apiKey", required: true, max: 500 }))
  settings.fields.add(new TextField({ name: "folderId", required: true, max: 100 }))
  settings.fields.add(new TextField({ name: "model", max: 100 }))
  settings.fields.add(new URLField({ name: "endpoint" }))
  settings.fields.add(new NumberField({ name: "dailyLimit", min: 0, onlyInt: true }))
  settings.fields.add(new AutodateField({ name: "created", onCreate: true }))
  settings.fields.add(new AutodateField({ name: "updated", onCreate: true, onUpdate: true }))
  // Только администратор (правила null).
  settings.listRule = null
  settings.viewRule = null
  settings.createRule = null
  settings.updateRule = null
  settings.deleteRule = null
  app.save(settings)

  const log = new Collection({ type: "base", name: "ai_generations" })
  log.fields.add(new RelationField({ name: "teacher", required: true, maxSelect: 1, collectionId: users.id, cascadeDelete: true }))
  log.fields.add(new RelationField({ name: "practice", maxSelect: 1, collectionId: practices.id }))
  log.fields.add(new TextField({ name: "prompt", max: 2000 }))
  log.fields.add(new NumberField({ name: "tasks", onlyInt: true }))
  log.fields.add(new NumberField({ name: "tokens", onlyInt: true }))
  log.fields.add(new TextField({ name: "error", max: 500 }))
  log.fields.add(new AutodateField({ name: "created", onCreate: true }))
  log.addIndex("idx_ai_generations_teacher", false, "teacher, created", "")
  log.listRule = null
  log.viewRule = null
  log.createRule = null
  log.updateRule = null
  log.deleteRule = null
  app.save(log)
}, (app) => {
  for (const name of ["ai_generations", "ai_settings"]) {
    try {
      app.delete(app.findCollectionByNameOrId(name))
    } catch (_) {}
  }
})
