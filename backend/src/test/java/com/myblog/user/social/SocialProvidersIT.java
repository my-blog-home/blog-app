package com.myblog.user.social;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** 클라이언트 ID·시크릿이 있는 제공자만 켜지고, 그 제공자의 로그인 화면으로 보낸다 */
@TestPropertySource(properties = {
        "blog.social.github.client-id=test-github-id",
        "blog.social.github.client-secret=test-github-secret"})
class SocialProvidersIT extends IntegrationTestBase {

    @Test
    void 설정한_제공자만_켜지고_그_제공자로_보낸다() throws Exception {
        mvc.perform(get("/api/auth/social/providers"))
                .andExpect(jsonPath("$.providers.length()").value(1))
                .andExpect(jsonPath("$.providers[0]").value("github"));
        mvc.perform(get("/oauth2/authorization/github"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("https://github.com/login/oauth/authorize")))
                .andExpect(header().string("Location", containsString("client_id=test-github-id")))
                .andExpect(header().string("Location", containsString("/login/oauth2/code/github")));
        // 시크릿이 없는 카카오는 켜지지 않아 카카오로 보내지 않는다
        mvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(header().doesNotExist("Location"));
    }
}
