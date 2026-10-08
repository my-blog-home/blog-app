package com.myblog.admin;

import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import com.myblog.common.sql.SuspensionSql;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 신고 처리 (FR-079, BR-47, BR-48).
 * 같은 대상(글·댓글)의 신고를 한 줄로 모아 보여 준다. 묶는 기준은 같은 상태 + 같은 대상이고,
 * 대상이 지워졌으면 "대상 종류 + 신고 당시 내용 + 작성자"가 같은 것끼리, 처리한 신고는 같은 처리 시각끼리 묶는다.
 * 처리는 줄의 신고 전부를 한 트랜잭션에서 같은 결과로 바꾸고, 하나라도 이미 처리됐으면 아무것도 바꾸지 않는다.
 * 처리 완료는 신고된 글·댓글을 반드시 지우고(이미 지워졌으면 기록만), 반려는 그대로 둔다.
 */
@Service
public class AdminReportService {

    public enum Status { PENDING, RESOLVED, REJECTED, ALL }

    public enum Action { RESOLVE, REJECT }

    private static final Map<String, String> REASON_LABELS = new LinkedHashMap<>();

    static {
        REASON_LABELS.put("SPAM", "스팸");
        REASON_LABELS.put("ABUSE", "욕설·혐오");
        REASON_LABELS.put("ADULT", "음란물");
        REASON_LABELS.put("ETC", "기타");
    }

    /** 신고된 대상의 작성자. 탈퇴했으면 닉네임 자리에 "탈퇴한 사용자" */
    public record Author(Long id, String nickname, boolean withdrawn, boolean admin, boolean suspended) {
    }

    public record ReportEntry(long id, String reporterNickname, String reason, String reasonLabel, String detail,
                              Instant createdAt) {
    }

    /**
     * 신고 한 줄(묶음). exists가 거짓이면 대상이 지워진 것이고 postId·commentId는 비어 있다.
     * targetText는 신고 당시의 글 제목 또는 댓글 내용. postTitle은 댓글이 달린 글의 지금 제목(있을 때).
     */
    public record ReportGroup(String key, String targetType, boolean exists, Long postId, Long commentId,
                              String postTitle, String targetText, Author targetAuthor, String status,
                              int reportCount, String reasonSummary, Map<String, Integer> reasons,
                              String reporterSummary, List<ReportEntry> reports, List<Long> reportIds,
                              Instant latestAt, String handledBy, Instant handledAt, String handleNote,
                              boolean canSuspend) {
    }

    /** counts는 탭에 쓰는 신고 건수(줄 수가 아님). totalCount·totalPages는 줄 기준 */
    public record ReportPage(Map<String, Long> counts, long totalCount, int page, int totalPages,
                             List<ReportGroup> items) {
    }

    public record Summary(Map<String, Long> counts, long noticeCount, List<ReportGroup> latestPending) {
    }

    public record SuspendRequest(Integer days, String reason) {
    }

    public record ProcessResult(int handledCount, int deletedCount, Long suspendedMemberId, String message) {
    }

    /** 묶음 기준. 별칭이 r인 report 표에 쓴다 */
    private static final String GROUP_KEY = """
            ((case when r.target_type = 'POST' and r.post_id is not null then 'P' || r.post_id
                   when r.target_type = 'COMMENT' and r.comment_id is not null then 'C' || r.comment_id
                   else 'D' || r.target_type || ':' || coalesce(cast(r.target_author_id as varchar), '-')
                        || ':' || md5(r.target_text) end)
             || '|' || r.status || '|' || coalesce(cast(extract(epoch from r.handled_at) as varchar), ''))""";

    private static final String FILTER = " (cast(:status as varchar) = 'ALL' or r.status = :status) ";

    private final NamedParameterJdbcTemplate jdbc;
    private final AdminSuspensionService suspensions;
    private final BlogLimits limits;
    private final Clock clock;

    public AdminReportService(NamedParameterJdbcTemplate jdbc, AdminSuspensionService suspensions, BlogLimits limits,
                              Clock clock) {
        this.jdbc = jdbc;
        this.suspensions = suspensions;
        this.limits = limits;
        this.clock = clock;
    }

