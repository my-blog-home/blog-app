package com.myblog.search;

import com.myblog.post.service.PostQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/search")
    public PostQueryService.PageResult search(@RequestParam(defaultValue = "") String q,
                                              @RequestParam(defaultValue = "1") int page) {
        return searchService.search(q, page);
    }
}
