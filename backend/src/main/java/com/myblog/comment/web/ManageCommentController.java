package com.myblog.comment.web;

import com.myblog.comment.service.ManageCommentService;
import com.myblog.common.security.CurrentMember;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ManageCommentController {

    private final ManageCommentService service;

    public ManageCommentController(ManageCommentService service) {
        this.service = service;
    }

    @GetMapping("/api/manage/blogs/{blogId}/comments")
    public ManageCommentService.Page comments(@PathVariable long blogId, @RequestParam(defaultValue = "1") int page) {
        return service.list(blogId, CurrentMember.id(), page);
    }

    @GetMapping("/api/manage/new-comments")
    public ManageCommentService.NewCount newComments() {
        return service.newCount(CurrentMember.id());
    }
}
