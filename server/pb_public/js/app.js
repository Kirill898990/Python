/*
 * Запуск сайта и переходы между экранами.
 * Адреса экранов: #/ (главная), #/subject/chemistry, #/practice/<id>, #/theory, #/cabinet,
 * #/submission/<id>, #/admin/works|tasks|students, #/admin/practice/<id или new>,
 * #/chats (список чатов), #/chat/class/<класс>, #/chat/private/<id ученика>.
 */
(function () {
  "use strict";
  const root = document.getElementById("app");

  const routes = [
    { pattern: /^$/, screen: () => Screens.home(), name: "home" },
    { pattern: /^subject\/(chemistry|biology)$/, screen: (m) => Screens.subject({ subject: m[1] }), name: "subject" },
    { pattern: /^theory$/, screen: () => Screens.theory(), name: "theory" },
    { pattern: /^trainer$/, screen: () => Trainer.screen(), name: "trainer" },
    { pattern: /^shop$/, screen: () => Shop.catalog(), name: "shop" },
    { pattern: /^shop\/product\/([a-z0-9]+)$/, screen: (m) => Shop.product({ id: m[1] }), name: "shop-product" },
    { pattern: /^shop\/cart$/, screen: () => Shop.cart(), name: "shop-cart" },
    { pattern: /^shop\/checkout$/, screen: () => Shop.checkout(), name: "shop-checkout" },
    { pattern: /^shop\/order\/([a-z0-9]+)$/, screen: (m) => Shop.order({ id: m[1] }), name: "shop-order" },
    { pattern: /^course\/([a-z0-9]+)$/, screen: (m) => Shop.course({ id: m[1] }), name: "course" },
    { pattern: /^admin\/course\/([a-z0-9]+)$/, screen: (m) => Shop.teacherCourse({ id: m[1] }), name: "teacher-course", teacher: true },
    { pattern: /^admin\/course\/([a-z0-9]+)\/new\/(chemistry|biology)\/(\d{1,2})$/,
      screen: (m) => Teacher.editor({ id: "new", enrollment: m[1], subject: m[2], grade: +m[3] }), name: "editor", teacher: true },
    { pattern: /^practice\/([a-z0-9]+)$/, screen: (m) => Screens.practice({ id: m[1] }), name: "practice" },
    { pattern: /^cabinet$/, screen: () => Screens.cabinet(), name: "cabinet" },
    { pattern: /^submission\/([a-z0-9]+)$/, screen: (m) => Screens.submission({ id: m[1] }), name: "submission" },
    { pattern: /^chats$/, screen: () => Chat.list(), name: "chats" },
    { pattern: /^reference(?:\/(solubility))?$/, screen: (m) => ReferenceScreen.screen({ tab: m[1] }), name: "reference" },
    { pattern: /^chat\/(class|private|course)\/([^/]{1,40})$/, screen: (m) => Chat.room({ kind: m[1], key: m[2] }), name: "chat" },
    { pattern: /^admin\/practice\/([a-z0-9]+)$/, screen: (m) => Teacher.editor({ id: m[1] }), name: "editor", teacher: true },
    { pattern: /^admin(?:\/(works|journal|tasks|students|courses))?$/, screen: (m) => Teacher.admin({ tab: m[1] }), name: "admin", teacher: true },
  ];

  let current = null;

  function path() {
    return decodeURIComponent(location.hash.replace(/^#\/?/, "")).replace(/\/+$/, "");
  }

  function render() {
    const user = Api.state.user;
    let screen;
    if (!user) {
      current = "auth";
      screen = Screens.auth();
    } else if (user.wantsTeacher && !user.isTeacher) {
      current = "pending";
      screen = Screens.pendingTeacher();
    } else {
      const p = path();
      const route = routes.find((r) => r.pattern.test(p));
      if (!route) {
        App.replace("");
        return;
      }
      current = route.name;
      if (route.teacher && !user.isTeacher) {
        screen = UI.h("div", { class: "screen" },
          UI.topBar("Нет доступа", () => App.back("")),
          UI.emptyBox("🔒", "Этот раздел доступен только учителю"));
      } else {
        screen = route.screen(p.match(route.pattern));
      }
    }
    // Экран, который закрываем, может попросить что-то за собой убрать (например, отписаться от чата).
    const old = root.firstElementChild;
    if (old && typeof old.onLeave === "function") old.onLeave();
    root.replaceChildren(screen);
    updateTitle();
  }

  function updateTitle() {
    const title = root.querySelector(".topbar-title");
    document.title = title ? `${title.textContent} — Простая химия` : "Простая химия";
  }

  // ----- История переходов: кнопка «Назад» ведёт туда, откуда пришли -----
  function depth() {
    return (history.state && history.state.depth) || 0;
  }

  let replacing = false;
  window.addEventListener("hashchange", () => {
    // Новый переход — на шаг глубже; замена адреса — на той же глубине; «Назад» — глубина из истории.
    if (!history.state) history.replaceState({ depth: (window.__lastDepth || 0) + (replacing ? 0 : 1) }, "");
    replacing = false;
    window.__lastDepth = depth();
    render();
    window.scrollTo(0, 0);
  });

  window.App = {
    render,
    go(to) {
      location.hash = "#/" + to;
    },
    /** Назад по истории, а если сайт открыт сразу на этой странице — на страницу fallback. */
    back(fallback) {
      if (depth() > 0) history.back();
      else App.replace(fallback);
    },
    /** Открыть экран вместо текущего (без новой записи в истории). */
    replace(to) {
      if (location.hash === "#/" + to) {
        render();
        return;
      }
      replacing = true;
      location.replace("#/" + to);
    },
  };

  // При вводе текста экран не перерисовываем, чтобы не сбить курсор.
  Api.onChange((what) => {
    if (what === "user") {
      render();
      return;
    }
    const screens = what === "submissions" ? ["home", "subject"] : ["home", "subject", "theory", "admin"];
    if ((what === "content" || what === "submissions") && screens.includes(current)) {
      const active = document.activeElement;
      if (!active || !["INPUT", "TEXTAREA"].includes(active.tagName)) render();
    }
  });

  // Тень у верхней панели при прокрутке, как в приложении.
  window.addEventListener("scroll", () => {
    const bar = root.querySelector(".topbar");
    if (bar) bar.classList.toggle("scrolled", window.scrollY > 4);
  }, { passive: true });

  window.__lastDepth = depth();
  render();
  if (Api.state.user) {
    Api.refreshSession().then(() => Promise.all([Api.refreshContent(), Api.refreshMySubmissions()]));
  }
})();
