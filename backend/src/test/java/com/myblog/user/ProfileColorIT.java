package com.myblog.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 프로필 색: 정해진 6가지 중 하나, 기본은 첫 색 (FR-05) */
class ProfileColorIT extends IntegrationTestBase {

    @Test
    void 프로필_색은_정해진_색만_고르고_여러_화면에_함께_나온다() throws Exception {
        LoggedIn me = signupAndLogin("나나", "me@example.com");
        mvc.perform(get("/api/auth/me").cookie(me.session())).andExpect(jsonPath("$.profileColor").value("#c9dcfb"));
        mvc.perform(get("/api/me").cookie(me.session())).andExpect(jsonPath("$.profileColor").value("#c9dcfb"));

        mvc.perform(withJson(patch("/api/me"), Map.of("nickname", "나나", "bio", "", "profileColor", "#000000"))
                        .cookie(me.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("profileColor"));
        mvc.perform(withJson(patch("/api/me"), Map.of("nickname", "나나", "bio", "", "profileColor", "#E2D8F8"))
                        .cookie(me.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileColor").value("#e2d8f8"));
        // 색을 보내지 않으면 그대로 둔다
        mvc.perform(withJson(patch("/api/me"), Map.of("nickname", "나나2", "bio", "")).cookie(me.session()))
                .andExpect(jsonPath("$.profileColor").value("#e2d8f8"));

        long postId = createPost(me, "글", "PUBLIC");
        mvc.perform(get("/api/auth/me").cookie(me.session())).andExpect(jsonPath("$.profileColor").value("#e2d8f8"));
        mvc.perform(get("/api/blogs/" + me.blogId())).andExpect(jsonPath("$.ownerColor").value("#e2d8f8"));
        mvc.perform(get("/api/posts")).andExpect(jsonPath("$.items[0].authorColor").value("#e2d8f8"));
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.authorColor").value("#e2d8f8"));
    }
}
