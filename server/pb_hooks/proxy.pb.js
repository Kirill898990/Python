/// <reference path="../pb_data/types.d.ts" />

// Работа за посредником: за Caddy или (на домашнем сервере) за туннелем Cloudflare. Тогда PocketBase слушает
// только 127.0.0.1:8090 (это настраивает install.sh и пишет BEHIND_PROXY=1 в server.env), а настоящий адрес
// посетителя приходит в заголовке — по нему работают лимиты запросов и защита от подбора паролей.
// У Caddy это X-Forwarded-For, у Cloudflare — CF-Connecting-IP (PROXY_IP_HEADER в server.env).
// Без посредника заголовку верить нельзя (его может подделать любой), поэтому настройку выключаем.
onBootstrap((e) => {
  e.next()
  const header = $os.getenv("PROXY_IP_HEADER") || "X-Forwarded-For"
  const want = $os.getenv("BEHIND_PROXY") === "1" ? [header] : []
  const s = $app.settings()
  const have = Array.from(s.trustedProxy.headers || [])
  if (have.join(",") !== want.join(",")) {
    s.trustedProxy.headers = want
    s.trustedProxy.useLeftmostIP = false
    $app.save(s)
  }
})