    public static Status parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return Status.PENDING;
        }
        try {
            return Status.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return Status.PENDING;
        }
    }

    @Transactional(readOnly = true)
    public ReportPage list(Status status, int requestedPage) {
        return list(status, requestedPage, limits.pageSize());
    }

    private ReportPage list(Status status, int requestedPage, int size) {
        Map<String, Object> params = new HashMap<>();
        params.put("status", status.name());
        long total = jdbc.queryForObject("select count(distinct " + GROUP_KEY + ") from report r where" + FILTER,
                params, Long.class);
        int totalPages = (int) Math.max(1, (total + size - 1) / size);
        int page = Math.min(Math.max(1, requestedPage), totalPages);
        params.put("limit", size);
        params.put("offset", (page - 1) * size);
        List<String> keys = jdbc.queryForList("""
                select g.gkey from (select %s as gkey, r.created_at from report r where %s) g
                group by g.gkey order by max(g.created_at) desc, g.gkey limit :limit offset :offset
                """.formatted(GROUP_KEY, FILTER), params, String.class);
        return new ReportPage(counts(), total, page, totalPages, groups(keys, status));
    }

    /** 상태별 신고 건수 (탭의 숫자) */
    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Status s : Status.values()) {
            counts.put(s.name(), 0L);
        }
        jdbc.query("select status, count(*) as cnt from report group by status", Map.of(), rs -> {
            counts.put(rs.getString("status"), rs.getLong("cnt"));
        });
        counts.put(Status.ALL.name(), counts.get("PENDING") + counts.get("RESOLVED") + counts.get("REJECTED"));
        return counts;
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        Long notices = jdbc.queryForObject("select count(*) from notice", Map.of(), Long.class);
        ReportPage pending = list(Status.PENDING, 1, limits.adminSummaryPendingCount());
        return new Summary(pending.counts(), notices == null ? 0 : notices, pending.items());
    }

    private List<ReportGroup> groups(List<String> keys, Status status) {
        if (keys.isEmpty()) {
            return List.of();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("status", status.name());
        params.put("keys", keys);
        params.put("now", Timestamp.from(clock.instant()));
        List<Row> rows = jdbc.query("""
                select r.id, r.target_type, r.post_id, r.comment_id, r.target_text, r.target_author_id, r.reason,
                       r.detail, r.created_at, r.status, r.handled_at, r.handle_note, %s as gkey,
                       rep.nickname as reporter_nickname, rep.withdrawn_at is not null as reporter_withdrawn,
                       ta.nickname as author_nickname, ta.withdrawn_at is not null as author_withdrawn,
                       ta.role as author_role, %s as author_suspended,
                       hb.nickname as handler_nickname, p.title as post_title
                from report r
                left join member rep on rep.id = r.reporter_id
                left join member ta on ta.id = r.target_author_id
                left join member hb on hb.id = r.handled_by
                left join post p on p.id = r.post_id
                where %s and %s in (:keys)
                order by r.created_at asc, r.id asc
                """.formatted(GROUP_KEY, SuspensionSql.memberSuspended("ta.id", "now"), FILTER, GROUP_KEY),
                params, (rs, n) -> {
                    Timestamp handled = rs.getTimestamp("handled_at");
                    return new Row(rs.getLong("id"), rs.getString("gkey"), rs.getString("target_type"),
                            rs.getObject("post_id", Long.class), rs.getObject("comment_id", Long.class),
                            rs.getString("target_text"), rs.getObject("target_author_id", Long.class),
                            rs.getString("reason"), rs.getString("detail"), rs.getTimestamp("created_at").toInstant(),
                            rs.getString("status"), handled == null ? null : handled.toInstant(),
                            rs.getString("handle_note"),
                            rs.getBoolean("reporter_withdrawn") ? Messages.WITHDRAWN_USER : rs.getString("reporter_nickname"),
                            rs.getString("author_nickname"), rs.getBoolean("author_withdrawn"),
                            "ADMIN".equals(rs.getString("author_role")), rs.getBoolean("author_suspended"),
                            rs.getString("handler_nickname"), rs.getString("post_title"));
                });
        Map<String, List<Row>> byKey = rows.stream()
                .collect(Collectors.groupingBy(Row::key, LinkedHashMap::new, Collectors.toList()));
        List<ReportGroup> result = new ArrayList<>();
        for (String key : keys) {
            List<Row> group = byKey.get(key);
            if (group != null) {
                result.add(group(key, group));
            }
        }
        return result;
    }

    private ReportGroup group(String key, List<Row> rows) {
        Row first = rows.get(rows.size() - 1); // 가장 최근 신고
        boolean isPost = "POST".equals(first.targetType());
        boolean exists = isPost ? first.postId() != null : first.commentId() != null;
        Long authorId = first.targetAuthorId();
        Author author = new Author(authorId == null || first.authorWithdrawn() ? null : authorId,
                authorId == null || first.authorWithdrawn() ? Messages.WITHDRAWN_USER : first.authorNickname(),
                authorId == null || first.authorWithdrawn(), first.authorAdmin(), first.authorSuspended());

        Map<String, Integer> reasons = new LinkedHashMap<>();
        for (Row row : rows) {
            reasons.merge(row.reason(), 1, Integer::sum);
        }
        List<String> order = new ArrayList<>(REASON_LABELS.keySet());
        String reasonSummary = reasons.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparingInt(e -> order.indexOf(e.getKey())))
                .map(e -> REASON_LABELS.getOrDefault(e.getKey(), e.getKey()) + " " + e.getValue())
                .collect(Collectors.joining(" · "));
        List<String> names = rows.stream().map(Row::reporterNickname).map(n -> n == null ? Messages.WITHDRAWN_USER : n)
                .toList();
        String reporterSummary = String.join(", ", names.subList(0, Math.min(2, names.size())))
                + (names.size() > 2 ? " 외 " + (names.size() - 2) + "명" : "");

        List<ReportEntry> entries = rows.stream()
                .map(r -> new ReportEntry(r.id(), r.reporterNickname(), r.reason(),
                        REASON_LABELS.getOrDefault(r.reason(), r.reason()), r.detail(), r.createdAt()))
                .toList();
        boolean pending = "PENDING".equals(first.status());
        boolean canSuspend = pending && !author.withdrawn() && !author.admin();
        return new ReportGroup(key, first.targetType(), exists, first.postId(), isPost ? null : first.commentId(),
                isPost ? null : first.postTitle(), first.targetText(), author, first.status(), rows.size(),
                reasonSummary, reasons, reporterSummary, entries, rows.stream().map(Row::id).toList(),
                first.createdAt(), first.handlerNickname(), first.handledAt(), first.handleNote(), canSuspend);
    }

    /**
     * 신고 여러 건을 한 번에 처리한다 (FR-079, BR-48). 하나라도 이미 처리됐으면 409이고 아무것도 바뀌지 않는다.
     * 작성자 정지는 처리 완료와 함께만 할 수 있고, 작성자가 관리자·탈퇴한 회원이면 409 (BR-49).
     * 신고 처리·대상 삭제·정지는 한 트랜잭션이라 하나라도 실패하면 모두 취소된다.
     */
    @Transactional
    public ProcessResult process(long adminId, List<Long> rawIds, String rawAction, String rawNote,
                                 SuspendRequest suspend) {
        if (rawIds == null || rawIds.isEmpty() || rawIds.stream().anyMatch(Objects::isNull)) {
            throw ApiException.field("reportIds", Messages.REPORT_IDS_REQUIRED);
        }
        List<Long> ids = List.copyOf(new LinkedHashSet<>(rawIds));
        Action action;
        try {
            action = Action.valueOf(rawAction == null ? "" : rawAction.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.field("action", Messages.REPORT_ACTION);
        }
        String note = rawNote == null || rawNote.isBlank() ? null : rawNote.strip();
        if (note != null && note.codePointCount(0, note.length()) > limits.handleNoteMax()) {
            throw ApiException.field("note", Messages.HANDLE_NOTE_TOO_LONG.formatted(limits.handleNoteMax()));
        }
        AdminSuspensionService.Input suspendInput = null;
        if (suspend != null) {
            if (action != Action.RESOLVE) {
                throw ApiException.field("suspend", Messages.SUSPEND_ONLY_WITH_RESOLVE);
            }
            suspendInput = suspensions.validate(suspend.days(), suspend.reason());
        }

        List<Target> targets = jdbc.query("""
                select id, status, target_type, post_id, comment_id, target_author_id
                from report where id in (:ids) order by id for update
                """, Map.of("ids", ids), (rs, n) -> new Target(rs.getLong("id"), rs.getString("status"),
                rs.getString("target_type"), rs.getObject("post_id", Long.class),
                rs.getObject("comment_id", Long.class), rs.getObject("target_author_id", Long.class)));
        if (targets.size() != ids.size()) {
            throw ApiException.notFound(Messages.REPORT_NOT_FOUND);
        }
        if (targets.stream().anyMatch(t -> !"PENDING".equals(t.status()))) {
            throw new ApiException(ErrorCode.ALREADY_HANDLED, Messages.REPORT_ALREADY_HANDLED);
        }
        Long suspendTarget = null;
        if (suspendInput != null) {
            Set<Long> authors = targets.stream().map(Target::authorId).filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (authors.size() > 1) {
                throw ApiException.field("suspend", Messages.SUSPEND_ONE_AUTHOR);
            }
            suspendTarget = authors.isEmpty() ? null : authors.iterator().next();
            suspensions.requireSuspendable(suspendTarget);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("ids", ids);
        params.put("status", action == Action.RESOLVE ? "RESOLVED" : "REJECTED");
        params.put("admin", adminId);
        params.put("now", Timestamp.from(clock.instant()));
        params.put("note", note);
        int handled = jdbc.update("""
                update report set status = :status, handled_by = :admin, handled_at = :now, handle_note = :note
                where id in (:ids) and status = 'PENDING'
                """, params);
        if (handled != ids.size()) {
            throw new ApiException(ErrorCode.ALREADY_HANDLED, Messages.REPORT_ALREADY_HANDLED);
        }

        int deletedPosts = 0;
        int deletedComments = 0;
        if (action == Action.RESOLVE) {
            List<Long> commentIds = targets.stream().filter(t -> "COMMENT".equals(t.type()) && t.commentId() != null)
                    .map(Target::commentId).distinct().toList();
            List<Long> postIds = targets.stream().filter(t -> "POST".equals(t.type()) && t.postId() != null)
                    .map(Target::postId).distinct().toList();
            // 답글·좋아요·태그 등은 표의 ON DELETE 규칙으로 함께 지워지고, 신고의 글·댓글 번호는 비워진다
            if (!commentIds.isEmpty()) {
                deletedComments = jdbc.update("delete from comment where id in (:ids)", Map.of("ids", commentIds));
            }
            if (!postIds.isEmpty()) {
                deletedPosts = jdbc.update("delete from post where id in (:ids)", Map.of("ids", postIds));
            }
        }

        Long suspended = null;
        String suffix = "";
        if (suspendInput != null) {
            suspensions.suspend(adminId, suspendTarget, suspendInput.days(), suspendInput.reason(), ids.get(0));
            suspended = suspendTarget;
            suffix = suspendInput.days() == null ? ". 작성자를 영구 정지했습니다"
                    : ". 작성자를 " + suspendInput.days() + "일 정지했습니다";
        }
        return new ProcessResult(handled, deletedPosts + deletedComments, suspended,
                message(action, handled, deletedPosts, deletedComments) + suffix);
    }

    private static String message(Action action, int handled, int posts, int comments) {
        if (action == Action.REJECT) {
            return "신고 " + handled + "건을 반려했습니다";
        }
        if (posts == 0 && comments == 0) {
            return "신고 " + handled + "건을 처리했습니다. 대상은 이미 삭제되었습니다";
        }
        String what = posts > 0 && comments > 0 ? "글과 댓글을" : posts > 0 ? "글을" : "댓글을";
        return "신고 " + handled + "건을 처리하고 " + what + " 삭제했습니다";
    }

    private record Target(long id, String status, String type, Long postId, Long commentId, Long authorId) {
    }

    private record Row(long id, String key, String targetType, Long postId, Long commentId, String targetText,
                       Long targetAuthorId, String reason, String detail, Instant createdAt, String status,
                       Instant handledAt, String handleNote, String reporterNickname, String authorNickname,
                       boolean authorWithdrawn, boolean authorAdmin, boolean authorSuspended,
                       String handlerNickname, String postTitle) {
    }
}
