// Проверка заданий тренажёра: все формулы разбираются, массы считаются, уравнения уравнены.
// Запуск из корня проекта: node server/tools/check_trainer.js
global.window = globalThis;
require("../pb_public/js/chem.js");
require("../pb_public/js/trainer-data.js");
const Chem = globalThis.Chem;
const D = globalThis.TrainerData;
const errors = [];
D.formulas.forEach(([name, formula]) => {
  const r = Chem.checkTask({ type: "formula", answer: formula }, formula);
  if (r.kind !== "correct") errors.push(`формула ${name} ${formula}: ${r.message}`);
});
D.molar.forEach((f) => {
  const m = Chem.molarMass(f);
  if (!m.ok) errors.push(`масса ${f}: ${m.message}`);
});
D.equations.forEach(([text, coefficients]) => {
  const eq = Chem.parseEquation(text);
  if (!eq) return errors.push(`уравнение не разбирается: ${text}`);
  if (eq.substances.length !== coefficients.length) return errors.push(`не то число коэффициентов: ${text}`);
  const r = Chem.checkTask({ type: "equation", text }, coefficients.join("|"));
  if (r.kind !== "correct") errors.push(`уравнение ${text} ${coefficients}: ${r.message}`);
  if (coefficients.every((c) => c === 1)) errors.push(`все коэффициенты 1 — слишком просто: ${text}`);
});
D.classes.forEach(([f, c]) => {
  if (!Chem.parse(Chem.normalize(f)).ok) errors.push(`класс: формула не разбирается ${f}`);
  if (!(c >= 1 && c <= D.classNames.length)) errors.push(`класс: нет класса ${c} для ${f}`);
});
D.biology.forEach(([q, options, explain]) => {
  if (!q.trim() || !explain.trim()) errors.push(`биология: пустой вопрос или пояснение: ${q}`);
  if (options.length !== 4 || new Set(options).size !== 4) errors.push(`биология: нужно 4 разных варианта: ${q}`);
});
console.log(errors.length ? errors.join("\n") : `ok: ${D.formulas.length} формул, ${D.molar.length} масс, ${D.equations.length} уравнений, ${D.classes.length} веществ, ${D.biology.length} вопросов по биологии`);
process.exit(errors.length ? 1 : 0);
