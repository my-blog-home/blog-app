package com.myblog.stats;

import com.myblog.blog.service.BlogService;
import com.myblog.comment.service.ManageCommentService;
import com.myblog.common.error.ApiException;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드와 통계 화면 (BM-02, BM-06). 누적은 일별 기록의 합으로 구한다 (research R-12).
 */
@Service
public class StatsQueryService {

    public record Counts(long today, long yesterday, long total) {
    }

    public record Daily(LocalDate date, long views, long visitors, long comments) {
    }

    public record PopularPost(long postId, String title, long views) {
    }

    public record RecentPost(long postId, String title, Instant createdAt, String visibility) {
    }

    public record Dashboard(Counts views, Counts visitors, long newCommentCount, List<Daily> daily30,
                            List<PopularPost> popular7, List<RecentPost> recent) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final BlogService blogService;
    private final ManageCommentService comments;
    private final Clock clock;
    private final ZoneId zone;

    public StatsQueryService(NamedParameterJdbcTemplate jdbc, BlogService blogService, ManageCommentService comments,
                             Clock clock, @Value("${blog.zone}") String zone) {
        this.jdbc = jdbc;
        this.blogService = blogService;
        this.comments = comments;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(long blogId, long memberId) {
        blogService.getOwned(blogId, memberId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<Daily> daily30 = daily(blogId, today, 30);
        Map<String, Object> totals = jdbc.queryForMap("""
                select coalesce(sum(view_count), 0) as views, coalesce(sum(visitor_count), 0) as visitors
                from daily_stats where blog_id = :blogId
                """, Map.of("blogId", blogId));
        Daily t = daily30.get(daily30.size() - 1);
        Daily y = daily30.get(daily30.size() - 2);

        Map<String, Object> params = new HashMap<>();
        params.put("blogId", blogId);
        params.put("from", Date.valueOf(today.minusDays(6)));
        List<PopularPost> popular = jdbc.query("""
                select p.id, p.title, sum(v.view_count) as views
                from post_daily_views v join post p on p.id = v.post_id
                where p.blog_id = :blogId and p.visibility = 'PUBLIC' and v.stat_date >= :from
                group by p.id, p.title
                order by views desc, p.id desc
                limit 5
                """, params, (rs, row) -> new PopularPost(rs.getLong("id"), rs.getString("title"), rs.getLong("views")));
        List<RecentPost> recent = jdbc.query("""
                select id, title, created_at, visibility from post where blog_id = :blogId
                order by created_at desc, id desc limit 5
                """, params, (rs, row) -> new RecentPost(rs.getLong("id"), rs.getString("title"),
                rs.getTimestamp("created_at").toInstant(), rs.getString("visibility")));

        return new Dashboard(
                new Counts(t.views(), y.views(), ((Number) totals.get("views")).longValue()),
                new Counts(t.visitors(), y.visitors(), ((Number) totals.get("visitors")).longValue()),
                comments.countNew(blogId), daily30, popular, recent);
    }

    /** 7일 또는 30일 (BM-06-1) */
    @Transactional(readOnly = true)
    public List<Daily> stats(long blogId, long memberId, int days) {
        blogService.getOwned(blogId, memberId);
        if (days != 7 && days != 30) {
            throw ApiException.field("days", "기간은 7일 또는 30일입니다");
        }
        return daily(blogId, LocalDate.now(clock.withZone(zone)), days);
    }

    /** 기록이 없는 날은 0으로 채운다. 댓글 수는 한국 시간 날짜로 센다 */
    private List<Daily> daily(long blogId, LocalDate today, int days) {
        LocalDate from = today.minusDays(days - 1);
        Map<String, Object> params = new HashMap<>();
        params.put("blogId", blogId);
        params.put("from", Date.valueOf(from));
        params.put("to", Date.valueOf(today));
        params.put("zone", zone.getId());
        return jdbc.query("""
                select d::date as day,
                       coalesce(s.view_count, 0) as views,
                       coalesce(s.visitor_count, 0) as visitors,
                       (select count(*) from comment c join post p on p.id = c.post_id
                        where p.blog_id = :blogId and (c.created_at at time zone :zone)::date = d::date) as comments
                from generate_series(cast(:from as date), cast(:to as date), interval '1 day') d
                left join daily_stats s on s.blog_id = :blogId and s.stat_date = d::date
                order by day
                """, params, (rs, row) -> new Daily(rs.getDate("day").toLocalDate(), rs.getLong("views"),
                rs.getLong("visitors"), rs.getLong("comments")));
    }
}
