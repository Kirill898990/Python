/*
 * «Простая химия» для Windows: окно с сайтом урокискириллом.рф.
 *
 * Всё содержимое (практики, оценки, чат, магазин, звонки) — это сам сайт, поэтому приложение обновляется вместе
 * с сайтом и переустанавливать его не нужно. Здесь только то, чего нет у сайта в браузере: своё окно и значок,
 * меню на русском, микрофон и камера для звонков, понятный экран без интернета.
 */
"use strict";

const { app, BrowserWindow, Menu, dialog, session, shell } = require("electron");
const fs = require("fs");
const path = require("path");

const SITE = process.env.SITE_URL || "https://xn--h1aaafbiaikhnfkx.xn--p1ai/"; // урокискириллом.рф
const ORIGIN = new URL(SITE).origin;
const TITLE = "Простая химия";
const OFFLINE = path.join(__dirname, "offline.html");

/** Ссылка на наш сайт (её открываем в приложении), а не на чужой (её — в обычном браузере). */
function isOurs(url) {
  try {
    return new URL(url).origin === ORIGIN;
  } catch (_) {
    return false;
  }
}
function isOffline(url) {
  return typeof url === "string" && url.startsWith("file:") && decodeURIComponent(url).includes("offline.html");
}
function openOutside(url) {
  if (/^(https?|mailto|tel):/i.test(url)) shell.openExternal(url).catch(() => {});
}

// Только одно окно: второй запуск просто показывает уже открытое.
const firstInstance = app.requestSingleInstanceLock();
if (!firstInstance) app.quit();

let win = null;

// ---------- Размер и положение окна запоминаем между запусками ----------
const stateFile = () => path.join(app.getPath("userData"), "window.json");
function loadState() {
  try {
    const s = JSON.parse(fs.readFileSync(stateFile(), "utf8"));
    if (s && s.width >= 400 && s.height >= 400) return s;
  } catch (_) { /* первый запуск */ }
  return { width: 1200, height: 820 };
}
function saveState() {
  if (!win || win.isDestroyed()) return;
  try {
    const b = win.getNormalBounds();
    fs.writeFileSync(stateFile(), JSON.stringify({ ...b, maximized: win.isMaximized() }));
  } catch (_) { /* не страшно */ }
}

// ---------- Разрешения: микрофон и камера для звонков — только нашему сайту ----------
const ALLOWED = new Set(["media", "notifications", "clipboard-sanitized-write", "fullscreen"]);
function setupPermissions(ses) {
  ses.setPermissionRequestHandler((wc, permission, callback, details) => {
    const url = (details && details.requestingUrl) || (wc && wc.getURL()) || "";
    callback(isOurs(url) && ALLOWED.has(permission));
  });
  // Адрес сайта здесь приходит то с «/» на конце, то без — сравниваем как адрес, а не как строку.
  ses.setPermissionCheckHandler((_wc, permission, requestingOrigin) => isOurs(requestingOrigin) && ALLOWED.has(permission));
}

// ---------- Контекстное меню: вырезать, копировать, вставить, исправить опечатку ----------
function contextMenu(wc, params) {
  const items = [];
  if (params.misspelledWord && params.dictionarySuggestions.length) {
    for (const word of params.dictionarySuggestions.slice(0, 5)) items.push({ label: word, click: () => wc.replaceMisspelling(word) });
    items.push({ type: "separator" });
  }
  if (params.linkURL) {
    items.push({ label: "Открыть ссылку в браузере", click: () => openOutside(params.linkURL) });
    items.push({ label: "Копировать ссылку", click: () => require("electron").clipboard.writeText(params.linkURL) });
    items.push({ type: "separator" });
  }
  if (params.isEditable) {
    items.push(
      { label: "Отменить", role: "undo", enabled: params.editFlags.canUndo },
      { type: "separator" },
      { label: "Вырезать", role: "cut", enabled: params.editFlags.canCut },
      { label: "Копировать", role: "copy", enabled: params.editFlags.canCopy },
      { label: "Вставить", role: "paste", enabled: params.editFlags.canPaste },
      { type: "separator" },
      { label: "Выделить всё", role: "selectAll" },
    );
  } else if (params.selectionText && params.selectionText.trim()) {
    items.push({ label: "Копировать", role: "copy" });
  }
  if (items.length && items[items.length - 1].type === "separator") items.pop();
  if (items.length) Menu.buildFromTemplate(items).popup({ window: BrowserWindow.fromWebContents(wc) || undefined });
}

