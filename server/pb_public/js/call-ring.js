/*
 * Входящий звонок на сайте: пока сайт открыт, ученик курса и учитель курса видят вверху «… звонит» и могут ответить.
 * Сообщения о звонках приходят сразу (realtime PocketBase), сам звонок открывается на странице call.html.
 */
(function () {
  "use strict";
  const { h, button } = UI;

  let unsubscribe = null;
  let watchingFor = "";
  let banner = null;
  let ringing = null; // {call, timer, tone}

  /** Гудок входящего звонка (если браузер разрешил звук). */
  function startTone() {
    try {
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      const beep = () => {
        [0, 0.25].forEach((delay) => {
          const osc = ctx.createOscillator();
          const gain = ctx.createGain();
          osc.frequency.value = 880;
          gain.gain.setValueAtTime(0.0001, ctx.currentTime + delay);
          gain.gain.exponentialRampToValueAtTime(0.15, ctx.currentTime + delay + 0.02);
          gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + delay + 0.2);
          osc.connect(gain).connect(ctx.destination);
          osc.start(ctx.currentTime + delay);
          osc.stop(ctx.currentTime + delay + 0.22);
        });
      };
      beep();
      const timer = setInterval(beep, 2000);
      return () => { clearInterval(timer); ctx.close().catch(() => {}); };
    } catch (_) {
      return () => {};
    }
  }

  function stopRinging() {
    if (!ringing) return;
    clearTimeout(ringing.timer);
    ringing.tone();
    ringing = null;
    if (banner) banner.remove();
    banner = null;
  }

  function show(record) {
    if (ringing || /call\.html/.test(location.pathname)) return;
    const data = record.data || {};
    const who = data.name || (data.teacher ? "Учитель" : "Ученик курса");
    const answer = () => {
      stopRinging();
      location.href = Api.callUrl({ e: record.enrollment, call: record.call, answer: 1, video: data.video ? 1 : "" });
    };
    const decline = () => {
      stopRinging();
      Api.callSignal(record.call, record.from, record.enrollment, "decline").catch(() => {});
    };
    banner = h("div", { class: "ring-banner", role: "alertdialog", "aria-label": "Входящий звонок" },
      h("span", { class: "ring-icon", "aria-hidden": "true" }, data.video ? "🎥" : "📞"),
      h("div", { class: "grow" },
        h("div", { class: "bold" }, who),
        h("div", { class: "small" }, data.video ? "Видеозвонок по курсу" : "Звонок по курсу")),
      button("Отклонить", decline, { cls: "ring-decline" }),
      button("Ответить", answer, { cls: "ring-accept" }));
    document.body.append(banner);
    // Через 45 секунд звонящий сам кладёт трубку.
    ringing = { call: record.call, tone: startTone(), timer: setTimeout(stopRinging, 45000) };
  }

  function onSignal(e) {
    const r = e && e.record;
    const me = Api.state.user;
    if (!r || e.action !== "create" || !me || r.to !== me.id) return;
    if (r.type === "ring") {
      // Звонок старше 45 секунд (страницу открыли позже) уже не показываем.
      if (Date.now() - Content.timeOf(r.created) < 45000) show(r);
    } else if (ringing && r.call === ringing.call && (r.type === "hangup" || r.type === "accept")) {
      stopRinging();
    }
  }

  /** Слушаем звонки, только если у человека есть курс (ученик купил или учитель ведёт). */
  async function update() {
    const me = Api.state.user;
    const id = me ? me.id : "";
    if (id === watchingFor) return;
    watchingFor = id;
    if (unsubscribe) { unsubscribe(); unsubscribe = null; }
    stopRinging();
    if (!me) return;
    try {
      const ens = await Api.enrollments();
      if (watchingFor !== id || !ens.some((en) => en.active)) return;
      unsubscribe = Api.realtime.subscribe(onSignal, "call_signals");
    } catch (_) { /* нет связи — не страшно, звонок можно принять в следующий раз */ }
  }

  Api.onChange((what) => { if (what === "user") update(); });
  window.addEventListener("load", update);
  window.CallRing = { update };
})();
