/// <reference path="../pb_data/types.d.ts" />

// Работа за Caddy. Когда на сервере стоит мессенджер beemes, https для обоих сайтов получает Caddy,
// а PocketBase слушает только 127.0.0.1:8090 (это настраивает install.sh и пишет BEHIND_PROXY=1 в server.env).
// Тогда настоящий адрес посетителя приходит в заголовке X-Forwarded-For — по нему работают лимиты запросов.
// Без Caddy заголовку верить нельзя (его может подделать любой), поэтому настройку выключаем.
onBootstrap((e) => {
  e.next()
  const want = $os.getenv("BEHIND_PROXY") === "1" ? ["X-Forwarded-For"] : []
  const s = $app.settings()
  const have = Array.from(s.trustedProxy.headers || [])
  if (have.join(",") !== want.join(",")) {
    s.trustedProxy.headers = want
    s.trustedProxy.useLeftmostIP = false
    $app.save(s)
  }
})
