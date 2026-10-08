package com.myblog.comment.web;

import com.myblog.comment.service.CommentService;
import com.myblog.common.security.CurrentMember;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommentController {

    public record CommentRequest(String content) {
    }

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/api/posts/{postId}/comments")
    public List<CommentService.CommentView> list(@PathVariable long postId) {
        return commentService.list(postId, CurrentMember.idIfPresent().orElse(null));
    }

    @PostMapping("/api/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> add(@PathVariable long postId, @RequestBody CommentRequest request) {
        return Map.of("id", commentService.add(postId, CurrentMember.id(), request.content()));
    }

    @DeleteMapping("/api/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long commentId) {
        commentService.delete(commentId, CurrentMember.id());
    }
}
