package com.myblog.comment.service;

import com.myblog.comment.domain.Comment;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.Messages;
import com.myblog.post.service.ReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 신고. 사유·중복·자기 댓글 규칙은 글 신고와 같고, 신고 당시의 내용과 작성자를 함께 남긴다 (FR-071, BR-17, BR-19).
 * 볼 수 없는 댓글(가려진 비밀 댓글 포함)은 "존재하지 않는 댓글입니다" (BR-16).
 */
@Service
public class CommentReportService {

    private final CommentService commentService;
    private final ReportService reports;

    public CommentReportService(CommentService commentService, ReportService reports) {
        this.commentService = commentService;
        this.reports = reports;
    }

    @Transactional
    public void report(long commentId, long memberId, String reason, String detail) {
        Comment comment = commentService.getReadable(commentId, memberId).comment();
        if (comment.isAuthoredBy(memberId)) {
            throw ApiException.field("reason", Messages.REPORT_OWN_COMMENT);
        }
        reports.save(new ReportService.Target(ReportService.TargetType.COMMENT, comment.getPostId(), comment.getId(),
                comment.getContent(), comment.getAuthorId()), memberId, reason, detail, Messages.REPORT_COMMENT_DUPLICATE);
    }
}
