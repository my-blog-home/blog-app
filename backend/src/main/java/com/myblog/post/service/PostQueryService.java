package com.myblog.post.service;

import com.myblog.blog.domain.Blog;
import com.myblog.blog.domain.Category;
import com.myblog.blog.domain.CategoryRepository;
import com.myblog.blog.service.BlogService;
import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.Messages;
import com.myblog.common.sql.PostVisibilitySql;
import com.myblog.post.domain.Post;
import com.myblog.post.domain.PostStatus;
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
 * 글 목록·상세·이전/다음 글. 비공개 글·비공개 분류의 글은 블로그 주인 본인에게만 나오고,
 * 임시저장 글은 어느 목록에도 나오지 않는다 (CF-09, CF-10, CF-13, BR-01, BR-46).
 * 화면의 작성 시각(createdAt)은 처음 작성완료한 시각(published_at)이다.
 */
@Service
public class PostQueryService {

    public record PostItem(long id, String title, String excerpt, long blogId, String blogName,
                           long categoryId, String categoryName, String categoryVisibility, long topicId,
                           String topicName, Instant createdAt, Visibility visibility, String thumbnailUrl,
                           String authorColor, long likeCount, long commentCount, long authorId,
                           String authorNickname) {
    }

    /** 메인 글 정렬: 최신순(작성일), 인기순(조회수 + 좋아요 × 10, 같으면 최근 글 먼저). 모르는 값은 최신순 (BR-07) */
    public enum Sort {
        LATEST("p.published_at desc, p.id desc"),
        POPULAR("(p.view_count + 10 * (select count(*) from post_like pl where pl.post_id = p.id)) desc,"
                + " p.published_at desc, p.id desc");

        private final String orderBy;

        Sort(String orderBy) {
            this.orderBy = orderBy;
        }

        public static Sort from(String raw) {
            return "popular".equalsIgnoreCase(raw == null ? "" : raw.strip()) ? POPULAR : LATEST;
        }
    }

    /** 본문의 첫 이미지를 목록 썸네일로 쓴다 */
    private static final java.util.regex.Pattern FIRST_IMAGE =
            java.util.regex.Pattern.compile("!\\[[^\\]]*]\\((/images/[0-9a-f\\-]{36}\\.(?:jpg|png|gif|webp))\\)");

    public record PageResult(long totalCount, int page, int totalPages, List<PostItem> items) {
    }

    public record Ref(long id, String name) {
    }

    public record PostDetail(long id, Ref blog, Ref category, Ref topic, String title, String body,
                             Visibility visibility, PostStatus status, Instant createdAt, Instant updatedAt,
                             Long prevPostId, Long nextPostId, boolean editable, String authorColor,
                             long likeCount, boolean likedByMe, long commentCount, List<String> tags,
                             long viewCount, long authorId, String authorNickname) {
    }

