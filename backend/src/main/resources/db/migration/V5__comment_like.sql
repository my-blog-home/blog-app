CREATE TABLE comment (
    id          BIGSERIAL PRIMARY KEY,
    post_id     BIGINT       NOT NULL REFERENCES post (id) ON DELETE CASCADE,
    author_id   BIGINT       REFERENCES member (id) ON DELETE SET NULL,
    content     VARCHAR(500) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_comment_post ON comment (post_id, created_at);
CREATE INDEX ix_comment_created ON comment (created_at DESC);

CREATE TABLE post_like (
    id          BIGSERIAL PRIMARY KEY,
    post_id     BIGINT      NOT NULL REFERENCES post (id) ON DELETE CASCADE,
    member_id   BIGINT      NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_post_like UNIQUE (post_id, member_id)
);
