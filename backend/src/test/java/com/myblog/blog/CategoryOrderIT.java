package com.myblog.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.myblog.support.IntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 분류 순서를 끌어서 바꾼다. 주인만, 직접 만든 분류 전부를 보내야 하고, 미분류는 늘 맨 뒤다 (FR-17, CR-31, CR-56).
 * 내 블로그 화면의 분류 카드에 쓰는 임시저장 글 수도 확인한다 (FR-32)
 */
class CategoryOrderIT extends IntegrationTestBase {

    private MockHttpServletRequestBuilder order(LoggedIn user, List<Long> ids) throws Exception {
        return order(user, user.blogId(), ids);
    }

    private MockHttpServletRequestBuilder order(LoggedIn user, long blogId, List<Long> ids) throws Exception {
        return withJson(put("/api/blogs/" + blogId + "/categories/order"), Map.of("categoryIds", ids))
                .cookie(user.session());
    }

    private List<String> names(LoggedIn user) throws Exception {
        String body = mvc.perform(get("/api/blogs/" + user.blogId()).cookie(user.session()))
                .andReturn().getResponse().getContentAsString();
        List<String> names = new ArrayList<>();
        for (JsonNode c : json.readTree(body).get("categories")) {
            names.add(c.get("name").asText());
        }
        return names;
    }

    @Test
    void 보낸_순서대로_저장하고_미분류는_맨_뒤에_둔다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        long a = createCategory(owner, "가");
        long b = createCategory(owner, "나");
        long c = createCategory(owner, "다");

        mvc.perform(order(owner, List.of(c, a, b))).andExpect(status().isNoContent());

        assertThat(names(owner)).containsExactly("다", "가", "나", "미분류");
    }

    @Test
    void 주인이_아니면_바꿀_수_없다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        LoggedIn other = signupAndLogin("다른회원", "other@example.com");
        long a = createCategory(owner, "가");
        long b = createCategory(owner, "나");

        mvc.perform(order(other, owner.blogId(), List.of(b, a))).andExpect(status().isNotFound());
        mvc.perform(withJson(put("/api/blogs/" + owner.blogId() + "/categories/order"),
                Map.of("categoryIds", List.of(b, a)))).andExpect(status().isUnauthorized());

        assertThat(names(owner)).containsExactly("가", "나", "미분류");
    }

    @Test
    void 분류_목록이_맞지_않으면_400() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        long a = createCategory(owner, "가");
        long b = createCategory(owner, "나");
        Long defaultId = jdbc.queryForObject("select id from category where is_default and blog_id = ?", Long.class,
                owner.blogId());
        LoggedIn other = signupAndLogin("다른회원", "other@example.com");
        long foreign = createCategory(other, "남의분류");

        // 하나 빠짐, 겹침, 미분류 포함, 남의 분류
        mvc.perform(order(owner, List.of(a))).andExpect(status().isBadRequest());
        mvc.perform(order(owner, List.of(a, a))).andExpect(status().isBadRequest());
        mvc.perform(order(owner, List.of(defaultId, b, a))).andExpect(status().isBadRequest());
        mvc.perform(order(owner, List.of(b, defaultId))).andExpect(status().isBadRequest());
        mvc.perform(order(owner, List.of(b, foreign))).andExpect(status().isBadRequest());

        assertThat(names(owner)).containsExactly("가", "나", "미분류");
    }

    @Test
    void 임시저장_글_수는_주인에게만_보인다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        createPost(owner, Map.of("title", "쓰는 중", "body", "본문", "draft", true));
        createPost(owner, "공개 글", "PUBLIC");

        mvc.perform(get("/api/blogs/" + owner.blogId()).cookie(owner.session()))
                .andExpect(jsonPath("$.categories[0].postCount").value(1))
                .andExpect(jsonPath("$.categories[0].draftCount").value(1));
        mvc.perform(get("/api/blogs/" + owner.blogId()))
                .andExpect(jsonPath("$.categories[0].draftCount").value(0));
    }
}
