# 내 블로그 (코드)

요구사항과 설계 문서는 [my-blog-home/docs](https://github.com/my-blog-home/docs)의 `specs/001-blog-platform/`에 있습니다.
spec의 사용자 시나리오 US1~US8을 모두 구현했습니다: 이메일 인증 가입·로그인, 블로그·분류·글쓰기, 목록·검색, 댓글·좋아요, 마이페이지·비밀번호 찾기, 블로그 관리·새 댓글 표시, 조회수·방문자 통계, 태그·이미지·신고.

| 폴더 | 내용 |
|---|---|
| `backend/` | Spring Boot 3, Java 21. 모듈: `common`, `user`, `blog`, `post`, `comment`, `search`, `stats` (의존 방향: comment·stats·search → post → blog → user) |
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

## 배포

[DEPLOY.md](DEPLOY.md)를 봅니다. 배포용 jar는 `./build.sh`, 도커 이미지는 `docker build -t ylog .`로 만듭니다.

## 한 번에 켜기 (추천)

```bash
./run.sh        # DB·Redis를 켜고 화면을 빌드한 뒤 서버를 띄운다 → http://localhost:8080
./run.sh stop   # 끈다
```

가입 인증번호는 메일 대신 `.run/server.log`에 찍힙니다. Mac을 다시 켜면 `./run.sh`를 한 번 실행하면 됩니다.

## 개발할 때 따로 켜기

```bash
# 서버 (http://localhost:8080). 개발 중에는 인증번호가 메일 대신 서버 로그에 찍힙니다
cd backend && ./gradlew bootRun

# 화면 (http://localhost:5173). /api 요청은 서버로 넘어갑니다
cd frontend && npm install && npm run dev
```

## 데이터가 있는 곳

| 무엇 | 어디 |
|---|---|
| 회원, 블로그, 글, 댓글, 통계, 로그인 세션 | PostgreSQL `myblog` |
| 인증번호, 재발송 제한, 조회수 중복 방지, 댓글 5초 제한 | Redis (자동 만료) |
| 이미지 파일 | `~/.myblog/images` (설정 `blog.image.dir`). MinIO를 쓰려면 `ImageStorage`의 S3 구현을 더한다 |

## 테스트

```bash
cd backend && ./gradlew test     # 통합 테스트: 로컬 PostgreSQL(myblog_test)과 Redis 15번 DB 사용
cd frontend && npm test          # 비밀번호 규칙, 스크립트 차단
```

서버 통합 테스트 55개는 헌법이 자동 테스트를 요구한 네 가지(권한, 비공개 글 노출, 인증번호·로그인 잠금, 계정 존재 비노출)와 댓글·좋아요·태그·이미지·신고·관리·통계 규칙을 확인합니다.