    private static final String SELECT_ITEMS = """
            select p.id, p.title, p.body, p.blog_id, b.name as blog_name, p.category_id, c.name as category_name,
                   c.visibility as category_visibility, p.topic_id, t.name as topic_name,
                   p.published_at, p.visibility, m.profile_color, m.id as author_id, m.nickname as author_nickname,
                   (select count(*) from post_like pl where pl.post_id = p.id) as like_count,
                   (select count(*) from comment cm where cm.post_id = p.id) as comment_count
            from post p
            join blog b on b.id = p.blog_id
            join category c on c.id = p.category_id
            join topic t on t.id = p.topic_id
            join member m on m.id = b.owner_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PostService postService;
    private final BlogService blogService;
    private final CategoryRepository categories;
    private final TagService tagService;
    private final BlogLimits limits;
    private final ApplicationEventPublisher events;

    public PostQueryService(NamedParameterJdbcTemplate jdbc, PostService postService, BlogService blogService,
                            CategoryRepository categories, TagService tagService, BlogLimits limits,
                            ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.postService = postService;
        this.blogService = blogService;
        this.categories = categories;
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
                where %s and exists (
                    select 1 from post_tag pt join tag tg on tg.id = pt.tag_id
                    where pt.post_id = p.id and lower(tg.name) = lower(:tag))
                """.formatted(PostVisibilitySql.PUBLIC), params, page);
    }

    /**
     * 한 블로그의 글 목록. 주인이면 비공개 글·비공개 분류의 글도 포함하고, 임시저장 글은 주인에게도 뺀다 (CF-10-6, 7, BR-01).
     * 없는 분류나 방문자에게 숨긴 비공개 분류를 고르면 "존재하지 않는 분류입니다" (BR-46)
     */
    @Transactional(readOnly = true)
    public PageResult blogPosts(long blogId, Long categoryId, int page, Long viewerId) {
        return blogPosts(blogId, categoryId, null, page, viewerId);
    }

    /**
     * q가 있으면 블로그 안 검색: 그 블로그에서 볼 수 있는 글의 제목·본문·태그에서 모든 단어가 들어 있는 글 (FR-070, BR-10).
     * 검색어 규칙은 전체 검색과 같다
     */
    @Transactional(readOnly = true)
    public PageResult blogPosts(long blogId, Long categoryId, String q, int page, Long viewerId) {
        Blog blog = blogService.get(blogId);
        boolean owner = blog.isOwnedBy(viewerId);
        if (categoryId != null) {
            Category category = categories.findById(categoryId).orElse(null);
            if (category == null || !category.getBlogId().equals(blogId) || (!owner && !category.isPublic())) {
                throw ApiException.notFound(Messages.CATEGORY_NOT_FOUND);
            }
        }
        Map<String, Object> params = new HashMap<>();
        params.put("blogId", blogId);
        params.put("includePrivate", owner);
        params.put("categoryId", categoryId);
        String where = """
                where p.blog_id = :blogId
                  and %s
                  and (cast(:categoryId as bigint) is null or p.category_id = :categoryId)
                """.formatted(PostVisibilitySql.visibleTo("p", "includePrivate"));
        if (q != null) {
            where += SearchWords.conditions(SearchWords.check(q, limits), "p", true, params);
        }
        return page(where, params, page);
    }

    /**
     * 작성자 프로필의 글 목록: 그 회원 블로그의 글. 남이 보면 공개 조건, 내가 보면 비공개 글까지(임시저장은 빼고) (FR-075, BR-28)
     */
    @Transactional(readOnly = true)
    public PageResult authorPosts(long memberId, int page, Long viewerId) {
        Map<String, Object> params = new HashMap<>();
        params.put("memberId", memberId);
        params.put("includePrivate", viewerId != null && viewerId == memberId);
        String where = """
                where p.blog_id in (select ab.id from blog ab where ab.owner_id = :memberId)
                  and %s
                """.formatted(PostVisibilitySql.visibleTo("p", "includePrivate"));
        return page(where, params, page);
    }

    /** 첫 화면: 모든 블로그의 공개 글. 주제(글의 주제)로 거르고 최신순·인기순으로 정렬한다 (FR-34, BR-03, BR-07) */
    @Transactional(readOnly = true)
    public PageResult recentPublic(int page, Long topicId, Sort sort) {
        Map<String, Object> params = new HashMap<>();
        params.put("topicId", topicId);
        return page("where " + PostVisibilitySql.PUBLIC
                + " and (cast(:topicId as bigint) is null or p.topic_id = :topicId)\n", params, page, sort);
    }

    /** 구독 피드: 내가 구독한 블로그의 공개 글만, 첫 화면과 같은 정렬 (FR-067, BR-12) */
    @Transactional(readOnly = true)
    public PageResult feed(long memberId, int page, Sort sort) {
        Map<String, Object> params = new HashMap<>();
        params.put("me", memberId);
        return page("where " + PostVisibilitySql.PUBLIC
                + " and p.blog_id in (select s.blog_id from subscription s where s.member_id = :me)\n", params, page, sort);
    }

    /** 내 활동의 좋아요한 글: 최근에 누른 순서. 지금 읽을 수 없는 글은 빠진다 (FR-069, BR-32) */
    @Transactional(readOnly = true)
    public PageResult likedPosts(long memberId, int page) {
        Map<String, Object> params = new HashMap<>();
        params.put("me", memberId);
        String where = """
                where exists (select 1 from post_like pl where pl.post_id = p.id and pl.member_id = :me)
                  and %s
                """.formatted(PostVisibilitySql.readableBy("p",
                "(select ob.owner_id from blog ob where ob.id = p.blog_id)", "me"));
        return page(where, params, page,
                "(select pl.created_at from post_like pl where pl.post_id = p.id and pl.member_id = :me) desc, p.id desc");
    }

    /** 검색 모듈이 만든 조건으로 공개 글을 읽는다 (search → post 방향) */
    @Transactional(readOnly = true)
    public PageResult searchPage(String where, Map<String, Object> params, int page) {
        return page(where, params, page);
    }

    PageResult page(String where, Map<String, Object> params, int requestedPage) {
        return page(where, params, requestedPage, Sort.LATEST);
    }

    /** where 조건으로 세고, 범위를 넘는 페이지는 마지막 페이지로 바꿔 읽는다 (CF-10-1~4) */
    PageResult page(String where, Map<String, Object> params, int requestedPage, Sort sort) {
        return page(where, params, requestedPage, sort.orderBy);
    }

    private PageResult page(String where, Map<String, Object> params, int requestedPage, String orderBy) {
        long total = jdbc.queryForObject("select count(*) from post p " + where, params, Long.class);
        int size = limits.pageSize();
        int totalPages = (int) Math.max(1, (total + size - 1) / size);
        int page = Math.min(Math.max(1, requestedPage), totalPages);
        params.put("limit", size);
        params.put("offset", (page - 1) * size);
        List<PostItem> items = jdbc.query(SELECT_ITEMS + where
                + "order by " + orderBy + " limit :limit offset :offset", params, itemMapper());
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
        String topicName = jdbc.queryForObject("select name from topic where id = :id",
                Map.of("id", post.getTopicId()), String.class);
        Map<String, Object> author = jdbc.queryForMap("select nickname, profile_color from member where id = :id",
                Map.of("id", blog.getOwnerId()));
        String authorColor = (String) author.get("profile_color");
        Long prev = null;
        Long next = null;
        if (post.isPublished()) {
            Map<String, Object> params = Map.of("blogId", post.getBlogId(), "publishedAt",
                    java.sql.Timestamp.from(post.getPublishedAt()), "id", post.getId());
            // 이전 글 = 바로 앞에 쓴 공개 글, 다음 글 = 바로 뒤에 쓴 공개 글 (CF-09-2)
            Long before = first(jdbc.queryForList("""
                    select p.id from post p
                    where p.blog_id = :blogId and %s and (p.published_at, p.id) < (:publishedAt, :id)
                    order by p.published_at desc, p.id desc limit 1
                    """.formatted(PostVisibilitySql.PUBLIC), params, Long.class));
            Long after = first(jdbc.queryForList("""
                    select p.id from post p
                    where p.blog_id = :blogId and %s and (p.published_at, p.id) > (:publishedAt, :id)
                    order by p.published_at asc, p.id asc limit 1
                    """.formatted(PostVisibilitySql.PUBLIC), params, Long.class));
            prev = before;
            next = after;
        }
        return new PostDetail(post.getId(), new Ref(blog.getId(), blog.getName()),
                new Ref(post.getCategoryId(), categoryName), new Ref(post.getTopicId(), topicName),
                post.getTitle(), post.getBody(), post.getVisibility(), post.getStatus(),
                post.getPublishedAt(), post.getUpdatedAt(), prev, next, blog.isOwnedBy(viewerId), authorColor,
                count("select count(*) from post_like where post_id = :id", post.getId(), null),
                viewerId != null && count("select count(*) from post_like where post_id = :id and member_id = :viewer",
                        post.getId(), viewerId) > 0,
                count("select count(*) from comment where post_id = :id", post.getId(), null),
                tagService.tagsOf(post.getId()), post.getViewCount(), blog.getOwnerId(),
                (String) author.get("nickname"));
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
                rs.getLong("category_id"), rs.getString("category_name"), rs.getString("category_visibility"),
                rs.getLong("topic_id"), rs.getString("topic_name"),
                rs.getTimestamp("published_at").toInstant(), Visibility.valueOf(rs.getString("visibility")),
                thumbnail(rs.getString("body")), rs.getString("profile_color"),
                rs.getLong("like_count"), rs.getLong("comment_count"), rs.getLong("author_id"),
                rs.getString("author_nickname"));
    }

    private static String thumbnail(String body) {
        java.util.regex.Matcher m = FIRST_IMAGE.matcher(body);
        return m.find() ? m.group(1) : null;
    }

    private static Long first(List<Long> ids) {
        return ids.isEmpty() ? null : ids.get(0);
    }
}
