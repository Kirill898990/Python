/// <reference path="../pb_data/types.d.ts" />

// Отзывы о товарах магазина (коллекция reviews — в pb_migrations/1763200000_shop_more.js).
// Написать отзыв может только тот, кто купил товар: у него есть курс (enrollment) по этому товару.
// Имя автора сервер подставляет сам и сокращает: «Иван Петров» → «Иван П.».
// У каждого товара сервер считает средний рейтинг и число отзывов (поля rating и reviews в ответе).

onRecordCreateRequest((e) => {
  if (e.hasSuperuserAuth()) return e.next()
  const { shortName } = require(`${__hooks}/mail_lib.js`)
  const auth = e.auth
  const bought = e.app.countRecords("enrollments", $dbx.hashExp({ student: auth.id, product: e.record.getString("product") }))
  if (!bought) throw new ForbiddenError("Отзыв может оставить только тот, кто купил этот курс")
  let exists = null
  try {
    exists = e.app.findFirstRecordByFilter("reviews", "product = {:p} && author = {:a}", { p: e.record.getString("product"), a: auth.id })
  } catch (_) {}
  if (exists) throw new BadRequestError("Вы уже оставили отзыв об этом курсе — его можно изменить")
  e.record.set("authorName", shortName(auth.getString("name")))
  e.record.set("text", String(e.record.getString("text")).trim())
  e.next()
}, "reviews")

onRecordUpdateRequest((e) => {
  if (e.hasSuperuserAuth()) return e.next()
  const { shortName } = require(`${__hooks}/mail_lib.js`)
  e.record.set("authorName", shortName(e.auth.getString("name")))
  e.record.set("text", String(e.record.getString("text")).trim())
  e.next()
}, "reviews")

onRecordEnrich((e) => {
  // Средняя оценка ×10 целым числом (4.5 → 45): так её проще прочитать из базы.
  const stats = new DynamicModel({ n: 0, avg10: 0 })
  try {
    e.app.db().newQuery("SELECT COUNT(*) AS n, CAST(ROUND(COALESCE(AVG(rating), 0) * 10) AS INTEGER) AS avg10 FROM reviews WHERE product = {:p}")
      .bind({ p: e.record.id }).one(stats)
  } catch (_) {}
  e.record.withCustomData(true)
  e.record.set("reviews", stats.n)
  e.record.set("rating", stats.avg10 / 10)
  e.next()
}, "products")
