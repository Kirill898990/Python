#!/usr/bin/env bash
#
# Установка (или обновление) сервера «Простая химия» на Ubuntu одной командой.
#
# Запуск на сервере (под пользователем root):
#   curl -fsSL https://raw.githubusercontent.com/Kirill898990/Python/refs/heads/main/server/install.sh \
#     | bash -s -- ДОМЕН ПОЧТА_АДМИНИСТРАТОРА ПАРОЛЬ_АДМИНИСТРАТОРА
#
# Что делает:
#   1. скачивает PocketBase и файлы проекта с GitHub (база, правила доступа, сайт);
#   2. настраивает автозапуск, сервер будет работать сам, даже после перезагрузки;
#   3. PocketBase сам получает бесплатный сертификат https для домена;
#   4. создаёт администратора (если указаны почта и пароль).
#
# Повторный запуск обновляет сайт и миграции. База с учениками и оценками (pb_data) не трогается.

set -euo pipefail

DOMAIN_INPUT="${1:-}"
ADMIN_EMAIL="${2:-}"
ADMIN_PASSWORD="${3:-}"
PB_VERSION="${PB_VERSION:-0.40.6}"
REPO="${REPO:-Kirill898990/Python}"
BRANCH="${BRANCH:-main}"
DIR="${DIR:-/opt/pocketbase}"

say() { printf '\n\033[1;32m==> %s\033[0m\n' "$1"; }
fail() { printf '\n\033[1;31mОшибка: %s\033[0m\n' "$1" >&2; exit 1; }

[ -n "$DOMAIN_INPUT" ] || fail "укажите домен, например: bash install.sh урокискириллом.рф admin@mail.ru Пароль123"
[ "$(id -u)" -eq 0 ] || fail "запустите от имени root (или добавьте sudo перед bash)"
if [ -n "$ADMIN_EMAIL" ] && [ "${#ADMIN_PASSWORD}" -lt 10 ]; then
  fail "пароль администратора должен быть не короче 10 символов"
fi

say "Устанавливаю нужные программы"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq curl unzip idn2 ca-certificates >/dev/null

# Русский домен → технический вид: урокискириллом.рф → xn--h1aaafbiaikhnfkx.xn--p1ai
DOMAIN="$(idn2 "$DOMAIN_INPUT")"
DOMAIN="${DOMAIN#www.}"

case "$(uname -m)" in
  x86_64) ARCH="amd64" ;;
  aarch64 | arm64) ARCH="arm64" ;;
  *) fail "неизвестный тип процессора: $(uname -m)" ;;
esac

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$DIR"

say "Скачиваю PocketBase $PB_VERSION"
curl -fsSL -o "$TMP/pb.zip" \
  "https://github.com/pocketbase/pocketbase/releases/download/v${PB_VERSION}/pocketbase_${PB_VERSION}_linux_${ARCH}.zip"
unzip -q -o "$TMP/pb.zip" pocketbase -d "$TMP/pb"

say "Скачиваю сайт и базу с GitHub (ветка $BRANCH)"
curl -fsSL -o "$TMP/src.zip" "https://github.com/${REPO}/archive/refs/heads/${BRANCH}.zip"
unzip -q "$TMP/src.zip" -d "$TMP/src"
SRC="$(find "$TMP/src" -mindepth 2 -maxdepth 2 -type d -name server | head -n 1)"
[ -n "$SRC" ] && [ -d "$SRC/pb_migrations" ] && [ -d "$SRC/pb_public" ] || fail "в архиве не найдена папка server"

# Останавливаем сервер на время обновления (при первой установке его ещё нет).
systemctl stop pocketbase 2>/dev/null || true

install -m 755 "$TMP/pb/pocketbase" "$DIR/pocketbase"
mkdir -p "$DIR/pb_migrations"
cp "$SRC"/pb_migrations/*.js "$DIR/pb_migrations/"
rm -rf "$DIR/pb_public"
cp -r "$SRC/pb_public" "$DIR/pb_public"

say "Применяю структуру базы"
"$DIR/pocketbase" migrate up --dir "$DIR/pb_data" --migrationsDir "$DIR/pb_migrations" >/dev/null

if [ -n "$ADMIN_EMAIL" ]; then
  say "Создаю администратора $ADMIN_EMAIL"
  "$DIR/pocketbase" superuser upsert "$ADMIN_EMAIL" "$ADMIN_PASSWORD" --dir "$DIR/pb_data" >/dev/null
fi

say "Настраиваю автозапуск"
cat > /etc/systemd/system/pocketbase.service <<EOF
[Unit]
Description=PocketBase для приложения «Простая химия»
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=root
WorkingDirectory=$DIR
ExecStart=$DIR/pocketbase serve $DOMAIN www.$DOMAIN --dir $DIR/pb_data --migrationsDir $DIR/pb_migrations --publicDir $DIR/pb_public
Restart=always
RestartSec=5
LimitNOFILE=4096

[Install]
WantedBy=multi-user.target
EOF

# Открываем порты 80 и 443, если на сервере включён брандмауэр.
if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q "Status: active"; then
  ufw allow 80/tcp >/dev/null
  ufw allow 443/tcp >/dev/null
fi

systemctl daemon-reload
systemctl enable pocketbase >/dev/null 2>&1
systemctl restart pocketbase
sleep 3
systemctl is-active --quiet pocketbase || fail "сервер не запустился. Подробности: journalctl -u pocketbase -n 50"

# Проверяем, что домен уже указывает на этот сервер.
# «|| true»: если домен ещё не настроен, проверка просто ничего не найдёт, а скрипт не остановится.
SERVER_IP="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for (i = 1; i < NF; i++) if ($i == "src") print $(i + 1)}' || true)"
DOMAIN_IP="$(getent ahostsv4 "$DOMAIN" 2>/dev/null | awk 'NR == 1 {print $1}' || true)"

say "Готово! Сервер работает и будет запускаться сам."
echo "  Сайт:                  https://$DOMAIN_INPUT/"
echo "  Панель администратора: https://$DOMAIN_INPUT/_/"
echo "  Журнал сервера:        journalctl -u pocketbase -f"
if [ -z "$ADMIN_EMAIL" ]; then
  echo
  echo "  Администратора создайте командой:"
  echo "  $DIR/pocketbase superuser upsert ПОЧТА ПАРОЛЬ --dir $DIR/pb_data"
fi
if [ -n "$SERVER_IP" ] && [ "$DOMAIN_IP" != "$SERVER_IP" ]; then
  echo
  printf '\033[1;33m  Внимание: домен сейчас указывает на %s, а у этого сервера адрес %s.\033[0m\n' "${DOMAIN_IP:-никуда}" "$SERVER_IP"
  echo "  Поменяйте A-запись домена на $SERVER_IP. Когда DNS обновится (от 15 минут до суток),"
  echo "  сайт и https заработают сами, ничего перезапускать не нужно."
fi
