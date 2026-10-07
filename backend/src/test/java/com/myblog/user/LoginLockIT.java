package com.myblog.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 로그인 잠금과 계정 존재 비노출 (FR-011, FR-012, 헌법 II) */
class LoginLockIT extends IntegrationTestBase {

    private static final String EMAIL = "member@example.com";

    @Test
    void 없는_이메일과_틀린_비밀번호의_응답이_같다() throws Exception {
        signup("회원", EMAIL);
        MvcResult unknown = login("nobody@example.com", PASSWORD).andExpect(status().isUnauthorized()).andReturn();
        MvcResult wrong = login(EMAIL, "wrong1!a").andExpect(status().isUnauthorized()).andReturn();
        assertThat(unknown.getResponse().getContentAsString()).isEqualTo(wrong.getResponse().getContentAsString());
    }

    @Test
    void 연속_5회_실패하면_맞는_비밀번호도_잠긴다() throws Exception {
        signup("회원", EMAIL);
        for (int i = 0; i < 4; i++) {
            login(EMAIL, "wrong1!a").andExpect(status().isUnauthorized());
        }
        login(EMAIL, "wrong1!a").andExpect(status().isLocked());
        login(EMAIL, PASSWORD).andExpect(status().isLocked())
                .andExpect(jsonPath("$.details.retryAfterMinutes").value(10));
    }

    @Test
    void 성공하면_실패_횟수가_0이_된다() throws Exception {
        signup("회원", EMAIL);
        for (int i = 0; i < 4; i++) {
            login(EMAIL, "wrong1!a");
        }
        login(EMAIL, PASSWORD).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select failed_login_count from member", Integer.class)).isZero();
    }

    @Test
    void 잠금_시간이_지나면_다시_로그인할_수_있다() throws Exception {
        signup("회원", EMAIL);
        for (int i = 0; i < 5; i++) {
            login(EMAIL, "wrong1!a");
        }
        jdbc.update("update member set locked_until = now() - interval '1 second'");
        login(EMAIL, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 없는_이메일은_잠기지_않는다() throws Exception {
        for (int i = 0; i < 6; i++) {
            login("nobody@example.com", PASSWORD).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void 로그인하면_세션이_새로_발급되고_로그아웃하면_끊긴다() throws Exception {
        LoggedIn user = signupAndLogin("회원", EMAIL);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me")
                .cookie(user.session())).andExpect(status().isOk());
        mvc.perform(jsonPost("/api/auth/logout", Map.of()).cookie(user.session())).andExpect(status().isNoContent());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me")
                .cookie(user.session())).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mvc.perform(jsonPost("/api/auth/login", Map.of("email", email, "password", password)));
    }
}
