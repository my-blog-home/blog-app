package com.myblog.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 관리자 글·댓글 관리: 신고 없이도 찾아보고 지운다. 비밀 댓글 내용은 내려 주지 않는다 */
class AdminContentIT extends IntegrationTestBase {

    private long comment(LoggedIn user, long postId, String content) throws Exception {
        String body = mvc.perform(jsonPost("/api/posts/" + postId + "/comments", Map.of("content", content))
                        .cookie(user.session()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    @Test
    void 글과_댓글을_찾아보고_지운다() throws Exception {
        LoggedIn writer = signupAndLogin("글쓴이", "writer@example.com");
        LoggedIn reader = signupAndLogin("읽는이", "reader@example.com");
        long postId = createPost(writer, "관리 대상 글", "PUBLIC");
        long commentId = comment(reader, postId, "지울 댓글");
        // 댓글은 연속으로 쓰면 막히므로 비밀 댓글은 바로 넣는다
        jdbc.update("insert into comment (post_id, author_id, content, is_secret, created_at)"
                + " values (?, ?, '몰래 쓴 말', true, now() + interval '1 second')", postId, reader.memberId());
        LoggedIn admin = adminLogin("admin@example.com");

        mvc.perform(get("/api/admin/posts").param("q", "관리 대상").cookie(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].id").value(postId))
                .andExpect(jsonPath("$.items[0].authorNickname").value("글쓴이"))
                .andExpect(jsonPath("$.items[0].commentCount").value(2));
        mvc.perform(get("/api/admin/comments").cookie(admin.session()))
                .andExpect(jsonPath("$.items[0].secret").value(true))
                .andExpect(jsonPath("$.items[0].content").doesNotExist())
                .andExpect(jsonPath("$.items[1].content").value("지울 댓글"));
        // 비밀 댓글 내용으로는 찾지 못한다
        mvc.perform(get("/api/admin/comments").param("q", "몰래").cookie(admin.session()))
                .andExpect(jsonPath("$.totalCount").value(0));

        mvc.perform(delete("/api/admin/comments/" + commentId).with(csrf()).cookie(admin.session()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/comments/" + commentId).with(csrf()).cookie(admin.session()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/posts/" + postId).with(csrf()).cookie(admin.session()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/comments").cookie(admin.session()))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void 관리자가_아니면_볼_수도_지울_수도_없다() throws Exception {
        LoggedIn writer = signupAndLogin("글쓴이", "writer2@example.com");
        long postId = createPost(writer, "남의 글", "PUBLIC");
        mvc.perform(get("/api/admin/posts").cookie(writer.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/comments")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/posts/" + postId).with(csrf()).cookie(writer.session()))
                .andExpect(status().isForbidden());
    }
}
