package com.myblog.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.myblog.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 답글(FR-065, BR-14, BR-15)과 비밀 댓글(FR-066, BR-16) */
class ReplySecretIT extends IntegrationTestBase {

    private LoggedIn owner;
    private LoggedIn writer;
    private LoggedIn replier;
    private LoggedIn stranger;
    private long postId;

    @BeforeEach
    void setUp() throws Exception {
        owner = signupAndLogin("블로그주인", "owner@example.com");
        writer = signupAndLogin("댓글쓴이", "writer@example.com");
        replier = signupAndLogin("답글쓴이", "replier@example.com");
        stranger = signupAndLogin("지나가는이", "stranger@example.com");
        postId = createPost(owner, "글", "PUBLIC");
    }

    private long comment(LoggedIn user, String content, boolean secret, Long parentId) throws Exception {
        redis.delete("comment-cooldown:" + user.memberId());
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        body.put("secret", secret);
        body.put("parentId", parentId);
        String response = mvc.perform(jsonPost("/api/posts/" + postId + "/comments", body).cookie(user.session()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private JsonNode list(Cookie session) throws Exception {
        var request = get("/api/posts/" + postId + "/comments");
        if (session != null) {
            request.cookie(session);
        }
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void 답글은_원댓글_아래에_오래된_순으로_모이고_주인은_글쓴이로_표시된다() throws Exception {
        long parent = comment(writer, "원댓글", false, null);
        comment(owner, "주인 답글", false, parent);
        comment(replier, "둘째 답글", false, parent);

        JsonNode list = list(stranger.session());
        assertThat(list.size()).isEqualTo(1);
        JsonNode top = list.get(0);
        assertThat(top.get("isBlogOwner").asBoolean()).isFalse();
        assertThat(top.get("canReply").asBoolean()).isTrue();
        assertThat(top.get("replies").size()).isEqualTo(2);
        assertThat(top.get("replies").get(0).get("content").asText()).isEqualTo("주인 답글");
        assertThat(top.get("replies").get(0).get("isBlogOwner").asBoolean()).isTrue();
        assertThat(top.get("replies").get(0).get("canReply").asBoolean()).isFalse();
        assertThat(top.get("replies").get(1).get("content").asText()).isEqualTo("둘째 답글");

        // 댓글 수는 답글을 포함한다
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.commentCount").value(3));
        mvc.perform(get("/api/manage/blogs/" + owner.blogId() + "/comments").cookie(owner.session()))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.items[0].reply").value(true));
        // 주인이 단 답글은 새 댓글에 세지 않는다
        mvc.perform(get("/api/manage/new-comments").cookie(owner.session()))
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void 답글에는_답글을_달_수_없다() throws Exception {
        long parent = comment(writer, "원댓글", false, null);
        long reply = comment(replier, "답글", false, parent);
        redis.delete("comment-cooldown:" + stranger.memberId());
        mvc.perform(jsonPost("/api/posts/" + postId + "/comments", Map.of("content", "답글의 답글", "parentId", reply))
                        .cookie(stranger.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("답글에는 답글을 달 수 없습니다"));
    }

    @Test
    void 다른_글의_댓글이나_없는_댓글에는_답글을_달_수_없다() throws Exception {
        long otherPost = createPost(owner, "다른 글", "PUBLIC");
        long parent = comment(writer, "원댓글", false, null);
        mvc.perform(jsonPost("/api/posts/" + otherPost + "/comments", Map.of("content", "답글", "parentId", parent))
                        .cookie(stranger.session()))
                .andExpect(status().isNotFound());
        mvc.perform(jsonPost("/api/posts/" + postId + "/comments", Map.of("content", "답글", "parentId", 999999))
                        .cookie(stranger.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 답글에도_오초_제한이_있다() throws Exception {
        long parent = comment(writer, "원댓글", false, null);
        mvc.perform(jsonPost("/api/posts/" + postId + "/comments", Map.of("content", "바로 답글", "parentId", parent))
                        .cookie(writer.session()))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void 댓글을_지우면_답글도_지워진다() throws Exception {
        long parent = comment(writer, "원댓글", false, null);
        long reply = comment(replier, "답글", false, parent);
        // 원댓글 작성자는 남의 답글을 지울 수 없다
        mvc.perform(delete("/api/comments/" + reply).with(csrf()).cookie(writer.session()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/comments/" + parent).with(csrf()).cookie(writer.session()))
                .andExpect(status().isNoContent());
        assertThat(list(null).size()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from comment where id = ?", Long.class, reply)).isZero();
    }

    @Test
    void 비밀_댓글은_작성자와_블로그주인만_내용을_본다() throws Exception {
        comment(writer, "비밀 내용", true, null);

        for (LoggedIn reader : new LoggedIn[] {writer, owner}) {
            JsonNode c = list(reader.session()).get(0);
            assertThat(c.get("hidden").asBoolean()).isFalse();
            assertThat(c.get("secret").asBoolean()).isTrue();
            assertThat(c.get("content").asText()).isEqualTo("비밀 내용");
            assertThat(c.get("deletable").asBoolean()).isTrue();
        }
        JsonNode forOwner = list(owner.session()).get(0);
        assertThat(forOwner.get("canReply").asBoolean()).isTrue();
        assertThat(forOwner.get("reportable").asBoolean()).isTrue();

        for (Cookie other : new Cookie[] {stranger.session(), null}) {
            JsonNode c = list(other).get(0);
            assertThat(c.get("hidden").asBoolean()).isTrue();
            assertThat(c.get("content").isNull()).isTrue();
            assertThat(c.get("authorNickname").asText()).isEqualTo("댓글쓴이");
            assertThat(c.get("createdAt").isNull()).isFalse();
            assertThat(c.get("deletable").asBoolean()).isFalse();
            assertThat(c.get("canReply").asBoolean()).isFalse();
            assertThat(c.get("reportable").asBoolean()).isFalse();
        }
    }

    @Test
    void 가려진_비밀_댓글에는_답글을_달_수_없고_지울_수도_없다() throws Exception {
        long secret = comment(writer, "비밀", true, null);
        mvc.perform(jsonPost("/api/posts/" + postId + "/comments", Map.of("content", "답글", "parentId", secret))
                        .cookie(stranger.session()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/comments/" + secret).with(csrf()).cookie(stranger.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 비밀_댓글의_답글은_항상_비밀이고_원댓글_작성자도_본다() throws Exception {
        long parent = comment(writer, "비밀 원댓글", true, null);
        comment(owner, "주인의 답글", false, parent);

        JsonNode forWriter = list(writer.session()).get(0).get("replies").get(0);
        assertThat(forWriter.get("secret").asBoolean()).isTrue();
        assertThat(forWriter.get("hidden").asBoolean()).isFalse();
        assertThat(forWriter.get("content").asText()).isEqualTo("주인의 답글");

        JsonNode forStranger = list(stranger.session()).get(0).get("replies").get(0);
        assertThat(forStranger.get("hidden").asBoolean()).isTrue();
        assertThat(forStranger.get("content").isNull()).isTrue();
    }

    @Test
    void 공개_댓글에_단_비밀_답글은_원댓글_작성자가_본다() throws Exception {
        long parent = comment(writer, "공개 원댓글", false, null);
        comment(replier, "비밀 답글", true, parent);

        assertThat(list(writer.session()).get(0).get("replies").get(0).get("hidden").asBoolean()).isFalse();
        assertThat(list(replier.session()).get(0).get("replies").get(0).get("hidden").asBoolean()).isFalse();
        assertThat(list(owner.session()).get(0).get("replies").get(0).get("hidden").asBoolean()).isFalse();
        assertThat(list(stranger.session()).get(0).get("replies").get(0).get("hidden").asBoolean()).isTrue();
        assertThat(list(null).get(0).get("replies").get(0).get("hidden").asBoolean()).isTrue();
        // 원댓글은 공개라 누구나 본다
        assertThat(list(null).get(0).get("content").asText()).isEqualTo("공개 원댓글");
    }

    @Test
    void 댓글_관리에_비밀_표시가_나온다() throws Exception {
        comment(writer, "비밀", true, null);
        mvc.perform(get("/api/manage/blogs/" + owner.blogId() + "/comments").cookie(owner.session()))
                .andExpect(jsonPath("$.items[0].secret").value(true))
                .andExpect(jsonPath("$.items[0].excerpt").value("비밀"));
    }
}
