package com.myblog.blog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 분류 공개 범위·소개글과 "미분류" 규칙 (FR-17, BR-34, BR-46, CR-58) */
class CategoryVisibilityIT extends IntegrationTestBase {

    private void setVisibility(LoggedIn owner, long categoryId, String visibility) throws Exception {
        mvc.perform(withJson(put("/api/categories/" + categoryId + "/visibility"), Map.of("visibility", visibility))
                .cookie(owner.session())).andExpect(status().isNoContent());
    }

    @Test
    void 비공개_분류의_글은_방문자에게_어디에도_나오지_않는다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        LoggedIn other = signupAndLogin("다른회원", "other@example.com");
        long secret = createCategory(owner, "비밀");
        long first = createPost(owner, "공개 하나 검색어", "PUBLIC");
        long hidden = createPost(owner, Map.of("title", "숨은 글 검색어", "body", "본문", "categoryId", secret,
                "tags", List.of("숨김")));
        long third = createPost(owner, "공개 둘 검색어", "PUBLIC");
        setVisibility(owner, secret, "PRIVATE");

        for (var request : List.of(get("/api/blogs/" + owner.blogId()), get("/api/blogs/" + owner.blogId())
                .cookie(other.session()))) {
            mvc.perform(request)
                    .andExpect(jsonPath("$.totalPostCount").value(2))
                    .andExpect(jsonPath("$.categories.length()").value(1))
                    .andExpect(jsonPath("$.categories[0].name").value("미분류"));
        }
        mvc.perform(get("/api/blogs/" + owner.blogId() + "/posts")).andExpect(jsonPath("$.totalCount").value(2));
        mvc.perform(get("/api/posts")).andExpect(jsonPath("$.totalCount").value(2));
        mvc.perform(get("/api/search").param("q", "검색어")).andExpect(jsonPath("$.totalCount").value(2));
        mvc.perform(get("/api/tags/숨김/posts")).andExpect(jsonPath("$.totalCount").value(0));
        mvc.perform(get("/api/topics")).andExpect(jsonPath("$[8].postCount").value(2));
        mvc.perform(get("/api/posts/" + hidden).cookie(other.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 글입니다"));
        mvc.perform(get("/api/blogs/" + owner.blogId() + "/posts").param("categoryId", String.valueOf(secret)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 분류입니다"));
        mvc.perform(get("/api/blogs/" + owner.blogId() + "/posts").param("categoryId", "99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 분류입니다"));
        mvc.perform(get("/api/posts/" + first)).andExpect(jsonPath("$.nextPostId").value(third));
        mvc.perform(get("/api/posts/" + third)).andExpect(jsonPath("$.prevPostId").value(first));

        // 주인에게는 비공개 분류와 그 글이 보인다
        mvc.perform(get("/api/blogs/" + owner.blogId()).cookie(owner.session()))
                .andExpect(jsonPath("$.totalPostCount").value(3))
                .andExpect(jsonPath("$.categories[0].name").value("비밀"))
                .andExpect(jsonPath("$.categories[0].visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.categories[0].publicPostCount").value(1));
        mvc.perform(get("/api/blogs/" + owner.blogId() + "/posts").param("categoryId", String.valueOf(secret))
                .cookie(owner.session())).andExpect(jsonPath("$.totalCount").value(1));
        mvc.perform(get("/api/posts/" + hidden).cookie(owner.session())).andExpect(status().isOk());

        // 다시 공개하면 글의 원래 설정대로 돌아온다
        setVisibility(owner, secret, "PUBLIC");
        mvc.perform(get("/api/blogs/" + owner.blogId() + "/posts")).andExpect(jsonPath("$.totalCount").value(3));
        mvc.perform(get("/api/posts/" + hidden)).andExpect(status().isOk());

        mvc.perform(withJson(put("/api/categories/" + secret + "/visibility"), Map.of("visibility", "HIDDEN"))
                .cookie(owner.session())).andExpect(status().isBadRequest());
        mvc.perform(withJson(put("/api/categories/" + secret + "/visibility"), Map.of("visibility", "PRIVATE"))
                .cookie(other.session())).andExpect(status().isNotFound());
    }

    @Test
    void 미분류도_비공개로_할_수_있다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        createPost(owner, "글", "PUBLIC");
        long defaultId = jdbc.queryForObject("select id from category where is_default", Long.class);
        setVisibility(owner, defaultId, "PRIVATE");
        mvc.perform(get("/api/blogs/" + owner.blogId()))
                .andExpect(jsonPath("$.categories.length()").value(0))
                .andExpect(jsonPath("$.totalPostCount").value(0));
        mvc.perform(get("/api/posts")).andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void 미분류는_항상_맨_뒤이고_옮길_수_없다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        long a = createCategory(owner, "A");
        long b = createCategory(owner, "B");
        long defaultId = jdbc.queryForObject("select id from category where is_default", Long.class);
        mvc.perform(get("/api/blogs/" + owner.blogId()))
                .andExpect(jsonPath("$.categories[0].name").value("A"))
                .andExpect(jsonPath("$.categories[1].name").value("B"))
                .andExpect(jsonPath("$.categories[2].name").value("미분류"));

        mvc.perform(jsonPost("/api/categories/" + b + "/move", Map.of("direction", "UP")).cookie(owner.session()))
                .andExpect(status().isNoContent());
        // 직접 만든 분류의 맨 끝에서 아래로 내려도 미분류 뒤로 가지 않는다
        mvc.perform(jsonPost("/api/categories/" + a + "/move", Map.of("direction", "DOWN")).cookie(owner.session()))
                .andExpect(status().isNoContent());
        mvc.perform(jsonPost("/api/categories/" + defaultId + "/move", Map.of("direction", "UP")).cookie(owner.session()))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/blogs/" + owner.blogId()))
                .andExpect(jsonPath("$.categories[0].name").value("B"))
                .andExpect(jsonPath("$.categories[1].name").value("A"))
                .andExpect(jsonPath("$.categories[2].name").value("미분류"));
    }

    @Test
    void 미분류라는_이름은_다른_분류에_쓸_수_없다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        long a = createCategory(owner, "A");
        long defaultId = jdbc.queryForObject("select id from category where is_default", Long.class);
        mvc.perform(withJson(patch("/api/categories/" + defaultId), Map.of("name", "기타")).cookie(owner.session()))
                .andExpect(status().isNoContent());

        mvc.perform(jsonPost("/api/blogs/" + owner.blogId() + "/categories", Map.of("name", " 미분류 "))
                        .cookie(owner.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("미분류는 쓸 수 없는 이름입니다"));
        mvc.perform(withJson(patch("/api/categories/" + a), Map.of("name", "미분류")).cookie(owner.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("미분류는 쓸 수 없는 이름입니다"));
        // 기본 분류는 원래 이름으로 되돌릴 수 있다
        mvc.perform(withJson(patch("/api/categories/" + defaultId), Map.of("name", "미분류")).cookie(owner.session()))
                .andExpect(status().isNoContent());
    }

    @Test
    void 소개글은_앞뒤_공백을_지우고_100자까지이며_색은_안_쓴_첫_색이다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        String created = mvc.perform(jsonPost("/api/blogs/" + owner.blogId() + "/categories",
                        Map.of("name", "여행", "description", "  다녀온 곳  ")).cookie(owner.session()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long travel = json.readTree(created).get("id").asLong();
        long food = createCategory(owner, "음식");
        mvc.perform(get("/api/blogs/" + owner.blogId()))
                .andExpect(jsonPath("$.categories[0].description").value("다녀온 곳"))
                .andExpect(jsonPath("$.categories[0].colorIndex").value(1))
                .andExpect(jsonPath("$.categories[1].colorIndex").value(2))
                .andExpect(jsonPath("$.categories[2].colorIndex").value(0));

        mvc.perform(withJson(patch("/api/categories/" + travel), Map.of("name", "여행", "description", "가".repeat(101)))
                        .cookie(owner.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("description"));
        mvc.perform(withJson(patch("/api/categories/" + travel), Map.of("name", "여행기", "description", ""))
                .cookie(owner.session())).andExpect(status().isNoContent());
        mvc.perform(get("/api/blogs/" + owner.blogId()))
                .andExpect(jsonPath("$.categories[0].name").value("여행기"))
                .andExpect(jsonPath("$.categories[0].description").doesNotExist());

        mvc.perform(delete("/api/categories/" + travel).with(csrf()).cookie(owner.session()))
                .andExpect(status().isNoContent());
        long next = createCategory(owner, "운동");
        mvc.perform(get("/api/blogs/" + owner.blogId()))
                .andExpect(jsonPath("$.categories[1].id").value(next))
                .andExpect(jsonPath("$.categories[1].colorIndex").value(1))
                .andExpect(jsonPath("$.categories[0].id").value(food));
    }
}
