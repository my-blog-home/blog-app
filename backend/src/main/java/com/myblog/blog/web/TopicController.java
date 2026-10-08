package com.myblog.blog.web;

import com.myblog.blog.service.TopicService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TopicController {

    private final TopicService topics;

    public TopicController(TopicService topics) {
        this.topics = topics;
    }

    @GetMapping("/api/topics")
    public List<TopicService.TopicView> list() {
        return topics.list();
    }
}
