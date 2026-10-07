CREATE TABLE member (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(254) NOT NULL,
    nickname            VARCHAR(10)  NOT NULL,
    bio                 VARCHAR(100),
    password_hash       VARCHAR(100) NOT NULL,
    failed_login_count  SMALLINT     NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX ux_member_email ON member (lower(email));
CREATE UNIQUE INDEX ux_member_nickname ON member (lower(nickname));