/** Правила для каждой страницы приложения (главное окно и окна звонков). */
function guard(wc) {
  // Ссылки «в новой вкладке»: свои — в новом окне приложения, чужие — в браузере.
  wc.setWindowOpenHandler(({ url }) => {
    if (isOurs(url)) {
      return {
        action: "allow",
        overrideBrowserWindowOptions: { width: 1000, height: 760, autoHideMenuBar: true, backgroundColor: "#ffffff", icon: path.join(__dirname, "icon.png") },
      };
    }
    openOutside(url);
    return { action: "deny" };
  });
  // Переход на чужой сайт (например, оплата) — в обычном браузере.
  wc.on("will-navigate", (event, url) => {
    if (isOurs(url) || isOffline(url)) return;
    event.preventDefault();
    openOutside(url);
  });
  wc.on("context-menu", (_e, params) => contextMenu(wc, params));
  // Нет интернета — понятный экран с кнопкой «Повторить».
  wc.on("did-fail-load", (_e, code, _desc, url, isMainFrame) => {
    if (!isMainFrame || code === -3 /* отменено */ || isOffline(url)) return;
    wc.loadFile(OFFLINE, { query: { u: url && isOurs(url) ? url : SITE } }).catch(() => {});
  });
}

function createWindow() {
  const state = loadState();
  win = new BrowserWindow({
    ...state,
    minWidth: 400,
    minHeight: 500,
    title: TITLE,
    icon: path.join(__dirname, "icon.png"),
    backgroundColor: "#ffffff",
    autoHideMenuBar: true,
    show: false,
    webPreferences: { contextIsolation: true, sandbox: true, nodeIntegration: false, spellcheck: true },
  });
  if (state.maximized) win.maximize();
  win.once("ready-to-show", () => win.show());
  win.on("close", saveState);
  win.on("closed", () => { win = null; });
  win.loadURL(SITE);
}

app.on("web-contents-created", (_e, wc) => guard(wc));

app.on("second-instance", () => {
  if (!win) return createWindow();
  if (win.isMinimized()) win.restore();
  win.focus();
});

function menu() {
  const home = () => win && win.loadURL(SITE);
  return Menu.buildFromTemplate([
    {
      label: "Приложение",
      submenu: [
        { label: "На главную", accelerator: "Alt+Home", click: home },
        { label: "Назад", accelerator: "Alt+Left", click: () => win && win.webContents.navigationHistory.canGoBack() && win.webContents.navigationHistory.goBack() },
        { label: "Обновить страницу", accelerator: "F5", click: () => win && win.webContents.reload() },
        { label: "Обновить страницу", accelerator: "CmdOrCtrl+R", click: () => win && win.webContents.reload(), visible: false },
        { type: "separator" },
        { label: "Выход", role: "quit" },
      ],
    },
    {
      label: "Правка",
      submenu: [
        { label: "Отменить", role: "undo" },
        { label: "Повторить", role: "redo" },
        { type: "separator" },
        { label: "Вырезать", role: "cut" },
        { label: "Копировать", role: "copy" },
        { label: "Вставить", role: "paste" },
        { label: "Выделить всё", role: "selectAll" },
      ],
    },
    {
      label: "Вид",
      submenu: [
        { label: "Крупнее", role: "zoomIn", accelerator: "CmdOrCtrl+=" },
        { label: "Мельче", role: "zoomOut" },
        { label: "Обычный размер", role: "resetZoom" },
        { type: "separator" },
        { label: "Во весь экран", role: "togglefullscreen" },
      ],
    },
    {
      label: "Справка",
      submenu: [
        { label: "Открыть сайт в браузере", click: () => openOutside(win && isOurs(win.webContents.getURL()) ? win.webContents.getURL() : SITE) },
        {
          label: "О программе",
          click: () => dialog.showMessageBox(win, {
            type: "info",
            title: "О программе",
            message: `${TITLE} для Windows`,
            detail: `Версия ${app.getVersion()}\nХимия и биология для 5–11 классов.\nСайт: урокискириллом.рф`,
          }),
        },
      ],
    },
  ]);
}

if (firstInstance) app.whenReady().then(() => {
  app.setAppUserModelId("ru.prostayahimiya.desktop");
  setupPermissions(session.defaultSession);
  Menu.setApplicationMenu(menu());
  createWindow();
});

app.on("window-all-closed", () => app.quit());

module.exports = { isOurs, isOffline, ORIGIN, SITE };
