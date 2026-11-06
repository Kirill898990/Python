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
  /** «2 дня», «7 дней», «30 дней» */
  const daysText = (n) => {
    const d = n % 10;
    const word = n % 100 >= 11 && n % 100 <= 14 ? "дней" : d === 1 ? "день" : d >= 2 && d <= 4 ? "дня" : "дней";
    return `${n} ${word}`;
  };
  const plansOf = (p) => (Array.isArray(p.plans) && p.plans.length ? p.plans : [{ days: 30, price: p.price }])
    .slice().sort((a, b) => a.days - b.days);
  /** Цена выбранного срока. */
  const planPrice = (p, days) => (plansOf(p).find((x) => x.days === days) || plansOf(p)[0]).price;
  const minPrice = (p) => Math.min(...plansOf(p).map((x) => x.price));
  /** Срок курса: не закончился ли и до какого дня. */
  const expiresOf = (en) => (en.expiresAt ? Content.timeOf(en.expiresAt) : 0);
  const isLive = (en) => en.active && expiresOf(en) > Date.now();
  const untilText = (en) => {
    const t = expiresOf(en);
    if (!t) return "";
    const d = new Date(t);
    return (t > Date.now() ? "до " : "закончился ") + d.toLocaleDateString("ru-RU", { day: "numeric", month: "long" });
  };
  let teachersCache = null;
  /** Подтверждённые учителя (их же видят ученики при регистрации). */
  const teachers = async () => teachersCache || (teachersCache = (await Api.registrationInfo()).teachers);
  const teacherName = (list, id) => (list.find((t) => t.id === id) || {}).name || "";
  const subjectTitle = (key) => Content.subjectOf(key).title;
  const subjectEmoji = (key) => (key === "biology" ? "🧬" : "⚗️");
  const optionsText = (it, list) => {
    const name = it.teacherName || (list && teacherName(list, it.teacher));
    return [it.subject && subjectTitle(it.subject), it.exam, it.days && daysText(it.days), name && "учитель " + name].filter(Boolean).join(" · ");
  };
  /** «1 отзыв», «3 отзыва», «12 отзывов» */
  const reviewsText = (n) => {
    const d = n % 10;
    const word = n % 100 >= 11 && n % 100 <= 14 ? "отзывов" : d === 1 ? "отзыв" : d >= 2 && d <= 4 ? "отзыва" : "отзывов";
    return `${n} ${word}`;
  };
  /** Звёзды оценки: ★★★★☆ */
  const stars = (n, cls = "") => h("span", { class: "stars " + cls, "aria-label": `Оценка ${n} из 5` },
    "★".repeat(Math.round(n)), h("span", { class: "stars-off" }, "★".repeat(5 - Math.round(n))));
  /** Сколько осталось до конца курса: «2 дня», «5 ч». */
  const leftText = (en) => {
    const hours = Api.hoursLeft(en);
    if (hours == null || hours <= 0) return "";
    return hours < 24 ? `${Math.max(1, Math.ceil(hours))} ч` : daysText(Math.ceil(hours / 24));
  };
  /** Курс скоро кончится (меньше 3 дней) — пора напомнить о продлении. */
  const endingSoon = (en) => isLive(en) && Api.hoursLeft(en) < 72;
  /** ОГЭ сдают в 9 классе, ЕГЭ — в 11. */
  const examGrade = (exam) => (exam === "ОГЭ" ? 9 : 11);

  /** Подвал магазина: продавец, документы и контакты — их проверяет ЮKassa. */
  function footer() {
    const line = h("p", { class: "tiny muted" });
    Api.shopInfo().then((i) => {
      line.textContent = [i.sellerName && `Продавец: ${i.sellerName}`, i.inn && `ИНН ${i.inn}`, i.phone, i.email].filter(Boolean).join(" · ");
    }, () => {});
    return h("footer", { class: "shop-footer" },
      h("div", { class: "row-wrap" },
        h("a", { href: "#/shop/info" }, "О продавце и контакты"),
        h("a", { href: "#/shop/info" }, "Оплата и получение курса"),
        h("a", { href: "#/shop/info" }, "Возврат"),
        h("a", { href: "#/shop/offer" }, "Оферта"),
        h("a", { href: "privacy.html" }, "Конфиденциальность")),
      line);
  }

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
    return h("div", { class: "screen shop" }, header(""), grid, footer());
  }

  function productCard(p) {
    return h("a", { class: "shop-card", href: `#/shop/product/${p.id}` },
      h("div", { class: "shop-card-img" },
        h("img", { src: Api.productImage(p, "600x600"), alt: "", loading: "lazy" }),
        p.badge && h("span", { class: "shop-badge" }, p.badge)),
      h("div", { class: "shop-card-body" },
        h("div", { class: "shop-price" }, (plansOf(p).length > 1 ? "от " : "") + rub(minPrice(p)), p.oldPrice > minPrice(p) && h("s", null, rub(p.oldPrice))),
        h("div", { class: "shop-card-title" }, p.title),
        p.reviews > 0 && h("div", { class: "shop-rating small" }, stars(p.rating), h("span", { class: "muted" }, ` ${String(p.rating).replace(".", ",")} · ${reviewsText(p.reviews)}`)),
        h("span", { class: "btn btn-filled btn-block shop-buy" }, h("span", null, "В корзину"))));
  }

  // ======================= Карточка товара =======================
  /** Как проходит курс и как получить доступ (это требование ЮKassa: как покупатель получает услугу). */
  const howItWorks = () => h("div", { class: "card stack" },
    h("div", { class: "card-title" }, "Как получить курс"),
    h("ol", { class: "shop-steps" },
      h("li", null, "Выберите учителя, предмет, экзамен и срок и оплатите курс."),
      h("li", null, "Сразу после оплаты на сайте урокискириллом.рф и в приложении «Простая химия» в вашем аккаунте появится раздел «Подготовка»."),
      h("li", null, "Учитель присылает туда практики и тренировочные задания для вас, проверяет работы и отвечает в чате курса."),
      h("li", null, "Доступ открыт на выбранный срок. Его можно продлить, купив курс ещё раз — срок прибавится.")));

  function product(params) {
    let teacher = "";
    let subject = "";
    let exam = "";
    let days = 0;
    const body = Screens.asyncBlock(async () => [await Api.product(params.id), await teachers()], ([p, all]) => {
      const subjects = Array.isArray(p.subjects) ? p.subjects : [];
      const exams = Array.isArray(p.exams) ? p.exams : [];
      const allowed = Array.isArray(p.teachers) && p.teachers.length ? all.filter((t) => p.teachers.includes(t.id)) : all;
      const plans = plansOf(p);
      if (allowed.length === 1) teacher = allowed[0].id;
      if (subjects.length === 1) subject = subjects[0];
      if (exams.length === 1) exam = exams[0];
      if (!days) days = plans[0].days;
      const teacherBox = h("div", { class: "row-wrap" });
      const subjectBox = h("div", { class: "row-wrap" });
      const examBox = h("div", { class: "row-wrap" });
      const planBox = h("div", { class: "shop-plans" });
      const price = h("div", { class: "shop-price big" });
      const message = h("div", { "aria-live": "polite" });
      const render = () => {
        replace(teacherBox, allowed.map((t) => chip("👩‍🏫 " + t.name, teacher === t.id, () => { teacher = t.id; replace(message); render(); })));
        replace(subjectBox, subjects.map((k) => chip(`${subjectEmoji(k)} ${subjectTitle(k)}`, subject === k, () => { subject = k; replace(message); render(); })));
        replace(examBox, exams.map((x) => chip(x, exam === x, () => { exam = x; replace(message); render(); })));
        replace(planBox, plans.map((pl) => h("button", {
          type: "button", class: "shop-plan" + (pl.days === days ? " selected" : ""), "aria-pressed": pl.days === days ? "true" : "false",
          onclick: () => { days = pl.days; render(); },
        }, h("b", null, daysText(pl.days)), h("span", null, rub(pl.price)))));
        replace(price, rub(planPrice(p, days)), h("span", { class: "small muted" }, ` за ${daysText(days)}`));
      };
      render();
      const add = (buyNow) => {
        if (!teacher) return replace(message, feedback("Выберите учителя", "error"));
        if (subjects.length && !subject) return replace(message, feedback("Выберите предмет", "error"));
        if (exams.length && !exam) return replace(message, feedback("Выберите экзамен", "error"));
        Api.cart.add({ product: p.id, teacher, subject, exam, days });
        if (buyNow) return App.go("shop/checkout");
        toast("Курс в корзине");
        App.render();
      };
      return h("div", { class: "shop-product" },
        h("div", { class: "shop-product-img" }, h("img", { src: Api.productImage(p, "600x600"), alt: p.title })),
        h("div", { class: "shop-product-info stack" },
          p.badge && h("span", { class: "shop-badge static" }, p.badge),
          h("h1", { class: "shop-product-title" }, p.title),
          p.reviews > 0 && h("a", { class: "shop-rating", href: "#reviews", onclick: (e) => { e.preventDefault(); document.getElementById("reviews").scrollIntoView({ behavior: "smooth" }); } },
            stars(p.rating), h("span", { class: "small muted" }, ` ${String(p.rating).replace(".", ",")} · ${reviewsText(p.reviews)}`)),
          price,
          h("div", { class: "small bold" }, "Срок подписки"), planBox,
          h("div", { class: "small bold" }, "Учитель"),
          allowed.length ? teacherBox : h("p", { class: "small muted" }, "Учителя скоро появятся"),
          subjects.length > 0 && [h("div", { class: "small bold" }, "Предмет"), subjectBox],
          exams.length > 0 && [h("div", { class: "small bold" }, "Экзамен"), examBox],
          message,
          h("div", { class: "row-wrap" },
            button("🛒 В корзину", () => add(false), { kind: "outline" }),
            button("Купить сейчас", () => add(true))),
          h("p", { class: "tiny muted" }, "Курс откроется сразу после оплаты: в аккаунте появится раздел «Подготовка»."),
          sectionLabel("Описание"),
          h("div", { class: "shop-description" }, p.description),
          howItWorks(),
          reviewsBlock(p)));
    });
    return h("div", { class: "screen shop" }, header("shop"), body, footer());
  }

  // ======================= Отзывы =======================
  /** Отзывы о товаре. Написать может только тот, кто купил этот курс (это проверяет и сервер). */
  function reviewsBlock(p) {
    const box = h("div", { class: "stack", id: "reviews" });
    const user = Api.state.user;
    async function load() {
      const [list, bought] = await Promise.all([
        Api.reviews(p.id),
        user && !user.isTeacher ? Api.enrollments().then((ens) => ens.some((en) => en.product === p.id), () => false) : false,
      ]);
      const mine = user ? list.find((r) => r.author === user.id) : null;
      const others = list.filter((r) => r !== mine);
      replace(box,
        sectionLabel(list.length ? `Отзывы (${list.length})` : "Отзывы"),
        mine ? reviewCard(mine, true) : bought ? reviewForm(null) : h("p", { class: "small muted" }, list.length
          ? "Отзыв может оставить только тот, кто купил этот курс."
          : "Отзывов пока нет. Оставить отзыв может только тот, кто купил этот курс."),
        others.map((r) => reviewCard(r, false)));
    }
    function reviewCard(r, own) {
      return h("div", { class: "card stack review" + (own ? " card-highlight" : "") },
        h("div", { class: "row" },
          h("div", { class: "avatar" }, (r.authorName || "?")[0].toUpperCase()),
          h("div", { class: "grow" },
            h("div", { class: "bold" }, own ? "Ваш отзыв" : r.authorName),
            h("div", { class: "tiny muted" }, Content.formatDate(r.created))),
          stars(r.rating)),
        r.text && h("p", { class: "review-text" }, r.text),
        own && h("div", { class: "row-wrap" },
          button("Изменить", () => editOwn(r), { kind: "text" }),
          button("Удалить", async () => {
            if (!(await UI.confirm("Удалить отзыв?", "Отзыв пропадёт со страницы курса. Потом можно написать новый.", "Удалить"))) return;
            try { await Api.deleteReview(r.id); toast("Отзыв удалён"); load(); } catch (e) { toast(e.message); }
          }, { kind: "text" })));
    }
    function editOwn(r) {
      const form = reviewForm(r);
      const old = box.querySelector(".review.card-highlight");
      if (old) old.replaceWith(form);
    }
    function reviewForm(r) {
      let rating = r ? r.rating : 0;
      let text = r ? r.text : "";
      const picker = h("div", { class: "star-picker", role: "radiogroup", "aria-label": "Оценка" });
      const drawPicker = () => replace(picker, [1, 2, 3, 4, 5].map((n) => h("button", {
        type: "button", class: "star" + (n <= rating ? " on" : ""), role: "radio", "aria-checked": n === rating ? "true" : "false",
        "aria-label": `${n} из 5`, onclick: () => { rating = n; replace(message); drawPicker(); },
      }, "★")));
      const message = h("div", { "aria-live": "polite" });
      drawPicker();
      const send = async () => {
        if (!rating) return replace(message, feedback("Поставьте оценку — от 1 до 5 звёзд", "error"));
        try {
          await Api.saveReview(p.id, rating, text, r && r.id);
          toast(r ? "Отзыв изменён" : "Спасибо за отзыв!");
          load();
        } catch (e) {
          replace(message, feedback(e.message || "Не получилось", "error"));
        }
      };
      return h("div", { class: "card stack review-form" },
        h("div", { class: "card-title" }, r ? "Изменить отзыв" : "Оставьте отзыв о курсе"),
        h("p", { class: "small muted" }, "Вы купили этот курс, поэтому можете рассказать о нём другим."),
        picker,
        field({ label: "Ваш отзыв", multiline: true, rows: 4, value: text, placeholder: "Что понравилось, что можно сделать лучше", oninput: (v) => { text = v; } }),
        message,
        h("div", { class: "row-wrap" },
          button(r ? "Сохранить" : "Отправить отзыв", send),
          r && button("Отмена", load, { kind: "text" })));
    }
    replace(box, sectionLabel("Отзывы"), loading());
    load().catch(() => replace(box, sectionLabel("Отзывы"), h("p", { class: "small muted" }, "Не получилось загрузить отзывы.")));
    return box;
  }

  /** Товары корзины вместе с данными с сервера. Убирает из корзины то, что больше не продаётся. */
  async function cartItems() {
    const items = Api.cart.get();
    const [products, list] = await Promise.all([Api.products(), teachers()]);
    const byId = new Map(products.map((p) => [p.id, p]));
    const valid = items.filter((it) => byId.has(it.product) && it.teacher);
    if (valid.length !== items.length) Api.cart.set(valid);
    return valid.map((it, index) => ({ it, index, p: byId.get(it.product), price: planPrice(byId.get(it.product), it.days), list }));
  }

  // ======================= Корзина =======================
  function cart() {
    const body = Screens.asyncBlock(cartItems, (rows) => {
      if (!rows.length) {
        return [emptyBox("🛒", "В корзине пока пусто"), h("a", { class: "btn btn-filled btn-block", href: "#/shop" }, h("span", null, "Перейти в магазин"))];
      }
      const total = rows.reduce((sum, r) => sum + r.price, 0);
      return [
        rows.map(({ it, index, p, price, list }) => h("div", { class: "card shop-cart-row" },
          h("img", { class: "shop-thumb", src: Api.productImage(p, "300x300"), alt: "" }),
          h("div", { class: "grow" },
            h("div", { class: "bold" }, p.title),
            h("div", { class: "small muted" }, optionsText(it, list)),
            h("div", { class: "shop-price" }, rub(price))),
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
    return h("div", { class: "screen shop" }, header("shop"), h("h2", { class: "home-title" }, "Корзина"), body, footer());
  }

  // ======================= Оформление и оплата =======================
  function checkout() {
    const user = Api.state.user;
    if (!user) {
      // Без входа: курс открывается в аккаунте ученика, поэтому сначала вход или регистрация. Корзина сохранится.
      return h("div", { class: "screen shop" }, header("shop/cart"),
        h("div", { class: "card stack center" },
          h("div", { class: "congrats" }, "👤"),
          h("div", { class: "home-title" }, "Войдите, чтобы оплатить"),
          h("p", { class: "small" }, "Курс откроется в вашем аккаунте ученика на сайте и в приложении. Корзина сохранится."),
          button("Войти или зарегистрироваться", () => { Api.storage.set("afterLogin", "shop/checkout"); App.go(""); }, { cls: "btn-block" })),
        footer());
    }
    if (user.isTeacher) {
      return h("div", { class: "screen shop" }, header("shop/cart"),
        emptyBox("👩‍🏫", "Курсы покупают ученики. Чтобы проверить покупку, войдите в аккаунт ученика."), footer());
    }
    let email = Api.storage.get("beemei_email", "");
    let phone = Api.storage.get("beemei_phone", "");
    const body = Screens.asyncBlock(cartItems, (rows) => {
      if (!rows.length) return [emptyBox("🛒", "В корзине пока пусто"), h("a", { class: "btn btn-text", href: "#/shop" }, "Перейти в магазин")];
      const subtotal = rows.reduce((sum, r) => sum + r.price, 0);
      const items = rows.map((x) => x.it);
      const agree = h("input", { type: "checkbox" });
      const message = h("div", { "aria-live": "polite" });
      // Поставили галочку — убираем напоминание о ней.
      agree.addEventListener("change", () => { if (agree.checked) replace(message); });
      // Промокод: сервер проверяет его и считает скидку (и ещё раз — при оплате).
      let promoCode = Api.storage.get("beemei_promo", "");
      let promo = null;
      const promoMsg = h("div", { "aria-live": "polite" });
      const totals = h("div", { class: "stack" });
      const total = () => (promo ? promo.total : subtotal);
      const payText = () => `Оплатить ${rub(total())}`;
      const payBtn = button(payText(), pay, { cls: "btn-block btn-tall" });
      const renderTotals = () => {
        replace(totals,
          promo && h("div", { class: "row small" }, h("span", { class: "grow muted" }, "Без скидки"), h("s", { class: "muted" }, rub(subtotal))),
          promo && h("div", { class: "row small ok bold" }, h("span", { class: "grow" }, `Промокод ${promo.code}`), h("span", null, "−" + rub(promo.discount))),
          h("div", { class: "row shop-total" }, h("span", { class: "grow" }, "Итого"), h("b", null, rub(total()))));
        if (!payBtn.disabled) replace(payBtn, h("span", null, payText()));
      };
      async function applyPromo() {
        replace(promoMsg);
        const code = promoCode.trim();
        if (!code) { promo = null; renderTotals(); return true; }
        try {
          promo = await Api.promo(code, items);
          Api.storage.set("beemei_promo", promo.code);
          replace(promoMsg, feedback(`${promo.text}: −${rub(promo.discount)}`, "success"));
          renderTotals();
          return true;
        } catch (e) {
          promo = null;
          Api.storage.remove("beemei_promo");
          replace(promoMsg, feedback(e.message || "Промокод не подошёл", "error"));
          renderTotals();
          return false;
        }
      }
      const promoField = field({
        label: "Промокод", value: promoCode, placeholder: "Если есть", autocapitalize: "characters", autocomplete: "off",
        oninput: (v) => { promoCode = v.toUpperCase(); promoField.input.value = promoCode; if (promo && promo.code !== promoCode.replace(/\s+/g, "")) { promo = null; replace(promoMsg); renderTotals(); } },
        onenter: applyPromo,
      });
      renderTotals();
      if (promoCode) applyPromo();
      async function pay() {
        if (!agree.checked) return replace(message, feedback("Подтвердите согласие с условиями покупки", "error"));
        // Вписали промокод, но не нажали «Применить» — проверяем сейчас.
        if (promoCode.trim() && !promo && !(await applyPromo())) return replace(message, feedback("Промокод не подошёл — исправьте его или сотрите", "error"));
        payBtn.disabled = true;
        replace(payBtn, h("span", { class: "spinner spinner-sm" }), h("span", null, "Переходим к оплате…"));
        try {
          const r = await Api.checkout(items, email.trim(), phone.trim(), promo ? promo.code : "");
          Api.storage.remove("beemei_promo");
          Api.storage.set("beemei_email", email.trim());
          Api.storage.set("beemei_phone", phone.trim());
          location.href = r.url; // страница оплаты ЮKassa, после неё — обратно на страницу заказа
        } catch (e) {
          replace(message, feedback(e.message || "Не получилось", "error"));
          payBtn.disabled = false;
          replace(payBtn, h("span", null, payText()));
        }
      }
      return [
        h("div", { class: "card stack" },
          h("div", { class: "card-title" }, "Ваш заказ"),
          rows.map(({ it, p, price, list }) => h("div", { class: "row" },
            h("div", { class: "grow" }, h("div", null, p.title), h("div", { class: "tiny muted" }, optionsText(it, list))),
            h("b", null, rub(price)))),
          totals),
        h("div", { class: "card stack" },
          h("div", { class: "card-title" }, "Промокод"),
          h("div", { class: "promo-row" }, promoField, button("Применить", applyPromo, { kind: "outline" })),
          promoMsg),
        h("div", { class: "card stack" },
          h("div", { class: "card-title" }, "Куда прислать чек"),
          field({ label: "Электронная почта", type: "email", value: email, autocomplete: "email", autocapitalize: "none", oninput: (v) => { email = v; } }),
          field({ label: "Телефон", type: "tel", value: phone, autocomplete: "tel", inputmode: "tel", oninput: (v) => { phone = v; } }),
          h("p", { class: "tiny muted" }, "Достаточно почты или телефона.")),
        h("div", { class: "card stack" },
          h("div", { class: "card-title" }, "Оплата"),
          h("p", { class: "small" }, "Оплата через ЮKassa: СБП, банковская карта или SberPay. После оплаты вы вернётесь на сайт, и курс откроется сразу."),
          h("label", { class: "check-row" }, agree,
            h("span", null, "Я согласен с ", h("a", { href: "#/shop/offer", target: "_blank", rel: "noopener" }, "условиями покупки (публичной офертой)"))),
          message,
          payBtn),
      ];
    });
    return h("div", { class: "screen shop" }, header("shop/cart"), h("h2", { class: "home-title" }, "Оформление заказа"), body, footer());
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
        o.discount > 0 && h("div", { class: "row small ok bold" }, h("span", { class: "grow" }, `Промокод ${o.promo}`), h("span", null, "−" + rub(o.discount))),
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

  // ======================= Мои покупки =======================
  const STATUS = {
    paid: ["Оплачен", "pill-ok"],
    pending: ["Ждёт оплаты", "pill-wait"],
    canceled: ["Отменён", "pill-bad"],
  };
  function myOrders() {
    const body = Screens.asyncBlock(() => Promise.all([Api.myOrders(), Api.enrollments().catch(() => [])]), ([orders, ens]) => {
      if (!orders.length) {
        return [emptyBox("🛍️", "Покупок пока нет. Курсы подготовки к ОГЭ и ЕГЭ — в магазине beemei."),
          h("a", { class: "btn btn-filled btn-block", href: "#/shop" }, h("span", null, "Перейти в магазин"))];
      }
      return orders.map((o) => {
        const [label, cls] = STATUS[o.status] || [o.status, "pill-muted"];
        const items = Array.isArray(o.items) ? o.items : [];
        const course = (it) => ens.find((en) => en.product === it.product && en.teacher === it.teacher && en.subject === it.subject && en.exam === it.exam);
        return h("div", { class: "card stack" },
          h("div", { class: "row" },
            h("div", { class: "grow" },
              h("div", { class: "card-title" }, "Заказ от " + new Date(Content.timeOf(o.created)).toLocaleDateString("ru-RU", { day: "numeric", month: "long", year: "numeric" })),
              h("div", { class: "tiny muted" }, "Номер заказа: " + o.id)),
            h("span", { class: "pill " + cls }, label)),
          items.map((it) => {
            const en = o.status === "paid" ? course(it) : null;
            return h("div", { class: "row" },
              h("div", { class: "grow" },
                h("div", null, it.title),
                h("div", { class: "tiny muted" }, optionsText(it)),
                en && h("div", { class: "tiny" + (isLive(en) ? "" : " err") }, (isLive(en) ? "Курс открыт " : "Курс ") + untilText(en))),
              h("b", null, rub(it.price)));
          }),
          o.discount > 0 && h("div", { class: "row small ok bold" }, h("span", { class: "grow" }, `Промокод ${o.promo}`), h("span", null, "−" + rub(o.discount))),
          h("div", { class: "row shop-total" }, h("span", { class: "grow" }, "Итого"), h("b", null, rub(o.total))),
          o.status === "pending" && h("a", { class: "btn btn-outline btn-block", href: `#/shop/order/${o.id}` }, h("span", null, "Проверить оплату")),
          o.status === "paid" && items[0] && h("a", { class: "btn btn-text", href: `#/shop/product/${items[0].product}` }, "Продлить или купить ещё"));
      });
    });
    return h("div", { class: "screen shop" }, topBar("Мои покупки", () => App.back("cabinet")), body,
      h("p", { class: "tiny muted" }, "Чек приходит на почту или телефон, указанные при покупке. Вопросы по заказу — на почту продавца, указав номер заказа."),
      footer());
  }

  // ======================= О продавце, оплата, возврат и оферта (открыты всем) =======================
  /** Условия возврата по умолчанию, если продавец не написал свои (Collections → shop_info → refundPolicy). */
  const DEFAULT_REFUND = "Если вы передумали до того, как учитель выдал первое задание, мы вернём деньги полностью. " +
    "Если курс уже начался, вернём деньги за оставшиеся дни подписки. Чтобы вернуть деньги, напишите на почту продавца " +
    "с номером заказа — деньги вернутся тем же способом, которым вы платили, обычно в течение 10 рабочих дней.";
  const val = (v) => v || "—";

  function info() {
    const body = Screens.asyncBlock(() => Api.shopInfo(), (i) => [
      h("div", { class: "card stack" },
        h("div", { class: "card-title" }, "Продавец"),
        h("p", null, h("b", null, val(i.sellerName)), h("br"), "Плательщик налога на профессиональный доход (самозанятый)", h("br"), "ИНН: " + val(i.inn)),
        h("p", null, "Телефон: ", i.phone ? h("a", { href: "tel:" + i.phone.replace(/[^\d+]/g, "") }, i.phone) : "—", h("br"),
          "Почта: ", i.email ? h("a", { href: "mailto:" + i.email }, i.email) : "—", h("br"),
          "Почтовый адрес: " + val(i.address))),
      h("div", { class: "card stack" },
        h("div", { class: "card-title" }, "Что мы продаём"),
        h("p", null, "Личные онлайн-курсы подготовки к ОГЭ и ЕГЭ по химии и биологии с учителем. Цены указаны в рублях на странице каждого курса, " +
          "срок подписки выбирается при покупке.")),
      howItWorks(),
      h("div", { class: "card stack" },
        h("div", { class: "card-title" }, "Оплата"),
        h("p", null, "Оплата онлайн через сервис ЮKassa: СБП, банковская карта, SberPay. Данные карты вводятся на защищённой странице ЮKassa — " +
          "мы их не получаем. Чек приходит на почту или телефон, указанные при покупке.")),
      h("div", { class: "card stack" },
        h("div", { class: "card-title" }, "Возврат денег"),
        h("p", { class: "shop-description" }, i.refundPolicy || DEFAULT_REFUND)),
      h("div", { class: "card stack" },
        h("div", { class: "card-title" }, "Документы"),
        h("a", { href: "#/shop/offer" }, "Публичная оферта"),
        h("a", { href: "privacy.html" }, "Политика конфиденциальности")),
    ]);
    return h("div", { class: "screen shop" }, header("shop"), h("h2", { class: "home-title" }, "О продавце и контакты"), body);
  }

  function offer() {
    const body = Screens.asyncBlock(() => Api.shopInfo(), (i) => [
      h("p", { class: "muted small" }, "Магазин beemei на сайте урокискириллом.рф" +
        (i.updated ? " · редакция от " + new Date(Content.timeOf(i.updated)).toLocaleDateString("ru-RU") : "")),
      h("div", { class: "card stack" },
        h("p", null, h("b", null, "Исполнитель: "), `${val(i.sellerName)}, плательщик налога на профессиональный доход (самозанятый), ИНН ${val(i.inn)}. ` +
          `Телефон: ${val(i.phone)}. Почта: ${val(i.email)}. Почтовый адрес: ${val(i.address)}.`),
        h("p", null, "Этот документ — предложение (публичная оферта) заключить договор на оказание услуг онлайн-обучения на условиях ниже. " +
          "Оплачивая курс, покупатель принимает эти условия.")),
      h("h3", { class: "section-title" }, "1. Предмет договора"),
      h("div", { class: "card stack" },
        h("p", null, "Исполнитель проводит для покупателя личный онлайн-курс подготовки к ОГЭ или ЕГЭ по выбранному предмету (химия или биология) " +
          "с выбранным учителем: выдаёт персональные практики и тренировочные задания на сайте урокискириллом.рф и в приложении «Простая химия», " +
          "проверяет работы с оценкой и комментарием и отвечает на вопросы в чате курса.")),
      h("h3", { class: "section-title" }, "2. Срок и доступ"),
      h("div", { class: "card stack" },
        h("p", null, "Курс оплачивается на выбранный при покупке срок (например, 2, 7, 14 или 30 дней). Доступ открывается в аккаунте покупателя " +
          "сразу после подтверждения оплаты и действует до конца оплаченного срока. Повторная оплата того же курса у того же учителя продлевает срок.")),
      h("h3", { class: "section-title" }, "3. Стоимость и оплата"),
      h("div", { class: "card stack" },
        h("p", null, "Стоимость указана в рублях на странице курса и зависит от срока. Оплата — через сервис ЮKassa (СБП, банковская карта, SberPay). " +
          "Чек направляется на указанную при покупке почту или телефон.")),
      h("h3", { class: "section-title" }, "4. Возврат денег"),
      h("div", { class: "card stack" }, h("p", { class: "shop-description" }, i.refundPolicy || DEFAULT_REFUND)),
      h("h3", { class: "section-title" }, "5. Прочие условия"),
      h("div", { class: "card stack" },
        h("p", null, "Персональные данные обрабатываются по ", h("a", { href: "privacy.html" }, "политике конфиденциальности"), ". " +
          "Если покупатель младше 18 лет, покупку совершает или одобряет родитель (законный представитель). " +
          "Вопросы и претензии принимаются по почте и телефону исполнителя.")),
    ]);
    return h("div", { class: "screen shop" }, header("shop"), h("h2", { class: "home-title" }, "Публичная оферта"), body, footer());
  }

  // ======================= «Подготовка» у ученика =======================
  /** Купленные курсы ученика на главной (запоминаем, чтобы при перерисовке не мигало). */
  let myCourses = [];
  function homeCourses() {
    const box = h("div", { class: "stack" });
    const render = () => replace(box, myCourses.length > 0 && [
      sectionLabel("Подготовка"),
      myCourses.map((en) => h("a", { class: "card card-link" + (isLive(en) ? " card-highlight" : ""), href: isLive(en) ? `#/course/${en.id}` : `#/shop/product/${en.product}` },
        emojiBadge(subjectEmoji(en.subject), "c-primary"),
        h("div", { class: "grow" },
          h("div", { class: "card-title" }, `Подготовка к ${en.exam} · ${subjectTitle(en.subject)}`),
          h("div", { class: "card-sub muted" }, isLive(en)
            ? ["Личный курс", en.teacherName && "учитель " + en.teacherName, untilText(en)].filter(Boolean).join(" · ")
            : "Курс закончился — нажмите, чтобы продлить"),
          endingSoon(en) && h("div", { class: "tiny err bold" }, `⏳ Осталось ${leftText(en)} — продлите, чтобы не потерять доступ`)),
        icon("chevron"))),
    ]);
    render();
    Promise.all([Api.enrollments(), teachers().catch(() => [])]).then(([list, all]) => {
      // Закончившиеся курсы показываем ещё 30 дней — чтобы было откуда продлить.
      myCourses = list.filter((x) => x.active && expiresOf(x) > Date.now() - 30 * 24 * 3600 * 1000)
        .map((x) => Object.assign(x, { teacherName: (x.expand && x.expand.teacher && x.expand.teacher.name) || teacherName(all, x.teacher) }));
      render();
    }, () => {});
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
      const [en, all] = await Promise.all([Api.enrollment(params.id), teachers().catch(() => []), Api.refreshContent(), Api.refreshMySubmissions()]);
      return { en, all };
    }, ({ en, all }) => {
      const teacher = (en.expand && en.expand.teacher) || (teacherName(all, en.teacher) ? { name: teacherName(all, en.teacher) } : null);
      const list = content.coursePractices(en.id).filter((p) => p.published);
      if (!isLive(en)) {
        return [
          emptyBox("⏳", `Курс «Подготовка к ${en.exam} · ${subjectTitle(en.subject)}» закончился. Продлите его, и задания с чатом снова откроются.`),
          h("a", { class: "btn btn-filled btn-block", href: `#/shop/product/${en.product}` }, h("span", null, "Продлить курс")),
        ];
      }
      return [
        h("div", { class: "card row card-highlight" },
          emojiBadge(subjectEmoji(en.subject), "c-primary"),
          h("div", { class: "grow" },
            h("div", { class: "card-title" }, `Подготовка к ${en.exam} · ${subjectTitle(en.subject)}`),
            h("div", { class: "card-sub muted" }, ["Личный курс", teacher && "учитель " + teacher.name, untilText(en)].filter(Boolean).join(" · ")))),
        endingSoon(en) && h("div", { class: "card stack renew-banner" },
          h("div", { class: "bold" }, `⏳ Курс закончится через ${leftText(en)}`),
          h("p", { class: "small" }, "Продлите его, чтобы задания и чат с учителем не закрылись. Новый срок прибавится к оставшимся дням."),
          h("a", { class: "btn btn-filled btn-block", href: `#/shop/product/${en.product}` }, h("span", null, "Продлить курс"))),
        callButtons(en, teacher ? "учителю" : ""),
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

  /** Звонок в курсе: голосом или с видео. Открывается страница звонка call.html. */
  function callButtons(en, whom) {
    const go = (video) => { location.href = Api.callUrl({ e: en.id, video: video ? 1 : "" }); };
    return h("div", { class: "call-buttons" },
      button(`📞 Позвонить${whom ? " " + whom : ""}`, () => go(false), { kind: "outline" }),
      button("🎥 Видеозвонок", () => go(true), { kind: "outline" }));
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
              h("div", { class: "card-sub" }, [en.exam, subjectTitle(en.subject), untilText(en) || "без срока"].join(" · ")),
              !isLive(en) && h("div", { class: "tiny err bold" }, "Курс закончился")),
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
            h("div", { class: "tiny muted" }, [st && `логин ${st.username}`, st && st.group && `${st.group} класс`, untilText(en)].filter(Boolean).join(" · ")),
            !isLive(en) && h("div", { class: "tiny err bold" }, "Курс закончился: ученик не видит задания, пока не продлит"))),
        isLive(en) && callButtons(en, "ученику"),
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

  window.Shop = { catalog, product, cart, checkout, order, myOrders, info, offer, homeCourses, course, teacherList, teacherCourse };
})();
