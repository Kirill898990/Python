// Письма (напоминание о конце курса) и общие мелочи для хуков магазина.
// Почта настраивается в веб-панели PocketBase: Settings → Mail settings (SMTP). Без неё письма не уходят.

/** Настроена ли почта для отправки писем. */
function mailReady(app) {
  const s = app.settings()
  return !!(s.smtp && s.smtp.enabled && s.meta.senderAddress)
}

/** Отправить письмо. → true, если ушло. */
function sendMail(app, to, subject, html) {
  if (!mailReady(app) || !to) return false
  const meta = app.settings().meta
  try {
    app.newMailClient().send(new MailerMessage({
      from: { address: meta.senderAddress, name: meta.senderName || "Простая химия" },
      to: [{ address: to }],
      subject,
      html,
    }))
    return true
  } catch (err) {
    app.logger().warn("Не получилось отправить письмо", "error", String(err), "to", to)
    return false
  }
}

const escapeHtml = (s) => String(s == null ? "" : s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c])

/** «Иван Петров» → «Иван П.» (так имя видно в отзывах). */
function shortName(name) {
  const parts = String(name || "").trim().split(/\s+/).filter(Boolean)
  if (!parts.length) return "Покупатель"
  return parts.length > 1 ? `${parts[0]} ${parts[1][0].toUpperCase()}.` : parts[0]
}

module.exports = { mailReady, sendMail, escapeHtml, shortName }
