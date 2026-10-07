CREATE TABLE blog (
    id                       BIGSERIAL PRIMARY KEY,
    owner_id                 BIGINT       NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    name                     VARCHAR(30)  NOT NULL,
    description              VARCHAR(200),
    comments_last_viewed_at  TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_blog_owner ON blog (owner_id);

CREATE TABLE category (
    id           BIGSERIAL PRIMARY KEY,
    blog_id      BIGINT      NOT NULL REFERENCES blog (id) ON DELETE CASCADE,
    name         VARCHAR(20) NOT NULL,
    sort_order   INT         NOT NULL,
    color_index  SMALLINT    NOT NULL,
    is_default   BOOLEAN     NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX ux_category_name ON category (blog_id, lower(name));
