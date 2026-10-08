package com.myblog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 회원 관리와 정지: 목록·검색, 정지 로그인 안내, 세션 끊기, 기간이 끝나면 풀림, 해제 (FR-080, FR-081, BR-49) */
class AdminMemberIT extends IntegrationTestBase {

    private static final String EMAIL = "member@example.com";

    private void suspend(LoggedIn admin, long memberId, Integer days, String reason, int expected) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("days", days);
        body.put("reason", reason);
        mvc.perform(jsonPost("/api/admin/members/" + memberId + "/suspensions", body).cookie(admin.session()))
                .andExpect(status().is(expected));
    }

    private org.springframework.test.web.servlet.ResultActions login(String password) throws Exception {
        return mvc.perform(jsonPost("/api/auth/login", Map.of("email", EMAIL, "password", password)));
    }

    @Test
    void 기간_정지된_회원은_비밀번호가_맞을_때만_끝나는_시각과_사유를_보고_로그인한_상태도_끊긴다() throws Exception {
        LoggedIn admin = adminLogin("admin@example.com");
        LoggedIn member = signupAndLogin("회원", EMAIL);
        suspend(admin, member.memberId(), 7, "광고 댓글 반복", 201);

        Instant ends = jdbc.queryForObject("select ends_at from suspension", java.sql.Timestamp.class).toInstant();
        String until = DateTimeFormatter.ofPattern("yyyy. M. d. HH:mm").format(ends.atZone(ZoneId.of("Asia/Seoul")));
        login(PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"))
                .andExpect(jsonPath("$.message").value("정지된 계정입니다. " + until
                        + "까지 정지입니다. 사유: 광고 댓글 반복. 운영자에게 문의해 주세요"));
        login("wrong1!a")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다"));
        mvc.perform(get("/api/auth/me").cookie(member.session())).andExpect(status().isUnauthorized());
        // 쓴 글은 그대로 보인다
        mvc.perform(get("/api/blogs/" + member.blogId())).andExpect(status().isOk());
    }

    @Test
    void 영구_정지는_영구_정지_안내이고_사유가_없으면_사유를_빼고_해제하면_다시_로그인한다() throws Exception {
        LoggedIn admin = adminLogin("admin@example.com");
        LoggedIn member = signupAndLogin("회원", EMAIL);
        suspend(admin, member.memberId(), 3, "", 201);
        suspend(admin, member.memberId(), null, "", 201);
        login(PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("정지된 계정입니다. 영구 정지입니다. 운영자에게 문의해 주세요"));

        mvc.perform(get("/api/admin/members?status=SUSPENDED").cookie(admin.session()))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].suspension.endsAt").doesNotExist())
                .andExpect(jsonPath("$.items[0].suspensionCount").value(2));

        mvc.perform(delete("/api/admin/members/" + member.memberId() + "/suspensions").with(csrf()).cookie(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liftedCount").value(2));
        assertThat(jdbc.queryForObject("select count(*) from suspension where lifted_by is not null", Long.class)).isEqualTo(2);
        mvc.perform(get("/api/admin/members/" + member.memberId() + "/suspensions").cookie(admin.session()))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].permanent").value(true))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].liftedBy").value("운영자"));
        login(PASSWORD).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/members/" + member.memberId() + "/suspensions").with(csrf()).cookie(admin.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("정지 중인 회원이 아닙니다"));
    }

    @Test
    void 기간이_끝난_정지는_저절로_풀리고_다른_길로_남은_세션도_다음_요청에서_끊긴다() throws Exception {
        LoggedIn admin = adminLogin("admin@example.com");
        LoggedIn member = signupAndLogin("회원", EMAIL);
        // 지난 정지 기록 → 로그인된다
        jdbc.update("""
                insert into suspension (member_id, reason, starts_at, ends_at, created_by)
                values (?, '지난 정지', now() - interval '4 days', now() - interval '1 minute', ?)""",
                member.memberId(), admin.memberId());
        login(PASSWORD).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(member.session())).andExpect(status().isOk());

        // 세션을 지우지 않고 정지 기록만 넣어도 다음 요청에서 끊긴다
        jdbc.update("""
                insert into suspension (member_id, reason, starts_at, ends_at, created_by)
                values (?, '', now(), now() + interval '3 days', ?)""", member.memberId(), admin.memberId());
        mvc.perform(get("/api/auth/me").cookie(member.session())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(member.session())).andExpect(status().isUnauthorized());
    }

    @Test
    void 회원_목록은_상태별로_거르고_검색하며_탈퇴한_회원의_개인정보는_가린다() throws Exception {
        LoggedIn admin = adminLogin("admin@example.com");
        LoggedIn active = signupAndLogin("활동회원", EMAIL);
        LoggedIn gone = signupAndLogin("떠난회원", "gone@example.com");
        LoggedIn bad = signupAndLogin("나쁜회원", "bad@example.com");
        createPost(active, "글", "PUBLIC");
        suspend(admin, bad.memberId(), 30, "욕설", 201);
        mvc.perform(delete("/api/me").with(csrf()).cookie(gone.session()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("password", PASSWORD, "agreed", true))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/members").cookie(admin.session()))
                .andExpect(jsonPath("$.counts.ALL").value(4))
                .andExpect(jsonPath("$.counts.ACTIVE").value(1))
                .andExpect(jsonPath("$.counts.SUSPENDED").value(1))
                .andExpect(jsonPath("$.counts.WITHDRAWN").value(1));
        mvc.perform(get("/api/admin/members?status=ACTIVE").cookie(admin.session()))
                .andExpect(jsonPath("$.items[0].nickname").value("활동회원"))
                .andExpect(jsonPath("$.items[0].email").value(EMAIL))
                .andExpect(jsonPath("$.items[0].postCount").value(1))
                .andExpect(jsonPath("$.items[0].blog.name").value("활동회원의 블로그"));
        mvc.perform(get("/api/admin/members?status=WITHDRAWN").cookie(admin.session()))
                .andExpect(jsonPath("$.items[0].id").value(gone.memberId()))
                .andExpect(jsonPath("$.items[0].nickname").doesNotExist())
                .andExpect(jsonPath("$.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.items[0].canSuspend").value(false));
        mvc.perform(get("/api/admin/members?status=SUSPENDED").cookie(admin.session()))
                .andExpect(jsonPath("$.items[0].suspension.reason").value("욕설"));
        mvc.perform(get("/api/admin/members?q=나쁜").cookie(admin.session()))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(bad.memberId()));
        mvc.perform(get("/api/admin/members?q=bad@").cookie(admin.session()))
                .andExpect(jsonPath("$.items", hasSize(1)));

        // 탈퇴한 회원·없는 회원은 정지할 수 없다. 기간은 3·7·30·영구만
        suspend(admin, gone.memberId(), 3, "", 409);
        suspend(admin, 999999L, 3, "", 404);
        suspend(admin, active.memberId(), 10, "", 400);
        suspend(admin, active.memberId(), 3, "가".repeat(201), 400);
    }
}
