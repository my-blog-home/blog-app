package com.myblog.admin;

import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.Messages;
import com.myblog.notice.NoticeService;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 공지·이용 안내 쓰기·고치기·지우기 (FR-082, P:FR-36).
 * 종류(NOTICE/GUIDE), 제목 1~100자, 내용 1~5,000자, 상단 고정. 내용은 글자만 저장하고 화면이 HTML로 해석하지 않는다.
 */
@Service
public class AdminNoticeService {

    /** 고치기에서 비어 있는 칸은 그대로 둔다 */
    public record NoticeInput(String type, String title, String content, Boolean pinned) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final NoticeService notices;
    private final BlogLimits limits;
    private final Clock clock;

    public AdminNoticeService(NamedParameterJdbcTemplate jdbc, NoticeService notices, BlogLimits limits, Clock clock) {
        this.jdbc = jdbc;
        this.notices = notices;
        this.limits = limits;
        this.clock = clock;
    }

    @Transactional
    public NoticeService.NoticeDetail create(long adminId, NoticeInput input) {
        Valid valid = validate(input.type(), input.title(), input.content());
        Map<String, Object> params = params(valid, Boolean.TRUE.equals(input.pinned()));
        params.put("admin", adminId);
        Long id = jdbc.queryForObject("""
                insert into notice (created_by, type, title, content, pinned, created_at)
                values (:admin, :type, :title, :content, :pinned, :now) returning id
                """, params, Long.class);
        return notices.detail(id);
    }

    @Transactional
    public NoticeService.NoticeDetail update(long noticeId, NoticeInput input) {
        NoticeService.NoticeDetail current = notices.detail(noticeId);
        Valid valid = validate(input.type() == null ? current.type().name() : input.type(),
                input.title() == null ? current.title() : input.title(),
                input.content() == null ? current.content() : input.content());
        Map<String, Object> params = params(valid, input.pinned() == null ? current.pinned() : input.pinned());
        params.put("id", noticeId);
        jdbc.update("""
                update notice set type = :type, title = :title, content = :content, pinned = :pinned, updated_at = :now
                where id = :id
                """, params);
        return notices.detail(noticeId);
    }

    @Transactional
    public void delete(long noticeId) {
        if (jdbc.update("delete from notice where id = :id", Map.of("id", noticeId)) == 0) {
            throw ApiException.notFound(Messages.NOTICE_NOT_FOUND);
        }
    }

    private Map<String, Object> params(Valid valid, boolean pinned) {
        Map<String, Object> params = new HashMap<>();
        params.put("type", valid.type());
        params.put("title", valid.title());
        params.put("content", valid.content());
        params.put("pinned", pinned);
        params.put("now", Timestamp.from(clock.instant()));
        return params;
    }

    private Valid validate(String rawType, String rawTitle, String rawContent) {
        String type = rawType == null ? "" : rawType.strip().toUpperCase(Locale.ROOT);
        if (!List.of("NOTICE", "GUIDE").contains(type)) {
            throw ApiException.field("type", Messages.NOTICE_TYPE);
        }
        String title = rawTitle == null ? "" : rawTitle.strip();
        int titleLength = title.codePointCount(0, title.length());
        if (titleLength < 1 || titleLength > limits.noticeTitleMax()) {
            throw ApiException.field("title", Messages.NOTICE_TITLE_RULE.formatted(limits.noticeTitleMax()));
        }
        String content = rawContent == null ? "" : rawContent.strip();
        int contentLength = content.codePointCount(0, content.length());
        if (contentLength < 1 || contentLength > limits.noticeContentMax()) {
            throw ApiException.field("content", Messages.NOTICE_CONTENT_RULE.formatted(limits.noticeContentMax()));
        }
        return new Valid(type, title, content);
    }

    private record Valid(String type, String title, String content) {
    }
}
