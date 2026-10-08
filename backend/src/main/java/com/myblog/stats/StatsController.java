package com.myblog.stats;

import com.myblog.common.security.CurrentMember;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatsController {

    private final StatsQueryService stats;

    public StatsController(StatsQueryService stats) {
        this.stats = stats;
    }

    @GetMapping("/api/manage/blogs/{blogId}/dashboard")
    public StatsQueryService.Dashboard dashboard(@PathVariable long blogId) {
        return stats.dashboard(blogId, CurrentMember.id());
    }

    @GetMapping("/api/manage/blogs/{blogId}/stats")
    public List<StatsQueryService.Daily> daily(@PathVariable long blogId, @RequestParam(defaultValue = "30") int days) {
        return stats.stats(blogId, CurrentMember.id(), days);
    }
}
