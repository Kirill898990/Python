/*
 * Проверка правил приложения без Electron: подменяем модуль electron заглушкой и смотрим,
 * что разрешено, что открывается в браузере и что происходит без интернета.
 *   node test.js
 */
"use strict";
const Module = require("module");
const path = require("path");
const assert = require("assert");

const calls = { external: [], loadFile: [], loadURL: [], quit: 0 };
let ready;
const handlers = {};
const fakeWc = () => {
  const on = {};
  return {
    on: (name, fn) => { on[name] = fn; },
    emit: (name, ...args) => on[name] && on[name](...args),
    setWindowOpenHandler(fn) { this.open = fn; },
    loadFile: (f, opts) => { calls.loadFile.push([f, opts]); return Promise.resolve(); },
    getURL: () => "https://xn--h1aaafbiaikhnfkx.xn--p1ai/",
    replaceMisspelling() {},
  };
};
const electron = {
  app: {
    requestSingleInstanceLock: () => true,
    quit: () => { calls.quit++; },
    on: (name, fn) => { handlers[name] = fn; },
    whenReady: () => ({ then: (fn) => { ready = fn; } }),
    getPath: () => require("os").tmpdir(),
    getVersion: () => "1.0.0",
    setAppUserModelId() {},
  },
  BrowserWindow: class {
    constructor(opts) { this.opts = opts; this.webContents = fakeWc(); electron.lastWindow = this; }
    maximize() {} once() {} on() {} loadURL(u) { calls.loadURL.push(u); } isDestroyed() { return false; }
    static fromWebContents() { return null; }
  },
  Menu: { buildFromTemplate: (t) => ({ template: t, popup() {} }), setApplicationMenu(m) { electron.menu = m; } },
  dialog: { showMessageBox() {} },
  shell: { openExternal: (u) => { calls.external.push(u); return Promise.resolve(); } },
  clipboard: { writeText() {} },
  session: {
    defaultSession: {
      setPermissionRequestHandler(fn) { electron.permRequest = fn; },
      setPermissionCheckHandler(fn) { electron.permCheck = fn; },
      flushStorageData() { calls.flushed = (calls.flushed || 0) + 1; },
    },
  },
};
const origResolve = Module._resolveFilename;
Module._resolveFilename = function (request, ...rest) {
  if (request === "electron") return "electron";
  return origResolve.call(this, request, ...rest);
};
require.cache.electron = { id: "electron", filename: "electron", loaded: true, exports: electron };

const main = require(path.join(__dirname, "main.js"));
let n = 0;
const ok = (cond, msg) => { assert.ok(cond, msg); n++; console.log("OK  ", msg); };

ok(main.ORIGIN === "https://xn--h1aaafbiaikhnfkx.xn--p1ai", "сайт — урокискириллом.рф");
ok(main.isOurs("https://xn--h1aaafbiaikhnfkx.xn--p1ai/#/course/1"), "свои страницы открываются в приложении");
ok(!main.isOurs("https://yoomoney.ru/checkout"), "чужие сайты — нет");
ok(!main.isOurs("https://xn--h1aaafbiaikhnfkx.xn--p1ai.evil.ru/"), "похожий чужой адрес — нет");

ready();
ok(calls.loadURL[0] === main.SITE, "при запуске открывается сайт");
ok(electron.menu && electron.menu.template.map((m) => m.label).join(",") === "Приложение,Правка,Вид,Справка", "меню на русском");

// разрешения
const ask = (perm, url) => new Promise((res) => electron.permRequest(null, perm, res, { requestingUrl: url }));
(async () => {
  ok(await ask("media", main.SITE + "call.html") === true, "звонок: микрофон и камера разрешены нашему сайту");
  ok(await ask("media", "https://evil.example/") === false, "чужому сайту микрофон не дают");
  ok(await ask("geolocation", main.SITE) === false, "лишние разрешения (геолокация) не дают");
  ok(electron.permCheck(null, "media", main.ORIGIN) === true && electron.permCheck(null, "media", "https://evil.example") === false, "проверка разрешений тоже по адресу");
  ok(electron.permCheck(null, "media", main.ORIGIN + "/") === true, "адрес с «/» на конце — тоже наш");

  // правила для страниц
  const wc = fakeWc();
  handlers["web-contents-created"](null, wc);
  ok(wc.open({ url: main.SITE + "call.html#e=1" }).action === "allow", "окно звонка нашего сайта открывается в приложении");
  ok(wc.open({ url: "https://yoomoney.ru/pay" }).action === "deny" && calls.external.includes("https://yoomoney.ru/pay"), "оплата открывается в обычном браузере");
  let prevented = false;
  wc.emit("will-navigate", { preventDefault: () => { prevented = true; } }, "https://evil.example/");
  ok(prevented && calls.external.includes("https://evil.example/"), "переход на чужой сайт — в браузер");
  prevented = false;
  wc.emit("will-navigate", { preventDefault: () => { prevented = true; } }, main.SITE + "#/cabinet");
  ok(!prevented, "переходы по своему сайту не мешаем");
  wc.emit("did-fail-load", {}, -106, "ERR_INTERNET_DISCONNECTED", main.SITE + "#/cabinet", true);
  const [file, opts] = calls.loadFile.at(-1);
  ok(file.endsWith("offline.html") && opts.query.u === main.SITE + "#/cabinet", "без интернета — экран «Нет связи», потом вернёт туда же");
  const before = calls.loadFile.length;
  wc.emit("did-fail-load", {}, -3, "ERR_ABORTED", main.SITE, true);
  ok(calls.loadFile.length === before, "отменённая загрузка — не ошибка");
  ok(wc.open({ url: "javascript:alert(1)" }).action === "deny", "странные ссылки не открываются");
  const flushedBefore = calls.flushed || 0;
  handlers["before-quit"]();
  ok((calls.flushed || 0) - flushedBefore === 1, "перед выходом вход записывается на диск");
  console.log(`\n${n} checks, 0 fails`);
})().catch((e) => { console.error("FAIL", e.message); process.exit(1); });
