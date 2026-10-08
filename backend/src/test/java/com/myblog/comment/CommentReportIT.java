package com.myblog.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 댓글 신고 (FR-071, BR-17, BR-19) */
class CommentReportIT extends IntegrationTestBase {

    private LoggedIn owner;
    private LoggedIn writer;
    private LoggedIn reporter;
    private long postId;

    @BeforeEach
    void setUp() throws Exception {
        owner = signupAndLogin("블로그주인", "owner@example.com");
        writer = signupAndLogin("댓글쓴이", "writer@example.com");
        reporter = signupAndLogin("신고자", "reporter@example.com");
        postId = createPost(owner, "글", "PUBLIC");
    }

    private long comment(LoggedIn user, String content, boolean secret) throws Exception {
        redis.delete("comment-cooldown:" + user.memberId());
        String body = mvc.perform(jsonPost("/api/posts/" + postId + "/comments",
                        Map.of("content", content, "secret", secret)).cookie(user.session()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    @Test
    void 남의_댓글을_신고하면_접수되고_다시_신고할_수_없다() throws Exception {
        long id = comment(writer, "광고 댓글", false);
        mvc.perform(jsonPost("/api/comments/" + id + "/reports", Map.of("reason", "SPAM")).cookie(reporter.session()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("신고가 접수되었습니다"));
        mvc.perform(jsonPost("/api/comments/" + id + "/reports", Map.of("reason", "ABUSE")).cookie(reporter.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 신고한 댓글입니다"));
        // 블로그 주인도 신고할 수 있다
        mvc.perform(jsonPost("/api/comments/" + id + "/reports", Map.of("reason", "SPAM")).cookie(owner.session()))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(reporter.session()))
                .andExpect(jsonPath("$[0].reportedByMe").value(true));
        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(writer.session()))
                .andExpect(jsonPath("$[0].reportedByMe").value(false))
                .andExpect(jsonPath("$[0].reportable").value(false));
    }

    @Test
    void 내_댓글은_신고할_수_없고_사유가_필요하다() throws Exception {
        long id = comment(writer, "내 댓글", false);
        mvc.perform(jsonPost("/api/comments/" + id + "/reports", Map.of("reason", "SPAM")).cookie(writer.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("내 댓글은 신고할 수 없습니다"));
        mvc.perform(jsonPost("/api/comments/" + id + "/reports", Map.of("reason", "몰라")).cookie(reporter.session()))
                .andExpect(status().isBadRequest());
        mvc.perform(jsonPost("/api/comments/" + id + "/reports", Map.of("reason", "SPAM")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 가려진_비밀_댓글은_신고할_수_없다() throws Exception {
        long id = comment(writer, "비밀", true);
        mvc.perform(jsonPost("/api/comments/" + id + "/reports", Map.of("reason", "SPAM")).cookie(reporter.session()))
                .andExpect(status().isNotFound());
        mvc.perform(jsonPost("/api/comments/999999/reports", Map.of("reason", "SPAM")).cookie(reporter.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 댓글을_지워도_신고_당시_내용과_작성자가_남는다() throws Exception {
        long id = comment(writer, "지워질 댓글", false);
        mvc.perform(jsonPost("/api/comments/" + id + "/reports", Map.of("reason", "ETC", "detail", "설명"))
                        .cookie(reporter.session()))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/comments/" + id).with(csrf()).cookie(writer.session()))
                .andExpect(status().isNoContent());

        Map<String, Object> row = jdbc.queryForMap("select * from report where reporter_id = ?", reporter.memberId());
        assertThat(row.get("target_type")).isEqualTo("COMMENT");
        assertThat(row.get("comment_id")).isNull();
        assertThat(((Number) row.get("post_id")).longValue()).isEqualTo(postId);
        assertThat(row.get("target_text")).isEqualTo("지워질 댓글");
        assertThat(((Number) row.get("target_author_id")).longValue()).isEqualTo(writer.memberId());
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("detail")).isEqualTo("설명");
    }

    @Test
    void 글_신고도_제목을_남기고_글을_지워도_기록이_남는다() throws Exception {
        mvc.perform(jsonPost("/api/posts/" + postId + "/reports", Map.of("reason", "SPAM")).cookie(reporter.session()))
                .andExpect(status().isCreated());
        mvc.perform(jsonPost("/api/posts/" + postId + "/reports", Map.of("reason", "SPAM")).cookie(reporter.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 신고한 글입니다"));
        mvc.perform(delete("/api/posts/" + postId).with(csrf()).cookie(owner.session()))
                .andExpect(status().isOk());

        Map<String, Object> row = jdbc.queryForMap("select * from report where reporter_id = ?", reporter.memberId());
        assertThat(row.get("target_type")).isEqualTo("POST");
        assertThat(row.get("post_id")).isNull();
        assertThat(row.get("target_text")).isEqualTo("글");
        assertThat(((Number) row.get("target_author_id")).longValue()).isEqualTo(owner.memberId());
    }
}
