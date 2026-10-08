package com.myblog.post.service;

import com.myblog.blog.domain.Blog;
import com.myblog.blog.service.BlogService;
import com.myblog.common.config.BlogLimits;
import com.myblog.post.domain.Post;
import com.myblog.post.domain.Visibility;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 목록·상세·이전/다음 글. 비공개 글은 블로그 주인 본인에게만 나온다 (CF-09, CF-10, CF-13).
 */
@Service
public class PostQueryService {

    public record PostItem(long id, String title, String excerpt, long blogId, String blogName,
                           long categoryId, String categoryName, Instant createdAt, Visibility visibility,
                           String thumbnailUrl) {
    }

    /** 본문의 첫 이미지를 목록 썸네일로 쓴다 */
    private static final java.util.regex.Pattern FIRST_IMAGE =
            java.util.regex.Pattern.compile("!\\[[^\\]]*]\\((/images/[0-9a-f\\-]{36}\\.(?:jpg|png|gif|webp))\\)");

    public record PageResult(long totalCount, int page, int totalPages, List<PostItem> items) {
    }

    public record Ref(long id, String name) {
    }

    public record PostDetail(long id, Ref blog, Ref category, String title, String body, Visibility visibility,
                             Instant createdAt, Instant updatedAt, Long prevPostId, Long nextPostId, boolean editable,
                             long likeCount, boolean likedByMe, long commentCount, List<String> tags) {
    }

