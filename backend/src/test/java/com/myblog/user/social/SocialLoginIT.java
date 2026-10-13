package com.myblog.user.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.Cookie;

/**
 * 소셜 로그인 규칙: 연결된 계정으로 로그인, 같은 인증 이메일이면 기존 계정에 연결, 처음이면 닉네임·약관 동의 후 가입.
 * 제공자 화면을 거치는 부분은 SocialProvidersIT, 여기서는 제공자에게서 받은 정보부터 확인한다.
 */
class SocialLoginIT extends IntegrationTestBase {

    @Autowired
    SocialLoginService social;

    private static SocialProfile github(String id, String email, boolean verified) {
        return new SocialProfile("github", id, email, verified, "깃허브");
    }

    @Autowired
    @SuppressWarnings("rawtypes")
    org.springframework.session.SessionRepository sessions;

    /** 제공자에서 돌아온 직후처럼, 처음 가입 정보가 담긴 세션(Spring Session JDBC)을 만들고 그 쿠키를 돌려준다 */
    @SuppressWarnings("unchecked")
    private Cookie pendingSession(SocialProfile profile) {
        org.springframework.session.Session session = (org.springframework.session.Session) sessions.createSession();
        session.setAttribute(SocialAuthHandlers.PENDING, profile);
        session.setAttribute(SocialAuthHandlers.PENDING_NICKNAME, "깃허브");
        sessions.save(session);
        return new Cookie("SESSION", java.util.Base64.getEncoder().encodeToString(session.getId().getBytes()));
    }

    /** 로그인하면 세션 ID가 바뀌므로 응답의 새 쿠키를 쓴다 */
    private static Cookie sessionOf(org.springframework.test.web.servlet.MvcResult result, Cookie before) {
        Cookie after = result.getResponse().getCookie("SESSION");
        return after == null ? before : after;
    }

