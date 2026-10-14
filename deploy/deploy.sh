#!/bin/bash
# 서버에서 실행: 올라온 도커 이미지로 Ylog를 무중단으로 바꿔 띄운다 (블루-그린 배포).
# GitHub Actions가 SSH로 접속해 환경변수와 함께 실행한다 (.github/workflows/deploy.yml).
#
#   바깥(포트 8330) → ylog-proxy(Nginx, 로드 밸런서) → ylog-app-blue-1·2 또는 ylog-app-green-1·2(앱, 포트를 바깥에 열지 않음)
#
# 1. 지금 쓰지 않는 색으로 새 앱들(APP_COUNT개, 기본 2)을 하나씩 띄운다. 옛 앱들은 계속 요청을 받는다.
# 2. 새 컨테이너가 응답할 때까지 기다린다. 끝내 안 켜지면 새 것만 지우고 끝낸다(사이트는 옛 버전으로 계속 동작).
# 3. Nginx upstream을 새 색의 앱들로 바꾸고 reload한다. reload는 처리 중인 요청을 끊지 않는다. 평소에는 앱들이 요청을 나눠 받는다.
# 4. 옛 컨테이너를 graceful shutdown으로 끈다(처리 중인 요청을 마치고 꺼짐).
#
# 필요한 환경변수: DB_ADDRESS, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD
# 선택: DB_DATABASE(nhnacademy), DB_SCHEMA(DB_NAME 값), APP_PORT(8330), SHOW_CODE_ON_SCREEN(false), COOKIE_SECURE(true),
#       SMTP_USERNAME, SMTP_PASSWORD(둘 다 있으면 인증 메일을 실제로 보낸다. Gmail 주소와 앱 비밀번호),
#       SMTP_HOST(smtp.gmail.com), SMTP_PORT(587), SMTP_FROM("Ylog <SMTP_USERNAME>"), MAIL_MODE(SMTP 계정이 있으면 smtp, 없으면 log), SUDO_PASSWORD,
#       ADMIN_EMAIL, ADMIN_PASSWORD(둘 다 있을 때만 처음 켤 때 관리자 계정을 만든다. 기본은 빈 값),
#       GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET, KAKAO_CLIENT_ID, KAKAO_CLIENT_SECRET(소셜 로그인. 값이 있는 제공자만 켜진다),
#       PUBLIC_URL(사이트 공개 주소. 링크 미리보기와 소셜 로그인 콜백 주소에 쓴다), APP_COUNT(색마다 앱 개수, 기본 2)
set -euo pipefail

APP_DIR="${APP_DIR:-$HOME/ylog}"
IMAGE="ylog:latest"
PROXY_IMAGE="nginx:1.27-alpine"
APP_PREFIX="ylog-app"
LEGACY_APP="ylog-app"        # 무중단 배포 전에 포트 8330을 직접 열던 컨테이너
PROXY="ylog-proxy"
REDIS="ylog-redis"
NETWORK="ylog-net"
APP_PORT="${APP_PORT:-8330}" # 바깥에서 들어오는 포트(Nginx가 연다)
INNER_PORT=8080              # 앱 컨테이너 안 포트(네트워크 안에서만 쓴다)
ACTIVE_FILE="$APP_DIR/active-color"
NGINX_DIR="$APP_DIR/nginx"

cd "$APP_DIR"

# docker 권한이 없으면 sudo로 실행한다
if docker info >/dev/null 2>&1; then
  DOCKER=(docker)
elif [ -n "${SUDO_PASSWORD:-}" ]; then
  echo "$SUDO_PASSWORD" | sudo -S -v >/dev/null 2>&1
  DOCKER=(sudo docker)
else
  DOCKER=(sudo -n docker)
fi
d() { "${DOCKER[@]}" "$@"; }
exists() { d ps -a --format '{{.Names}}' | grep -qx "$1"; }
running() { [ "$(d inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" = "true" ]; }

echo "▶ 이미지 불러오기"
gunzip -c ylog-image.tar.gz | d load
# Nginx 이미지는 서버가 인터넷에서 받지 않아도 되게 GitHub Actions가 함께 보낸다
if [ -f proxy-image.tar.gz ]; then
  d image inspect "$PROXY_IMAGE" >/dev/null 2>&1 || gunzip -c proxy-image.tar.gz | d load
fi

echo "▶ 네트워크와 Redis 준비"
d network inspect "$NETWORK" >/dev/null 2>&1 || d network create "$NETWORK"
if ! exists "$REDIS"; then
  # Redis는 바깥에 포트를 열지 않고 앱만 접속한다
  d run -d --name "$REDIS" --network "$NETWORK" --restart unless-stopped \
    -v ylog-redis-data:/data redis:7 redis-server --appendonly yes
elif ! running "$REDIS"; then
  d start "$REDIS"
fi

