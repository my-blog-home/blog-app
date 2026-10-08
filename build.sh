#!/bin/bash
# 배포용 jar 하나를 만든다: backend/build/libs/myblog.jar (화면 포함)
set -e
cd "$(dirname "$0")"
(cd frontend && npm ci --no-audit --no-fund && npm run build)
(cd backend && ./gradlew clean test bootJar)
echo "만들었습니다: backend/build/libs/myblog.jar"
