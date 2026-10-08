package com.myblog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.myblog.support.IntegrationTestBase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** 관리자 신고 처리: 같은 대상 묶기, 한 번에 처리, 이미 처리한 신고, 처리 완료는 대상 삭제, 작성자 정지 (FR-079, BR-48) */
class AdminReportIT extends IntegrationTestBase {

    private LoggedIn admin;
    private LoggedIn owner;
    private LoggedIn spammer;
    private List<LoggedIn> reporters;
    private long postId;
    private long commentId;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminLogin("admin@example.com");
        owner = signupAndLogin("블로그주인", "owner@example.com");
        spammer = signupAndLogin("광고왕", "spam@example.com");
        reporters = new ArrayList<>();
        for (String name : new String[] {"가나", "다라", "마바"}) {
            reporters.add(signupAndLogin(name, name.hashCode() + "@example.com"));
        }
        postId = createPost(owner, "평범한 글", "PUBLIC");
        String body = mvc.perform(jsonPost("/api/posts/" + postId + "/comments", Map.of("content", "부업으로 월 300!"))
                .cookie(spammer.session())).andReturn().getResponse().getContentAsString();
        commentId = json.readTree(body).get("id").asLong();
    }

    private void reportComment(LoggedIn who, String reason) throws Exception {
        mvc.perform(jsonPost("/api/comments/" + commentId + "/reports", Map.of("reason", reason)).cookie(who.session()))
                .andExpect(status().isCreated());
    }

    private ResultActions process(List<Long> ids, String action, Map<String, Object> suspend) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("reportIds", ids);
        body.put("action", action);
        body.put("note", "광고 댓글");
        if (suspend != null) {
            body.put("suspend", suspend);
        }
        return mvc.perform(withJson(patch("/api/admin/reports"), body).cookie(admin.session()));
    }

    private JsonNode pending() throws Exception {
        return json.readTree(mvc.perform(get("/api/admin/reports?status=PENDING").cookie(admin.session()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private List<Long> ids(JsonNode row) {
        List<Long> ids = new ArrayList<>();
        row.get("reportIds").forEach(n -> ids.add(n.asLong()));
        return ids;
    }

    @Test
    void 같은_댓글의_신고는_한_줄로_모이고_처리_완료하면_댓글이_지워진다() throws Exception {
        reportComment(reporters.get(0), "SPAM");
        reportComment(reporters.get(1), "SPAM");
        reportComment(reporters.get(2), "ETC");
        mvc.perform(jsonPost("/api/posts/" + postId + "/reports", Map.of("reason", "ABUSE"))
                .cookie(reporters.get(0).session())).andExpect(status().isCreated());

        JsonNode page = pending();
        assertThat(page.get("counts").get("PENDING").asLong()).isEqualTo(4);
        assertThat(page.get("totalCount").asLong()).isEqualTo(2);
        JsonNode row = null;
        for (JsonNode item : page.get("items")) {
            if (item.get("targetType").asText().equals("COMMENT")) {
                row = item;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.get("reportCount").asInt()).isEqualTo(3);
        assertThat(row.get("reasonSummary").asText()).isEqualTo("스팸 2 · 기타 1");
        assertThat(row.get("reporterSummary").asText()).isEqualTo("가나, 다라 외 1명");
        assertThat(row.get("targetText").asText()).isEqualTo("부업으로 월 300!");
        assertThat(row.get("targetAuthor").get("nickname").asText()).isEqualTo("광고왕");
        assertThat(row.get("exists").asBoolean()).isTrue();
        assertThat(row.get("canSuspend").asBoolean()).isTrue();
        mvc.perform(get("/api/auth/me").cookie(admin.session())).andExpect(jsonPath("$.pendingReportCount").value(4));

        process(ids(row), "RESOLVE", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handledCount").value(3))
                .andExpect(jsonPath("$.deletedCount").value(1))
                .andExpect(jsonPath("$.message").value("신고 3건을 처리하고 댓글을 삭제했습니다"));

        assertThat(jdbc.queryForObject("select count(*) from comment where id = ?", Long.class, commentId)).isZero();
        assertThat(jdbc.queryForList("select distinct handle_note from report where target_type = 'COMMENT'", String.class))
                .containsExactly("광고 댓글");
        // 처리한 신고는 지워진 대상이어도 한 줄로 남는다
        mvc.perform(get("/api/admin/reports?status=RESOLVED").cookie(admin.session()))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].reportCount").value(3))
                .andExpect(jsonPath("$.items[0].exists").value(false))
                .andExpect(jsonPath("$.items[0].handledBy").value("운영자"))
                .andExpect(jsonPath("$.counts.RESOLVED").value(3))
                .andExpect(jsonPath("$.counts.ALL").value(4));
    }

    @Test
    void 이미_처리한_신고가_섞이면_409이고_아무것도_바뀌지_않는다() throws Exception {
        reportComment(reporters.get(0), "SPAM");
        reportComment(reporters.get(1), "ABUSE");
        List<Long> ids = ids(pending().get("items").get(0));
        process(List.of(ids.get(0)), "REJECT", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("신고 1건을 반려했습니다"));

        process(ids, "RESOLVE", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 처리한 신고입니다"));
        assertThat(jdbc.queryForObject("select count(*) from comment where id = ?", Long.class, commentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from report where id = ?", String.class, ids.get(1)))
                .isEqualTo("PENDING");
        // 반려는 대상을 그대로 둔다
        process(List.of(ids.get(1)), "REJECT", null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from comment where id = ?", Long.class, commentId)).isEqualTo(1);
    }

    @Test
    void 글_신고를_처리_완료하면_글이_지워지고_작성자를_정지할_수_있다() throws Exception {
        mvc.perform(jsonPost("/api/posts/" + postId + "/reports", Map.of("reason", "SPAM"))
                .cookie(reporters.get(0).session())).andExpect(status().isCreated());
        List<Long> ids = ids(pending().get("items").get(0));

        // 반려와 함께 정지할 수 없다
        process(ids, "REJECT", Map.of("days", 7, "reason", "광고")).andExpect(status().isBadRequest());
        // 기간이 틀리면 처리도 되지 않는다
        process(ids, "RESOLVE", Map.of("days", 5, "reason", "광고")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from post where id = ?", Long.class, postId)).isEqualTo(1);

        process(ids, "RESOLVE", Map.of("days", 7, "reason", "광고 글"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspendedMemberId").value(owner.memberId()))
                .andExpect(jsonPath("$.message").value("신고 1건을 처리하고 글을 삭제했습니다. 작성자를 7일 정지했습니다"));
        assertThat(jdbc.queryForObject("select count(*) from post where id = ?", Long.class, postId)).isZero();
        assertThat(jdbc.queryForObject("select report_id from suspension where member_id = ?", Long.class,
                owner.memberId())).isEqualTo(ids.get(0));
        // 정지되면 로그인해 둔 상태가 끊긴다
        mvc.perform(get("/api/auth/me").cookie(owner.session())).andExpect(status().isUnauthorized());
    }

    @Test
    void 작성자가_탈퇴했거나_관리자면_정지와_함께_처리할_수_없다() throws Exception {
        reportComment(reporters.get(0), "SPAM");
        List<Long> ids = ids(pending().get("items").get(0));
        jdbc.update("update member set role = 'ADMIN' where id = ?", spammer.memberId());
        process(ids, "RESOLVE", Map.of("days", 3, "reason", ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("정지할 수 없는 회원입니다"));
        assertThat(jdbc.queryForObject("select status from report where id = ?", String.class, ids.get(0)))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from comment where id = ?", Long.class, commentId)).isEqualTo(1);
        // 정지 없이 처리만 하는 것은 된다
        process(ids, "RESOLVE", null).andExpect(status().isOk());
    }

    @Test
    void 대상이_먼저_지워진_신고는_내용과_작성자로_묶고_처리_완료로_기록만_남긴다() throws Exception {
        reportComment(reporters.get(0), "SPAM");
        reportComment(reporters.get(1), "SPAM");
        jdbc.update("delete from comment where id = ?", commentId);
        JsonNode row = pending().get("items").get(0);
        assertThat(row.get("reportCount").asInt()).isEqualTo(2);
        assertThat(row.get("exists").asBoolean()).isFalse();
        process(ids(row), "RESOLVE", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCount").value(0))
                .andExpect(jsonPath("$.message").value("신고 2건을 처리했습니다. 대상은 이미 삭제되었습니다"));
    }

    @Test
    void 메모가_200자를_넘거나_없는_신고면_처리하지_않는다() throws Exception {
        reportComment(reporters.get(0), "SPAM");
        List<Long> ids = ids(pending().get("items").get(0));
        mvc.perform(withJson(patch("/api/admin/reports"), Map.of("reportIds", ids, "action", "RESOLVE",
                "note", "가".repeat(201))).cookie(admin.session())).andExpect(status().isBadRequest());
        process(List.of(ids.get(0), 999999L), "RESOLVE", null).andExpect(status().isNotFound());
        process(List.of(), "RESOLVE", null).andExpect(status().isBadRequest());
        process(ids, "DELETE", null).andExpect(status().isBadRequest());
    }

    @Test
    void 요약에는_신고_수와_공지_수와_처리_대기_5줄이_나온다() throws Exception {
        reportComment(reporters.get(0), "SPAM");
        mvc.perform(get("/api/admin/summary").cookie(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.PENDING").value(1))
                .andExpect(jsonPath("$.noticeCount").isNumber())
                .andExpect(jsonPath("$.latestPending", hasSize(1)));
    }
}
