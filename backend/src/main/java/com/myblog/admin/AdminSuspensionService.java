package com.myblog.admin;

import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import com.myblog.common.security.SessionTerminator;
import com.myblog.common.sql.SuspensionSql;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 정지·해제 (FR-080, FR-081, BR-49).
 * 정지는 기록으로 남고, 지금 정지 중인지는 시각으로 계산하므로 기간이 끝나면 저절로 풀린다.
 * 겹치면 가장 오래 가는 것(영구가 먼저)이 효력을 갖고, 해제하면 효력 있는 정지를 모두 푼다(기록은 남음).
 * 관리자와 탈퇴한 회원은 정지할 수 없다. 정지하면 그 회원의 로그인 세션을 모두 지운다.
 */
@Service
public class AdminSuspensionService {

    /** days가 비어 있으면 영구 정지 */
    public record SuspensionView(long id, long memberId, String reason, Instant startsAt, Instant endsAt,
                                 boolean permanent, Integer days) {
    }

    /** 정지 기록 한 줄 (회원 관리의 정지 이력). active는 지금 효력이 있는지 */
    public record HistoryEntry(long id, String reason, Instant startsAt, Instant endsAt, boolean permanent,
                               boolean active, Instant liftedAt, String createdBy, String liftedBy, Long reportId) {
    }

    /** 확인을 마친 정지 입력. days가 비어 있으면 영구 */
    public record Input(Integer days, String reason) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final SessionTerminator sessions;
    private final BlogLimits limits;
    private final Clock clock;

    public AdminSuspensionService(NamedParameterJdbcTemplate jdbc, SessionTerminator sessions, BlogLimits limits,
                                  Clock clock) {
        this.jdbc = jdbc;
        this.sessions = sessions;
        this.limits = limits;
        this.clock = clock;
    }

    /** 기간은 정해진 일수(3·7·30) 또는 영구(빈 값, 0), 사유는 0~200자 */
    public Input validate(Integer rawDays, String rawReason) {
        Integer days = rawDays == null || rawDays == 0 ? null : rawDays;
        if (days != null && !limits.suspensionDays().contains(days)) {
            throw ApiException.field("days", Messages.SUSPEND_DAYS);
        }
        String reason = rawReason == null ? "" : rawReason.strip();
        if (reason.codePointCount(0, reason.length()) > limits.suspensionReasonMax()) {
            throw ApiException.field("reason", Messages.SUSPEND_REASON_TOO_LONG.formatted(limits.suspensionReasonMax()));
        }
        return new Input(days, reason);
    }

    /** 없는 회원은 404, 관리자·탈퇴한 회원은 409 "정지할 수 없는 회원입니다" */
    public void requireSuspendable(Long memberId) {
        if (memberId == null) {
            throw new ApiException(ErrorCode.CANNOT_SUSPEND, Messages.CANNOT_SUSPEND);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select role, withdrawn_at from member where id = :id", Map.of("id", memberId));
        if (rows.isEmpty()) {
            throw ApiException.notFound(Messages.MEMBER_NOT_FOUND);
        }
        Map<String, Object> row = rows.get(0);
        if ("ADMIN".equals(row.get("role")) || row.get("withdrawn_at") != null) {
            throw new ApiException(ErrorCode.CANNOT_SUSPEND, Messages.CANNOT_SUSPEND);
        }
    }

    @Transactional
    public SuspensionView suspend(long adminId, long memberId, Integer rawDays, String rawReason, Long reportId) {
        Input input = validate(rawDays, rawReason);
        requireSuspendable(memberId);
        Instant now = clock.instant();
        Instant ends = input.days() == null ? null : now.plus(Duration.ofDays(input.days()));
        Map<String, Object> params = new HashMap<>();
        params.put("memberId", memberId);
        params.put("reason", input.reason());
        params.put("startsAt", Timestamp.from(now));
        params.put("endsAt", ends == null ? null : Timestamp.from(ends));
        params.put("reportId", reportId);
        params.put("admin", adminId);
        Long id = jdbc.queryForObject("""
                insert into suspension (member_id, reason, starts_at, ends_at, report_id, created_by)
                values (:memberId, :reason, :startsAt, cast(:endsAt as timestamptz), :reportId, :admin)
                returning id
                """, params, Long.class);
        sessions.terminateAll(memberId);
        return new SuspensionView(id, memberId, input.reason(), now, ends, ends == null, input.days());
    }

    /** 회원의 정지 이력. 최근 정지가 위 (FR-080) */
    @Transactional(readOnly = true)
    public List<HistoryEntry> history(long memberId) {
        Long exists = jdbc.queryForObject("select count(*) from member where id = :id", Map.of("id", memberId), Long.class);
        if (exists == null || exists == 0) {
            throw ApiException.notFound(Messages.MEMBER_NOT_FOUND);
        }
        return jdbc.query("""
                select s.id, s.reason, s.starts_at, s.ends_at, s.lifted_at, s.report_id, %s as active,
                       cb.nickname as created_by, lb.nickname as lifted_by
                from suspension s
                left join member cb on cb.id = s.created_by
                left join member lb on lb.id = s.lifted_by
                where s.member_id = :id
                order by s.starts_at desc, s.id desc
                """.formatted(SuspensionSql.active("s", "now")),
                Map.of("id", memberId, "now", Timestamp.from(clock.instant())), (rs, n) -> {
                    Timestamp ends = rs.getTimestamp("ends_at");
                    Timestamp lifted = rs.getTimestamp("lifted_at");
                    return new HistoryEntry(rs.getLong("id"), rs.getString("reason"),
                            rs.getTimestamp("starts_at").toInstant(), ends == null ? null : ends.toInstant(),
                            ends == null, rs.getBoolean("active"), lifted == null ? null : lifted.toInstant(),
                            rs.getString("created_by"), rs.getString("lifted_by"),
                            rs.getObject("report_id", Long.class));
                });
    }

    /** 효력 있는 정지를 모두 푼다. 정지 중이 아니면 409 */
    @Transactional
    public int lift(long adminId, long memberId) {
        Long exists = jdbc.queryForObject("select count(*) from member where id = :id", Map.of("id", memberId), Long.class);
        if (exists == null || exists == 0) {
            throw ApiException.notFound(Messages.MEMBER_NOT_FOUND);
        }
        Timestamp now = Timestamp.from(clock.instant());
        int lifted = jdbc.update("update suspension s set lifted_at = :now, lifted_by = :admin where s.member_id = :id and "
                + SuspensionSql.active("s", "now"), Map.of("now", now, "admin", adminId, "id", memberId));
        if (lifted == 0) {
            throw new ApiException(ErrorCode.NOT_SUSPENDED, Messages.NOT_SUSPENDED);
        }
        return lifted;
    }
}
