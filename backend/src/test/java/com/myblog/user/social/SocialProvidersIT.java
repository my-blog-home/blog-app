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
        "blog.social.github.client-secret=test-github-secret",
        "blog.public-url=https://ylog.example.com/"})
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
                // 공개 주소가 정해져 있으면 콜백은 요청 주소(http://localhost)가 아니라 공개 주소로 만든다
                .andExpect(header().string("Location",
                        containsString("redirect_uri=https://ylog.example.com/login/oauth2/code/github")));
        // 링크 미리보기 주소도 공개 주소로 만든다
        mvc.perform(get("/notices"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        containsString("<meta property=\"og:url\" content=\"https://ylog.example.com/notices\" />")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        containsString("<meta property=\"og:image\" content=\"https://ylog.example.com/og-default.png\" />")));
        // 시크릿이 없는 카카오는 켜지지 않아 카카오로 보내지 않는다
        mvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(header().doesNotExist("Location"));
    }
}
