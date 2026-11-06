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
#   3. PocketBase сам получает бесплатный сертификат https для домена,
#      а если на сервере стоит Caddy (его ставит мессенджер beemes) — сайт работает за ним, https получает Caddy;
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
# Точная версия кода (номер коммита). Её передаёт автообновление из GitHub, чтобы поставить ровно то, что слили.
REF="${REF:-}"
DIR="${DIR:-/opt/pocketbase}"
SYSTEMD_DIR="${SYSTEMD_DIR:-/etc/systemd/system}"
CADDY_DIR="${CADDY_DIR:-/etc/caddy}"
TURN_CONF="${TURN_CONF:-/etc/turnserver.conf}"

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

if [ -n "$REF" ]; then
  say "Скачиваю сайт и базу с GitHub (версия ${REF:0:7})"
  curl -fsSL -o "$TMP/src.zip" "https://github.com/${REPO}/archive/${REF}.zip"
else
  say "Скачиваю сайт и базу с GitHub (ветка $BRANCH)"
  curl -fsSL -o "$TMP/src.zip" "https://github.com/${REPO}/archive/refs/heads/${BRANCH}.zip"
fi
unzip -q "$TMP/src.zip" -d "$TMP/src"
SRC="$(find "$TMP/src" -mindepth 2 -maxdepth 2 -type d -name server | head -n 1)"
[ -n "$SRC" ] && [ -d "$SRC/pb_migrations" ] && [ -d "$SRC/pb_public" ] && [ -d "$SRC/pb_hooks" ] || fail "в архиве не найдена папка server"

# Останавливаем сервер на время обновления (при первой установке его ещё нет).
systemctl stop pocketbase 2>/dev/null || true

install -m 755 "$TMP/pb/pocketbase" "$DIR/pocketbase"
mkdir -p "$DIR/pb_migrations"
cp "$SRC"/pb_migrations/*.js "$DIR/pb_migrations/"
rm -rf "$DIR/pb_public" "$DIR/pb_hooks"
cp -r "$SRC/pb_public" "$DIR/pb_public"
cp -r "$SRC/pb_hooks" "$DIR/pb_hooks"   # проверка ответов на сервере

# Первая установка или обновление? Базу при обновлении не трогаем.
FRESH_INSTALL=0
[ -d "$DIR/pb_data" ] || FRESH_INSTALL=1

say "Применяю структуру базы"
"$DIR/pocketbase" migrate up --dir "$DIR/pb_data" --migrationsDir "$DIR/pb_migrations" --hooksDir "$DIR/pb_hooks" >/dev/null

if [ -n "$ADMIN_EMAIL" ]; then
  say "Создаю администратора $ADMIN_EMAIL"
  "$DIR/pocketbase" superuser upsert "$ADMIN_EMAIL" "$ADMIN_PASSWORD" --dir "$DIR/pb_data" >/dev/null
fi

# На сервере есть Caddy с папкой сайтов (её создаёт установка beemes)? Тогда порты 80 и 443 держит Caddy,
# а сайт слушает только 127.0.0.1:8090 — снаружи к нему можно попасть только через Caddy.
BEHIND_CADDY=0
if [ -d "$CADDY_DIR/sites" ] && command -v caddy >/dev/null 2>&1; then BEHIND_CADDY=1; fi
if [ "$BEHIND_CADDY" = 1 ]; then LISTEN="--http 127.0.0.1:8090"; else LISTEN="$DOMAIN www.$DOMAIN"; fi

say "Настраиваю автозапуск"
cat > "$SYSTEMD_DIR/pocketbase.service" <<EOF
[Unit]
Description=PocketBase для приложения «Простая химия»
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=root
WorkingDirectory=$DIR
EnvironmentFile=-$DIR/server.env
ExecStart=$DIR/pocketbase serve $LISTEN --dir $DIR/pb_data --migrationsDir $DIR/pb_migrations --hooksDir $DIR/pb_hooks --publicDir $DIR/pb_public
Restart=always
RestartSec=5
LimitNOFILE=4096

[Install]
WantedBy=multi-user.target
EOF

# Настройки сервера, которые не хранятся в базе (секрет для звонков, адрес сайта для писем). Видны только root.
ENV_FILE="$DIR/server.env"
touch "$ENV_FILE"
chmod 600 "$ENV_FILE"
set_env() {
  { grep -v "^$1=" "$ENV_FILE" || true; echo "$1=$2"; } > "$ENV_FILE.tmp"
  mv "$ENV_FILE.tmp" "$ENV_FILE"
  chmod 600 "$ENV_FILE"
}
set_env SITE_URL "https://$DOMAIN"
set_env BEHIND_PROXY "$BEHIND_CADDY"

UFW_ON=0
if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q "Status: active"; then UFW_ON=1; fi

# Звонки в курсе: свой TURN-сервер (coturn). Он нужен, когда телефоны не могут соединиться напрямую
# (так часто бывает в мобильном интернете). Голос и видео тогда идут через этот сервер. Отключить: TURN=0.
if [ "${TURN:-1}" != "0" ]; then
  say "Настраиваю сервер для звонков (coturn)"
  if apt-get install -y -qq coturn >/dev/null 2>&1; then
    TURN_SECRET="$(grep '^TURN_SECRET=' "$ENV_FILE" | cut -d= -f2- || true)"
    # Если TURN-сервер уже настроил beemes — берём его секрет, чтобы звонки работали в обоих.
    [ -n "$TURN_SECRET" ] || TURN_SECRET="$(sed -n 's/^static-auth-secret=//p' "$TURN_CONF" 2>/dev/null | head -n 1 || true)"
    [ -n "$TURN_SECRET" ] || TURN_SECRET="$(head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n')"
    set_env TURN_SECRET "$TURN_SECRET"
    set_env TURN_HOST "$DOMAIN"
    # Если у сервера внутренний адрес за NAT, coturn должен знать внешний.
    LOCAL_IP="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for (i = 1; i < NF; i++) if ($i == "src") print $(i + 1)}' || true)"
    PUBLIC_IP="$(getent ahostsv4 "$DOMAIN" 2>/dev/null | awk 'NR == 1 {print $1}' || true)"
    EXTERNAL=""
    case "$LOCAL_IP" in
      10.* | 192.168.* | 172.1[6-9].* | 172.2[0-9].* | 172.3[01].*)
        if [ -n "$PUBLIC_IP" ] && [ "$PUBLIC_IP" != "$LOCAL_IP" ]; then EXTERNAL="external-ip=$PUBLIC_IP/$LOCAL_IP"; fi ;;
    esac
    cat > "$TURN_CONF" <<TURNCONF
# Создано install.sh для звонков в курсе. Пароли проверяются по секрету из $ENV_FILE.
listening-port=3478
fingerprint
use-auth-secret
static-auth-secret=$TURN_SECRET
realm=$DOMAIN
min-port=49160
max-port=49250
total-quota=60
no-cli
no-tls
no-dtls
no-multicast-peers
denied-peer-ip=0.0.0.0-0.255.255.255
denied-peer-ip=10.0.0.0-10.255.255.255
denied-peer-ip=100.64.0.0-100.127.255.255
denied-peer-ip=127.0.0.0-127.255.255.255
denied-peer-ip=169.254.0.0-169.254.255.255
denied-peer-ip=172.16.0.0-172.31.255.255
denied-peer-ip=192.168.0.0-192.168.255.255
denied-peer-ip=::1
syslog
$EXTERNAL
TURNCONF
    chmod 640 "$TURN_CONF"
    chgrp turnserver "$TURN_CONF" 2>/dev/null || true
    if [ -f /etc/default/coturn ]; then sed -i 's/^#*TURNSERVER_ENABLED=.*/TURNSERVER_ENABLED=1/' /etc/default/coturn; fi
    systemctl enable coturn >/dev/null 2>&1 || true
    systemctl restart coturn 2>/dev/null || echo "  Сервер для звонков не запустился: звонки будут работать, только если телефоны соединятся напрямую."
    if [ "$UFW_ON" = 1 ]; then
      ufw allow 3478 >/dev/null
      ufw allow 49160:49250/udp >/dev/null
    fi
  else
    echo "  Не получилось установить coturn: звонки будут работать, только если телефоны соединятся напрямую."
  fi
