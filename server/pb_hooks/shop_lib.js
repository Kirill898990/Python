// Магазин beemei: запросы к ЮKassa и выдача курса после оплаты (используется в shop.pb.js).

/** Base64 для заголовка Basic (shopId:секретный ключ — латиница и цифры). */
function base64(text) {
  const abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
  const bytes = []
  for (let i = 0; i < text.length; i++) {
    let c = text.charCodeAt(i)
    if (c < 128) bytes.push(c)
    else if (c < 2048) bytes.push(192 | (c >> 6), 128 | (c & 63))
    else bytes.push(224 | (c >> 12), 128 | ((c >> 6) & 63), 128 | (c & 63))
  }
  let out = ""
  for (let i = 0; i < bytes.length; i += 3) {
    const n = (bytes[i] << 16) | ((bytes[i + 1] || 0) << 8) | (bytes[i + 2] || 0)
    out += abc[(n >> 18) & 63] + abc[(n >> 12) & 63]
    out += i + 1 < bytes.length ? abc[(n >> 6) & 63] : "="
    out += i + 2 < bytes.length ? abc[n & 63] : "="
  }
  return out
}

function settings(app) {
  try {
    return app.findFirstRecordByFilter("shop_settings", 'shopId != "" && secretKey != ""')
  } catch (_) {
    return null
  }
}

/** Запрос к API ЮKassa. → {status, json} */
function yookassa(s, method, path, body, idempotenceKey) {
  const headers = {
    Authorization: "Basic " + base64(s.getString("shopId") + ":" + s.getString("secretKey")),
    "Content-Type": "application/json",
  }
  if (idempotenceKey) headers["Idempotence-Key"] = idempotenceKey
  const res = $http.send({
    url: (s.getString("apiUrl") || "https://api.yookassa.ru/v3").replace(/\/+$/, "") + path,
    method,
    headers,
    body: body ? JSON.stringify(body) : "",
    timeout: 30,
  })
  return { status: res.statusCode, json: res.json || {} }
}

const now = () => new Date().toISOString().replace("T", " ")
const toTime = (v) => { const t = v ? Date.parse(String(v).replace(" ", "T")) : NaN; return isNaN(t) ? 0 : t }
const rub = (n) => String(Math.round(n)).replace(/\B(?=(\d{3})+(?!\d))/g, " ") + " ₽"

/**
 * Товары из корзины → позиции заказа. Цены и сроки берём из базы, а не из запроса.
 * raw: [{product, teacher, subject, exam, days}]. Ошибку бросает понятным текстом.
 */
function priceItems(app, raw) {
  const items = []
  const seen = {}
  ;(Array.isArray(raw) ? raw.slice(0, 10) : []).forEach((x) => {
    let p
    try {
      p = app.findRecordById("products", String((x && x.product) || "").replace(/[^a-z0-9]/gi, ""))
    } catch (_) {}
    if (!p || !p.getBool("active")) throw new BadRequestError("Одного из товаров больше нет в продаже. Обновите корзину.")
    const json = (field) => { try { return JSON.parse(p.getString(field) || "[]") || [] } catch (_) { return [] } }
    const allowedSubjects = json("subjects")
    const allowedExams = json("exams")
    const plans = json("plans")
    const subject = String(x.subject || "")
    const exam = String(x.exam || "")
    if (allowedSubjects.length && allowedSubjects.indexOf(subject) < 0) throw new BadRequestError("Выберите предмет для курса")
    if (allowedExams.length && allowedExams.indexOf(exam) < 0) throw new BadRequestError("Выберите экзамен для курса")
    const plan = plans.find((pl) => pl && pl.days === parseInt(x.days, 10))
    if (plans.length && !plan) throw new BadRequestError("Выберите срок подписки")
    const days = plan ? plan.days : 30
    const price = plan ? Number(plan.price) : p.getFloat("price")
    // Учитель: из списка учителей курса, а если список пуст — любой подтверждённый учитель.
    const teacherId = String(x.teacher || "").replace(/[^a-z0-9]/gi, "")
    const allowedTeachers = p.getStringSlice("teachers")
    let teacher = null
    try { teacher = app.findRecordById("users", teacherId) } catch (_) {}
    if (!teacher || !teacher.getBool("isTeacher") || (allowedTeachers.length && allowedTeachers.indexOf(teacherId) < 0)) {
      throw new BadRequestError("Выберите учителя")
    }
    const key = [p.id, subject, exam, teacherId].join("|")
    if (seen[key]) throw new BadRequestError("Один и тот же курс в корзине дважды")
    seen[key] = true
    items.push({
      product: p.id, title: p.getString("title"), price, days,
      subject, exam, teacher: teacherId, teacherName: teacher.getString("name"),
    })
  })
  return items
}

/** Промокод: «Promo-10 » → «PROMO-10». */
const normalizeCode = (code) => String(code || "").toUpperCase().replace(/\s+/g, "").slice(0, 30)

/**
 * Применить промокод к позициям заказа. buyerId — для промокодов «один раз на покупателя» (может быть пустым).
 * → {code, discount, subtotal, total, text}. Если промокод не подходит, бросает ошибку с причиной.
 * Скидка действует на товары из списка промокода (пустой список — на все товары, и на новые тоже).
 */
