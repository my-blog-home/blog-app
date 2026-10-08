package com.myblog.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 인증번호 규칙과 가입 시 서버 재확인 (FR-001~009, 헌법 II) */
class SignupVerificationIT extends IntegrationTestBase {

    private static final String EMAIL = "new@example.com";

    @Test
    void 인증번호는_혼동문자_없는_6자리이고_맞으면_폐기된다() throws Exception {
        requestCode().andExpect(status().isNoContent());
        String code = codeFor("signup", EMAIL);
        assertThat(code).matches("^[A-HJ-NP-Z2-9]{6}$");

        confirm(code).andExpect(status().isNoContent());
        confirm(code).andExpect(status().isGone());
    }

    @Test
    void 일분_안에_다시_받을_수_없다() throws Exception {
        requestCode().andExpect(status().isNoContent());
        requestCode().andExpect(status().isTooManyRequests());
    }

    @Test
    void 하루_5번까지만_받을_수_있다() throws Exception {
        for (int i = 0; i < 5; i++) {
            requestCode().andExpect(status().isNoContent());
            redis.delete("verify:signup:" + EMAIL + ":cooldown");
        }
        requestCode().andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("오늘은 더 이상 인증번호를 보낼 수 없습니다"));
    }

    @Test
    void 연속_5회_틀리면_번호를_쓸_수_없다() throws Exception {
        requestCode();
        String code = codeFor("signup", EMAIL);
        for (int i = 0; i < 4; i++) {
            confirm("WRONG2").andExpect(status().isBadRequest());
        }
        confirm("WRONG2").andExpect(status().isGone());
        confirm(code).andExpect(status().isGone());
    }

    @Test
    void 인증없이_가입요청만_보내면_거절한다() throws Exception {
        mvc.perform(jsonPost("/api/auth/signup", Map.of("nickname", "지영", "email", EMAIL,
                        "password", PASSWORD, "passwordConfirm", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 인증표시가_만료되면_가입을_거절한다() throws Exception {
        requestCode();
        confirm(codeFor("signup", EMAIL));
        redis.delete("verify:signup:" + EMAIL + ":verified"); // 30분이 지난 상태
        mvc.perform(jsonPost("/api/auth/signup", Map.of("nickname", "지영", "email", EMAIL,
                        "password", PASSWORD, "passwordConfirm", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 가입하면_블로그와_미분류가_생기고_같은_이메일로는_메일을_보내지_않는다() throws Exception {
        signup("지영", EMAIL);
        assertThat(jdbc.queryForObject("select name from blog", String.class)).isEqualTo("지영의 블로그");
        assertThat(jdbc.queryForObject("select name from category where is_default", String.class)).isEqualTo("미분류");

        redis.delete("verify:signup:" + EMAIL + ":cooldown");
        mvc.perform(jsonPost("/api/auth/signup/verification", Map.of("nickname", "다른이", "email", "NEW@example.com")))
                .andExpect(status().isConflict());
        assertThat(codeFor("signup", EMAIL)).isNull();
    }

    @Test
    void 비밀번호는_8자_이상이면_길이_제한_없이_가입된다() throws Exception {
        requestCode();
        confirm(codeFor("signup", EMAIL));
        String longPassword = "abcdefgh1234!@#$";
        mvc.perform(jsonPost("/api/auth/signup", Map.of("nickname", "지영", "email", EMAIL,
                        "password", longPassword, "passwordConfirm", longPassword)))
                .andExpect(status().isCreated());
    }

    @Test
    void 비밀번호가_8자보다_짧으면_거절한다() throws Exception {
        requestCode();
        confirm(codeFor("signup", EMAIL));
        mvc.perform(jsonPost("/api/auth/signup", Map.of("nickname", "지영", "email", EMAIL,
                        "password", "ab1!", "passwordConfirm", "ab1!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("비밀번호는 영문, 숫자, 특수문자를 포함해 8자 이상으로 입력해 주세요"));
    }

    private org.springframework.test.web.servlet.ResultActions requestCode() throws Exception {
        return mvc.perform(jsonPost("/api/auth/signup/verification", Map.of("nickname", "지영", "email", EMAIL)));
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String code) throws Exception {
        return mvc.perform(jsonPost("/api/auth/signup/verification/confirm", Map.of("email", EMAIL, "code", code)));
    }
}
