package com.myblog.blog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 분류는 블로그 주인만 관리하고, 글이 있거나 미분류이면 지울 수 없다 (FR-024, FR-025) */
class CategoryAuthorizationIT extends IntegrationTestBase {

    @Test
    void 남의_블로그에_분류를_추가할_수_없다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        LoggedIn other = signupAndLogin("다른회원", "other@example.com");
        mvc.perform(jsonPost("/api/blogs/" + owner.blogId() + "/categories", Map.of("name", "여행"))
                        .cookie(other.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 같은_이름의_분류는_대소문자를_무시하고_막는다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        mvc.perform(jsonPost("/api/blogs/" + owner.blogId() + "/categories", Map.of("name", "Travel"))
                .cookie(owner.session())).andExpect(status().isCreated());
        mvc.perform(jsonPost("/api/blogs/" + owner.blogId() + "/categories", Map.of("name", "travel"))
                        .cookie(owner.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 있는 분류입니다"));
    }

    @Test
    void 글이_있는_분류와_미분류는_지울_수_없다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        createPost(owner, "미분류 글", "PUBLIC");
        Long defaultId = jdbc.queryForObject("select id from category where is_default", Long.class);
        mvc.perform(delete("/api/categories/" + defaultId).with(csrf()).cookie(owner.session()))
                .andExpect(status().isConflict());

        String created = mvc.perform(jsonPost("/api/blogs/" + owner.blogId() + "/categories", Map.of("name", "여행"))
                .cookie(owner.session())).andReturn().getResponse().getContentAsString();
        long travelId = json.readTree(created).get("id").asLong();
        mvc.perform(jsonPost("/api/blogs/" + owner.blogId() + "/posts",
                Map.of("title", "여행 글", "body", "본문", "categoryId", travelId)).cookie(owner.session()));
        mvc.perform(delete("/api/categories/" + travelId).with(csrf()).cookie(owner.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.postCount").value(1));
    }
}
