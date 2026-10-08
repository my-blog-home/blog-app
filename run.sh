#!/bin/bash
# 내 블로그를 이 Mac에서 켠다: http://localhost:8080
# 끄기: ./run.sh stop
set -e
cd "$(dirname "$0")"
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 PATH=/opt/homebrew/opt/openjdk@21/bin:/opt/homebrew/bin:$PATH
PID_FILE=.run/server.pid
mkdir -p .run

if [ "$1" = "stop" ]; then
  [ -f $PID_FILE ] && kill "$(cat $PID_FILE)" 2>/dev/null && echo "껐습니다" || echo "켜져 있지 않습니다"
  rm -f $PID_FILE
  exit 0
fi

if [ -f $PID_FILE ] && kill -0 "$(cat $PID_FILE)" 2>/dev/null; then
  echo "이미 켜져 있습니다: http://localhost:8080"
  exit 0
fi

brew services start postgresql@16 >/dev/null
brew services start redis >/dev/null
(cd frontend && npm install --silent --no-audit --no-fund && npm run build --silent)
(cd backend && ./gradlew bootJar -q)
nohup java -jar backend/build/libs/myblog.jar > .run/server.log 2>&1 &
echo $! > $PID_FILE
for i in $(seq 1 30); do
  if curl -s -o /dev/null http://localhost:8080/api/config/limits; then
    echo "켜졌습니다: http://localhost:8080 (로그: .run/server.log, 인증번호도 여기에 찍힙니다)"
    exit 0
  fi
  sleep 1
done
echo "30초 안에 켜지지 않았습니다. .run/server.log를 확인해 주세요"
exit 1
