CREATE TABLE tag (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(15) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_tag_name ON tag (lower(name));

CREATE TABLE post_tag (
    post_id  BIGINT NOT NULL REFERENCES post (id) ON DELETE CASCADE,
    tag_id   BIGINT NOT NULL REFERENCES tag (id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, tag_id)
);
CREATE INDEX ix_post_tag_tag ON post_tag (tag_id);

CREATE TABLE report (
    id           BIGSERIAL PRIMARY KEY,
    post_id      BIGINT       NOT NULL REFERENCES post (id) ON DELETE CASCADE,
    reporter_id  BIGINT       NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    reason       VARCHAR(20)  NOT NULL CHECK (reason IN ('SPAM', 'ABUSE', 'ADULT', 'ETC')),
    detail       VARCHAR(200),
    created_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_report UNIQUE (post_id, reporter_id)
);

CREATE TABLE post_image (
    id            BIGSERIAL PRIMARY KEY,
    post_id       BIGINT       REFERENCES post (id) ON DELETE CASCADE,
    uploader_id   BIGINT       NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    storage_key   VARCHAR(100) NOT NULL UNIQUE,
    content_type  VARCHAR(20)  NOT NULL,
    size_bytes    INT          NOT NULL CHECK (size_bytes <= 5242880),
    created_at    TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_post_image_post ON post_image (post_id);
