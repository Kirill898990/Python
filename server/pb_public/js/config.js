/*
 * Настройки сайта.
 *
 * serverUrl — адрес сервера PocketBase.
 *   Оставьте пустым, если сайт лежит в папке pb_public самого сервера (так и задумано):
 *   тогда сайт и сервер открываются по одному адресу, например https://урокискириллом.рф
 *   Если сайт лежит где-то ещё (например, на GitHub Pages), впишите адрес сервера:
 *   serverUrl: "https://урокискириллом.рф"
 */
window.APP_CONFIG = {
  serverUrl: "",

  /*
   * rustoreUrl — ссылка на приложение в RuStore. Пока пусто, на сайте написано «Скоро в RuStore».
   * Когда приложение опубликуют, впишите ссылку со страницы приложения в RuStore, обычно такую:
   * rustoreUrl: "https://www.rustore.ru/catalog/app/ru.prostayahimiya.app"
   */
  rustoreUrl: "",
};
