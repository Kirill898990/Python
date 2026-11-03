/*
 * Данные сайта: связь с сервером PocketBase, вход, задания, работы, оценки.
 * Повторяет Repository.kt из Android-приложения.
 */
(function () {
  "use strict";

  // ---------- Адрес сервера ----------
  function serverUrl() {
    const configured = (window.APP_CONFIG && window.APP_CONFIG.serverUrl) || "";
    if (configured) return configured.replace(/\/+$/, "");
    if (location.protocol === "http:" || location.protocol === "https:") return location.origin;
    return "http://127.0.0.1:8090"; // сайт открыт файлом с компьютера — ищем сервер на этом же компьютере
  }

  // ---------- Хранилище браузера ----------
  const storage = {
    get(key, fallback) {
      try {
        const raw = localStorage.getItem(key);
        return raw == null ? fallback : JSON.parse(raw);
      } catch (_) {
        return fallback;
      }
    },
    set(key, value) {
      try {
        localStorage.setItem(key, JSON.stringify(value));
      } catch (_) { /* браузер запретил хранилище — работаем без него */ }
    },
    remove(key) {
      try { localStorage.removeItem(key); } catch (_) { /* ignore */ }
    },
  };

  class ApiError extends Error {
    constructor(message, status = 0, fieldErrors = {}) {
      super(message);
      this.status = status;
      this.fieldErrors = fieldErrors;
    }
  }

  // ---------- Запросы к PocketBase ----------
  let token = storage.get("token", null);

  async function request(method, path, { query, body } = {}) {
    const url = new URL(serverUrl() + "/" + path);
    if (query) Object.entries(query).forEach(([k, v]) => v != null && url.searchParams.set(k, v));
    const headers = { "Content-Type": "application/json" };
    if (token) headers.Authorization = token;
    let response;
    try {
      response = await fetch(url, {
        method,
        headers,
        body: body ? JSON.stringify(body) : (method === "POST" || method === "PATCH" ? "{}" : undefined),
      });
    } catch (_) {
      throw new ApiError("Нет связи с сервером. Проверь интернет и попробуй ещё раз.");
    }
    const text = await response.text();
    if (!response.ok) throw parseError(response.status, text, path);
    return text ? JSON.parse(text) : null;
  }

  function fieldMessage(field, code) {
    if (code === "validation_not_unique" && field === "username") return "Этот логин уже занят";
    if (code === "validation_not_unique") return "Такое значение уже есть";
    if (code === "validation_required") return "Заполни это поле";
    if (code === "validation_values_mismatch") return "Пароли не совпадают";
    if (code === "validation_invalid_format" && field === "username") {
      return "Логин: только маленькие латинские буквы, цифры и знаки _ . -";
    }
    if (field === "username") return "Логин: от 3 до 32 символов";
    if (field === "password") return "Пароль: не меньше 6 символов";
    if (field === "name") return "Имя: от 2 до 100 символов";
    return `Проверь поле «${field}»`;
  }

  function parseError(status, text, path) {
    const fields = {};
    try {
      const data = JSON.parse(text).data || {};
      Object.entries(data).forEach(([field, value]) => { fields[field] = fieldMessage(field, value && value.code); });
    } catch (_) { /* не JSON */ }
    const firstField = Object.values(fields)[0];
    let message;
    if (path.endsWith("auth-with-password") && status === 400) message = "Неверный логин или пароль";
    else if (firstField) message = firstField;
    else if (status === 401) message = "Нужно войти в аккаунт заново";
    else if (status === 403) message = "Нет доступа";
    else if (status === 404) message = "Не найдено. Возможно, запись уже удалили.";
    else if (status === 429) message = "Слишком много попыток. Подожди минуту.";
    else if (status >= 500) message = "Ошибка на сервере. Попробуй позже.";
    else message = `Что-то пошло не так (код ${status})`;
    return new ApiError(message, status, fields);
  }

  async function listAll(collection, { filter, sort, expand } = {}) {
    const items = [];
    for (let page = 1; ; page++) {
      const r = await request("GET", `api/collections/${collection}/records`, {
        query: { page, perPage: 200, filter, sort, expand },
      });
      items.push(...r.items);
      if (page >= r.totalPages) break;
    }
    return items;
  }

  // ---------- Состояние сайта ----------
  const listeners = new Set();
  const state = {
    user: storage.get("user", null),
    content: storage.get("content", { practices: [], tasks: [] }),
    contentError: null,
  };

  function emit(what) {
    listeners.forEach((fn) => fn(what));
  }

  /** Если сервер сказал, что вход устарел, выходим из аккаунта. */
  async function guard(promise) {
    try {
      return await promise;
    } catch (e) {
      if (e instanceof ApiError && e.status === 401 && token) logout();
      throw e;
    }
  }

  function saveSession(response) {
    token = response.token;
    state.user = response.record;
    storage.set("token", token);
    storage.set("user", state.user);
    emit("user");
  }

  function logout() {
    token = null;
    state.user = null;
    state.content = { practices: [], tasks: [] };
    storage.remove("token");
    storage.remove("user");
    storage.remove("content");
    emit("user");
  }

  const normalizeUsername = (v) => String(v).trim().toLowerCase();
  /** id записей PocketBase — только латинские буквы и цифры. Всё остальное отбрасываем. */
  const safeId = (id) => String(id).replace(/[^a-z0-9]/gi, "");

  // ---------- Прогресс ученика (только в этом браузере) ----------
  function progressKey() {
    return "progress_" + (state.user ? state.user.id : "guest");
  }
  const progress = {
    load() {
      return storage.get(progressKey(), { solved: [], drafts: {} });
    },
    solved() {
      return new Set(this.load().solved);
    },
    markSolved(taskId) {
      const p = this.load();
      if (!p.solved.includes(taskId)) {
        p.solved.push(taskId);
        storage.set(progressKey(), p);
      }
    },
    draft(taskId) {
      return this.load().drafts[taskId] || "";
    },
    saveDraft(taskId, text) {
      const p = this.load();
      p.drafts[taskId] = text;
      storage.set(progressKey(), p);
    },
    reset(taskIds) {
      const p = this.load();
      p.solved = p.solved.filter((id) => !taskIds.includes(id));
      taskIds.forEach((id) => delete p.drafts[id]);
      storage.set(progressKey(), p);
    },
  };

  // ---------- Помощники для заданий ----------
  const content = {
    practice(id) {
      return state.content.practices.find((p) => p.id === id) || null;
    },
    tasksOf(practiceId) {
      return state.content.tasks
        .filter((t) => t.practice === practiceId)
        .sort((a, b) => (a.position - b.position) || a.id.localeCompare(b.id));
    },
    practicesOf(subject) {
      return state.content.practices
        .filter((p) => p.subject === subject)
        .sort((a, b) => (a.grade - b.grade) || (a.position - b.position) || a.title.localeCompare(b.title));
    },
    checkableTaskIds() {
      return state.content.tasks.filter(isCheckable).map((t) => t.id);
    },
  };

  function isCheckable(task) {
    return task.type === "formula" || task.type === "equation";
  }

  // ---------- Публичный интерфейс ----------
  window.Api = {
    ApiError,
    state,
    storage,
    progress,
    content,
    isCheckable,
    onChange(fn) { listeners.add(fn); return () => listeners.delete(fn); },

    async login(username, password) {
      const r = await request("POST", "api/collections/users/auth-with-password", {
        body: { identity: normalizeUsername(username), password },
      });
      saveSession(r);
      await this.refreshContent();
    },

    async register(name, group, username, password, passwordConfirm) {
      await request("POST", "api/collections/users/records", {
        body: {
          name: String(name).trim(),
          group: String(group).trim().toUpperCase(),
          username: normalizeUsername(username),
          password,
          passwordConfirm,
        },
      });
      await this.login(username, password);
    },

    /** Продлевает вход и обновляет профиль (например, если учителю дали права). */
    async refreshSession() {
      if (!token) return;
      try {
        saveSession(await request("POST", "api/collections/users/auth-refresh"));
      } catch (e) {
        if (e.status === 401 || e.status === 404) logout();
      }
    },

    logout,

    async refreshContent() {
      try {
        const [practices, tasks] = await guard(Promise.all([
          listAll("practices", { sort: "grade,position,title" }),
          listAll("tasks", { sort: "position" }),
        ]));
        state.content = { practices, tasks };
        state.contentError = null;
        storage.set("content", state.content);
        emit("content");
        return true;
      } catch (e) {
        state.contentError = e.message;
        emit("content");
        return false;
      }
    },

    submit(practice, answers, solved, total) {
      return guard(request("POST", "api/collections/submissions/records", {
        body: {
          student: state.user.id,
          practice: practice.id,
          practiceTitle: fullTitle(practice),
          answers,
          solved,
          total,
        },
      }));
    },

    mySubmissions(practiceId) {
      if (!state.user) return Promise.resolve([]);
      let filter = `student = "${state.user.id}"`;
      if (practiceId) filter += ` && practice = "${safeId(practiceId)}"`;
      return guard(listAll("submissions", { filter, sort: "-created" }));
    },

    submission(id) {
      return guard(request("GET", `api/collections/submissions/records/${safeId(id)}`, { query: { expand: "student" } }));
    },

    // ----- Учитель -----
    allSubmissions(onlyUnchecked) {
      return guard(listAll("submissions", { filter: onlyUnchecked ? "grade = 0" : undefined, sort: "-created", expand: "student" }));
    },

    grade(id, grade, comment) {
      return guard(request("PATCH", `api/collections/submissions/records/${safeId(id)}`, {
        query: { expand: "student" },
        body: { grade, comment: String(comment).trim(), gradedAt: new Date().toISOString() },
      }));
    },

    async savePractice(p) {
      const body = { title: p.title.trim(), subject: p.subject, grade: p.grade, position: p.position || 0, published: !!p.published };
      const saved = await guard(p.id
        ? request("PATCH", `api/collections/practices/records/${safeId(p.id)}`, { body })
        : request("POST", "api/collections/practices/records", { body }));
      state.content.practices = state.content.practices.filter((x) => x.id !== saved.id).concat(saved);
      storage.set("content", state.content);
      return saved;
    },

    async deletePractice(id) {
      await guard(request("DELETE", `api/collections/practices/records/${safeId(id)}`));
      state.content.practices = state.content.practices.filter((x) => x.id !== id);
      state.content.tasks = state.content.tasks.filter((t) => t.practice !== id);
      storage.set("content", state.content);
    },

    async saveTask(t) {
      const body = {
        practice: t.practice, type: t.type, position: t.position || 0,
        text: String(t.text).trim(), answer: String(t.answer || "").trim(), hint: String(t.hint || "").trim(),
      };
      const saved = await guard(t.id
        ? request("PATCH", `api/collections/tasks/records/${safeId(t.id)}`, { body })
        : request("POST", "api/collections/tasks/records", { body }));
      state.content.tasks = state.content.tasks.filter((x) => x.id !== saved.id).concat(saved);
      storage.set("content", state.content);
      return saved;
    },

    async deleteTask(id) {
      await guard(request("DELETE", `api/collections/tasks/records/${safeId(id)}`));
      state.content.tasks = state.content.tasks.filter((t) => t.id !== id);
      storage.set("content", state.content);
    },

    students() {
      return guard(listAll("users", { filter: "isTeacher = false", sort: "group,name" }));
    },

    resetPassword(userId, password) {
      return guard(request("PATCH", `api/collections/users/records/${safeId(userId)}`, { body: { password, passwordConfirm: password } }));
    },
  };

  // ---------- Общие функции ----------
  const SUBJECTS = {
    chemistry: { key: "chemistry", title: "Химия", emoji: "⚗️" },
    biology: { key: "biology", title: "Биология", emoji: "🧬" },
  };

  function subjectOf(key) {
    return SUBJECTS[key] || SUBJECTS.chemistry;
  }

  /** «Химия, 8 класс · Практика по химии» */
  function fullTitle(p) {
    return `${subjectOf(p.subject).title}, ${p.grade} класс · ${p.title}`;
  }

  /** «2026-11-03 16:44:58.123Z» → «3 ноября, 19:44» */
  function formatDate(value) {
    if (!value) return "";
    const d = new Date(String(value).trim().replace(" ", "T"));
    if (isNaN(d)) return value;
    const date = d.toLocaleDateString("ru-RU", { day: "numeric", month: "long" });
    const time = d.toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" });
    return `${date}, ${time}`;
  }

  /** «3 задания», «5 заданий», «1 задание» */
  function tasksWord(n) {
    const m100 = n % 100;
    const m10 = n % 10;
    if (m100 >= 11 && m100 <= 14) return "заданий";
    if (m10 === 1) return "задание";
    if (m10 >= 2 && m10 <= 4) return "задания";
    return "заданий";
  }

  // Постоянное содержимое: ссылки на теорию и классы, которые показываются всегда.
  const THEORY = [
    { subject: "chemistry", grade: 9, url: "https://www.yaklass.ru/p/himija/9-klass" },
    { subject: "chemistry", grade: 8, url: "https://www.yaklass.ru/p/himija/8-klass?YklShowAll=1" },
    { subject: "biology", grade: 9, url: "https://www.yaklass.ru/p/biologia/9-klass?YklShowAll=1" },
    { subject: "biology", grade: 8, url: "https://www.yaklass.ru/p/biologia/8-klass?YklShowAll=1" },
    { subject: "biology", grade: 7, url: "https://www.yaklass.ru/p/biologia/7-klass?YklShowAll=1" },
    { subject: "biology", grade: 6, url: "https://www.yaklass.ru/p/biologia/6-klass?YklShowAll=1" },
    { subject: "biology", grade: 5, url: "https://www.yaklass.ru/p/biologia/5-klass?YklShowAll=1" },
  ];
  const BASE_GRADES = { chemistry: [8, 9, 10], biology: [5, 6, 7, 8, 9] };

  function grades(subject) {
    const all = BASE_GRADES[subject].concat(content.practicesOf(subject).map((p) => p.grade));
    return [...new Set(all)].sort((a, b) => b - a);
  }

  function range(numbers) {
    const min = Math.min(...numbers);
    const max = Math.max(...numbers);
    return min === max ? String(min) : `${min}–${max}`;
  }

  window.Content = {
    SUBJECTS,
    subjectOf,
    fullTitle,
    formatDate,
    tasksWord,
    THEORY,
    FEEDBACK_URL: "https://forms.yandex.ru/u/6ad4f70a4a052d113bebc2a1",
    AUTHOR: "Простая химия с Ириной",
    theoryUrl: (subject, grade) => (THEORY.find((t) => t.subject === subject && t.grade === grade) || {}).url || null,
    grades,
    gradeRange: (subject) => range(grades(subject)),
    allGradesRange: () => range(grades("chemistry").concat(grades("biology"))),
  };
})();
