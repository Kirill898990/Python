// Задания от нейросети: текст запроса к YandexGPT и проверка того, что она вернула.
// Отдельный файл, чтобы это можно было проверить обычным node (server/tools/check_ai.js).
const Chem = require(`${__hooks}/../pb_public/js/chem.js`)

const TYPES = {
  choice: {
    title: "Тест с выбором ответа",
    format: '{"type": "choice", "text": "Вопрос?", "options": ["вариант 1", "вариант 2", "вариант 3", "вариант 4"], "answer": "2"} — ' +
      'answer: номера верных вариантов через запятую, начиная с 1; если верных несколько, добавь "multiple": true',
  },
  fill: {
    title: "Пропущенные слова",
    format: '{"type": "fill", "text": "Вода состоит из ___ и ___.", "answer": "водорода|кислорода"} — каждый пропуск — три подчёркивания ___; ' +
      "answer — правильные слова для пропусков по порядку через «|», другие допустимые варианты слова — через «/»",
  },
  formula: {
    title: "Формула по названию вещества",
    format: '{"type": "formula", "text": "оксид алюминия", "answer": "Al2O3"} — формула латиницей, индексы обычными цифрами',
  },
  molar: {
    title: "Молярная масса",
    format: '{"type": "molar", "text": "H2SO4"} — только формула вещества, ответ посчитает программа',
  },
  equation: {
    title: "Расставить коэффициенты",
    format: '{"type": "equation", "text": "Al + O2 = Al2O3", "answer": "4 3 2"} — уравнение без коэффициентов, ' +
      "answer — наименьшие правильные коэффициенты через пробел для каждого вещества по порядку",
  },
  question: {
    title: "Вопрос с развёрнутым ответом",
    format: '{"type": "question", "text": "Объясни, почему ..."} — ответ ученика проверит учитель',
  },
}

/** Текст для нейросети: кто она, какие задания нужны и в каком виде вернуть ответ. */
function systemPrompt({ subject, grade, title, count, types }) {
  const subj = subject === "biology" ? "биологии" : "химии"
  return [
    `Ты помогаешь школьному учителю ${subj} составлять задания для учеников ${grade} класса.`,
    title ? `Задания пойдут в практику «${title}».` : "",
    `Составь ровно ${count} заданий по запросу учителя. Можно использовать только эти виды заданий:`,
    ...types.map((t) => `- ${TYPES[t].title}: ${TYPES[t].format}.`),
    "Требования: задания соответствуют школьной программе этого класса, формулировки короткие и понятные, без ошибок;",
    "неверные варианты в тестах правдоподобные; задания не повторяются; химические формулы пиши латиницей.",
    'Ответ — только JSON-объект {"tasks": [ ... ]} без пояснений и без Markdown.',
  ].filter(Boolean).join("\n")
}

/** Вытащить JSON из ответа нейросети (иногда она оборачивает его в ```json … ```). */
function parseJson(text) {
  let s = String(text || "").trim().replace(/^```(?:json)?\s*/i, "").replace(/```\s*$/, "")
  const start = s.search(/[[{]/)
  const end = Math.max(s.lastIndexOf("}"), s.lastIndexOf("]"))
  if (start < 0 || end < start) return null
  try {
    return JSON.parse(s.slice(start, end + 1))
  } catch (_) {
    return null
  }
}

/**
 * Привести задания нейросети к виду наших заданий и проверить тем же кодом, что редактор учителя.
 * Неверные (формула не разбирается, уравнение не уравнено, нет верного ответа) отбрасываются.
 */
function cleanTasks(raw, types) {
  const list = Array.isArray(raw) ? raw : raw && Array.isArray(raw.tasks) ? raw.tasks : []
  const ok = []
  let dropped = 0
  list.slice(0, 30).forEach((x) => {
    if (!x || typeof x !== "object" || types.indexOf(String(x.type)) < 0) return dropped++
    const type = String(x.type)
    const text = String(x.text || "").trim().slice(0, 1000)
    let answer = Array.isArray(x.answer) ? x.answer.join(type === "equation" ? " " : type === "fill" ? "|" : ",") : String(x.answer == null ? "" : x.answer).trim()
    let options = Array.isArray(x.options) ? x.options.map((o) => String(o).trim()).filter(Boolean).slice(0, 8) : []
    let multiple = false
    if (type === "choice") {
      const right = Chem.parseChoice(answer)
      answer = right.join(",")
      multiple = right.length > 1
    } else {
      options = []
    }
    if (type === "molar" || type === "question") answer = ""
    if (type === "formula") answer = Chem.normalize(answer)
    const fixedText = type === "fill" ? text.replace(/_{3,}/g, "___") : type === "molar" ? Chem.normalize(text) : text
    if (Chem.validateTask(type, fixedText, answer, options)) return dropped++
    ok.push({ type, text: fixedText, answer, options, multiple })
  })
  return { tasks: ok, dropped }
}

module.exports = { TYPES, systemPrompt, parseJson, cleanTasks }
