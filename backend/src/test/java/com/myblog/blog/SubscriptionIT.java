package com.myblog.blog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 구독과 구독 피드, 햄버거 메뉴의 구독한 블로그 (FR-067, FR-068, BR-12) */
class SubscriptionIT extends IntegrationTestBase {

    private LoggedIn alice;
    private LoggedIn bob;
    private LoggedIn carol;

    @BeforeEach
    void setUp() throws Exception {
        alice = signupAndLogin("앨리스", "alice@example.com");
        bob = signupAndLogin("밥돌이", "bob@example.com");
        carol = signupAndLogin("캐롤", "carol@example.com");
    }

    @Test
    void 내_블로그는_구독할_수_없다() throws Exception {
        mvc.perform(put("/api/blogs/" + alice.blogId() + "/subscription").with(csrf()).cookie(alice.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("내 블로그는 구독할 수 없습니다"));
        mvc.perform(put("/api/blogs/" + bob.blogId() + "/subscription").with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/blogs/999999/subscription").with(csrf()).cookie(alice.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 구독하고_취소하면_구독자_수가_바뀐다() throws Exception {
        mvc.perform(put("/api/blogs/" + bob.blogId() + "/subscription").with(csrf()).cookie(alice.session()))
                .andExpect(jsonPath("$.subscribed").value(true))
                .andExpect(jsonPath("$.subscriberCount").value(1));
        // 한 번 더 보내도 그대로
        mvc.perform(put("/api/blogs/" + bob.blogId() + "/subscription").with(csrf()).cookie(alice.session()))
                .andExpect(jsonPath("$.subscriberCount").value(1));
        mvc.perform(put("/api/blogs/" + bob.blogId() + "/subscription").with(csrf()).cookie(carol.session()))
                .andExpect(jsonPath("$.subscriberCount").value(2));

        mvc.perform(get("/api/blogs/" + bob.blogId()).cookie(alice.session()))
                .andExpect(jsonPath("$.subscriberCount").value(2))
                .andExpect(jsonPath("$.subscribedByMe").value(true));
        mvc.perform(get("/api/blogs/" + bob.blogId()))
                .andExpect(jsonPath("$.subscriberCount").value(2))
                .andExpect(jsonPath("$.subscribedByMe").value(false));

        mvc.perform(delete("/api/blogs/" + bob.blogId() + "/subscription").with(csrf()).cookie(alice.session()))
                .andExpect(jsonPath("$.subscribed").value(false))
                .andExpect(jsonPath("$.subscriberCount").value(1));
    }

    @Test
    void 구독_피드에는_구독한_블로그의_공개_글만_나온다() throws Exception {
        long bobPublic = createPost(bob, "밥의 공개 글", "PUBLIC");
        createPost(bob, "밥의 비공개 글", "PRIVATE");
        createPost(bob, Map.of("title", "밥의 임시저장", "body", "본문", "draft", true));
        long hiddenCategory = createCategory(bob, "숨김");
        mvc.perform(withJson(put("/api/categories/" + hiddenCategory + "/visibility"), Map.of("visibility", "PRIVATE"))
                        .cookie(bob.session()))
                .andExpect(status().isNoContent());
        createPost(bob, Map.of("title", "숨긴 분류의 글", "body", "본문", "visibility", "PUBLIC",
                "categoryId", hiddenCategory));
        createPost(carol, "캐롤의 글", "PUBLIC");

        mvc.perform(get("/api/feed").cookie(alice.session()))
                .andExpect(jsonPath("$.totalCount").value(0));

        mvc.perform(put("/api/blogs/" + bob.blogId() + "/subscription").with(csrf()).cookie(alice.session()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/feed").cookie(alice.session()))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].id").value(bobPublic));
        mvc.perform(get("/api/feed")).andExpect(status().isUnauthorized());
    }

    @Test
    void 구독한_블로그는_최근_구독한_것부터_나온다() throws Exception {
        mvc.perform(put("/api/blogs/" + bob.blogId() + "/subscription").with(csrf()).cookie(alice.session()));
        mvc.perform(put("/api/blogs/" + carol.blogId() + "/subscription").with(csrf()).cookie(alice.session()));
        mvc.perform(get("/api/me/subscriptions").cookie(alice.session()))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.items[0].blogId").value(carol.blogId()))
                .andExpect(jsonPath("$.items[0].ownerNickname").value("캐롤"))
                .andExpect(jsonPath("$.items[1].blogId").value(bob.blogId()));
        mvc.perform(get("/api/me/subscriptions?limit=1").cookie(alice.session()))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/me/subscriptions")).andExpect(status().isUnauthorized());
    }
}
