package com.myblog.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 주제: 블로그 대표 주제, 글의 주제 기본값, 메인 주제별 보기와 인기순 (FR-09, FR-34, BR-07, CR-68) */
class TopicIT extends IntegrationTestBase {

    private static final long IT = 1;
    private static final long COOKING = 3;
    private static final long TRAVEL = 5;
    private static final long DAILY = 9;

    @Test
    void 주제_10개를_순서대로_보이고_방문자에게_보이는_글만_센다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        createPost(owner, Map.of("title", "요리 글", "body", "본문", "topicId", COOKING));
        createPost(owner, Map.of("title", "비공개 요리", "body", "본문", "topicId", COOKING, "visibility", "PRIVATE"));
        createPost(owner, Map.of("title", "임시 요리", "body", "본문", "topicId", COOKING, "draft", true));

        mvc.perform(get("/api/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].name").value("IT·개발"))
                .andExpect(jsonPath("$[8].name").value("일상"))
                .andExpect(jsonPath("$[9].name").value("취미"))
                .andExpect(jsonPath("$[2].postCount").value(1));
    }

    @Test
    void 글의_주제는_마지막에_쓴_글의_주제_없으면_블로그_대표_주제다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        mvc.perform(get("/api/blogs/" + owner.blogId()).cookie(owner.session()))
                .andExpect(jsonPath("$.topic.id").value(DAILY))
                .andExpect(jsonPath("$.topic.name").value("일상"))
                .andExpect(jsonPath("$.lastUsedTopicId").doesNotExist());

        // 설정에서 대표 주제를 여행으로 바꾸면 첫 글의 주제는 여행
        mvc.perform(withJson(patch("/api/blogs/" + owner.blogId()), Map.of("name", "여행 블로그", "topicId", TRAVEL))
                        .cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic.name").value("여행"));
        long first = createPost(owner, "첫 글", "PUBLIC");
        mvc.perform(get("/api/posts/" + first)).andExpect(jsonPath("$.topic.id").value(TRAVEL))
                .andExpect(jsonPath("$.topic.name").value("여행"));

        // 글마다 주제를 고를 수 있고, 다음 글은 마지막에 쓴 글의 주제를 따른다
        createPost(owner, Map.of("title", "요리", "body", "본문", "topicId", COOKING));
        long third = createPost(owner, "세 번째", "PUBLIC");
        mvc.perform(get("/api/posts/" + third)).andExpect(jsonPath("$.topic.id").value(COOKING));
        mvc.perform(get("/api/blogs/" + owner.blogId()).cookie(owner.session()))
                .andExpect(jsonPath("$.lastUsedTopicId").value(COOKING));

        // 블로그 주제를 바꿔도 이미 쓴 글의 주제는 그대로
        mvc.perform(withJson(patch("/api/blogs/" + owner.blogId()), Map.of("name", "여행 블로그", "topicId", IT))
                .cookie(owner.session())).andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + first)).andExpect(jsonPath("$.topic.id").value(TRAVEL));

        mvc.perform(jsonPost("/api/blogs/" + owner.blogId() + "/posts",
                        Map.of("title", "잘못된 주제", "body", "본문", "topicId", 999)).cookie(owner.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("topicId"));
        mvc.perform(withJson(patch("/api/blogs/" + owner.blogId()), Map.of("name", "이름", "topicId", 0))
                        .cookie(owner.session()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 메인은_글의_주제로_거르고_목록에_주제를_보인다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        createPost(owner, Map.of("title", "강릉 여행", "body", "본문", "topicId", TRAVEL));
        createPost(owner, Map.of("title", "김치찌개", "body", "본문", "topicId", COOKING));
        createPost(owner, Map.of("title", "비공개 여행", "body", "본문", "topicId", TRAVEL, "visibility", "PRIVATE"));

        mvc.perform(get("/api/posts").param("topicId", String.valueOf(TRAVEL)))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].title").value("강릉 여행"))
                .andExpect(jsonPath("$.items[0].topicId").value(TRAVEL))
                .andExpect(jsonPath("$.items[0].topicName").value("여행"));
        // 비공개 글은 주인에게도 메인에 나오지 않는다 (BR-03)
        mvc.perform(get("/api/posts").param("topicId", String.valueOf(TRAVEL)).cookie(owner.session()))
                .andExpect(jsonPath("$.totalCount").value(1));
        mvc.perform(get("/api/posts")).andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    void 인기순은_조회수_더하기_좋아요_10배이고_같으면_최근_글이_먼저다() throws Exception {
        LoggedIn owner = signupAndLogin("주인", "owner@example.com");
        LoggedIn other = signupAndLogin("다른회원", "other@example.com");
        long a = createPost(owner, "A", "PUBLIC");
        long b = createPost(owner, "B", "PUBLIC");
        long c = createPost(owner, "C", "PUBLIC");
        jdbc.update("update post set view_count = 25 where id = ?", a);
        jdbc.update("update post set view_count = 5 where id = ?", b);
        jdbc.update("update post set view_count = 21 where id = ?", c);
        jdbc.update("insert into post_like (post_id, member_id, created_at) values (?, ?, now()), (?, ?, now()), (?, ?, now())",
                b, owner.memberId(), b, other.memberId(), c, other.memberId());

        // A = 25, B = 5 + 20 = 25 (A보다 최근), C = 21 + 10 = 31
        mvc.perform(get("/api/posts").param("sort", "popular"))
                .andExpect(jsonPath("$.items[0].title").value("C"))
                .andExpect(jsonPath("$.items[1].title").value("B"))
                .andExpect(jsonPath("$.items[2].title").value("A"));
        mvc.perform(get("/api/posts").param("sort", "latest"))
                .andExpect(jsonPath("$.items[0].title").value("C"))
                .andExpect(jsonPath("$.items[1].title").value("B"));
        // 모르는 정렬 값은 최신순
        mvc.perform(get("/api/posts").param("sort", "likes"))
                .andExpect(jsonPath("$.items[0].title").value("C"))
                .andExpect(jsonPath("$.items[2].title").value("A"));
        jdbc.update("update post set view_count = 100 where id = ?", a);
        mvc.perform(get("/api/posts").param("sort", "popular"))
                .andExpect(jsonPath("$.items[0].title").value("A"));
    }
}
