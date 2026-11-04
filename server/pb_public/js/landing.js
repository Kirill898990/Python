/*
 * Страница приложения: подставляет ссылку на последнюю версию APK (её выкладывают в веб-панели
 * сервера, коллекция app_releases) и ссылку на RuStore из js/config.js.
 */
(function () {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const base = ((window.APP_CONFIG && window.APP_CONFIG.serverUrl) || location.origin).replace(/\/+$/, "");

  // RuStore
  const rustoreUrl = (window.APP_CONFIG && window.APP_CONFIG.rustoreUrl) || "";
  const store = $("rustore-button");
  if (rustoreUrl) {
    store.href = rustoreUrl;
    store.target = "_blank";
    store.rel = "noopener noreferrer";
    $("rustore-small").textContent = "Скачать в";
    $("rustore-note").textContent = "Если на телефоне есть RuStore, лучше ставить оттуда — приложение будет обновляться само.";
  } else {
    store.classList.add("soon");
  }

  const formatDate = (value) => {
    const d = new Date(String(value).replace(" ", "T"));
    return isNaN(d) ? "" : d.toLocaleDateString("ru-RU", { day: "numeric", month: "long", year: "numeric" });
  };

  async function loadRelease() {
    const apk = $("apk-button");
    try {
      const r = await fetch(`${base}/api/collections/app_releases/records?sort=-versionCode&perPage=1`);
      if (!r.ok) throw new Error(String(r.status));
      const release = (await r.json()).items[0];
      if (!release) {
        $("apk-meta").textContent = "скоро появится";
        return;
      }
      const url = `${base}/api/files/${release.collectionName}/${release.id}/${encodeURIComponent(release.apk)}?download=1`;
      apk.href = url;
      apk.setAttribute("download", "prostaya-himiya.apk");
      apk.removeAttribute("aria-disabled");
      $("apk-meta").textContent = `версия ${release.versionName}`;
      const chip = $("version-chip");
      chip.textContent = `Версия ${release.versionName}`;
      chip.hidden = false;
      // Размер файла — из заголовка ответа, если сервер его отдаёт.
      fetch(url, { method: "HEAD" }).then((h) => {
        const size = Number(h.headers.get("content-length"));
        if (size > 0) $("apk-meta").textContent = `версия ${release.versionName} · ${(size / 1048576).toFixed(1).replace(".", ",")} МБ`;
      }, () => {});
      if (release.notes) {
        $("new-version").textContent = `Версия ${release.versionName}`;
        $("new-date").textContent = formatDate(release.created);
        $("new-notes").textContent = release.notes;
        $("whats-new").hidden = false;
      }
    } catch (_) {
      $("apk-meta").textContent = "не удалось загрузить, обновите страницу";
    }
  }

  loadRelease();
})();
