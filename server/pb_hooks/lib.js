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

module.exports = { Chem, taskData, hasInput }
