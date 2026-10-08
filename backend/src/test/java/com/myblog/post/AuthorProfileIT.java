package com.myblog.post;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 작성자 프로필: 남에게는 공개 글만, 나에게는 비공개 글까지, 임시저장은 어디에도 없음 (FR-075, BR-28) */
class AuthorProfileIT extends IntegrationTestBase {

    @Test
    void 남이_보면_공개_글만_내가_보면_비공개_글까지_보인다() throws Exception {
        LoggedIn author = signupAndLogin("작가", "author@example.com");
        LoggedIn reader = signupAndLogin("독자", "reader@example.com");
        long publicPost = createPost(author, "공개 글", "PUBLIC");
        createPost(author, "비공개 글", "PRIVATE");
        createPost(author, Map.of("title", "임시저장 글", "body", "본문", "draft", true));
        long hiddenCategory = createCategory(author, "숨김");
        mvc.perform(withJson(put("/api/categories/" + hiddenCategory + "/visibility"), Map.of("visibility", "PRIVATE"))
                .cookie(author.session())).andExpect(status().isNoContent());
        createPost(author, Map.of("title", "숨긴 분류의 글", "body", "본문", "visibility", "PUBLIC", "categoryId", hiddenCategory));
        jdbc.update("insert into subscription (member_id, blog_id, created_at) values (?, ?, now())",
                reader.memberId(), author.blogId());

        mvc.perform(get("/api/users/" + author.memberId()).cookie(reader.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("작가"))
                .andExpect(jsonPath("$.profileColor").value("#c9dcfb"))
                .andExpect(jsonPath("$.isMe").value(false))
                .andExpect(jsonPath("$.publicPostCount").value(1))
                .andExpect(jsonPath("$.privatePostCount").value(nullValue()))
                .andExpect(jsonPath("$.blog.id").value(author.blogId()))
                .andExpect(jsonPath("$.blog.name").value("작가의 블로그"))
                .andExpect(jsonPath("$.blog.topicName").value("일상"))
                .andExpect(jsonPath("$.blog.subscriberCount").value(1));
        mvc.perform(get("/api/users/" + author.memberId() + "/posts").cookie(reader.session()))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].id").value(publicPost));
        mvc.perform(get("/api/users/" + author.memberId() + "/posts"))
                .andExpect(jsonPath("$.totalCount").value(1));

        mvc.perform(get("/api/users/" + author.memberId()).cookie(author.session()))
                .andExpect(jsonPath("$.isMe").value(true))
                .andExpect(jsonPath("$.publicPostCount").value(1))
                .andExpect(jsonPath("$.privatePostCount").value(2));
        mvc.perform(get("/api/users/" + author.memberId() + "/posts").cookie(author.session()))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.items[?(@.title == '임시저장 글')]").isEmpty());
    }

    @Test
    void 없는_회원은_존재하지_않는_회원이다() throws Exception {
        mvc.perform(get("/api/users/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 회원입니다"));
        mvc.perform(get("/api/users/999999/posts"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 글_상세와_블로그에_작성자와_조회수가_있다() throws Exception {
        LoggedIn author = signupAndLogin("작가", "author@example.com");
        long postId = createPost(author, "글", "PUBLIC");
        jdbc.update("update post set view_count = 7 where id = ?", postId);
        mvc.perform(get("/api/posts/" + postId).cookie(author.session()))
                .andExpect(jsonPath("$.viewCount").value(7))
                .andExpect(jsonPath("$.authorId").value(author.memberId()))
                .andExpect(jsonPath("$.authorNickname").value("작가"));
        mvc.perform(get("/api/blogs/" + author.blogId()))
                .andExpect(jsonPath("$.ownerId").value(author.memberId()));
    }
}
