package com.myblog.comment.web;

import com.myblog.comment.service.CommentActivityService;
import com.myblog.comment.service.CommentReportService;
import com.myblog.comment.service.CommentService;
import com.myblog.common.error.Messages;
import com.myblog.common.security.CurrentMember;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommentController {

    /** secret: 비밀 댓글(기본 꺼짐), parentId: 답글이면 원댓글 id (FR-065, FR-066) */
    public record CommentRequest(String content, Boolean secret, Long parentId) {
    }

    public record ReportRequest(String reason, String detail) {
    }

    private final CommentService commentService;
    private final CommentReportService reportService;
    private final CommentActivityService activityService;

    public CommentController(CommentService commentService, CommentReportService reportService,
                             CommentActivityService activityService) {
        this.commentService = commentService;
        this.reportService = reportService;
        this.activityService = activityService;
    }

    @GetMapping("/api/posts/{postId}/comments")
    public List<CommentService.CommentView> list(@PathVariable long postId) {
        return commentService.list(postId, CurrentMember.idIfPresent().orElse(null));
    }

    @PostMapping("/api/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> add(@PathVariable long postId, @RequestBody CommentRequest request) {
        return Map.of("id", commentService.add(postId, CurrentMember.id(), request.content(),
                Boolean.TRUE.equals(request.secret()), request.parentId()));
    }

    @DeleteMapping("/api/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long commentId) {
        commentService.delete(commentId, CurrentMember.id());
    }

    /** 댓글 신고 (FR-071) */
    @PostMapping("/api/comments/{commentId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> report(@PathVariable long commentId, @RequestBody ReportRequest request) {
        reportService.report(commentId, CurrentMember.id(), request.reason(), request.detail());
        return Map.of("message", Messages.REPORT_DONE);
    }

    /** 내 활동: 댓글 단 글 (FR-069) */
    @GetMapping("/api/me/commented-posts")
    public CommentActivityService.Page commentedPosts(@RequestParam(defaultValue = "1") int page) {
        return activityService.commentedPosts(CurrentMember.id(), page);
    }
}
