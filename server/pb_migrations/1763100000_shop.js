/// <reference path="../pb_data/types.d.ts" />

// Магазин beemei и личные курсы подготовки к ОГЭ и ЕГЭ.
//
// shop_settings — ключи ЮKassa (shopId и секретный ключ). Заполняет только администратор в веб-панели.
// products      — товары магазина: название, описание, картинка, сроки подписки с ценами (plans),
//                 какие учителя ведут курс (пусто — любой подтверждённый), какие предметы и экзамены можно выбрать.
//                 Добавляет и меняет только администратор.
// shop_info     — данные продавца для оферты и контактов (ФИО, ИНН, почта, телефон, условия возврата).
//                 Видны всем, меняет только администратор.
// orders        — заказы: кто купил, что (с выбранным предметом и экзаменом), сумма и статус оплаты.
//                 Создаёт и отмечает оплаченными только сервер (pb_hooks/shop.pb.js) по ответу ЮKassa.
// enrollments   — купленные курсы: ученик, учитель, предмет, экзамен и до какого времени открыт (expiresAt).
//                 Пока срок не кончился, у ученика есть «Подготовка», а у учителя — ученик во вкладке «Курсы».
//                 Повторная покупка того же курса у того же учителя продлевает срок.
// practices.enrollment — практика для одного ученика курса (её видит только он).
// messages kind = "course" — чат ученика курса с учителем.
// users.courseTeachers — учителя курсов ученика: им видно имя ученика (для «Мои курсы» и работ).

const TEACHER = "@request.auth.isTeacher = true"
const SIGNED_IN = '@request.auth.id != ""'

migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  const practices = app.findCollectionByNameOrId("practices")

  const settings = new Collection({ type: "base", name: "shop_settings" })
  settings.fields.add(new TextField({ name: "shopId", required: true, max: 50 }))
  settings.fields.add(new TextField({ name: "secretKey", required: true, max: 200 }))
  settings.fields.add(new URLField({ name: "apiUrl" }))
  settings.fields.add(new URLField({ name: "siteUrl" }))
  settings.fields.add(new AutodateField({ name: "created", onCreate: true }))
  settings.fields.add(new AutodateField({ name: "updated", onCreate: true, onUpdate: true }))
  settings.listRule = null
  settings.viewRule = null
  settings.createRule = null
  settings.updateRule = null
  settings.deleteRule = null
  app.save(settings)

  const products = new Collection({ type: "base", name: "products" })
  products.fields.add(new TextField({ name: "title", required: true, max: 200 }))
  products.fields.add(new TextField({ name: "description", max: 5000 }))
  // Самая низкая цена (для «от 2 000 ₽» на витрине). Цены по срокам — в plans: [{days, price}].
  products.fields.add(new NumberField({ name: "price", required: true, min: 1 }))
  products.fields.add(new JSONField({ name: "plans", maxSize: 2000 }))
  products.fields.add(new NumberField({ name: "oldPrice", min: 0 }))
  products.fields.add(new FileField({
    name: "image", maxSelect: 1, maxSize: 5 * 1024 * 1024,
    mimeTypes: ["image/jpeg", "image/png", "image/webp"], thumbs: ["600x600", "300x300"],
  }))
  products.fields.add(new TextField({ name: "badge", max: 30 }))
  products.fields.add(new TextField({ name: "category", max: 50 }))
  products.fields.add(new RelationField({ name: "teachers", maxSelect: 50, collectionId: users.id }))
  products.fields.add(new JSONField({ name: "subjects", maxSize: 500 }))
  products.fields.add(new JSONField({ name: "exams", maxSize: 500 }))
  products.fields.add(new BoolField({ name: "active" }))
  products.fields.add(new NumberField({ name: "position", onlyInt: true }))
  products.fields.add(new AutodateField({ name: "created", onCreate: true }))
  products.fields.add(new AutodateField({ name: "updated", onCreate: true, onUpdate: true }))
  // Витрину видят все, даже без входа. Менять товары может только администратор.
  products.listRule = "active = true"
  products.viewRule = "active = true"
  products.createRule = null
  products.updateRule = null
  products.deleteRule = null
  app.save(products)

  const info = new Collection({ type: "base", name: "shop_info" })
  info.fields.add(new TextField({ name: "sellerName", max: 200 }))
  info.fields.add(new TextField({ name: "inn", max: 20 }))
  info.fields.add(new TextField({ name: "email", max: 200 }))
  info.fields.add(new TextField({ name: "phone", max: 50 }))
  info.fields.add(new TextField({ name: "address", max: 300 }))
  info.fields.add(new TextField({ name: "refundPolicy", max: 5000 }))
  info.fields.add(new AutodateField({ name: "updated", onCreate: true, onUpdate: true }))
  info.listRule = ""
  info.viewRule = ""
  info.createRule = null
  info.updateRule = null
  info.deleteRule = null
  app.save(info)

  const orders = new Collection({ type: "base", name: "orders" })
  orders.fields.add(new RelationField({ name: "buyer", required: true, maxSelect: 1, collectionId: users.id }))
  orders.fields.add(new JSONField({ name: "items", maxSize: 20000 }))
  orders.fields.add(new NumberField({ name: "total", min: 0 }))
  orders.fields.add(new TextField({ name: "status", max: 20 }))
  orders.fields.add(new TextField({ name: "paymentId", max: 100 }))
  orders.fields.add(new DateField({ name: "paidAt" }))
  orders.fields.add(new TextField({ name: "email", max: 200 }))
  orders.fields.add(new TextField({ name: "phone", max: 30 }))
  orders.fields.add(new AutodateField({ name: "created", onCreate: true }))
  orders.fields.add(new AutodateField({ name: "updated", onCreate: true, onUpdate: true }))
  orders.addIndex("idx_orders_payment", false, "paymentId", "")
  orders.addIndex("idx_orders_buyer", false, "buyer, created", "")
  orders.listRule = "buyer = @request.auth.id"
  orders.viewRule = "buyer = @request.auth.id"
  orders.createRule = null
  orders.updateRule = null
  orders.deleteRule = null
  app.save(orders)

  const enrollments = new Collection({ type: "base", name: "enrollments" })
  enrollments.fields.add(new RelationField({ name: "student", required: true, maxSelect: 1, collectionId: users.id, cascadeDelete: true }))
  enrollments.fields.add(new RelationField({ name: "teacher", required: true, maxSelect: 1, collectionId: users.id }))
  enrollments.fields.add(new RelationField({ name: "product", maxSelect: 1, collectionId: products.id }))
  enrollments.fields.add(new RelationField({ name: "order", maxSelect: 1, collectionId: orders.id }))
  enrollments.fields.add(new TextField({ name: "title", max: 200 }))
  enrollments.fields.add(new TextField({ name: "subject", max: 20 }))
  enrollments.fields.add(new TextField({ name: "exam", max: 20 }))
  enrollments.fields.add(new BoolField({ name: "active" }))
  enrollments.fields.add(new DateField({ name: "expiresAt" }))
  enrollments.fields.add(new AutodateField({ name: "created", onCreate: true }))
  enrollments.addIndex("idx_enrollments_student", false, "student", "")
  enrollments.addIndex("idx_enrollments_teacher", false, "teacher", "")
  enrollments.listRule = "student = @request.auth.id || teacher = @request.auth.id"
  enrollments.viewRule = enrollments.listRule
  enrollments.createRule = null
  enrollments.updateRule = null
  enrollments.deleteRule = null
  app.save(enrollments)

  // Учителю курса видно имя ученика курса, даже если это не его ученик по школе.
  users.fields.add(new RelationField({ name: "courseTeachers", maxSelect: 50, collectionId: users.id }))
  users.listRule = `(${users.listRule}) || (${TEACHER} && courseTeachers.id ?= @request.auth.id)`
  users.viewRule = users.listRule
  users.createRule = `(${users.createRule}) && @request.body.courseTeachers:isset = false`
  users.updateRule = `(${users.updateRule}) && @request.body.courseTeachers:isset = false`
  app.save(users)

  // Практики для одного ученика курса: обычные ученики учителя их не видят.
  practices.fields.add(new RelationField({ name: "enrollment", maxSelect: 1, collectionId: enrollments.id }))
  practices.addIndex("idx_practices_enrollment", false, "enrollment", "")
  const course = 'enrollment != "" && enrollment.student = @request.auth.id && enrollment.active = true && enrollment.expiresAt > @now'
  practices.listRule = `${SIGNED_IN} && (owner = @request.auth.id ||` +
    ` (published = true && enrollment = "" && owner = @request.auth.teacher && owner != "") || (published = true && ${course}))`
  practices.viewRule = practices.listRule
  practices.createRule = `${TEACHER} && (owner = @request.auth.id || owner = "") && (enrollment = "" || enrollment.teacher = @request.auth.id)`
  practices.updateRule = `${TEACHER} && owner = @request.auth.id && (@request.body.owner:isset = false || @request.body.owner = @request.auth.id)` +
    " && @request.body.enrollment:isset = false"
  app.save(practices)

  const tasks = app.findCollectionByNameOrId("tasks")
  tasks.listRule = `${SIGNED_IN} && (practice.owner = @request.auth.id ||` +
    ' (practice.published = true && practice.exam = false && practice.enrollment = "" && practice.owner = @request.auth.teacher && practice.owner != "") ||' +
    ' (practice.published = true && practice.exam = false && practice.enrollment != "" && practice.enrollment.student = @request.auth.id' +
    " && practice.enrollment.active = true && practice.enrollment.expiresAt > @now))"
  tasks.viewRule = tasks.listRule
  app.save(tasks)

  // Работы по своим практикам (и курсовым) учитель видит и проверяет.
  const submissions = app.findCollectionByNameOrId("submissions")
  submissions.listRule = `student = @request.auth.id || (${TEACHER} && (student.teacher = @request.auth.id || practice.owner = @request.auth.id))`
  submissions.viewRule = submissions.listRule
  submissions.updateRule = `${TEACHER} && (student.teacher = @request.auth.id || practice.owner = @request.auth.id)`
  submissions.deleteRule = submissions.updateRule
  app.save(submissions)

  const checks = app.findCollectionByNameOrId("checks")
  checks.listRule = `student = @request.auth.id || (${TEACHER} && (student.teacher = @request.auth.id || task.practice.owner = @request.auth.id))`
  checks.viewRule = checks.listRule
  app.save(checks)

  // Чат курса: ученик курса и его учитель.
  const messages = app.findCollectionByNameOrId("messages")
  messages.fields.getByName("kind").values = ["class", "private", "course"]
  messages.fields.add(new RelationField({ name: "enrollment", maxSelect: 1, collectionId: enrollments.id }))
  messages.addIndex("idx_messages_course", false, "kind, enrollment, created", "")
  messages.listRule = `(${messages.listRule}) || (${SIGNED_IN} && kind = "course" && enrollment.student = @request.auth.id)`
  messages.viewRule = messages.listRule
  messages.createRule = `(${messages.createRule}) || (${SIGNED_IN}` +
    " && author = @request.auth.id && authorName = @request.auth.name && fromTeacher = @request.auth.isTeacher" +
    ' && kind = "course" && group = "" && student = "" && enrollment != "" && enrollment.active = true && enrollment.expiresAt > @now' +
    " && (enrollment.student = @request.auth.id || enrollment.teacher = @request.auth.id)" +
    ' && (teacher = "" || teacher = enrollment.teacher))'
  app.save(messages)

  // Первый товар — личный онлайн-курс. Учителя выбирает покупатель (любой подтверждённый учитель).
  // Цены, текст и картинку можно поменять в веб-панели.
  {
    const p = new Record(products)
    p.set("title", "Личный онлайн-курс подготовки к ОГЭ и ЕГЭ")
    p.set("description", [
      "Индивидуальная подготовка к ОГЭ или ЕГЭ по химии или биологии вместе с учителем.",
      "",
      "Что входит:",
      "• персональные практики и тренировочные задания, которые учитель составляет именно под вас;",
      "• проверка каждой работы с оценкой и комментарием;",
      "• личный чат с учителем для вопросов;",
      "• разбор ошибок и план подготовки.",
      "",
      "Как начать: выберите учителя, предмет, экзамен и срок и оплатите курс. Сразу после оплаты в приложении и на сайте",
      "появится раздел «Подготовка». Когда срок закончится, курс можно продлить.",
    ].join("\n"))
    p.set("price", 2000)
    p.set("plans", [{ days: 2, price: 2000 }, { days: 7, price: 7000 }, { days: 14, price: 14000 }, { days: 30, price: 30000 }])
    p.set("badge", "Онлайн-курс")
    p.set("category", "Подготовка к ОГЭ и ЕГЭ")
    p.set("subjects", ["chemistry", "biology"])
    p.set("exams", ["ОГЭ", "ЕГЭ"])
    p.set("active", true)
    p.set("position", 1)
    app.save(p)
  }
  // Данные продавца — заполните в веб-панели (Collections → shop_info), без них ЮKassa не подключит сайт.
  app.save(new Record(info))
}, (app) => {
  const messages = app.findCollectionByNameOrId("messages")
  messages.fields.removeByName("enrollment")
  messages.fields.getByName("kind").values = ["class", "private"]
  app.save(messages)
  const practices = app.findCollectionByNameOrId("practices")
  practices.fields.removeByName("enrollment")
  app.save(practices)
  const users = app.findCollectionByNameOrId("users")
  users.fields.removeByName("courseTeachers")
  app.save(users)
  for (const name of ["enrollments", "orders", "shop_info", "products", "shop_settings"]) {
    try {
      app.delete(app.findCollectionByNameOrId(name))
    } catch (_) {}
  }
})
