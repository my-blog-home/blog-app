package com.myblog.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.myblog.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 버전별 릴리스 노트를 공지로 한 번만 올리고, 바닥글에 현재 버전을 알려 준다 */
class ReleaseNoteIT extends IntegrationTestBase {

    @Autowired
    ReleaseNotePublisher publisher;

    @AfterEach
    void removeReleaseNotices() {
        jdbc.update("delete from notice where release_version is not null");
    }

    @Test
    void 릴리스_노트는_버전마다_한_번만_공지로_올라간다() {
        int first = publisher.publishAll();
        int second = publisher.publishAll();
        assertThat(first).isGreaterThanOrEqualTo(1);
        assertThat(second).isZero();
        assertThat(jdbc.queryForObject("select title from notice where release_version = '1.0.0'", String.class))
                .isEqualTo("[v1.0.0] Ylog 1.0.0 출시");
        assertThat(jdbc.queryForObject("select content from notice where release_version = '1.0.0'", String.class))
                .startsWith("Ylog의 첫 정식 버전").doesNotContain("title:");
    }

    @Test
    void 현재_버전과_그_버전의_릴리스_노트_공지를_알려_준다() throws Exception {
        publisher.publishAll();
        Long id = jdbc.queryForObject("select id from notice where release_version = '1.0.0'", Long.class);
        mvc.perform(get("/api/config/version"))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.releaseNoticeId").value(id));
        mvc.perform(get("/api/notices").param("type", "NOTICE"))
                .andExpect(jsonPath("$.items[?(@.releaseVersion == '1.0.0')]").exists());
    }

    @Test
    void 버전은_숫자_순서로_비교한다() {
        assertThat(ReleaseNotePublisher.compareVersions("1.10.0", "1.9.2")).isPositive();
        assertThat(ReleaseNotePublisher.compareVersions("1.0.0", "1.0.0")).isZero();
    }
}