# 서버 자신에 있는 DB는 컨테이너 안에서 localhost가 아니라 호스트 주소로 접속해야 한다
DB_HOST="$DB_ADDRESS"
case "$DB_HOST" in
  localhost|127.0.0.1) DB_HOST="host.docker.internal" ;;
esac

# Crowfoot에서 발급한 PostgreSQL은 공용 데이터베이스(nhnacademy) 안에 내 스키마를 준다.
# 그래서 시크릿 DB_NAME에는 발급받은 스키마 이름(예: cf_u30_d1)을 넣고, 데이터베이스는 DB_DATABASE(기본 nhnacademy)로 접속한다.
# 다른 PostgreSQL을 쓰게 되면 DB_DATABASE에 데이터베이스 이름, DB_SCHEMA에 스키마 이름(보통 public)을 넣는다.
DB_DATABASE="${DB_DATABASE:-nhnacademy}"
DB_SCHEMA="${DB_SCHEMA:-$DB_NAME}"
DB_URL="jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_DATABASE?currentSchema=$DB_SCHEMA"

CURRENT="$(cat "$ACTIVE_FILE" 2>/dev/null || true)"
if [ "$CURRENT" = "blue" ]; then NEXT="green"; else NEXT="blue"; fi
# 인증 메일: SMTP 계정이 있으면 실제로 보내고, 없으면 인증번호를 서버 로그에만 찍는다
SMTP_HOST="${SMTP_HOST:-smtp.gmail.com}"
if [ -n "${SMTP_USERNAME:-}" ] && [ -n "${SMTP_PASSWORD:-}" ]; then
  MAIL_MODE="${MAIL_MODE:-smtp}"
  SMTP_FROM="${SMTP_FROM:-Ylog <$SMTP_USERNAME>}"
else
  MAIL_MODE="${MAIL_MODE:-log}"
  echo "⚠️  SMTP_USERNAME·SMTP_PASSWORD 시크릿이 없어 인증 메일을 보내지 않습니다(인증번호는 서버 로그에만 남음)"
fi

# 색마다 띄우는 앱 개수. Nginx가 이 앱들에 요청을 나눠 보낸다(로드 밸런싱). 1로 바꾸면 다시 앱 하나로 돈다
APP_COUNT="${APP_COUNT:-2}"
NEW_APPS=()
for n in $(seq 1 "$APP_COUNT"); do NEW_APPS+=("$APP_PREFIX-$NEXT-$n"); done
# 지금 서비스 중인 색의 앱들(예전 이름 ylog-app-blue 처럼 번호 없는 것도 포함)
OLD_APPS=()
if [ -n "$CURRENT" ]; then
  while read -r name; do [ -n "$name" ] && OLD_APPS+=("$name"); done \
    < <(d ps -a --format '{{.Names}}' | grep -E "^$APP_PREFIX-$CURRENT(-[0-9]+)?$" || true)
fi

run_app() {
  local name="$1"
  d rm -f "$name" >/dev/null 2>&1 || true
  # 학교 공용 서버라 앱 하나가 메모리를 너무 쓰지 않게 2GB로 묶는다(평소 약 0.7GB)
  d run -d --name "$name" --hostname "$name" --network "$NETWORK" --restart unless-stopped \
    --memory 2g -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50" \
    --add-host host.docker.internal:host-gateway \
    -v ylog-images:/var/lib/ylog/images \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e PORT="$INNER_PORT" \
    -e INSTANCE_NAME="$name" \
    -e DB_URL="$DB_URL" \
    -e DB_SCHEMA="$DB_SCHEMA" \
    -e DB_USERNAME="$DB_USERNAME" \
    -e DB_PASSWORD="$DB_PASSWORD" \
    -e REDIS_HOST="$REDIS" \
    -e MAIL_MODE="$MAIL_MODE" \
    -e SHOW_CODE_ON_SCREEN="${SHOW_CODE_ON_SCREEN:-false}" \
    -e COOKIE_SECURE="${COOKIE_SECURE:-true}" \
    -e SMTP_HOST="$SMTP_HOST" \
    -e SMTP_PORT="${SMTP_PORT:-587}" \
    -e SMTP_USERNAME="${SMTP_USERNAME:-}" \
    -e SMTP_PASSWORD="${SMTP_PASSWORD:-}" \
    -e SMTP_FROM="${SMTP_FROM:-no-reply@ylog.local}" \
    -e ADMIN_EMAIL="${ADMIN_EMAIL:-}" \
    -e ADMIN_PASSWORD="${ADMIN_PASSWORD:-}" \
    -e PUBLIC_URL="${PUBLIC_URL:-}" \
    -e GITHUB_CLIENT_ID="${GITHUB_CLIENT_ID:-}" \
    -e GITHUB_CLIENT_SECRET="${GITHUB_CLIENT_SECRET:-}" \
    -e KAKAO_CLIENT_ID="${KAKAO_CLIENT_ID:-}" \
    -e KAKAO_CLIENT_SECRET="${KAKAO_CLIENT_SECRET:-}" \
    "$IMAGE" >/dev/null
}

