/*
 * Магазин beemei: витрина, карточка курса, корзина, оформление и оплата через ЮKassa, статус заказа.
 * И сами курсы: «Подготовка» у ученика и «Мои курсы» у учителя (практики для одного ученика и чат курса).
 * Сервер — pb_hooks/shop.pb.js: цены берёт из базы, курс выдаёт только после того, как ЮKassa подтвердила оплату.
 */
(function () {
  "use strict";
  const { h, replace, icon, topBar, button, field, feedback, chip, emptyBox, sectionLabel, gradeBadge, toast, emojiBadge, loading, errorBox } = UI;
  const { content } = Api;

  const rub = (n) => Number(n || 0).toLocaleString("ru-RU") + " ₽";
  const subjectTitle = (key) => Content.subjectOf(key).title;
  const subjectEmoji = (key) => (key === "biology" ? "🧬" : "⚗️");
  const optionsText = (it) => [it.subject && subjectTitle(it.subject), it.exam].filter(Boolean).join(" · ");
  /** ОГЭ сдают в 9 классе, ЕГЭ — в 11. */
  const examGrade = (exam) => (exam === "ОГЭ" ? 9 : 11);

  function header(back) {
    const count = Api.cart.count();
    return topBar(h("span", { class: "shop-logo" }, h("span", { "aria-hidden": "true" }, "🐝"), "beemei"), () => App.back(back),
      h("a", { class: "icon-btn shop-cart", href: "#/shop/cart", "aria-label": "Корзина" + (count ? `: ${count}` : ""), title: "Корзина" },
        h("span", { "aria-hidden": "true" }, "🛒"), count > 0 && h("span", { class: "shop-cart-count" }, String(count))));
  }

  // ======================= Витрина =======================
  function catalog() {
    const grid = Screens.asyncBlock(() => Api.products(), (items) => items.length
      ? h("div", { class: "shop-grid" }, items.map(productCard))
      : emptyBox("🛍️", "Скоро здесь появятся курсы"));
    return h("div", { class: "screen shop" },
      header(""),
      h("div", { class: "shop-hero" },
        h("div", { class: "shop-hero-title" }, "Подготовка к ОГЭ и ЕГЭ"),
        h("p", null, "Личные онлайн-курсы по химии и биологии с учителем: задания под вас, проверка работ и чат.")),
      grid);
  }

  function productCard(p) {
    return h("a", { class: "shop-card", href: `#/shop/product/${p.id}` },
      h("div", { class: "shop-card-img" },
        h("img", { src: Api.productImage(p, "600x600"), alt: "", loading: "lazy" }),
        p.badge && h("span", { class: "shop-badge" }, p.badge)),
      h("div", { class: "shop-card-body" },
        h("div", { class: "shop-price" }, rub(p.price), p.oldPrice > p.price && h("s", null, rub(p.oldPrice))),
        h("div", { class: "shop-card-title" }, p.title),
        p.category && h("div", { class: "tiny muted" }, p.category),
        h("span", { class: "btn btn-filled btn-block shop-buy" }, h("span", null, "В корзину"))));
  }

  // ======================= Карточка товара =======================
  function product(params) {
    let subject = "";
    let exam = "";
    const body = Screens.asyncBlock(() => Api.product(params.id), (p) => {
      const subjects = Array.isArray(p.subjects) ? p.subjects : [];
      const exams = Array.isArray(p.exams) ? p.exams : [];
      if (subjects.length === 1) subject = subjects[0];
      if (exams.length === 1) exam = exams[0];
      const subjectBox = h("div", { class: "row-wrap" });
      const examBox = h("div", { class: "row-wrap" });
      const message = h("div", { "aria-live": "polite" });
      const render = () => {
        replace(subjectBox, subjects.map((k) => chip(`${subjectEmoji(k)} ${subjectTitle(k)}`, subject === k, () => { subject = k; replace(message); render(); })));
        replace(examBox, exams.map((x) => chip(x, exam === x, () => { exam = x; replace(message); render(); })));
      };
      render();
      const add = (buyNow) => {
        if (subjects.length && !subject) return replace(message, feedback("Выберите предмет", "error"));
        if (exams.length && !exam) return replace(message, feedback("Выберите экзамен", "error"));
        Api.cart.add({ product: p.id, subject, exam });
        if (buyNow) return App.go("shop/checkout");
        toast("Курс в корзине");
        App.render();
      };
      return h("div", { class: "shop-product" },
        h("div", { class: "shop-product-img" }, h("img", { src: Api.productImage(p, "600x600"), alt: p.title })),
        h("div", { class: "shop-product-info stack" },
          p.badge && h("span", { class: "shop-badge static" }, p.badge),
          h("h1", { class: "shop-product-title" }, p.title),
          h("div", { class: "shop-price big" }, rub(p.price), p.oldPrice > p.price && h("s", null, rub(p.oldPrice))),
          subjects.length > 0 && [h("div", { class: "small bold" }, "Предмет"), subjectBox],
          exams.length > 0 && [h("div", { class: "small bold" }, "Экзамен"), examBox],
          message,
          h("div", { class: "row-wrap" },
            button("🛒 В корзину", () => add(false), { kind: "outline" }),
            button("Купить сейчас", () => add(true))),
          h("p", { class: "tiny muted" }, "Курс откроется сразу после оплаты: на главной появится раздел «Подготовка»."),
          sectionLabel("Описание"),
          h("div", { class: "shop-description" }, p.description)));
    });
    return h("div", { class: "screen shop" }, header("shop"), body);
  }

  /** Товары корзины вместе с данными с сервера. Убирает из корзины то, что больше не продаётся. */
  async function cartItems() {
    const items = Api.cart.get();
    const byId = new Map((await Api.products()).map((p) => [p.id, p]));
    const valid = items.filter((it) => byId.has(it.product));
    if (valid.length !== items.length) Api.cart.set(valid);
    return valid.map((it, index) => ({ it, index, p: byId.get(it.product) }));
  }

  // ======================= Корзина =======================
  function cart() {
    const body = Screens.asyncBlock(cartItems, (rows) => {
      if (!rows.length) {
        return [emptyBox("🛒", "В корзине пока пусто"), h("a", { class: "btn btn-filled btn-block", href: "#/shop" }, h("span", null, "Перейти в магазин"))];
      }
      const total = rows.reduce((sum, r) => sum + r.p.price, 0);
      return [
        rows.map(({ it, index, p }) => h("div", { class: "card shop-cart-row" },
          h("img", { class: "shop-thumb", src: Api.productImage(p, "300x300"), alt: "" }),
          h("div", { class: "grow" },
            h("div", { class: "bold" }, p.title),
            h("div", { class: "small muted" }, optionsText(it)),
            h("div", { class: "shop-price" }, rub(p.price))),
          h("button", {
            class: "icon-btn", type: "button", "aria-label": "Убрать из корзины", title: "Убрать",
            onclick: () => { Api.cart.remove(index); App.render(); },
          }, icon("delete")))),
        h("div", { class: "card stack shop-summary" },
          h("div", { class: "row" }, h("span", { class: "grow muted" }, `Товаров: ${rows.length}`)),
          h("div", { class: "row shop-total" }, h("span", { class: "grow" }, "Итого"), h("b", null, rub(total))),
          button("Перейти к оформлению", () => App.go("shop/checkout"), { cls: "btn-block btn-tall" })),
      ];
    });
    return h("div", { class: "screen shop" }, header("shop"), h("h2", { class: "home-title" }, "Корзина"), body);
  }

  // ======================= Оформление и оплата =======================
  function checkout() {
    const user = Api.state.user;
    if (user && user.isTeacher) {
      return h("div", { class: "screen shop" }, header("shop/cart"),
        emptyBox("👩‍🏫", "Курсы покупают ученики. Чтобы проверить покупку, войдите в аккаунт ученика."));
    }
    let email = Api.storage.get("beemei_email", "");
    let phone = Api.storage.get("beemei_phone", "");
    const body = Screens.asyncBlock(cartItems, (rows) => {
      if (!rows.length) return [emptyBox("🛒", "В корзине пока пусто"), h("a", { class: "btn btn-text", href: "#/shop" }, "Перейти в магазин")];
      const total = rows.reduce((sum, r) => sum + r.p.price, 0);
      const agree = h("input", { type: "checkbox" });
      const message = h("div", { "aria-live": "polite" });
      const payText = `Оплатить ${rub(total)}`;
      const payBtn = button(payText, pay, { cls: "btn-block btn-tall" });
      async function pay() {
        if (!agree.checked) return replace(message, feedback("Подтвердите согласие с условиями покупки", "error"));
        payBtn.disabled = true;
        replace(payBtn, h("span", { class: "spinner spinner-sm" }), h("span", null, "Переходим к оплате…"));
        try {
          const r = await Api.checkout(rows.map((x) => x.it), email.trim(), phone.trim());
          Api.storage.set("beemei_email", email.trim());
          Api.storage.set("beemei_phone", phone.trim());
          location.href = r.url; // страница оплаты ЮKassa, после неё — обратно на страницу заказа
        } catch (e) {
          replace(message, feedback(e.message || "Не получилось", "error"));
          payBtn.disabled = false;
          replace(payBtn, h("span", null, payText));
        }
      }
      return [
        h("div", { class: "card stack" },
          h("div", { class: "card-title" }, "Ваш заказ"),
          rows.map(({ it, p }) => h("div", { class: "row" },
            h("div", { class: "grow" }, h("div", null, p.title), h("div", { class: "tiny muted" }, optionsText(it))),
            h("b", null, rub(p.price)))),
          h("div", { class: "row shop-total" }, h("span", { class: "grow" }, "Итого"), h("b", null, rub(total)))),
        h("div", { class: "card stack" },
          h("div", { class: "card-title" }, "Куда прислать чек"),
          field({ label: "Электронная почта", type: "email", value: email, autocomplete: "email", autocapitalize: "none", oninput: (v) => { email = v; } }),
          field({ label: "Телефон", type: "tel", value: phone, autocomplete: "tel", inputmode: "tel", oninput: (v) => { phone = v; } }),
          h("p", { class: "tiny muted" }, "Достаточно почты или телефона.")),
        h("div", { class: "card stack" },
          h("div", { class: "card-title" }, "Оплата"),
          h("p", { class: "small" }, "Оплата через ЮKassa: СБП, банковская карта или SberPay. После оплаты вы вернётесь на сайт, и курс откроется сразу."),
          h("label", { class: "check-row" }, agree,
            h("span", null, "Я согласен с ", h("a", { href: "offer.html", target: "_blank", rel: "noopener" }, "условиями покупки (публичной офертой)"))),
          message,
          payBtn),
      ];
    });
    return h("div", { class: "screen shop" }, header("shop/cart"), h("h2", { class: "home-title" }, "Оформление заказа"), body);
  }

  // ======================= Заказ после оплаты =======================
  function order(params) {
    const box = h("div", { class: "stack" });
    let tries = 0;
    async function load() {
      let o;
      try {
        o = await Api.order(params.id);
      } catch (e) {
        return replace(box, errorBox(e.message || "Ошибка", load));
      }
      const list = h("div", { class: "card stack" },
        o.items.map((it) => h("div", { class: "row" },
          h("div", { class: "grow" }, h("div", null, it.title), h("div", { class: "tiny muted" }, optionsText(it))),
          h("b", null, rub(it.price)))),
        h("div", { class: "row shop-total" }, h("span", { class: "grow" }, "Итого"), h("b", null, rub(o.total))));
      if (o.status === "paid") {
        Api.cart.clear();
        await Api.refreshContent();
        return replace(box,
          h("div", { class: "card stack center" },
            h("div", { class: "congrats" }, "🎉"),
            h("div", { class: "home-title" }, "Оплата прошла!"),
            h("p", null, "Курс открыт. На главной появился раздел «Подготовка» — там задания от учителя и чат с ним."),
            h("a", { class: "btn btn-filled btn-block", href: "#/" }, h("span", null, "Перейти к подготовке"))),
          list);
      }
      if (o.status === "pending" && tries++ < 40) {
        replace(box,
          h("div", { class: "card stack center" },
            h("span", { class: "spinner" }),
            h("p", null, "Ждём подтверждение оплаты от банка… Обычно это несколько секунд."),
            h("p", { class: "tiny muted" }, "Если вы закрыли страницу оплаты, не заплатив, вернитесь в корзину и оформите заказ ещё раз.")),
          list);
        setTimeout(() => { if (box.isConnected) load(); }, 3000);
        return;
      }
      replace(box,
        h("div", { class: "card stack center" },
          h("div", { class: "congrats" }, "😕"),
          h("div", { class: "home-title" }, o.status === "pending" ? "Оплата ещё не подтверждена" : "Оплата не прошла"),
          h("p", { class: "small muted" }, o.status === "pending"
            ? "Если деньги списались, курс откроется сам, как только банк подтвердит оплату. Можно обновить эту страницу позже."
            : "Деньги не списаны. Товары остались в корзине — можно попробовать ещё раз."),
          h("a", { class: "btn btn-filled btn-block", href: "#/shop/cart" }, h("span", null, "Вернуться в корзину"))),
        list);
    }
    load();
    return h("div", { class: "screen shop" }, header("shop"), h("h2", { class: "home-title" }, "Заказ"), box);
  }

  // ======================= «Подготовка» у ученика =======================
  /** Купленные курсы ученика на главной (запоминаем, чтобы при перерисовке не мигало). */
  let myCourses = [];
  function homeCourses() {
    const box = h("div", { class: "stack" });
    const render = () => replace(box, myCourses.length > 0 && [
      sectionLabel("Подготовка"),
      myCourses.map((en) => h("a", { class: "card card-link card-highlight", href: `#/course/${en.id}` },
        emojiBadge(subjectEmoji(en.subject), "c-primary"),
        h("div", { class: "grow" },
          h("div", { class: "card-title" }, `Подготовка к ${en.exam} · ${subjectTitle(en.subject)}`),
          h("div", { class: "card-sub muted" }, "Личный курс" + (en.expand && en.expand.teacher ? ` · учитель ${en.expand.teacher.name}` : ""))),
        icon("chevron"))),
    ]);
    render();
    Api.enrollments().then((list) => { myCourses = list.filter((x) => x.active); render(); }, () => {});
    return box;
  }

  function coursePracticeRow(p, href) {
    const last = Api.lastSubmission(p.id);
    const status = last ? (last.grade > 0 ? `Сдано · оценка ${last.grade}` : "Сдано · на проверке") : "Не сдано";
    const d = Content.deadlineOf(p);
    return h("a", { class: "card card-link", href },
      h("div", { class: "grow" },
        h("div", { class: "row" }, h("span", { class: "bold" }, p.title), !p.published && h("span", { class: "pill pill-draft" }, "Черновик")),
        h("div", { class: "card-sub" }, [p.exam ? Content.examLabel(p) : null, href.startsWith("#/practice") ? status : null, d && "до " + Content.formatDay(d)].filter(Boolean).join(" · "))),
      last && last.grade > 0 && gradeBadge(last.grade),
      icon("chevron"));
  }

  function course(params) {
    const body = Screens.asyncBlock(async () => {
      const en = await Api.enrollment(params.id);
      await Promise.all([Api.refreshContent(), Api.refreshMySubmissions()]);
      return en;
    }, (en) => {
      const teacher = en.expand && en.expand.teacher;
      const list = content.coursePractices(en.id).filter((p) => p.published);
      return [
        h("div", { class: "card row card-highlight" },
          emojiBadge(subjectEmoji(en.subject), "c-primary"),
          h("div", { class: "grow" },
            h("div", { class: "card-title" }, `Подготовка к ${en.exam} · ${subjectTitle(en.subject)}`),
            h("div", { class: "card-sub muted" }, "Личный курс" + (teacher ? ` · учитель ${teacher.name}` : "")))),
        button("💬 Чат с учителем", () => Chat.open({ kind: "course", enrollment: en.id }, "Чат с учителем курса"), { kind: "outline", cls: "btn-block" }),
        sectionLabel("Задания от учителя"),
        list.length
          ? list.map((p) => coursePracticeRow(p, `#/practice/${p.id}`))
          : emptyBox("📝", "Учитель скоро пришлёт первые задания. Пока можно написать ему в чат или потренироваться в тренажёре."),
        h("a", { class: "btn btn-text", href: "#/trainer" }, "🎯 Тренажёр"),
      ];
    });
    return h("div", { class: "screen" }, topBar("Подготовка", () => App.back("")), body);
  }

  // ======================= «Мои курсы» у учителя =======================
  function teacherList() {
    return Screens.asyncBlock(() => Api.enrollments(), (items) => items.length
      ? [
        h("p", { class: "muted small" }, `Учеников на курсах: ${items.length}. Нажмите на ученика, чтобы дать ему задания и написать в чат.`),
        items.map((en) => {
          const st = en.expand && en.expand.student;
          return h("a", { class: "card card-link", href: `#/admin/course/${en.id}` },
            emojiBadge(subjectEmoji(en.subject), en.subject === "biology" ? "c-biology" : "c-chemistry"),
            h("div", { class: "grow" },
              h("div", { class: "card-title" }, st ? st.name : "Ученик"),
              h("div", { class: "card-sub" }, `${en.exam} · ${subjectTitle(en.subject)} · куплен ${Content.formatDate(en.created)}` + (en.active ? "" : " · отключён"))),
            icon("chevron"));
        }),
      ]
      : emptyBox("🎓", "Пока никто не купил курс. Когда ученик оплатит курс в магазине beemei, он появится здесь."));
  }

  function teacherCourse(params) {
    const body = Screens.asyncBlock(async () => {
      const [en, subs] = await Promise.all([Api.enrollment(params.id), Api.allSubmissions(false), Api.refreshContent()]);
      return { en, subs };
    }, ({ en, subs }) => {
      const st = en.expand && en.expand.student;
      const list = content.coursePractices(en.id);
      const ids = new Set(list.map((p) => p.id));
      const works = subs.filter((s) => ids.has(s.practice));
      return [
        h("div", { class: "card row" },
          h("div", { class: "avatar big" }, ((st && st.name) || "?")[0].toUpperCase()),
          h("div", { class: "grow" },
            h("div", { class: "home-title" }, st ? st.name : "Ученик"),
            h("div", { class: "small muted" }, `Подготовка к ${en.exam} · ${subjectTitle(en.subject)}`),
            h("div", { class: "tiny muted" }, [st && `логин ${st.username}`, st && st.group && `${st.group} класс`, `курс куплен ${Content.formatDate(en.created)}`].filter(Boolean).join(" · ")))),
        button("💬 Чат с учеником", () => Chat.open({ kind: "course", enrollment: en.id }, st ? st.name : "Ученик курса"), { kind: "outline", cls: "btn-block" }),
        sectionLabel(`Практики ученика (${list.length})`),
        list.length === 0 && h("p", { class: "muted small" }, "Эти практики видит только этот ученик. Можно задать обычные задания, контрольную со временем или сгенерировать задания нейросетью."),
        list.map((p) => coursePracticeRow(p, `#/admin/practice/${p.id}`)),
        button("+ Новая практика для ученика", () => App.go(`admin/course/${en.id}/new/${en.subject}/${examGrade(en.exam)}`), { cls: "btn-block" }),
        sectionLabel(`Работы ученика (${works.length})`),
        works.length ? works.map((s) => Screens.submissionCard(s, false)) : h("p", { class: "muted small" }, "Ученик пока не сдавал работ."),
      ];
    });
    return h("div", { class: "screen" }, topBar("Ученик курса", () => App.back("admin/courses")), body);
  }

  window.Shop = { catalog, product, cart, checkout, order, homeCourses, course, teacherList, teacherCourse };
})();
