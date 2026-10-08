-- 일별 통계 (BM-06, research R-12). 날짜는 한국 시간 기준
CREATE TABLE daily_stats (
    blog_id        BIGINT NOT NULL REFERENCES blog (id) ON DELETE CASCADE,
    stat_date      DATE   NOT NULL,
    view_count     INT    NOT NULL DEFAULT 0,
    visitor_count  INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (blog_id, stat_date)
);

-- 대시보드의 "최근 7일 인기 글"을 위해 글별 일별 조회수도 둔다 (BM-02-3)
CREATE TABLE post_daily_views (
    post_id     BIGINT NOT NULL REFERENCES post (id) ON DELETE CASCADE,
    stat_date   DATE   NOT NULL,
    view_count  INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (post_id, stat_date)
);
