package com.myblog.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

/** 비공개 글은 작성자 외에게 어떤 경로로도 나오지 않는다 (FR-026, FR-032, SC-004) */
class PrivatePostExposureIT extends IntegrationTestBase {

    @Test
    void 방문자의_목록_검색_분류글수_이전다음에_비공개_글이_없다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        long first = createPost(owner, "공개 하나 비밀단어", "PUBLIC");
        long hidden = createPost(owner, "비공개 비밀단어", "PRIVATE");
        long third = createPost(owner, "공개 둘 비밀단어", "PUBLIC");

        mvc.perform(get("/api/blogs/" + owner.blogId() + "/posts"))
                .andExpect(jsonPath("$.totalCount").value(2));
        mvc.perform(get("/api/posts"))
                .andExpect(jsonPath("$.totalCount").value(2));
        mvc.perform(get("/api/search").param("q", "비밀단어"))
                .andExpect(jsonPath("$.totalCount").value(2));
        mvc.perform(get("/api/blogs/" + owner.blogId()))
                .andExpect(jsonPath("$.categories[0].postCount").value(2));
        mvc.perform(get("/api/posts/" + first))
                .andExpect(jsonPath("$.nextPostId").value(third));
        mvc.perform(get("/api/posts/" + third))
                .andExpect(jsonPath("$.prevPostId").value(first));
        mvc.perform(get("/api/posts/" + hidden)).andExpect(status().isNotFound());
    }

    @Test
    void 주인의_목록에는_비공개_글도_나온다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        createPost(owner, "공개", "PUBLIC");
        createPost(owner, "비공개", "PRIVATE");
        mvc.perform(get("/api/blogs/" + owner.blogId() + "/posts").cookie(owner.session()))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.items[0].visibility").value("PRIVATE"));
        mvc.perform(get("/api/blogs/" + owner.blogId()).cookie(owner.session()))
                .andExpect(jsonPath("$.categories[0].postCount").value(2));
    }

    @Test
    void 검색은_모든_단어를_포함하고_특수문자도_오류없이_찾는다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        createPost(owner, "봄 여행 기록", "PUBLIC");
        createPost(owner, "봄 산책", "PUBLIC");
        createPost(owner, "할인 100% 후기", "PUBLIC");

        mvc.perform(get("/api/search").param("q", "봄 여행")).andExpect(jsonPath("$.totalCount").value(1));
        mvc.perform(get("/api/search").param("q", "100%")).andExpect(jsonPath("$.totalCount").value(1));
        mvc.perform(get("/api/search").param("q", "a_")).andExpect(jsonPath("$.totalCount").value(0));
        mvc.perform(get("/api/search").param("q", "봄")).andExpect(status().isBadRequest());
    }

    @Test
    void 없는_페이지는_마지막_페이지를_보여준다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        for (int i = 1; i <= 12; i++) {
            createPost(owner, "글 " + i, "PUBLIC");
        }
        mvc.perform(get("/api/blogs/" + owner.blogId() + "/posts").param("page", "99"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].title").value("글 2"));
    }
}
