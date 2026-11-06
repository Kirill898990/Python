/// <reference path="../pb_data/types.d.ts" />

// Звонки в курсе: ученик курса и его учитель звонят друг другу прямо на сайте и в приложении (WebRTC).
// Голос и видео идут напрямую между телефонами/компьютерами, а если напрямую не получается —
// через свой TURN-сервер (coturn на этом же сервере, его ставит install.sh). Сторонние сервисы не нужны.
//
// Чтобы договориться о соединении, стороны пишут друг другу служебные сообщения в call_signals
// (вызов, ответ, описание соединения, конец звонка). Кто кому может писать — в правилах коллекции.
//
// GET /api/call/ice      — адреса STUN/TURN и временный пароль для TURN (на сутки).
// GET /api/call/incoming — кто сейчас звонит этому человеку (для приложения: оно спрашивает раз в несколько секунд).

routerAdd("GET", "/api/call/ice", (e) => {
  const { iceServers } = require(`${__hooks}/call_lib.js`)
  const host = $os.getenv("TURN_HOST") || String(e.request.host || "").replace(/:\d+$/, "")
  return e.json(200, { iceServers: iceServers($os.getenv("TURN_SECRET"), host, e.auth.id) })
}, $apis.requireAuth("users"))

routerAdd("GET", "/api/call/incoming", (e) => {
  const iso = (t) => new Date(t).toISOString().replace("T", " ")
  const rings = e.app.findRecordsByFilter("call_signals", "to = {:me} && type = 'ring' && created > {:t}", "-created", 5, 0,
    { me: e.auth.id, t: iso(Date.now() - 45 * 1000) })
  for (const r of rings) {
    const done = e.app.countRecords("call_signals",
      $dbx.exp("call = {:c} AND type IN ('accept', 'decline', 'busy', 'hangup')", { c: r.getString("call") }))
    if (done) continue
    let data = {}
    try { data = JSON.parse(r.getString("data") || "{}") || {} } catch (_) {}
    return e.json(200, {
      call: {
        id: r.getString("call"), enrollment: r.getString("enrollment"), from: r.getString("from"),
        name: data.name || "", video: !!data.video, created: r.getString("created"),
      },
    })
  }
  return e.json(200, { call: null })
}, $apis.requireAuth("users"))

onRecordCreateRequest((e) => {
  if (e.hasSuperuserAuth()) return e.next()
  if (e.record.getString("type") === "ring") {
    const iso = (t) => new Date(t).toISOString().replace("T", " ")
    const recent = e.app.countRecords("call_signals",
      $dbx.exp("[[from]] = {:f} AND type = 'ring' AND created > {:t}", { f: e.auth.id, t: iso(Date.now() - 60 * 1000) }))
    if (recent >= 5) throw new TooManyRequestsError("Слишком много звонков подряд. Подождите минуту.")
    let data = {}
    try { data = JSON.parse(e.record.getString("data") || "{}") || {} } catch (_) {}
    // Имя звонящего пишет сервер — его увидит тот, кому звонят.
    e.record.set("data", { video: !!data.video, name: e.auth.getString("name"), teacher: e.auth.getBool("isTeacher") })
  }
  e.next()
}, "call_signals")

// Служебные сообщения звонков нужны только во время звонка — раз в час удаляем те, что старше суток.
cronAdd("call_signals_cleanup", "23 * * * *", () => {
  $app.db().newQuery("DELETE FROM call_signals WHERE created < {:t}")
    .bind({ t: new Date(Date.now() - 24 * 3600 * 1000).toISOString().replace("T", " ") }).execute()
})
