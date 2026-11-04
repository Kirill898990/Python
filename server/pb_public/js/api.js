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

  /** body — объект (отправится как JSON) или FormData (для загрузки картинок). */
  async function request(method, path, { query, body } = {}) {
    const url = new URL(serverUrl() + "/" + path);
    if (query) Object.entries(query).forEach(([k, v]) => v != null && url.searchParams.set(k, v));
    const isForm = typeof FormData !== "undefined" && body instanceof FormData;
    const headers = isForm ? {} : { "Content-Type": "application/json" };
    if (token) headers.Authorization = token;
    let response;
    try {
      response = await fetch(url, {
        method,
        headers,
        body: isForm ? body : body ? JSON.stringify(body) : (method === "POST" || method === "PATCH" ? "{}" : undefined),
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
    if (code === "class_code_required") return "Впиши код класса, который дал учитель";
    if (code === "class_code_invalid") return "Неверный код класса. Проверь его у учителя";
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
  /**
   * Ученик видит только практики для своего класса (пустой список классов — практика для всех).
   * Учитель видит всё.
   */
  function forUser(data, user) {
    const notes = data.notes || [];
    const variants = data.variants || {};
    if (!user || user.isTeacher) return { practices: data.practices, tasks: data.tasks, notes, variants };
    const practices = data.practices.filter((p) => !Array.isArray(p.classes) || !p.classes.length || p.classes.includes(user.group));
    const ids = new Set(practices.map((p) => p.id));
    return { practices, tasks: data.tasks.filter((t) => ids.has(t.practice)), notes, variants };
  }

  const state = {
    user: storage.get("user", null),
    content: forUser(storage.get("content", { practices: [], tasks: [] }), storage.get("user", null)),
    contentError: null,
    /** Сданные работы ученика (новые сверху): по ним считается прогресс на главной и в списке практик. */
    mySubmissions: [],
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
    realtime.close();
    realtime.handlers.clear();
    token = null;
    state.user = null;
    state.content = { practices: [], tasks: [] };
    state.mySubmissions = [];
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
      const p = storage.get(progressKey(), {});
      return { solved: p.solved || [], drafts: p.drafts || {}, attempts: p.attempts || {} };
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
      taskIds.forEach((id) => { delete p.drafts[id]; delete p.attempts[id]; });
      storage.set(progressKey(), p);
    },
  };

  // ---------- Чат ----------
  /** Строка для фильтра PocketBase в кавычках, как pb.filter() в официальной библиотеке. */
  const quote = (v) => "'" + String(v).replace(/\\/g, "\\\\").replace(/'/g, "\\'") + "'";

  /** Комната чата: {kind: "class", group} или {kind: "private", student}. */
  function roomKey(room) {
    return room.kind === "class" ? "class:" + room.group : "private:" + room.student;
  }
  function roomFilter(room) {
    return room.kind === "class"
      ? `kind = "class" && group = ${quote(room.group)}`
      : `kind = "private" && student = "${safeId(room.student)}"`;
  }
  function inRoom(message, room) {
    return message.kind === room.kind &&
      (room.kind === "class" ? message.group === room.group : message.student === room.student);
  }

  /** Когда пользователь последний раз открывал каждый чат (только в этом браузере). */
  const chatSeen = {
    key: () => "chatSeen_" + (state.user ? state.user.id : "guest"),
    get(room) { return storage.get(this.key(), {})[roomKey(room)] || ""; },
    mark(room, created) {
      const all = storage.get(this.key(), {});
      if (created && created > (all[roomKey(room)] || "")) {
        all[roomKey(room)] = created;
        storage.set(this.key(), all);
      }
    },
  };

  /**
   * Новые сообщения без перезагрузки страницы (realtime PocketBase через EventSource).
   * Сервер сам проверяет права: ученику приходят только сообщения, которые ему можно видеть.
   */
  const realtime = {
    source: null,
    handlers: new Set(),
    subscribe(fn) {
      this.handlers.add(fn);
      if (!this.source && typeof EventSource !== "undefined") this.connect();
      return () => {
        this.handlers.delete(fn);
        if (!this.handlers.size) this.close();
      };
    },
    connect() {
      const source = new EventSource(serverUrl() + "/api/realtime");
      this.source = source;
      source.addEventListener("PB_CONNECT", (e) => {
        let clientId = "";
        try { clientId = JSON.parse(e.data).clientId; } catch (_) { return; }
        request("POST", "api/realtime", { body: { clientId, subscriptions: ["messages"] } })
          .then(() => this.handlers.forEach((fn) => fn({ action: "connected" })), () => {});
      });
      source.addEventListener("messages", (e) => {
        let data;
        try { data = JSON.parse(e.data); } catch (_) { return; }
        this.handlers.forEach((fn) => fn(data));
      });
    },
    close() {
      if (this.source) this.source.close();
      this.source = null;
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
    /** Сколько вариантов в практике (0 — вариантов нет, все решают одно и то же). */
    variantCount(practiceId) {
      return Math.max(0, ...this.tasksOf(practiceId).map((t) => t.variant || 0));
    },
    /**
     * Вариант текущего ученика в практике. Варианты раздаются по очереди внутри класса в порядке
     * регистрации (1, 2, 1, 2…) — это считает сервер (server/pb_hooks, /api/variants).
     */
    variantFor(practiceId) {
      const count = this.variantCount(practiceId);
      if (!count) return 0;
      const v = (state.content.variants || {})[practiceId];
      return v >= 1 && v <= count ? v : 1;
    },
    /** Для учителя: вариант каждого ученика по тому же правилу, что на сервере. id ученика → вариант. */
    variantsOf(practiceId, students) {
      const count = this.variantCount(practiceId);
      const result = new Map();
      if (!count) return result;
      const byGroup = new Map();
      students.slice()
        .sort((a, b) => (a.created < b.created ? -1 : a.created > b.created ? 1 : a.id < b.id ? -1 : a.id > b.id ? 1 : 0))
        .forEach((st) => {
          if (!byGroup.has(st.group)) byGroup.set(st.group, []);
          byGroup.get(st.group).push(st);
        });
      byGroup.forEach((list) => list.forEach((st, i) => result.set(st.id, (i % count) + 1)));
      return result;
    },
    /** Задания, которые видит ученик: общие и своего варианта. */
    tasksForVariant(practiceId, variant) {
      return this.tasksOf(practiceId).filter((t) => !t.variant || t.variant === variant);
    },
    /** Задания практики для текущего пользователя (учитель видит первый вариант). */
    myTasks(practiceId) {
      const u = state.user;
      const variant = u && !u.isTeacher ? this.variantFor(practiceId) : Math.min(1, this.variantCount(practiceId));
      return this.tasksForVariant(practiceId, variant);
    },
    noteFor(subject, grade) {
      return (state.content.notes || []).find((n) => n.subject === subject && n.grade === grade) || null;
    },
    practicesOf(subject) {
      return state.content.practices
        .filter((p) => p.subject === subject)
        .sort((a, b) => (a.grade - b.grade) || (a.position - b.position) || a.title.localeCompare(b.title));
    },
  };

  /** Задания, которые проверяются автоматически (на сервере). */
  function isCheckable(task) {
    return Chem.isCheckableType(task.type);
  }

  /** Адрес картинки задания. thumb — уменьшенная копия, например «800x0». */
  function fileUrl(record, filename, thumb) {
    if (!record || !filename) return "";
    const url = `${serverUrl()}/api/files/${record.collectionName || "tasks"}/${record.id}/${encodeURIComponent(filename)}`;
    return thumb ? `${url}?thumb=${thumb}` : url;
  }

  // ---------- Публичный интерфейс ----------
  window.Api = {
    ApiError,
    state,
    storage,
    progress,
    content,
    isCheckable,
    fileUrl,

    /**
     * Проверить ответы на сервере (правильных ответов у ученика нет).
     * items: [{task, input}] → [{task, kind: correct|hint|wrong|error, message, blanks, attempts}]
     */
    async check(items) {
      const r = await guard(request("POST", "api/check", { body: { items } }));
      return r.results || [];
    },
    onChange(fn) { listeners.add(fn); return () => listeners.delete(fn); },

    async login(username, password) {
      const r = await request("POST", "api/collections/users/auth-with-password", {
        body: { identity: normalizeUsername(username), password },
      });
      saveSession(r);
      await this.refreshContent();
      await this.refreshMySubmissions();
    },

    /** Нужен ли код класса для регистрации (его включает учитель). */
    async registrationInfo() {
      try {
        return await request("GET", "api/registration-info");
      } catch (_) {
        return { codeRequired: false };
      }
    },

    async register(name, group, username, password, passwordConfirm, classCode) {
      await request("POST", "api/collections/users/records", {
        body: {
          name: String(name).trim(),
          group: String(group || "").trim().toUpperCase(),
          classCode: String(classCode || "").trim().toUpperCase(),
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
        const isStudent = state.user && !state.user.isTeacher;
        const [practices, tasks, notes, variants] = await guard(Promise.all([
          listAll("practices", { sort: "grade,position,title" }),
          listAll("tasks", { sort: "position" }),
          listAll("notes", { sort: "subject,grade" }).catch(() => []),
          // Варианты ученика считает сервер (ученик не видит список одноклассников).
          isStudent ? request("GET", "api/variants").then((r) => r.variants || {}, () => ({})) : Promise.resolve({}),
        ]));
        state.content = forUser({ practices, tasks, notes, variants }, state.user);
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

    async submit(practice, answers, solved, total, variant) {
      const saved = await guard(request("POST", "api/collections/submissions/records", {
        body: {
          variant: variant || 0,
          student: state.user.id,
          practice: practice.id,
          practiceTitle: fullTitle(practice),
          answers,
          solved,
          total,
        },
      }));
      state.mySubmissions = [saved].concat(state.mySubmissions);
      emit("submissions");
      return saved;
    },

    /** Обновляет список сданных работ ученика (для прогресса). Ошибки не страшны — останется старый список. */
    async refreshMySubmissions() {
      if (!state.user || state.user.isTeacher) return;
      try {
        state.mySubmissions = await this.mySubmissions();
        emit("submissions");
      } catch (_) { /* нет связи — покажем, что было */ }
    },

    /** Последняя сданная работа по практике или null. */
    lastSubmission(practiceId) {
      return state.mySubmissions.find((s) => s.practice === practiceId) || null;
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
      const body = {
        title: p.title.trim(), subject: p.subject, grade: p.grade, position: p.position || 0, published: !!p.published,
        classes: Array.isArray(p.classes) ? p.classes : [], deadline: p.deadline || "",
      };
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
      // Правильный ответ скрыт от учеников, поэтому сервер принимает его в поле answerText.
      const body = {
        practice: t.practice, type: t.type, position: t.position || 0,
        text: String(t.text).trim(), answerText: String(t.answer || "").trim(),
        options: Array.isArray(t.options) ? t.options : [], multiple: !!t.multiple,
        variant: t.variant || 0,
      };
      const saved = await guard(t.id
        ? request("PATCH", `api/collections/tasks/records/${safeId(t.id)}`, { body })
        : request("POST", "api/collections/tasks/records", { body }));
      state.content.tasks = state.content.tasks.filter((x) => x.id !== saved.id).concat(saved);
      storage.set("content", state.content);
      return saved;
    },

    /** Загрузить или убрать (file = null) картинку задания. */
    async setTaskImage(id, file) {
      let saved;
      if (file) {
        const form = new FormData();
        form.append("image", file, file.name || "image.jpg");
        saved = await guard(request("PATCH", `api/collections/tasks/records/${safeId(id)}`, { body: form }));
      } else {
        saved = await guard(request("PATCH", `api/collections/tasks/records/${safeId(id)}`, { body: { image: null } }));
      }
      state.content.tasks = state.content.tasks.filter((x) => x.id !== saved.id).concat(saved);
      storage.set("content", state.content);
      return saved;
    },

    /** Сохранить заметку учителя к теории (пустой текст — удалить заметку). */
    async saveNote(subject, grade, text) {
      const existing = content.noteFor(subject, grade);
      const clean = String(text || "").trim();
      const notes = (state.content.notes || []).filter((n) => !(n.subject === subject && n.grade === grade));
      if (!clean) {
        if (existing) await guard(request("DELETE", `api/collections/notes/records/${safeId(existing.id)}`));
      } else {
        const saved = await guard(existing
          ? request("PATCH", `api/collections/notes/records/${safeId(existing.id)}`, { body: { text: clean } })
          : request("POST", "api/collections/notes/records", { body: { subject, grade, text: clean } }));
        notes.push(saved);
      }
      state.content.notes = notes;
      storage.set("content", state.content);
    },

    async deleteTask(id) {
      await guard(request("DELETE", `api/collections/tasks/records/${safeId(id)}`));
      state.content.tasks = state.content.tasks.filter((t) => t.id !== id);
      storage.set("content", state.content);
    },

    students() {
      return guard(listAll("users", { filter: "isTeacher = false", sort: "group,name" }));
    },

    // ----- Чат -----
    roomKey,
    inRoom,
    chatSeen,
    realtime,

    /** Последние сообщения комнаты (до limit штук), старые сверху. hasMore — есть ли ещё более ранние. */
    async messages(room, limit = 50) {
      const r = await guard(request("GET", "api/collections/messages/records", {
        query: { filter: roomFilter(room), sort: "-created,-id", perPage: limit, page: 1 },
      }));
      return { items: r.items.reverse(), hasMore: r.totalItems > r.items.length };
    },

    sendMessage(room, text) {
      const u = state.user;
      return guard(request("POST", "api/collections/messages/records", {
        body: {
          author: u.id, authorName: u.name, fromTeacher: !!u.isTeacher, kind: room.kind,
          group: room.kind === "class" ? room.group : "",
          student: room.kind === "private" ? room.student : "",
          text: String(text).trim(),
        },
      }));
    },

    deleteMessage(id) {
      return guard(request("DELETE", `api/collections/messages/records/${safeId(id)}`));
    },

    /**
     * Список чатов с последним сообщением.
     * Ученик: чат своего класса и переписка с учителем. Учитель: все классы и все личные переписки.
     */
    async chatRooms() {
      const u = state.user;
      const last = async (room) => {
        const r = await guard(request("GET", "api/collections/messages/records", {
          query: { filter: roomFilter(room), sort: "-created,-id", perPage: 1, skipTotal: 1 },
        }));
        return r.items[0] || null;
      };
      const withState = (room) => {
        const seen = chatSeen.get(room);
        room.unread = !!room.last && room.last.author !== u.id && room.last.created > seen;
        return room;
      };
      if (!u.isTeacher) {
        const rooms = [];
        if (u.group) rooms.push({ kind: "class", group: u.group, title: `Чат класса ${u.group}` });
        rooms.push({ kind: "private", student: u.id, title: "Учитель", subtitle: "Личные сообщения" });
        await Promise.all(rooms.map(async (room) => { room.last = await last(room); }));
        return rooms.map(withState);
      }
      const [students, privateMessages] = await Promise.all([
        this.students(),
        guard(request("GET", "api/collections/messages/records", {
          query: { filter: 'kind = "private"', sort: "-created,-id", perPage: 500, skipTotal: 1 },
        })).then((r) => r.items),
      ]);
      const groups = [...new Set(students.map((s) => s.group).filter(Boolean))].sort((a, b) => a.localeCompare(b, "ru", { numeric: true }));
      const classes = groups.map((group) => ({ kind: "class", group, title: `${group} класс` }));
      await Promise.all(classes.map(async (room) => { room.last = await last(room); }));
      const byId = new Map(students.map((s) => [s.id, s]));
      const threads = [];
      const seenStudents = new Set();
      privateMessages.forEach((m) => {
        if (seenStudents.has(m.student)) return;
        seenStudents.add(m.student);
        const st = byId.get(m.student);
        threads.push({
          kind: "private", student: m.student, last: m,
          title: st ? st.name : "Ученик", subtitle: st && st.group ? `${st.group} класс` : "",
        });
      });
      return classes.concat(threads).map(withState);
    },

    /** Сколько чатов с новыми сообщениями (для значка на главной). */
    async unreadChats() {
      try {
        return (await this.chatRooms()).filter((r) => r.unread).length;
      } catch (_) {
        return 0;
      }
    },

    // ----- Коды классов -----
    classCodes() {
      return guard(listAll("class_codes", { sort: "group,created" }));
    },

    /** Новый код для класса: 6 букв и цифр, без похожих друг на друга (0 и O, 1 и I). */
    createClassCode(group) {
      const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
      const random = new Uint32Array(6);
      crypto.getRandomValues(random);
      const code = Array.from(random, (n) => alphabet[n % alphabet.length]).join("");
      return guard(request("POST", "api/collections/class_codes/records", {
        body: { group: String(group).trim().toUpperCase(), code, active: true },
      }));
    },

    setClassCodeActive(id, active) {
      return guard(request("PATCH", `api/collections/class_codes/records/${safeId(id)}`, { body: { active: !!active } }));
    },

    deleteClassCode(id) {
      return guard(request("DELETE", `api/collections/class_codes/records/${safeId(id)}`));
    },

    /** Всё для журнала: ученики и все сданные работы. */
    async journal() {
      const [students, submissions] = await Promise.all([this.students(), this.allSubmissions(false)]);
      return { students, submissions };
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

  /** «1 практики», «3 практик»: для фразы «Сдано 2 из N …» */
  function practicesWord(n) {
    return n % 10 === 1 && n % 100 !== 11 ? "практики" : "практик";
  }

  /** «9А» → 9; если класс записан без цифры — 0 (неизвестно). */
  function gradeOf(group) {
    const n = parseInt(String(group || ""), 10);
    return n >= 1 && n <= 11 ? n : 0;
  }

  /**
   * Задана ли практика этому классу (для прогресса, сроков и журнала).
   * Если учитель выбрал классы — только им. Если практика «для всех классов» — ученикам своей параллели:
   * биология 5 класса засчитывается 5А и 5Б, но не девятиклассникам.
   */
  function isAssigned(practice, group) {
    const classes = Array.isArray(practice.classes) ? practice.classes : [];
    if (classes.length) return classes.includes(group);
    const grade = gradeOf(group);
    return !grade || practice.grade === grade;
  }

  /** Срок сдачи: Date или null. */
  function deadlineOf(practice) {
    if (!practice || !practice.deadline) return null;
    const d = new Date(String(practice.deadline).trim().replace(" ", "T"));
    return isNaN(d) ? null : d;
  }

  /** «12 ноября» */
  function formatDay(d) {
    return d.toLocaleDateString("ru-RU", { day: "numeric", month: "long" });
  }

  /**
   * Что написать про срок у практики для ученика: {text, overdue} или null.
   * Если работа уже сдана, срок не показываем.
   */
  function deadlineStatus(practice, submitted) {
    const d = deadlineOf(practice);
    if (!d || submitted) return null;
    return d < new Date() ? { text: `Срок прошёл ${formatDay(d)}`, overdue: true } : { text: `Сдать до ${formatDay(d)}`, overdue: false };
  }

  /** Сдана ли работа позже срока. */
  function isLate(submission, practice) {
    const d = deadlineOf(practice);
    return !!d && new Date(String(submission.created).trim().replace(" ", "T")) > d;
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
    { subject: "chemistry", grade: 11, url: "https://www.yaklass.ru/p/himija/11-klass?YklShowAll=1" },
    { subject: "chemistry", grade: 10, url: "https://www.yaklass.ru/p/himija/10-klass" },
    { subject: "chemistry", grade: 9, url: "https://www.yaklass.ru/p/himija/9-klass" },
    { subject: "chemistry", grade: 8, url: "https://www.yaklass.ru/p/himija/8-klass?YklShowAll=1" },
    { subject: "biology", grade: 11, url: "https://www.yaklass.ru/p/biologia/11-klass?YklShowAll=1" },
    { subject: "biology", grade: 10, url: "https://www.yaklass.ru/p/biologia/10-klass" },
    { subject: "biology", grade: 9, url: "https://www.yaklass.ru/p/biologia/9-klass?YklShowAll=1" },
    { subject: "biology", grade: 8, url: "https://www.yaklass.ru/p/biologia/8-klass?YklShowAll=1" },
    { subject: "biology", grade: 7, url: "https://www.yaklass.ru/p/biologia/7-klass?YklShowAll=1" },
    { subject: "biology", grade: 6, url: "https://www.yaklass.ru/p/biologia/6-klass?YklShowAll=1" },
    { subject: "biology", grade: 5, url: "https://www.yaklass.ru/p/biologia/5-klass?YklShowAll=1" },
  ];
  const BASE_GRADES = { chemistry: [8, 9, 10, 11], biology: [5, 6, 7, 8, 9, 10, 11] };

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
    practicesWord,
    gradeOf,
    isAssigned,
    deadlineOf,
    deadlineStatus,
    formatDay,
    isLate,
    THEORY,
    FEEDBACK_URL: "https://forms.yandex.ru/u/6ad4f70a4a052d113bebc2a1",
    AUTHOR: "Простая химия с Ириной",
    theoryUrl: (subject, grade) => (THEORY.find((t) => t.subject === subject && t.grade === grade) || {}).url || null,
    grades,
    gradeRange: (subject) => range(grades(subject)),
    allGradesRange: () => range(grades("chemistry").concat(grades("biology"))),
  };
})();
