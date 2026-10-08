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

    @Transactional
    public void report(long postId, long memberId, String reason, String rawDetail) {
        Post post = postService.getVisible(postId, memberId);
        if (blogService.get(post.getBlogId()).isOwnedBy(memberId)) {
            throw ApiException.field("reason", Messages.REPORT_OWN_POST);
        }
        if (reason == null || !REASONS.contains(reason)) {
            throw ApiException.field("reason", Messages.REPORT_REASON);
        }
        String detail = "ETC".equals(reason) && rawDetail != null && !rawDetail.isBlank() ? rawDetail.strip() : null;
        if (detail != null && detail.codePointCount(0, detail.length()) > limits.reportDetailMax()) {
            throw ApiException.field("detail", "설명은 " + limits.reportDetailMax() + "자 이하로 입력해 주세요");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("postId", postId);
        params.put("memberId", memberId);
        params.put("reason", reason);
        params.put("detail", detail);
        params.put("now", Timestamp.from(clock.instant()));
        try {
            jdbc.update("""
                    insert into report (post_id, reporter_id, reason, detail, created_at)
                    values (:postId, :memberId, :reason, :detail, :now)
                    """, params);
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.CONFLICT, Messages.REPORT_DUPLICATE);
        }
    }
}
