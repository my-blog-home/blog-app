package com.myblog.blog.service;

import com.myblog.blog.domain.Blog;
import com.myblog.blog.domain.Category;
import com.myblog.blog.domain.CategoryRepository;
import com.myblog.blog.domain.CategoryVisibility;
import com.myblog.common.sql.PostVisibilitySql;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 화면에 쓰는 정보. 분류별 글 수는 방문자에게 보이는 글만 센다 (CF-08-9, BR-46).
 * 비공개 분류는 방문자에게 목록에서 빠지고, 주인에게는 공개 범위를 함께 알려 준다.
 * 임시저장 글은 주인에게도 글 수에 넣지 않는다 (BR-01).
 */
@Service
public class BlogQueryService {

    /** publicPostCount: 작성완료한 글 중 글 자체가 공개인 수 (분류를 공개로 바꿀 때 확인용, 주인에게만 의미 있음) */
    public record CategoryView(long id, String name, String description, CategoryVisibility visibility, int colorIndex,
                               boolean isDefault, long postCount, long publicPostCount) {
    }

    public record BlogView(long id, String name, String description, TopicService.TopicRef topic,
                           String ownerNickname, String ownerColor, boolean owner, long totalPostCount,
                           Long lastUsedCategoryId, Long lastUsedTopicId, List<CategoryView> categories) {
    }

    private final BlogService blogService;
    private final CategoryRepository categories;
    private final TopicService topics;
    private final NamedParameterJdbcTemplate jdbc;

    public BlogQueryService(BlogService blogService, CategoryRepository categories, TopicService topics,
                            NamedParameterJdbcTemplate jdbc) {
        this.blogService = blogService;
        this.categories = categories;
        this.topics = topics;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public BlogView view(long blogId, Long viewerId) {
        Blog blog = blogService.get(blogId);
        boolean owner = blog.isOwnedBy(viewerId);
        Map<String, Object> params = Map.of("blogId", blogId, "includePrivate", owner);

        Map<Long, long[]> counts = new HashMap<>();
        jdbc.query("""
                select p.category_id, count(*) as cnt,
                       count(*) filter (where p.visibility = 'PUBLIC') as public_cnt
                from post p
                where p.blog_id = :blogId and %s
                group by p.category_id
                """.formatted(PostVisibilitySql.visibleTo("p", "includePrivate")), params, rs -> {
            counts.put(rs.getLong("category_id"), new long[] {rs.getLong("cnt"), rs.getLong("public_cnt")});
        });

        List<CategoryView> categoryViews = categories.findOrdered(blogId).stream()
                .filter(c -> owner || c.isPublic())
                .map((Category c) -> {
                    long[] count = counts.getOrDefault(c.getId(), new long[2]);
                    return new CategoryView(c.getId(), c.getName(), c.getDescription(), c.getVisibility(),
                            c.getColorIndex(), c.isDefault(), count[0], count[1]);
                })
                .toList();
        long total = counts.values().stream().mapToLong(c -> c[0]).sum();

        Map<String, Object> ownerRow = jdbc.queryForMap("select nickname, profile_color from member where id = :id",
                Map.of("id", blog.getOwnerId()));

        Long lastUsedCategoryId = null;
        Long lastUsedTopicId = null;
        if (owner) {
            // 글쓰기의 처음 값: 마지막에 쓴 글의 분류·주제 (CF-05-5, FR-09)
            List<Map<String, Object>> last = jdbc.queryForList("""
                    select category_id, topic_id from post where blog_id = :blogId
                    order by created_at desc, id desc limit 1
                    """, params);
            if (!last.isEmpty()) {
                lastUsedCategoryId = ((Number) last.get(0).get("category_id")).longValue();
                lastUsedTopicId = ((Number) last.get(0).get("topic_id")).longValue();
            }
        }
        return new BlogView(blog.getId(), blog.getName(), blog.getDescription(), topics.ref(blog.getTopicId()),
                (String) ownerRow.get("nickname"), (String) ownerRow.get("profile_color"), owner, total,
                lastUsedCategoryId, lastUsedTopicId, categoryViews);
    }
}
