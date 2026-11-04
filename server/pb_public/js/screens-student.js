/*
 * Экраны сайта: вход, главная, предмет, теория, практика, личный кабинет, сданная работа.
 * Повторяют экраны Android-приложения (android/app/src/main/java/.../ui).
 */
(function () {
  "use strict";
  const { h, replace, icon, topBar, emojiBadge, sectionLabel, button, field, segmented, progressBar, gradeBadge, feedback, loading, errorBox, emptyBox, confirm, menuButton } = UI;
  const { content } = Api;

  /** Показывает загрузку, потом результат или ошибку с кнопкой «Попробовать ещё раз». */
  function asyncBlock(load, render) {
    const box = h("div", { class: "stack" });
    const run = () => {
      replace(box, loading());
      load().then((value) => replace(box, render(value)), (e) => replace(box, errorBox(e.message || "Ошибка", run)));
    };
    run();
    box.reload = run;
    return box;
  }

  // ======================= Вход и регистрация =======================
  function auth() {
    let register = false;
    let busy = false;
    const values = { name: "", group: "", username: "", password: "", password2: "" };
    const formBox = h("div", { class: "auth-form" });

    function renderForm() {
      const fields = {};
      const make = (key, opts) => {
        fields[key] = field({ ...opts, value: values[key], oninput: (v) => { values[key] = v; } });
        return fields[key];
      };
      const error = h("div", { class: "dialog-error", role: "alert" });
      const submitBtn = button(register ? "Зарегистрироваться" : "Войти", submit, { cls: "btn-block btn-tall" });

      async function submit() {
        if (busy) return;
        Object.values(fields).forEach((f) => f.setError(null));
        error.textContent = "";
        const errors = {};
        if (register && values.name.trim().length < 2) errors.name = "Впиши имя и фамилию";
        if (register && !values.group.trim()) errors.group = "Впиши класс, например 8Б";
        if (values.username.trim().length < 3) errors.username = "Логин: не меньше 3 символов";
        if (values.password.length < 6) errors.password = "Пароль: не меньше 6 символов";
        if (register && values.password2 !== values.password) errors.passwordConfirm = "Пароли не совпадают";
        if (Object.keys(errors).length) return showErrors(errors);
        busy = true;
        replace(submitBtn, h("span", { class: "spinner spinner-sm" }));
        try {
          if (register) await Api.register(values.name, values.group, values.username, values.password, values.password2);
          else await Api.login(values.username, values.password);
        } catch (e) {
          busy = false;
          replace(submitBtn, h("span", null, register ? "Зарегистрироваться" : "Войти"));
          if (e.fieldErrors && Object.keys(e.fieldErrors).length) showErrors(e.fieldErrors);
          else error.textContent = e.message;
        }
      }

      function showErrors(errors) {
        const map = { identity: "username", passwordConfirm: "password2" };
        Object.entries(errors).forEach(([k, msg]) => {
          const f = fields[map[k] || k];
          if (f) f.setError(msg);
          else error.textContent = msg;
        });
      }

      replace(formBox,
        segmented([{ value: false, label: "Вход" }, { value: true, label: "Регистрация" }], register, (v) => { register = v; renderForm(); }),
        register && make("name", { label: "Имя и фамилия", placeholder: "Петя Иванов", autocomplete: "name", autocapitalize: "words" }),
        register && make("group", { label: "Класс", placeholder: "8Б", autocapitalize: "characters" }),
        make("username", {
          label: "Логин", placeholder: "petya", autocomplete: "username", autocapitalize: "none",
          supporting: register ? "Латинские буквы и цифры, его ты будешь вводить при входе" : null,
        }),
        make("password", {
          label: "Пароль", type: "password", autocomplete: register ? "new-password" : "current-password",
          supporting: register ? "Не меньше 6 символов" : null, onenter: register ? null : submit,
        }),
        register && make("password2", { label: "Повтори пароль", type: "password", autocomplete: "new-password", onenter: submit }),
        error,
        submitBtn,
        h("p", { class: "tiny muted center" }, register
          ? "Учителю нужно зарегистрироваться так же, а потом администратор сервера даст ему права учителя."
          : "Забыл пароль? Попроси учителя сбросить его."));
      const username = fields.username.input;
      username.addEventListener("input", () => {
        const fixed = username.value.trim().toLowerCase();
        if (fixed !== username.value) { username.value = fixed; values.username = fixed; }
      });
      if (fields.group) {
        const g = fields.group.input;
        g.addEventListener("input", () => { g.value = g.value.toUpperCase(); values.group = g.value; });
      }
    }

    renderForm();
    return h("div", { class: "auth" },
      emojiBadge("🔬", "c-primary", "big"),
      h("h1", null, "Химия и биология"),
      h("p", { class: "muted small" }, Content.AUTHOR),
      formBox,
      h("div", { class: "auth-install" }, installSection()),
      h("a", { class: "policy-link", href: "privacy.html" }, "Политика конфиденциальности"));
  }

  // ======================= Раздел «Приложение на телефон» =======================

  /** Сайт уже открыт как приложение с экрана «Домой»? Тогда инструкция для iPhone не нужна. */
  function runsAsInstalledApp() {
    return window.navigator.standalone === true ||
      (window.matchMedia && window.matchMedia("(display-mode: standalone)").matches);
  }

  /** Табличка «Скачать в RuStore» и инструкция, как поставить сайт на iPhone. */
  function installSection() {
    const url = (window.APP_CONFIG && window.APP_CONFIG.rustoreUrl) || "";
    const badgeText = h("span", { class: "store-badge-text" },
      h("span", { class: "store-badge-small" }, url ? "Скачать в" : "Скоро в"),
      h("span", { class: "store-badge-big" }, "RuStore"));
    const badge = url
      ? h("a", { class: "store-badge", href: url, target: "_blank", rel: "noopener noreferrer" }, icon("phone", 28), badgeText)
      : h("span", { class: "store-badge store-badge-soon", "aria-disabled": "true" }, icon("phone", 28), badgeText);

    const android = h("div", { class: "card install-card" },
      h("div", { class: "card-title" }, "На Android"),
      h("p", { class: "small muted" }, url
        ? "Установите приложение из RuStore: задания и оценки те же, что на сайте."
        : "Приложение скоро появится в RuStore. Пока можно пользоваться сайтом."),
      badge);

    const step = (n, ...content) => h("li", { class: "install-step" }, h("span", { class: "install-num" }, String(n)), h("span", null, content));
    const iphone = runsAsInstalledApp() ? null : h("div", { class: "card install-card" },
      h("div", { class: "card-title" }, "На iPhone"),
      h("p", { class: "small muted" }, "Добавьте сайт на экран «Домой», и он будет открываться как приложение:"),
      h("ol", { class: "install-steps" },
        step(1, "Откройте этот сайт в ", h("b", null, "Safari"), "."),
        step(2, "Нажмите ", h("b", null, "«Поделиться»"), " ", h("span", { class: "inline-icon" }, icon("share", 18)), " внизу экрана."),
        step(3, "Выберите ", h("b", null, "«На экран „Домой“»"), " ", h("span", { class: "inline-icon" }, icon("addBox", 18)), "."),
        step(4, "Нажмите ", h("b", null, "«Добавить»"), ". На экране появится иконка «Простая химия».")));

    return h("section", { class: "install", "aria-label": "Приложение на телефон" },
      sectionLabel("Приложение на телефон"),
      h("div", { class: "install-grid" }, android, iphone));
  }

  // ======================= Главная =======================
  function home() {
    const user = Api.state.user;
    // Прогресс по сданным работам: одинаковый на сайте и в приложении, на любом устройстве.
    const totalPractices = Api.state.content.practices.length;
    const submitted = new Set(Api.state.mySubmissions.map((s) => s.practice).filter((id) => content.practice(id))).size;

    const menuCard = (href, emoji, colorClass, title, subtitle, highlight = false) =>
      h("a", { class: "card card-link" + (highlight ? " card-highlight" : ""), href },
        emojiBadge(emoji, colorClass),
        h("div", { class: "grow" }, h("div", { class: "card-title" }, title), h("div", { class: "card-sub muted" }, subtitle)),
        icon("chevron"));

    const refreshBtn = button("Обновить", async () => {
      await Promise.all([Api.refreshContent(), Api.refreshMySubmissions()]);
    }, { kind: "text" });

    return h("div", { class: "screen" },
      h("div", { class: "home-header" },
        emojiBadge("🔬", "c-primary", "md"),
        h("div", { class: "grow" },
          h("h1", { class: "home-title" }, "Химия и биология"),
          h("div", { class: "row subtitle-row" }, h("span", { class: "muted small" }, "Теория и практика"),
            h("span", { class: "pill" }, `${Content.allGradesRange()} классы`))),
        h("button", { class: "avatar", title: "Личный кабинет", "aria-label": "Личный кабинет", onclick: () => App.go("cabinet") },
          (user && user.name ? user.name[0] : "?").toUpperCase())),
      Api.state.contentError && h("div", { class: "banner-offline" },
        h("p", null, Api.state.contentError + " Показаны сохранённые задания."), refreshBtn),
      h("img", { class: "banner banner-lab", src: "images/banner-lab.jpg", alt: "" }),
      user && user.isTeacher && menuCard("#/admin/works", "📋", "c-primary", "Панель учителя", "Проверка работ, оценки и задания", true),
      user && Chat.homeCard(),
      totalPractices > 0 && !(user && user.isTeacher) && h("div", { class: "card" },
        h("div", { class: "card-title" }, submitted === totalPractices ? "Все практики сданы! 🎉" : "Твой прогресс"),
        h("p", { class: "muted small mt" }, `Сдано ${submitted} из ${totalPractices} ${Content.practicesWord(totalPractices)}`),
        h("div", { class: "mt" }, progressBar(submitted / totalPractices))),
      sectionLabel("Материалы к занятиям"),
      menuCard("#/subject/biology", "🧬", "c-biology", "Практики по биологии", `${Content.gradeRange("biology")} класс`),
      menuCard("#/subject/chemistry", "⚗️", "c-chemistry", "Практики по химии", `${Content.gradeRange("chemistry")} класс`),
      menuCard("#/theory", "📚", "c-secondary", "Теоретическая часть", "Учебники ЯКласса по классам"),
      h("img", { class: "banner banner-people", src: "images/scientists.jpg", alt: "" }),
      h("div", { class: "footer" },
        h("div", { class: "card-title" }, Content.AUTHOR),
        h("p", { class: "muted small" }, "Понравился сайт? Расскажи, что улучшить"),
        h("a", { class: "btn btn-outline", href: Content.FEEDBACK_URL, target: "_blank", rel: "noopener noreferrer" },
          icon("star", 18), h("span", null, "Оставить отзыв"))),
      installSection(),
      h("p", { class: "center" }, h("a", { class: "policy-link", href: "privacy.html" }, "Политика конфиденциальности")));
  }

  // ======================= Предмет: классы и практики =======================
  function subject(params) {
    const key = params.subject === "biology" ? "biology" : "chemistry";
    const showStatus = !(Api.state.user && Api.state.user.isTeacher);
    const practices = content.practicesOf(key);
    const colorClass = key === "biology" ? "c-biology" : "c-chemistry";

    const gradeCard = (grade) => {
      const list = practices.filter((p) => p.grade === grade);
      const theory = Content.theoryUrl(key, grade);
      return h("div", { class: "card" },
        h("div", { class: "row" },
          h("div", { class: `grade-circle ${colorClass}` }, String(grade)),
          h("div", { class: "grow" },
            h("div", { class: "card-title" }, `${grade} класс`),
            list.length === 0 && h("div", { class: "card-sub" }, "Задания скоро появятся"))),
        list.map((p) => {
          const total = content.tasksOf(p.id).length;
          const last = showStatus ? Api.lastSubmission(p.id) : null;
          const status = !showStatus || !last
            ? `${total} ${Content.tasksWord(total)}` + (showStatus ? " · не сдано" : "")
            : last.grade > 0 ? `Сдано · оценка ${last.grade}` : "Сдано · на проверке";
          return [
            h("div", { class: "divider" }),
            h("a", { class: "practice-row card-link", href: `#/practice/${p.id}` },
              h("div", { class: "grow" },
                h("div", { class: "row" }, h("span", { class: "bold" }, p.title), !p.published && h("span", { class: "pill pill-draft" }, "Черновик")),
                h("div", { class: "card-sub" }, status)),
              last && last.grade > 0 && gradeBadge(last.grade),
              icon("chevron")),
          ];
        }),
        theory && h("a", { class: "btn btn-text mt", href: theory, target: "_blank", rel: "noopener noreferrer" },
          h("span", null, "Теория в ЯКлассе"), icon("open", 16)));
    };

    return h("div", { class: "screen" },
      topBar(key === "biology" ? "Практики по биологии" : "Практики по химии", () => App.back("")),
      Content.grades(key).map(gradeCard));
  }

  // ======================= Теория =======================
  function theory() {
    const groups = ["chemistry", "biology"].map((key) => {
      const s = Content.subjectOf(key);
      const links = Content.THEORY.filter((t) => t.subject === key);
      return [
        sectionLabel(`Теория по предмету «${s.title}»`),
        links.map((t) => h("a", { class: "card card-link", href: t.url, target: "_blank", rel: "noopener noreferrer" },
          emojiBadge(s.emoji, key === "biology" ? "c-biology" : "c-chemistry", "sm"),
          h("div", { class: "grow" }, h("div", { class: "card-title" }, `${t.grade} класс`), h("div", { class: "card-sub" }, `${s.title} · ЯКласс`)),
          icon("open"))),
      ];
    });
    return h("div", { class: "screen" },
      topBar("Теоретическая часть", () => App.back("")),
      h("p", { class: "muted small" }, "Учебные материалы откроются на сайте ЯКласс в новой вкладке."),
      groups);
  }

  // ======================= Защита от списывания =======================

  const PASTE_MESSAGE = "Вставлять текст нельзя — напиши ответ сам";
  const COPY_MESSAGE = "Копировать задания нельзя";

  /**
   * Для учеников: задания нельзя выделить и скопировать, а в поля ответа нельзя вставить текст
   * (ни через Ctrl+V и меню, ни перетаскиванием, ни большим куском с клавиатуры телефона).
   */
  function protectFromCopy(root) {
    root.classList.add("no-copy");
    const isField = (el) => el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA");
    const block = (message) => (e) => {
      e.preventDefault();
      e.stopPropagation();
      if (message) UI.toast(message);
    };
    root.addEventListener("copy", block(COPY_MESSAGE), true);
    root.addEventListener("cut", block(COPY_MESSAGE), true);
    root.addEventListener("contextmenu", block(null), true);
    root.addEventListener("dragstart", block(null), true);
    root.addEventListener("paste", block(PASTE_MESSAGE), true);
    root.addEventListener("drop", block(PASTE_MESSAGE), true);
    root.addEventListener("beforeinput", (e) => {
      if (/^insertFrom(Paste|Drop|Yank)/.test(e.inputType || "")) block(PASTE_MESSAGE)(e);
    }, true);

    // Клавиатура телефона может вставить текст из буфера как обычный ввод.
    // Если за одно изменение добавилось слишком много символов, откатываем его.
    const last = new WeakMap();
    root.addEventListener("focusin", (e) => { if (isField(e.target)) last.set(e.target, e.target.value); }, true);
    root.addEventListener("input", (e) => {
      const el = e.target;
      if (!isField(el)) return;
      const limit = el.tagName === "TEXTAREA" ? 30 : 6;
      if (last.has(el) && el.value.length - last.get(el).length > limit) {
        el.value = last.get(el);
        e.stopImmediatePropagation();
        UI.toast(PASTE_MESSAGE);
        return;
      }
      last.set(el, el.value);
    }, true);
    return root;
  }

  // ======================= Практика =======================

  /** Разделы практики в том порядке, в котором они идут на странице. */
  const SECTIONS = [
    { type: "question", title: "Вопросы по теории", note: "Ответь своими словами. Черновик сохраняется сам, а в конце сдай работу учителю." },
    { type: "choice", title: "Тест", note: "Выбери верный ответ и нажми «Проверить»." },
    { type: "fill", title: "Вставь пропущенные слова", note: "Впиши в пропуски подходящие слова." },
    { type: "formula", title: "Составь формулы по валентности", note: "Пиши латинскими буквами, индексы обычными цифрами: например, H2O." },
    { type: "molar", title: "Вычисли молярную массу", note: "Ответ в г/моль. Атомные массы бери из таблицы Менделеева, хлор — 35,5." },
    { type: "equation", title: "Расставь коэффициенты", note: "Впиши коэффициенты в клеточки. Пустая клеточка означает коэффициент 1." },
  ];

  /** Картинка к заданию (если учитель её добавил). */
  function taskImage(task) {
    if (!task.image) return null;
    const img = h("img", {
      class: "task-image", src: Api.fileUrl(task, task.image, "800x0"), alt: "Рисунок к заданию", loading: "lazy",
      onclick: () => img.classList.toggle("zoomed"),
    });
    return img;
  }

  function practice(params) {
    const p = content.practice(params.id);
    if (!p) {
      return h("div", { class: "screen" }, topBar("Практика", () => App.back("")), emptyBox("🗂️", "Эта практика больше недоступна"));
    }
    const user = Api.state.user;
    const store = Api.progress;
    const all = content.tasksOf(p.id).filter((t) => t.type !== "equation" || Chem.parseEquation(t.text));
    const byType = (type) => all.filter((t) => t.type === type);
    const sections = SECTIONS.filter((s) => byType(s.type).length);
    const checkable = all.filter(Api.isCheckable);
    /** Как получить то, что ввёл ученик, для каждого задания (заполняется ниже). */
    const inputOf = new Map();

    // ----- Прогресс сверху: сколько заданий заполнено (правильность не раскрывается) -----
    const progressBox = h("div");
    const filled = (id) => store.draft(id).replace(/[|,]/g, "").trim() !== "";
    function updateProgress() {
      if (!all.length) return replace(progressBox);
      const done = all.filter((t) => filled(t.id)).length;
      replace(progressBox,
        h("p", { class: "muted small" }, done === all.length
          ? "Все задания заполнены — можно сдавать работу"
          : `Заполнено ${done} из ${all.length} ${Content.tasksWord(all.length)}`),
        h("div", { class: "mt" }, progressBar(done / all.length)));
    }
    updateProgress();
    const saveDraft = (id, value) => { store.saveDraft(id, value); updateProgress(); };

    /**
     * Карточка задания с кнопкой «Проверить». Ответ проверяет сервер.
     * input() — то, что ввёл ученик; onResult(r) — показать, какие части верны (для пропусков).
     */
    function checkCard(task, label, body, { input, onResult, onChange, allowEmpty = false } = {}) {
      const solvedNow = store.solved().has(task.id);
      const pill = h("span", { class: "pill pill-solved", hidden: !solvedNow }, icon("check", 16), "Решено");
      const resultBox = h("div", { "aria-live": "polite" });
      const checkBtn = button("Проверить", check);
      let busy = false;
      const card = h("div", { class: "card" + (solvedNow ? " solved" : "") },
        h("div", { class: "row" }, h("span", { class: "task-label grow" }, label), pill),
        taskImage(task),
        body,
        h("div", { class: "task-actions" }, checkBtn),
        resultBox);
      inputOf.set(task.id, input);
      card.clearResult = () => { replace(resultBox); if (onChange) onChange(); };
      async function check() {
        const value = input();
        if (busy) return;
        if (!allowEmpty && !value.replace(/[|,]/g, "").trim()) {
          replace(resultBox, feedback(task.type === "choice" ? "Выбери ответ" : "Сначала впиши ответ", "warning"));
          return;
        }
        busy = true;
        checkBtn.disabled = true;
        replace(checkBtn, h("span", { class: "spinner spinner-sm" }), h("span", null, "Проверяем…"));
        try {
          const [r] = await Api.check([{ task: task.id, input: value }]);
          const kind = r.kind === "correct" ? "success" : r.kind === "hint" ? "warning" : "error";
          replace(resultBox, feedback(r.message || (r.kind === "correct" ? "Верно!" : "Неверно"), kind));
          if (onResult) onResult(r);
          if (r.kind === "correct") {
            store.markSolved(task.id);
            pill.hidden = false;
            card.classList.add("solved");
          }
        } catch (e) {
          replace(resultBox, feedback(e.status ? e.message : "Нет связи с сервером. Проверка работает только с интернетом.", "error"));
        }
        busy = false;
        checkBtn.disabled = false;
        replace(checkBtn, h("span", null, "Проверить"));
      }
      card.check = check;
      return card;
    }

    const numberOf = (type) => sections.findIndex((s) => s.type === type) + 1;

    // ----- Вопросы -----
    const questionCards = byType("question").map((q, i) => {
      inputOf.set(q.id, () => store.draft(q.id));
      return h("div", { class: "card" },
        h("div", { class: "row" }, h("span", { class: "task-label grow" }, `Вопрос ${i + 1}`)),
        taskImage(q),
        h("p", { class: "mt" }, q.text),
        h("div", { class: "mt" }, field({ label: "Твой ответ", multiline: true, value: store.draft(q.id), oninput: (v) => saveDraft(q.id, v) })));
    });

    // ----- Тест -----
    const choiceCards = byType("choice").map((task, i) => {
      const options = Array.isArray(task.options) ? task.options : [];
      const chosen = new Set(Chem.parseChoice(store.draft(task.id)));
      const name = "choice-" + task.id;
      let card;
      const value = () => [...chosen].sort((a, b) => a - b).join(",");
      const list = h("div", { class: "choice-list", role: task.multiple ? "group" : "radiogroup" },
        options.map((text, k) => {
          const n = k + 1;
          const box = h("input", { type: task.multiple ? "checkbox" : "radio", name, checked: chosen.has(n) });
          box.addEventListener("change", () => {
            if (!task.multiple) chosen.clear();
            if (box.checked) chosen.add(n); else chosen.delete(n);
            saveDraft(task.id, value());
            card.clearResult();
          });
          return h("label", { class: "choice" }, box, h("span", null, Chem.prettyIfFormula(text)));
        }));
      card = checkCard(task, `Задание ${numberOf("choice")}.${i + 1}`, [
        h("p", { class: "mt" }, task.text),
        task.multiple && h("p", { class: "tiny muted" }, "Верных ответов может быть несколько"),
        list,
      ], { input: value });
      return card;
    });

    // ----- Пропущенные слова -----
    const fillCards = byType("fill").map((task, i) => {
      const parts = String(task.text).split(/_{3,}/);
      const saved = store.draft(task.id).split("|");
      let card;
      const inputs = parts.slice(1).map((_, k) => {
        const input = h("input", { class: "blank", autocomplete: "off", autocapitalize: "none", spellcheck: "false", "aria-label": `Пропуск ${k + 1}` });
        input.value = saved[k] || "";
        const fit = () => { input.style.width = Math.max(6, input.value.length + 2) + "ch"; };
        fit();
        input.addEventListener("input", () => {
          fit();
          input.classList.remove("blank-ok", "blank-bad");
          saveDraft(task.id, inputs.map((x) => x.value).join("|"));
          card.clearResult();
        });
        input.addEventListener("keydown", (e) => {
          if (e.key !== "Enter") return;
          if (k < inputs.length - 1) inputs[k + 1].focus(); else card.check();
        });
        return input;
      });
      const sentence = h("p", { class: "fill-text mt" }, parts.map((part, k) => [part, inputs[k] || null]));
      card = checkCard(task, `Задание ${numberOf("fill")}.${i + 1}`, sentence, {
        input: () => inputs.map((x) => x.value.trim()).join("|"),
        onResult: (r) => (r.blanks || []).forEach((ok, k) => {
          if (inputs[k]) inputs[k].classList.add(ok ? "blank-ok" : "blank-bad");
        }),
      });
      return card;
    });

    // ----- Формулы -----
    const formulaCards = byType("formula").map((task, i) => {
      let card;
      const f = field({
        label: "Формула", value: store.draft(task.id), autocapitalize: "none", inputmode: "latin",
        oninput: (v) => {
          saveDraft(task.id, v);
          f.setSupport(v.trim() ? "Получается: " + Chem.pretty(Chem.normalize(v)) : "");
          card.clearResult();
        },
        onenter: () => card.check(),
      });
      f.setSupport(f.input.value.trim() ? "Получается: " + Chem.pretty(Chem.normalize(f.input.value)) : "");
      card = checkCard(task, `Задание ${numberOf("formula")}.${i + 1}`, [
        h("div", { class: "task-name" }, task.text.charAt(0).toUpperCase() + task.text.slice(1)),
        h("div", { class: "mt" }, f),
      ], { input: () => f.input.value });
      return card;
    });

    // ----- Молярная масса -----
    const molarCards = byType("molar").map((task, i) => {
      let card;
      const input = h("input", { class: "molar-input", inputmode: "decimal", autocomplete: "off", "aria-label": "Молярная масса, г/моль" });
      input.value = store.draft(task.id);
      input.addEventListener("input", () => { saveDraft(task.id, input.value); card.clearResult(); });
      input.addEventListener("keydown", (e) => { if (e.key === "Enter") card.check(); });
      card = checkCard(task, `Задание ${numberOf("molar")}.${i + 1}`,
        h("div", { class: "molar mt" }, h("span", { class: "task-name" }, `M(${Chem.pretty(Chem.normalize(task.text))}) =`), input, h("span", null, "г/моль")),
        { input: () => input.value.trim() });
      return card;
    });

    // ----- Уравнения -----
    const equationCards = byType("equation").map((task, i) => {
      const eq = Chem.parseEquation(task.text);
      const saved = store.draft(task.id).split("|");
      let card;
      const inputs = eq.substances.map((formula, k) => {
        const input = h("input", {
          class: "coef", inputmode: "numeric", maxlength: "2", autocomplete: "off",
          "aria-label": "Коэффициент перед " + Chem.pretty(formula),
        });
        input.value = saved[k] || "";
        input.addEventListener("input", () => {
          input.value = input.value.replace(/\D/g, "").slice(0, 2);
          saveDraft(task.id, inputs.map((x) => x.value).join("|"));
          card.clearResult();
        });
        input.addEventListener("keydown", (e) => {
          if (e.key !== "Enter") return;
          if (k < inputs.length - 1) inputs[k + 1].focus(); else card.check();
        });
        return input;
      });
      const terms = eq.substances.map((formula, k) => h("span", { class: "term" },
        k > 0 && h("span", { class: "sign" }, k === eq.reactants.length ? "→" : "+"),
        inputs[k], h("span", null, Chem.pretty(formula))));
      // Пустые клеточки — это единицы, поэтому проверять можно и без ввода.
      card = checkCard(task, `Задание ${numberOf("equation")}.${i + 1}`, h("div", { class: "equation" }, terms),
        { input: () => inputs.map((x) => x.value).join("|"), allowEmpty: true });
      return card;
    });

    const cardsOf = { question: questionCards, choice: choiceCards, fill: fillCards, formula: formulaCards, molar: molarCards, equation: equationCards };

    // ----- Сдача работы -----
    function buildAnswers() {
      return all.map((t) => {
        const input = (inputOf.get(t.id) || (() => store.draft(t.id)))();
        const empty = !String(input).replace(/[|,]/g, "").trim();
        const item = { task: t.id, kind: t.type, prompt: t.text, answer: "", input };
        switch (t.type) {
          case "question":
            item.answer = String(input).trim();
            delete item.input;
            break;
          case "formula":
            item.answer = empty ? "" : Chem.pretty(Chem.normalize(input));
            break;
          case "equation": {
            const eq = Chem.parseEquation(t.text);
            const values = String(input).split("|");
            item.prompt = Chem.prettyEquation(eq);
            item.answer = empty ? "" : Chem.prettyEquation(eq, eq.substances.map((_, k) => parseInt(values[k], 10) || 1));
            break;
          }
          case "choice":
            item.answer = Chem.parseChoice(input).map((n) => (t.options || [])[n - 1]).filter(Boolean).map(Chem.prettyIfFormula).join("; ");
            break;
          case "fill": {
            const words = String(input).split("|");
            let k = 0;
            item.answer = empty ? "" : String(t.text).replace(/_{3,}/g, () => `«${(words[k++] || "").trim() || "…"}»`);
            break;
          }
          case "molar":
            item.prompt = `M(${Chem.pretty(Chem.normalize(t.text))})`;
            item.answer = empty ? "" : String(input).trim() + " г/моль";
            break;
        }
        return item;
      });
    }

    function submitCard() {
      const statusBox = h("div");
      const resultBox = h("div");
      let last = null;
      let sending = false;
      const sendBtn = button("Сдать работу учителю", send, { iconName: "send", cls: "btn-block mt" });

      function renderStatus() {
        if (!last) {
          replace(statusBox, h("p", { class: "muted small" }, "Учитель увидит твои ответы и поставит оценку. Её можно будет посмотреть в личном кабинете."));
        } else {
          replace(statusBox,
            h("div", { class: "row" },
              h("div", { class: "grow" },
                h("div", { class: "bold small" }, "Сдано " + Content.formatDate(last.created)),
                h("div", { class: "tiny muted" }, last.grade ? "Оценка учителя" : "Учитель ещё не проверил работу")),
              gradeBadge(last.grade)),
            last.comment && h("p", { class: "small mt" }, "💬 " + last.comment));
        }
        replace(sendBtn, icon("send", 18), h("span", null, last ? "Сдать ещё раз" : "Сдать работу учителю"));
      }

      async function send() {
        if (sending) return;
        const items = buildAnswers();
        if (!items.some((x) => x.answer)) {
          replace(resultBox, feedback("Сначала ответь хотя бы на одно задание.", "error"));
          return;
        }
        sending = true;
        sendBtn.disabled = true;
        replace(sendBtn, h("span", { class: "spinner spinner-sm" }), h("span", null, "Отправляем…"));
        try {
          // Сколько верно, сервер посчитает сам.
          last = await Api.submit(p, items, 0, checkable.length);
          replace(resultBox,
            feedback("Готово! Работа отправлена учителю.", "success"),
            h("a", { class: "btn btn-text mt", href: "#/cabinet" }, "Открыть личный кабинет"));
        } catch (e) {
          replace(resultBox, feedback(e.message || "Не получилось отправить", "error"));
        }
        sending = false;
        sendBtn.disabled = false;
        renderStatus();
      }

      renderStatus();
      Api.mySubmissions(p.id).then((list) => { last = list[0] || null; renderStatus(); }, () => {});
      return h("div", { class: "card" }, h("div", { class: "task-label" }, "Сдать работу"), h("div", { class: "mt" }, statusBox), sendBtn, resultBox);
    }

    const reset = async () => {
      const ok = await confirm("Начать заново?", "Черновики ответов и отметки о решённых заданиях по этой практике удалятся. Сданные работы останутся у учителя.", "Начать заново");
      if (!ok) return;
      store.reset(all.map((t) => t.id));
      App.render();
    };

    const screen = h("div", { class: "screen" },
      topBar(`${Content.subjectOf(p.subject).title} · ${p.grade} класс`, () => App.back(`subject/${p.subject}`),
        menuButton([{ label: "Начать заново", onclick: reset }])),
      h("h2", { class: "home-title" }, p.title),
      progressBox,
      sections.length === 0 && emptyBox("📝", "В этой практике пока нет заданий"),
      sections.map((s, i) => [
        h("h3", { class: "section-title" }, `${i + 1}. ${s.title}`),
        h("p", { class: "muted small" }, s.note),
        cardsOf[s.type],
      ]),
      sections.length > 0 && (user && user.isTeacher
        ? h("p", { class: "muted small" }, "Так практику видят ученики. Сдавать работу учителю не нужно.")
        : submitCard()));
    // Ученику нельзя копировать задания и вставлять текст в ответы. У учителя ограничений нет.
    return user && user.isTeacher ? screen : protectFromCopy(screen);
  }

  // ======================= Личный кабинет =======================
  function submissionCard(s, showStudent) {
    const st = s.expand && s.expand.student;
    return h("a", { class: "card card-link", href: `#/submission/${s.id}` },
      h("div", { class: "grow" },
        showStudent && h("div", { class: "card-title" }, st ? (st.group ? `${st.name}, ${st.group}` : st.name) : "Ученик удалён"),
        h("div", { class: showStudent ? "small" : "card-title" }, s.practiceTitle),
        h("div", { class: "tiny muted" }, Content.formatDate(s.created) + (s.total > 0 ? ` · решено ${s.solved} из ${s.total}` : ""))),
      gradeBadge(s.grade));
  }

  function cabinet() {
    const user = Api.state.user;
    const list = asyncBlock(() => Api.mySubmissions(), (items) => {
      if (!items.length) return emptyBox("📝", "Ты ещё не сдавал работ. Реши практику и нажми «Сдать работу учителю».");
      const graded = items.filter((s) => s.grade > 0);
      const avg = graded.length ? (graded.reduce((a, s) => a + s.grade, 0) / graded.length).toFixed(1).replace(".", ",") : null;
      return [
        avg && h("div", { class: "summary" }, `Средний балл: ${avg} · проверено ${graded.length} из ${items.length}`),
        items.map((s) => submissionCard(s, false)),
      ];
    });
    const logout = async () => {
      if (await confirm("Выйти из аккаунта?", "Сданные работы и оценки сохранятся, их можно будет посмотреть после входа.", "Выйти")) {
        Api.logout();
        location.hash = "";
      }
    };
    return h("div", { class: "screen" },
      topBar("Личный кабинет", () => App.back("")),
      h("div", { class: "card row" },
        h("div", { class: "avatar big" }, (user.name || "?")[0].toUpperCase()),
        h("div", { class: "grow" },
          h("div", { class: "home-title" }, user.name),
          h("div", { class: "small muted" }, (user.isTeacher ? "Учитель" : user.group ? `${user.group} класс` : "") + ` · логин ${user.username}`))),
      sectionLabel("Мои работы"),
      list,
      button("Выйти из аккаунта", logout, { kind: "outline", cls: "btn-block mt-lg" }));
  }

  // ======================= Сданная работа =======================
  function submission(params) {
    const isTeacher = Api.state.user && Api.state.user.isTeacher;
    const body = asyncBlock(() => Api.submission(params.id), (s) => {
      let qn = 0;
      const answers = (s.answers || []).map((a) => {
        const title = a.kind === "question" ? `Вопрос ${++qn}`
          : a.kind === "formula" ? `Формула: ${a.prompt}`
          : ({ equation: "Уравнение", choice: "Тест", fill: "Пропущенные слова", molar: "Молярная масса" })[a.kind] || "Задание";
        const tries = a.attempts > 0 ? ` · попыток: ${a.attempts}` : "";
        const status = a.correct === true
          ? h("span", { class: "small bold ok" }, "✅ верно" + tries)
          : a.correct === false ? h("span", { class: "small bold err" }, a.answer ? "❌ неверно" + tries : "— не решено") : null;
        return h("div", { class: "card" },
          h("div", { class: "row" }, h("span", { class: "task-label grow" }, title), status),
          a.kind !== "formula" && h("p", { class: "small mt" }, a.prompt),
          h("p", { class: "mt" + (a.answer ? " bold" : " muted") }, a.answer || "Нет ответа"));
      });
      const st = s.expand && s.expand.student;
      return [
        h("div", null,
          isTeacher && st && h("h2", { class: "home-title" }, st.group ? `${st.name}, ${st.group}` : st.name),
          h("div", { class: "card-title" }, s.practiceTitle),
          h("p", { class: "small muted" }, "Сдано " + Content.formatDate(s.created) + (s.total > 0 ? ` · автопроверка: верно ${s.solved} из ${s.total}` : ""))),
        isTeacher ? Teacher.gradeEditor(s) : gradeView(s),
        sectionLabel("Ответы"),
        answers,
      ];
    });
    const screen = h("div", { class: "screen" },
      topBar(isTeacher ? "Проверка работы" : "Моя работа", () => App.back(isTeacher ? "admin/works" : "cabinet")),
      body);
    return isTeacher ? screen : protectFromCopy(screen);
  }

  function gradeView(s) {
    return h("div", { class: "card" },
      h("div", { class: "row" },
        h("div", { class: "grow" },
          h("div", { class: "card-title" }, s.grade ? "Оценка учителя" : "Работа ещё не проверена"),
          s.grade > 0 && s.gradedAt && h("div", { class: "tiny muted" }, "Проверено " + Content.formatDate(s.gradedAt))),
        gradeBadge(s.grade, true)),
      s.comment && h("p", { class: "mt" }, "💬 " + s.comment));
  }

  window.Screens = { auth, home, subject, theory, practice, cabinet, submission, submissionCard, asyncBlock };
})();
