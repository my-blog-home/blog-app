package com.myblog.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 마이페이지·비밀번호 변경·탈퇴 (FR-016~019) */
class AccountManagementIT extends IntegrationTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private com.myblog.user.service.RejoinPolicy rejoinPolicy;

    private static final String EMAIL = "member@example.com";

    @Test
    void 닉네임과_소개를_바꾸고_남의_닉네임은_쓸_수_없다() throws Exception {
        LoggedIn me = signupAndLogin("나나", EMAIL);
        signup("너너", "other@example.com");
        send(patch("/api/me"), me.session(), Map.of("nickname", "나나", "bio", "안녕하세요"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("안녕하세요"));
        send(patch("/api/me"), me.session(), Map.of("nickname", "너너", "bio", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 비밀번호를_바꾸면_다른_기기만_끊긴다() throws Exception {
        LoggedIn first = signupAndLogin("회원", EMAIL);
        Cookie second = login();
        send(put("/api/me/password"), first.session(), Map.of("currentPassword", PASSWORD,
                "newPassword", "new456#$", "newPasswordConfirm", "new456#$"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(first.session())).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(second)).andExpect(status().isUnauthorized());
    }

    @Test
    void 현재_비밀번호와_같은_값으로는_바꿀_수_없다() throws Exception {
        LoggedIn me = signupAndLogin("회원", EMAIL);
        send(put("/api/me/password"), me.session(), Map.of("currentPassword", PASSWORD,
                "newPassword", PASSWORD, "newPasswordConfirm", PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
    }

    @Test
    void 현재_비밀번호_오입력은_로그인_실패와_합산되어_잠긴다() throws Exception {
        LoggedIn me = signupAndLogin("회원", EMAIL);
        for (int i = 0; i < 3; i++) {
            mvc.perform(jsonPost("/api/auth/login", Map.of("email", EMAIL, "password", "wrong1!a")));
        }
        send(put("/api/me/password"), me.session(), Map.of("currentPassword", "wrong1!a",
                "newPassword", "new456#$", "newPasswordConfirm", "new456#$"))
                .andExpect(status().isBadRequest());
        send(put("/api/me/password"), me.session(), Map.of("currentPassword", "wrong1!a",
                "newPassword", "new456#$", "newPasswordConfirm", "new456#$"))
                .andExpect(status().isLocked());
    }

    @Test
    void 탈퇴하면_회원_행은_남고_내_블로그는_지워지고_남의_글_댓글은_탈퇴한_사용자로_남는다() throws Exception {
        LoggedIn author = signupAndLogin("글쓴이", "author@example.com");
        LoggedIn me = signupAndLogin("회원", EMAIL);
        long othersPost = createPost(author, "남의 글", "PUBLIC");
        createPost(me, "내 글", "PUBLIC");
        mvc.perform(jsonPost("/api/posts/" + othersPost + "/comments", Map.of("content", "댓글")).cookie(me.session()))
                .andExpect(status().isCreated());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/posts/" + othersPost + "/like")
                .with(csrf()).cookie(me.session()));
        jdbc.update("insert into subscription (member_id, blog_id, created_at) values (?, ?, now())",
                me.memberId(), author.blogId());

        send(delete("/api/me"), me.session(), Map.of("password", PASSWORD, "agreed", true))
                .andExpect(status().isNoContent());

        Map<String, Object> row = jdbc.queryForMap("select * from member where id = ?", me.memberId());
        assertThat(row.get("withdrawn_at")).isNotNull();
        assertThat(row.get("email")).isEqualTo("del_" + me.memberId() + "_" + EMAIL);
        assertThat(row.get("original_email")).isEqualTo(EMAIL);
        assertThat(row.get("nickname")).isEqualTo("탈퇴" + me.memberId());
        assertThat(row.get("bio")).isNull();
        assertThat(row.get("password_hash")).isEqualTo("!");
        assertThat(jdbc.queryForObject("select count(*) from blog where owner_id = ?", Long.class, me.memberId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from post where title = '내 글'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from post_like where member_id = ?", Long.class, me.memberId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from subscription where member_id = ?", Long.class, me.memberId())).isZero();
        // 댓글은 회원 행을 계속 가리키고, 화면에는 탈퇴한 사용자로 보인다
        assertThat(jdbc.queryForObject("select author_id from comment", Long.class)).isEqualTo(me.memberId());
        mvc.perform(get("/api/posts/" + othersPost + "/comments").cookie(author.session()))
                .andExpect(jsonPath("$[0].authorNickname").doesNotExist())
                .andExpect(jsonPath("$[0].authorId").doesNotExist())
                .andExpect(jsonPath("$[0].authorWithdrawn").value(true))
                .andExpect(jsonPath("$[0].reportable").value(false))
                .andExpect(jsonPath("$[0].content").value("댓글"));
        mvc.perform(get("/api/auth/me").cookie(me.session())).andExpect(status().isUnauthorized());
        // 작성자 프로필은 없는 회원과 같다
        mvc.perform(get("/api/users/" + me.memberId())).andExpect(status().isNotFound());
        // 원래 이메일로도, del_ 이메일로도 로그인할 수 없다 (같은 실패 문구)
        mvc.perform(jsonPost("/api/auth/login", Map.of("email", EMAIL, "password", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다"));
        mvc.perform(jsonPost("/api/auth/login", Map.of("email", "del_" + me.memberId() + "_" + EMAIL, "password", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다"));
    }

    @Test
    void 탈퇴한_지_30일이_지나야_같은_이메일로_다시_가입하고_닉네임은_바로_쓸_수_있다() throws Exception {
        LoggedIn me = signupAndLogin("회원", EMAIL);
        send(delete("/api/me"), me.session(), Map.of("password", PASSWORD, "agreed", true))
                .andExpect(status().isNoContent());

        // 닉네임은 바로 다시 쓸 수 있다
        signup("회원", "other@example.com");

        // 탈퇴한 지 29일(한국 날짜): 인증번호를 보내지 않고 가입할 수 있는 날을 알린다
        jdbc.update("update member set withdrawn_at = now() - interval '29 days' where id = ?", me.memberId());
        String availableOn = jdbc.queryForObject("""
                select to_char(((withdrawn_at at time zone 'Asia/Seoul')::date + 30), 'FMYYYY. FMMM. FMDD.')
                from member where id = ?""", String.class, me.memberId());
        mvc.perform(jsonPost("/api/auth/signup/verification", Map.of("nickname", "새회원", "email", EMAIL)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REJOIN_WAIT"))
                .andExpect(jsonPath("$.message").value(
                        "탈퇴한 지 30일이 지나지 않아 같은 이메일로 다시 가입할 수 없습니다. " + availableOn + "부터 가입할 수 있습니다"));
        assertThat(codeFor("signup", EMAIL)).isNull();

        // 30일이 지나면 가입할 수 있다. 원래 이메일은 매일 작업이 지운다
        jdbc.update("update member set withdrawn_at = now() - interval '31 days' where id = ?", me.memberId());
        assertThat(rejoinPolicy.clearExpiredOriginalEmails()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select original_email from member where id = ?", String.class, me.memberId())).isNull();
        // 처음 가입할 때 보낸 인증번호의 재전송 대기는 지운다 (실제로는 30일 뒤라 이미 풀려 있다)
        redis.delete("verify:signup:" + EMAIL + ":cooldown");
        signup("새회원", EMAIL);
    }

    @Test
    void 탈퇴한_이메일의_비밀번호_찾기는_가입하지_않은_이메일과_같다() throws Exception {
        LoggedIn me = signupAndLogin("회원", EMAIL);
        send(delete("/api/me"), me.session(), Map.of("password", PASSWORD, "agreed", true))
                .andExpect(status().isNoContent());
        // 가입하지 않은 이메일과 같은 응답이 나오고, 끝까지 가도 탈퇴한 회원의 비밀번호는 바뀌지 않는다
        for (String email : new String[] {EMAIL, "del_" + me.memberId() + "_" + EMAIL, "nobody@example.com"}) {
            mvc.perform(jsonPost("/api/auth/password-reset/verification", Map.of("email", email)))
                    .andExpect(status().isNoContent());
            mvc.perform(jsonPost("/api/auth/password-reset/verification/confirm",
                    Map.of("email", email, "code", codeFor("reset", email)))).andExpect(status().isNoContent());
            mvc.perform(jsonPost("/api/auth/password-reset", Map.of("email", email,
                    "newPassword", "new456#$", "newPasswordConfirm", "new456#$"))).andExpect(status().isNoContent());
        }
        assertThat(jdbc.queryForObject("select password_hash from member where id = ?", String.class, me.memberId()))
                .isEqualTo("!");
        mvc.perform(jsonPost("/api/auth/login", Map.of("email", "del_" + me.memberId() + "_" + EMAIL,
                "password", "new456#$"))).andExpect(status().isUnauthorized());
    }

    @Test
    void 탈퇴_닉네임_모양은_가입에_쓸_수_없다() throws Exception {
        mvc.perform(jsonPost("/api/auth/signup/verification", Map.of("nickname", "탈퇴12", "email", EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("쓸 수 없는 닉네임입니다"));
    }

    @Test
    void 비밀번호가_틀리면_탈퇴되지_않는다() throws Exception {
        LoggedIn me = signupAndLogin("회원", EMAIL);
        send(delete("/api/me"), me.session(), Map.of("password", "wrong1!a", "agreed", true))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/auth/me").cookie(me.session())).andExpect(status().isOk());
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                               Cookie session, Object body) throws Exception {
        return mvc.perform(builder.with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private Cookie login() throws Exception {
        return mvc.perform(jsonPost("/api/auth/login", Map.of("email", EMAIL, "password", PASSWORD)))
                .andReturn().getResponse().getCookie("SESSION");
    }
}
