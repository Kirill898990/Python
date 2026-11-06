/// <reference path="../pb_data/types.d.ts" />

// Магазин beemei (коллекции — в pb_migrations/1763100000_shop.js).
//
// POST /api/shop/checkout   — оформить заказ: сервер сам берёт цены из базы, создаёт заказ и платёж ЮKassa
//                             и возвращает адрес страницы оплаты (СБП, карта, SberPay — что включено в ЮKassa).
// POST /api/shop/promo     — проверить промокод и посчитать скидку (окончательно сервер считает её ещё раз при оплате).
// GET  /api/shop/orders/:id — статус заказа (если ещё ждём оплату, сервер спрашивает ЮKassa).
// POST /api/shop/yookassa   — уведомления ЮKassa (адрес вписывается в личном кабинете ЮKassa).
//                             Уведомлению на слово не верим: статус платежа сервер перепроверяет у ЮKassa.

routerAdd("POST", "/api/shop/checkout", (e) => {
  const { settings, yookassa, priceItems, applyPromo } = require(`${__hooks}/shop_lib.js`)
  const auth = e.auth
  if (auth.getBool("isTeacher")) throw new BadRequestError("Курсы покупают ученики. Войдите в аккаунт ученика.")
  const s = settings(e.app)
  if (!s) throw new BadRequestError("Оплата пока не подключена. Попробуйте позже.")
  const body = e.requestInfo().body || {}
  const raw = Array.isArray(body.items) ? body.items.slice(0, 10) : []
  if (!raw.length) throw new BadRequestError("Корзина пуста")
  const email = String(body.email || "").trim().slice(0, 200)
  const phone = String(body.phone || "").replace(/[^\d+]/g, "").slice(0, 30)
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email) && phone.replace(/\D/g, "").length < 10) {
    throw new BadRequestError("Укажите почту или телефон — туда придёт чек")
  }

  const subjects = { chemistry: "химия", biology: "биология" }
  const items = priceItems(e.app, raw)
  const promo = applyPromo(e.app, body.promo, items, auth.id)
  const total = promo.total

  const order = new Record(e.app.findCollectionByNameOrId("orders"))
  order.set("buyer", auth.id)
  order.set("items", items)
  order.set("subtotal", promo.subtotal)
  order.set("discount", promo.discount)
  order.set("promo", promo.code)
  order.set("total", total)
  order.set("status", "pending")
  order.set("email", email)
  order.set("phone", phone)
  e.app.save(order)

  const site = (s.getString("siteUrl") || ("https://" + e.request.host)).replace(/\/+$/, "")
  const first = items[0]
  const description = (items.length === 1
    ? `${first.title}: ${subjects[first.subject] || first.subject}, ${first.exam}, ${first.days} дн.`
    : `Заказ beemei: ${items.length} курса`).slice(0, 128)
  let r
  try {
    r = yookassa(s, "POST", "/payments", {
      amount: { value: total.toFixed(2), currency: "RUB" },
      capture: true,
      confirmation: { type: "redirect", return_url: `${site}/#/shop/order/${order.id}` },
      description,
      metadata: { order_id: order.id },
    }, order.id)
  } catch (err) {
    r = { status: 0, json: {} }
  }
  const url = r.json && r.json.confirmation && r.json.confirmation.confirmation_url
  if (r.status !== 200 || !url || !r.json.id) {
    order.set("status", "error")
    e.app.save(order)
    throw new BadRequestError("Не получилось создать платёж. Попробуйте ещё раз через минуту.")
  }
  order.set("paymentId", r.json.id)
  e.app.save(order)
  return e.json(200, { order: order.id, url })
}, $apis.requireAuth("users"))

// Проверить промокод до оплаты: {code, items} → {code, discount, subtotal, total, text}.
routerAdd("POST", "/api/shop/promo", (e) => {
  const { priceItems, applyPromo, normalizeCode } = require(`${__hooks}/shop_lib.js`)
  const body = e.requestInfo().body || {}
  if (!normalizeCode(body.code)) throw new BadRequestError("Впишите промокод")
  const items = priceItems(e.app, body.items)
  if (!items.length) throw new BadRequestError("Корзина пуста")
  return e.json(200, applyPromo(e.app, body.code, items, e.auth ? e.auth.id : ""))
})

