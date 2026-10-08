package com.myblog.post.service;

import com.myblog.blog.service.BlogService;
import com.myblog.common.config.BlogLimits;
import com.myblog.post.domain.Visibility;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 관리의 글 관리: 비공개 포함 내 글 전체, 공개 여부·분류로 거르기 (BM-03).
 */
@Service
public class ManagePostQueryService {

    public record ManagedPost(long id, String title, String categoryName, Instant createdAt, Visibility visibility,
                              long viewCount, long commentCount) {
    }

    public record Page(long totalCount, int page, int totalPages, List<ManagedPost> items) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final BlogService blogService;
    private final BlogLimits limits;

    public ManagePostQueryService(NamedParameterJdbcTemplate jdbc, BlogService blogService, BlogLimits limits) {
        this.jdbc = jdbc;
        this.blogService = blogService;
        this.limits = limits;
    }

    @Transactional(readOnly = true)
    public Page list(long blogId, long memberId, Visibility visibility, Long categoryId, int requestedPage) {
        blogService.getOwned(blogId, memberId);
        Map<String, Object> params = new HashMap<>();
        params.put("blogId", blogId);
        params.put("visibility", visibility == null ? null : visibility.name());
        params.put("categoryId", categoryId);
        String where = """
                where p.blog_id = :blogId
                  and (cast(:visibility as varchar) is null or p.visibility = :visibility)
                  and (cast(:categoryId as bigint) is null or p.category_id = :categoryId)
                """;
        long total = jdbc.queryForObject("select count(*) from post p " + where, params, Long.class);
        int size = limits.pageSize();
        int totalPages = (int) Math.max(1, (total + size - 1) / size);
        int page = Math.min(Math.max(1, requestedPage), totalPages);
        params.put("limit", size);
        params.put("offset", (page - 1) * size);
        List<ManagedPost> items = jdbc.query("""
                select p.id, p.title, c.name as category_name, p.created_at, p.visibility, p.view_count,
                       (select count(*) from comment cm where cm.post_id = p.id) as comment_count
                from post p join category c on c.id = p.category_id
                """ + where + "order by p.created_at desc, p.id desc limit :limit offset :offset", params,
                (rs, row) -> new ManagedPost(rs.getLong("id"), rs.getString("title"), rs.getString("category_name"),
                        rs.getTimestamp("created_at").toInstant(), Visibility.valueOf(rs.getString("visibility")),
                        rs.getLong("view_count"), rs.getLong("comment_count")));
        return new Page(total, page, totalPages, items);
    }
}
