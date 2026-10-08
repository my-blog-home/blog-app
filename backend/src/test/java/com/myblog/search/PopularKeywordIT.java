package com.myblog.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 실시간 인기 검색어 (FR-073, BR-29, BR-31) */
class PopularKeywordIT extends IntegrationTestBase {

    @Autowired
    private PopularKeywordService popularKeywords;

    /** 지금부터 minutesAgo분 전에 검색한 기록을 count개 넣는다 */
    private void logged(String keyword, int count, int minutesAgo) {
        for (int i = 0; i < count; i++) {
            jdbc.update("insert into search_log (keyword, keyword_key, searched_at) values (?, lower(?), now() - make_interval(mins => ?))",
                    keyword, keyword, minutesAgo);
        }
    }

    private long countOf(String key) {
        return jdbc.queryForObject("select count(*) from search_log where keyword_key = ?", Long.class, key);
    }

    @Test
    void 같은_세션의_같은_검색어는_한_번만_세고_대소문자는_합친다() throws Exception {
        MvcResult first = mvc.perform(get("/api/search").param("q", "Java"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = first.getResponse().getCookie(SearchController.SEARCH_SESSION_COOKIE);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/search").param("q", " java ").cookie(session)).andExpect(status().isOk());
        assertThat(countOf("java")).isEqualTo(1);

        // 다른 세션이면 다시 센다. 표기는 처음 쓰인 "Java"
        mvc.perform(get("/api/search").param("q", "JAVA")).andExpect(status().isOk());
        assertThat(countOf("java")).isEqualTo(2);

        // 회원은 회원별로 한 번
        LoggedIn member = signupAndLogin("검색회원", "member@example.com");
        mvc.perform(get("/api/search").param("q", "Spring").cookie(member.session()));
        mvc.perform(get("/api/search").param("q", "spring").cookie(member.session()));
        assertThat(countOf("spring")).isEqualTo(1);

        // 규칙에 맞지 않는 검색은 기록하지 않는다
        mvc.perform(get("/api/search").param("q", "자")).andExpect(status().isBadRequest());
        assertThat(countOf("자")).isZero();

        // 30자까지만 기록한다
        String longQuery = "가".repeat(40);
        mvc.perform(get("/api/search").param("q", longQuery)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select keyword from search_log where keyword like '가%'", String.class))
                .isEqualTo("가".repeat(30));

        mvc.perform(get("/api/search/popular-keywords"))
                .andExpect(jsonPath("$.items[0].keyword").value("Java"))
                .andExpect(jsonPath("$.items[0].rank").value(1));
    }

    @Test
    void 최근_24시간_순위를_1시간_전_순위와_비교한다() throws Exception {
        logged("오래된", 5, 24 * 60 + 30); // 1시간 전 순위에는 들고, 지금은 24시간이 지나 빠진다
        logged("강릉", 3, 120);
        logged("부산", 2, 120);
        logged("부산", 2, 10);
        logged("대구", 2, 80);
        logged("제주", 2, 180);
        mvc.perform(get("/api/search").param("q", "Java"));
        mvc.perform(get("/api/search").param("q", "java"));

        // 1시간 전: 오래된 5, 강릉 3, 대구 2(가장 최근), 부산 2, 제주 2
        // 지금: 부산 4, 강릉 3, Java 2(가장 최근), 대구 2, 제주 2
        mvc.perform(get("/api/search/popular-keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value(org.hamcrest.Matchers.matchesPattern("\\d{2}:\\d{2}")))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0].keyword").value("부산"))
                .andExpect(jsonPath("$.items[0].change").value(3))
                .andExpect(jsonPath("$.items[0].isNew").value(false))
                .andExpect(jsonPath("$.items[1].keyword").value("강릉"))
                .andExpect(jsonPath("$.items[1].change").value(0))
                .andExpect(jsonPath("$.items[2].keyword").value("Java"))
                .andExpect(jsonPath("$.items[2].isNew").value(true))
                .andExpect(jsonPath("$.items[3].keyword").value("대구"))
                .andExpect(jsonPath("$.items[3].change").value(-1))
                .andExpect(jsonPath("$.items[4].keyword").value("제주"))
                .andExpect(jsonPath("$.items[4].change").value(0))
                .andExpect(jsonPath("$.items[4].rank").value(5));
    }

    @Test
    void 상위_10개만_보이고_이틀이_지난_기록은_지운다() throws Exception {
        for (int i = 0; i < 12; i++) {
            logged("단어" + i, i + 1, 5);
        }
        mvc.perform(get("/api/search/popular-keywords"))
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.items[0].keyword").value("단어11"))
                .andExpect(jsonPath("$.items[9].keyword").value("단어2"));

        logged("옛날", 1, 3 * 24 * 60);
        logged("어제", 1, 24 * 60);
        popularKeywords.cleanUp();
        List<String> left = jdbc.queryForList("select distinct keyword from search_log where keyword in ('옛날', '어제')", String.class);
        assertThat(left).containsExactly("어제");
    }
}
