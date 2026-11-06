// Общие функции для серверных обработчиков (main.pb.js).
// Проверка ответов — тот же файл chem.js, что работает на сайте, поэтому результаты совпадают.
const Chem = require(`${__hooks}/../pb_public/js/chem.js`)

/** Запись задания из базы → объект для Chem.checkTask. */
function taskData(record) {
  let options = []
  try {
    options = JSON.parse(record.getString("options") || "[]") || []
  } catch (_) {}
  return {
    id: record.id,
    type: record.getString("type"),
    text: record.getString("text"),
    answer: record.getString("answer"),
    options,
  }
}

/** Ввёл ли ученик хоть что-нибудь (для уравнения и пропусков части разделены «|»). */
function hasInput(input) {
  return String(input == null ? "" : input).replace(/[|,]/g, "").trim() !== ""
}

/** Время из поля даты PocketBase («2026-11-05 10:00:00.000Z») в миллисекундах. Пусто — NaN. */
function timeOf(value) {
  const s = String(value || "").trim()
  return s ? Date.parse(s.replace(" ", "T")) : NaN
}

/** Контрольная: ученик видит результаты после срока сдачи или когда учитель поставил оценку. */
function examResultsOpen(practice, submission) {
  if (submission.getInt("grade") > 0) return true
  const deadline = timeOf(practice.getString("deadline"))
  return !isNaN(deadline) && deadline <= Date.now()
}

/**
 * Класс в одном виде: без пробелов, большими буквами, латинские буквы-двойники заменены русскими.
 * «8 б» → «8Б», «8A» (латиница) → «8А». Иначе один класс разъехался бы на несколько.
 */
function normalizeGroup(value) {
  const twins = { A: "А", B: "В", C: "С", E: "Е", H: "Н", K: "К", M: "М", O: "О", P: "Р", T: "Т", X: "Х", Y: "У" }
  return String(value || "").replace(/\s+/g, "").toUpperCase().replace(/[ABCEHKMOPTXY]/g, (ch) => twins[ch])
}

/**
 * Подпись ответов работы: какие задания и что ввёл ученик. Одинаковые ответы — одинаковая подпись.
 * Приложение 1.0 не присылает номера заданий — тогда берём текст задания и ответ.
 */
function answersSignature(list) {
  const text = (Array.isArray(list) ? list : [])
    .map((a) => (a && (a.task || a.prompt || "")) + "\u0001" + (a ? String(a.input == null ? a.answer || "" : a.input) : ""))
    .join("\u0002")
  return $security.sha256(text)
}

module.exports = { Chem, taskData, hasInput, timeOf, examResultsOpen, normalizeGroup, answersSignature }
