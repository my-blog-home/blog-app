package com.myblog.comment.service;

import com.myblog.common.config.BlogLimits;
import com.myblog.common.sql.PostVisibilitySql;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 활동의 "댓글 단 글": 내 댓글(답글 포함)이 가장 최근인 글부터, 글마다 내 가장 최근 댓글 하나와
 * 나머지 내 댓글 수("외 n개")를 보여 준다. 지금 읽을 수 없는 글은 목록과 개수에서 빠진다 (FR-069, BR-32).
 */
@Service
public class CommentActivityService {

    public record CommentedPost(long postId, String postTitle, long blogId, String blogName, long commentId,
                                String excerpt, boolean secret, boolean reply, Instant commentedAt, long otherCount) {
    }

    public record Page(long totalCount, int page, int totalPages, List<CommentedPost> items) {
    }

    /** 글마다 내 댓글을 최근 순으로 번호 매긴 것. rn = 1이 가장 최근 댓글 */
    private static final String MINE = """
            with mine as (
                select c.post_id, c.id, c.content, c.is_secret, c.parent_id, c.created_at,
                       row_number() over (partition by c.post_id order by c.created_at desc, c.id desc) as rn,
                       count(*) over (partition by c.post_id) as cnt
                from comment c
                where c.author_id = :me
            )
            """;

    private static final String FROM = """
            from mine
            join post p on p.id = mine.post_id
            join blog b on b.id = p.blog_id
            where mine.rn = 1 and %s
            """.formatted(PostVisibilitySql.readableBy("p", "b.owner_id", "me"));

    private final NamedParameterJdbcTemplate jdbc;
    private final BlogLimits limits;

    public CommentActivityService(NamedParameterJdbcTemplate jdbc, BlogLimits limits) {
        this.jdbc = jdbc;
        this.limits = limits;
    }

    @Transactional(readOnly = true)
    public Page commentedPosts(long memberId, int requestedPage) {
        Map<String, Object> params = new HashMap<>();
        params.put("me", memberId);
        long total = jdbc.queryForObject(MINE + "select count(*) " + FROM, params, Long.class);
        int size = limits.pageSize();
        int totalPages = (int) Math.max(1, (total + size - 1) / size);
        int page = Math.min(Math.max(1, requestedPage), totalPages);
        params.put("limit", size);
        params.put("offset", (page - 1) * size);
        List<CommentedPost> items = jdbc.query(MINE + """
                select p.id as post_id, p.title, b.id as blog_id, b.name as blog_name, mine.id as comment_id,
                       mine.content, mine.is_secret, mine.parent_id, mine.created_at, mine.cnt
                """ + FROM + """
                order by mine.created_at desc, mine.id desc
                limit :limit offset :offset
                """, params, (rs, row) -> new CommentedPost(rs.getLong("post_id"), rs.getString("title"),
                rs.getLong("blog_id"), rs.getString("blog_name"), rs.getLong("comment_id"),
                ManageCommentService.excerpt(rs.getString("content")), rs.getBoolean("is_secret"),
                rs.getObject("parent_id") != null, rs.getTimestamp("created_at").toInstant(), rs.getLong("cnt") - 1));
        return new Page(total, page, totalPages, items);
    }
}
