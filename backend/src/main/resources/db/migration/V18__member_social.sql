-- 소셜 로그인(GitHub·카카오 등)으로 연결한 계정. 회원 한 명이 여러 제공자를 연결할 수 있다
CREATE TABLE member_social (
    id                BIGSERIAL PRIMARY KEY,
    member_id         BIGINT       NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    provider          VARCHAR(20)  NOT NULL,
    provider_user_id  VARCHAR(100) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_member_social UNIQUE (provider, provider_user_id)
);
CREATE INDEX ix_member_social_member ON member_social (member_id);
