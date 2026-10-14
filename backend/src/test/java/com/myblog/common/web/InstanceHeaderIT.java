package com.myblog.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.myblog.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** 응답 헤더로 어느 앱이 답했는지 알려 준다 (로드 밸런싱 확인용) */
@TestPropertySource(properties = "blog.instance-name=ylog-app-blue-2")
class InstanceHeaderIT extends IntegrationTestBase {

    @Test
    void 응답마다_앱_이름을_붙인다() throws Exception {
        mvc.perform(get("/api/config/version")).andExpect(header().string("X-Ylog-Instance", "ylog-app-blue-2"));
    }
}
