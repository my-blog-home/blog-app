package com.myblog.post.web;

import com.myblog.common.security.CurrentMember;
import com.myblog.post.domain.Visibility;
import com.myblog.post.service.ManagePostQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ManagePostController {

    private final ManagePostQueryService service;

    public ManagePostController(ManagePostQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/manage/blogs/{blogId}/posts")
    public ManagePostQueryService.Page posts(@PathVariable long blogId,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) Visibility visibility,
                                             @RequestParam(required = false) Long categoryId,
                                             @RequestParam(defaultValue = "1") int page) {
        return service.list(blogId, CurrentMember.id(),
                ManagePostQueryService.StatusFilter.from(status, visibility), categoryId, page);
    }
}