fi

# Защита входа на сам сервер (SSH) от подбора пароля: fail2ban на время блокирует адреса,
# с которых много раз ввели неверный пароль. Работает сам, настраивать ничего не нужно.
if ! command -v fail2ban-client >/dev/null 2>&1; then
  say "Ставлю защиту входа на сервер (fail2ban)"
  apt-get install -y -qq fail2ban >/dev/null 2>&1 || echo "  Не получилось установить fail2ban — сайт это не затрагивает."
fi
if command -v fail2ban-client >/dev/null 2>&1; then
  systemctl enable fail2ban >/dev/null 2>&1 || true
  systemctl restart fail2ban >/dev/null 2>&1 || true
fi

# Открываем порты 80 и 443, если на сервере включён брандмауэр.
if [ "$UFW_ON" = 1 ]; then
  ufw allow 80/tcp >/dev/null
  ufw allow 443/tcp >/dev/null
fi

systemctl daemon-reload
systemctl enable pocketbase >/dev/null 2>&1
systemctl restart pocketbase
sleep 3
systemctl is-active --quiet pocketbase || fail "сервер не запустился. Подробности: journalctl -u pocketbase -n 50"

if [ "$BEHIND_CADDY" = 1 ]; then
  say "Добавляю сайт в Caddy (https)"
  cat > "$CADDY_DIR/sites/school.caddy" <<EOF
# Сайт «Простая химия» (создано server/install.sh). PocketBase работает на 127.0.0.1:8090.
$DOMAIN, www.$DOMAIN {
	@compress not path /api/realtime
	encode @compress zstd gzip
	reverse_proxy 127.0.0.1:8090
}
EOF
  caddy validate --config "$CADDY_DIR/Caddyfile" --adapter caddyfile >/dev/null 2>&1 || fail "ошибка в настройках Caddy: caddy validate --config $CADDY_DIR/Caddyfile"
  systemctl reload caddy 2>/dev/null || systemctl restart caddy 2>/dev/null || true
fi

# Проверяем, что домен уже указывает на этот сервер.
# «|| true»: если домен ещё не настроен, проверка просто ничего не найдёт, а скрипт не остановится.
SERVER_IP="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for (i = 1; i < NF; i++) if ($i == "src") print $(i + 1)}' || true)"
DOMAIN_IP="$(getent ahostsv4 "$DOMAIN" 2>/dev/null | awk 'NR == 1 {print $1}' || true)"

say "Готово! Сервер работает и будет запускаться сам."
echo "  Сайт:                  https://$DOMAIN_INPUT/"
echo "  Панель администратора: https://$DOMAIN_INPUT/_/"
echo "  Журнал сервера:        journalctl -u pocketbase -f"
if [ -z "$ADMIN_EMAIL" ] && [ "$FRESH_INSTALL" = 1 ]; then
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
