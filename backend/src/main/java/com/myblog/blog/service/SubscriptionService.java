package com.myblog.blog.service;

import com.myblog.blog.domain.Blog;
import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구독: 로그인한 회원이 남의 블로그를 구독·취소한다. 회원별로 기록하고 구독자 수는 모두의 합이다 (FR-067, BR-12).
 * 같은 요청을 여러 번 보내도 결과가 같다.
 */
@Service
public class SubscriptionService {

    public record SubscriptionState(boolean subscribed, long subscriberCount) {
    }

    public record SubscribedBlog(long blogId, String blogName, String ownerNickname) {
    }

    /** 햄버거 메뉴의 구독한 블로그: 전체 수와 최근에 구독한 것부터 limit개 (FR-068) */
    public record MySubscriptions(long totalCount, List<SubscribedBlog> items) {
    }

    private final BlogService blogService;
    private final NamedParameterJdbcTemplate jdbc;
    private final BlogLimits limits;
    private final Clock clock;

    public SubscriptionService(BlogService blogService, NamedParameterJdbcTemplate jdbc, BlogLimits limits, Clock clock) {
        this.blogService = blogService;
        this.jdbc = jdbc;
        this.limits = limits;
        this.clock = clock;
    }

    /** 내 블로그는 구독할 수 없다 (BR-12) */
    @Transactional
    public SubscriptionState subscribe(long blogId, long memberId) {
        Blog blog = blogService.get(blogId);
        if (blog.isOwnedBy(memberId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, Messages.SUBSCRIBE_OWN_BLOG);
        }
        jdbc.update("""
                insert into subscription (member_id, blog_id, created_at) values (:memberId, :blogId, :now)
                on conflict do nothing
                """, Map.of("memberId", memberId, "blogId", blogId, "now", Timestamp.from(clock.instant())));
        return state(blogId, memberId);
    }

    @Transactional
    public SubscriptionState unsubscribe(long blogId, long memberId) {
        blogService.get(blogId);
        jdbc.update("delete from subscription where member_id = :memberId and blog_id = :blogId",
                Map.of("memberId", memberId, "blogId", blogId));
        return state(blogId, memberId);
    }

    public SubscriptionState state(long blogId, Long viewerId) {
        Map<String, Object> params = new HashMap<>();
        params.put("blogId", blogId);
        params.put("viewer", viewerId);
        return jdbc.queryForObject("""
                select count(*) as cnt,
                       count(*) filter (where member_id = cast(:viewer as bigint)) > 0 as mine
                from subscription where blog_id = :blogId
                """, params, (rs, row) -> new SubscriptionState(rs.getBoolean("mine"), rs.getLong("cnt")));
    }

    /** limit은 1~8개(기본 8개). 나머지는 totalCount로 "외 N개 더 보기"를 보여 준다 */
    @Transactional(readOnly = true)
    public MySubscriptions mine(long memberId, Integer requestedLimit) {
        int max = limits.drawerSubscriptionMax();
        int limit = requestedLimit == null ? max : Math.min(Math.max(1, requestedLimit), max);
        Map<String, Object> params = Map.of("memberId", memberId, "limit", limit);
        long total = jdbc.queryForObject("select count(*) from subscription where member_id = :memberId",
                params, Long.class);
        List<SubscribedBlog> items = jdbc.query("""
                select b.id, b.name, m.nickname
                from subscription s
                join blog b on b.id = s.blog_id
                join member m on m.id = b.owner_id
                where s.member_id = :memberId
                order by s.created_at desc, b.id desc
                limit :limit
                """, params, (rs, row) -> new SubscribedBlog(rs.getLong("id"), rs.getString("name"),
                rs.getString("nickname")));
        return new MySubscriptions(total, items);
    }
}
