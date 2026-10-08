package com.myblog.comment;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 댓글·좋아요 권한 (FR-038~040, 헌법 II) */
class CommentAuthorizationIT extends IntegrationTestBase {

    private LoggedIn owner;
    private LoggedIn writer;
    private LoggedIn stranger;
    private long postId;

    @BeforeEach
    void setUp() throws Exception {
        owner = signupAndLogin("블로그주인", "owner@example.com");
        writer = signupAndLogin("댓글쓴이", "writer@example.com");
        stranger = signupAndLogin("지나가는이", "stranger@example.com");
        postId = createPost(owner, "글", "PUBLIC");
    }

    private long comment(LoggedIn user, String content) throws Exception {
        String body = mvc.perform(jsonPost("/api/posts/" + postId + "/comments", Map.of("content", content))
                        .cookie(user.session()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    @Test
    void 비회원은_댓글을_쓸_수_없다() throws Exception {
        mvc.perform(jsonPost("/api/posts/" + postId + "/comments", Map.of("content", "안녕")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 오초_안에_다시_등록할_수_없다() throws Exception {
        comment(writer, "첫 댓글");
        mvc.perform(jsonPost("/api/posts/" + postId + "/comments", Map.of("content", "또")).cookie(writer.session()))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void 작성자도_블로그주인도_아니면_지울_수_없다() throws Exception {
        long id = comment(writer, "댓글");
        mvc.perform(delete("/api/comments/" + id).with(csrf()).cookie(stranger.session()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(stranger.session()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].deletable").value(false));
    }

    @Test
    void 작성자와_블로그주인은_지울_수_있다() throws Exception {
        long first = comment(writer, "하나");
        redis.delete("comment-cooldown:" + writer.memberId());
        long second = comment(writer, "둘");
        mvc.perform(delete("/api/comments/" + first).with(csrf()).cookie(writer.session()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/comments/" + second).with(csrf()).cookie(owner.session()))
                .andExpect(status().isNoContent());
    }

    @Test
    void 공백만_있는_댓글은_거절한다() throws Exception {
        mvc.perform(jsonPost("/api/posts/" + postId + "/comments", Map.of("content", "   ")).cookie(writer.session()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 좋아요는_켜고_끄며_자기_글에는_누를_수_없다() throws Exception {
        mvc.perform(put("/api/posts/" + postId + "/like").with(csrf()).cookie(writer.session()))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));
        mvc.perform(get("/api/posts/" + postId).cookie(writer.session()))
                .andExpect(jsonPath("$.likedByMe").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));
        mvc.perform(put("/api/posts/" + postId + "/like").with(csrf()).cookie(writer.session()))
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(0));
        mvc.perform(put("/api/posts/" + postId + "/like").with(csrf()).cookie(owner.session()))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/posts/" + postId + "/like").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 비공개_글의_댓글은_남에게_보이지_않는다() throws Exception {
        long privateId = createPost(owner, "비공개", "PRIVATE");
        mvc.perform(get("/api/posts/" + privateId + "/comments").cookie(writer.session()))
                .andExpect(status().isNotFound());
    }
}