routerAdd("GET", "/api/shop/orders/{id}", (e) => {
  const { refresh } = require(`${__hooks}/shop_lib.js`)
  let order
  try {
    order = e.app.findRecordById("orders", e.request.pathValue("id"))
  } catch (_) {}
  if (!order || order.getString("buyer") !== e.auth.id) throw new NotFoundError("Заказ не найден")
  const status = refresh(e.app, order)
  let items = []
  try { items = JSON.parse(order.getString("items") || "[]") || [] } catch (_) {}
  return e.json(200, {
    id: order.id, status, total: order.getFloat("total"), created: order.getString("created"),
    promo: order.getString("promo"), discount: order.getFloat("discount"),
    items: items.map((it) => ({ title: it.title, price: it.price, subject: it.subject, exam: it.exam, days: it.days, teacherName: it.teacherName })),
  })
}, $apis.requireAuth("users"))

routerAdd("POST", "/api/shop/yookassa", (e) => {
  const { refresh } = require(`${__hooks}/shop_lib.js`)
  const body = e.requestInfo().body || {}
  const paymentId = String((body.object && body.object.id) || "").slice(0, 100)
  if (paymentId) {
    try {
      const order = e.app.findFirstRecordByFilter("orders", "paymentId = {:p}", { p: paymentId })
      refresh(e.app, order)
    } catch (_) {}
  }
  // ЮKassa ждёт ответ 200, иначе будет присылать уведомление повторно.
  return e.json(200, { ok: true })
})

// Напоминание на почту за сутки до конца курса — со ссылкой «Продлить».
// Письмо уходит на почту аккаунта (если привязана) или на почту, указанную при покупке. Нужна настроенная почта (SMTP).
cronAdd("course_reminders", "*/20 * * * *", () => {
  const { sendMail, mailReady, escapeHtml } = require(`${__hooks}/mail_lib.js`)
  const { toTime } = require(`${__hooks}/shop_lib.js`)
  if (!mailReady($app)) return
  const iso = (t) => new Date(t).toISOString().replace("T", " ")
  let site = ""
  try { site = $app.findFirstRecordByFilter("shop_settings", "siteUrl != ''").getString("siteUrl") } catch (_) {}
  site = (site || $os.getenv("SITE_URL") || "").replace(/\/+$/, "")
  const subjects = { chemistry: "химия", biology: "биология" }
  const months = ["января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"]
  const list = $app.findRecordsByFilter("enrollments", "active = true && expiresAt > {:now} && expiresAt < {:soon}", "expiresAt", 200, 0,
    { now: iso(Date.now()), soon: iso(Date.now() + 26 * 3600 * 1000) })
  list.forEach((en) => {
    const until = en.getString("expiresAt")
    if (en.getString("remindedFor") === until) return
    // Сначала отмечаем, потом отправляем — чтобы письмо не ушло дважды.
    en.set("remindedFor", until)
    $app.saveNoValidate(en)
    let student
    try { student = $app.findRecordById("users", en.getString("student")) } catch (_) { return }
    let email = student.verified() ? student.email() : ""
    if (!email) {
      try {
        email = $app.findRecordsByFilter("orders", "buyer = {:b} && status = 'paid' && email != ''", "-created", 1, 0, { b: student.id })[0].getString("email")
      } catch (_) {}
    }
    if (!email) return
    const msk = new Date(toTime(until) + 3 * 3600 * 1000)
    const pad = (n) => (n < 10 ? "0" : "") + n
    const when = `${msk.getUTCDate()} ${months[msk.getUTCMonth()]} в ${pad(msk.getUTCHours())}:${pad(msk.getUTCMinutes())} (МСК)`
    const title = `Подготовка к ${en.getString("exam")} · ${subjects[en.getString("subject")] || en.getString("subject")}`
    const link = site && en.getString("product") ? `${site}/#/shop/product/${en.getString("product")}` : ""
    sendMail($app, email, `Курс «${title}» заканчивается завтра`,
      `<p>Здравствуйте, ${escapeHtml(student.getString("name"))}!</p>` +
      `<p>Доступ к курсу «${escapeHtml(title)}» открыт до ${when}. Потом задания и чат с учителем закроются.</p>` +
      (link ? `<p><a href="${link}" style="display:inline-block;padding:10px 18px;background:#187A5F;color:#fff;border-radius:8px;text-decoration:none">Продлить курс</a></p>` +
        "<p>Если продлить, срок прибавится к оставшимся дням — ничего не потеряется.</p>" : "") +
      "<p>— beemei, «Простая химия с Ириной»</p>")
  })
})

// Промокод в веб-панели можно вписать как угодно («sale 10») — храним заглавными без пробелов («SALE10»).
function normalizePromo(e) {
  const { normalizeCode } = require(`${__hooks}/shop_lib.js`)
  e.record.set("code", normalizeCode(e.record.getString("code")))
  e.next()
}
onRecordCreate(normalizePromo, "promo_codes")
onRecordUpdate(normalizePromo, "promo_codes")
