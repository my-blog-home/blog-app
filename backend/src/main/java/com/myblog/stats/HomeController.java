package com.myblog.stats;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 첫 화면 오른쪽의 지금 핫한 글·이번 주 인기 블로거 (FR-072, FR-074) */
@RestController
public class HomeController {

    private final HomeRankingService rankings;

    public HomeController(HomeRankingService rankings) {
        this.rankings = rankings;
    }

    @GetMapping("/api/home/hot-posts")
    public List<HomeRankingService.HotPost> hotPosts(@RequestParam(required = false) Long topicId,
                                                     @RequestParam(required = false) Integer limit) {
        return rankings.hotPosts(topicId, limit);
    }

    @GetMapping("/api/home/hot-bloggers")
    public List<HomeRankingService.HotBlogger> hotBloggers(@RequestParam(required = false) Long topicId) {
        return rankings.hotBloggers(topicId);
    }
}
