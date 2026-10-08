#!/bin/bash
# 서버에서 실행: 올라온 도커 이미지를 불러와 Ylog를 다시 띄운다.
# GitHub Actions가 SSH로 접속해 환경변수와 함께 실행한다 (.github/workflows/deploy.yml).
#
# 필요한 환경변수: DB_ADDRESS, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD
# 선택: DB_SCHEMA(public), APP_PORT(8330), MAIL_MODE(log), SHOW_CODE_ON_SCREEN(true), COOKIE_SECURE(false),
#       SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD, SMTP_FROM, SUDO_PASSWORD
set -euo pipefail

APP_DIR="${APP_DIR:-$HOME/ylog}"
IMAGE="ylog:latest"
APP="ylog-app"
REDIS="ylog-redis"
NETWORK="ylog-net"
APP_PORT="${APP_PORT:-8330}"

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

echo "▶ 이미지 불러오기"
gunzip -c ylog-image.tar.gz | d load

echo "▶ 네트워크와 Redis 준비"
d network inspect "$NETWORK" >/dev/null 2>&1 || d network create "$NETWORK"
if ! d ps -a --format '{{.Names}}' | grep -qx "$REDIS"; then
  # Redis는 바깥에 포트를 열지 않고 앱만 접속한다
  d run -d --name "$REDIS" --network "$NETWORK" --restart unless-stopped \
    -v ylog-redis-data:/data redis:7 redis-server --appendonly yes
elif [ "$(d inspect -f '{{.State.Running}}' "$REDIS")" != "true" ]; then
  d start "$REDIS"
fi

# 서버 자신에 있는 DB는 컨테이너 안에서 localhost가 아니라 호스트 주소로 접속해야 한다
DB_HOST="$DB_ADDRESS"
case "$DB_HOST" in
  localhost|127.0.0.1) DB_HOST="host.docker.internal" ;;
esac

# 공용 데이터베이스 안의 스키마를 쓰면 접속 주소에 지정한다 (테이블·Flyway 기록이 그 스키마에 생긴다)
DB_SCHEMA="${DB_SCHEMA:-public}"
DB_URL="jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME?currentSchema=$DB_SCHEMA"

echo "▶ 이전 컨테이너 정리"
d rm -f "$APP" >/dev/null 2>&1 || true

echo "▶ 새 컨테이너 실행 (포트 $APP_PORT)"
d run -d --name "$APP" --network "$NETWORK" --restart unless-stopped \
  --add-host host.docker.internal:host-gateway \
  -p "$APP_PORT:$APP_PORT" \
  -v ylog-images:/var/lib/ylog/images \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e PORT="$APP_PORT" \
  -e DB_URL="$DB_URL" \
  -e DB_SCHEMA="$DB_SCHEMA" \
  -e DB_USERNAME="$DB_USERNAME" \
  -e DB_PASSWORD="$DB_PASSWORD" \
  -e REDIS_HOST="$REDIS" \
  -e MAIL_MODE="${MAIL_MODE:-log}" \
  -e SHOW_CODE_ON_SCREEN="${SHOW_CODE_ON_SCREEN:-true}" \
  -e COOKIE_SECURE="${COOKIE_SECURE:-false}" \
  -e SMTP_HOST="${SMTP_HOST:-}" \
  -e SMTP_PORT="${SMTP_PORT:-587}" \
  -e SMTP_USERNAME="${SMTP_USERNAME:-}" \
  -e SMTP_PASSWORD="${SMTP_PASSWORD:-}" \
  -e SMTP_FROM="${SMTP_FROM:-no-reply@ylog.local}" \
  "$IMAGE"

echo "▶ 켜질 때까지 기다리기"
for i in $(seq 1 60); do
  if curl -fs "http://localhost:$APP_PORT/api/config/limits" >/dev/null 2>&1; then
    echo "✅ 배포 완료: http://<서버 주소>:$APP_PORT"
    d image prune -f >/dev/null 2>&1 || true
    rm -f ylog-image.tar.gz
    exit 0
  fi
  sleep 2
done

echo "❌ 2분 안에 켜지지 않았습니다. 최근 로그:"
d logs --tail 100 "$APP" || true
exit 1
