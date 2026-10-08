package com.myblog.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 로컬 PostgreSQL(myblog_test)과 Redis(15번 DB)를 쓰는 통합 테스트 기반.
 * 테스트마다 데이터를 비운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "abc123!@";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected StringRedisTemplate redis;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanUp() {
        // suspension·report 등 회원을 가리키는 표는 CASCADE로 함께 비워진다
        jdbc.execute("TRUNCATE member, suspension, spring_session, search_log RESTART IDENTITY CASCADE");
        // 처음 넣은 안내(created_by 없음)는 남기고, 테스트에서 관리자가 쓴 공지만 지운다
        jdbc.update("DELETE FROM notice WHERE created_by IS NOT NULL");
        redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    protected MockHttpServletRequestBuilder jsonPost(String url, Object body) throws Exception {
        return post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    /** PATCH·PUT 같은 요청에 JSON 본문과 CSRF를 붙인다 */
    protected MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return builder.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    protected String codeFor(String purpose, String email) {
        return redis.opsForValue().get("verify:" + purpose + ":" + email + ":code");
    }

    /** 인증번호 받기 → 확인 → 가입 */
    protected void signup(String nickname, String email) throws Exception {
        mvc.perform(jsonPost("/api/auth/signup/verification", Map.of("nickname", nickname, "email", email)))
                .andExpect(status().isNoContent());
        mvc.perform(jsonPost("/api/auth/signup/verification/confirm",
                        Map.of("email", email, "code", codeFor("signup", email))))
                .andExpect(status().isNoContent());
        mvc.perform(jsonPost("/api/auth/signup", Map.of("nickname", nickname, "email", email,
                        "password", PASSWORD, "passwordConfirm", PASSWORD)))
                .andExpect(status().isCreated());
    }

    /** 로그인한 회원. 요청에 session 쿠키를 붙여 쓴다 */
    protected record LoggedIn(Cookie session, long memberId, long blogId) {
    }

    protected LoggedIn signupAndLogin(String nickname, String email) throws Exception {
        signup(nickname, email);
        MvcResult result = mvc.perform(jsonPost("/api/auth/login", Map.of("email", email, "password", PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode me = json.readTree(result.getResponse().getContentAsString());
        Cookie session = result.getResponse().getCookie("SESSION");
        return new LoggedIn(session, me.get("id").asLong(), me.get("blogId").asLong());
    }

    /**
     * 관리자로 로그인한다. 관리자는 가입으로 만들 수 없으므로 가입한 회원을 관리자로 바꾸고 블로그를 지운다 (FR-078).
     * blogId는 0이다(블로그 없음)
     */
    protected LoggedIn adminLogin(String email) throws Exception {
        signup("운영자", email);
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE email = ?", email);
        jdbc.update("DELETE FROM blog WHERE owner_id = (SELECT id FROM member WHERE email = ?)", email);
        MvcResult result = mvc.perform(jsonPost("/api/auth/login", Map.of("email", email, "password", PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode me = json.readTree(result.getResponse().getContentAsString());
        return new LoggedIn(result.getResponse().getCookie("SESSION"), me.get("id").asLong(), 0);
    }

    protected long createPost(LoggedIn user, String title, String visibility) throws Exception {
        return createPost(user, Map.of("title", title, "body", title + " 본문", "visibility", visibility));
    }

    /** 요청 본문을 그대로 보내 글을 만든다 (주제·분류·임시저장 등) */
    protected long createPost(LoggedIn user, Map<String, ?> body) throws Exception {
        MvcResult result = mvc.perform(jsonPost("/api/blogs/" + user.blogId() + "/posts", body)
                        .cookie(user.session()))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    protected long createCategory(LoggedIn user, String name) throws Exception {
        MvcResult result = mvc.perform(jsonPost("/api/blogs/" + user.blogId() + "/categories", Map.of("name", name))
                        .cookie(user.session()))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
