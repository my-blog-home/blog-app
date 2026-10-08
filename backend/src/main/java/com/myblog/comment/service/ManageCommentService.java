package com.myblog.comment.service;

import com.myblog.blog.domain.BlogRepository;
import com.myblog.blog.service.BlogService;
import com.myblog.common.config.BlogLimits;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 관리의 댓글 관리와 새 댓글 표시 (BM-05, research R-13).
 * 새 댓글 = 댓글 관리를 마지막으로 연 뒤 남이 단 댓글.
 */
@Service
public class ManageCommentService {

    public record ManagedComment(long id, String authorNickname, Instant createdAt, String excerpt, long postId,
                                 String postTitle, boolean isNew) {
    }

    public record Page(long totalCount, int page, int totalPages, List<ManagedComment> items) {
    }

    public record NewCount(Long blogId, long count) {
    }

    private static final String IS_NEW = """
            (c.created_at > coalesce(b.comments_last_viewed_at, b.created_at)
             and (c.author_id is null or c.author_id <> b.owner_id))
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final BlogService blogService;
    private final BlogRepository blogs;
    private final BlogLimits limits;
    private final Clock clock;

    public ManageCommentService(NamedParameterJdbcTemplate jdbc, BlogService blogService, BlogRepository blogs,
                                BlogLimits limits, Clock clock) {
        this.jdbc = jdbc;
        this.blogService = blogService;
        this.blogs = blogs;
        this.limits = limits;
        this.clock = clock;
    }

    /** 내 블로그 글에 달린 모든 댓글, 최신순 (BM-05-1). 읽음 처리는 markRead로 따로 한다 */
    @Transactional(readOnly = true)
    public Page list(long blogId, long memberId, int requestedPage) {
        blogService.getOwned(blogId, memberId);
        Map<String, Object> params = new HashMap<>();
        params.put("blogId", blogId);
        long total = jdbc.queryForObject("""
                select count(*) from comment c join post p on p.id = c.post_id where p.blog_id = :blogId
                """, params, Long.class);
        int size = limits.pageSize();
        int totalPages = (int) Math.max(1, (total + size - 1) / size);
        int page = Math.min(Math.max(1, requestedPage), totalPages);
        params.put("limit", size);
        params.put("offset", (page - 1) * size);
        List<ManagedComment> items = jdbc.query("""
                select c.id, m.nickname, c.created_at, c.content, p.id as post_id, p.title,
                """ + IS_NEW + """
                 as is_new
                from comment c
                join post p on p.id = c.post_id
                join blog b on b.id = p.blog_id
                left join member m on m.id = c.author_id
                where p.blog_id = :blogId
                order by c.created_at desc, c.id desc
                limit :limit offset :offset
                """, params, (rs, row) -> new ManagedComment(rs.getLong("id"), rs.getString("nickname"),
                rs.getTimestamp("created_at").toInstant(), excerpt(rs.getString("content")), rs.getLong("post_id"),
                rs.getString("title"), rs.getBoolean("is_new")));
        return new Page(total, page, totalPages, items);
    }

    /** 댓글 관리 화면을 열면 새 댓글이 모두 읽음이 된다 (BM-05-6) */
    @Transactional
    public void markRead(long blogId, long memberId) {
        blogService.getOwned(blogId, memberId);
        jdbc.update("update blog set comments_last_viewed_at = :now where id = :blogId",
                Map.of("now", Timestamp.from(clock.instant()), "blogId", blogId));
    }

    /** 메뉴·사용자 메뉴·대시보드에 보여 줄 새 댓글 수 (BM-05-5) */
    @Transactional(readOnly = true)
    public NewCount newCount(long memberId) {
        return blogs.findFirstByOwnerIdOrderByIdAsc(memberId)
                .map(blog -> new NewCount(blog.getId(), countNew(blog.getId())))
                .orElse(new NewCount(null, 0));
    }

    public long countNew(long blogId) {
        return jdbc.queryForObject("""
                select count(*) from comment c join post p on p.id = c.post_id join blog b on b.id = p.blog_id
                where p.blog_id = :blogId and
                """ + IS_NEW, Map.of("blogId", blogId), Long.class);
    }

    private static String excerpt(String content) {
        String text = content.replaceAll("\\s+", " ").strip();
        return text.codePointCount(0, text.length()) <= 50 ? text : text.substring(0, text.offsetByCodePoints(0, 50)) + "…";
    }
}
