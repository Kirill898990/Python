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
    const all = content.checkableTaskIds();
    const solved = Api.progress.solved();
    const solvedCount = all.filter((id) => solved.has(id)).length;

    const menuCard = (href, emoji, colorClass, title, subtitle, highlight = false) =>
      h("a", { class: "card card-link" + (highlight ? " card-highlight" : ""), href },
        emojiBadge(emoji, colorClass),
        h("div", { class: "grow" }, h("div", { class: "card-title" }, title), h("div", { class: "card-sub muted" }, subtitle)),
        icon("chevron"));

    const refreshBtn = button("Обновить", async () => { await Api.refreshContent(); }, { kind: "text" });

    return h("div", { class: "screen" },
      h("div", { class: "home-header" },
        emojiBadge("🔬", "c-primary", "md"),
        h("div", { class: "grow" },
          h("h1", { class: "home-title" }, "Химия и биология"),
          h("div", { class: "row" }, h("span", { class: "muted small" }, "Теория и практика"),
            h("span", { class: "pill" }, `${Content.allGradesRange()} классы`))),
        h("button", { class: "avatar", title: "Личный кабинет", "aria-label": "Личный кабинет", onclick: () => App.go("cabinet") },
          (user && user.name ? user.name[0] : "?").toUpperCase())),
      Api.state.contentError && h("div", { class: "banner-offline" },
        h("p", null, Api.state.contentError + " Показаны сохранённые задания."), refreshBtn),
      h("img", { class: "banner banner-lab", src: "images/banner-lab.jpg", alt: "" }),
      user && user.isTeacher && menuCard("#/admin/works", "📋", "c-primary", "Панель учителя", "Проверка работ, оценки и задания", true),
      all.length > 0 && !(user && user.isTeacher) && h("div", { class: "card" },
        h("div", { class: "card-title" }, solvedCount === all.length ? "Все задания решены! 🎉" : "Твой прогресс"),
        h("p", { class: "muted small mt" }, `Решено ${solvedCount} из ${all.length} ${Content.tasksWord(all.length)}`),
        h("div", { class: "mt" }, progressBar(solvedCount / all.length))),
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
    const solved = Api.progress.solved();
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
          const ids = content.tasksOf(p.id).filter(Api.isCheckable).map((t) => t.id);
          const done = ids.filter((id) => solved.has(id)).length;
          return [
            h("div", { class: "divider" }),
            h("a", { class: "practice-row card-link", href: `#/practice/${p.id}` },
              h("div", { class: "grow" },
                h("div", { class: "row" }, h("span", { class: "bold" }, p.title), !p.published && h("span", { class: "pill pill-draft" }, "Черновик")),
                h("div", { class: "card-sub" }, ids.length === 0 ? "Вопросы по теории" : done === ids.length ? "Пройдено ✓" : `Решено ${done} из ${ids.length} ${Content.tasksWord(ids.length)}`),
                ids.length > 0 && h("div", { class: "mt" }, progressBar(done / ids.length, key === "biology" ? "bio" : ""))),
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

  // ======================= Практика =======================
  function practice(params) {
    const p = content.practice(params.id);
    if (!p) {
      return h("div", { class: "screen" }, topBar("Практика", () => App.back("")), emptyBox("🗂️", "Эта практика больше недоступна"));
    }
    const user = Api.state.user;
    const store = Api.progress;
    const tasks = content.tasksOf(p.id);
    const questions = tasks.filter((t) => t.type === "question");
    const formulas = tasks.filter((t) => t.type === "formula" && t.answer);
    const equations = tasks
      .filter((t) => t.type === "equation")
      .map((t) => ({ task: t, eq: Chem.parseEquation(t.text), coefs: Chem.parseCoefficients(t.answer) }))
      .filter((x) => x.eq && x.coefs);
    const checkableIds = formulas.map((t) => t.id).concat(equations.map((x) => x.task.id));

    const sections = [questions.length && "q", formulas.length && "f", equations.length && "e"].filter(Boolean);
    const num = (s) => sections.indexOf(s) + 1;

    // ----- Прогресс сверху -----
    const progressBox = h("div");
    function updateProgress() {
      if (!checkableIds.length) return replace(progressBox);
      const solved = store.solved();
      const done = checkableIds.filter((id) => solved.has(id)).length;
      replace(progressBox,
        h("p", { class: "muted small" }, done === checkableIds.length
          ? "Все задания с проверкой решены! 🎉"
          : `Решено ${done} из ${checkableIds.length} ${Content.tasksWord(checkableIds.length)}`),
        h("div", { class: "mt" }, progressBar(done / checkableIds.length)));
    }
    updateProgress();

    const taskCard = (label, taskId, ...children) => {
      const solvedNow = taskId && store.solved().has(taskId);
      const pill = h("span", { class: "pill pill-solved", hidden: !solvedNow }, icon("check", 16), "Решено");
      const card = h("div", { class: "card" + (solvedNow ? " solved" : "") },
        h("div", { class: "row" }, h("span", { class: "task-label grow" }, label), pill),
        children);
      card.markSolved = () => { pill.hidden = false; card.classList.add("solved"); updateProgress(); };
      return card;
    };

    const hintToggle = (task, hintBox) => button("Подсказка", () => {
      if (hintBox.childNodes.length) replace(hintBox);
      else replace(hintBox, h("div", { class: "hint" }, "💡 " + (task.hint || "У этого задания нет подсказки.")));
    }, { kind: "text", iconName: "bulb" });

    const showAnswer = (box, answerText) => replace(box,
      button("Показать ответ", () => replace(box, h("div", { class: "answer-reveal" }, "Ответ: " + answerText)), { kind: "text", iconName: "eye" }));

    // ----- Вопросы -----
    const questionCards = questions.map((q, i) => taskCard(`Вопрос ${i + 1}`, null,
      h("p", { class: "mt" }, q.text),
      h("div", { class: "mt" }, field({
        label: "Твой ответ", multiline: true, value: store.draft(q.id),
        oninput: (v) => store.saveDraft(q.id, v),
      }))));

    // ----- Формулы -----
    const formulaCards = formulas.map((task, i) => {
      const hintBox = h("div");
      const resultBox = h("div");
      const answerBox = h("div");
      let card;
      const f = field({
        label: "Формула", value: store.draft(task.id), autocapitalize: "none", inputmode: "latin",
        oninput: (v) => {
          store.saveDraft(task.id, v);
          f.setInvalid(false);
          f.setSupport(v.trim() ? "Получается: " + Chem.pretty(Chem.normalize(v)) : "");
          replace(resultBox);
          checkBtn.disabled = !v.trim();
        },
        onenter: () => check(),
      });
      f.setSupport(f.input.value.trim() ? "Получается: " + Chem.pretty(Chem.normalize(f.input.value)) : "");
      const checkBtn = button("Проверить", () => check(), { disabled: !f.input.value.trim() });
      function check() {
        const value = f.input.value;
        if (!value.trim()) return;
        const r = Chem.checkFormula(value, task.answer);
        if (r.kind === "correct") {
          replace(resultBox, feedback("Верно! " + Chem.pretty(task.answer), "success"));
          replace(answerBox);
          store.markSolved(task.id);
          card.markSolved();
        } else {
          replace(resultBox, feedback(r.message, r.kind === "hint" ? "warning" : "error"));
          f.setInvalid(r.kind === "wrong");
          if (!answerBox.childNodes.length) showAnswer(answerBox, Chem.pretty(task.answer));
        }
      }
      card = taskCard(`Задание ${num("f")}.${i + 1}`, task.id,
        h("div", { class: "task-name" }, task.text.charAt(0).toUpperCase() + task.text.slice(1)),
        h("div", { class: "mt" }, f),
        h("div", { class: "task-actions" }, checkBtn, hintToggle(task, hintBox)),
        hintBox, resultBox, answerBox);
      return card;
    });

    // ----- Уравнения -----
    const coefsOf = new Map();
    const equationCards = equations.map(({ task, eq, coefs }, i) => {
      const saved = store.draft(task.id).split("|");
      const inputs = eq.substances.map((formula, k) => {
        const input = h("input", {
          class: "coef", inputmode: "numeric", maxlength: "2", autocomplete: "off",
          "aria-label": "Коэффициент перед " + Chem.pretty(formula),
        });
        input.value = saved[k] || "";
        input.addEventListener("input", () => {
          input.value = input.value.replace(/\D/g, "").slice(0, 2);
          store.saveDraft(task.id, inputs.map((x) => x.value).join("|"));
          replace(resultBox);
        });
        input.addEventListener("keydown", (e) => {
          if (e.key !== "Enter") return;
          if (k < inputs.length - 1) inputs[k + 1].focus();
          else check();
        });
        return input;
      });
      coefsOf.set(task.id, () => inputs.map((x) => x.value));
      const hintBox = h("div");
      const resultBox = h("div");
      const answerBox = h("div");
      let card;
      function check() {
        const values = inputs.map((x) => x.value);
        const r = Chem.checkEquation(eq, values);
        if (r.kind === "correct") {
          replace(resultBox, feedback("Верно! " + Chem.prettyEquation(eq, values.map((v) => parseInt(v, 10) || 1)), "success"));
          replace(answerBox);
          store.markSolved(task.id);
          card.markSolved();
          return;
        }
        if (r.kind === "notReduced") {
          replace(resultBox, feedback(`Атомов поровну, но все коэффициенты делятся на ${r.divisor}. Нужны самые маленькие.`, "warning"));
        } else if (r.kind === "unbalanced") {
          replace(resultBox, feedback("Пока не сходится:\n" + r.mismatches.map((m) => `• ${m.element}: слева ${m.left}, справа ${m.right}`).join("\n"), "error"));
        } else {
          replace(resultBox, feedback(r.message, "warning"));
        }
        if (!answerBox.childNodes.length) showAnswer(answerBox, Chem.prettyEquation(eq, coefs));
      }
      const terms = eq.substances.map((formula, k) => h("span", { class: "term" },
        k > 0 && h("span", { class: "sign" }, k === eq.reactants.length ? "→" : "+"),
        inputs[k], h("span", null, Chem.pretty(formula))));
      card = taskCard(`Задание ${num("e")}.${i + 1}`, task.id,
        h("div", { class: "equation" }, terms),
        h("div", { class: "task-actions" }, button("Проверить", check), hintToggle(task, hintBox)),
        hintBox, resultBox, answerBox);
      return card;
    });

    // ----- Сдача работы -----
    function buildAnswers() {
      const items = [];
      questions.forEach((q) => items.push({ kind: "question", prompt: q.text, answer: store.draft(q.id).trim() }));
      formulas.forEach((t) => {
        const input = store.draft(t.id);
        items.push({
          kind: "formula", prompt: t.text,
          answer: input.trim() ? Chem.pretty(Chem.normalize(input)) : "",
          correct: !!input.trim() && Chem.checkFormula(input, t.answer).kind === "correct",
        });
      });
      equations.forEach(({ task, eq }) => {
        const values = coefsOf.get(task.id)();
        const filled = values.some((v) => v.trim());
        items.push({
          kind: "equation", prompt: Chem.prettyEquation(eq),
          answer: filled ? Chem.prettyEquation(eq, values.map((v) => parseInt(v, 10) || 1)) : "",
          correct: filled && Chem.checkEquation(eq, values).kind === "correct",
        });
      });
      return items;
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
          last = await Api.submit(p, items, items.filter((x) => x.correct === true).length, checkableIds.length);
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
      const ok = await confirm("Начать заново?", "Отметки о решённых заданиях и черновики ответов по этой практике удалятся.", "Начать заново");
      if (!ok) return;
      store.reset(questions.map((q) => q.id).concat(checkableIds));
      App.render();
    };

    return h("div", { class: "screen" },
      topBar(`${Content.subjectOf(p.subject).title} · ${p.grade} класс`, () => App.back(`subject/${p.subject}`),
        menuButton([{ label: "Начать заново", onclick: reset }])),
      h("h2", { class: "home-title" }, p.title),
      progressBox,
      sections.length === 0 && emptyBox("📝", "В этой практике пока нет заданий"),
      questions.length > 0 && [
        h("h3", { class: "section-title" }, `${num("q")}. Вопросы по теории`),
        h("p", { class: "muted small" }, "Ответь своими словами. Черновик сохраняется сам, а в конце сдай работу учителю."),
        questionCards,
      ],
      formulas.length > 0 && [
        h("h3", { class: "section-title" }, `${num("f")}. Составь формулы по валентности`),
        h("p", { class: "muted small" }, "Пиши латинскими буквами, индексы обычными цифрами: например, H2O."),
        formulaCards,
      ],
      equations.length > 0 && [
        h("h3", { class: "section-title" }, `${num("e")}. Расставь коэффициенты`),
        h("p", { class: "muted small" }, "Впиши коэффициенты в клеточки. Пустая клеточка означает коэффициент 1."),
        equationCards,
      ],
      sections.length > 0 && (user && user.isTeacher
        ? h("p", { class: "muted small" }, "Так практику видят ученики. Сдавать работу учителю не нужно.")
        : submitCard()));
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
        const title = a.kind === "question" ? `Вопрос ${++qn}` : a.kind === "formula" ? `Формула: ${a.prompt}` : "Уравнение";
        const status = a.correct === true
          ? h("span", { class: "small bold ok" }, "✅ верно")
          : a.correct === false ? h("span", { class: "small bold err" }, a.answer ? "❌ неверно" : "— не решено") : null;
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
    return h("div", { class: "screen" },
      topBar(isTeacher ? "Проверка работы" : "Моя работа", () => App.back(isTeacher ? "admin/works" : "cabinet")),
      body);
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
