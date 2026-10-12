package com.myblog.post;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

/** 북마크: 본인만 보고, 읽을 수 있는 글에만, 최근 저장 순 */
class BookmarkIT extends IntegrationTestBase {

    @Test
    void 북마크는_켜고_끄며_내_목록에_최근_순으로_나온다() throws Exception {
        LoggedIn author = signupAndLogin("글쓴이", "author@example.com");
        LoggedIn me = signupAndLogin("독자", "reader@example.com");
        long first = createPost(author, "첫 글", "PUBLIC");
        long second = createPost(author, "둘째 글", "PUBLIC");

        mvc.perform(put("/api/posts/" + first + "/bookmark").with(csrf()).cookie(me.session()))
                .andExpect(jsonPath("$.bookmarked").value(true));
        mvc.perform(put("/api/posts/" + second + "/bookmark").with(csrf()).cookie(me.session()))
                .andExpect(jsonPath("$.bookmarked").value(true));
        mvc.perform(get("/api/me/bookmarks").cookie(me.session()))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.items[0].title").value("둘째 글"));
        mvc.perform(get("/api/posts/" + first).cookie(me.session()))
                .andExpect(jsonPath("$.bookmarkedByMe").value(true));

        mvc.perform(put("/api/posts/" + first + "/bookmark").with(csrf()).cookie(me.session()))
                .andExpect(jsonPath("$.bookmarked").value(false));
        mvc.perform(get("/api/me/bookmarks").cookie(me.session()))
                .andExpect(jsonPath("$.totalCount").value(1));
        // 남의 북마크는 보이지 않는다
        mvc.perform(get("/api/me/bookmarks").cookie(author.session()))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void 로그인해야_하고_볼_수_없는_글은_저장할_수_없다() throws Exception {
        LoggedIn author = signupAndLogin("글쓴이", "author@example.com");
        LoggedIn me = signupAndLogin("독자", "reader@example.com");
        long hidden = createPost(author, "비공개", "PRIVATE");
        mvc.perform(put("/api/posts/" + hidden + "/bookmark").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/posts/" + hidden + "/bookmark").with(csrf()).cookie(me.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 저장한_글이_비공개로_바뀌면_목록에서_빠진다() throws Exception {
        LoggedIn author = signupAndLogin("글쓴이", "author@example.com");
        LoggedIn me = signupAndLogin("독자", "reader@example.com");
        long postId = createPost(author, "곧 비공개", "PUBLIC");
        mvc.perform(put("/api/posts/" + postId + "/bookmark").with(csrf()).cookie(me.session()));
        jdbc.update("update post set visibility = 'PRIVATE' where id = ?", postId);
        mvc.perform(get("/api/me/bookmarks").cookie(me.session()))
                .andExpect(jsonPath("$.totalCount").value(0));
    }
}
