package com.myblog.post.web;

import com.myblog.common.security.CurrentMember;
import com.myblog.post.service.AuthorProfileService;
import com.myblog.post.service.PostQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 작성자 프로필과 그 회원의 글 목록 (FR-075, BR-28) */
@RestController
public class AuthorController {

    private final AuthorProfileService profiles;
    private final PostQueryService postQuery;

    public AuthorController(AuthorProfileService profiles, PostQueryService postQuery) {
        this.profiles = profiles;
        this.postQuery = postQuery;
    }

    @GetMapping("/api/users/{userId}")
    public AuthorProfileService.Profile profile(@PathVariable long userId) {
        return profiles.profile(userId, CurrentMember.idIfPresent().orElse(null));
    }

    @GetMapping("/api/users/{userId}/posts")
    public PostQueryService.PageResult posts(@PathVariable long userId, @RequestParam(defaultValue = "1") int page) {
        profiles.requireMember(userId);
        return postQuery.authorPosts(userId, page, CurrentMember.idIfPresent().orElse(null));
    }
}
