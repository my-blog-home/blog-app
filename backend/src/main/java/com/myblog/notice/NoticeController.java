package com.myblog.notice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공지·이용 안내 (FR-077). 누구나 읽는다 */
@RestController
public class NoticeController {

    private final NoticeService notices;

    public NoticeController(NoticeService notices) {
        this.notices = notices;
    }

    @GetMapping("/api/notices")
    public NoticeService.NoticePage list(@RequestParam(required = false) String type,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(required = false) Integer size) {
        return notices.list(type, page, size);
    }

    @GetMapping("/api/notices/{noticeId}")
    public NoticeService.NoticeDetail detail(@PathVariable long noticeId) {
        return notices.detail(noticeId);
    }
}
