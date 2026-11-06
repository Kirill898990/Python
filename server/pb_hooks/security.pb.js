/// <reference path="../pb_data/types.d.ts" />

// Защита сайта.
//
// 1. Подбор пароля: после 10 неверных паролей к одному логину вход в этот аккаунт закрыт на 15 минут
//    (с любого адреса). Неизвестные логины не запоминаем — им нечего защищать.
//    Ограничение частоты запросов с одного адреса — в pb_migrations/1763400000_security.js.
// 2. Заголовки безопасности: браузер не угадывает тип файлов, сайт нельзя вставить в чужую страницу
//    (защита от «кликджекинга»), камера и микрофон — только для своего сайта, всегда https.
//
// SQL-инъекции: все запросы к базе в обработчиках идут с параметрами ({:name}), фильтры PocketBase
// разбирает сам и тоже передаёт значения параметрами, а права проверяют правила коллекций.

onRecordAuthWithPasswordRequest((e) => {
  if (!e.record) return e.next()
  const store = e.app.store()
  const key = "login_fail:" + e.record.collection().name + ":" + e.record.id
  const now = Date.now()
  let info = store.get(key)
  if (info && info.until > now) {
    const minutes = Math.ceil((info.until - now) / 60000)
    throw new TooManyRequestsError(`Слишком много неверных паролей. Вход в этот аккаунт закрыт на ${minutes} мин. Если забыл пароль, попроси учителя сбросить его.`)
  }
  try {
    e.next()
  } catch (err) {
    if (!info || now - info.since > 15 * 60 * 1000) info = { n: 0, since: now, until: 0 }
    info.n++
    if (info.n >= 10) info = { n: 0, since: now, until: now + 15 * 60 * 1000 }
    store.set(key, info)
    throw err
  }
  store.remove(key)
})

routerUse((e) => {
  const h = e.response.header()
  h.set("X-Content-Type-Options", "nosniff")
  h.set("X-Frame-Options", "SAMEORIGIN")
  h.set("Referrer-Policy", "strict-origin-when-cross-origin")
  h.set("Permissions-Policy", "camera=(self), microphone=(self), geolocation=(), payment=(), usb=()")
  // На настоящем сервере — только https (на своём компьютере для проверки https нет).
  const host = String(e.request.host || "")
  if (!/^(127\.|localhost|\[::1\]|10\.|192\.168\.)/.test(host)) {
    h.set("Strict-Transport-Security", "max-age=31536000")
  }
  return e.next()
})
