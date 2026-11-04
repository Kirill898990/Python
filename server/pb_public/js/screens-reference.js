/*
 * Справочник: таблица Менделеева и таблица растворимости (данные — js/reference.js).
 * Открывается с главной и прямо из практики (кнопка с книжкой), черновик ответов при этом сохраняется.
 */
(function () {
  "use strict";
  const { h, replace, topBar, segmented, dialog, button } = UI;

  /**
   * Ставит элемент в клетку сетки. Через el.style, а не атрибут style: атрибуты стилей запрещены
   * правилами безопасности сайта (Content-Security-Policy в index.html).
   */
  function place(el, row, col) {
    el.style.gridRow = String(row);
    el.style.gridColumn = String(col);
    return el;
  }

  const BLOCK_NAMES = { s: "s-элемент", p: "p-элемент", d: "d-элемент", f: "f-элемент" };
  const PERIODS = ["", "1", "2", "3", "4", "5", "6", "7"];

  /** Масса для задач: как в школьной таблице (хлор — 35,5, остальные округляем до целых). */
  function schoolMass(e) {
    if (e.mass.startsWith("[")) return e.mass;
    if (e.symbol === "Cl") return "35,5";
    return String(Math.round(parseFloat(e.mass)));
  }

  function showElement(e) {
    const groupText = e.row >= 9 ? (e.row === 9 ? "лантаноид, 3 группа" : "актиноид, 3 группа") : `${e.col} группа`;
    const period = e.row >= 9 ? e.row - 3 : e.row;
    dialog((close) => ({
      title: `${e.symbol} — ${e.name}`,
      body: [
        h("div", { class: `element-big block-${e.block}` },
          h("div", { class: "element-z" }, String(e.z)),
          h("div", { class: "element-symbol" }, e.symbol),
          h("div", { class: "element-mass" }, e.mass.replace(".", ","))),
        h("div", { class: "stat-row" }, h("span", null, "Порядковый номер"), h("b", null, String(e.z))),
        h("div", { class: "stat-row" }, h("span", null, "Относительная атомная масса Ar"), h("b", null, e.mass.replace(".", ","))),
        h("div", { class: "stat-row" }, h("span", null, "Для задач обычно берут"), h("b", null, schoolMass(e))),
        h("div", { class: "stat-row" }, h("span", null, "Период"), h("b", null, String(period))),
        h("div", { class: "stat-row" }, h("span", null, "Положение"), h("b", null, groupText)),
        h("div", { class: "stat-row" }, h("span", null, "Семейство"), h("b", null, BLOCK_NAMES[e.block])),
        e.mass.startsWith("[") && h("p", { class: "tiny muted mt" }, "В квадратных скобках — массовое число самого устойчивого изотопа: элемент радиоактивный."),
      ],
      actions: [button("Закрыть", close, { kind: "text" })],
    }));
  }

  function periodicTable() {
    const grid = h("div", { class: "ptable", role: "grid", "aria-label": "Таблица Менделеева" });
    // Номера групп сверху и периодов слева.
    for (let col = 1; col <= 18; col++) grid.append(place(h("div", { class: "ptable-label" }, String(col)), 1, col + 1));
    for (let row = 1; row <= 7; row++) grid.append(place(h("div", { class: "ptable-label" }, PERIODS[row]), row + 1, 1));
    grid.append(place(h("div", { class: "ptable-gap" }, "La–Lu"), 7, 4));
    grid.append(place(h("div", { class: "ptable-gap" }, "Ac–Lr"), 8, 4));
    Reference.elements.forEach((e) => {
      const row = e.row + 1; // строки 9 и 10 — лантаноиды и актиноиды, под таблицей с пустой строкой
      grid.append(place(h("button", {
        class: `ptable-cell block-${e.block}`, type: "button",
        title: `${e.name}, ${e.mass}`, "aria-label": `${e.z} ${e.name}`,
        onclick: () => showElement(e),
      },
      h("span", { class: "cell-z" }, String(e.z)),
      h("span", { class: "cell-symbol" }, e.symbol),
      h("span", { class: "cell-mass" }, schoolMass(e))), row, e.col + 1));
    });
    return [
      h("div", { class: "ptable-wrap" }, grid),
      h("div", { class: "legend" },
        ["s", "p", "d", "f"].map((b) => h("span", { class: "legend-item" }, h("span", { class: `legend-dot block-${b}` }), BLOCK_NAMES[b]))),
      h("p", { class: "tiny muted" }, "Нажмите на элемент, чтобы увидеть подробности. Под символом — атомная масса, как её берут в задачах."),
    ];
  }

  const SOLUBILITY_TEXT = { "Р": "растворимо", "М": "малорастворимо", "Н": "нерастворимо", "—": "не существует или разлагается водой" };
  const SOLUBILITY_CLASS = { "Р": "sol-r", "М": "sol-m", "Н": "sol-n", "—": "sol-x" };

  function solubilityTable() {
    const { cations, anions, cells } = Reference.solubility;
    const table = h("table", { class: "soltable" },
      h("thead", null, h("tr", null, h("th", { class: "corner" }, "Анион \\ катион"), cations.map((c) => h("th", { scope: "col" }, c)))),
      h("tbody", null, anions.map((a, i) => h("tr", null,
        h("th", { scope: "row" }, a),
        [...cells[i]].map((v, k) => h("td", { class: SOLUBILITY_CLASS[v], title: `${cations[k]} + ${a}: ${SOLUBILITY_TEXT[v]}` }, v))))));
    return [
      h("div", { class: "soltable-wrap" }, table),
      h("div", { class: "legend" },
        Object.entries(SOLUBILITY_TEXT).map(([k, v]) => h("span", { class: "legend-item" }, h("span", { class: `legend-box ${SOLUBILITY_CLASS[k]}` }, k), v))),
      h("p", { class: "tiny muted" }, "Р — больше 1 г вещества в 100 г воды, М — от 0,001 до 1 г, Н — меньше 0,001 г. Наведите на клетку, чтобы увидеть подсказку."),
    ];
  }

  function screen(params) {
    const tab = params.tab === "solubility" ? "solubility" : "periodic";
    const body = h("div", { class: "stack" });
    replace(body, tab === "periodic" ? periodicTable() : solubilityTable());
    return h("div", { class: "screen screen-wide" },
      topBar("Справочник", () => App.back("")),
      segmented([{ value: "periodic", label: "Таблица Менделеева" }, { value: "solubility", label: "Растворимость" }], tab,
        (v) => App.replace(v === "periodic" ? "reference" : "reference/solubility")),
      body);
  }

  window.ReferenceScreen = { screen };
})();
