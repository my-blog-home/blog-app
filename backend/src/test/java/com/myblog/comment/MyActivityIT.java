package com.myblog.comment;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 내 활동: 좋아요한 글과 댓글 단 글. 지금 읽을 수 없는 글은 빠진다 (FR-069, BR-32) */
class MyActivityIT extends IntegrationTestBase {

    private LoggedIn owner;
    private LoggedIn me;

    @BeforeEach
    void setUp() throws Exception {
        owner = signupAndLogin("블로그주인", "owner@example.com");
        me = signupAndLogin("나야나", "me@example.com");
    }

    private long comment(long postId, String content, Long parentId) throws Exception {
        redis.delete("comment-cooldown:" + me.memberId());
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        body.put("parentId", parentId);
        String response = mvc.perform(jsonPost("/api/posts/" + postId + "/comments", body).cookie(me.session()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private void makePrivate(long postId, String title) throws Exception {
        mvc.perform(withJson(put("/api/posts/" + postId), Map.of("title", title, "body", "본문", "visibility", "PRIVATE"))
                        .cookie(owner.session()))
                .andExpect(status().isOk());
    }

    @Test
    void 좋아요한_글은_최근에_누른_순이고_읽을_수_없는_글은_빠진다() throws Exception {
        long first = createPost(owner, "첫 글", "PUBLIC");
        long second = createPost(owner, "둘째 글", "PUBLIC");
        long later = createPost(owner, "비공개 될 글", "PUBLIC");
        for (long id : new long[] {second, first, later}) {
            mvc.perform(put("/api/posts/" + id + "/like").with(csrf()).cookie(me.session())).andExpect(status().isOk());
        }
        makePrivate(later, "비공개 될 글");

        mvc.perform(get("/api/me/liked-posts").cookie(me.session()))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.items[0].id").value(first))
                .andExpect(jsonPath("$.items[1].id").value(second));
        mvc.perform(get("/api/me/liked-posts")).andExpect(status().isUnauthorized());
    }

    @Test
    void 댓글_단_글은_내_최근_댓글_순이고_글마다_최근_댓글_하나와_나머지_수를_보여_준다() throws Exception {
        long a = createPost(owner, "가 글", "PUBLIC");
        long b = createPost(owner, "나 글", "PUBLIC");
        long hidden = createPost(owner, "숨을 글", "PUBLIC");
        comment(a, "가에 첫 댓글", null);
        comment(b, "나에 댓글", null);
        comment(hidden, "숨을 글에 댓글", null);
        long parent = comment(a, "가에 둘째 댓글", null);
        long reply = comment(a, "가에 답글", parent);
        makePrivate(hidden, "숨을 글");

        mvc.perform(get("/api/me/commented-posts").cookie(me.session()))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.items[0].postId").value(a))
                .andExpect(jsonPath("$.items[0].commentId").value(reply))
                .andExpect(jsonPath("$.items[0].excerpt").value("가에 답글"))
                .andExpect(jsonPath("$.items[0].reply").value(true))
                .andExpect(jsonPath("$.items[0].otherCount").value(2))
                .andExpect(jsonPath("$.items[1].postId").value(b))
                .andExpect(jsonPath("$.items[1].otherCount").value(0));
    }
}
