/*
 * Химические формулы: разбор, подсчёт атомов, проверка ответов.
 * Это та же логика, что и в Android-приложении (android/app/src/main/java/.../chem), чтобы сайт и приложение
 * проверяли ответы одинаково.
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

  /** «Fe(OH)3» → «Fe(OH)₃» */
  function pretty(formula) {
    return String(formula).replace(/[0-9]/g, (d) => SUBSCRIPTS[Number(d)]);
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

  /** Проверка задания, которое создаёт учитель. Текст ошибки или null. */
  function validateTask(type, text, answer) {
    if (!String(text).trim()) {
      return type === "formula" ? "Впиши название вещества" : type === "equation" ? "Впиши уравнение" : "Впиши текст вопроса";
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

  global.Chem = {
    pretty, normalize, parse, checkFormula,
    parseEquation, prettyEquation, checkEquation, balanceOf, parseCoefficients, validateTask,
  };
})(typeof window !== "undefined" ? window : globalThis);