    @Test
    void 처음이면_닉네임과_약관_동의를_받아_비밀번호_없는_회원과_블로그를_만든다() throws Exception {
        SocialProfile profile = github("1001", "New@Example.com", true);
        SocialLoginService.Outcome outcome = social.login(profile);
        assertThat(outcome).isInstanceOf(SocialLoginService.NeedsSignup.class);
        assertThat(((SocialLoginService.NeedsSignup) outcome).suggestedNickname()).isEqualTo("깃허브");

        Cookie session = pendingSession(profile);
        mvc.perform(get("/api/auth/social/pending").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("github"))
                .andExpect(jsonPath("$.suggestedNickname").value("깃허브"));
        // 약관에 동의하지 않으면 가입하지 않는다
        mvc.perform(jsonPost("/api/auth/social/complete", Map.of("nickname", "깃허브", "agreed", false)).cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("agreed"));
        session = sessionOf(mvc.perform(jsonPost("/api/auth/social/complete", Map.of("nickname", "깃허브", "agreed", true))
                        .cookie(session))
                .andExpect(status().isOk()).andReturn(), session);

        Map<String, Object> row = jdbc.queryForMap("select id, email, password_hash from member where nickname = '깃허브'");
        assertThat(row.get("email")).isEqualTo("new@example.com");
        assertThat(row.get("password_hash")).isEqualTo("!");
        assertThat(jdbc.queryForObject("select count(*) from blog where owner_id = ?", Long.class, row.get("id"))).isEqualTo(1);
        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(jsonPath("$.nickname").value("깃허브"))
                .andExpect(jsonPath("$.hasPassword").value(false));

        // 다음부터는 같은 소셜 계정으로 바로 로그인한다
        assertThat(social.login(profile)).isEqualTo(new SocialLoginService.LoggedIn(((Number) row.get("id")).longValue(), false));
        // 가입 마무리는 한 번뿐이다
        mvc.perform(jsonPost("/api/auth/social/complete", Map.of("nickname", "다른이름", "agreed", true)).cookie(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void 인증된_이메일이_기존_회원과_같으면_새로_만들지_않고_연결한다() throws Exception {
        LoggedIn existing = signupAndLogin("기존회원", "same@example.com");
        SocialProfile profile = github("2002", "SAME@example.com", true);

        assertThat(social.login(profile)).isEqualTo(new SocialLoginService.LoggedIn(existing.memberId(), true));
        assertThat(social.login(profile)).isEqualTo(new SocialLoginService.LoggedIn(existing.memberId(), false));
        assertThat(jdbc.queryForObject("select count(*) from member", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select member_id from member_social where provider_user_id = '2002'", Long.class))
                .isEqualTo(existing.memberId());
    }

    @Test
    void 제공자가_인증한_이메일이_없으면_가입도_연결도_하지_않는다() {
        assertThat(social.login(github("3003", null, false))).isEqualTo(new SocialLoginService.Rejected("email"));
        assertThat(social.login(github("3004", "notverified@example.com", false)))
                .isEqualTo(new SocialLoginService.Rejected("email"));
    }

    @Test
    void 정지된_회원은_소셜_로그인도_막는다() throws Exception {
        LoggedIn member = signupAndLogin("정지회원", "stop@example.com");
        jdbc.update("insert into suspension (member_id, starts_at, ends_at, reason, created_by) values (?, now(), null, '스팸', ?)",
                member.memberId(), member.memberId());
        assertThat(social.login(github("4004", "stop@example.com", true))).isEqualTo(new SocialLoginService.Rejected("suspended"));
    }

    @Test
    void 비밀번호_없이_가입한_회원은_동의만으로_탈퇴하고_연결도_지운다() throws Exception {
        SocialProfile profile = github("5005", "bye@example.com", true);
        Cookie session = pendingSession(profile);
        session = sessionOf(mvc.perform(jsonPost("/api/auth/social/complete", Map.of("nickname", "잘가요", "agreed", true))
                        .cookie(session))
                .andExpect(status().isOk()).andReturn(), session);
        mvc.perform(withJson(delete("/api/me"), Map.of("password", "", "agreed", true)).cookie(session).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from member_social", Long.class)).isZero();
    }

    @Test
    void 가입_중인_정보가_없으면_마무리할_수_없고_설정이_없으면_제공자가_없다() throws Exception {
        mvc.perform(get("/api/auth/social/pending")).andExpect(status().isNotFound());
        mvc.perform(jsonPost("/api/auth/social/complete", Map.of("nickname", "아무개", "agreed", true)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/auth/social/providers")).andExpect(jsonPath("$.providers").isEmpty());
    }

    @Test
    void 카카오는_이메일_없이_회원번호와_닉네임만으로_가입하고_이메일로_연결하지_않는다() throws Exception {
        LoggedIn existing = signupAndLogin("기존회원", "mine@example.com");
        // 카카오는 이메일을 주지 않는다: 기존 회원과 이메일로 연결하지 않고 새로 가입시킨다
        SocialProfile kakao = new SocialProfile("kakao", "777", null, false, "카카오닉");
        assertThat(social.login(kakao)).isInstanceOf(SocialLoginService.NeedsSignup.class);

        Cookie session = pendingSession(kakao);
        mvc.perform(get("/api/auth/social/pending").cookie(session))
                .andExpect(jsonPath("$.provider").value("kakao"))
                .andExpect(jsonPath("$.email").doesNotExist());
        session = sessionOf(mvc.perform(jsonPost("/api/auth/social/complete", Map.of("nickname", "카카오닉", "agreed", true))
                        .cookie(session))
                .andExpect(status().isOk()).andReturn(), session);
        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(jsonPath("$.nickname").value("카카오닉"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.hasPassword").value(false));
        long kakaoMember = jdbc.queryForObject("select id from member where nickname = '카카오닉'", Long.class);
        assertThat(kakaoMember).isNotEqualTo(existing.memberId());
        assertThat(jdbc.queryForObject("select email from member where id = ?", String.class, kakaoMember))
                .isEqualTo("kakao_777@social.invalid");

        // 다음부터는 카카오 회원번호로 그 회원에 로그인한다
        assertThat(social.login(kakao)).isEqualTo(new SocialLoginService.LoggedIn(kakaoMember, false));
        // GitHub처럼 이메일을 주는 제공자는 인증 이메일이 없으면 여전히 가입하지 않는다
        assertThat(social.login(github("9009", null, false))).isEqualTo(new SocialLoginService.Rejected("email"));
    }
}
