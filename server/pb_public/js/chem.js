/*
 * Химические формулы: разбор, подсчёт атомов, проверка ответов.
 * Этот же файл подключает сервер (server/pb_hooks): ответы учеников проверяются на сервере,
 * поэтому правильные ответы не попадают ни в браузер, ни в приложение ученика.
 */
(function (global) {
  "use strict";

  const ELEMENTS = new Set((
    "H He Li Be B C N O F Ne Na Mg Al Si P S Cl Ar K Ca Sc Ti V Cr Mn Fe Co Ni Cu Zn " +
    "Ga Ge As Se Br Kr Rb Sr Y Zr Nb Mo Tc Ru Rh Pd Ag Cd In Sn Sb Te I Xe Cs Ba " +
    "La Ce Pr Nd Pm Sm Eu Gd Tb Dy Ho Er Tm Yb Lu Hf Ta W Re Os Ir Pt Au Hg Tl Pb " +
    "Bi Po At Rn Fr Ra Ac Th Pa U Np Pu Am Cm Bk Cf Es Fm Md No Lr Rf Db Sg Bh Hs " +
    "Mt Ds Rg Cn Nh Fl Mc Lv Ts Og"
  ).split(" "));

  const SUBSCRIPTS = "₀₁₂₃₄₅₆₇₈₉";

  // Русские буквы, похожие на латинские: ученики часто пишут «Н2О» в русской раскладке.
  const LOOKALIKES = {
    "А": "A", "В": "B", "Е": "E", "К": "K", "М": "M", "Н": "H", "О": "O", "Р": "P", "С": "C",
    "Т": "T", "Х": "X", "а": "a", "е": "e", "о": "o", "р": "p", "с": "c", "у": "y", "х": "x",
  };

  /** «Fe(OH)3» → «Fe(OH)₃», «CuSO4·5H2O» → «CuSO₄·5H₂O» (число после точки — не индекс). */
  function pretty(formula) {
    let out = "";
    let index = false;
    for (const ch of String(formula)) {
      if (ch >= "0" && ch <= "9") {
        out += index ? SUBSCRIPTS[Number(ch)] : ch;
      } else {
        out += ch;
        index = !/[\s·*•+=→(-]/.test(ch);
      }
    }
    return out;
  }

  /** Вариант ответа «CO2» показываем как «CO₂», а обычные слова не трогаем. */
  function prettyIfFormula(text) {
    const s = String(text).trim();
    return /^[A-Za-z][A-Za-z0-9()·*]*$/.test(s) && parse(s).ok ? pretty(s) : String(text);
  }

  /** Убирает пробелы, меняет нижние индексы на цифры и русские буквы на латинские. */
  function normalize(input) {
    let out = "";
    for (const ch of String(input)) {
      if (/\s/.test(ch)) continue;
      const sub = SUBSCRIPTS.indexOf(ch);
      if (sub >= 0) out += String(sub);
      else out += LOOKALIKES[ch] || ch;
    }
    return out;
  }

  class FormulaError extends Error {}

  /** Считает атомы: {ok: true, atoms: Map} или {ok: false, message}. */
  function parse(formula) {
    const s = normalize(formula);
    if (!s) return { ok: false, message: "Впиши формулу" };
    let pos = 0;
    const fail = (message) => { throw new FormulaError(message); };
    const isDigit = (c) => c >= "0" && c <= "9";
    const add = (map, el, n) => map.set(el, (map.get(el) || 0) + n);

    function readCount() {
      const start = pos;
      while (pos < s.length && isDigit(s[pos])) pos++;
      if (start === pos) return 1;
      const n = parseInt(s.slice(start, pos), 10);
      if (n === 0) fail("Индекс не может быть равен нулю");
      return n;
    }

    function group() {
      const atoms = new Map();
      while (pos < s.length) {
        const ch = s[pos];
        if (ch === "(") {
          pos++;
          const inner = group();
          if (pos >= s.length || s[pos] !== ")") fail("Не хватает закрывающей скобки");
          pos++;
          if (inner.size === 0) fail("Пустые скобки");
          const count = readCount();
          inner.forEach((n, el) => add(atoms, el, n * count));
        } else if (ch === ")") {
          return atoms;
        } else if (ch >= "A" && ch <= "Z") {
          pos++;
          let symbol = ch;
          if (pos < s.length && s[pos] >= "a" && s[pos] <= "z") {
            symbol += s[pos];
            pos++;
          }
          if (!ELEMENTS.has(symbol)) fail("Нет такого элемента: " + symbol);
          add(atoms, symbol, readCount());
        } else if (ch >= "a" && ch <= "z") {
          fail(`Символ элемента начинается с заглавной буквы: ${ch.toUpperCase()}, а не ${ch}`);
        } else if (isDigit(ch)) {
          fail(pos === 0 ? "Число в начале не нужно: здесь пишется только формула" : "Индекс стоит не на месте");
        } else {
          fail(`Непонятный символ «${ch}». Пиши латинскими буквами`);
        }
      }
      return atoms;
    }

    try {
      const atoms = group();
      if (pos < s.length) fail("Лишняя закрывающая скобка");
      return { ok: true, atoms };
    } catch (e) {
      if (e instanceof FormulaError) return { ok: false, message: e.message };
      throw e;
    }
  }

  function sameAtoms(a, b) {
    if (a.size !== b.size) return false;
    for (const [el, n] of a) if (b.get(el) !== n) return false;
    return true;
  }

  function sameKeys(a, b) {
    if (a.size !== b.size) return false;
    for (const el of a.keys()) if (!b.has(el)) return false;
    return true;
  }

  /** {kind: "correct"} | {kind: "hint", message} | {kind: "wrong", message} */
  function checkFormula(input, answer) {
    const typed = normalize(input);
    if (typed === answer) return { kind: "correct" };
    const r = parse(typed);
    if (!r.ok) return { kind: "hint", message: r.message };
    const answerAtoms = parse(answer).atoms;
    if (sameAtoms(r.atoms, answerAtoms)) {
      return { kind: "hint", message: "Почти! Атомы подобраны верно, но формулу принято записывать по-другому." };
    }
    if (sameKeys(r.atoms, answerAtoms)) {
      return { kind: "wrong", message: "Элементы верные, а индексы нет. Проверь валентности." };
    }
    return { kind: "wrong", message: "Проверь, из каких элементов состоит вещество." };
  }

  // ---------- Молярная масса ----------

  /** Относительные атомные массы, округлённые как в школьной таблице Менделеева. */
  const ATOMIC_MASS = {
    H: 1, He: 4, Li: 7, Be: 9, B: 11, C: 12, N: 14, O: 16, F: 19, Ne: 20, Na: 23, Mg: 24, Al: 27, Si: 28,
    P: 31, S: 32, Cl: 35.5, Ar: 40, K: 39, Ca: 40, Sc: 45, Ti: 48, V: 51, Cr: 52, Mn: 55, Fe: 56, Co: 59,
    Ni: 59, Cu: 64, Zn: 65, Ga: 70, Ge: 73, As: 75, Se: 79, Br: 80, Kr: 84, Rb: 85.5, Sr: 88, Y: 89,
    Zr: 91, Nb: 93, Mo: 96, Ru: 101, Rh: 103, Pd: 106, Ag: 108, Cd: 112, In: 115, Sn: 119, Sb: 122,
    Te: 128, I: 127, Xe: 131, Cs: 133, Ba: 137, La: 139, W: 184, Pt: 195, Au: 197, Hg: 201, Tl: 204,
    Pb: 207, Bi: 209, Ra: 226, U: 238,
  };

  /**
   * Молярная масса в г/моль: {ok: true, value} или {ok: false, message}.
   * Понимает кристаллогидраты: «CuSO4·5H2O» (точку можно заменить на *).
   */
  function molarMass(formula) {
    const parts = normalize(formula).split(/[·*•]/);
    let total = 0;
    for (const raw of parts) {
      const m = /^(\d*)(.*)$/.exec(raw);
      const times = m[1] ? parseInt(m[1], 10) : 1;
      const r = parse(m[2]);
      if (!r.ok) return r;
      for (const [el, n] of r.atoms) {
        if (!(el in ATOMIC_MASS)) return { ok: false, message: `Нет атомной массы для ${el}` };
        total += ATOMIC_MASS[el] * n * times;
      }
    }
    return { ok: true, value: Math.round(total * 10) / 10 };
  }

  /** «98,08 г/моль» → 98.08; NaN, если числа нет. */
  function parseNumber(input) {
    const m = /[-+]?\d+(?:[.,]\d+)?/.exec(String(input).replace(/\s+/g, ""));
    return m ? parseFloat(m[0].replace(",", ".")) : NaN;
  }

  /** Число для показа: 35.5 → «35,5». */
  function formatNumber(n) {
    return String(n).replace(".", ",");
  }

  // ---------- Пропущенные слова ----------

  /** Пропуск в тексте задания — три и больше подчёркиваний. */
  const BLANK = /_{3,}/;

  /** «Вода состоит из [водорода] и [кислорода]» → {text: «… из ___ и ___», answer: «водорода|кислорода»} */
  function splitBlanks(source) {
    const answers = [];
    const text = String(source).replace(/\[([^\]]*)\]/g, (_, inner) => {
      answers.push(inner.split("/").map((x) => x.trim()).filter(Boolean).join("/"));
      return "___";
    });
    return { text, answer: answers.join("|") };
  }

  /** Обратно: текст с пропусками и ответы → текст со словами в квадратных скобках (для редактора). */
  function joinBlanks(text, answer) {
    const answers = String(answer || "").split("|");
    let i = 0;
    return String(text).replace(/_{3,}/g, () => "[" + (answers[i++] || "") + "]");
  }

  function blankCount(text) {
    return String(text).split(BLANK).length - 1;
  }

  /** Сравнение слов без учёта регистра, буквы ё, лишних пробелов и точки в конце. */
  function sameWord(a, b) {
    const clean = (v) => normalizeDigits(String(v)).toLowerCase().replace(/ё/g, "е").replace(/\s+/g, " ").replace(/[.,;:!?]+$/, "").trim();
    return clean(a) !== "" && clean(a) === clean(b);
  }

  function normalizeDigits(v) {
    return v.replace(/[₀-₉]/g, (c) => String(SUBSCRIPTS.indexOf(c)));
  }

  // ---------- Тест с выбором ответа ----------

  /** «1, 3» → [1, 3] (номера вариантов с единицы, по возрастанию, без повторов). */
  function parseChoice(value) {
    return [...new Set(String(value).split(/[\s,;|]+/).filter(Boolean).map((x) => parseInt(x, 10)).filter((n) => n >= 1))]
      .sort((a, b) => a - b);
  }

  // ---------- Уравнения ----------

  const ARROW = /→|->|=>|=/;

  /** «Al + O2 = Al2O3» → {reactants, products}. null, если записано неправильно. */
  function parseEquation(text) {
    const sides = String(text).split(ARROW);
    if (sides.length !== 2) return null;
    const side = (s) => s.split("+").map(normalize);
    const reactants = side(sides[0]);
    const products = side(sides[1]);
    const all = reactants.concat(products);
    if (all.some((f) => !f || !parse(f).ok)) return null;
    return { reactants, products, substances: all };
  }

  /** «4Al + 3O₂ → 2Al₂O₃». Коэффициент 1 не пишется. */
  function prettyEquation(eq, coefficients) {
    const side = (list, offset) => list.map((f, i) => {
      const c = coefficients ? coefficients[offset + i] : 1;
      return (c === 1 ? "" : String(c)) + pretty(f);
    }).join(" + ");
    return side(eq.reactants, 0) + " → " + side(eq.products, eq.reactants.length);
  }

  function balanceOf(eq, coefficients) {
    const left = new Map();
    const right = new Map();
    eq.substances.forEach((formula, i) => {
      const target = i < eq.reactants.length ? left : right;
      parse(formula).atoms.forEach((n, el) => target.set(el, (target.get(el) || 0) + n * coefficients[i]));
    });
    const elements = [...new Set([...left.keys(), ...right.keys()])];
    return elements.map((element) => ({ element, left: left.get(element) || 0, right: right.get(element) || 0 }));
  }

  const gcd = (a, b) => (b === 0 ? a : gcd(b, a % b));

  /**
   * Проверяет коэффициенты ученика. Пустое поле — коэффициент 1.
   * {kind: "correct"} | {kind: "notReduced", divisor} | {kind: "unbalanced", mismatches} | {kind: "invalid", message}
   */
  function checkEquation(eq, inputs) {
    const coefficients = [];
    for (const raw of inputs) {
      const text = String(raw).trim();
      if (!text) { coefficients.push(1); continue; }
      const n = /^[+-]?\d+$/.test(text) ? parseInt(text, 10) : NaN;
      if (!(n >= 1 && n <= 99)) return { kind: "invalid", message: "Коэффициенты — целые числа от 1 до 99" };
      coefficients.push(n);
    }
    const mismatches = balanceOf(eq, coefficients).filter((b) => b.left !== b.right);
    if (mismatches.length) return { kind: "unbalanced", mismatches };
    const divisor = coefficients.reduce(gcd);
    return divisor > 1 ? { kind: "notReduced", divisor } : { kind: "correct" };
  }

  /** «4 3 2» или «4, 3, 2» → [4, 3, 2]; null, если не числа. */
  function parseCoefficients(text) {
    const parts = String(text).split(/[\s,;]+/).filter(Boolean);
    if (!parts.length) return null;
    const numbers = [];
    for (const p of parts) {
      if (!/^[+-]?\d+$/.test(p)) return null;
      numbers.push(parseInt(p, 10));
    }
    return numbers;
  }

  // ---------- Проверка любого задания ----------

  /** Типы заданий, которые проверяются автоматически. */
  const CHECKABLE = ["formula", "equation", "choice", "fill", "molar"];
  const isCheckableType = (type) => CHECKABLE.includes(type);

  /**
   * Проверяет ответ ученика. task: {type, text, answer, options}; input — то, что ввёл ученик
   * (для уравнения коэффициенты через «|», для теста номера через запятую, для пропусков слова через «|»).
   * Ответ: {kind: "correct" | "hint" | "wrong", message, blanks?}. blanks — какие пропуски заполнены верно.
   */
  function checkTask(task, input) {
    const value = String(input == null ? "" : input);
    switch (task.type) {
      case "formula": {
        if (!value.trim()) return { kind: "hint", message: "Впиши формулу" };
        const r = checkFormula(value, normalize(task.answer));
        return r.kind === "correct" ? { kind: "correct", message: "Верно! " + pretty(normalize(value)) } : r;
      }
      case "equation": {
        const eq = parseEquation(task.text);
        if (!eq) return { kind: "hint", message: "В задании ошибка: уравнение записано неправильно" };
        const values = value.split("|");
        while (values.length < eq.substances.length) values.push("");
        const r = checkEquation(eq, values.slice(0, eq.substances.length));
        if (r.kind === "correct") {
          const coefficients = values.map((v) => parseInt(v, 10) || 1);
          return { kind: "correct", message: "Верно! " + prettyEquation(eq, coefficients) };
        }
        if (r.kind === "notReduced") {
          return { kind: "hint", message: `Атомов поровну, но все коэффициенты делятся на ${r.divisor}. Нужны самые маленькие.` };
        }
        if (r.kind === "unbalanced") {
          return {
            kind: "wrong",
            message: "Пока не сходится:\n" + r.mismatches.map((m) => `• ${m.element}: слева ${m.left}, справа ${m.right}`).join("\n"),
          };
        }
        return { kind: "hint", message: r.message };
      }
      case "choice": {
        const chosen = parseChoice(value);
        if (!chosen.length) return { kind: "hint", message: "Выбери ответ" };
        const right = parseChoice(task.answer);
        if (chosen.length === right.length && chosen.every((n, i) => n === right[i])) return { kind: "correct", message: "Верно!" };
        if (right.length > 1) {
          const hits = chosen.filter((n) => right.includes(n)).length;
          if (hits > 0 && hits === chosen.length) return { kind: "hint", message: "Выбраны не все верные ответы" };
          return { kind: "wrong", message: "Среди выбранных есть неверный ответ" };
        }
        return { kind: "wrong", message: "Неверно. Подумай ещё" };
      }
      case "fill": {
        const words = value.split("|");
        const answers = String(task.answer || "").split("|");
        const blanks = answers.map((alts, i) => alts.split("/").some((alt) => sameWord(words[i] || "", alt)));
        const ok = blanks.filter(Boolean).length;
        if (!words.some((w) => w.trim())) return { kind: "hint", message: "Заполни пропуски", blanks };
        if (ok === answers.length) return { kind: "correct", message: answers.length > 1 ? "Верно! Все пропуски заполнены правильно" : "Верно!", blanks };
        return { kind: "wrong", message: `Верно ${ok} из ${answers.length}. Исправь выделенные пропуски`, blanks };
      }
      case "molar": {
        const m = molarMass(task.text);
        if (!m.ok) return { kind: "hint", message: "В задании ошибка: " + m.message };
        const n = parseNumber(value);
        if (isNaN(n)) return { kind: "hint", message: "Впиши число, например 98" };
        // Принимаем и школьные округлённые массы (98), и точные (98,08).
        if (Math.abs(n - m.value) <= Math.max(0.5, m.value * 0.003)) {
          return { kind: "correct", message: `Верно! M(${pretty(normalize(task.text))}) = ${formatNumber(m.value)} г/моль` };
        }
        return { kind: "wrong", message: "Неверно. Сложи атомные массы всех атомов: Ar элемента × число атомов" };
      }
      default:
        return { kind: "hint", message: "Это задание проверяет учитель" };
    }
  }

  /** Проверка задания, которое создаёт учитель. Текст ошибки или null. */
  function validateTask(type, text, answer, options) {
    if (!String(text).trim()) {
      return {
        formula: "Впиши название вещества", equation: "Впиши уравнение", molar: "Впиши формулу вещества",
        fill: "Впиши текст с пропусками", choice: "Впиши вопрос",
      }[type] || "Впиши текст вопроса";
    }
    if (type === "molar") {
      const m = molarMass(text);
      return m.ok ? null : "Формула: " + m.message;
    }
    if (type === "fill") {
      const n = blankCount(text);
      if (!n) return "Отметь пропуски: напиши правильные слова в квадратных скобках, например [кислорода]";
      const answers = String(answer || "").split("|");
      if (answers.length !== n || answers.some((a) => !a.trim())) return "В каждых квадратных скобках должно быть слово";
      return null;
    }
    if (type === "choice") {
      const list = (options || []).map((o) => String(o).trim());
      if (list.filter(Boolean).length < 2) return "Нужно хотя бы два варианта ответа";
      if (list.some((o) => !o)) return "Заполни все варианты или удали пустые";
      const right = parseChoice(answer);
      if (!right.length) return "Отметь галочкой хотя бы один верный ответ";
      if (right.some((n) => n > list.length)) return "Верный ответ указан неправильно";
      return null;
    }
    if (type === "formula") {
      const r = parse(answer);
      if (!r.ok) return String(answer).trim() ? "Формула: " + r.message : "Впиши правильную формулу";
      return null;
    }
    if (type === "equation") return validateEquation(text, answer);
    return null;
  }

  function validateEquation(text, answer) {
    const sides = String(text).split(ARROW);
    if (sides.length !== 2) return "Запиши уравнение так: Al + O2 = Al2O3";
    for (const formula of sides.flatMap((s) => s.split("+"))) {
      const r = parse(formula);
      if (!r.ok) return `«${formula.trim()}»: ${r.message}`;
    }
    const eq = parseEquation(text);
    if (!eq) return "Запиши уравнение так: Al + O2 = Al2O3";
    const parts = String(answer).split(/[\s,;]+/).filter(Boolean);
    if (parts.length !== eq.substances.length) {
      return `Нужно столько коэффициентов, сколько веществ в уравнении (${eq.substances.length}), через пробел`;
    }
    const r = checkEquation(eq, parts);
    switch (r.kind) {
      case "correct": return null;
      case "notReduced": return `Коэффициенты можно сократить в ${r.divisor} раза`;
      case "invalid": return r.message;
      default:
        return "Коэффициенты не уравнивают реакцию: " +
          r.mismatches.map((m) => `${m.element} слева ${m.left}, справа ${m.right}`).join("; ");
    }
  }

  const Chem = {
    pretty, prettyIfFormula, normalize, parse, checkFormula,
    parseEquation, prettyEquation, checkEquation, balanceOf, parseCoefficients, validateTask,
    molarMass, parseNumber, formatNumber, splitBlanks, joinBlanks, blankCount, BLANK, parseChoice,
    checkTask, isCheckableType, CHECKABLE,
    /** Атомная масса элемента, как в школьной таблице (для объяснения ответа в тренажёре). */
    atomicMass: (element) => ATOMIC_MASS[element],
  };
  global.Chem = Chem;
  // На сервере (server/pb_hooks) файл подключается через require().
  if (typeof module === "object" && module && module.exports) module.exports = Chem;
})(typeof window !== "undefined" ? window : globalThis);
