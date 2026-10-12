-- 북마크: 나중에 읽을 글을 저장한다. 좋아요와 따로이고 본인만 본다 (2026-10-12)
CREATE TABLE bookmark (
    member_id   BIGINT      NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    post_id     BIGINT      NOT NULL REFERENCES post (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (member_id, post_id)
);
CREATE INDEX ix_bookmark_member_created ON bookmark (member_id, created_at DESC);
