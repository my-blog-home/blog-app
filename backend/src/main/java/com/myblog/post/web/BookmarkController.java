package com.myblog.post.web;

import com.myblog.common.security.CurrentMember;
import com.myblog.post.service.BookmarkService;
import com.myblog.post.service.PostQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookmarkController {

    private final BookmarkService bookmarks;
    private final PostQueryService postQuery;

    public BookmarkController(BookmarkService bookmarks, PostQueryService postQuery) {
        this.bookmarks = bookmarks;
        this.postQuery = postQuery;
    }

    @PutMapping("/api/posts/{postId}/bookmark")
    public BookmarkService.BookmarkState toggle(@PathVariable long postId) {
        return bookmarks.toggle(postId, CurrentMember.id());
    }

    /** 내 북마크: 최근에 저장한 순서 */
    @GetMapping("/api/me/bookmarks")
    public PostQueryService.PageResult myBookmarks(@RequestParam(defaultValue = "1") int page) {
        return postQuery.bookmarkedPosts(CurrentMember.id(), page);
    }
}
