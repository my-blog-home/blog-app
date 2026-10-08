package com.myblog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import com.myblog.user.service.AdminAccountInitializer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 관리자 계정과 관리자 화면 접근 (FR-078) */
class AdminAccessIT extends IntegrationTestBase {

    @Autowired
    private AdminAccountInitializer initializer;

    @Autowired
    private com.myblog.user.domain.MemberRepository memberRepository;

    @Autowired
    private com.myblog.user.validation.InputRules rules;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private java.time.Clock clock;

    private AdminAccountInitializer initializer(String email, String password) {
        return new AdminAccountInitializer(memberRepository, rules, passwordEncoder, clock, email, password);
    }

    @Test
    void 관리자_이메일과_비밀번호가_둘_다_있을_때만_서버를_켤_때_관리자를_만든다() throws Exception {
        initializer("boss@example.com", "").run(null);
        initializer("", "boss12!@").run(null);
        assertThat(jdbc.queryForObject("select count(*) from member", Long.class)).isZero();
        initializer("boss@example.com", "boss12!@").run(null);
        initializer("boss@example.com", "boss12!@").run(null);
        assertThat(jdbc.queryForObject("select count(*) from member where role = 'ADMIN'", Long.class)).isEqualTo(1);
    }

    @Test
    void 가입으로_만든_계정은_항상_일반_회원이다() throws Exception {
        signup("회원", "member@example.com");
        assertThat(jdbc.queryForObject("select role from member", String.class)).isEqualTo("MEMBER");
    }

    @Test
    void 관리자_API는_비회원_401_일반_회원_403이다() throws Exception {
        LoggedIn member = signupAndLogin("회원", "member@example.com");
        for (String url : new String[] {"/api/admin/summary", "/api/admin/reports", "/api/admin/members"}) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            mvc.perform(get(url).cookie(member.session()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("관리자만 볼 수 있는 화면입니다"));
        }
        mvc.perform(patch("/api/admin/reports").with(csrf()).cookie(member.session())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reportIds\":[1],\"action\":\"REJECT\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(jsonPost("/api/admin/notices", Map.of("type", "NOTICE", "title", "t", "content", "c"))
                .cookie(member.session())).andExpect(status().isForbidden());
    }

    @Test
    void 관리자는_블로그가_없어도_되고_내_정보에_역할과_대기_신고_수가_나온다() throws Exception {
        LoggedIn admin = adminLogin("admin@example.com");
        LoggedIn member = signupAndLogin("회원", "member@example.com");
        mvc.perform(get("/api/auth/me").cookie(admin.session()))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.blogId").doesNotExist())
                .andExpect(jsonPath("$.pendingReportCount").value(0));
        mvc.perform(get("/api/auth/me").cookie(member.session()))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.pendingReportCount").doesNotExist());
        mvc.perform(get("/api/me").cookie(admin.session())).andExpect(status().isOk());
        mvc.perform(get("/api/admin/summary").cookie(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.PENDING").value(0));
    }

    @Test
    void 관리자는_탈퇴할_수_없고_정지할_수_없다() throws Exception {
        LoggedIn admin = adminLogin("admin@example.com");
        mvc.perform(delete("/api/me").with(csrf()).cookie(admin.session()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("password", PASSWORD, "agreed", true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("관리자 계정은 탈퇴할 수 없습니다"));
        mvc.perform(jsonPost("/api/admin/members/" + admin.memberId() + "/suspensions", Map.of("days", 3))
                        .cookie(admin.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("정지할 수 없는 회원입니다"));
    }

    @Test
    void 설정한_관리자_계정은_한_번만_만들어지고_블로그가_없다() throws Exception {
        assertThat(initializer.ensureAdmin("Boss@Example.com", "boss12!@")).isTrue();
        assertThat(initializer.ensureAdmin("boss@example.com", "boss12!@")).isFalse();
        Map<String, Object> row = jdbc.queryForMap("select id, role, nickname from member where email = 'boss@example.com'");
        assertThat(row.get("role")).isEqualTo("ADMIN");
        assertThat(row.get("nickname")).isEqualTo("운영자");
        assertThat(jdbc.queryForObject("select count(*) from blog where owner_id = ?", Long.class, row.get("id"))).isZero();
        mvc.perform(jsonPost("/api/auth/login", Map.of("email", "boss@example.com", "password", "boss12!@")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        // 규칙에 맞지 않는 비밀번호로는 만들지 않는다
        assertThat(initializer.ensureAdmin("weak@example.com", "short")).isFalse();
    }
}
