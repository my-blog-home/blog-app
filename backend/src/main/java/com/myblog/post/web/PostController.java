package com.myblog.post.web;

import com.myblog.common.security.CurrentMember;
import com.myblog.post.domain.Post;
import com.myblog.post.domain.Visibility;
import com.myblog.post.service.PostEditingService;
import com.myblog.post.service.PostQueryService;
import com.myblog.post.service.PostService;
import com.myblog.post.service.TagService;
import java.util.List;
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

    public record PostRequest(String title, String body, Long categoryId, Visibility visibility, List<String> tags) {
        PostService.PostInput toInput() {
            return new PostService.PostInput(title, body, categoryId, visibility);
        }
    }

    public record PostSource(long id, long blogId, long categoryId, String title, String body, Visibility visibility,
                             List<String> tags) {
    }

    private final PostService postService;
    private final PostEditingService editing;
    private final PostQueryService postQuery;
    private final TagService tagService;

    public PostController(PostService postService, PostEditingService editing, PostQueryService postQuery,
                          TagService tagService) {
        this.postService = postService;
        this.editing = editing;
        this.postQuery = postQuery;
        this.tagService = tagService;
    }

    @GetMapping("/api/tags/{name}/posts")
    public PostQueryService.PageResult tagPosts(@PathVariable String name, @RequestParam(defaultValue = "1") int page) {
        return postQuery.tagPosts(name, page);
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
        Post post = editing.create(blogId, CurrentMember.id(), request.toInput(), request.tags());
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
                post.getVisibility(), tagService.tagsOf(post.getId()));
    }

    @PutMapping("/api/posts/{postId}")
    public Map<String, Long> update(@PathVariable long postId, @RequestBody PostRequest request) {
        Post post = editing.update(postId, CurrentMember.id(), request.toInput(), request.tags());
        return Map.of("id", post.getId());
    }

    @DeleteMapping("/api/posts/{postId}")
    public Map<String, Long> delete(@PathVariable long postId) {
        return Map.of("blogId", editing.delete(postId, CurrentMember.id()));
    }
}
