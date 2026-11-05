/// <reference path="../pb_data/types.d.ts" />

// Магазин beemei (коллекции — в pb_migrations/1763100000_shop.js).
//
// POST /api/shop/checkout   — оформить заказ: сервер сам берёт цены из базы, создаёт заказ и платёж ЮKassa
//                             и возвращает адрес страницы оплаты (СБП, карта, SberPay — что включено в ЮKassa).
// GET  /api/shop/orders/:id — статус заказа (если ещё ждём оплату, сервер спрашивает ЮKassa).
// POST /api/shop/yookassa   — уведомления ЮKassa (адрес вписывается в личном кабинете ЮKassa).
//                             Уведомлению на слово не верим: статус платежа сервер перепроверяет у ЮKassa.

routerAdd("POST", "/api/shop/checkout", (e) => {
  const { settings, yookassa } = require(`${__hooks}/shop_lib.js`)
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
  const items = []
  const seen = {}
  raw.forEach((x) => {
    let p
    try {
      p = e.app.findRecordById("products", String((x && x.product) || "").replace(/[^a-z0-9]/gi, ""))
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
    // Срок и цену берём из товара, а не из запроса.
    const plan = plans.find((pl) => pl && pl.days === parseInt(x.days, 10))
    if (plans.length && !plan) throw new BadRequestError("Выберите срок подписки")
    const days = plan ? plan.days : 30
    const price = plan ? Number(plan.price) : p.getFloat("price")
    // Учитель: из списка учителей курса, а если список пуст — любой подтверждённый учитель.
    const teacherId = String(x.teacher || "").replace(/[^a-z0-9]/gi, "")
    const allowedTeachers = p.getStringSlice("teachers")
    let teacher = null
    try { teacher = e.app.findRecordById("users", teacherId) } catch (_) {}
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
  const total = items.reduce((sum, it) => sum + it.price, 0)

  const order = new Record(e.app.findCollectionByNameOrId("orders"))
  order.set("buyer", auth.id)
  order.set("items", items)
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
