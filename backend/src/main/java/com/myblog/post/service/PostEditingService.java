package com.myblog.post.service;

import com.myblog.post.domain.Post;
import com.myblog.post.image.ImageService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 저장을 태그·이미지 연결과 한 묶음으로 처리한다 (CF-05, CF-20, CF-22).
 */
@Service
public class PostEditingService {

    private final PostService posts;
    private final TagService tags;
    private final ImageService images;

    public PostEditingService(PostService posts, TagService tags, ImageService images) {
        this.posts = posts;
        this.tags = tags;
        this.images = images;
    }

    @Transactional
    public Post create(long blogId, long memberId, PostService.PostInput input, List<String> rawTags) {
        List<String> tagNames = tags.normalize(rawTags);
        Post post = posts.create(blogId, memberId, input);
        tags.replace(post.getId(), tagNames);
        images.linkToPost(post.getId(), memberId, post.getBody());
        return post;
    }

    @Transactional
    public Post update(long postId, long memberId, PostService.PostInput input, List<String> rawTags) {
        List<String> tagNames = tags.normalize(rawTags);
        Post post = posts.update(postId, memberId, input);
        tags.replace(post.getId(), tagNames);
        images.linkToPost(post.getId(), memberId, post.getBody());
        return post;
    }

    /** 글과 함께 댓글·좋아요·태그 연결·이미지·신고를 지운다 (CF-05-15, CF-22-5) */
    @Transactional
    public long delete(long postId, long memberId) {
        posts.getOwned(postId, memberId);
        images.deleteFilesAfterCommit(postId);
        return posts.delete(postId, memberId);
    }
}
