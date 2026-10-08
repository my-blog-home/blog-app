package com.myblog.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 임시저장: 주인만 보고, 작성완료하면 그때가 작성 시각이 된다 (FR-11, BR-01, BR-02, BR-04) */
class DraftIT extends IntegrationTestBase {

    @Test
    void 임시저장_글은_제목과_본문을_비워도_되고_제목없음으로_저장된다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        long id = createPost(owner, Map.of("title", "  ", "body", "", "draft", true));

        mvc.perform(get("/api/posts/" + id + "/edit").cookie(owner.session()))
                .andExpect(jsonPath("$.title").value("제목 없음"))
                .andExpect(jsonPath("$.body").value(""))
                .andExpect(jsonPath("$.status").value("DRAFT"));
        assertThat(jdbc.queryForObject("select published_at from post where id = ?", Timestamp.class, id)).isNull();

        // 작성완료는 제목·본문 규칙을 따른다
        mvc.perform(jsonPost("/api/blogs/" + owner.blogId() + "/posts", Map.of("title", " ", "body", "본문"))
                        .cookie(owner.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
        mvc.perform(withJson(put("/api/posts/" + id), Map.of("title", "제목", "body", " ", "draft", false))
                        .cookie(owner.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("body"));
    }

    @Test
    void 임시저장_글은_다른_사람에게도_주인의_블로그_목록에도_나오지_않는다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        LoggedIn other = signupAndLogin("다른회원", "other@example.com");
        long draft = createPost(owner, Map.of("title", "쓰는 중 비밀단어", "body", "본문", "draft", true,
                "tags", List.of("임시")));

        mvc.perform(get("/api/posts/" + draft)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 글입니다"));
        mvc.perform(get("/api/posts/" + draft).cookie(other.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/posts/" + draft + "/comments").cookie(other.session())).andExpect(status().isNotFound());
        mvc.perform(withJson(put("/api/posts/" + draft + "/like"), Map.of()).cookie(other.session()))
                .andExpect(status().isNotFound());
        mvc.perform(jsonPost("/api/posts/" + draft + "/reports", Map.of("reason", "SPAM")).cookie(other.session()))
                .andExpect(status().isNotFound());

        for (Cookie session : new Cookie[] {owner.session(), other.session()}) {
            mvc.perform(get("/api/blogs/" + owner.blogId() + "/posts").cookie(session))
                    .andExpect(jsonPath("$.totalCount").value(0));
            mvc.perform(get("/api/blogs/" + owner.blogId()).cookie(session))
                    .andExpect(jsonPath("$.totalPostCount").value(0))
                    .andExpect(jsonPath("$.categories[0].postCount").value(0));
            mvc.perform(get("/api/posts").cookie(session)).andExpect(jsonPath("$.totalCount").value(0));
            mvc.perform(get("/api/search").param("q", "비밀단어").cookie(session))
                    .andExpect(jsonPath("$.totalCount").value(0));
            mvc.perform(get("/api/tags/임시/posts").cookie(session)).andExpect(jsonPath("$.totalCount").value(0));
        }
        mvc.perform(get("/api/topics")).andExpect(jsonPath("$[8].postCount").value(0));
        mvc.perform(get("/api/manage/blogs/" + owner.blogId() + "/dashboard").cookie(owner.session()))
                .andExpect(jsonPath("$.recent.length()").value(0));

        // 블로그 관리의 글 관리에서만 보인다
        String base = "/api/manage/blogs/" + owner.blogId() + "/posts";
        createPost(owner, "공개 글", "PUBLIC");
        createPost(owner, "비공개 글", "PRIVATE");
        mvc.perform(get(base).cookie(owner.session())).andExpect(jsonPath("$.totalCount").value(3));
        mvc.perform(get(base).param("status", "ALL").cookie(owner.session())).andExpect(jsonPath("$.totalCount").value(3));
        mvc.perform(get(base).param("status", "DRAFT").cookie(owner.session()))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"));
        mvc.perform(get(base).param("status", "PUBLIC").cookie(owner.session())).andExpect(jsonPath("$.totalCount").value(1));
        mvc.perform(get(base).param("status", "PRIVATE").cookie(owner.session())).andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void 작성완료하면_그때가_작성시각이고_한_번만_정해진다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        long id = createPost(owner, Map.of("title", "", "body", "", "draft", true));
        long earlier = createPost(owner, "먼저 올린 글", "PUBLIC");

        mvc.perform(withJson(put("/api/posts/" + id), Map.of("title", "다 쓴 글", "body", "본문", "draft", false))
                .cookie(owner.session())).andExpect(status().isOk());
        Timestamp published = jdbc.queryForObject("select published_at from post where id = ?", Timestamp.class, id);
        assertThat(published).isNotNull();
        mvc.perform(get("/api/posts/" + id))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.prevPostId").value(earlier));
        // 목록 순서도 작성완료한 시각을 따른다
        mvc.perform(get("/api/blogs/" + owner.blogId() + "/posts"))
                .andExpect(jsonPath("$.items[0].title").value("다 쓴 글"));

        // 이미 작성완료한 글에서 임시저장을 눌러도 작성완료 상태는 그대로이고 내용만 저장된다 (BR-04)
        mvc.perform(withJson(put("/api/posts/" + id), Map.of("title", "고친 제목", "body", "본문", "draft", true))
                .cookie(owner.session())).andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + id))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.title").value("고친 제목"));
        mvc.perform(withJson(put("/api/posts/" + id), Map.of("title", "또 고친 제목", "body", "본문"))
                .cookie(owner.session())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select published_at from post where id = ?", Timestamp.class, id))
                .isEqualTo(published);
        // 작성완료한 글을 임시저장으로 눌러도 제목은 비울 수 없다
        mvc.perform(withJson(put("/api/posts/" + id), Map.of("title", "", "body", "본문", "draft", true))
                        .cookie(owner.session()))
                .andExpect(status().isBadRequest());
    }
}
