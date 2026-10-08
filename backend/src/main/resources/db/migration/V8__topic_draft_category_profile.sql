-- 주제 10개 (고정, 관리 화면 없음). 순서는 화면에 보이는 순서 (요구사항 용어 '주제', ERD 2-14)
CREATE TABLE topic (
    id          BIGINT      PRIMARY KEY,
    name        VARCHAR(20) NOT NULL UNIQUE,
    sort_order  INT         NOT NULL
);
INSERT INTO topic (id, name, sort_order) VALUES
    (1, 'IT·개발', 1),
    (2, '공부·학습', 2),
    (3, '요리', 3),
    (4, '맛집·카페', 4),
    (5, '여행', 5),
    (6, '문화·리뷰', 6),
    (7, '건강·운동', 7),
    (8, '반려동물', 8),
    (9, '일상', 9),
    (10, '취미', 10);

-- 블로그의 대표 주제. 기존 블로그는 '일상'
ALTER TABLE blog ADD COLUMN topic_id BIGINT NOT NULL DEFAULT 9 REFERENCES topic (id);

-- 프로필 색 (FR-05). 정해진 6가지 중 하나
ALTER TABLE member ADD COLUMN profile_color VARCHAR(7) NOT NULL DEFAULT '#c9dcfb'
    CHECK (profile_color IN ('#c9dcfb', '#e2d8f8', '#cdeee4', '#f8d6c6', '#f5e3ad', '#f9dbe8'));

-- 분류 소개글(0~100자)과 공개 범위 (FR-17, BR-46)
ALTER TABLE category ADD COLUMN description VARCHAR(100);
ALTER TABLE category ADD COLUMN visibility VARCHAR(10) NOT NULL DEFAULT 'PUBLIC'
    CHECK (visibility IN ('PUBLIC', 'PRIVATE'));

-- 글의 주제(항상 있음), 작성 상태(임시저장/작성완료), 처음 작성완료한 시각 (FR-09, FR-11, CR-68)
ALTER TABLE post ADD COLUMN topic_id BIGINT REFERENCES topic (id);
UPDATE post p SET topic_id = b.topic_id FROM blog b WHERE b.id = p.blog_id;
ALTER TABLE post ALTER COLUMN topic_id SET NOT NULL;

ALTER TABLE post ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'PUBLISHED'
    CHECK (status IN ('DRAFT', 'PUBLISHED'));
ALTER TABLE post ADD COLUMN published_at TIMESTAMPTZ;
UPDATE post SET published_at = created_at;
ALTER TABLE post ADD CONSTRAINT ck_post_published_at
    CHECK ((status = 'DRAFT' AND published_at IS NULL) OR (status = 'PUBLISHED' AND published_at IS NOT NULL));

-- 목록은 처음 작성완료한 시각(published_at) 순서로 읽는다
DROP INDEX ix_post_blog_list;
DROP INDEX ix_post_public;
DROP INDEX ix_post_category;
CREATE INDEX ix_post_blog_list ON post (blog_id, published_at DESC, id DESC);
CREATE INDEX ix_post_category ON post (category_id, published_at DESC);
CREATE INDEX ix_post_public ON post (published_at DESC, id DESC) WHERE status = 'PUBLISHED' AND visibility = 'PUBLIC';
CREATE INDEX ix_post_topic ON post (topic_id, published_at DESC, id DESC) WHERE status = 'PUBLISHED';
