/*
 * Чат: общий чат класса и личная переписка ученика с учителем.
 * Ученик видит чат своего класса и свою переписку с учителем; учитель — все классы и все переписки
 * и может удалять сообщения. Права проверяет сервер (server/pb_migrations/1762100000_chat.js).
 */
(function () {
  "use strict";
  const { h, replace, icon, topBar, emojiBadge, sectionLabel, emptyBox, confirm, toast } = UI;

  /** Заголовки комнат из последнего списка чатов: чтобы сразу показать имя ученика в шапке. */
  const titles = new Map();

  const toDate = (value) => new Date(String(value || "").trim().replace(" ", "T"));
  const timeOf = (value) => toDate(value).toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" });

  function dayOf(value) {
    const d = toDate(value);
    const today = new Date();
    const yesterday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1);
    if (d.toDateString() === today.toDateString()) return "Сегодня";
    if (d.toDateString() === yesterday.toDateString()) return "Вчера";
    return d.toLocaleDateString("ru-RU", { day: "numeric", month: "long" });
  }

  /** «14:05» сегодня, «вчера», «3 нояб.» раньше — для списка чатов. */
  function shortWhen(value) {
    const day = dayOf(value);
    if (day === "Сегодня") return timeOf(value);
    if (day === "Вчера") return "вчера";
    return toDate(value).toLocaleDateString("ru-RU", { day: "numeric", month: "short" });
  }

  function roomPath(room) {
    if (room.kind === "course") return `chat/course/${room.enrollment}`;
    return room.kind === "class"
      ? `chat/class/${encodeURIComponent(room.group)}`
      : `chat/private/${room.student}`;
  }

  // ======================= Список чатов =======================
  function list() {
    const user = Api.state.user;
    const roomCard = (room) => {
      titles.set(Api.roomKey(room), room.title);
      const last = room.last;
      const preview = last
        ? (last.author === user.id ? "Вы: " : room.kind === "class" ? last.authorName + ": " : "") + last.text
        : "Сообщений пока нет";
      const badge = room.kind === "class"
        ? emojiBadge("👥", "c-chemistry")
        : user.isTeacher
          ? h("div", { class: "avatar chat-avatar" }, (room.title || "?")[0].toUpperCase())
          : emojiBadge("👩‍🏫", "c-secondary");
      return h("a", { class: "card card-link chat-room" + (room.unread ? " unread" : ""), href: "#/" + roomPath(room) },
        badge,
        h("div", { class: "grow" },
          h("div", { class: "row" },
            h("span", { class: "card-title grow" }, room.title),
            last && h("span", { class: "tiny muted" }, shortWhen(last.created))),
          room.subtitle && h("div", { class: "tiny muted" }, room.subtitle),
          h("div", { class: "card-sub chat-preview" }, preview)),
        room.unread && h("span", { class: "unread-dot", "aria-label": "Новые сообщения" }));
    };

    const body = Screens.asyncBlock(() => Api.chatRooms(), (rooms) => {
      if (!user.isTeacher) {
        return [
          h("p", { class: "muted small" }, "Пиши вежливо: сообщения видит учитель. Лично можно написать только учителю."),
          rooms.map(roomCard),
        ];
      }
      const classes = rooms.filter((r) => r.kind === "class");
      const threads = rooms.filter((r) => r.kind === "private");
      return [
        sectionLabel("Чаты классов"),
        classes.length ? classes.map(roomCard) : emptyBox("👥", "Классы появятся, когда ученики зарегистрируются"),
        sectionLabel("Личные сообщения"),
        threads.length ? threads.map(roomCard) : h("p", { class: "muted small" }, "Ученики ещё не писали вам лично. Написать ученику можно из вкладки «Ученики» в панели учителя."),
      ];
    });
    return h("div", { class: "screen" }, topBar("Чат", () => App.back("")), body);
  }

  // ======================= Переписка =======================
  function room(params) {
    const user = Api.state.user;
    const r = params.kind === "class" ? { kind: "class", group: params.key }
      : params.kind === "course" ? { kind: "course", enrollment: params.key } : { kind: "private", student: params.key };
    const key = Api.roomKey(r);
    const defaultTitle = r.kind === "course" ? (user.isTeacher ? "Ученик курса" : "Учитель курса")
      : r.kind === "class"
        ? (user.isTeacher ? `${r.group} класс` : `Чат класса ${r.group}`)
        : (user.isTeacher ? "Ученик" : "Учитель");
    const title = titles.get(key) || defaultTitle;
    const back = r.kind === "course" ? (user.isTeacher ? `admin/course/${r.enrollment}` : `course/${r.enrollment}`) : "chats";
    const header = topBar(title, () => App.back(back));
    if (user.isTeacher && r.kind === "private" && !titles.has(key)) {
      Api.students().then((all) => {
        const st = all.find((s) => s.id === r.student);
        if (st) header.querySelector(".topbar-title").textContent = st.group ? `${st.name}, ${st.group}` : st.name;
      }, () => {});
    }

    let messages = [];
    let loaded = false;
    let limit = 50;
    let hasMore = false;
    const listBox = h("div", { class: "chat-list", "aria-live": "polite" });
    const status = h("div");

    function bubble(m) {
      const mine = m.author === user.id;
      const showName = !mine && (r.kind === "class" || !user.isTeacher);
      const del = user.isTeacher && h("button", {
        class: "icon-btn msg-delete", "aria-label": "Удалить сообщение", title: "Удалить",
        onclick: async () => {
          if (!(await confirm("Удалить сообщение?", `«${m.text.slice(0, 80)}» — ${m.authorName}`, "Удалить"))) return;
          try {
            await Api.deleteMessage(m.id);
            removeMessage(m.id);
            toast("Сообщение удалено");
          } catch (e) {
            toast(e.message || "Не получилось удалить");
          }
        },
      }, icon("delete", 18));
      return h("div", { class: "msg-row" + (mine ? " mine" : "") },
        h("div", { class: "msg" + (mine ? " msg-mine" : "") + (m.fromTeacher && !mine ? " msg-teacher" : "") },
          showName && h("div", { class: "msg-author" }, m.authorName, m.fromTeacher && h("span", { class: "pill pill-teacher" }, "Учитель")),
          h("div", { class: "msg-text" }, m.text),
          h("div", { class: "msg-time" }, timeOf(m.created))),
        del);
    }

    function render() {
      const children = [];
      if (hasMore) {
        children.push(h("button", { class: "btn btn-text chat-more", type: "button", onclick: loadMore }, h("span", null, "Показать ранние сообщения")));
      }
      let day = "";
      messages.forEach((m) => {
        const d = dayOf(m.created);
        if (d !== day) {
          day = d;
          children.push(h("div", { class: "chat-day" }, d));
        }
        children.push(bubble(m));
      });
      if (!messages.length) {
        children.push(emptyBox("💬", r.kind === "course"
          ? (user.isTeacher ? "Напишите ученику курса первым" : "Задай учителю курса вопрос — переписку видите только вы двое")
          : r.kind === "class"
          ? "Здесь пока пусто. Напиши первое сообщение классу!"
          : user.isTeacher ? "Напишите ученику первым" : "Задай учителю вопрос — его увидит только учитель"));
      }
      replace(listBox, children);
      if (messages.length) Api.chatSeen.mark(r, messages[messages.length - 1].created);
    }

    const nearBottom = () => window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 120;
    const toBottom = () => window.scrollTo(0, document.documentElement.scrollHeight);

    async function load() {
      try {
        const page = await Api.messages(r, limit);
        messages = page.items;
        hasMore = page.hasMore;
        loaded = true;
        replace(status);
        render();
      } catch (e) {
        replace(status, UI.errorBox(e.message || "Ошибка", load));
      }
    }

    async function loadMore() {
      const before = document.documentElement.scrollHeight;
      limit += 50;
      await load();
      window.scrollTo(0, window.scrollY + document.documentElement.scrollHeight - before);
    }

    function addMessage(m) {
      if (messages.some((x) => x.id === m.id)) return;
      const stick = nearBottom() || m.author === user.id;
      messages.push(m);
      messages.sort((a, b) => (a.created < b.created ? -1 : a.created > b.created ? 1 : 0));
      render();
      if (stick) toBottom();
    }

    function removeMessage(id) {
      const before = messages.length;
      messages = messages.filter((x) => x.id !== id);
      if (messages.length !== before) render();
    }

    // ----- Поле ввода -----
    const input = h("textarea", {
      class: "chat-input", rows: 1, maxlength: 1000, placeholder: "Сообщение", "aria-label": "Сообщение",
      autocapitalize: "sentences",
    });
    const sendBtn = h("button", { class: "icon-btn chat-send", type: "button", "aria-label": "Отправить", title: "Отправить", disabled: true }, icon("send"));
    let sending = false;
    const resize = () => {
      input.style.height = "auto";
      input.style.height = Math.min(input.scrollHeight, 140) + "px";
    };
    input.addEventListener("input", () => {
      sendBtn.disabled = sending || !input.value.trim();
      resize();
    });
    const touch = window.matchMedia && window.matchMedia("(pointer: coarse)").matches;
    input.addEventListener("keydown", (e) => {
      // На компьютере Enter отправляет, Shift+Enter — новая строка. На телефоне Enter — новая строка.
      if (e.key === "Enter" && !e.shiftKey && !touch) {
        e.preventDefault();
        send();
      }
    });
    async function send() {
      const text = input.value.trim();
      if (!text || sending) return;
      sending = true;
      sendBtn.disabled = true;
      try {
        addMessage(await Api.sendMessage(r, text));
        input.value = "";
        resize();
      } catch (e) {
        if (e.status === 400 || e.status === 403) {
          // Возможно, учитель поменял имя или класс: обновляем профиль и пробуем ещё раз.
          await Api.refreshSession();
          try {
            addMessage(await Api.sendMessage(r, text));
            input.value = "";
            resize();
          } catch (e2) {
            toast(e2.message || "Не получилось отправить");
          }
        } else {
          toast(e.message || "Не получилось отправить");
        }
      }
      sending = false;
      sendBtn.disabled = !input.value.trim();
      input.focus();
    }
    sendBtn.addEventListener("click", send);

    // ----- Новые сообщения без перезагрузки -----
    const unsubscribe = Api.realtime.subscribe((e) => {
      if (e.action === "connected") {
        if (loaded) load(); // после переподключения догружаем то, что пришло, пока связи не было
      } else if (e.action === "create" && e.record && Api.inRoom(e.record, r)) {
        addMessage(e.record);
      } else if (e.action === "delete" && e.record) {
        removeMessage(e.record.id);
      }
    });

    listBox.append(UI.loading());
    load().then(toBottom);

    const screen = h("div", { class: "screen chat-screen" },
      header,
      status,
      listBox,
      h("div", { class: "chat-composer" }, input, sendBtn));
    screen.onLeave = unsubscribe;
    return screen;
  }

  /** Плашка «Чат» на главной: показывает, в скольких чатах есть новые сообщения. */
  function homeCard() {
    const user = Api.state.user;
    const sub = h("div", { class: "card-sub muted" }, user.isTeacher ? "Чаты классов и личные сообщения" : "Чат класса и вопросы учителю");
    const pill = h("span", { class: "pill pill-unread", hidden: true });
    const card = h("a", { class: "card card-link", href: "#/chats" },
      emojiBadge("💬", "c-secondary"),
      h("div", { class: "grow" }, h("div", { class: "card-title" }, "Чат"), sub),
      pill,
      icon("chevron"));
    Api.unreadChats().then((n) => {
      if (!n) return;
      pill.hidden = false;
      pill.textContent = n === 1 ? "новое" : `новые: ${n}`;
    });
    return card;
  }

  /** Открыть переписку с заголовком (например, из списка учеников). */
  function open(r, title) {
    if (title) titles.set(Api.roomKey(r), title);
    App.go(roomPath(r));
  }

  window.Chat = { list, room, homeCard, roomPath, open };
})();
