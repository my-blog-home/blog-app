package com.myblog.post.web;

import com.myblog.common.security.CurrentMember;
import com.myblog.post.domain.Post;
import com.myblog.post.domain.Visibility;
import com.myblog.post.service.PostQueryService;
import com.myblog.post.service.PostService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PostController {

    public record PostRequest(String title, String body, Long categoryId, Visibility visibility) {
        PostService.PostInput toInput() {
            return new PostService.PostInput(title, body, categoryId, visibility);
        }
    }

    public record PostSource(long id, long blogId, long categoryId, String title, String body, Visibility visibility) {
    }

    private final PostService postService;
    private final PostQueryService postQuery;

    public PostController(PostService postService, PostQueryService postQuery) {
        this.postService = postService;
        this.postQuery = postQuery;
    }

    @GetMapping("/api/posts")
    public PostQueryService.PageResult recent(@RequestParam(defaultValue = "1") int page) {
        return postQuery.recentPublic(page);
    }

    @GetMapping("/api/blogs/{blogId}/posts")
    public PostQueryService.PageResult blogPosts(@PathVariable long blogId,
                                                 @RequestParam(required = false) Long categoryId,
                                                 @RequestParam(defaultValue = "1") int page) {
        return postQuery.blogPosts(blogId, categoryId, page, CurrentMember.idIfPresent().orElse(null));
    }

    @PostMapping("/api/blogs/{blogId}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> create(@PathVariable long blogId, @RequestBody PostRequest request) {
        Post post = postService.create(blogId, CurrentMember.id(), request.toInput());
        return Map.of("id", post.getId());
    }

    @GetMapping("/api/posts/{postId}")
    public PostQueryService.PostDetail detail(@PathVariable long postId) {
        return postQuery.detail(postId, CurrentMember.idIfPresent().orElse(null));
    }

    @GetMapping("/api/posts/{postId}/edit")
    public PostSource source(@PathVariable long postId) {
        Post post = postService.getOwned(postId, CurrentMember.id());
        return new PostSource(post.getId(), post.getBlogId(), post.getCategoryId(), post.getTitle(), post.getBody(),
                post.getVisibility());
    }

    @PutMapping("/api/posts/{postId}")
    public Map<String, Long> update(@PathVariable long postId, @RequestBody PostRequest request) {
        Post post = postService.update(postId, CurrentMember.id(), request.toInput());
        return Map.of("id", post.getId());
    }

    @DeleteMapping("/api/posts/{postId}")
    public Map<String, Long> delete(@PathVariable long postId) {
        return Map.of("blogId", postService.delete(postId, CurrentMember.id()));
    }
}
