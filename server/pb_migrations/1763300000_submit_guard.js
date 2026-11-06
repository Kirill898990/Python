/// <reference path="../pb_data/types.d.ts" />

// Защита от повторной сдачи одной и той же работы (pb_hooks/main.pb.js).
// submit_guard — по одной строке на «ученик + практика»: подпись последних отправленных ответов (sig)
// и когда их отправили (at, миллисекунды). Запись меняется одним запросом к базе, поэтому даже если
// ученик нажал «Сдать» много раз подряд, сохранится только одна работа. Служебная, видна только серверу.

migrate((app) => {
  const guard = new Collection({ type: "base", name: "submit_guard" })
  guard.fields.add(new TextField({ name: "slot", required: true, max: 40 }))
  guard.fields.add(new TextField({ name: "sig", max: 64 }))
  guard.fields.add(new NumberField({ name: "at", onlyInt: true }))
  guard.addIndex("idx_submit_guard_slot", true, "slot", "")
  guard.listRule = null
  guard.viewRule = null
  guard.createRule = null
  guard.updateRule = null
  guard.deleteRule = null
  app.save(guard)
}, (app) => {
  try {
    app.delete(app.findCollectionByNameOrId("submit_guard"))
  } catch (_) {}
})
