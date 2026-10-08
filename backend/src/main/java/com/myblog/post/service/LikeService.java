package com.myblog.post.service;

import com.myblog.blog.service.BlogService;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.Messages;
import com.myblog.post.domain.Post;
import com.myblog.post.domain.PostLike;
import com.myblog.post.domain.PostLikeRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요: 한 회원이 한 글에 한 번, 다시 누르면 취소, 자기 글은 불가 (CF-19).
 */
@Service
public class LikeService {

    public record LikeState(boolean liked, long likeCount) {
    }

    private final PostLikeRepository likes;
    private final PostService postService;
    private final BlogService blogService;
    private final Clock clock;

    public LikeService(PostLikeRepository likes, PostService postService, BlogService blogService, Clock clock) {
        this.likes = likes;
        this.postService = postService;
        this.blogService = blogService;
        this.clock = clock;
    }

    @Transactional
    public LikeState toggle(long postId, long memberId) {
        Post post = postService.getVisible(postId, memberId);
        if (blogService.get(post.getBlogId()).isOwnedBy(memberId)) {
            throw ApiException.field("like", Messages.LIKE_OWN_POST);
        }
        boolean liked;
        var existing = likes.findByPostIdAndMemberId(postId, memberId);
        if (existing.isPresent()) {
            likes.delete(existing.get());
            liked = false;
        } else {
            likes.save(new PostLike(postId, memberId, clock.instant()));
            liked = true;
        }
        likes.flush();
        return new LikeState(liked, likes.countByPostId(postId));
    }
}
