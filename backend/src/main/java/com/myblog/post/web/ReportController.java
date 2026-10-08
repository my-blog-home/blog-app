package com.myblog.post.web;

import com.myblog.common.error.Messages;
import com.myblog.common.security.CurrentMember;
import com.myblog.post.service.ReportService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

    public record ReportRequest(String reason, String detail) {
    }

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @PostMapping("/api/posts/{postId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> report(@PathVariable long postId, @RequestBody ReportRequest request) {
        reports.report(postId, CurrentMember.id(), request.reason(), request.detail());
        return Map.of("message", Messages.REPORT_DONE);
    }
}
