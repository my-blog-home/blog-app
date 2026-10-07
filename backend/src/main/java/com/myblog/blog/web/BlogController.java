package com.myblog.blog.web;

import com.myblog.blog.service.BlogQueryService;
import com.myblog.blog.service.BlogService;
import com.myblog.blog.service.CategoryService;
import com.myblog.common.security.CurrentMember;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BlogController {

    public record BlogUpdateRequest(String name, String description) {
    }

    public record CategoryRequest(String name) {
    }

    public record MoveRequest(String direction) {
    }

    private final BlogService blogService;
    private final BlogQueryService blogQuery;
    private final CategoryService categoryService;

    public BlogController(BlogService blogService, BlogQueryService blogQuery, CategoryService categoryService) {
        this.blogService = blogService;
        this.blogQuery = blogQuery;
        this.categoryService = categoryService;
    }

    @GetMapping("/api/blogs/{blogId}")
    public BlogQueryService.BlogView get(@PathVariable long blogId) {
        return blogQuery.view(blogId, CurrentMember.idIfPresent().orElse(null));
    }

    @PatchMapping("/api/blogs/{blogId}")
    public BlogQueryService.BlogView update(@PathVariable long blogId, @RequestBody BlogUpdateRequest request) {
        long memberId = CurrentMember.id();
        blogService.update(blogId, memberId, request.name(), request.description());
        return blogQuery.view(blogId, memberId);
    }

    @PostMapping("/api/blogs/{blogId}/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> addCategory(@PathVariable long blogId, @RequestBody CategoryRequest request) {
        return Map.of("id", categoryService.add(blogId, CurrentMember.id(), request.name()).getId());
    }

    @PatchMapping("/api/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void renameCategory(@PathVariable long categoryId, @RequestBody CategoryRequest request) {
        categoryService.rename(categoryId, CurrentMember.id(), request.name());
    }

    @PostMapping("/api/categories/{categoryId}/move")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void moveCategory(@PathVariable long categoryId, @RequestBody MoveRequest request) {
        categoryService.move(categoryId, CurrentMember.id(), "UP".equalsIgnoreCase(request.direction()));
    }

    @DeleteMapping("/api/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable long categoryId) {
        categoryService.delete(categoryId, CurrentMember.id());
    }
}
