package com.myblog.post.service;

import com.myblog.blog.service.BlogService;
import com.myblog.common.config.BlogLimits;
import com.myblog.common.sql.PostVisibilitySql;
import com.myblog.post.domain.PostStatus;
import com.myblog.post.domain.Visibility;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 관리의 글 관리: 비공개·임시저장 포함 내 글 전체, 상태(전체·공개·비공개·임시저장)·분류로 거르기 (BM-03, FR-32, FR-40).
 * 작성일은 처음 작성완료한 시각이고, 임시저장 글은 마지막으로 저장한 시각을 보여 준다.
 */
@Service
public class ManagePostQueryService {

    public record ManagedPost(long id, String title, String categoryName, String categoryVisibility,
                              Instant createdAt, Visibility visibility, PostStatus status,
                              long viewCount, long commentCount) {
    }

    /** 글 관리 거르기. PUBLIC·PRIVATE는 작성완료한 글의 공개 설정, DRAFT는 임시저장 글 */
    public enum StatusFilter {
        ALL, PUBLIC, PRIVATE, DRAFT;

        /** status가 없으면 예전 visibility 값을 쓴다 */
        public static StatusFilter from(String status, Visibility visibility) {
            if (status != null && !status.isBlank()) {
                try {
                    return valueOf(status.strip().toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    return ALL;
                }
            }
            return visibility == null ? ALL : valueOf(visibility.name());
        }
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
    public Page list(long blogId, long memberId, StatusFilter filter, Long categoryId, int requestedPage) {
        blogService.getOwned(blogId, memberId);
        Map<String, Object> params = new HashMap<>();
        params.put("blogId", blogId);
        params.put("categoryId", categoryId);
        String statusCondition = switch (filter) {
            case ALL -> "true";
            case PUBLIC -> PostVisibilitySql.PUBLISHED + " and p.visibility = 'PUBLIC'";
            case PRIVATE -> PostVisibilitySql.PUBLISHED + " and p.visibility = 'PRIVATE'";
            case DRAFT -> "p.status = 'DRAFT'";
        };
        String where = """
                where p.blog_id = :blogId
                  and %s
                  and (cast(:categoryId as bigint) is null or p.category_id = :categoryId)
                """.formatted(statusCondition);
        long total = jdbc.queryForObject("select count(*) from post p " + where, params, Long.class);
        int size = limits.pageSize();
        int totalPages = (int) Math.max(1, (total + size - 1) / size);
        int page = Math.min(Math.max(1, requestedPage), totalPages);
        params.put("limit", size);
        params.put("offset", (page - 1) * size);
        List<ManagedPost> items = jdbc.query("""
                select p.id, p.title, c.name as category_name, c.visibility as category_visibility,
                       coalesce(p.published_at, p.updated_at, p.created_at) as shown_at, p.visibility, p.status,
                       p.view_count, (select count(*) from comment cm where cm.post_id = p.id) as comment_count
                from post p join category c on c.id = p.category_id
                """ + where + """
                order by coalesce(p.published_at, p.updated_at, p.created_at) desc, p.id desc
                limit :limit offset :offset
                """, params,
                (rs, row) -> new ManagedPost(rs.getLong("id"), rs.getString("title"), rs.getString("category_name"),
                        rs.getString("category_visibility"), rs.getTimestamp("shown_at").toInstant(),
                        Visibility.valueOf(rs.getString("visibility")), PostStatus.valueOf(rs.getString("status")),
                        rs.getLong("view_count"), rs.getLong("comment_count")));
        return new Page(total, page, totalPages, items);
    }
}
