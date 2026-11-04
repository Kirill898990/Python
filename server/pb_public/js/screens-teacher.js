/*
 * Экраны учителя: проверка работ и оценки, редактор практик и заданий, список учеников.
 * Сервер сам не пустит сюда ученика (правила в server/pb_migrations), а сайт ещё и прячет эти экраны.
 */
(function () {
  "use strict";
  const { h, replace, icon, topBar, sectionLabel, button, field, segmented, chip, emptyBox, dialog, confirm, toast } = UI;
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
    const tab = ["works", "tasks", "students"].includes(params.tab) ? params.tab : "works";
    const tabs = h("nav", { class: "tabs" },
      [["works", "Работы"], ["tasks", "Задания"], ["students", "Ученики"]].map(([key, title]) =>
        h("a", { class: "tab" + (key === tab ? " selected" : ""), href: `#/admin/${key}`, "aria-current": key === tab ? "page" : null }, title)));
    const body = tab === "works" ? works() : tab === "tasks" ? practices() : students();
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
            h("div", { class: "card-sub" }, `Заданий: ${content.tasksOf(p.id).length}`)),
          icon("chevron"))),
      ];
    });
    return h("div", { class: "stack" },
      h("p", { class: "muted small" }, "Здесь можно добавлять и менять практики и задания. Практика с меткой «Черновик» видна только вам, пока вы её не опубликуете."),
      list,
      h("div", { class: "fab-space" }),
      h("button", { class: "fab", onclick: () => App.go("admin/practice/new") }, "+ Новая практика"));
  }

  // ----- Ученики -----
  function students() {
    return Screens.asyncBlock(() => Api.students(), (items) => {
      if (!items.length) return emptyBox("👥", "Пока никто из учеников не зарегистрировался");
      return [
        h("p", { class: "muted small" }, `Учеников: ${items.length}. Нажмите на ученика, чтобы сбросить ему пароль, или на 💬, чтобы написать ему.`),
        items.map((st) => h("div", { class: "card student-row" },
          h("button", { class: "student-main", type: "button", onclick: () => resetPassword(st) },
            h("div", { class: "card-title" }, st.name),
            h("div", { class: "card-sub" }, [st.group && `${st.group} класс`, `логин ${st.username}`].filter(Boolean).join(" · "))),
          h("button", {
            class: "icon-btn", type: "button", "aria-label": `Написать: ${st.name}`, title: "Написать ученику",
            onclick: () => Chat.open({ kind: "private", student: st.id }, st.group ? `${st.name}, ${st.group}` : st.name),
          }, icon("chat")))),
      ];
    });
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
    };
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
          h("div", { class: "task-label" }, `${index + 1}. ${info.emoji} ${info.title}`),
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

        validate();
        return {
          title: (task.id ? "Задание: " : "Новое задание: ") + info.title.toLowerCase(),
          body: [
            textField,
            answerField,
            t.type === "choice" && optionsBox,
            h("div", { class: "small bold" }, "Картинка (необязательно)"),
            imageBox,
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
        h("div", { class: "row" },
          h("div", { class: "grow" }, h("div", null, "Опубликовано"), publishedHint),
          h("label", { class: "switch" }, publishedInput, h("span"))),
        saveBtn),
      tasksBox);
  }

  window.Teacher = { admin, editor, gradeEditor };
})();
