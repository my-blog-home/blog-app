package com.myblog.stats;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 블로그 관리 권한, 새 댓글 표시, 조회수·방문자 집계 (FR-044~051, SC-008) */
class ManageAndStatsIT extends IntegrationTestBase {

    private static final Cookie VISITOR = new Cookie("vid", "11111111-2222-3333-4444-555555555555");

    private LoggedIn owner;
    private LoggedIn other;

    @BeforeEach
    void setUp() throws Exception {
        owner = signupAndLogin("주인", "owner@example.com");
        other = signupAndLogin("다른회원", "other@example.com");
    }

    @Test
    void 관리_화면은_블로그_주인만_볼_수_있다() throws Exception {
        String base = "/api/manage/blogs/" + owner.blogId();
        for (String path : new String[] {"/dashboard", "/posts", "/comments", "/stats"}) {
            mvc.perform(get(base + path).cookie(other.session())).andExpect(status().isNotFound());
            mvc.perform(get(base + path)).andExpect(status().isUnauthorized());
            mvc.perform(get(base + path).cookie(owner.session())).andExpect(status().isOk());
        }
    }

    @Test
    void 글_관리는_비공개를_포함하고_공개여부로_거른다() throws Exception {
        createPost(owner, "공개", "PUBLIC");
        createPost(owner, "비공개", "PRIVATE");
        String base = "/api/manage/blogs/" + owner.blogId() + "/posts";
        mvc.perform(get(base).cookie(owner.session())).andExpect(jsonPath("$.totalCount").value(2));
        mvc.perform(get(base).param("visibility", "PRIVATE").cookie(owner.session()))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].title").value("비공개"));
    }

    @Test
    void 새_댓글은_남이_단_것만_세고_댓글_관리를_열면_읽음이_된다() throws Exception {
        long postId = createPost(owner, "글", "PUBLIC");
        comment(other, postId, "하나");
        redis.delete("comment-cooldown:" + other.memberId());
        comment(other, postId, "둘");
        comment(owner, postId, "내 댓글");

        mvc.perform(get("/api/manage/new-comments").cookie(owner.session()))
                .andExpect(jsonPath("$.count").value(2));
        mvc.perform(get("/api/manage/blogs/" + owner.blogId() + "/comments").cookie(owner.session()))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.items[0].isNew").value(false))
                .andExpect(jsonPath("$.items[1].isNew").value(true));
        mvc.perform(get("/api/manage/new-comments").cookie(owner.session()))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void 같은_방문자가_글_3개를_열고_하나를_다시_열면_방문자_1_조회수_3이다() throws Exception {
        long a = createPost(owner, "A", "PUBLIC");
        long b = createPost(owner, "B", "PUBLIC");
        long c = createPost(owner, "C", "PUBLIC");
        for (long id : new long[] {a, b, c, a}) {
            mvc.perform(get("/api/posts/" + id).cookie(VISITOR)).andExpect(status().isOk());
        }
        // 블로그 주인 본인의 조회는 세지 않는다
        mvc.perform(get("/api/posts/" + a).cookie(owner.session())).andExpect(status().isOk());

        mvc.perform(get("/api/manage/blogs/" + owner.blogId() + "/dashboard").cookie(owner.session()))
                .andExpect(jsonPath("$.views.today").value(3))
                .andExpect(jsonPath("$.visitors.today").value(1))
                .andExpect(jsonPath("$.views.total").value(3))
                .andExpect(jsonPath("$.daily30.length()").value(30))
                .andExpect(jsonPath("$.popular7.length()").value(3))
                .andExpect(jsonPath("$.recent[0].title").value("C"));

        // 다른 회원은 다른 방문자다
        mvc.perform(get("/api/posts/" + a).cookie(other.session()));
        mvc.perform(get("/api/manage/blogs/" + owner.blogId() + "/stats").param("days", "7").cookie(owner.session()))
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[6].views").value(4))
                .andExpect(jsonPath("$[6].visitors").value(2));
    }

    @Test
    void 통계_기간은_7일과_30일만_된다() throws Exception {
        mvc.perform(get("/api/manage/blogs/" + owner.blogId() + "/stats").param("days", "10").cookie(owner.session()))
                .andExpect(status().isBadRequest());
    }

    private void comment(LoggedIn user, long postId, String content) throws Exception {
        mvc.perform(jsonPost("/api/posts/" + postId + "/comments", Map.of("content", content)).cookie(user.session()))
                .andExpect(status().isCreated());
    }
}
