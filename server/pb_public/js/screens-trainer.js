/*
 * Тренажёр: бесконечные задания, которые сайт придумывает сам, с мгновенной проверкой.
 * Учителю ничего не отправляется и на оценки не влияет — просто потренироваться.
 * Задания берутся из js/trainer-data.js (его делает server/tools/make_trainer.py). Так же работает приложение.
 */
(function () {
  "use strict";
  const { h, replace, topBar, button, field, feedback, chip, emojiBadge } = UI;
  const D = window.TrainerData;

  const TOPICS = [
    { key: "mix", title: "🎲 Всё вперемешку" },
    { key: "formula", title: "🧪 Формулы" },
    { key: "molar", title: "⚖️ Молярная масса" },
    { key: "equation", title: "⚗️ Уравнения" },
    { key: "classes", title: "🗂️ Классы веществ" },
    { key: "biology", title: "🧬 Биология" },
  ];
  const KINDS = ["formula", "molar", "equation", "classes", "biology"];
  const LABELS = {
    formula: "Составь формулу по валентности",
    molar: "Вычисли молярную массу",
    equation: "Расставь коэффициенты",
    classes: "Определи класс вещества",
    biology: "Ответь на вопрос по биологии",
  };

  // Чтобы задания не повторялись подряд: последние показанные.
  const recent = [];
  function pick(kind, list) {
    let i;
    for (let tries = 0; tries < 20; tries++) {
      i = Math.floor(Math.random() * list.length);
      if (!recent.includes(kind + i)) break;
    }
    recent.push(kind + i);
    if (recent.length > 12) recent.shift();
    return list[i];
  }

  /** «M(H₂SO₄) = 2·1 + 32 + 4·16 = 98 г/моль» */
  function molarExplain(formula) {
    const m = Chem.molarMass(formula);
    const name = `M(${Chem.pretty(Chem.normalize(formula))})`;
    const parsed = Chem.parse(Chem.normalize(formula));
    if (!parsed.ok || !parsed.atoms) return `${name} = ${Chem.formatNumber(m.value)} г/моль`;
    const parts = [...parsed.atoms].map(([el, n]) => {
      const a = String(Chem.atomicMass(el)).replace(".", ",");
      return n > 1 ? `${n}·${a}` : a;
    });
    return `${name} = ${parts.join(" + ")} = ${Chem.formatNumber(m.value)} г/моль`;
  }

  /** Новое задание: {kind, type, text, answer, options, explain} — как задание учителя, поэтому проверка та же. */
  function makeTask(topic) {
    const kind = topic === "mix" ? KINDS[Math.floor(Math.random() * KINDS.length)] : topic;
    switch (kind) {
      case "formula": {
        const [name, formula, valences] = pick(kind, D.formulas);
        return { kind, type: "formula", text: name, answer: formula, explain: `${Chem.pretty(formula)} (${valences})` };
      }
      case "molar": {
        const formula = pick(kind, D.molar);
        return { kind, type: "molar", text: formula, explain: molarExplain(formula) };
      }
      case "equation": {
        const [text, coefficients] = pick(kind, D.equations);
        const eq = Chem.parseEquation(text);
        return { kind, type: "equation", text, eq, explain: Chem.prettyEquation(eq, coefficients) };
      }
      case "biology": {
        // Первый вариант в данных — верный; показываем варианты в случайном порядке.
        const [question, options, explain] = pick(kind, D.biology);
        const order = options.map((o, i) => [Math.random(), i]).sort((a, b) => a[0] - b[0]).map((x) => x[1]);
        return {
          kind, type: "choice", text: question, options: order.map((i) => options[i]),
          answer: String(order.indexOf(0) + 1), explain: `${options[0]}. ${explain}`,
        };
      }
      default: {
        const [formula, cls] = pick("classes", D.classes);
        return {
          kind: "classes", type: "choice",
          text: `К какому классу веществ относится ${Chem.pretty(Chem.normalize(formula))}?`,
          options: D.classNames, answer: String(cls), explain: D.classHints[cls - 1],
        };
      }
    }
  }

  // ----- Счёт (только в этом браузере) -----
  const statsKey = () => "trainer_" + (Api.state.user ? Api.state.user.id : "guest");
  const loadStats = () => Api.storage.get(statsKey(), { total: 0, best: 0 });
  const saveStats = (s) => Api.storage.set(statsKey(), s);

  let topic = "mix";
  let task = null;
  let streak = 0;

  function screen() {
    if (!task) task = makeTask(topic);
    const box = h("div", { class: "stack" });
    const render = () => replace(box, body(render));
    render();
    return h("div", { class: "screen" },
      topBar("Тренажёр", () => App.back(""),
        h("button", { class: "icon-btn", type: "button", "aria-label": "Справочник", title: "Таблица Менделеева и растворимости", onclick: () => App.go("reference") }, UI.icon("book"))),
      h("p", { class: "muted small" }, "Задания придумываются сами и проверяются сразу. Учителю ничего не отправляется — тренируйся сколько хочешь."),
      h("div", { class: "row-wrap" }, TOPICS.map((t) => chip(t.title, topic === t.key, () => {
        topic = t.key;
        task = makeTask(topic);
        App.render();
      }))),
      box);
  }

  function body(rerender) {
    const stats = loadStats();
    let input = () => "";
    let solved = false;
    let wrongOnce = false;
    const result = h("div", { "aria-live": "polite" });
    const actions = h("div", { class: "task-actions row-wrap" });

    const next = () => { task = makeTask(topic); rerender(); };
    const showAnswer = () => {
      streak = 0;
      solved = true;
      replace(result, feedback("Ответ: " + task.explain, "warning"));
      renderActions();
      updateStats();
    };
    function check() {
      if (solved) return next();
      const r = Chem.checkTask(task, input());
      const kind = r.kind === "correct" ? "success" : r.kind === "hint" ? "warning" : "error";
      // Для массы и класса вещества сразу показываем, как получился ответ.
      const message = r.kind === "correct" && (task.kind === "molar" || task.kind === "classes" || task.kind === "biology")
        ? "Верно! " + task.explain
        : r.message || (r.kind === "correct" ? "Верно!" : "Неверно");
      replace(result, feedback(message, kind));
      if (r.kind === "correct") {
        solved = true;
        streak++;
        const s = loadStats();
        s.total = (s.total || 0) + 1;
        s.best = Math.max(s.best || 0, streak);
        saveStats(s);
      } else if (r.kind === "wrong") {
        wrongOnce = true;
        streak = 0;
      }
      renderActions();
      updateStats();
    }
    function renderActions() {
      replace(actions,
        solved ? button("Дальше →", next) : button("Проверить", check),
        !solved && wrongOnce && button("Показать ответ", showAnswer, { kind: "text" }),
        !solved && button("Пропустить", () => { streak = 0; next(); }, { kind: "text" }));
    }
    const statsBox = h("div", { class: "trainer-stats" });
    function updateStats() {
      const s = loadStats();
      replace(statsBox,
        h("div", null, h("b", null, String(streak)), h("span", null, "подряд 🔥")),
        h("div", null, h("b", null, String(s.best || 0)), h("span", null, "рекорд")),
        h("div", null, h("b", null, String(s.total || 0)), h("span", null, "решено всего")));
    }
    updateStats();
    renderActions();

    // ----- Само задание -----
    let content;
    if (task.type === "formula") {
      const f = field({
        label: "Формула", autocapitalize: "none", inputmode: "latin",
        oninput: (v) => { f.setSupport(v.trim() ? "Получается: " + Chem.pretty(Chem.normalize(v)) : ""); replace(result); },
        onenter: check,
      });
      input = () => f.input.value;
      content = [h("div", { class: "task-name" }, task.text.charAt(0).toUpperCase() + task.text.slice(1)), h("div", { class: "mt" }, f)];
      setTimeout(() => f.input.focus(), 0);
    } else if (task.type === "molar") {
      const el = h("input", { class: "molar-input", inputmode: "decimal", autocomplete: "off", "aria-label": "Молярная масса, г/моль" });
      el.addEventListener("keydown", (e) => { if (e.key === "Enter") check(); });
      el.addEventListener("input", () => replace(result));
      input = () => el.value.trim();
      content = h("div", { class: "molar mt" }, h("span", { class: "task-name" }, `M(${Chem.pretty(Chem.normalize(task.text))}) =`), el, h("span", null, "г/моль"));
      setTimeout(() => el.focus(), 0);
    } else if (task.type === "equation") {
      const eq = task.eq;
      const inputs = eq.substances.map((formula, k) => {
        const el = h("input", { class: "coef", inputmode: "numeric", maxlength: "2", autocomplete: "off", "aria-label": "Коэффициент перед " + Chem.pretty(formula) });
        el.addEventListener("input", () => { el.value = el.value.replace(/\D/g, "").slice(0, 2); replace(result); });
        el.addEventListener("keydown", (e) => {
          if (e.key !== "Enter") return;
          if (k < inputs.length - 1) inputs[k + 1].focus(); else check();
        });
        return el;
      });
      input = () => inputs.map((x) => x.value).join("|");
      content = [
        h("p", { class: "tiny muted" }, "Пустая клеточка — коэффициент 1."),
        h("div", { class: "equation" }, eq.substances.map((formula, k) => h("span", { class: "term" },
          k > 0 && h("span", { class: "sign" }, k === eq.reactants.length ? "→" : "+"), inputs[k], h("span", null, Chem.pretty(formula))))),
      ];
      setTimeout(() => inputs[0].focus(), 0);
    } else {
      const chosen = new Set();
      const name = "trainer-choice";
      input = () => [...chosen].join(",");
      content = [
        h("p", { class: "mt" }, task.text),
        h("div", { class: "choice-list", role: "radiogroup" }, task.options.map((text, k) => {
          const radio = h("input", { type: "radio", name });
          radio.addEventListener("change", () => { chosen.clear(); chosen.add(k + 1); replace(result); });
          return h("label", { class: "choice" }, radio, h("span", null, text));
        })),
      ];
    }

    return [
      statsBox,
      h("div", { class: "card" },
        h("div", { class: "row" }, emojiBadge("🎯", "c-chemistry"), h("span", { class: "task-label grow" }, LABELS[task.kind])),
        content,
        actions,
        result),
    ];
  }

  window.Trainer = { screen, makeTask };
})();
