package com.myblog.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 공지·이용 안내 읽기와 처음 넣은 안내 (FR-077, BR-30) */
class NoticeIT extends IntegrationTestBase {

    @Test
    void 처음_안내_9개가_있고_우리_규칙과_맞는다() {
        assertThat(jdbc.queryForObject("select count(*) from notice where type = 'NOTICE'", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from notice where type = 'GUIDE'", Long.class)).isEqualTo(7);
        List<String> contents = jdbc.queryForList("select title || ' ' || content from notice", String.class);
        assertThat(contents).noneMatch(c -> c.contains("데모") || c.contains("8자 이상") || c.contains("여러 개")
                || c.contains("새 블로그 만들기") || c.contains("<"));
        assertThat(contents).anyMatch(c -> c.contains("8~10자") && c.contains("특수문자"));
    }

    @Test
    void 고정_글이_위로_그다음_최신순이고_종류로_거른다() throws Exception {
        mvc.perform(get("/api/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(9))
                .andExpect(jsonPath("$.items.length()").value(9))
                .andExpect(jsonPath("$.items[0].title").value("서비스 이용 시 유의사항"))
                .andExpect(jsonPath("$.items[0].pinned").value(true))
                .andExpect(jsonPath("$.items[0].type").value("NOTICE"))
                .andExpect(jsonPath("$.items[1].title").value("Ylog에 오신 것을 환영해요"))
                .andExpect(jsonPath("$.items[2].title").value("첫 화면의 정렬과 인기 검색어 보기"))
                .andExpect(jsonPath("$.items[8].title").value("블로그 만들고 첫 글 쓰기"));
        mvc.perform(get("/api/notices").param("type", "GUIDE"))
                .andExpect(jsonPath("$.totalCount").value(7))
                .andExpect(jsonPath("$.items[*].type", not(hasItem("NOTICE"))));
        mvc.perform(get("/api/notices").param("type", "notice"))
                .andExpect(jsonPath("$.totalCount").value(2));
        // 모르는 종류는 전체
        mvc.perform(get("/api/notices").param("type", "ETC"))
                .andExpect(jsonPath("$.totalCount").value(9));
        // 첫 화면은 5개만
        mvc.perform(get("/api/notices").param("size", "5"))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/notices").param("size", "5").param("page", "2"))
                .andExpect(jsonPath("$.items.length()").value(4));
    }

    @Test
    void 상세는_내용과_같은_종류의_다른_안내를_보인다() throws Exception {
        long guideId = jdbc.queryForObject("select id from notice where title = '댓글과 비밀 댓글 쓰기'", Long.class);
        mvc.perform(get("/api/notices/" + guideId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("댓글과 비밀 댓글 쓰기"))
                .andExpect(jsonPath("$.type").value("GUIDE"))
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("답글")))
                .andExpect(jsonPath("$.others.length()").value(5))
                .andExpect(jsonPath("$.others[*].id", not(hasItem((int) guideId))))
                .andExpect(jsonPath("$.others[*].type", not(hasItem("NOTICE"))));

        long noticeId = jdbc.queryForObject("select id from notice where title = 'Ylog에 오신 것을 환영해요'", Long.class);
        mvc.perform(get("/api/notices/" + noticeId))
                .andExpect(jsonPath("$.others.length()").value(1))
                .andExpect(jsonPath("$.others[0].title").value("서비스 이용 시 유의사항"));

        mvc.perform(get("/api/notices/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 공지입니다"));
    }
}
