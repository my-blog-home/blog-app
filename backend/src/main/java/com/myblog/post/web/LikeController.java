package com.myblog.post.web;

import com.myblog.common.security.CurrentMember;
import com.myblog.post.service.LikeService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @PutMapping("/api/posts/{postId}/like")
    public LikeService.LikeState toggle(@PathVariable long postId) {
        return likeService.toggle(postId, CurrentMember.id());
    }
}
