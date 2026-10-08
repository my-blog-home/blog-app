package com.myblog.blog.web;

import com.myblog.blog.service.BlogQueryService;
import com.myblog.blog.service.BlogService;
import com.myblog.blog.service.CategoryService;
import com.myblog.blog.service.SubscriptionService;
import com.myblog.common.security.CurrentMember;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BlogController {

    public record BlogUpdateRequest(String name, String description, Long topicId) {
    }

    public record CategoryRequest(String name, String description) {
    }

    public record CategoryVisibilityRequest(String visibility) {
    }

    public record MoveRequest(String direction) {
    }

    private final BlogService blogService;
    private final BlogQueryService blogQuery;
    private final CategoryService categoryService;
    private final SubscriptionService subscriptions;

    public BlogController(BlogService blogService, BlogQueryService blogQuery, CategoryService categoryService,
                          SubscriptionService subscriptions) {
        this.blogService = blogService;
        this.blogQuery = blogQuery;
        this.categoryService = categoryService;
        this.subscriptions = subscriptions;
    }

    /** 구독 (FR-067) */
    @PutMapping("/api/blogs/{blogId}/subscription")
    public SubscriptionService.SubscriptionState subscribe(@PathVariable long blogId) {
        return subscriptions.subscribe(blogId, CurrentMember.id());
    }

    /** 구독 취소 (FR-067) */
    @DeleteMapping("/api/blogs/{blogId}/subscription")
    public SubscriptionService.SubscriptionState unsubscribe(@PathVariable long blogId) {
        return subscriptions.unsubscribe(blogId, CurrentMember.id());
    }

    /** 햄버거 메뉴의 구독한 블로그 (FR-068) */
    @GetMapping("/api/me/subscriptions")
    public SubscriptionService.MySubscriptions mySubscriptions(@RequestParam(required = false) Integer limit) {
        return subscriptions.mine(CurrentMember.id(), limit);
    }

    @GetMapping("/api/blogs/{blogId}")
    public BlogQueryService.BlogView get(@PathVariable long blogId) {
        return blogQuery.view(blogId, CurrentMember.idIfPresent().orElse(null));
    }

    @PatchMapping("/api/blogs/{blogId}")
    public BlogQueryService.BlogView update(@PathVariable long blogId, @RequestBody BlogUpdateRequest request) {
        long memberId = CurrentMember.id();
        blogService.update(blogId, memberId, request.name(), request.description(), request.topicId());
        return blogQuery.view(blogId, memberId);
    }

    @PostMapping("/api/blogs/{blogId}/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> addCategory(@PathVariable long blogId, @RequestBody CategoryRequest request) {
        return Map.of("id", categoryService.add(blogId, CurrentMember.id(), request.name(), request.description()).getId());
    }

    @PatchMapping("/api/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void editCategory(@PathVariable long categoryId, @RequestBody CategoryRequest request) {
        categoryService.edit(categoryId, CurrentMember.id(), request.name(), request.description());
    }

    @PutMapping("/api/categories/{categoryId}/visibility")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeCategoryVisibility(@PathVariable long categoryId,
                                         @RequestBody CategoryVisibilityRequest request) {
        categoryService.changeVisibility(categoryId, CurrentMember.id(), request.visibility());
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
