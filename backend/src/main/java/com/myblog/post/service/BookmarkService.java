package com.myblog.post.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 북마크: 나중에 읽을 글을 저장한다. 좋아요와 달리 숫자가 드러나지 않고 본인만 본다.
 * 읽을 수 있는 글에만 누를 수 있고, 내 글도 저장할 수 있다.
 */
@Service
public class BookmarkService {

    public record BookmarkState(boolean bookmarked) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final PostService postService;
    private final Clock clock;

    public BookmarkService(NamedParameterJdbcTemplate jdbc, PostService postService, Clock clock) {
        this.jdbc = jdbc;
        this.postService = postService;
        this.clock = clock;
    }

    /** 누를 때마다 저장과 해제를 오간다 */
    @Transactional
    public BookmarkState toggle(long postId, long memberId) {
        postService.getVisible(postId, memberId);
        Map<String, Object> params = Map.of("post", postId, "me", memberId, "now", Timestamp.from(clock.instant()));
        int removed = jdbc.update("delete from bookmark where post_id = :post and member_id = :me", params);
        if (removed > 0) {
            return new BookmarkState(false);
        }
        jdbc.update("""
                insert into bookmark (member_id, post_id, created_at) values (:me, :post, :now)
                on conflict do nothing
                """, params);
        return new BookmarkState(true);
    }
}
