CREATE TABLE post (
    id           BIGSERIAL PRIMARY KEY,
    blog_id      BIGINT       NOT NULL REFERENCES blog (id) ON DELETE CASCADE,
    category_id  BIGINT       NOT NULL REFERENCES category (id) ON DELETE RESTRICT,
    title        VARCHAR(100) NOT NULL,
    body         TEXT         NOT NULL,
    visibility   VARCHAR(10)  NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    view_count   BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ
);
CREATE INDEX ix_post_blog_list ON post (blog_id, visibility, created_at DESC, id DESC);
CREATE INDEX ix_post_category ON post (category_id, created_at DESC);
CREATE INDEX ix_post_public ON post (visibility, created_at DESC, id DESC);
