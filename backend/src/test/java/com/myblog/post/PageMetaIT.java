package com.myblog.post;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 링크 미리보기: 수집기는 자바스크립트를 실행하지 않으므로 서버가 주소마다 og 태그를 채워 준다 */
class PageMetaIT extends IntegrationTestBase {

    @Test
    void 공개_글은_제목_요약_첫_이미지를_절대_주소로_보여_준다() throws Exception {
        LoggedIn writer = signupAndLogin("작가", "writer@example.com");
        long postId = createPost(writer, Map.of("title", "제주 \"여행\" 기록", "visibility", "PUBLIC",
                "body", "# 첫날\n\n바다가 **정말** 예뻤어요.\n\n![사진](/images/123e4567-e89b-12d3-a456-426614174000.jpg)"));
        mvc.perform(get("/posts/" + postId).header("User-Agent", "kakaotalk-scrap/1.0"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("<meta property=\"og:title\" content=\"제주 &quot;여행&quot; 기록\" />")))
                .andExpect(content().string(containsString("<meta property=\"og:type\" content=\"article\" />")))
                .andExpect(content().string(containsString("바다가 정말 예뻤어요")))
                .andExpect(content().string(containsString(
                        "<meta property=\"og:image\" content=\"http://localhost/images/123e4567-e89b-12d3-a456-426614174000.jpg\" />")))
                .andExpect(content().string(containsString("<meta property=\"og:url\" content=\"http://localhost/posts/" + postId + "\" />")))
                .andExpect(content().string(containsString("<title>제주 &quot;여행&quot; 기록 · Ylog</title>")));
    }

    @Test
    void 이미지가_없는_글은_기본_이미지_블로그는_이름과_소개() throws Exception {
        LoggedIn writer = signupAndLogin("작가", "writer2@example.com");
        long postId = createPost(writer, "글자만 있는 글", "PUBLIC");
        mvc.perform(get("/posts/" + postId))
                .andExpect(content().string(containsString("<meta property=\"og:image\" content=\"http://localhost/og-default.png\" />")));
        jdbc.update("update blog set description = '여행 이야기를 적어요' where id = ?", writer.blogId());
        mvc.perform(get("/blogs/" + writer.blogId()))
                .andExpect(content().string(containsString("<meta property=\"og:description\" content=\"여행 이야기를 적어요\" />")));
    }

    @Test
    void 비공개_글이나_없는_글은_제목이_새지_않고_사이트_기본값() throws Exception {
        LoggedIn writer = signupAndLogin("작가", "writer3@example.com");
        long secret = createPost(writer, "아무도 몰라야 하는 제목", "PRIVATE");
        mvc.perform(get("/posts/" + secret))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("아무도 몰라야 하는 제목"))))
                .andExpect(content().string(containsString("<meta property=\"og:title\" content=\"Ylog\" />")));
        mvc.perform(get("/posts/999999"))
                .andExpect(content().string(containsString("<meta property=\"og:title\" content=\"Ylog\" />")));
        mvc.perform(get("/manage"))
                .andExpect(content().string(containsString("<title>Ylog</title>")));
    }
}