wait_healthy() {
  local name="$1" ip
  for i in $(seq 1 90); do
    ip="$(d inspect -f "{{(index .NetworkSettings.Networks \"$NETWORK\").IPAddress}}" "$name" 2>/dev/null || true)"
    # 서버에서 컨테이너 주소로 직접 묻고, 안 되면 Nginx 컨테이너 안에서 컨테이너 이름으로 묻는다
    if [ -n "$ip" ] && curl -fs --max-time 2 "http://$ip:$INNER_PORT/api/config/limits" >/dev/null 2>&1; then
      return 0
    fi
    if running "$PROXY" && d exec "$PROXY" wget -q -T 2 -O /dev/null "http://$name:$INNER_PORT/api/config/limits" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

echo "▶ 새 앱 ${#NEW_APPS[@]}개 실행: ${NEW_APPS[*]} (지금 서비스 중: ${OLD_APPS[*]:-$LEGACY_APP})"
# 하나씩 띄우고 응답을 확인한 뒤 다음 것을 띄운다(DB 마이그레이션·관리자 계정 만들기가 겹치지 않게)
for name in "${NEW_APPS[@]}"; do
  run_app "$name"
  if ! wait_healthy "$name"; then
    echo "❌ $name 이(가) 3분 안에 켜지지 않았습니다. 새 앱만 지우고, 사이트는 지금 버전으로 계속 동작합니다. 최근 로그:"
    d logs --tail 100 "$name" || true
    for n in "${NEW_APPS[@]}"; do d rm -f "$n" >/dev/null 2>&1 || true; done
    exit 1
  fi
  echo "  ✓ $name 응답 확인"
done

echo "▶ Nginx를 ${NEXT} 쪽 앱 ${#NEW_APPS[@]}개로 바꾸기"
UPSTREAM=""
for name in "${NEW_APPS[@]}"; do UPSTREAM+="    server $name:$INNER_PORT max_fails=3 fail_timeout=10s;"$'\n'; done
mkdir -p "$NGINX_DIR"
cat > "$NGINX_DIR/default.conf" <<CONF
# deploy.sh가 배포할 때마다 새로 쓴다. 지금 서비스 중인 앱: ${NEW_APPS[*]}
# 바깥에 또 프록시(도메인·HTTPS)가 있으면 그쪽이 보낸 원래 주소 정보를 그대로 넘기고, 없으면 이 서버 기준으로 채운다
map \$http_x_forwarded_proto \$ylog_proto {
    default \$http_x_forwarded_proto;
    ""      \$scheme;
}
# 요청을 앱들에 차례로 나눠 보낸다(라운드 로빈). 하나가 응답하지 않으면 잠시 빼고 나머지로 보낸다
upstream ylog_app {
$UPSTREAM}
server {
    listen 80;
    client_max_body_size 6m;
    location / {
        proxy_pass http://ylog_app;
        proxy_next_upstream error timeout http_502 http_503;
        proxy_http_version 1.1;
        proxy_set_header Host \$http_host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$ylog_proto;
    }
}
CONF

if exists "$PROXY" && running "$PROXY"; then
  d exec "$PROXY" nginx -t
  d exec "$PROXY" nginx -s reload
else
  # 처음 한 번: 포트 8330을 직접 열던 옛 컨테이너를 내리고 Nginx가 그 포트를 맡는다(이때만 잠깐 끊긴다)
  d rm -f "$LEGACY_APP" "$PROXY" >/dev/null 2>&1 || true
  d run -d --name "$PROXY" --network "$NETWORK" --restart unless-stopped \
    -p "$APP_PORT:80" -v "$NGINX_DIR:/etc/nginx/conf.d:ro" "$PROXY_IMAGE"
fi
echo "$NEXT" > "$ACTIVE_FILE"

echo "▶ 바깥 주소로 확인"
for i in $(seq 1 15); do
  if curl -fs --max-time 2 "http://localhost:$APP_PORT/api/config/limits" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if [ "${#OLD_APPS[@]}" -gt 0 ]; then
  echo "▶ 옛 앱 끄기: ${OLD_APPS[*]} (처리 중인 요청을 마치고 꺼짐)"
  sleep 5
  for name in "${OLD_APPS[@]}"; do
    d stop -t 30 "$name" >/dev/null || true
    d rm -f "$name" >/dev/null 2>&1 || true
  done
fi

d image prune -f >/dev/null 2>&1 || true
rm -f ylog-image.tar.gz proxy-image.tar.gz
echo "✅ 배포 완료: ${NEW_APPS[*]} (http://<서버 주소>:$APP_PORT)"
