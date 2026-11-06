/// <reference path="../pb_data/types.d.ts" />

// Защита от перебора паролей и потока запросов: встроенный ограничитель PocketBase (Settings → Rate limits).
// Лимиты считаются для каждого IP-адреса. Они заметно больше стандартных, потому что в школе весь класс
// часто выходит в интернет с одного адреса: 30 учеников должны спокойно войти и сдать работы одновременно.
// Администратор (вход в веб-панель) под лимиты не попадает. Поменять лимиты можно в веб-панели.

migrate((app) => {
  const settings = app.settings()
  settings.rateLimits.enabled = true
  settings.rateLimits.rules = [
    // Вход (и в веб-панель тоже): 60 попыток в минуту с одного адреса.
    { label: "*:auth", maxRequests: 60, duration: 60, audience: "" },
    // Регистрация: 40 новых аккаунтов за 10 минут с одного адреса (класс регистрируется на уроке).
    { label: "users:create", maxRequests: 40, duration: 600, audience: "@guest" },
    // Любые новые записи (работы, сообщения): 120 за 10 секунд.
    { label: "*:create", maxRequests: 120, duration: 10, audience: "" },
    { label: "/api/batch", maxRequests: 5, duration: 1, audience: "" },
    // Магазин: оформление заказа и проверка промокода.
    { label: "/api/shop/", maxRequests: 60, duration: 60, audience: "" },
    // Нейросеть: каждый запрос стоит денег.
    { label: "/api/generate-tasks", maxRequests: 10, duration: 60, audience: "" },
    // Гостям (без входа) — 300 запросов за 10 секунд, ученикам и учителям — 1500.
    { label: "/api/", maxRequests: 300, duration: 10, audience: "@guest" },
    { label: "/api/", maxRequests: 1500, duration: 10, audience: "@auth" },
  ]
  app.save(settings)
}, (app) => {
  const settings = app.settings()
  settings.rateLimits.enabled = false
  app.save(settings)
})
