/// <reference path="../pb_data/types.d.ts" />

// Новый учебный год (поля — в pb_migrations/1762900000_new_year.js).
// POST /api/new-year {clearDeadlines, clearClassChats} — учитель переводит своих учеников:
// «8А» → «9А», «11Б» → выпускник. Класс без номера («Кружок») не меняется.
// По желанию убираются сроки сдачи у практик учителя и сообщения в чатах классов
// (иначе новый 8А увидит прошлогодние сроки и переписку).

routerAdd("POST", "/api/new-year", (e) => {
  const auth = e.auth
  if (!auth.getBool("isTeacher")) throw new ForbiddenError("Это может только учитель")
  const body = e.requestInfo().body || {}
  const result = { moved: 0, graduated: 0, deadlinesCleared: 0, messagesDeleted: 0 }
  e.app.runInTransaction((tx) => {
    tx.findRecordsByFilter("users", "isTeacher = false && graduated = false && teacher = {:t}", "", 0, 0, { t: auth.id }).forEach((st) => {
      const group = st.getString("group").trim()
      const m = /^(\d{1,2})(.*)$/.exec(group)
      const grade = m ? parseInt(m[1], 10) : 0
      if (grade >= 11) {
        st.set("graduated", true)
        st.set("graduatedFrom", group)
        st.set("group", "")
        result.graduated++
      } else if (grade >= 1) {
        st.set("group", String(grade + 1) + m[2])
        result.moved++
      } else {
        return
      }
      tx.saveNoValidate(st)
    })
    if (body.clearDeadlines) {
      tx.findRecordsByFilter("practices", 'owner = {:t} && deadline != ""', "", 0, 0, { t: auth.id }).forEach((p) => {
        p.set("deadline", "")
        tx.saveNoValidate(p)
        result.deadlinesCleared++
      })
    }
    if (body.clearClassChats) {
      tx.findRecordsByFilter("messages", 'teacher = {:t} && kind = "class"', "", 0, 0, { t: auth.id }).forEach((msg) => {
        tx.delete(msg)
        result.messagesDeleted++
      })
    }
  })
  return e.json(200, result)
}, $apis.requireAuth("users"))
