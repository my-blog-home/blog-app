package com.myblog.admin;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

/** 방문자 화면 미리보기: 주인이 X-Ylog-View: visitor로 열면 비회원처럼 보이고 조회수·방문자에 세지 않는다 (FR-083, BR-44) */
class VisitorPreviewIT extends IntegrationTestBase {

    @Test
    void 미리보기는_비공개_글을_숨기고_주인_표시가_없다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        long publicPost = createPost(owner, "공개 글", "PUBLIC");
        long privatePost = createPost(owner, "비공개 글", "PRIVATE");

        mvc.perform(get("/api/blogs/" + owner.blogId() + "/posts").cookie(owner.session()))
                .andExpect(jsonPath("$.totalCount").value(2));
        mvc.perform(get("/api/blogs/" + owner.blogId() + "/posts").cookie(owner.session()).header("X-Ylog-View", "visitor"))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(get("/api/blogs/" + owner.blogId()).cookie(owner.session()).header("X-Ylog-View", "visitor"))
                .andExpect(jsonPath("$.owner").value(false));
        mvc.perform(get("/api/posts/" + privatePost).cookie(owner.session()).header("X-Ylog-View", "visitor"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 글입니다"));
        mvc.perform(get("/api/posts/" + publicPost).cookie(owner.session()).header("X-Ylog-View", "visitor"))
                .andExpect(jsonPath("$.editable").value(false));
        // 미리보기 뒤에도 로그인은 그대로다
        mvc.perform(get("/api/auth/me").cookie(owner.session())).andExpect(status().isOk());
    }

    @Test
    void 미리보기로_연_글은_조회수와_방문자에_세지_않는다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        LoggedIn reader = signupAndLogin("독자", "reader@example.com");
        long postId = createPost(owner, "공개 글", "PUBLIC");
        mvc.perform(get("/api/posts/" + postId).cookie(owner.session()).header("X-Ylog-View", "visitor"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + postId).cookie(reader.session()).header("X-Ylog-View", "visitor"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.viewCount").value(0));
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.viewCount").value(1));
    }

    @Test
    void 로그인하지_않은_요청의_미리보기_헤더는_무시하고_조회수에_센다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        long postId = createPost(owner, "공개 글", "PUBLIC");
        mvc.perform(get("/api/posts/" + postId).header("X-Ylog-View", "visitor")).andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.viewCount").value(1));
    }
}
