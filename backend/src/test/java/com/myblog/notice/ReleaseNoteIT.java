package com.myblog.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 버전별 릴리스 노트를 공지로 한 번만 올리고, 바닥글에 현재 버전을 알려 준다 */
class ReleaseNoteIT extends IntegrationTestBase {

    @Autowired
    ReleaseNotePublisher publisher;

    @Autowired
    org.springframework.boot.info.BuildProperties build;

    @AfterEach
    void removeReleaseNotices() {
        jdbc.update("delete from notice where release_version is not null");
    }

    @Test
    void 릴리스_노트는_버전마다_한_번만_공지로_올라간다() {
        int first = publisher.publishAll();
        int second = publisher.publishAll();
        assertThat(first).isPositive();
        assertThat(second).isZero();
        assertThat(jdbc.queryForObject("select title from notice where release_version = '1.0.0'", String.class))
                .isEqualTo("[v1.0.0] Ylog 1.0.0 출시");
        assertThat(jdbc.queryForObject("select content from notice where release_version = '1.0.0'", String.class))
                .startsWith("Ylog의 첫 정식 버전").doesNotContain("title:");
    }

    @Test
    void 바닥글은_지금_버전_노트나_가장_최근의_보이는_노트를_가리킨다() throws Exception {
        publisher.publishAll();
        String version = build.getVersion();
        String body = mvc.perform(get("/api/config/version"))
                .andExpect(jsonPath("$.version").value(version))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("releaseNoticeId").asLong();
        // 지금 버전의 노트 파일이 있으면 바닥글은 그 버전의 공지를 가리킨다
        mvc.perform(get("/api/notices/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseVersion").value(version));
    }

    @Test
    void 파일이_없어진_버전의_공지는_지우지_않고_숨긴다() throws Exception {
        // 모음 노트로 합치기 전에 올라가 있던 옛 버전 공지
        jdbc.update("insert into notice (type, title, content, pinned, created_at, release_version)"
                + " values ('NOTICE', '[v0.9.0] 옛 노트', '내용', false, now(), '0.9.0')");
        publisher.publishAll();
        assertThat(jdbc.queryForObject("select hidden from notice where release_version = '0.9.0'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select hidden from notice where release_version = '1.0.0'", Boolean.class)).isFalse();
        long oldId = jdbc.queryForObject("select id from notice where release_version = '0.9.0'", Long.class);
        mvc.perform(get("/api/notices/" + oldId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/notices").param("type", "NOTICE"))
                .andExpect(jsonPath("$.items[?(@.releaseVersion == '0.9.0')]").doesNotExist());
    }

    @Test
    void 버전은_숫자_순서로_비교한다() {
        assertThat(ReleaseNotePublisher.compareVersions("1.10.0", "1.9.2")).isPositive();
        assertThat(ReleaseNotePublisher.compareVersions("1.0.0", "1.0.0")).isZero();
    }
}
