/*
 * Маленькая библиотека для построения страниц: элементы, иконки, окна, всплывающие сообщения.
 * Весь текст вставляется как текст (textContent), поэтому ответы учеников не могут «сломать» страницу.
 */
(function () {
  "use strict";

  /** h("div", {class: "card", onclick: fn}, "текст", другойЭлемент) */
  function h(tag, props, ...children) {
    const el = document.createElement(tag);
    if (props) {
      for (const [key, value] of Object.entries(props)) {
        if (value == null || value === false) continue;
        if (key === "class") el.className = value;
        else if (key.startsWith("on") && typeof value === "function") el.addEventListener(key.slice(2), value);
        else if (key === "value") el.value = value;
        else if (key === "checked") el.checked = !!value;
        else if (key === "disabled") el.disabled = !!value;
        else el.setAttribute(key, value === true ? "" : value);
      }
    }
    append(el, children);
    return el;
  }

  function append(el, children) {
    for (const child of children.flat(Infinity)) {
      if (child == null || child === false) continue;
      el.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return el;
  }

  /** Заменяет содержимое элемента. */
  function replace(el, ...children) {
    el.replaceChildren();
    return append(el, children);
  }

  // ---------- Иконки (те же, что в приложении) ----------
  const ICONS = {
    back: "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z",
    chevron: "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z",
    open: "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7zM14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z",
    send: "M2.01,21L23,12 2.01,3 2,10l15,2 -15,2z",
    chat: "M20,2H4c-1.1,0 -1.99,0.9 -1.99,2L2,22l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM6,9h12v2H6V9zM14,14H6v-2h8v2zM18,8H6V6h12v2z",
    delete: "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z",
    check: "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z",
    star: "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z",
    more: "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
    share: "M16,5l-1.42,1.42 -1.59,-1.59V16h-1.98V4.83L9.42,6.42 8,5l4,-4 4,4zM20,10v11c0,1.1 -0.9,2 -2,2H6c-1.11,0 -2,-0.9 -2,-2V10c0,-1.11 0.89,-2 2,-2h3v2H6v11h12V10h-3V8h3c1.1,0 2,0.89 2,2z",
    addBox: "M19,3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM17,13h-4v4h-2v-4H7v-2h4V7h2v4h4v2z",
    phone: "M16,1H8C6.34,1 5,2.34 5,4v16c0,1.66 1.34,3 3,3h8c1.66,0 3,-1.34 3,-3V4c0,-1.66 -1.34,-3 -3,-3zM14,21h-4v-1h4v1zM17.25,18H6.75V4h10.5v14z",
    download: "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z",
    refresh: "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z",
  };

  function icon(name, size = 24) {
    const ns = "http://www.w3.org/2000/svg";
    const svg = document.createElementNS(ns, "svg");
    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("width", size);
    svg.setAttribute("height", size);
    svg.setAttribute("aria-hidden", "true");
    svg.classList.add("icon");
    const path = document.createElementNS(ns, "path");
    path.setAttribute("d", ICONS[name]);
    path.setAttribute("fill", "currentColor");
    svg.append(path);
    return svg;
  }

  // ---------- Готовые элементы ----------

  /** Верхняя панель со стрелкой «Назад». */
  function topBar(title, onBack, ...actions) {
    return h("header", { class: "topbar" },
      h("button", { class: "icon-btn", "aria-label": "Назад", title: "Назад", onclick: onBack }, icon("back")),
      h("h1", { class: "topbar-title" }, title),
      h("div", { class: "topbar-actions" }, actions));
  }

  function emojiBadge(emoji, colorClass, size = "") {
    return h("div", { class: `emoji-badge ${colorClass} ${size}` }, emoji);
  }

  function sectionLabel(text) {
    return h("div", { class: "section-label" }, text);
  }

  function button(text, onclick, { kind = "filled", iconName, disabled, cls = "" } = {}) {
    return h("button", { class: `btn btn-${kind} ${cls}`, onclick, disabled, type: "button" },
      iconName ? icon(iconName, 18) : null, h("span", null, text));
  }

  let fieldCounter = 0;

  /** Поле ввода с подписью сверху, как в приложении. */
  function field({ label, value = "", placeholder, type = "text", multiline = false, rows = 3, oninput, onenter, autocomplete, inputmode, supporting, autocapitalize = "sentences", cls = "" }) {
    const id = "f" + (++fieldCounter);
    const input = multiline
      ? h("textarea", { id, rows, placeholder, autocapitalize, spellcheck: "true", "aria-describedby": id + "-s" })
      : h("input", { id, type, placeholder, autocomplete, inputmode, autocapitalize, spellcheck: "false", "aria-describedby": id + "-s" });
    input.value = value;
    input.addEventListener("input", () => oninput && oninput(input.value));
    if (onenter && !multiline) input.addEventListener("keydown", (e) => { if (e.key === "Enter") onenter(); });
    const support = h("div", { class: "field-support", id: id + "-s" }, supporting || "");
    const wrap = h("div", { class: `field ${cls}` },
      h("label", { class: "field-label", for: id }, label),
      input,
      support);
    wrap.input = input;
    wrap.setError = (message) => {
      wrap.classList.toggle("field-error", !!message);
      input.setAttribute("aria-invalid", message ? "true" : "false");
      support.textContent = message || supporting || "";
    };
    /** Красная рамка без смены текста под полем. */
    wrap.setInvalid = (invalid) => {
      wrap.classList.toggle("field-error", !!invalid);
      input.setAttribute("aria-invalid", invalid ? "true" : "false");
    };
    wrap.setSupport = (text) => { support.textContent = text || ""; };
    return wrap;
  }

  /** Сегментированная кнопка выбора: options = [{value, label}] */
  function segmented(options, selected, onchange) {
    const root = h("div", { class: "segmented", role: "radiogroup" });
    function render(current) {
      replace(root, options.map((o) => h("button", {
        type: "button",
        role: "radio",
        "aria-checked": String(o.value === current),
        class: "segment" + (o.value === current ? " selected" : ""),
        onclick: () => { render(o.value); onchange(o.value); },
      }, o.value === current ? icon("check", 18) : null, h("span", null, o.label))));
    }
    render(selected);
    return root;
  }

  function chip(label, selected, onclick) {
    return h("button", { type: "button", class: "chip" + (selected ? " selected" : ""), "aria-pressed": String(selected), onclick },
      selected ? icon("check", 16) : null, label);
  }

  function progressBar(fraction, colorClass = "") {
    const bar = h("div", { class: `progress ${colorClass}`, role: "progressbar", "aria-valuemin": "0", "aria-valuemax": "100", "aria-valuenow": String(Math.round(fraction * 100)) },
      h("div", { class: "progress-fill" }));
    bar.firstChild.style.width = `${Math.max(0, Math.min(1, fraction)) * 100}%`;
    return bar;
  }

  /** Кружок с оценкой. 0 — «На проверке». */
  function gradeBadge(grade, big = false) {
    if (!grade) return h("span", { class: "pill pill-muted" }, "На проверке");
    const cls = grade === 5 ? "g5" : grade === 4 ? "g4" : grade === 3 ? "g3" : "g2";
    return h("span", { class: `grade ${cls}${big ? " grade-big" : ""}`, "aria-label": `Оценка ${grade}` }, String(grade));
  }

  /** Плашка с результатом проверки: success | warning | error */
  function feedback(text, kind) {
    const prefix = kind === "success" ? "✅ " : kind === "warning" ? "🤔 " : "❌ ";
    return h("div", { class: `feedback feedback-${kind}`, role: "status" }, prefix + text);
  }

  function loading() {
    return h("div", { class: "center-box" }, h("div", { class: "spinner", "aria-label": "Загрузка" }));
  }

  function errorBox(message, onRetry) {
    return h("div", { class: "center-box" },
      h("div", { class: "big-emoji" }, "😕"),
      h("p", null, message),
      button("Попробовать ещё раз", onRetry, { kind: "outline" }));
  }

  function emptyBox(emoji, text) {
    return h("div", { class: "center-box muted" }, h("div", { class: "big-emoji" }, emoji), h("p", null, text));
  }

  // ---------- Окна ----------

  /**
   * Открывает окно. build(close) возвращает {title, body, actions}.
   * Окно закрывается кнопкой, клавишей Esc или кликом мимо окна.
   */
  function dialog(build, { wide = false } = {}) {
    const dlg = h("dialog", { class: "dialog" + (wide ? " dialog-wide" : "") });
    const close = () => { dlg.close(); };
    dlg.addEventListener("close", () => dlg.remove());
    dlg.addEventListener("click", (e) => { if (e.target === dlg) close(); });
    const { title, body, actions } = build(close);
    append(dlg, [
      h("h2", { class: "dialog-title" }, title),
      h("div", { class: "dialog-body" }, body),
      h("div", { class: "dialog-actions" }, actions),
    ]);
    document.body.append(dlg);
    dlg.showModal();
    return { close, element: dlg };
  }

  /** Окно «Вы уверены?». Возвращает Promise<boolean>. */
  function confirm(title, text, okText) {
    return new Promise((resolve) => {
      let answered = false;
      const d = dialog((close) => ({
        title,
        body: h("p", null, text),
        actions: [
          button("Отмена", () => close(), { kind: "text" }),
          button(okText, () => { answered = true; close(); }, { kind: "text" }),
        ],
      }));
      d.element.addEventListener("close", () => resolve(answered));
    });
  }

  /** Короткое сообщение внизу экрана. */
  function toast(message) {
    const old = document.querySelector(".toast");
    if (old) old.remove();
    const el = h("div", { class: "toast", role: "status" }, message);
    document.body.append(el);
    setTimeout(() => el.classList.add("toast-hide"), 3000);
    setTimeout(() => el.remove(), 3400);
  }

  /** Маленькое меню у кнопки «⋮». items = [{label, onclick}] */
  function menuButton(items) {
    const wrap = h("div", { class: "menu-wrap" });
    const menu = h("div", { class: "menu", role: "menu", hidden: true },
      items.map((it) => h("button", { type: "button", role: "menuitem", class: "menu-item", onclick: () => { menu.hidden = true; it.onclick(); } }, it.label)));
    const btn = h("button", { class: "icon-btn", "aria-label": "Ещё", title: "Ещё", "aria-haspopup": "menu", onclick: (e) => { e.stopPropagation(); menu.hidden = !menu.hidden; } }, icon("more"));
    return append(wrap, [btn, menu]);
  }

  // Клик в любом месте страницы закрывает открытые меню.
  document.addEventListener("click", () => document.querySelectorAll(".menu").forEach((m) => { m.hidden = true; }));

  window.UI = {
    h, append, replace, icon, topBar, emojiBadge, sectionLabel, button, field, segmented, chip,
    progressBar, gradeBadge, feedback, loading, errorBox, emptyBox, dialog, confirm, toast, menuButton,
  };
})();
