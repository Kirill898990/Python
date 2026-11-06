/// <reference path="../pb_data/types.d.ts" />

// Промокоды, отзывы, звонки в курсе и напоминание о конце курса.
//
// promo_codes  — промокоды магазина. Заводит администратор в веб-панели. Скидка в процентах (percent)
//                и/или в рублях (amount). products — на какие товары действует (пусто — на все, и на будущие тоже).
//                Можно ограничить сроком (startsAt, endsAt), числом использований (maxUses), суммой заказа (minTotal)
//                и одним разом на покупателя (oncePerUser). used считает сервер, когда заказ оплачен.
// orders.promo/discount/subtotal — какой промокод применён и на сколько дешевле.
// reviews      — отзывы о товарах: оценка 1–5 и текст. Видны всем. Написать может только тот, кто купил товар
//                (проверяет pb_hooks/reviews.pb.js), один отзыв на товар — его можно поправить или удалить.
// call_signals — служебные сообщения звонка в курсе (вызов, ответ, соединение, конец).
//                Пишут друг другу только ученик курса и его учитель, пока курс открыт. Старые удаляет сервер.
// enrollments.remindedFor — для какого срока уже отправлено письмо «курс заканчивается завтра».

const SIGNED_IN = '@request.auth.id != ""'

migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  const products = app.findCollectionByNameOrId("products")
  const enrollments = app.findCollectionByNameOrId("enrollments")

  const promo = new Collection({ type: "base", name: "promo_codes" })
  promo.fields.add(new TextField({ name: "code", required: true, max: 30 }))
  promo.fields.add(new NumberField({ name: "percent", min: 0, max: 100 }))
  promo.fields.add(new NumberField({ name: "amount", min: 0 }))
  promo.fields.add(new RelationField({ name: "products", maxSelect: 100, collectionId: products.id }))
  promo.fields.add(new NumberField({ name: "minTotal", min: 0 }))
  promo.fields.add(new DateField({ name: "startsAt" }))
  promo.fields.add(new DateField({ name: "endsAt" }))
  promo.fields.add(new NumberField({ name: "maxUses", min: 0, onlyInt: true }))
  promo.fields.add(new NumberField({ name: "used", min: 0, onlyInt: true }))
  promo.fields.add(new BoolField({ name: "oncePerUser" }))
  promo.fields.add(new BoolField({ name: "active" }))
  promo.fields.add(new TextField({ name: "note", max: 300 }))
  promo.fields.add(new AutodateField({ name: "created", onCreate: true }))
  promo.fields.add(new AutodateField({ name: "updated", onCreate: true, onUpdate: true }))
  promo.addIndex("idx_promo_code", true, "code", "")
  promo.listRule = null
  promo.viewRule = null
  promo.createRule = null
  promo.updateRule = null
  promo.deleteRule = null
  app.save(promo)

  const orders = app.findCollectionByNameOrId("orders")
  orders.fields.add(new TextField({ name: "promo", max: 30 }))
  orders.fields.add(new NumberField({ name: "discount", min: 0 }))
  orders.fields.add(new NumberField({ name: "subtotal", min: 0 }))
  app.save(orders)

  const reviews = new Collection({ type: "base", name: "reviews" })
  reviews.fields.add(new RelationField({ name: "product", required: true, maxSelect: 1, collectionId: products.id, cascadeDelete: true }))
  reviews.fields.add(new RelationField({ name: "author", required: true, maxSelect: 1, collectionId: users.id, cascadeDelete: true }))
  reviews.fields.add(new TextField({ name: "authorName", max: 100 }))
  reviews.fields.add(new NumberField({ name: "rating", required: true, min: 1, max: 5, onlyInt: true }))
  reviews.fields.add(new TextField({ name: "text", max: 2000 }))
  reviews.fields.add(new AutodateField({ name: "created", onCreate: true }))
  reviews.fields.add(new AutodateField({ name: "updated", onCreate: true, onUpdate: true }))
  reviews.addIndex("idx_reviews_author", true, "product, author", "")
  reviews.addIndex("idx_reviews_product", false, "product, created", "")
  reviews.listRule = ""
  reviews.viewRule = ""
  // Купил ли автор товар, проверяет сервер (pb_hooks/reviews.pb.js).
  reviews.createRule = `${SIGNED_IN} && author = @request.auth.id && @request.auth.isTeacher = false`
  reviews.updateRule = "author = @request.auth.id && @request.body.author:isset = false && @request.body.product:isset = false"
  reviews.deleteRule = "author = @request.auth.id"
  app.save(reviews)

  const signals = new Collection({ type: "base", name: "call_signals" })
  signals.fields.add(new TextField({ name: "call", required: true, max: 30, pattern: "^[a-z0-9]+$" }))
  signals.fields.add(new RelationField({ name: "from", required: true, maxSelect: 1, collectionId: users.id, cascadeDelete: true }))
  signals.fields.add(new RelationField({ name: "to", required: true, maxSelect: 1, collectionId: users.id, cascadeDelete: true }))
  signals.fields.add(new RelationField({ name: "enrollment", required: true, maxSelect: 1, collectionId: enrollments.id, cascadeDelete: true }))
  signals.fields.add(new SelectField({ name: "type", required: true, maxSelect: 1, values: ["ring", "accept", "decline", "busy", "offer", "answer", "hangup"] }))
  signals.fields.add(new JSONField({ name: "data", maxSize: 60000 }))
  signals.fields.add(new AutodateField({ name: "created", onCreate: true }))
  signals.addIndex("idx_call_signals_to", false, "to, created", "")
  signals.addIndex("idx_call_signals_call", false, "call, created", "")
  signals.listRule = "from = @request.auth.id || to = @request.auth.id"
  signals.viewRule = signals.listRule
  signals.createRule = `${SIGNED_IN} && from = @request.auth.id && enrollment.active = true && enrollment.expiresAt > @now` +
    " && ((enrollment.student = @request.auth.id && to = enrollment.teacher) || (enrollment.teacher = @request.auth.id && to = enrollment.student))"
  signals.updateRule = null
  signals.deleteRule = null
  app.save(signals)

  enrollments.fields.add(new TextField({ name: "remindedFor", max: 40 }))
  app.save(enrollments)

}, (app) => {
  const enrollments = app.findCollectionByNameOrId("enrollments")
  enrollments.fields.removeByName("remindedFor")
  app.save(enrollments)
  const orders = app.findCollectionByNameOrId("orders")
  for (const name of ["promo", "discount", "subtotal"]) orders.fields.removeByName(name)
  app.save(orders)
  for (const name of ["call_signals", "reviews", "promo_codes"]) {
    try {
      app.delete(app.findCollectionByNameOrId(name))
    } catch (_) {}
  }
})
