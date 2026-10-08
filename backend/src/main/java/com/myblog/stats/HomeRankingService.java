package com.myblog.stats;

import com.myblog.common.config.BlogLimits;
import com.myblog.common.sql.PostVisibilitySql;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 첫 화면의 "지금 핫한 글"과 "이번 주 인기 블로거" (FR-072, FR-074, BR-33, BR-45).
 * 오늘이 아니라 가장 최근 공개 글이 올라온 날(한국 날짜)부터 거꾸로 N일 안에 쓴 공개 글만 본다. 그래서 시간이 지나도 비지 않는다.
 * 점수 = 좋아요 수 + 댓글 수(답글 포함) × 5. 조회수는 점수에 넣지 않는다. 주제를 고르면 글의 주제로 거른다.
 */
@Service
public class HomeRankingService {

    public record HotPost(long id, String title, long blogId, String blogName, String categoryName,
                          String authorNickname, String authorColor, long viewCount, long likeCount,
                          long commentCount, String topicName) {
    }

    public record HotBlogger(int rank, long blogId, String blogName, long ownerId, String ownerNickname,
                             String ownerColor, long subscriberCount, long score) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final BlogLimits limits;
    private final String zone;

    public HomeRankingService(NamedParameterJdbcTemplate jdbc, BlogLimits limits, @Value("${blog.zone}") String zone) {
        this.jdbc = jdbc;
        this.limits = limits;
        this.zone = zone;
    }

    /**
     * 기간 안의 공개 글(별칭 p)과 점수(score)를 고르는 공통 조건.
     * 글이 올라온 한국 날짜가 (가장 최근 공개 글의 한국 날짜 - days)보다 뒤여야 한다
     */
    private String windowedPosts(int days, Map<String, Object> params, Long topicId) {
        params.put("zone", zone);
        params.put("topicId", topicId);
        params.put("days", days);
        params.put("weight", limits.hotCommentWeight());
        return """
                select p.*,
                       (select count(*) from post_like pl where pl.post_id = p.id) as like_count,
                       (select count(*) from comment cm where cm.post_id = p.id) as comment_count
                from post p
                where %1$s
                  and (cast(:topicId as bigint) is null or p.topic_id = :topicId)
                  and (p.published_at at time zone :zone)::date > (
                      select (max(lp.published_at) at time zone :zone)::date - cast(:days as int)
                      from post lp
                      where %2$s and (cast(:topicId as bigint) is null or lp.topic_id = :topicId))
                """.formatted(PostVisibilitySql.PUBLIC, PostVisibilitySql.publicPost("lp"));
    }

    /** 점수가 같으면 조회수가 큰 글, 그래도 같으면 최근 글이 앞선다 (BR-33) */
    @Transactional(readOnly = true)
    public List<HotPost> hotPosts(Long topicId, Integer requestedLimit) {
        int max = limits.hotPostCount();
        int limit = requestedLimit == null ? max : Math.min(Math.max(1, requestedLimit), 10);
        Map<String, Object> params = new HashMap<>();
        String windowed = windowedPosts(limits.hotPostDays(), params, topicId);
        params.put("limit", limit);
        return jdbc.query("""
                with w as (%s)
                select w.id, w.title, w.blog_id, b.name as blog_name, c.name as category_name,
                       m.nickname, m.profile_color, w.view_count, w.like_count, w.comment_count, t.name as topic_name
                from w
                join blog b on b.id = w.blog_id
                join member m on m.id = b.owner_id
                join category c on c.id = w.category_id
                join topic t on t.id = w.topic_id
                order by (w.like_count + :weight * w.comment_count) desc, w.view_count desc,
                         w.published_at desc, w.id desc
                limit :limit
                """.formatted(windowed), params, (rs, row) -> new HotPost(rs.getLong("id"), rs.getString("title"),
                rs.getLong("blog_id"), rs.getString("blog_name"), rs.getString("category_name"),
                rs.getString("nickname"), rs.getString("profile_color"), rs.getLong("view_count"),
                rs.getLong("like_count"), rs.getLong("comment_count"), rs.getString("topic_name")));
    }

    /** 블로그마다 기간 안 글의 점수를 더한다. 같으면 구독자가 많은 블로그, 그래도 같으면 최근 글이 있는 블로그 (BR-45) */
    @Transactional(readOnly = true)
    public List<HotBlogger> hotBloggers(Long topicId) {
        Map<String, Object> params = new HashMap<>();
        String windowed = windowedPosts(limits.hotBloggerDays(), params, topicId);
        params.put("limit", limits.hotBloggerCount());
        List<HotBlogger> rows = jdbc.query("""
                with w as (%s),
                     per_blog as (
                         select w.blog_id, sum(w.like_count + :weight * w.comment_count) as score,
                                max(w.published_at) as latest
                         from w group by w.blog_id)
                select pb.blog_id, pb.score, b.name as blog_name, m.id as owner_id, m.nickname, m.profile_color,
                       (select count(*) from subscription s where s.blog_id = pb.blog_id) as subscriber_count,
                       pb.latest
                from per_blog pb
                join blog b on b.id = pb.blog_id
                join member m on m.id = b.owner_id
                order by pb.score desc, subscriber_count desc, pb.latest desc, pb.blog_id desc
                limit :limit
                """.formatted(windowed), params, (rs, row) -> new HotBlogger(row + 1, rs.getLong("blog_id"),
                rs.getString("blog_name"), rs.getLong("owner_id"), rs.getString("nickname"),
                rs.getString("profile_color"), rs.getLong("subscriber_count"), rs.getLong("score")));
        return rows;
    }
}
