# 배포 방법

Ylog는 **jar 하나**(서버 + 화면)로 돌아갑니다. 함께 필요한 것은 PostgreSQL 16, Redis 7, 메일(SMTP) 계정, 이미지를 둘 폴더입니다.

## 1. 준비물

| 무엇 | 메모 |
|---|---|
| PostgreSQL 16 | 빈 DB와 계정 하나. 표는 서버가 처음 켜질 때 Flyway로 만든다 |
| Redis 7 | 인증번호, 조회수 중복 방지, 인기 검색어 제한에 쓴다. 바깥에 열지 않는다 |
| SMTP 계정 | Gmail(앱 비밀번호) 또는 네이버. 없으면 `MAIL_MODE=log`로 임시 운영(인증번호가 서버 로그에 찍힘) |
| 이미지 폴더 | `IMAGE_DIR`. 서버를 다시 만들어도 남는 곳(도커면 볼륨) |
| HTTPS | 로그인 쿠키가 `Secure`라서 HTTPS가 필요하다. Nginx·로드밸런서가 HTTPS를 맡고 서버(8080)로 넘긴다 |

## 2. 환경변수

`.env.prod.example`을 참고합니다. 값은 서버에만 두고 Git에 올리지 않습니다.

| 이름 | 필수 | 설명 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | O | `prod` (빠뜨리면 개발 설정으로 켜진다) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | O | 예: `jdbc:postgresql://db:5432/myblog` |
| `REDIS_HOST` | O | `REDIS_PORT`(기본 6379), `REDIS_PASSWORD`(선택) |
| `MAIL_MODE` | | `smtp`(기본) 또는 `log` |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM` | smtp일 때 O | 인증 메일 발송 |
| `SHOW_CODE_ON_SCREEN` | | 기본 `false`. `true`면 가입·비밀번호 찾기 화면에 인증번호가 보인다. 메일 없이 시험할 때만 쓰고, 켜 두면 남의 이메일로도 가입할 수 있다 |
| `IMAGE_DIR` | | 기본 `/var/lib/ylog/images` |
| `PORT` | | 기본 8080 |
| `COOKIE_SECURE` | | 기본 `true`. HTTP로 잠깐 시험할 때만 `false` |

## 3-A. 도커로 배포

```bash
docker build -t ylog .
docker run -d --name ylog -p 8080:8080 --env-file .env.prod -v ylog-images:/var/lib/ylog/images ylog
```

## 3-B. jar로 배포

```bash
./build.sh                      # 화면 빌드 → 테스트(로컬 DB·Redis 필요) → jar
scp backend/build/libs/myblog-0.0.1-SNAPSHOT.jar 서버:/opt/ylog/app.jar
# 서버에서 (Java 21 필요)
set -a; . /opt/ylog/.env.prod; set +a
java -jar /opt/ylog/app.jar
```

## 4. 켜진 뒤 확인

- `GET /api/config/limits`가 200이면 서버가 켜진 것이다.
- 첫 화면이 열리고, 회원가입에서 인증 메일(또는 `log` 모드면 서버 로그의 인증번호)이 오는지 본다.

## 5. 이번 배포에 들어 있지 않은 것

관리자 화면(신고 처리, 회원 정지, 공지 쓰기), 탈퇴 후 30일 재가입 대기, 방문자 화면 미리보기는 만드는 중이라 `wip/admin-withdraw-preview` 브랜치에 따로 있다. 공지는 기본 안내 9개를 읽기만 할 수 있다.
