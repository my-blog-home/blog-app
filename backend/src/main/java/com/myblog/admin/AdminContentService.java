package com.myblog.admin;

import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.Messages;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 글·댓글 관리. 신고가 없어도 발행된 글과 댓글을 최근 순으로 보고, 제목·블로그·작성자·내용으로 찾고, 지운다.
 * 비밀 댓글은 내용을 내려 주지 않는다. 지우면 답글·좋아요·태그는 표의 ON DELETE 규칙으로 함께 지워진다.
 */
@Service
public class AdminContentService {

    public record PostRow(long id, String title, String visibility, java.time.Instant publishedAt, long blogId,
                          String blogName, Long authorId, String authorNickname, long commentCount, long reportCount) {
    }

    public record CommentRow(long id, String content, boolean secret, boolean reply, java.time.Instant createdAt,
                             long postId, String postTitle, Long authorId, String authorNickname, long reportCount) {
    }

    public record Page<T>(long totalCount, int page, int totalPages, List<T> items) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final BlogLimits limits;

    public AdminContentService(NamedParameterJdbcTemplate jdbc, BlogLimits limits) {
        this.jdbc = jdbc;
        this.limits = limits;
    }

    @Transactional(readOnly = true)
    public Page<PostRow> posts(String rawQuery, int requestedPage) {
        Map<String, Object> params = new HashMap<>();
        String where = " where p.status = 'PUBLISHED'" + search(rawQuery, params,
                "lower(p.title) like :q escape '\\' or lower(b.name) like :q escape '\\' or lower(m.nickname) like :q escape '\\'");
        String from = " from post p join blog b on b.id = p.blog_id left join member m on m.id = b.owner_id";
        long total = Objects.requireNonNullElse(
                jdbc.queryForObject("select count(*)" + from + where, params, Long.class), 0L);
        int page = paging(total, requestedPage, params);
        List<PostRow> items = jdbc.query("""
                select p.id, p.title, p.visibility, p.published_at, b.id as blog_id, b.name as blog_name,
                       m.id as author_id, case when m.withdrawn_at is null then m.nickname end as author_nickname,
                       (select count(*) from comment c where c.post_id = p.id) as comment_count,
                       (select count(*) from report r where r.post_id = p.id and r.target_type = 'POST') as report_count
                """ + from + where + " order by p.published_at desc, p.id desc limit :limit offset :offset", params,
                (rs, n) -> new PostRow(rs.getLong("id"), rs.getString("title"), rs.getString("visibility"),
                        rs.getTimestamp("published_at").toInstant(), rs.getLong("blog_id"), rs.getString("blog_name"),
                        rs.getObject("author_id", Long.class), rs.getString("author_nickname"),
                        rs.getLong("comment_count"), rs.getLong("report_count")));
        return new Page<>(total, page, totalPages(total), items);
    }

    @Transactional(readOnly = true)
    public Page<CommentRow> comments(String rawQuery, int requestedPage) {
        Map<String, Object> params = new HashMap<>();
        String where = " where true" + search(rawQuery, params,
                "(not c.is_secret and lower(c.content) like :q escape '\\') or lower(m.nickname) like :q escape '\\'");
        String from = " from comment c join post p on p.id = c.post_id left join member m on m.id = c.author_id";
        long total = Objects.requireNonNullElse(
                jdbc.queryForObject("select count(*)" + from + where, params, Long.class), 0L);
        int page = paging(total, requestedPage, params);
        List<CommentRow> items = jdbc.query("""
                select c.id, c.content, c.is_secret, c.parent_id, c.created_at, p.id as post_id, p.title as post_title,
                       m.id as author_id, case when m.withdrawn_at is null then m.nickname end as author_nickname,
                       (select count(*) from report r where r.comment_id = c.id) as report_count
                """ + from + where + " order by c.created_at desc, c.id desc limit :limit offset :offset", params,
                (rs, n) -> {
                    boolean secret = rs.getBoolean("is_secret");
                    return new CommentRow(rs.getLong("id"), secret ? null : rs.getString("content"), secret,
                            rs.getObject("parent_id") != null, rs.getTimestamp("created_at").toInstant(),
                            rs.getLong("post_id"), rs.getString("post_title"), rs.getObject("author_id", Long.class),
                            rs.getString("author_nickname"), rs.getLong("report_count"));
                });
        return new Page<>(total, page, totalPages(total), items);
    }

    @Transactional
    public void deletePost(long postId) {
        if (jdbc.update("delete from post where id = :id", Map.of("id", postId)) == 0) {
            throw ApiException.notFound(Messages.POST_NOT_FOUND);
        }
    }

    @Transactional
    public void deleteComment(long commentId) {
        if (jdbc.update("delete from comment where id = :id", Map.of("id", commentId)) == 0) {
            throw ApiException.notFound(Messages.COMMENT_NOT_FOUND);
        }
    }

    private static String search(String rawQuery, Map<String, Object> params, String condition) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        String escaped = rawQuery.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        params.put("q", "%" + escaped + "%");
        return " and (" + condition + ")";
    }

    private int paging(long total, int requestedPage, Map<String, Object> params) {
        int size = limits.pageSize();
        int page = Math.min(Math.max(1, requestedPage), totalPages(total));
        params.put("limit", size);
        params.put("offset", (page - 1) * size);
        return page;
    }

    private int totalPages(long total) {
        int size = limits.pageSize();
        return (int) Math.max(1, (total + size - 1) / size);
    }
}
