package com.myblog.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** 태그·이미지·신고 (FR-041~043) */
class TagImageReportIT extends IntegrationTestBase {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    private LoggedIn owner;
    private LoggedIn other;

    @BeforeEach
    void setUp() throws Exception {
        owner = signupAndLogin("주인", "owner@example.com");
        other = signupAndLogin("다른회원", "other@example.com");
    }

    private long write(String title, String body, List<String> tags, String visibility) throws Exception {
        String res = mvc.perform(jsonPost("/api/blogs/" + owner.blogId() + "/posts",
                        Map.of("title", title, "body", body, "tags", tags, "visibility", visibility))
                        .cookie(owner.session()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    @Test
    void 태그는_샵을_지우고_대소문자_무시로_겹침을_없앤다() throws Exception {
        long id = write("여행 글", "본문", List.of("#여행", "여행", "Travel", "travel"), "PUBLIC");
        mvc.perform(get("/api/posts/" + id))
                .andExpect(jsonPath("$.tags.length()").value(2));
        write("비공개 여행", "본문", List.of("여행"), "PRIVATE");
        mvc.perform(get("/api/tags/여행/posts")).andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void 태그가_6개이거나_공백이_있으면_거절한다() throws Exception {
        mvc.perform(jsonPost("/api/blogs/" + owner.blogId() + "/posts", Map.of("title", "t", "body", "b",
                        "tags", List.of("a", "b", "c", "d", "e", "f"))).cookie(owner.session()))
                .andExpect(status().isBadRequest());
        mvc.perform(jsonPost("/api/blogs/" + owner.blogId() + "/posts", Map.of("title", "t", "body", "b",
                        "tags", List.of("봄 여행"))).cookie(owner.session()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 이미지는_형식을_파일_내용으로_확인하고_글에_연결된다() throws Exception {
        mvc.perform(multipart("/api/images").file(new MockMultipartFile("file", "fake.png", "image/png",
                        "not an image".getBytes())).with(csrf()).cookie(owner.session()))
                .andExpect(status().isBadRequest());

        String res = mvc.perform(multipart("/api/images").file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                        .with(csrf()).cookie(owner.session()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String url = json.readTree(res).get("url").asText();

        // 글에 넣기 전에는 올린 사람만 본다
        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(get(url).cookie(owner.session())).andExpect(status().isOk());

        long postId = write("사진 글", "![](" + url + ")", List.of(), "PUBLIC");
        mvc.perform(get(url)).andExpect(status().isOk());

        mvc.perform(delete("/api/posts/" + postId).with(csrf()).cookie(owner.session())).andExpect(status().isOk());
        mvc.perform(get(url).cookie(owner.session())).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from post_image", Long.class)).isZero();
    }

    @Test
    void 남의_이미지는_내_글에_연결되지_않는다() throws Exception {
        String res = mvc.perform(multipart("/api/images").file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                .with(csrf()).cookie(other.session())).andReturn().getResponse().getContentAsString();
        String url = json.readTree(res).get("url").asText();
        write("남의 사진", "![](" + url + ")", List.of(), "PUBLIC");
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void 신고는_한_번만_하고_자기_글은_신고할_수_없다() throws Exception {
        long postId = write("글", "본문", List.of(), "PUBLIC");
        mvc.perform(jsonPost("/api/posts/" + postId + "/reports", Map.of("reason", "SPAM")).cookie(other.session()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("신고가 접수되었습니다"));
        mvc.perform(jsonPost("/api/posts/" + postId + "/reports", Map.of("reason", "ETC", "detail", "또"))
                        .cookie(other.session()))
                .andExpect(status().isConflict());
        mvc.perform(jsonPost("/api/posts/" + postId + "/reports", Map.of("reason", "SPAM")).cookie(owner.session()))
                .andExpect(status().isBadRequest());
        mvc.perform(jsonPost("/api/posts/" + postId + "/reports", Map.of("reason", "SPAM")))
                .andExpect(status().isUnauthorized());
    }
}
