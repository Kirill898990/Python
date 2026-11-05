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
  const toTime = (v) => { const t = v ? Date.parse(String(v).replace(" ", "T")) : NaN; return isNaN(t) ? 0 : t }
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

module.exports = { base64, settings, yookassa, fulfill, refresh, now }
