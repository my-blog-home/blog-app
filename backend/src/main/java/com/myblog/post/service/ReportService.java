package com.myblog.post.service;

import com.myblog.blog.service.BlogService;
import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import com.myblog.post.domain.Post;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고: 로그인한 회원이 남의 글을 한 번만. 이번 범위에서는 접수만 저장한다 (CF-21, CF-23-1).
 */
@Service
public class ReportService {

    private static final Set<String> REASONS = Set.of("SPAM", "ABUSE", "ADULT", "ETC");

    private final NamedParameterJdbcTemplate jdbc;
    private final PostService postService;
    private final BlogService blogService;
    private final BlogLimits limits;
    private final Clock clock;

    public ReportService(NamedParameterJdbcTemplate jdbc, PostService postService, BlogService blogService,
                         BlogLimits limits, Clock clock) {
        this.jdbc = jdbc;
        this.postService = postService;
        this.blogService = blogService;
        this.limits = limits;
        this.clock = clock;
    }

    /** 글 신고. 신고 당시의 제목과 작성자(블로그 주인)를 함께 남긴다 (FR-071, BR-19) */
    @Transactional
    public void report(long postId, long memberId, String reason, String rawDetail) {
        Post post = postService.getVisible(postId, memberId);
        long ownerId = blogService.get(post.getBlogId()).getOwnerId();
        if (ownerId == memberId) {
            throw ApiException.field("reason", Messages.REPORT_OWN_POST);
        }
        save(new Target(TargetType.POST, post.getId(), null, post.getTitle(), ownerId), memberId, reason, rawDetail,
                Messages.REPORT_DUPLICATE);
    }

    /** 신고 대상: 글 또는 댓글. 댓글 신고는 그 댓글이 달린 글 번호도 함께 둔다 */
    public enum TargetType {
        POST, COMMENT
    }

    public record Target(TargetType type, Long postId, Long commentId, String text, Long authorId) {
    }

    /**
     * 사유·설명을 확인하고 신고를 저장한다. 글·댓글 신고가 같은 규칙을 쓴다 (FR-071, BR-17).
     * 자기 글·댓글인지와 대상을 볼 수 있는지는 부르는 쪽에서 먼저 확인한다.
     */
    @Transactional
    public void save(Target target, long memberId, String reason, String rawDetail, String duplicateMessage) {
        if (reason == null || !REASONS.contains(reason)) {
            throw ApiException.field("reason", Messages.REPORT_REASON);
        }
        String detail = "ETC".equals(reason) && rawDetail != null && !rawDetail.isBlank() ? rawDetail.strip() : null;
        if (detail != null && detail.codePointCount(0, detail.length()) > limits.reportDetailMax()) {
            throw ApiException.field("detail", "설명은 " + limits.reportDetailMax() + "자 이하로 입력해 주세요");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("type", target.type().name());
        params.put("postId", target.postId());
        params.put("commentId", target.commentId());
        params.put("text", target.text());
        params.put("authorId", target.authorId());
        params.put("memberId", memberId);
        params.put("reason", reason);
        params.put("detail", detail);
        params.put("now", Timestamp.from(clock.instant()));
        try {
            jdbc.update("""
                    insert into report (target_type, post_id, comment_id, target_text, target_author_id,
                                        reporter_id, reason, detail, created_at)
                    values (:type, :postId, :commentId, :text, :authorId, :memberId, :reason, :detail, :now)
                    """, params);
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.CONFLICT, duplicateMessage);
        }
    }
}
