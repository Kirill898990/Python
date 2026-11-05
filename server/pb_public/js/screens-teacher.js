/*
 * Экраны учителя: проверка работ и оценки, редактор практик и заданий, список учеников.
 * Сервер сам не пустит сюда ученика (правила в server/pb_migrations), а сайт ещё и прячет эти экраны.
 */
(function () {
  "use strict";
  const { h, replace, icon, topBar, sectionLabel, button, field, segmented, chip, emptyBox, dialog, confirm, toast, gradeBadge } = UI;
  const { content } = Api;

  const TYPES = {
    question: { title: "Вопрос", emoji: "❓" },
    choice: { title: "Тест", emoji: "☑️" },
    fill: { title: "Пропуски", emoji: "✏️" },
    formula: { title: "Формула", emoji: "🧪" },
    molar: { title: "Молярная масса", emoji: "🧮" },
    equation: { title: "Уравнение", emoji: "⚖️" },
  };

  // ======================= Панель учителя =======================
  function admin(params) {
    const tab = ["works", "journal", "tasks", "students"].includes(params.tab) ? params.tab : "works";
    const tabs = h("nav", { class: "tabs" },
      [["works", "Работы"], ["journal", "Журнал"], ["tasks", "Задания"], ["students", "Ученики"]].map(([key, title]) =>
        h("a", { class: "tab" + (key === tab ? " selected" : ""), href: `#/admin/${key}`, "aria-current": key === tab ? "page" : null }, title)));
    const body = tab === "works" ? works() : tab === "journal" ? journal() : tab === "tasks" ? practices() : students();
    return h("div", { class: "screen" }, topBar("Панель учителя", () => App.back("")), tabs, body);
  }

  // ----- Работы -----
  let onlyUnchecked = true;
  function works() {
    const box = h("div", { class: "stack" });
    const filters = h("div", { class: "row-wrap" });
    function render() {
      replace(filters,
        chip("На проверке", onlyUnchecked, () => { onlyUnchecked = true; render(); }),
        chip("Все работы", !onlyUnchecked, () => { onlyUnchecked = false; render(); }));
      replace(box, Screens.asyncBlock(() => Api.allSubmissions(onlyUnchecked), (items) => items.length
        ? items.map((s) => Screens.submissionCard(s, true))
        : emptyBox("🎉", onlyUnchecked ? "Все работы проверены" : "Ученики пока не сдавали работ")));
    }
    render();
    return h("div", { class: "stack" }, filters, box);
  }

  /** Оценка и комментарий на экране работы. */
  function gradeEditor(s) {
    let grade = s.grade || 0;
    let comment = s.comment || "";
    let saving = false;
    const saveBtn = button(s.grade ? "Изменить оценку" : "Поставить оценку", save, { cls: "btn-block", disabled: !grade });
    const commentField = field({ label: "Комментарий для ученика", multiline: true, rows: 2, value: comment, oninput: (v) => { comment = v; } });

    async function save() {
      if (saving || !grade) return;
      saving = true;
      saveBtn.disabled = true;
      try {
        const saved = await Api.grade(s.id, grade, comment);
        s.grade = saved.grade;
        replace(saveBtn, h("span", null, "Изменить оценку"));
        toast("Оценка сохранена");
      } catch (e) {
        toast(e.message || "Не получилось сохранить");
      }
      saving = false;
      saveBtn.disabled = false;
    }

    return h("div", { class: "card stack" },
      h("div", { class: "card-title" }, "Оценка"),
      segmented([2, 3, 4, 5].map((g) => ({ value: g, label: String(g) })), grade, (g) => { grade = g; saveBtn.disabled = false; }),
      commentField,
      saveBtn);
  }

  /** Контрольная: разрешить ученику написать её ещё раз (сданная работа удалится). */
  function examRetake(s) {
    const st = s.expand && s.expand.student;
    let busy = false;
    const btn = button("Разрешить пересдать", async () => {
      if (busy) return;
      const ok = await confirm("Разрешить пересдать?",
        `Эта работа удалится, и ${st ? st.name : "ученик"} сможет написать контрольную ещё раз — даже если срок уже прошёл.`, "Разрешить");
      if (!ok) return;
      busy = true;
      btn.disabled = true;
      try {
        await Api.resetExam(s.practice, s.student);
        toast("Готово: ученик может написать контрольную ещё раз");
        App.back("admin/works");
      } catch (e) {
        toast(e.message || "Не получилось");
        busy = false;
        btn.disabled = false;
      }
    }, { kind: "outline", cls: "btn-block" });
    return h("div", { class: "card stack" },
      h("div", { class: "card-title" }, "Контрольная"),
      h("p", { class: "small muted" }, "Если ученику помешали (сел телефон, пропал интернет), можно дать ему написать работу заново."),
      btn);
  }

  // ----- Список практик -----
  function practices() {
    const list = ["chemistry", "biology"].map((key) => {
      const items = content.practicesOf(key);
      return [
        sectionLabel(Content.subjectOf(key).title),
        items.length === 0 && h("p", { class: "muted small" }, "Пока нет практик"),
        items.map((p) => h("a", { class: "card card-link", href: `#/admin/practice/${p.id}` },
          h("div", { class: "grow" },
            h("div", { class: "row" }, h("span", { class: "card-title" }, `${p.grade} класс · ${p.title}`), !p.published && h("span", { class: "pill pill-draft" }, "Черновик")),
            h("div", { class: "card-sub" }, [
              `Заданий: ${content.tasksOf(p.id).length}`,
              p.exam && Content.examLabel(p).toLowerCase(),
              Array.isArray(p.classes) && p.classes.length ? "для " + p.classes.join(", ") : "для всех классов",
              Content.deadlineOf(p) && "до " + Content.formatDay(Content.deadlineOf(p)),
            ].filter(Boolean).join(" · "))),
          icon("chevron"))),
      ];
    });
    return h("div", { class: "stack" },
      h("p", { class: "muted small" }, "Здесь можно добавлять и менять практики и задания. Практика с меткой «Черновик» видна только вам, пока вы её не опубликуете."),
      list,
      h("div", { class: "fab-space" }),
      h("button", { class: "fab", onclick: () => App.go("admin/practice/new") }, "+ Новая практика"));
  }

  // ----- Журнал -----
  let journalGroup = null;
  const byClass = (a, b) => a.localeCompare(b, "ru", { numeric: true });

  /** Практики, которые задавали этому классу (черновики не считаем). */
  function practicesFor(group) {
    return Api.state.content.practices
      .filter((p) => p.published && Content.isAssigned(p, group))
      .sort((a, b) => a.subject.localeCompare(b.subject) || (a.grade - b.grade) || (a.position - b.position) || a.title.localeCompare(b.title));
  }

  /** Клетка журнала: оценка, «на проверке», «не сдано после срока» или пусто. */
  function journalCell(subs, practice) {
    const graded = subs.find((s) => s.grade > 0);
    if (graded) return { kind: "grade", grade: graded.grade, sub: graded, late: Content.isLate(graded, practice) };
    if (subs.length) return { kind: "submitted", sub: subs[0], late: Content.isLate(subs[0], practice) };
    const d = Content.deadlineOf(practice);
    return d && d < new Date() ? { kind: "missed" } : { kind: "none" };
  }

  const cellText = (c) => (c.kind === "grade" ? String(c.grade) : c.kind === "submitted" ? "сдано" : c.kind === "missed" ? "не сдано" : "");
  const average = (grades) => (grades.length ? (grades.reduce((a, b) => a + b, 0) / grades.length).toFixed(1).replace(".", ",") : "");

  function journal() {
    return Screens.asyncBlock(() => Api.journal(), ({ students, submissions }) => {
      const groups = [...new Set(students.map((s) => s.group).filter(Boolean))].sort(byClass);
      if (!groups.length) return emptyBox("👥", "Журнал появится, когда ученики зарегистрируются");
      if (!groups.includes(journalGroup)) journalGroup = groups[0];
      const chips = h("div", { class: "row-wrap" });
      const body = h("div", { class: "stack" });

      function render() {
        replace(chips, groups.map((g) => chip(g, g === journalGroup, () => { journalGroup = g; render(); })));
        const group = journalGroup;
        const kids = students.filter((s) => s.group === group).sort((a, b) => a.name.localeCompare(b.name, "ru"));
        const list = practicesFor(group);
        const subsOf = (st, p) => submissions.filter((s) => s.student === st.id && s.practice === p.id);
        const rows = kids.map((st) => {
          const cells = list.map((p) => journalCell(subsOf(st, p), p));
          return { st, cells, avg: average(cells.filter((c) => c.kind === "grade").map((c) => c.grade)) };
        });

        const table = h("table", { class: "journal" },
          h("thead", null, h("tr", null,
            h("th", { class: "name" }, "Ученик"),
            list.map((p) => h("th", { class: "practice-col", title: Content.fullTitle(p) }, `${p.grade} кл. · ${p.title}`)),
            h("th", null, "Средний"))),
          h("tbody", null, rows.map((r) => h("tr", null,
            h("td", { class: "name" }, r.st.name),
            r.cells.map((c) => h("td", { class: c.late ? "late" : "" },
              c.sub
                ? h("a", { class: "cell-link", href: `#/submission/${c.sub.id}`, title: c.late ? "Сдано с опозданием" : "Открыть работу" },
                  c.kind === "grade" ? gradeBadge(c.grade) : h("span", { class: "pill pill-muted" }, "✓"),
                  c.late && h("span", { class: "late-mark", "aria-label": "с опозданием" }, "⏰"))
                : c.kind === "missed" ? h("span", { class: "err small" }, "—") : "")),
            h("td", { class: "avg" }, r.avg)))));

        replace(body,
          list.length === 0
            ? emptyBox("📝", `Для ${group} пока нет опубликованных практик`)
            : [
              h("div", { class: "journal-wrap" }, table),
              h("p", { class: "tiny muted" }, "Цифра — оценка, ✓ — сдано и ждёт проверки, ⏰ — сдано с опозданием, красный прочерк — не сдано после срока. Нажмите на клетку, чтобы открыть работу."),
              button("Скачать для Excel", () => downloadJournal(group, list, rows), { kind: "outline", iconName: "download" }),
              sectionLabel(`Статистика · ${group}`),
              list.map((p) => practiceStats(p, kids, submissions)),
            ]);
      }
      render();
      return [chips, body];
    });
  }

  /** Сводка по практике: сколько сдали, средняя оценка, самые трудные задания. */
  function practiceStats(practice, kids, submissions) {
    const latest = kids.map((st) => submissions.find((s) => s.student === st.id && s.practice === practice.id)).filter(Boolean);
    const graded = kids.map((st) => submissions.find((s) => s.student === st.id && s.practice === practice.id && s.grade > 0)).filter(Boolean);
    const late = latest.filter((s) => Content.isLate(s, practice)).length;
    // По каждому заданию с автопроверкой: у скольких верно и сколько в среднем попыток.
    const perTask = new Map();
    latest.forEach((s) => (s.answers || []).forEach((a) => {
      if (typeof a.correct !== "boolean") return;
      const key = a.task || a.prompt;
      const t = perTask.get(key) || { prompt: a.kind === "formula" ? a.prompt : a.prompt, kind: a.kind, right: 0, total: 0, attempts: 0 };
      t.total++;
      if (a.correct) t.right++;
      t.attempts += a.attempts || 0;
      perTask.set(key, t);
    }));
    const hardest = [...perTask.values()].filter((t) => t.right < t.total).sort((a, b) => a.right / a.total - b.right / b.total).slice(0, 3);
    const short = (text) => (String(text).length > 60 ? String(text).slice(0, 57) + "…" : String(text));
    return h("div", { class: "card" },
      h("div", { class: "card-title" }, Content.fullTitle(practice)),
      h("div", { class: "stat-row" }, h("span", null, "Сдали"), h("b", null, `${latest.length} из ${kids.length}` + (late ? ` (с опозданием: ${late})` : ""))),
      h("div", { class: "stat-row" }, h("span", null, "Средняя оценка"), h("b", null, average(graded.map((s) => s.grade)) || "—")),
      hardest.length > 0 && [
        h("div", { class: "small bold mt" }, "Самые трудные задания"),
        hardest.map((t) => h("div", { class: "stat-row" },
          h("span", { class: "grow" }, short(t.prompt)),
          h("span", { class: "err" }, `верно у ${t.right} из ${t.total}` + (t.attempts ? ` · попыток ≈ ${(t.attempts / t.total).toFixed(1).replace(".", ",")}` : "")))),
      ],
      latest.length > 0 && !hardest.length && perTask.size > 0 && h("p", { class: "small ok mt" }, "Все задания с автопроверкой решены верно 🎉"));
  }

  /** Файл CSV для Excel: точка с запятой и метка UTF-8, чтобы русские буквы открылись правильно. */
  function downloadJournal(group, list, rows) {
    const esc = (v) => {
      const s = String(v == null ? "" : v);
      return /[";\r\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
    };
    const header = ["Ученик", "Класс", ...list.map((p) => Content.fullTitle(p)), "Средний балл"];
    const lines = [header].concat(rows.map((r) => [r.st.name, group, ...r.cells.map(cellText), r.avg]));
    const text = "﻿" + lines.map((line) => line.map(esc).join(";")).join("\r\n");
    const url = URL.createObjectURL(new Blob([text], { type: "text/csv;charset=utf-8" }));
    const a = h("a", { href: url, download: `Журнал ${group}.csv` });
    document.body.append(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    toast("Файл журнала скачан");
  }

  // ----- Коды классов -----
  function classCodes(groups) {
    let newGroup = "";
    const block = Screens.asyncBlock(() => Api.classCodes(), (codes) => {
      const active = codes.filter((c) => c.active);
      const groupField = field({
        label: "Класс", placeholder: "8А", autocapitalize: "characters",
        oninput: (v) => { newGroup = v.toUpperCase(); },
      });
      groupField.input.addEventListener("input", () => { groupField.input.value = groupField.input.value.toUpperCase(); });
      const create = button("Создать код", async () => {
        const g = newGroup.trim();
        if (!g) return groupField.setError("Впишите класс, например 8А");
        try {
          await Api.createClassCode(g);
          toast(`Код для ${g} создан`);
          block.reload();
        } catch (e) {
          toast(e.message || "Ошибка");
        }
      });
      const action = (fn, message) => async () => {
        try {
          await fn();
          if (message) toast(message);
          block.reload();
        } catch (e) {
          toast(e.message || "Ошибка");
        }
      };
      return [
        h("p", { class: "muted small" }, active.length
          ? "Регистрация только по коду: ученик вводит код, и класс подставляется сам. Выключите все коды, чтобы снова разрешить регистрацию без кода."
          : "Сейчас ученики регистрируются сами и пишут свой класс. Если создать код класса, зарегистрироваться можно будет только по коду — так в класс не попадут чужие."),
        codes.map((c) => h("div", { class: "card code-card" + (c.active ? "" : " inactive") },
          h("div", { class: "grow" },
            h("div", { class: "tiny muted" }, `${c.group} класс` + (c.active ? "" : " · выключен")),
            h("div", { class: "code-value" }, c.code)),
          button(c.active ? "Выключить" : "Включить", action(() => Api.setClassCodeActive(c.id, !c.active)), { kind: "text" }),
          h("button", {
            class: "icon-btn", type: "button", "aria-label": `Удалить код ${c.code}`, title: "Удалить",
            onclick: async () => {
              if (await confirm("Удалить код?", `Код ${c.code} для ${c.group} перестанет работать. Уже зарегистрированные ученики останутся.`, "Удалить")) {
                action(() => Api.deleteClassCode(c.id), "Код удалён")();
              }
            },
          }, icon("delete", 18)))),
        h("div", { class: "card stack" },
          h("div", { class: "small bold" }, "Новый код"),
          groups.length > 0 && h("div", { class: "row-wrap" }, groups.map((g) => chip(g, false, () => { groupField.input.value = g; newGroup = g; }))),
          groupField,
          create),
      ];
    });
    return block;
  }

  // ----- Ученики -----
  function students() {
    const box = Screens.asyncBlock(() => Api.students(), (all) => {
      const items = all.filter((s) => !s.graduated);
      const graduates = all.filter((s) => s.graduated);
      const groups = [...new Set(items.map((s) => s.group).filter(Boolean))].sort(byClass);
      const codes = [sectionLabel("Коды классов"), classCodes(groups), sectionLabel("Ученики")];
      const row = (st) => h("div", { class: "card student-row" },
        h("button", { class: "student-main", type: "button", onclick: () => resetPassword(st) },
          h("div", { class: "card-title" }, st.name),
          h("div", { class: "card-sub" }, [st.graduated ? `выпускник ${st.graduatedFrom}` : st.group && `${st.group} класс`, `логин ${st.username}`].filter(Boolean).join(" · "))),
        h("button", {
          class: "icon-btn", type: "button", "aria-label": `Написать: ${st.name}`, title: "Написать ученику",
          onclick: () => Chat.open({ kind: "private", student: st.id }, st.group ? `${st.name}, ${st.group}` : st.name),
        }, icon("chat")));
      if (!items.length && !graduates.length) return [codes, emptyBox("👥", "Пока никто из учеников не зарегистрировался")];
      return [
        codes,
        h("p", { class: "muted small" }, `Учеников: ${items.length}. Нажмите на ученика, чтобы сбросить ему пароль, или на 💬, чтобы написать ему.`),
        items.map(row),
        items.length > 0 && newYearCard(items, () => box.reload()),
        graduates.length > 0 && [sectionLabel(`Выпускники · ${graduates.length}`), graduates.map(row)],
      ];
    });
    return box;
  }

  /** «Новый учебный год»: все — в следующий класс, 11-е — в выпускники. С подтверждением и списком, кто куда. */
  function newYearCard(items, done) {
    const open = () => dialog((close) => {
      const counts = new Map();
      items.forEach((st) => counts.set(st.group || "", (counts.get(st.group || "") || 0) + 1));
      const lines = [...counts.keys()].sort(byClass).map((g) => {
        const next = Content.nextGroup(g);
        const n = counts.get(g);
        if (next === null) return h("li", null, h("b", null, g), " → 🎓 выпускники", h("span", { class: "muted" }, ` · ${n}`));
        if (next === g) return h("li", { class: "muted" }, `${g || "без класса"} — без изменений · ${n}`);
        return h("li", null, h("b", null, g), " → ", h("b", null, next), h("span", { class: "muted" }, ` · ${n}`));
      });
      const deadlines = h("input", { type: "checkbox", checked: true });
      const chats = h("input", { type: "checkbox" });
      const error = h("div", { class: "dialog-error" });
      const go = button("Перевести", async () => {
        go.disabled = true;
        try {
          const r = await Api.newYear(deadlines.checked, chats.checked);
          close();
          toast(`Готово: в следующий класс — ${r.moved}, выпускников — ${r.graduated}`);
          await Api.refreshContent();
          done();
        } catch (e) {
          error.textContent = e.message || "Не получилось";
          go.disabled = false;
        }
      }, { kind: "text" });
      return {
        title: "Новый учебный год",
        body: [
          h("p", null, "Все ваши ученики перейдут в следующий класс, а 11-е классы станут выпускниками — им придёт поздравление."),
          h("ul", { class: "year-moves" }, lines),
          h("label", { class: "check-row" }, deadlines, h("span", null, "Убрать прошлогодние сроки сдачи у всех практик")),
          h("label", { class: "check-row" }, chats, h("span", null, "Очистить чаты классов (личные сообщения останутся)")),
          h("p", { class: "small err" }, "Отменить перевод нельзя."),
          error,
        ],
        actions: [button("Отмена", close, { kind: "text" }), go],
      };
    });
    return h("div", { class: "card stack mt-lg" },
      h("div", { class: "card-title" }, "🎒 Новый учебный год"),
      h("p", { class: "small muted" }, "В конце года переведите всех учеников в следующий класс одной кнопкой: 8А станет 9А, а 11-е классы — выпускниками."),
      button("Перевести в следующий класс", open, { kind: "outline", cls: "btn-block" }));
  }

  function resetPassword(st) {
    let password = "";
    dialog((close) => {
      const error = h("div", { class: "dialog-error" });
      const saveBtn = button("Сохранить", async () => {
        saveBtn.disabled = true;
        try {
          await Api.resetPassword(st.id, password);
          close();
          toast(`Пароль для ${st.name} изменён`);
        } catch (e) {
          error.textContent = e.message;
          saveBtn.disabled = false;
        }
      }, { kind: "text", disabled: true });
      return {
        title: "Новый пароль",
        body: [
          h("p", null, `Для ученика ${st.name} (логин ${st.username}). Сообщите ему новый пароль.`),
          field({
            label: "Новый пароль", supporting: "Не меньше 6 символов", autocapitalize: "none", autocomplete: "new-password",
            oninput: (v) => { password = v; saveBtn.disabled = v.length < 6; error.textContent = ""; },
          }),
          error,
        ],
        actions: [button("Отмена", close, { kind: "text" }), saveBtn],
      };
    });
  }

  // ======================= Редактор практики =======================
  function editor(params) {
    let id = params.id === "new" ? "" : params.id;
    const original = id ? content.practice(id) : null;
    if (id && !original) {
      return h("div", { class: "screen" }, topBar("Редактор практики", () => App.back("admin/tasks")), emptyBox("🗂️", "Практика не найдена"));
    }
    const form = {
      title: original ? original.title : "",
      subject: original ? original.subject : "chemistry",
      grade: original ? original.grade : 8,
      published: original ? original.published : false,
      position: original ? original.position : 0,
      classes: original && Array.isArray(original.classes) ? original.classes.slice() : [],
      deadline: original ? original.deadline || "" : "",
      exam: original ? !!original.exam : false,
      timeLimit: original ? original.timeLimit || 0 : 40,
    };

    // ----- Для каких классов -----
    const classChips = h("div", { class: "row-wrap" });
    let knownClasses = [...form.classes];
    const renderClasses = () => replace(classChips,
      chip("Все классы", form.classes.length === 0, () => { form.classes = []; renderClasses(); }),
      knownClasses.map((g) => chip(g, form.classes.includes(g), () => {
        form.classes = form.classes.includes(g) ? form.classes.filter((x) => x !== g) : form.classes.concat(g).sort();
        renderClasses();
      })));
    renderClasses();
    Api.students().then((list) => {
      knownClasses = [...new Set(knownClasses.concat(list.map((st) => st.group).filter(Boolean)))]
        .sort((a, b) => a.localeCompare(b, "ru", { numeric: true }));
      renderClasses();
    }, () => {});

    // ----- Срок сдачи (до конца выбранного дня) -----
    const toDateValue = (iso) => {
      const d = Content.deadlineOf({ deadline: iso });
      if (!d) return "";
      const pad = (n) => String(n).padStart(2, "0");
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    };
    const deadlineInput = h("input", { type: "date", class: "date-input", "aria-label": "Срок сдачи" });
    deadlineInput.value = toDateValue(form.deadline);
    deadlineInput.addEventListener("change", () => {
      form.deadline = deadlineInput.value ? new Date(deadlineInput.value + "T23:59:00").toISOString() : "";
    });
    const clearDeadline = button("Без срока", () => { deadlineInput.value = ""; form.deadline = ""; }, { kind: "text" });
    let busy = false;

    const saveBtn = button(id ? "Сохранить изменения" : "Создать практику", savePractice, { cls: "btn-block" });
    const gradeChips = h("div", { class: "row-wrap" });
    const renderGrades = () => replace(gradeChips, [5, 6, 7, 8, 9, 10, 11].map((g) =>
      chip(String(g), form.grade === g, () => { form.grade = g; renderGrades(); })));
    renderGrades();

    const publishedInput = h("input", { type: "checkbox", checked: form.published, "aria-label": "Опубликовано" });
    const publishedHint = h("div", { class: "tiny muted" });
    const updatePublished = () => { publishedHint.textContent = form.published ? "Ученики видят эту практику" : "Черновик: ученики не видят"; };
    publishedInput.addEventListener("change", () => { form.published = publishedInput.checked; updatePublished(); });
    updatePublished();

    // ----- Контрольная -----
    const examInput = h("input", { type: "checkbox", checked: form.exam, "aria-label": "Контрольная работа" });
    const timeField = field({
      label: "Время на работу, минут", value: form.timeLimit ? String(form.timeLimit) : "", inputmode: "numeric",
      supporting: "Пусто или 0 — без ограничения", oninput: (v) => { form.timeLimit = parseInt(v.replace(/\D/g, ""), 10) || 0; },
    });
    const examBox = h("div", { class: "stack" },
      timeField,
      h("p", { class: "tiny muted" }, "Ответы не проверяются сразу, подсказок нет, сдать можно один раз. Задания ученик увидит только после «Начать». " +
        "Результаты — после срока сдачи (если срока нет — когда вы поставите оценку)."));
    const updateExam = () => { examBox.hidden = !form.exam; };
    examInput.addEventListener("change", () => { form.exam = examInput.checked; updateExam(); });
    updateExam();

    async function savePractice() {
      if (busy) return;
      if (!form.title.trim()) return toast("Впишите название практики");
      busy = true;
      saveBtn.disabled = true;
      try {
        const saved = await Api.savePractice({ id, ...form });
        toast("Практика сохранена");
        if (!id) {
          id = saved.id;
          App.replace(`admin/practice/${saved.id}`);
          return;
        }
      } catch (e) {
        toast(e.message || "Ошибка");
      }
      busy = false;
      saveBtn.disabled = false;
    }

    // ----- Задания -----
    const tasksBox = h("div", { class: "stack" });
    function renderTasks() {
      if (!id) {
        return replace(tasksBox, sectionLabel("Задания (0)"),
          h("p", { class: "muted small" }, "Сначала создайте практику, потом можно будет добавить задания."));
      }
      const tasks = content.tasksOf(id);
      replace(tasksBox,
        sectionLabel(`Задания (${tasks.length})`),
        tasks.map((t, i) => taskRow(t, i, tasks)),
        h("p", { class: "small bold mt" }, "Добавить задание:"),
        h("div", { class: "row-wrap" }, Object.entries(TYPES).map(([type, info]) =>
          button("+ " + info.title, () => editTask({ practice: id, type, text: "", answer: "", options: [], position: Math.max(0, ...tasks.map((x) => x.position || 0)) + 1 }), { kind: "outline" }))),
        button("Удалить практику", deletePractice, { kind: "outline", cls: "btn-danger btn-block mt-lg" }));
    }

    function taskRow(t, index, tasks) {
      const eq = t.type === "equation" ? Chem.parseEquation(t.text) : null;
      const molar = t.type === "molar" ? Chem.molarMass(t.text) : null;
      const options = Array.isArray(t.options) ? t.options : [];
      const answer = {
        formula: () => Chem.pretty(t.answer || ""),
        equation: () => (eq ? Chem.prettyEquation(eq, Chem.parseCoefficients(t.answer)) : t.answer),
        choice: () => Chem.parseChoice(t.answer).map((n) => options[n - 1]).filter(Boolean).map(Chem.prettyIfFormula).join("; "),
        fill: () => String(t.answer || "").split("|").join("; "),
        molar: () => (molar && molar.ok ? Chem.formatNumber(molar.value) + " г/моль" : ""),
      }[t.type];
      const shown = eq ? Chem.prettyEquation(eq) : molar ? `M(${Chem.pretty(t.text)})` : t.text;
      const info = TYPES[t.type] || TYPES.question;
      return h("div", { class: "card task-row" },
        h("div", { class: "grow", role: "button", tabindex: "0", onclick: () => editTask(t), onkeydown: (e) => { if (e.key === "Enter") editTask(t); } },
          h("div", { class: "task-label" }, `${index + 1}. ${info.emoji} ${info.title}`, t.variant ? h("span", { class: "pill variant-pill variant-inline" }, `Вариант ${t.variant}`) : null),
          h("div", { class: "task-row-text" }, shown),
          t.type === "choice" && h("div", { class: "tiny muted" }, options.map((o, k) => `${k + 1}) ${Chem.prettyIfFormula(o)}`).join("   ")),
          answer && answer() && h("div", { class: "small ok" }, "Ответ: " + answer()),
          t.image && h("img", { class: "task-row-image", src: Api.fileUrl(t, t.image, "800x0"), alt: "Картинка к заданию" })),
        h("div", { class: "arrows" },
          h("button", { class: "icon-btn", "aria-label": "Выше", title: "Выше", disabled: index === 0, onclick: () => move(tasks, index, index - 1) }, "▲"),
          h("button", { class: "icon-btn", "aria-label": "Ниже", title: "Ниже", disabled: index === tasks.length - 1, onclick: () => move(tasks, index, index + 1) }, "▼")),
        h("button", { class: "icon-btn", "aria-label": "Удалить задание", title: "Удалить", onclick: () => deleteTask(t) }, "🗑️"));
    }

    async function serverAction(action, success) {
      if (busy) return;
      busy = true;
      try {
        await action();
        if (success) toast(success);
      } catch (e) {
        toast(e.message || "Ошибка");
      }
      busy = false;
      renderTasks();
    }

    function move(tasks, from, to) {
      const reordered = tasks.slice();
      reordered.splice(to, 0, reordered.splice(from, 1)[0]);
      serverAction(async () => {
        for (let i = 0; i < reordered.length; i++) {
          if (reordered[i].position !== i + 1) await Api.saveTask({ ...reordered[i], position: i + 1 });
        }
      });
    }

    async function deleteTask(t) {
      if (await confirm("Удалить задание?", t.text, "Удалить")) serverAction(() => Api.deleteTask(t.id), "Задание удалено");
    }

    async function deletePractice() {
      if (!(await confirm("Удалить практику?", "Удалятся практика и все её задания. Уже сданные работы и оценки учеников сохранятся.", "Удалить"))) return;
      try {
        await Api.deletePractice(id);
        toast("Практика удалена");
        App.replace("admin/tasks");
      } catch (e) {
        toast(e.message || "Ошибка");
      }
    }

    /** Окно добавления и изменения задания. Ответ проверяется сразу, чтобы в задание не попала ошибка. */
    function editTask(task) {
      const t = { ...task, options: Array.isArray(task.options) && task.options.length ? task.options.slice() : ["", ""] };
      const info = TYPES[t.type] || TYPES.question;
      // Картинка: новый файл, убрать старую или оставить как есть.
      let imageFile = null;
      let removeImage = false;
      dialog((close) => {
        const error = h("div", { class: "dialog-error" });
        const saveBtn = button("Сохранить", save, { kind: "text" });
        const touched = () => String(t.text).trim() || String(t.answer || "").trim() || t.options.some((o) => o.trim());

        // ----- Текст задания -----
        const textLabel = {
          question: "Текст вопроса", choice: "Вопрос", fill: "Текст с пропусками", formula: "Название вещества",
          molar: "Формула вещества", equation: "Уравнение без коэффициентов",
        }[t.type];
        const placeholder = { formula: "хлорид алюминия", equation: "Al + O2 = Al2O3", molar: "H2SO4", fill: "Вода состоит из [водорода] и [кислорода]." }[t.type];
        let fillSource = t.type === "fill" && t.id ? Chem.joinBlanks(t.text, t.answer) : t.text;
        const textField = field({
          label: textLabel, placeholder,
          multiline: ["question", "choice", "fill"].includes(t.type),
          value: t.type === "fill" ? fillSource : t.text,
          autocapitalize: ["equation", "molar"].includes(t.type) ? "none" : "sentences",
          supporting: t.type === "fill" ? "Правильные слова пишите в квадратных скобках. Несколько вариантов — через «/»: [водорода/H]." : "",
          oninput: (v) => {
            if (t.type === "fill") fillSource = v; else t.text = v;
            validate();
          },
        });

        // ----- Правильный ответ -----
        const answerField = ["formula", "equation"].includes(t.type) ? field({
          label: t.type === "formula" ? "Правильная формула" : "Коэффициенты по порядку",
          placeholder: t.type === "formula" ? "AlCl3" : "4 3 2",
          value: t.answer, autocapitalize: "none", oninput: (v) => { t.answer = v; validate(); },
        }) : null;

        // ----- Варианты ответа для теста -----
        let correct = new Set(t.type === "choice" ? Chem.parseChoice(t.answer) : []);
        const optionsBox = h("div", { class: "stack-sm" });
        function renderOptions() {
          replace(optionsBox,
            h("div", { class: "small bold" }, "Варианты ответа — отметьте галочкой верные"),
            t.options.map((text, k) => {
              const box = h("input", { type: "checkbox", checked: correct.has(k + 1), "aria-label": `Вариант ${k + 1} верный` });
              box.addEventListener("change", () => {
                if (box.checked) correct.add(k + 1); else correct.delete(k + 1);
                validate();
              });
              const input = h("input", { type: "text", placeholder: `Вариант ${k + 1}`, "aria-label": `Вариант ${k + 1}` });
              input.value = text;
              input.addEventListener("input", () => { t.options[k] = input.value; validate(); });
              const remove = h("button", {
                class: "icon-btn", type: "button", "aria-label": `Удалить вариант ${k + 1}`, title: "Удалить вариант", disabled: t.options.length <= 2,
                onclick: () => {
                  t.options.splice(k, 1);
                  correct = new Set([...correct].filter((n) => n !== k + 1).map((n) => (n > k + 1 ? n - 1 : n)));
                  renderOptions();
                  validate();
                },
              }, icon("delete", 18));
              return h("div", { class: "option-row" }, box, input, remove);
            }),
            t.options.length < 8 && button("+ Вариант", () => { t.options.push(""); renderOptions(); validate(); }, { kind: "text" }));
        }
        if (t.type === "choice") renderOptions();

        // ----- Картинка -----
        const imageBox = h("div", { class: "image-edit" });
        const fileInput = h("input", { type: "file", accept: "image/*", hidden: true });
        fileInput.addEventListener("change", () => {
          const file = fileInput.files && fileInput.files[0];
          if (!file) return;
          if (!file.type.startsWith("image/")) return toast("Это не картинка");
          imageFile = file;
          removeImage = false;
          renderImage();
        });
        let previewUrl = "";
        function renderImage() {
          if (previewUrl) URL.revokeObjectURL(previewUrl);
          previewUrl = imageFile ? URL.createObjectURL(imageFile) : "";
          const src = previewUrl || (!removeImage && t.image ? Api.fileUrl(t, t.image, "800x0") : "");
          replace(imageBox,
            src && h("img", { src, alt: "Картинка к заданию" }),
            button(src ? "Заменить картинку" : "Добавить картинку", () => fileInput.click(), { kind: "outline" }),
            src && button("Убрать", () => { imageFile = null; removeImage = true; renderImage(); }, { kind: "text" }),
            fileInput);
        }
        renderImage();

        /** Задание в том виде, в каком оно сохранится. */
        function current() {
          if (t.type === "fill") {
            const split = Chem.splitBlanks(fillSource);
            return { text: split.text, answer: split.answer, options: [] };
          }
          if (t.type === "choice") {
            return { text: t.text, answer: [...correct].sort((a, b) => a - b).join(","), options: t.options.map((o) => o.trim()) };
          }
          return { text: t.text, answer: t.type === "molar" ? "" : t.answer, options: [] };
        }

        function validate() {
          const c = current();
          const message = Chem.validateTask(t.type, c.text, c.answer, c.options);
          error.textContent = message && touched() ? "⚠️ " + message : "";
          saveBtn.disabled = !!message;
          if (t.type === "formula") answerField.setSupport(t.answer.trim() ? "Получается: " + Chem.pretty(Chem.normalize(t.answer)) : "");
          if (t.type === "equation") {
            const eq = Chem.parseEquation(t.text);
            const coefs = Chem.parseCoefficients(t.answer);
            answerField.setSupport(eq && coefs && coefs.length === eq.substances.length
              ? "Получается: " + Chem.prettyEquation(eq, coefs)
              : "Через пробел, для каждого вещества. 1 тоже пишите.");
          }
          if (t.type === "molar") {
            const m = Chem.molarMass(t.text);
            textField.setSupport(t.text.trim() && m.ok ? `Получается: M(${Chem.pretty(Chem.normalize(t.text))}) = ${Chem.formatNumber(m.value)} г/моль` : "");
          }
          if (t.type === "fill") {
            textField.setSupport(c.text.trim() && !message
              ? "Ученик увидит: " + c.text
              : "Правильные слова пишите в квадратных скобках. Несколько вариантов — через «/»: [водорода/H].");
          }
        }

        /** Большие фото с телефона уменьшаем до 1600 точек по длинной стороне. */
        async function shrink(file) {
          if (file.type === "image/gif" || typeof createImageBitmap !== "function") return file;
          try {
            const bitmap = await createImageBitmap(file);
            const scale = Math.min(1, 1600 / Math.max(bitmap.width, bitmap.height));
            if (scale === 1 && file.size < 1.5 * 1024 * 1024) return file;
            const canvas = document.createElement("canvas");
            canvas.width = Math.round(bitmap.width * scale);
            canvas.height = Math.round(bitmap.height * scale);
            const ctx = canvas.getContext("2d");
            ctx.fillStyle = "#fff";
            ctx.fillRect(0, 0, canvas.width, canvas.height);
            ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
            const blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.85));
            return blob ? new File([blob], "image.jpg", { type: "image/jpeg" }) : file;
          } catch (_) {
            return file;
          }
        }

        async function save() {
          const c = current();
          if (Chem.validateTask(t.type, c.text, c.answer, c.options)) return;
          const data = { ...t, text: c.text, answer: c.answer, options: c.options, multiple: t.type === "choice" && correct.size > 1 };
          // Приводим к единому виду: «Al+O2→Al2O3» станет «Al + O2 = Al2O3», формула без пробелов.
          if (t.type === "question") data.answer = "";
          if (t.type === "formula") data.answer = Chem.normalize(data.answer);
          if (t.type === "molar") data.text = Chem.normalize(data.text);
          if (t.type === "equation") {
            const eq = Chem.parseEquation(data.text);
            data.text = eq.reactants.join(" + ") + " = " + eq.products.join(" + ");
            data.answer = Chem.parseCoefficients(data.answer).join(" ");
          }
          saveBtn.disabled = true;
          replace(saveBtn, h("span", null, "Сохраняем…"));
          try {
            const saved = await Api.saveTask(data);
            if (imageFile) await Api.setTaskImage(saved.id, await shrink(imageFile));
            else if (removeImage && t.image) await Api.setTaskImage(saved.id, null);
            close();
            toast(task.id ? "Задание сохранено" : "Задание добавлено");
            renderTasks();
          } catch (e) {
            error.textContent = e.message;
            saveBtn.disabled = false;
            replace(saveBtn, h("span", null, "Сохранить"));
          }
        }

        // ----- Вариант -----
        const variantBox = h("div", { class: "row-wrap" });
        const renderVariant = () => replace(variantBox,
          [0, 1, 2, 3, 4].map((v) => chip(v === 0 ? "Для всех" : `Вариант ${v}`, (t.variant || 0) === v, () => { t.variant = v; renderVariant(); })));
        renderVariant();

        validate();
        return {
          title: (task.id ? "Задание: " : "Новое задание: ") + info.title.toLowerCase(),
          body: [
            textField,
            answerField,
            t.type === "choice" && optionsBox,
            h("div", { class: "small bold" }, "Картинка (необязательно)"),
            imageBox,
            h("div", { class: "small bold" }, "Кому это задание"),
            variantBox,
            h("div", { class: "tiny muted" }, "Можно сделать до 4 вариантов: каждый ученик получит свой вариант сам, а задания «для всех» решают все."),
            error,
          ],
          actions: [button("Отмена", close, { kind: "text" }), saveBtn],
        };
      }, { wide: true });
    }

    renderTasks();
    return h("div", { class: "screen" },
      topBar(id ? "Редактор практики" : "Новая практика", () => App.back("admin/tasks")),
      h("div", { class: "card stack" },
        field({ label: "Название практики", placeholder: "Практика по химии", value: form.title, oninput: (v) => { form.title = v; } }),
        h("div", { class: "small bold" }, "Предмет"),
        segmented([{ value: "chemistry", label: "⚗️ Химия" }, { value: "biology", label: "🧬 Биология" }], form.subject, (v) => { form.subject = v; }),
        h("div", { class: "small bold" }, "Класс"),
        gradeChips,
        h("div", { class: "small bold" }, "Для каких классов"),
        classChips,
        h("div", { class: "small bold" }, "Срок сдачи (необязательно)"),
        h("div", { class: "row" }, deadlineInput, clearDeadline),
        h("div", { class: "row" },
          h("div", { class: "grow" }, h("div", null, "Контрольная работа"), h("div", { class: "tiny muted" }, "Без мгновенной проверки, с таймером")),
          h("label", { class: "switch" }, examInput, h("span"))),
        examBox,
        h("div", { class: "row" },
          h("div", { class: "grow" }, h("div", null, "Опубликовано"), publishedHint),
          h("label", { class: "switch" }, publishedInput, h("span"))),
        saveBtn),
      tasksBox);
  }

  window.Teacher = { admin, editor, gradeEditor, examRetake };
})();
