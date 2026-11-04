/// <reference path="../pb_data/types.d.ts" />

// Версии приложения для скачивания со страницы урокискириллом.рф/app.
//
// Как выложить новую версию: веб-панель сервера → Collections → app_releases → New record,
// впишите номер версии (versionCode, как в app/build.gradle.kts), название (versionName, например 1.1),
// что нового (notes) и прикрепите файл app-release.apk. Страница сама покажет последнюю версию,
// а приложение, установленное не из RuStore, предложит обновиться.
// Видят все (даже без входа), добавляет только администратор сервера.

migrate((app) => {
  const releases = new Collection({ type: "base", name: "app_releases" })
  releases.fields.add(new NumberField({ name: "versionCode", required: true, min: 1, onlyInt: true }))
  releases.fields.add(new TextField({ name: "versionName", required: true, max: 20 }))
  releases.fields.add(new TextField({ name: "notes", max: 2000 }))
  releases.fields.add(new FileField({ name: "apk", required: true, maxSelect: 1, maxSize: 100 * 1024 * 1024 }))
  releases.fields.add(new AutodateField({ name: "created", onCreate: true }))
  releases.fields.add(new AutodateField({ name: "updated", onCreate: true, onUpdate: true }))
  releases.addIndex("idx_app_releases_code", true, "versionCode", "")
  releases.listRule = ""
  releases.viewRule = ""
  releases.createRule = null
  releases.updateRule = null
  releases.deleteRule = null
  app.save(releases)
}, (app) => {
  try {
    app.delete(app.findCollectionByNameOrId("app_releases"))
  } catch (_) {}
})
