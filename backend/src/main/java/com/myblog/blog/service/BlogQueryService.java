package com.myblog.blog.service;

import com.myblog.blog.domain.Blog;
import com.myblog.blog.domain.Category;
import com.myblog.blog.domain.CategoryRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 화면에 쓰는 정보. 분류별 글 수는 방문자에게 공개 글만 센다 (CF-08-9).
 */
@Service
public class BlogQueryService {

    public record CategoryView(long id, String name, int colorIndex, boolean isDefault, long postCount) {
    }

    public record BlogView(long id, String name, String description, String ownerNickname, boolean owner,
                           long totalPostCount, Long lastUsedCategoryId, List<CategoryView> categories) {
    }

    private final BlogService blogService;
    private final CategoryRepository categories;
    private final NamedParameterJdbcTemplate jdbc;

    public BlogQueryService(BlogService blogService, CategoryRepository categories, NamedParameterJdbcTemplate jdbc) {
        this.blogService = blogService;
        this.categories = categories;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public BlogView view(long blogId, Long viewerId) {
        Blog blog = blogService.get(blogId);
        boolean owner = blog.isOwnedBy(viewerId);
        Map<String, Object> params = Map.of("blogId", blogId, "includePrivate", owner);

        Map<Long, Long> counts = new HashMap<>();
        jdbc.query("""
                select category_id, count(*) as cnt from post
                where blog_id = :blogId and (:includePrivate or visibility = 'PUBLIC')
                group by category_id
                """, params, rs -> {
            counts.put(rs.getLong("category_id"), rs.getLong("cnt"));
        });

        List<CategoryView> categoryViews = categories.findByBlogIdOrderBySortOrderAsc(blogId).stream()
                .map((Category c) -> new CategoryView(c.getId(), c.getName(), c.getColorIndex(), c.isDefault(),
                        counts.getOrDefault(c.getId(), 0L)))
                .toList();
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        String nickname = jdbc.queryForObject("select nickname from member where id = :id",
                Map.of("id", blog.getOwnerId()), String.class);

        Long lastUsedCategoryId = null;
        if (owner) {
            List<Long> last = jdbc.queryForList("""
                    select category_id from post where blog_id = :blogId
                    order by created_at desc, id desc limit 1
                    """, params, Long.class);
            lastUsedCategoryId = last.isEmpty() ? null : last.get(0);
        }
        return new BlogView(blog.getId(), blog.getName(), blog.getDescription(), nickname, owner, total,
                lastUsedCategoryId, categoryViews);
    }
}
