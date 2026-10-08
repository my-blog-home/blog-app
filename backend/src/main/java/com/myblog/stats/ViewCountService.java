package com.myblog.stats;

import com.myblog.post.service.PostViewedEvent;
import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 조회수와 방문자 집계 (BM-06-3~7, research R-12).
 * 같은 사람이 같은 글을 30분 안에 다시 열면 세지 않고, 방문자는 블로그별로 하루 한 번, 블로그 주인 본인은 세지 않는다.
 */
@Service
public class ViewCountService {

    private static final Logger log = LoggerFactory.getLogger(ViewCountService.class);
    private static final Duration VIEW_DEDUP = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final ZoneId zone;

    public ViewCountService(StringRedisTemplate redis, NamedParameterJdbcTemplate jdbc, Clock clock,
                            @Value("${blog.zone}") String zone) {
        this.redis = redis;
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPostViewed(PostViewedEvent event) {
        if (event.viewerId() != null && event.viewerId() == event.ownerId()) {
            return;
        }
        try {
            ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
            LocalDate today = now.toLocalDate();
            Map<String, Object> params = Map.of("blogId", event.blogId(), "postId", event.postId(),
                    "date", Date.valueOf(today));

            boolean newView = Boolean.TRUE.equals(redis.opsForValue()
                    .setIfAbsent("view:" + event.postId() + ":" + event.visitorKey(), "1", VIEW_DEDUP));
            if (newView) {
                jdbc.update("update post set view_count = view_count + 1 where id = :postId", params);
                jdbc.update("""
                        insert into post_daily_views (post_id, stat_date, view_count) values (:postId, :date, 1)
                        on conflict (post_id, stat_date) do update set view_count = post_daily_views.view_count + 1
                        """, params);
                jdbc.update("""
                        insert into daily_stats (blog_id, stat_date, view_count) values (:blogId, :date, 1)
                        on conflict (blog_id, stat_date) do update set view_count = daily_stats.view_count + 1
                        """, params);
            }

            Duration untilMidnight = Duration.between(now, today.plusDays(1).atStartOfDay(zone));
            boolean newVisitor = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                    "visit:" + event.blogId() + ":" + event.visitorKey() + ":" + today, "1", untilMidnight));
            if (newVisitor) {
                jdbc.update("""
                        insert into daily_stats (blog_id, stat_date, visitor_count) values (:blogId, :date, 1)
                        on conflict (blog_id, stat_date) do update set visitor_count = daily_stats.visitor_count + 1
                        """, params);
            }
        } catch (RuntimeException e) {
            // 통계가 실패해도 글은 보여야 한다
            log.warn("조회수 집계 실패: post {}", event.postId(), e);
        }
    }
}
