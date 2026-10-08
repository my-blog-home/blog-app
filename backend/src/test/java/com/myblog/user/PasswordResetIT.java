package com.myblog.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 비밀번호 찾기는 가입 여부를 드러내지 않는다 (FR-020, FR-021, SC-005) */
class PasswordResetIT extends IntegrationTestBase {

    private static final String EMAIL = "member@example.com";
    private static final String NEW_PASSWORD = "new456#$";

    @Test
    void 가입한_이메일과_안한_이메일의_응답과_제한이_같다() throws Exception {
        signup("회원", EMAIL);
        MvcResult known = request(EMAIL).andExpect(status().isNoContent()).andReturn();
        MvcResult unknown = request("nobody@example.com").andExpect(status().isNoContent()).andReturn();
        assertThat(known.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());

        request(EMAIL).andExpect(status().isTooManyRequests());
        request("nobody@example.com").andExpect(status().isTooManyRequests());

        confirm("nobody@example.com", "AAAAAA").andExpect(status().isBadRequest());
        confirm(EMAIL, "AAAAAA").andExpect(status().isBadRequest());
    }

    @Test
    void 바꾸면_모든_기기가_끊기고_잠금이_풀리고_새_비밀번호로_로그인된다() throws Exception {
        LoggedIn device = signupAndLogin("회원", EMAIL);
        for (int i = 0; i < 5; i++) {
            mvc.perform(jsonPost("/api/auth/login", Map.of("email", EMAIL, "password", "wrong1!a")));
        }
        request(EMAIL);
        confirm(EMAIL, codeFor("reset", EMAIL)).andExpect(status().isNoContent());
        mvc.perform(jsonPost("/api/auth/password-reset", Map.of("email", EMAIL, "newPassword", NEW_PASSWORD,
                "newPasswordConfirm", NEW_PASSWORD))).andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").cookie(device.session())).andExpect(status().isUnauthorized());
        mvc.perform(jsonPost("/api/auth/login", Map.of("email", EMAIL, "password", PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(jsonPost("/api/auth/login", Map.of("email", EMAIL, "password", NEW_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void 인증없이는_바꿀_수_없다() throws Exception {
        signup("회원", EMAIL);
        mvc.perform(jsonPost("/api/auth/password-reset", Map.of("email", EMAIL, "newPassword", NEW_PASSWORD,
                "newPasswordConfirm", NEW_PASSWORD))).andExpect(status().isForbidden());
    }

    @Test
    void 가입용_인증으로_비밀번호를_바꿀_수_없다() throws Exception {
        signup("회원", EMAIL);
        redis.opsForValue().set("verify:signup:" + EMAIL + ":verified", "1");
        mvc.perform(jsonPost("/api/auth/password-reset", Map.of("email", EMAIL, "newPassword", NEW_PASSWORD,
                "newPasswordConfirm", NEW_PASSWORD))).andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions request(String email) throws Exception {
        return mvc.perform(jsonPost("/api/auth/password-reset/verification", Map.of("email", email)));
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String email, String code) throws Exception {
        return mvc.perform(jsonPost("/api/auth/password-reset/verification/confirm", Map.of("email", email, "code", code)));
    }
}
