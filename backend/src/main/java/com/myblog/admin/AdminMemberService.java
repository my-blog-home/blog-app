package com.myblog.admin;

import com.myblog.common.config.BlogLimits;
import com.myblog.common.sql.SuspensionSql;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 회원 관리 (FR-080, BR-49). 상태 탭(전체·활동 중·정지·탈퇴), 닉네임·이메일 검색, 최근 가입 순.
 * 탈퇴한 회원은 닉네임·이메일을 내려 주지 않는다(개인정보). 관리자는 "전체"에만 나온다.
 */
@Service
public class AdminMemberService {

    public enum Status { ALL, ACTIVE, SUSPENDED, WITHDRAWN }

    public record BlogRef(long id, String name) {
    }

    /** 지금 효력 있는 정지 (가장 오래 가는 것). endsAt이 비면 영구 */
    public record CurrentSuspension(Instant startsAt, Instant endsAt, String reason) {
    }

    public record MemberRow(long id, String nickname, String email, Instant joinedAt, Instant withdrawnAt,
                            String role, String status, BlogRef blog, long postCount, long reportCount,
                            long resolvedReportCount, long suspensionCount, CurrentSuspension suspension,
                            boolean canSuspend) {
    }

    public record MemberPage(Map<String, Long> counts, long totalCount, int page, int totalPages,
                             List<MemberRow> items) {
    }

    private static final String SUSPENDED = SuspensionSql.memberSuspended("m.id", "now");

    private final NamedParameterJdbcTemplate jdbc;
    private final BlogLimits limits;
    private final Clock clock;

    public AdminMemberService(NamedParameterJdbcTemplate jdbc, BlogLimits limits, Clock clock) {
        this.jdbc = jdbc;
        this.limits = limits;
        this.clock = clock;
    }

    public static Status parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return Status.ALL;
        }
        try {
            return Status.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return Status.ALL;
        }
    }

    private static String condition(Status status) {
        return switch (status) {
            case ALL -> "true";
            case ACTIVE -> "(m.withdrawn_at is null and m.role = 'MEMBER' and not " + SUSPENDED + ")";
            case SUSPENDED -> "(m.withdrawn_at is null and " + SUSPENDED + ")";
            case WITHDRAWN -> "(m.withdrawn_at is not null)";
        };
    }

    @Transactional(readOnly = true)
    public MemberPage list(Status status, String rawQuery, int requestedPage) {
        Map<String, Object> params = new HashMap<>();
        params.put("now", Timestamp.from(clock.instant()));
        String search = "true";
        if (rawQuery != null && !rawQuery.isBlank()) {
            String escaped = rawQuery.strip().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            params.put("q", "%" + escaped + "%");
            search = "(m.withdrawn_at is null and (lower(m.nickname) like :q escape '\\' or lower(m.email) like :q escape '\\'))";
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Status s : Status.values()) {
            counts.put(s.name(), jdbc.queryForObject("select count(*) from member m where " + condition(s) + " and "
                    + search, params, Long.class));
        }
        String where = " where " + condition(status) + " and " + search;
        long total = counts.get(status.name());
        int size = limits.pageSize();
        int totalPages = (int) Math.max(1, (total + size - 1) / size);
        int page = Math.min(Math.max(1, requestedPage), totalPages);
        params.put("limit", size);
        params.put("offset", (page - 1) * size);
        List<MemberRow> items = jdbc.query("""
                select m.id, m.nickname, m.email, m.role, m.created_at, m.withdrawn_at,
                       b.id as blog_id, b.name as blog_name,
                       (select count(*) from post p where p.blog_id = b.id and p.status = 'PUBLISHED') as post_count,
                       (select count(*) from report r where r.target_author_id = m.id) as report_count,
                       (select count(*) from report r where r.target_author_id = m.id and r.status = 'RESOLVED')
                           as resolved_count,
                       (select count(*) from suspension s where s.member_id = m.id) as suspension_count,
                       cs.starts_at, cs.ends_at, cs.reason, cs.member_id as suspended_id
                from member m
                left join lateral (select id, name from blog where owner_id = m.id order by id limit 1) b on true
                left join lateral (select s.member_id, s.starts_at, s.ends_at, s.reason from suspension s
                                   where s.member_id = m.id and %s
                                   order by s.%s limit 1) cs on true
                %s
                order by m.created_at desc, m.id desc
                limit :limit offset :offset
                """.formatted(SuspensionSql.active("s", "now"), SuspensionSql.LONGEST_FIRST, where), params,
                (rs, n) -> {
                    boolean withdrawn = rs.getTimestamp("withdrawn_at") != null;
                    boolean suspended = rs.getObject("suspended_id") != null;
                    boolean admin = "ADMIN".equals(rs.getString("role"));
                    Timestamp ends = rs.getTimestamp("ends_at");
                    Long blogId = rs.getObject("blog_id", Long.class);
                    return new MemberRow(rs.getLong("id"),
                            withdrawn ? null : rs.getString("nickname"), withdrawn ? null : rs.getString("email"),
                            rs.getTimestamp("created_at").toInstant(),
                            withdrawn ? rs.getTimestamp("withdrawn_at").toInstant() : null,
                            rs.getString("role"),
                            withdrawn ? Status.WITHDRAWN.name() : suspended ? Status.SUSPENDED.name() : Status.ACTIVE.name(),
                            blogId == null ? null : new BlogRef(blogId, rs.getString("blog_name")),
                            rs.getLong("post_count"), rs.getLong("report_count"), rs.getLong("resolved_count"),
                            rs.getLong("suspension_count"),
                            suspended ? new CurrentSuspension(rs.getTimestamp("starts_at").toInstant(),
                                    ends == null ? null : ends.toInstant(), rs.getString("reason")) : null,
                            !withdrawn && !admin);
                });
        return new MemberPage(counts, total, page, totalPages, items);
    }
}
