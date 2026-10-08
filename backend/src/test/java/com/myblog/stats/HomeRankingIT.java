package com.myblog.stats;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 지금 핫한 글과 이번 주 인기 블로거 (FR-072, FR-074, BR-33, BR-45) */
class HomeRankingIT extends IntegrationTestBase {

    private static final long COOKING = 3;
    private static final long TRAVEL = 5;

    private void publishedAt(long postId, String kst) {
        jdbc.update("update post set published_at = cast(? as timestamptz) where id = ?", kst + "+09", postId);
    }

    private void comments(long postId, long authorId, int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update("insert into comment (post_id, author_id, content, created_at) values (?, ?, '댓글', now())",
                    postId, authorId);
        }
    }

    private void like(long postId, long memberId) {
        jdbc.update("insert into post_like (post_id, member_id, created_at) values (?, ?, now())", postId, memberId);
    }

    @Test
    void 핫한_글은_가장_최근_글의_날부터_14일_안에서_좋아요_더하기_댓글_5배로_고른다() throws Exception {
        LoggedIn a = signupAndLogin("작가", "a@example.com");
        LoggedIn b = signupAndLogin("독자", "b@example.com");
        LoggedIn c = signupAndLogin("손님", "c@example.com");

        long latest = createPost(a, "최신 글", "PUBLIC");
        publishedAt(latest, "2026-09-30 10:00:00");
        long high = createPost(a, "댓글 많은 글", "PUBLIC");
        publishedAt(high, "2026-09-20 10:00:00");
        comments(high, b.memberId(), 1);
        // 답글도 댓글 수에 들어간다
        Long parent = jdbc.queryForObject("select id from comment where post_id = ?", Long.class, high);
        jdbc.update("insert into comment (post_id, author_id, content, created_at, parent_id) values (?, ?, '답글', now(), ?)",
                high, a.memberId(), parent);
        long liked = createPost(a, "좋아요 글", "PUBLIC");
        publishedAt(liked, "2026-09-25 10:00:00");
        like(liked, b.memberId());
        like(liked, c.memberId());
        long tieMoreViews = createPost(a, "동점 조회 많음", "PUBLIC");
        publishedAt(tieMoreViews, "2026-09-18 10:00:00");
        comments(tieMoreViews, b.memberId(), 1);
        jdbc.update("update post set view_count = 50 where id = ?", tieMoreViews);
        long tieFewViews = createPost(a, "동점 조회 적음", "PUBLIC");
        publishedAt(tieFewViews, "2026-09-19 10:00:00");
        comments(tieFewViews, b.memberId(), 1);
        jdbc.update("update post set view_count = 10 where id = ?", tieFewViews);
        // 한국 날짜로 9월 17일 00:30은 기간 안, 9월 16일 23:59는 기간 밖
        long boundaryIn = createPost(a, "경계 안", "PUBLIC");
        publishedAt(boundaryIn, "2026-09-17 00:30:00");
        comments(boundaryIn, b.memberId(), 3);
        long boundaryOut = createPost(a, "경계 밖", "PUBLIC");
        publishedAt(boundaryOut, "2026-09-16 23:59:00");
        comments(boundaryOut, b.memberId(), 10);
        long hidden = createPost(a, "비공개 인기", "PRIVATE");
        publishedAt(hidden, "2026-09-29 10:00:00");
        comments(hidden, b.memberId(), 10);
        createPost(a, Map.of("title", "임시저장", "body", "본문", "draft", true));
        long cooking = createPost(a, Map.of("title", "오래된 요리", "body", "본문", "topicId", COOKING));
        publishedAt(cooking, "2026-09-01 10:00:00");

        mvc.perform(get("/api/home/hot-posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("경계 안"))
                .andExpect(jsonPath("$[0].commentCount").value(3))
                .andExpect(jsonPath("$[1].title").value("댓글 많은 글"))
                .andExpect(jsonPath("$[1].commentCount").value(2))
                .andExpect(jsonPath("$[1].blogName").value("작가의 블로그"))
                .andExpect(jsonPath("$[1].authorNickname").value("작가"))
                .andExpect(jsonPath("$[1].authorColor").value("#c9dcfb"))
                .andExpect(jsonPath("$[1].topicName").value("일상"))
                .andExpect(jsonPath("$[2].title").value("동점 조회 많음"))
                .andExpect(jsonPath("$[2].viewCount").value(50));
        mvc.perform(get("/api/home/hot-posts").param("limit", "5"))
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[3].title").value("동점 조회 적음"))
                .andExpect(jsonPath("$[4].title").value("좋아요 글"))
                .andExpect(jsonPath("$[4].likeCount").value(2));

        // 주제를 고르면 그 주제의 가장 최근 글을 기준으로 그 주제의 글만 본다
        mvc.perform(get("/api/home/hot-posts").param("topicId", String.valueOf(COOKING)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("오래된 요리"))
                .andExpect(jsonPath("$[0].topicName").value("요리"));
        mvc.perform(get("/api/home/hot-posts").param("topicId", String.valueOf(TRAVEL)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 인기_블로거는_7일_안_글의_점수를_블로그마다_더하고_같으면_구독자_그다음_최근_글() throws Exception {
        LoggedIn a = signupAndLogin("에이", "a@example.com");
        LoggedIn b = signupAndLogin("비이", "b@example.com");
        LoggedIn c = signupAndLogin("씨이", "c@example.com");
        LoggedIn d = signupAndLogin("디이", "d@example.com");

        mvc.perform(get("/api/home/hot-bloggers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // A: 5 + 5 = 10 (9월 23일 23:50 글은 기간 밖)
        long a1 = createPost(a, "에이 최신", "PUBLIC");
        publishedAt(a1, "2026-09-30 10:00:00");
        comments(a1, b.memberId(), 1);
        long a2 = createPost(a, "에이 경계 안", "PUBLIC");
        publishedAt(a2, "2026-09-24 00:10:00");
        comments(a2, b.memberId(), 1);
        long a3 = createPost(a, "에이 경계 밖", "PUBLIC");
        publishedAt(a3, "2026-09-23 23:50:00");
        comments(a3, b.memberId(), 10);
        // B: 10, 구독자 1명 → 점수가 같은 A보다 앞선다
        long b1 = createPost(b, "비이 글", "PUBLIC");
        publishedAt(b1, "2026-09-28 10:00:00");
        comments(b1, a.memberId(), 2);
        jdbc.update("insert into subscription (member_id, blog_id, created_at) values (?, ?, now())", c.memberId(), b.blogId());
        // C와 D: 3점씩, 구독자 없음 → 최근 글이 있는 D가 앞선다
        long c1 = createPost(c, Map.of("title", "씨이 여행", "body", "본문", "topicId", TRAVEL));
        publishedAt(c1, "2026-09-29 10:00:00");
        like(c1, a.memberId());
        like(c1, b.memberId());
        like(c1, d.memberId());
        long cHidden = createPost(c, "씨이 비공개", "PRIVATE");
        publishedAt(cHidden, "2026-09-29 11:00:00");
        comments(cHidden, a.memberId(), 10);
        long d1 = createPost(d, "디이 글", "PUBLIC");
        publishedAt(d1, "2026-09-29 12:00:00");
        like(d1, a.memberId());
        like(d1, b.memberId());
        like(d1, c.memberId());

        mvc.perform(get("/api/home/hot-bloggers"))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].blogId").value(b.blogId()))
                .andExpect(jsonPath("$[0].ownerNickname").value("비이"))
                .andExpect(jsonPath("$[0].subscriberCount").value(1))
                .andExpect(jsonPath("$[0].score").value(10))
                .andExpect(jsonPath("$[1].blogId").value(a.blogId()))
                .andExpect(jsonPath("$[1].score").value(10))
                .andExpect(jsonPath("$[2].blogId").value(d.blogId()))
                .andExpect(jsonPath("$[3].blogId").value(c.blogId()))
                .andExpect(jsonPath("$[3].rank").value(4))
                .andExpect(jsonPath("$[3].blogName").value("씨이의 블로그"))
                .andExpect(jsonPath("$[3].ownerColor").value("#c9dcfb"));

        // 주제를 고르면 그 주제로 쓴 글의 점수만 더한다
        mvc.perform(get("/api/home/hot-bloggers").param("topicId", String.valueOf(TRAVEL)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].blogId").value(c.blogId()))
                .andExpect(jsonPath("$[0].score").value(3));
    }
}