function applyPromo(app, rawCode, items, buyerId) {
  const code = normalizeCode(rawCode)
  const subtotal = items.reduce((sum, it) => sum + it.price, 0)
  if (!code) return { code: "", discount: 0, subtotal, total: subtotal, text: "" }
  let p
  try {
    p = app.findFirstRecordByFilter("promo_codes", "code = {:c}", { c: code })
  } catch (_) {}
  if (!p || !p.getBool("active")) throw new BadRequestError("Такого промокода нет. Проверьте, как он написан.")
  const t = Date.now()
  const starts = toTime(p.getString("startsAt"))
  const ends = toTime(p.getString("endsAt"))
  if (starts && t < starts) throw new BadRequestError("Промокод ещё не начал действовать")
  if (ends && t > ends) throw new BadRequestError("Срок действия промокода закончился")
  const maxUses = p.getInt("maxUses")
  if (maxUses > 0 && p.getInt("used") >= maxUses) throw new BadRequestError("Промокод уже закончился")
  if (p.getBool("oncePerUser") && buyerId) {
    const used = app.countRecords("orders", $dbx.hashExp({ buyer: buyerId, promo: code, status: "paid" }))
    if (used > 0) throw new BadRequestError("Вы уже использовали этот промокод")
  }
  const minTotal = p.getFloat("minTotal")
  if (minTotal > 0 && subtotal < minTotal) throw new BadRequestError("Промокод действует при заказе от " + rub(minTotal))
  const only = p.getStringSlice("products")
  const eligible = items.filter((it) => !only.length || only.indexOf(it.product) >= 0).reduce((sum, it) => sum + it.price, 0)
  if (!eligible) throw new BadRequestError("Промокод не действует на товары в корзине")
  const percent = p.getFloat("percent")
  const amount = p.getFloat("amount")
  let discount = Math.round(eligible * percent / 100) + Math.round(amount)
  discount = Math.min(discount, eligible)
  // ЮKassa не принимает платёж меньше 1 рубля.
  const total = Math.max(1, subtotal - discount)
  discount = subtotal - total
  if (discount <= 0) throw new BadRequestError("Промокод не даёт скидки на этот заказ")
  const text = [percent > 0 && `${percent}%`, amount > 0 && rub(amount)].filter(Boolean).join(" + ")
  return { code, discount, subtotal, total, text: "Скидка " + text }
}

/**
 * Заказ оплачен — выдаём курсы. Отметка «оплачено» ставится одним запросом к базе, поэтому если
 * ЮKassa прислала уведомление, а покупатель одновременно открыл страницу заказа, курс выдастся один раз.
 */
function fulfill(app, orderId) {
  const res = app.db().newQuery("UPDATE orders SET status = 'paid', paidAt = {:now} WHERE id = {:id} AND status = 'pending'")
    .bind({ id: orderId, now: now() }).execute()
  if (res.rowsAffected() !== 1) return false
  const order = app.findRecordById("orders", orderId)
  let items = []
  try {
    items = JSON.parse(order.getString("items") || "[]") || []
  } catch (_) {}
  const buyer = app.findRecordById("users", order.getString("buyer"))
  const enrollments = app.findCollectionByNameOrId("enrollments")
  const teachers = buyer.getStringSlice("courseTeachers")
  items.forEach((it) => {
    // Тот же курс у того же учителя уже был — продлеваем: с конца текущего срока или с сегодняшнего дня.
    let r = null
    try {
      r = app.findFirstRecordByFilter("enrollments", "student = {:s} && teacher = {:t} && product = {:p} && subject = {:sub} && exam = {:e}",
        { s: buyer.id, t: it.teacher, p: it.product, sub: it.subject, e: it.exam })
    } catch (_) {}
    const from = Math.max(Date.now(), r ? toTime(r.getString("expiresAt")) : 0)
    if (!r) {
      r = new Record(enrollments)
      r.set("student", buyer.id)
      r.set("teacher", it.teacher)
      r.set("product", it.product)
      r.set("title", it.title)
      r.set("subject", it.subject)
      r.set("exam", it.exam)
    }
    r.set("order", order.id)
    r.set("active", true)
    r.set("expiresAt", new Date(from + (it.days || 0) * 24 * 3600 * 1000).toISOString().replace("T", " "))
    app.save(r)
    if (teachers.indexOf(it.teacher) < 0) teachers.push(it.teacher)
  })
  buyer.set("courseTeachers", teachers)
  app.saveNoValidate(buyer)
  const code = order.getString("promo")
  if (code) app.db().newQuery("UPDATE promo_codes SET used = used + 1 WHERE code = {:c}").bind({ c: code }).execute()
  return true
}

/** Узнать у ЮKassa, чем закончилась оплата заказа, и обновить заказ. → статус заказа */
function refresh(app, order) {
  const status = order.getString("status")
  const paymentId = order.getString("paymentId")
  if (status !== "pending" || !paymentId) return status
  const s = settings(app)
  if (!s) return status
  const r = yookassa(s, "GET", "/payments/" + encodeURIComponent(paymentId))
  if (r.status !== 200) return status
  const p = r.json
  const total = order.getFloat("total").toFixed(2)
  const sameOrder = p.metadata && p.metadata.order_id === order.id && p.amount && p.amount.value === total
  if (p.status === "succeeded" && sameOrder) {
    fulfill(app, order.id)
    return "paid"
  }
  if (p.status === "canceled") {
    app.db().newQuery("UPDATE orders SET status = 'canceled' WHERE id = {:id} AND status = 'pending'").bind({ id: order.id }).execute()
    return "canceled"
  }
  return status
}

module.exports = { base64, settings, yookassa, fulfill, refresh, now, toTime, priceItems, applyPromo, normalizeCode }
