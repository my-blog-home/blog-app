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
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | | 관리자 계정(선택). 둘 다 값이 있고 그 이메일로 가입한 회원이 없을 때만, 서버를 켤 때 관리자(닉네임 "운영자", 블로그 없음)를 하나 만든다. 이미 있으면 아무것도 바꾸지 않는다(비밀번호를 바꿔도 반영되지 않음). 비밀번호는 가입 규칙(영문·숫자·특수문자 8자 이상)을 따라야 한다. 관리자는 회원가입으로 만들 수 없다 |

## 3-A. 도커로 배포

```bash
docker build -t ylog .
docker run -d --name ylog -p 8080:8080 --env-file .env.prod -v ylog-images:/var/lib/ylog/images ylog
```

## 3-B. jar로 배포

```bash
./build.sh                      # 화면 빌드 → 테스트(로컬 DB·Redis 필요) → jar
scp backend/build/libs/myblog.jar 서버:/opt/ylog/app.jar
# 서버에서 (Java 21 필요)
set -a; . /opt/ylog/.env.prod; set +a
java -jar /opt/ylog/app.jar
```

## 3-C. GitHub Actions 자동 배포 (지금 쓰는 방식)

`main`에 push하거나 PR을 병합하면 `.github/workflows/deploy.yml`이 돈다.

1. 테스트(PostgreSQL·Redis 서비스 컨테이너로 서버 통합 테스트, 화면 테스트)
2. 도커 이미지 빌드 → 압축 파일로 서버 `~/ylog`에 복사(SSH)
3. 서버에서 `deploy/deploy.sh` 실행: Redis 컨테이너 준비, 이전 앱 컨테이너 교체, **포트 8330**으로 실행, 켜질 때까지 확인

PR에서는 `.github/workflows/ci.yml`이 테스트와 도커 빌드만 확인한다(배포 안 함). Actions 탭의 "Deploy"에서 손으로 다시 돌릴 수도 있다.

Crowfoot에서 발급한 PostgreSQL을 쓰므로 `DB_NAME`에는 발급받은 **스키마** 이름을 넣고, 데이터베이스는 기본값 `nhnacademy`로 접속한다.

**필수 시크릿**: `SSH_ADDRESS`, `SSH_PORT`, `SSH_ID`, `SSH_PASSWORD`, `DB_ADDRESS`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`

**선택 시크릿**(없으면 괄호 안 기본값): `DB_DATABASE`(nhnacademy), `DB_SCHEMA`(`DB_NAME` 값), `MAIL_MODE`(log), `SHOW_CODE_ON_SCREEN`(true), `COOKIE_SECURE`(false), `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`(둘 다 등록하면 처음 켤 때 관리자 계정을 만든다. 없으면 관리자 없이 켜진다)

서버 조건: Docker가 설치돼 있어야 한다. SSH 계정이 docker를 바로 못 쓰면 `SSH_PASSWORD`로 sudo를 쓴다. DB가 같은 서버에 있으면(`DB_ADDRESS`가 localhost) 컨테이너가 `host.docker.internal`로 접속하므로 PostgreSQL이 도커 네트워크(172.x)에서 오는 접속도 받아야 한다.

## 4. 켜진 뒤 확인

- `GET /api/config/limits`가 200이면 서버가 켜진 것이다.
- 첫 화면이 열리고, 회원가입에서 인증 메일(또는 `log` 모드면 서버 로그의 인증번호)이 오는지 본다.

## 5. 이번 배포에 들어 있지 않은 것

관리자 화면(신고 처리, 회원 정지, 공지 쓰기), 탈퇴 후 30일 재가입 대기, 방문자 화면 미리보기는 만드는 중이라 `wip/admin-withdraw-preview` 브랜치에 따로 있다. 공지는 기본 안내 9개를 읽기만 할 수 있다.
