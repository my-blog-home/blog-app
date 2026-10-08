package com.myblog.admin;

import com.myblog.notice.NoticeService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 API (/api/admin/**). 모든 요청에서 먼저 관리자인지 확인한다 (FR-078~082).
 * 비회원은 401, 관리자가 아닌 회원은 403 "관리자만 볼 수 있는 화면입니다".
 */
@RestController
public class AdminController {

    /** action: RESOLVE(처리 완료, 대상 삭제) 또는 REJECT(반려). suspend는 처리 완료일 때만 (FR-079) */
    public record ProcessRequest(List<Long> reportIds, String action, String note,
                                 AdminReportService.SuspendRequest suspend) {
    }

    /** days: 3·7·30, 비우면 영구 (FR-080) */
    public record SuspendRequest(Integer days, String reason) {
    }

    private final AdminGuard guard;
    private final AdminReportService reports;
    private final AdminMemberService members;
    private final AdminSuspensionService suspensions;
    private final AdminNoticeService notices;

    public AdminController(AdminGuard guard, AdminReportService reports, AdminMemberService members,
                           AdminSuspensionService suspensions, AdminNoticeService notices) {
        this.guard = guard;
        this.reports = reports;
        this.members = members;
        this.suspensions = suspensions;
        this.notices = notices;
    }

    @GetMapping("/api/admin/summary")
    public AdminReportService.Summary summary() {
        guard.requireAdmin();
        return reports.summary();
    }

    @GetMapping("/api/admin/reports")
    public AdminReportService.ReportPage reports(@RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") int page) {
        guard.requireAdmin();
        return reports.list(AdminReportService.parseStatus(status), page);
    }

    @PatchMapping("/api/admin/reports")
    public AdminReportService.ProcessResult process(@RequestBody ProcessRequest request) {
        long adminId = guard.requireAdmin();
        return reports.process(adminId, request.reportIds(), request.action(), request.note(), request.suspend());
    }

    @GetMapping("/api/admin/members")
    public AdminMemberService.MemberPage members(@RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "1") int page) {
        guard.requireAdmin();
        return members.list(AdminMemberService.parseStatus(status), q, page);
    }

    /** 정지 이력 (정지·해제 창에서 보여 준다) */
    @GetMapping("/api/admin/members/{memberId}/suspensions")
    public List<AdminSuspensionService.HistoryEntry> suspensionHistory(@PathVariable long memberId) {
        guard.requireAdmin();
        return suspensions.history(memberId);
    }

    @PostMapping("/api/admin/members/{memberId}/suspensions")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminSuspensionService.SuspensionView suspend(@PathVariable long memberId,
                                                         @RequestBody SuspendRequest request) {
        long adminId = guard.requireAdmin();
        return suspensions.suspend(adminId, memberId, request.days(), request.reason(), null);
    }

    @DeleteMapping("/api/admin/members/{memberId}/suspensions")
    public Map<String, Integer> lift(@PathVariable long memberId) {
        long adminId = guard.requireAdmin();
        return Map.of("liftedCount", suspensions.lift(adminId, memberId));
    }

    @PostMapping("/api/admin/notices")
    @ResponseStatus(HttpStatus.CREATED)
    public NoticeService.NoticeDetail createNotice(@RequestBody AdminNoticeService.NoticeInput request) {
        long adminId = guard.requireAdmin();
        return notices.create(adminId, request);
    }

    @PatchMapping("/api/admin/notices/{noticeId}")
    public NoticeService.NoticeDetail updateNotice(@PathVariable long noticeId,
                                                   @RequestBody AdminNoticeService.NoticeInput request) {
        guard.requireAdmin();
        return notices.update(noticeId, request);
    }

    @DeleteMapping("/api/admin/notices/{noticeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNotice(@PathVariable long noticeId) {
        guard.requireAdmin();
        notices.delete(noticeId);
    }
}
