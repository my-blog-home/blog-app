package com.myblog.post;

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
import org.springframework.http.MediaType;

/** 글의 수정·삭제 권한은 서버가 확인한다 (FR-029, FR-032, FR-055, 헌법 II) */
class PostAuthorizationIT extends IntegrationTestBase {

    private LoggedIn author;
    private LoggedIn other;
    private long postId;

    @BeforeEach
    void setUp() throws Exception {
        author = signupAndLogin("글쓴이", "author@example.com");
        other = signupAndLogin("다른이", "other@example.com");
        postId = createPost(author, "봄 여행 기록", "PUBLIC");
    }

    @Test
    void 남의_글은_수정할_수_없다() throws Exception {
        mvc.perform(put("/api/posts/" + postId).with(csrf()).cookie(other.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "바꿈", "body", "바꿈"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 글입니다"));
    }

    @Test
    void 남의_글은_삭제할_수_없다() throws Exception {
        mvc.perform(delete("/api/posts/" + postId).with(csrf()).cookie(other.session()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/posts/" + postId)).andExpect(status().isOk());
    }

    @Test
    void 남의_글_수정용_원본은_볼_수_없다() throws Exception {
        mvc.perform(get("/api/posts/" + postId + "/edit").cookie(other.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 남의_블로그에는_글을_쓸_수_없다() throws Exception {
        mvc.perform(jsonPost("/api/blogs/" + author.blogId() + "/posts", Map.of("title", "끼어들기", "body", "본문"))
                        .cookie(other.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 로그인하지_않으면_글을_쓸_수_없다() throws Exception {
        mvc.perform(jsonPost("/api/blogs/" + author.blogId() + "/posts", Map.of("title", "익명", "body", "본문")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void CSRF_토큰_없이는_삭제할_수_없다() throws Exception {
        mvc.perform(delete("/api/posts/" + postId).cookie(author.session()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/posts/" + postId)).andExpect(status().isOk());
    }

    @Test
    void 남의_비공개_글은_없는_글과_같다() throws Exception {
        long privateId = createPost(author, "비밀 일기", "PRIVATE");
        mvc.perform(get("/api/posts/" + privateId).cookie(other.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 글입니다"));
        mvc.perform(get("/api/posts/" + privateId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/posts/" + privateId).cookie(author.session())).andExpect(status().isOk());
    }

    @Test
    void 작성자는_수정하고_삭제할_수_있다() throws Exception {
        mvc.perform(put("/api/posts/" + postId).with(csrf()).cookie(author.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "고친 제목", "body", "고친 본문"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + postId))
                .andExpect(jsonPath("$.title").value("고친 제목"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        mvc.perform(delete("/api/posts/" + postId).with(csrf()).cookie(author.session()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + postId)).andExpect(status().isNotFound());
    }
}
