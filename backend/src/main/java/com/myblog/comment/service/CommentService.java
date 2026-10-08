package com.myblog.comment.service;

import com.myblog.blog.domain.Blog;
import com.myblog.blog.service.BlogService;
import com.myblog.comment.domain.Comment;
import com.myblog.comment.domain.CommentRepository;
import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import com.myblog.post.domain.Post;
import com.myblog.post.service.PostService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글: 로그인한 회원만 쓰고, 작성자와 그 글의 블로그 주인이 지운다 (CF-18).
 */
@Service
public class CommentService {

    public record CommentView(long id, String authorNickname, String content, Instant createdAt, boolean deletable) {
    }

    private final CommentRepository comments;
    private final PostService postService;
    private final BlogService blogService;
    private final NamedParameterJdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final BlogLimits limits;
    private final Clock clock;

    public CommentService(CommentRepository comments, PostService postService, BlogService blogService,
                          NamedParameterJdbcTemplate jdbc, StringRedisTemplate redis, BlogLimits limits, Clock clock) {
        this.comments = comments;
        this.postService = postService;
        this.blogService = blogService;
        this.jdbc = jdbc;
        this.redis = redis;
        this.limits = limits;
        this.clock = clock;
    }

    /** 글을 볼 수 있을 때만 댓글을 보여 준다. 오래된 댓글이 위 (CF-18-3) */
    @Transactional(readOnly = true)
    public List<CommentView> list(long postId, Long viewerId) {
        Post post = postService.getVisible(postId, viewerId);
        boolean blogOwner = blogService.get(post.getBlogId()).isOwnedBy(viewerId);
        return jdbc.query("""
                select c.id, c.author_id, m.nickname, c.content, c.created_at
                from comment c left join member m on m.id = c.author_id
                where c.post_id = :postId
                order by c.created_at asc, c.id asc
                """, Map.of("postId", postId), (rs, row) -> {
            Long authorId = rs.getObject("author_id", Long.class);
            boolean mine = viewerId != null && viewerId.equals(authorId);
            return new CommentView(rs.getLong("id"), rs.getString("nickname"), rs.getString("content"),
                    rs.getTimestamp("created_at").toInstant(), mine || blogOwner);
        });
    }

    /** 1~500자, 공백만은 안 되고, 같은 사람은 5초 안에 다시 등록할 수 없다 (CF-18-2, 7) */
    @Transactional
    public long add(long postId, long memberId, String rawContent) {
        Post post = postService.getVisible(postId, memberId);
        String content = rawContent == null ? "" : rawContent.strip();
        if (content.isEmpty()) {
            throw ApiException.field("content", Messages.COMMENT_REQUIRED);
        }
        if (content.codePointCount(0, content.length()) > limits.commentMax()) {
            throw ApiException.field("content", "댓글은 " + limits.commentMax() + "자 이하로 입력해 주세요");
        }
        Boolean first = redis.opsForValue().setIfAbsent("comment-cooldown:" + memberId, "1", limits.commentInterval());
        if (!Boolean.TRUE.equals(first)) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, Messages.COMMENT_TOO_FAST);
        }
        return comments.save(new Comment(post.getId(), memberId, content, clock.instant())).getId();
    }

    /** 남의 댓글이면 존재 자체를 숨긴다 (CF-18-4, NF-02) */
    @Transactional
    public void delete(long commentId, long memberId) {
        Comment comment = comments.findById(commentId)
                .orElseThrow(() -> ApiException.notFound(Messages.COMMENT_NOT_FOUND));
        Post post = postService.getVisible(comment.getPostId(), memberId);
        Blog blog = blogService.get(post.getBlogId());
        boolean author = comment.getAuthorId() != null && comment.getAuthorId() == memberId;
        if (!author && !blog.isOwnedBy(memberId)) {
            throw ApiException.notFound(Messages.COMMENT_NOT_FOUND);
        }
        comments.delete(comment);
    }
}
