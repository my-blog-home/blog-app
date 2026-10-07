package com.myblog.common.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LimitsController {

    private final BlogLimits limits;

    public LimitsController(BlogLimits limits) {
        this.limits = limits;
    }

    @GetMapping("/api/config/limits")
    public BlogLimits limits() {
        return limits;
    }
}
