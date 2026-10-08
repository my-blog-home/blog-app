package com.myblog.post.service;

import com.myblog.blog.domain.Blog;
import com.myblog.blog.domain.Category;
import com.myblog.blog.domain.CategoryRepository;
import com.myblog.blog.service.BlogService;
import com.myblog.blog.service.TopicService;
import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.Messages;
import com.myblog.common.sql.PostVisibilitySql;
import com.myblog.post.domain.Post;
import com.myblog.post.domain.PostRepository;
import com.myblog.post.domain.Visibility;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 쓰기·고치기·지우기. 권한은 서버에서 확인하고, 남의 글이면 존재 자체를 숨긴다 (CF-05, CF-12, NF-02).
 */
@Service
public class PostService {

    /** draft가 참이면 임시저장, 아니면 작성완료 (FR-11, BR-04) */
    public record PostInput(String title, String body, Long categoryId, Long topicId, Visibility visibility,
                            boolean draft) {
    }

    private final PostRepository posts;
    private final CategoryRepository categories;
    private final BlogService blogService;
    private final TopicService topics;
    private final NamedParameterJdbcTemplate jdbc;
    private final BlogLimits limits;
    private final Clock clock;

    public PostService(PostRepository posts, CategoryRepository categories, BlogService blogService,
                       TopicService topics, NamedParameterJdbcTemplate jdbc, BlogLimits limits, Clock clock) {
        this.posts = posts;
        this.categories = categories;
        this.blogService = blogService;
        this.topics = topics;
        this.jdbc = jdbc;
        this.limits = limits;
        this.clock = clock;
    }

    /** 주제를 고르지 않으면 마지막에 쓴 글의 주제, 쓴 글이 없으면 블로그의 대표 주제 (FR-09) */
    @Transactional
    public Post create(long blogId, long memberId, PostInput input) {
        Blog blog = blogService.getOwned(blogId, memberId);
        long defaultTopic = posts.findFirstByBlogIdOrderByCreatedAtDescIdDesc(blog.getId())
                .map(Post::getTopicId).orElse(blog.getTopicId());
        Valid valid = validate(blog.getId(), input, !input.draft(), defaultTopic);
        return posts.save(new Post(blog.getId(), valid.categoryId(), valid.topicId(), valid.title(), valid.body(),
                valid.visibility(), input.draft(), clock.instant()));
    }

    /**
     * 임시저장 글을 작성완료하면 그때가 작성 시각이 된다. 이미 작성완료한 글에서 임시저장을 눌러도
     * 작성완료 상태는 그대로이고 고친 내용만 저장한다 (BR-04). 주제를 보내지 않으면 지금 주제를 둔다.
     */
    @Transactional
    public Post update(long postId, long memberId, PostInput input) {
        Post post = getOwned(postId, memberId);
        boolean publishing = post.isPublished() || !input.draft();
        Valid valid = validate(post.getBlogId(), input, publishing, post.getTopicId());
        Instant now = clock.instant();
        post.edit(valid.categoryId(), valid.topicId(), valid.title(), valid.body(), valid.visibility(), now);
        if (publishing) {
            post.publish(now);
        }
        return post;
    }

    /** 댓글·좋아요·태그·이미지는 표의 ON DELETE CASCADE로 함께 지워진다 (CF-05-15) */
    @Transactional
    public long delete(long postId, long memberId) {
        Post post = getOwned(postId, memberId);
        posts.delete(post);
        return post.getBlogId();
    }

    /** 작성자 본인의 글만 돌려준다. 아니면 "존재하지 않는 글입니다" (CF-05-12) */
    @Transactional(readOnly = true)
    public Post getOwned(long postId, long memberId) {
        Post post = posts.findById(postId).orElseThrow(PostService::notFound);
        Blog blog = blogService.get(post.getBlogId());
        if (!blog.isOwnedBy(memberId)) {
            throw notFound();
        }
        return post;
    }

    /**
     * 방문자에게 보이는 글(작성완료·글 공개·분류 공개)이거나 작성자 본인일 때만 볼 수 있다 (CF-09-4, CF-13-4, BR-02, BR-46).
     * 조건은 PostVisibilitySql 한곳의 것을 쓴다. 댓글·좋아요·신고·이미지도 이 확인을 거친다.
     */
    @Transactional(readOnly = true)
    public Post getVisible(long postId, Long viewerId) {
        Post post = posts.findById(postId).orElseThrow(PostService::notFound);
        if (blogService.get(post.getBlogId()).isOwnedBy(viewerId)) {
            return post;
        }
        boolean visible = jdbc.queryForObject("select count(*) from post p where p.id = :id and "
                + PostVisibilitySql.PUBLIC, Map.of("id", postId), Long.class) > 0;
        if (!visible) {
            throw notFound();
        }
        return post;
    }

    /** 작성완료는 제목·본문이 필수, 임시저장은 비워도 되고 빈 제목은 "제목 없음"으로 저장한다 (FR-09, FR-11) */
    private Valid validate(long blogId, PostInput input, boolean publishing, long defaultTopic) {
        String title = input.title() == null ? "" : input.title().strip();
        if (title.isEmpty()) {
            if (publishing) {
                throw ApiException.field("title", Messages.TITLE_REQUIRED);
            }
            title = Messages.DRAFT_TITLE;
        }
        if (title.codePointCount(0, title.length()) > limits.postTitleMax()) {
            throw ApiException.field("title", "제목은 " + limits.postTitleMax() + "자 이하로 입력해 주세요");
        }
        String body = input.body() == null ? "" : input.body();
        if (publishing && body.isBlank()) {
            throw ApiException.field("body", Messages.BODY_REQUIRED);
        }
        if (body.codePointCount(0, body.length()) > limits.postBodyMax()) {
            throw ApiException.field("body", "본문은 " + limits.postBodyMax() + "자 이하로 입력해 주세요");
        }
        Long categoryId = input.categoryId();
        if (categoryId == null) {
            categoryId = categories.findFirstByBlogIdAndIsDefaultTrue(blogId).map(Category::getId).orElseThrow();
        } else {
            Category category = categories.findById(categoryId).orElse(null);
            if (category == null || !category.getBlogId().equals(blogId)) {
                throw ApiException.field("categoryId", "분류를 다시 골라 주세요");
            }
        }
        long topicId = input.topicId() == null ? defaultTopic : topics.check(input.topicId());
        Visibility visibility = input.visibility() == null ? Visibility.PUBLIC : input.visibility();
        return new Valid(title, body, categoryId, topicId, visibility);
    }

    private record Valid(String title, String body, long categoryId, long topicId, Visibility visibility) {
    }

    private static ApiException notFound() {
        return ApiException.notFound(Messages.POST_NOT_FOUND);
    }
}
