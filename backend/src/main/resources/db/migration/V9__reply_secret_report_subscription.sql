-- 답글(한 단계)과 비밀 댓글 (FR-065, FR-066). 원댓글을 지우면 답글도 지워진다 (BR-15)
ALTER TABLE comment ADD COLUMN parent_id BIGINT REFERENCES comment (id) ON DELETE CASCADE;
ALTER TABLE comment ADD COLUMN is_secret BOOLEAN NOT NULL DEFAULT false;
CREATE INDEX ix_comment_parent ON comment (parent_id);
CREATE INDEX ix_comment_author ON comment (author_id, created_at DESC);

-- 신고 대상을 글·댓글로 넓힌다 (FR-071, BR-17, BR-19).
-- 대상이 지워져도 기록은 남도록 글·댓글 번호는 비우고, 신고 당시의 제목·내용과 작성자를 함께 둔다.
ALTER TABLE report DROP CONSTRAINT ux_report;
ALTER TABLE report DROP CONSTRAINT report_post_id_fkey;
ALTER TABLE report ALTER COLUMN post_id DROP NOT NULL;
ALTER TABLE report ADD CONSTRAINT fk_report_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE SET NULL;
ALTER TABLE report ADD COLUMN target_type VARCHAR(10) NOT NULL DEFAULT 'POST'
    CHECK (target_type IN ('POST', 'COMMENT'));
ALTER TABLE report ADD COLUMN comment_id BIGINT REFERENCES comment (id) ON DELETE SET NULL;
ALTER TABLE report ADD COLUMN target_text VARCHAR(500);
ALTER TABLE report ADD COLUMN target_author_id BIGINT REFERENCES member (id) ON DELETE SET NULL;

-- 나중에 관리자 처리 기능을 만들 때 쓰는 칸. 지금은 처리 대기로만 남는다 (BR-18, CR-65)
ALTER TABLE report ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING', 'RESOLVED', 'REJECTED'));
ALTER TABLE report ADD COLUMN handled_by BIGINT REFERENCES member (id) ON DELETE SET NULL;
ALTER TABLE report ADD COLUMN handled_at TIMESTAMPTZ;
ALTER TABLE report ADD COLUMN handle_note VARCHAR(200);
ALTER TABLE report ADD CONSTRAINT ck_report_handled
    CHECK ((status = 'PENDING' AND handled_at IS NULL AND handle_note IS NULL)
        OR (status <> 'PENDING' AND handled_at IS NOT NULL));

-- 이미 있던 글 신고에 신고 당시 제목과 작성자를 채운다
UPDATE report r SET target_text = p.title, target_author_id = b.owner_id
FROM post p JOIN blog b ON b.id = p.blog_id
WHERE p.id = r.post_id;
UPDATE report SET target_text = '' WHERE target_text IS NULL;
ALTER TABLE report ALTER COLUMN target_text SET NOT NULL;

-- 한 사람은 같은 대상을 한 번만 신고한다. 대상이 지워져 번호가 비면 제약에서 빠진다
CREATE UNIQUE INDEX ux_report_post ON report (post_id, reporter_id) WHERE target_type = 'POST' AND post_id IS NOT NULL;
CREATE UNIQUE INDEX ux_report_comment ON report (comment_id, reporter_id) WHERE target_type = 'COMMENT' AND comment_id IS NOT NULL;
CREATE INDEX ix_report_status ON report (status, created_at DESC);

-- 구독: 회원 + 블로그 한 쌍당 하나 (FR-067, BR-12)
CREATE TABLE subscription (
    member_id   BIGINT      NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    blog_id     BIGINT      NOT NULL REFERENCES blog (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (member_id, blog_id)
);
CREATE INDEX ix_subscription_blog ON subscription (blog_id);
CREATE INDEX ix_subscription_member_recent ON subscription (member_id, created_at DESC);

-- 내 활동의 좋아요한 글 (최근 누른 순, FR-069)
CREATE INDEX ix_post_like_member ON post_like (member_id, created_at DESC);
