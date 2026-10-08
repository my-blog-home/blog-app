package com.myblog.notice;

import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.Messages;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지·이용 안내 읽기 (FR-077, BR-30). 누구나 읽는다. 고정 글이 위로, 그 안에서는 최신 글이 먼저.
 * 쓰고 고치는 관리자 기능은 아직 없고, 처음 안내는 마이그레이션(V10)으로 넣는다.
 * 내용은 글자만 저장하고, 화면이 빈 줄로 문단을 나눠 보여 준다(HTML로 해석하지 않음).
 */
@Service
public class NoticeService {

    public enum NoticeType { NOTICE, GUIDE }

    public record NoticeItem(long id, NoticeType type, String title, boolean pinned, Instant createdAt) {
    }

    public record NoticePage(long totalCount, int page, int totalPages, List<NoticeItem> items) {
    }

    /** others: 같은 종류의 다른 안내 (목록 순서로 최대 5개) */
    public record NoticeDetail(long id, NoticeType type, String title, String content, boolean pinned,
                               Instant createdAt, Instant updatedAt, List<NoticeItem> others) {
    }

    private static final String ORDER = " order by pinned desc, created_at desc, id desc";

    private final NamedParameterJdbcTemplate jdbc;
    private final BlogLimits limits;

    public NoticeService(NamedParameterJdbcTemplate jdbc, BlogLimits limits) {
        this.jdbc = jdbc;
        this.limits = limits;
    }

    /** 모르는 종류는 전체로 본다. size는 1~페이지 크기(첫 화면은 5) */
    @Transactional(readOnly = true)
    public NoticePage list(String rawType, int requestedPage, Integer requestedSize) {
        NoticeType type = parse(rawType);
        int size = requestedSize == null ? limits.pageSize() : Math.min(Math.max(1, requestedSize), limits.pageSize());
        Map<String, Object> params = new HashMap<>();
        params.put("type", type == null ? null : type.name());
        String where = " where (cast(:type as varchar) is null or type = :type)";
        long total = jdbc.queryForObject("select count(*) from notice" + where, params, Long.class);
        int totalPages = (int) Math.max(1, (total + size - 1) / size);
        int page = Math.min(Math.max(1, requestedPage), totalPages);
        params.put("limit", size);
        params.put("offset", (page - 1) * size);
        List<NoticeItem> items = jdbc.query("select id, type, title, pinned, created_at from notice" + where + ORDER
                + " limit :limit offset :offset", params, (rs, row) -> item(rs));
        return new NoticePage(total, page, totalPages, items);
    }

    @Transactional(readOnly = true)
    public NoticeDetail detail(long id) {
        List<NoticeDetail> found = jdbc.query("select * from notice where id = :id", Map.of("id", id),
                (rs, row) -> {
                    Timestamp updated = rs.getTimestamp("updated_at");
                    return new NoticeDetail(rs.getLong("id"), NoticeType.valueOf(rs.getString("type")),
                            rs.getString("title"), rs.getString("content"), rs.getBoolean("pinned"),
                            rs.getTimestamp("created_at").toInstant(), updated == null ? null : updated.toInstant(),
                            List.of());
                });
        if (found.isEmpty()) {
            throw ApiException.notFound(Messages.NOTICE_NOT_FOUND);
        }
        NoticeDetail notice = found.get(0);
        List<NoticeItem> others = jdbc.query("select id, type, title, pinned, created_at from notice"
                        + " where type = :type and id <> :id" + ORDER + " limit :limit",
                Map.of("type", notice.type().name(), "id", id, "limit", limits.noticeRelatedCount()),
                (rs, row) -> item(rs));
        return new NoticeDetail(notice.id(), notice.type(), notice.title(), notice.content(), notice.pinned(),
                notice.createdAt(), notice.updatedAt(), others);
    }

    private static NoticeType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return NoticeType.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static NoticeItem item(ResultSet rs) throws SQLException {
        return new NoticeItem(rs.getLong("id"), NoticeType.valueOf(rs.getString("type")), rs.getString("title"),
                rs.getBoolean("pinned"), rs.getTimestamp("created_at").toInstant());
    }
}
