# 내 블로그 (코드)

요구사항과 설계 문서는 [my-blog-home/docs](https://github.com/my-blog-home/docs)의 `specs/001-blog-platform/`에 있습니다.
지금 들어 있는 범위는 **MVP(US1~US3)**: 이메일 인증 가입·로그인, 블로그·분류·글쓰기, 목록·검색입니다.

| 폴더 | 내용 |
|---|---|
| `backend/` | Spring Boot 3, Java 21. 모듈: `common`, `user`, `blog`, `post`, `search` |
| `frontend/` | React 18 + Vite + TypeScript |
| `docker-compose.yml` | 개발용 PostgreSQL 16, Redis 7 |

## 준비

필요한 것: Java 21, Node 20 이상, PostgreSQL 16, Redis 7

```bash
# Homebrew로 설치하는 경우
brew install openjdk@21 node postgresql@16 redis gradle
brew services start postgresql@16
brew services start redis

# DB와 계정 만들기 (개발용, 테스트용)
createuser -s myblog
psql -d postgres -c "ALTER USER myblog PASSWORD 'myblog';"
createdb -O myblog myblog
createdb -O myblog myblog_test

# Gradle wrapper 만들기 (처음 한 번)
cd backend && gradle wrapper && cd ..
```

Docker를 쓴다면 위의 PostgreSQL·Redis 설치 대신 `docker compose up -d` 후 `myblog_test` DB만 만듭니다.

## 실행

```bash
# 서버 (http://localhost:8080). 개발 중에는 인증번호가 메일 대신 서버 로그에 찍힙니다
cd backend && ./gradlew bootRun

# 화면 (http://localhost:5173). /api 요청은 서버로 넘어갑니다
cd frontend && npm install && npm run dev
```

## 테스트

```bash
cd backend && ./gradlew test     # 통합 테스트: 로컬 PostgreSQL(myblog_test)과 Redis 15번 DB 사용
cd frontend && npm test          # 비밀번호 규칙, 스크립트 차단
```

통합 테스트는 헌법이 자동 테스트를 요구한 네 가지(권한, 비공개 글 노출, 인증번호·로그인 잠금, 계정 존재 비노출)를 확인합니다.