    private static final String SELECT_ITEMS = """
            select p.id, p.title, p.body, p.blog_id, b.name as blog_name, p.category_id, c.name as category_name,
                   p.created_at, p.visibility
            from post p
            join blog b on b.id = p.blog_id
            join category c on c.id = p.category_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PostService postService;
    private final BlogService blogService;
    private final TagService tagService;
    private final BlogLimits limits;
    private final ApplicationEventPublisher events;

    public PostQueryService(NamedParameterJdbcTemplate jdbc, PostService postService, BlogService blogService,
                            TagService tagService, BlogLimits limits, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.postService = postService;
        this.blogService = blogService;
        this.tagService = tagService;
        this.limits = limits;
        this.events = events;
    }

    /** 태그를 누르면 같은 태그가 붙은 공개 글을 보여 준다 (CF-20-3) */
    @Transactional(readOnly = true)
    public PageResult tagPosts(String tag, int page) {
        Map<String, Object> params = new HashMap<>();
        params.put("tag", tag.strip().replaceFirst("^#+", ""));
        return page("""
                where p.visibility = 'PUBLIC' and exists (
                    select 1 from post_tag pt join tag t on t.id = pt.tag_id
                    where pt.post_id = p.id and lower(t.name) = lower(:tag))
                """, params, page);
    }

    /** 한 블로그의 글 목록. 주인이면 비공개 글도 포함 (CF-10-6, 7) */
    @Transactional(readOnly = true)
    public PageResult blogPosts(long blogId, Long categoryId, int page, Long viewerId) {
        Blog blog = blogService.get(blogId);
        Map<String, Object> params = new HashMap<>();
        params.put("blogId", blogId);
        params.put("includePrivate", blog.isOwnedBy(viewerId));
        params.put("categoryId", categoryId);
        String where = """
                where p.blog_id = :blogId
                  and (:includePrivate or p.visibility = 'PUBLIC')
                  and (cast(:categoryId as bigint) is null or p.category_id = :categoryId)
                """;
        return page(where, params, page);
    }

    /** 첫 화면: 모든 블로그의 최근 공개 글 */
    @Transactional(readOnly = true)
    public PageResult recentPublic(int page) {
        return page("where p.visibility = 'PUBLIC'\n", new HashMap<>(), page);
    }

    /** 검색 모듈이 만든 조건으로 공개 글을 읽는다 (search → post 방향) */
    @Transactional(readOnly = true)
    public PageResult searchPage(String where, Map<String, Object> params, int page) {
        return page(where, params, page);
    }

    /** where 조건으로 세고, 범위를 넘는 페이지는 마지막 페이지로 바꿔 읽는다 (CF-10-1~4) */
    PageResult page(String where, Map<String, Object> params, int requestedPage) {
        long total = jdbc.queryForObject("select count(*) from post p " + where, params, Long.class);
        int size = limits.pageSize();
        int totalPages = (int) Math.max(1, (total + size - 1) / size);
        int page = Math.min(Math.max(1, requestedPage), totalPages);
        params.put("limit", size);
        params.put("offset", (page - 1) * size);
        List<PostItem> items = jdbc.query(SELECT_ITEMS + where
                + "order by p.created_at desc, p.id desc limit :limit offset :offset", params, itemMapper());
        return new PageResult(total, page, totalPages, items);
    }

    @Transactional(readOnly = true)
    public PostDetail detail(long postId, Long viewerId, String visitorKey) {
        Post post = postService.getVisible(postId, viewerId);
        Blog blog = blogService.get(post.getBlogId());
        if (visitorKey != null) {
            events.publishEvent(new PostViewedEvent(post.getId(), blog.getId(), blog.getOwnerId(), visitorKey, viewerId));
        }
        String categoryName = jdbc.queryForObject("select name from category where id = :id",
                Map.of("id", post.getCategoryId()), String.class);
        Map<String, Object> params = Map.of("blogId", post.getBlogId(), "createdAt",
                java.sql.Timestamp.from(post.getCreatedAt()), "id", post.getId());
        // 이전 글 = 바로 앞에 쓴 공개 글, 다음 글 = 바로 뒤에 쓴 공개 글 (CF-09-2)
        Long prev = first(jdbc.queryForList("""
                select id from post
                where blog_id = :blogId and visibility = 'PUBLIC' and (created_at, id) < (:createdAt, :id)
                order by created_at desc, id desc limit 1
                """, params, Long.class));
        Long next = first(jdbc.queryForList("""
                select id from post
                where blog_id = :blogId and visibility = 'PUBLIC' and (created_at, id) > (:createdAt, :id)
                order by created_at asc, id asc limit 1
                """, params, Long.class));
        return new PostDetail(post.getId(), new Ref(blog.getId(), blog.getName()),
                new Ref(post.getCategoryId(), categoryName), post.getTitle(), post.getBody(), post.getVisibility(),
                post.getCreatedAt(), post.getUpdatedAt(), prev, next, blog.isOwnedBy(viewerId),
                count("select count(*) from post_like where post_id = :id", post.getId(), null),
                viewerId != null && count("select count(*) from post_like where post_id = :id and member_id = :viewer",
                        post.getId(), viewerId) > 0,
                count("select count(*) from comment where post_id = :id", post.getId(), null),
                tagService.tagsOf(post.getId()));
    }

    /** 좋아요·댓글 수는 표에서 직접 센다 (post가 comment 모듈을 부르지 않도록) */
    private long count(String sql, long postId, Long viewerId) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", postId);
        params.put("viewer", viewerId);
        return jdbc.queryForObject(sql, params, Long.class);
    }

    RowMapper<PostItem> itemMapper() {
        return (ResultSet rs, int row) -> mapItem(rs);
    }

    private PostItem mapItem(ResultSet rs) throws SQLException {
        return new PostItem(rs.getLong("id"), rs.getString("title"),
                Excerpts.of(rs.getString("body"), limits.excerptLength()),
                rs.getLong("blog_id"), rs.getString("blog_name"),
                rs.getLong("category_id"), rs.getString("category_name"),
                rs.getTimestamp("created_at").toInstant(), Visibility.valueOf(rs.getString("visibility")),
                thumbnail(rs.getString("body")));
    }

    private static String thumbnail(String body) {
        java.util.regex.Matcher m = FIRST_IMAGE.matcher(body);
        return m.find() ? m.group(1) : null;
    }

    private static Long first(List<Long> ids) {
        return ids.isEmpty() ? null : ids.get(0);
    }
}
