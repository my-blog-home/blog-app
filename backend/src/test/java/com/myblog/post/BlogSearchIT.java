package com.myblog.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 블로그 안 검색, #태그 검색, 목록의 좋아요·댓글 수 (FR-070, FR-085, BR-05, BR-10) */
class BlogSearchIT extends IntegrationTestBase {

    @Test
    void 블로그_안_검색은_제목_본문_태그를_보고_주인은_비공개_글까지_찾는다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        LoggedIn other = signupAndLogin("남남", "other@example.com");
        long tagged = createPost(owner, Map.of("title", "봄 여행 기록", "body", "벚꽃이 예뻤다", "tags", List.of("제주")));
        long secret = createPost(owner, Map.of("title", "비공개 메모", "body", "제주 비밀 장소", "visibility", "PRIVATE"));
        createPost(owner, Map.of("title", "제주 임시저장", "body", "본문", "draft", true));
        createPost(other, Map.of("title", "제주 맛집", "body", "본문"));

        mvc.perform(get("/api/blogs/" + owner.blogId() + "/posts").param("q", "제주"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].id").value(tagged));
        mvc.perform(get("/api/blogs/" + owner.blogId() + "/posts").param("q", "제주").cookie(other.session()))
                .andExpect(jsonPath("$.totalCount").value(1));
        mvc.perform(get("/api/blogs/" + owner.blogId() + "/posts").param("q", "제주").cookie(owner.session()))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.items[0].id").value(secret))
                .andExpect(jsonPath("$.items[1].id").value(tagged));
        // 모든 단어가 들어 있어야 한다
        mvc.perform(get("/api/blogs/" + owner.blogId() + "/posts").param("q", "봄 벚꽃"))
                .andExpect(jsonPath("$.totalCount").value(1));
        mvc.perform(get("/api/blogs/" + owner.blogId() + "/posts").param("q", "봄 바다"))
                .andExpect(jsonPath("$.totalCount").value(0));
        mvc.perform(get("/api/blogs/" + owner.blogId() + "/posts").param("q", "제"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("검색어를 2자 이상 입력해 주세요"));
    }

    @Test
    void 전체_검색에서_샵으로_시작하면_그_태그가_정확히_붙은_공개_글만_찾는다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        long java = createPost(owner, Map.of("title", "자바 공부", "body", "본문", "tags", List.of("Java")));
        createPost(owner, Map.of("title", "자바스크립트", "body", "Java라는 단어", "tags", List.of("JavaScript")));
        createPost(owner, Map.of("title", "비공개 자바", "body", "본문", "tags", List.of("java"), "visibility", "PRIVATE"));

        mvc.perform(get("/api/search").param("q", "#java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].id").value(java));
        // 태그가 아닌 일반 검색은 제목·본문을 본다
        mvc.perform(get("/api/search").param("q", "java"))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void 목록에_좋아요_수와_답글을_포함한_댓글_수가_있다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        LoggedIn reader = signupAndLogin("독자", "reader@example.com");
        long postId = createPost(owner, "반응 많은 글", "PUBLIC");
        jdbc.update("insert into post_like (post_id, member_id, created_at) values (?, ?, now())", postId, reader.memberId());
        jdbc.update("insert into comment (post_id, author_id, content, created_at) values (?, ?, '댓글', now())",
                postId, reader.memberId());
        Long parent = jdbc.queryForObject("select id from comment where post_id = ?", Long.class, postId);
        jdbc.update("insert into comment (post_id, author_id, content, created_at, parent_id, is_secret) values (?, ?, '답글', now(), ?, true)",
                postId, owner.memberId(), parent);

        for (String url : List.of("/api/posts", "/api/blogs/" + owner.blogId() + "/posts", "/api/search?q=반응",
                "/api/users/" + owner.memberId() + "/posts")) {
            mvc.perform(get(url))
                    .andExpect(jsonPath("$.items[0].likeCount").value(1))
                    .andExpect(jsonPath("$.items[0].commentCount").value(2));
        }
        mvc.perform(get("/api/posts/" + postId))
                .andExpect(jsonPath("$.commentCount").value(2));
    }
}
