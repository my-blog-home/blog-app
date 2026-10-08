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
    void 탈퇴하면_내_블로그는_지워지고_남의_글_댓글은_탈퇴한_사용자로_남고_재가입할_수_있다() throws Exception {
        LoggedIn author = signupAndLogin("글쓴이", "author@example.com");
        LoggedIn me = signupAndLogin("회원", EMAIL);
        long othersPost = createPost(author, "남의 글", "PUBLIC");
        createPost(me, "내 글", "PUBLIC");
        mvc.perform(jsonPost("/api/posts/" + othersPost + "/comments", Map.of("content", "댓글")).cookie(me.session()))
                .andExpect(status().isCreated());

        send(delete("/api/me"), me.session(), Map.of("password", PASSWORD, "agreed", true))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from blog where owner_id = ?", Long.class, me.memberId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from post where title = '내 글'", Long.class)).isZero();
        mvc.perform(get("/api/posts/" + othersPost + "/comments"))
                .andExpect(jsonPath("$[0].authorNickname").doesNotExist())
                .andExpect(jsonPath("$[0].content").value("댓글"));
        mvc.perform(get("/api/auth/me").cookie(me.session())).andExpect(status().isUnauthorized());

        redis.delete("verify:signup:" + EMAIL + ":cooldown"); // 1분 재발송 제한만 지운다
        signup("회원", EMAIL);
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
