package com.myblog.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 관리자 공지 쓰기·고치기·지우기 (FR-082) */
class AdminNoticeIT extends IntegrationTestBase {

    @Test
    void 공지를_쓰고_고치고_지운다() throws Exception {
        LoggedIn admin = adminLogin("admin@example.com");
        String body = mvc.perform(jsonPost("/api/admin/notices",
                        Map.of("type", "NOTICE", "title", " 점검 안내 ", "content", "오늘 밤 점검합니다.\n\n10시부터", "pinned", true))
                        .cookie(admin.session()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("점검 안내"))
                .andExpect(jsonPath("$.pinned").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();
        mvc.perform(get("/api/notices?type=NOTICE")).andExpect(jsonPath("$.items[0].id").value(id));

        mvc.perform(withJson(patch("/api/admin/notices/" + id), Map.of("type", "GUIDE", "pinned", false))
                        .cookie(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("GUIDE"))
                .andExpect(jsonPath("$.title").value("점검 안내"))
                .andExpect(jsonPath("$.updatedAt").exists());

        mvc.perform(delete("/api/admin/notices/" + id).with(csrf()).cookie(admin.session()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notices/" + id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/notices/" + id).with(csrf()).cookie(admin.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 제목_내용_종류_규칙을_어기면_400이다() throws Exception {
        LoggedIn admin = adminLogin("admin@example.com");
        mvc.perform(jsonPost("/api/admin/notices", Map.of("type", "NOTICE", "title", "  ", "content", "내용"))
                        .cookie(admin.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
        mvc.perform(jsonPost("/api/admin/notices", Map.of("type", "NOTICE", "title", "가".repeat(101), "content", "내용"))
                        .cookie(admin.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
        mvc.perform(jsonPost("/api/admin/notices", Map.of("type", "NOTICE", "title", "제목", "content", "가".repeat(5001)))
                        .cookie(admin.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("content"));
        mvc.perform(jsonPost("/api/admin/notices", Map.of("type", "EVENT", "title", "제목", "content", "내용"))
                        .cookie(admin.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("type"));
        mvc.perform(jsonPost("/api/admin/notices", Map.of("type", "GUIDE", "title", "가".repeat(100),
                "content", "가".repeat(5000))).cookie(admin.session())).andExpect(status().isCreated());
    }
}
