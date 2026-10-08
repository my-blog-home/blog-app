package com.myblog.blog.service;

import com.myblog.common.error.ApiException;
import com.myblog.common.error.Messages;
import com.myblog.common.sql.PostVisibilitySql;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이트 전체를 나누는 주제 10개 (고정, 시드 데이터). 블로그의 대표 주제와 글의 주제로 쓴다 (용어 '주제', CR-68).
 * 글 수는 글의 주제로 세고, 방문자에게 보이는 글만 센다.
 */
@Service
public class TopicService {

    /** 새 블로그의 대표 주제 '일상' (V8 시드의 id) */
    public static final long DEFAULT_TOPIC_ID = 9L;

    public record TopicView(long id, String name, long postCount) {
    }

    public record TopicRef(long id, String name) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public TopicService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<TopicView> list() {
        return jdbc.query("""
                select t.id, t.name,
                       (select count(*) from post p where p.topic_id = t.id and %s) as post_count
                from topic t order by t.sort_order
                """.formatted(PostVisibilitySql.PUBLIC), Map.of(),
                (rs, row) -> new TopicView(rs.getLong("id"), rs.getString("name"), rs.getLong("post_count")));
    }

    public TopicRef ref(long topicId) {
        return jdbc.query("select id, name from topic where id = :id", Map.of("id", topicId),
                        (rs, row) -> new TopicRef(rs.getLong("id"), rs.getString("name")))
                .stream().findFirst().orElseThrow(() -> ApiException.field("topicId", Messages.TOPIC_INVALID));
    }

    /** 없는 주제면 "주제를 다시 골라 주세요" */
    public long check(Long topicId) {
        if (topicId == null || jdbc.queryForObject("select count(*) from topic where id = :id",
                Map.of("id", topicId), Long.class) == 0) {
            throw ApiException.field("topicId", Messages.TOPIC_INVALID);
        }
        return topicId;
    }
}
