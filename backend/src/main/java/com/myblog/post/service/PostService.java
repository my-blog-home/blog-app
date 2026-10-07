package com.myblog.post.service;

import com.myblog.blog.domain.Blog;
import com.myblog.blog.domain.Category;
import com.myblog.blog.domain.CategoryRepository;
import com.myblog.blog.service.BlogService;
import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.Messages;
import com.myblog.post.domain.Post;
import com.myblog.post.domain.PostRepository;
import com.myblog.post.domain.Visibility;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 쓰기·고치기·지우기. 권한은 서버에서 확인하고, 남의 글이면 존재 자체를 숨긴다 (CF-05, CF-12, NF-02).
 */
@Service
public class PostService {

    public record PostInput(String title, String body, Long categoryId, Visibility visibility) {
    }

    private final PostRepository posts;
    private final CategoryRepository categories;
    private final BlogService blogService;
    private final BlogLimits limits;
    private final Clock clock;

    public PostService(PostRepository posts, CategoryRepository categories, BlogService blogService,
                       BlogLimits limits, Clock clock) {
        this.posts = posts;
        this.categories = categories;
        this.blogService = blogService;
        this.limits = limits;
        this.clock = clock;
    }

    @Transactional
    public Post create(long blogId, long memberId, PostInput input) {
        Blog blog = blogService.getOwned(blogId, memberId);
        Valid valid = validate(blog.getId(), input);
        return posts.save(new Post(blog.getId(), valid.categoryId(), valid.title(), valid.body(), valid.visibility(),
                clock.instant()));
    }

    @Transactional
    public Post update(long postId, long memberId, PostInput input) {
        Post post = getOwned(postId, memberId);
        Valid valid = validate(post.getBlogId(), input);
        post.edit(valid.categoryId(), valid.title(), valid.body(), valid.visibility(), clock.instant());
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

    /** 공개 글이거나 작성자 본인일 때만 볼 수 있다 (CF-09-4, CF-13-4) */
    @Transactional(readOnly = true)
    public Post getVisible(long postId, Long viewerId) {
        Post post = posts.findById(postId).orElseThrow(PostService::notFound);
        if (!post.isPublic() && !blogService.get(post.getBlogId()).isOwnedBy(viewerId)) {
            throw notFound();
        }
        return post;
    }

    private Valid validate(long blogId, PostInput input) {
        String title = input.title() == null ? "" : input.title().strip();
        if (title.isEmpty()) {
            throw ApiException.field("title", Messages.TITLE_REQUIRED);
        }
        if (title.codePointCount(0, title.length()) > limits.postTitleMax()) {
            throw ApiException.field("title", "제목은 " + limits.postTitleMax() + "자 이하로 입력해 주세요");
        }
        String body = input.body() == null ? "" : input.body();
        if (body.isBlank()) {
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
        Visibility visibility = input.visibility() == null ? Visibility.PUBLIC : input.visibility();
        return new Valid(title, body, categoryId, visibility);
    }

    private record Valid(String title, String body, long categoryId, Visibility visibility) {
    }

    private static ApiException notFound() {
        return ApiException.notFound(Messages.POST_NOT_FOUND);
    }
}
