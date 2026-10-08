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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글: 로그인한 회원만 쓰고, 작성자와 그 글의 블로그 주인이 지운다 (CF-18).
 * 답글은 한 단계까지이고(FR-065, BR-14), 비밀 댓글은 작성자·블로그 주인·(답글이면) 원댓글 작성자만 내용을 본다 (FR-066, BR-16).
 */
@Service
public class CommentService {

    /**
     * 댓글 한 개. 가려진(hidden) 비밀 댓글은 content가 비고, 답글·삭제·신고를 할 수 없다.
     * isBlogOwner면 화면에 "글쓴이" 표시를 붙인다. replies는 원댓글에만 있고 오래된 답글이 위다.
     * 작성자가 탈퇴했으면 authorWithdrawn이 참이고 번호·닉네임·색은 비운다(화면은 "탈퇴한 사용자", FR-086).
     */
    public record CommentView(long id, Long authorId, String authorNickname, String authorColor,
                              boolean authorWithdrawn, boolean isBlogOwner,
                              String content, boolean secret, boolean hidden, Instant createdAt, boolean deletable,
                              boolean canReply, boolean reportable, boolean reportedByMe, List<CommentView> replies) {
    }

    /** 읽을 수 있는 댓글과 그 글·블로그 */
    public record Readable(Comment comment, Post post, Blog blog) {
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

    /** 글을 볼 수 있을 때만 댓글을 보여 준다. 오래된 댓글이 위이고, 답글은 원댓글 아래에 모은다 (CF-18-3, FR-065) */
    @Transactional(readOnly = true)
    public List<CommentView> list(long postId, Long viewerId) {
        Post post = postService.getVisible(postId, viewerId);
        Blog blog = blogService.get(post.getBlogId());
        Map<String, Object> params = new HashMap<>();
        params.put("postId", postId);
        params.put("viewer", viewerId);
        List<Row> rows = jdbc.query("""
                select c.id, c.parent_id, c.author_id, m.nickname, m.profile_color, c.content, c.is_secret,
                       (c.author_id is null or m.withdrawn_at is not null) as author_withdrawn,
                       c.created_at, pc.author_id as parent_author_id,
                       exists (select 1 from report r where r.target_type = 'COMMENT' and r.comment_id = c.id
                               and r.reporter_id = cast(:viewer as bigint)) as reported
                from comment c
                left join member m on m.id = c.author_id
                left join comment pc on pc.id = c.parent_id
                where c.post_id = :postId
                order by c.created_at asc, c.id asc
                """, params, (rs, n) -> new Row(rs.getLong("id"), rs.getObject("parent_id", Long.class),
                rs.getObject("author_id", Long.class), rs.getString("nickname"), rs.getString("profile_color"),
                rs.getString("content"), rs.getBoolean("is_secret"), rs.getTimestamp("created_at").toInstant(),
                rs.getObject("parent_author_id", Long.class), rs.getBoolean("reported"),
                rs.getBoolean("author_withdrawn")));

        Map<Long, CommentView> topLevel = new LinkedHashMap<>();
        Map<Long, List<CommentView>> replies = new HashMap<>();
        for (Row row : rows) {
            if (row.parentId() == null) {
                List<CommentView> children = new ArrayList<>();
                replies.put(row.id(), children);
                topLevel.put(row.id(), view(row, blog, viewerId, children));
            }
        }
        for (Row row : rows) {
            if (row.parentId() != null && replies.containsKey(row.parentId())) {
                replies.get(row.parentId()).add(view(row, blog, viewerId, List.of()));
            }
        }
        return List.copyOf(topLevel.values());
    }

    /** 가려진 댓글에는 답글·삭제·신고를 할 수 없다. 답글에는 답글을 달 수 없다 (BR-14, BR-16) */
    private CommentView view(Row row, Blog blog, Long viewerId, List<CommentView> children) {
        boolean hidden = !canRead(row.secret(), row.authorId(), row.parentAuthorId(), blog, viewerId);
        boolean mine = viewerId != null && viewerId.equals(row.authorId());
        boolean loggedIn = viewerId != null;
        boolean withdrawn = row.authorWithdrawn();
        return new CommentView(row.id(), withdrawn ? null : row.authorId(), withdrawn ? null : row.nickname(),
                withdrawn ? null : row.color(), withdrawn, blog.isOwnedBy(row.authorId()),
                hidden ? null : row.content(), row.secret(), hidden, row.createdAt(),
                !hidden && (mine || blog.isOwnedBy(viewerId)),
                loggedIn && !hidden && row.parentId() == null,
                loggedIn && !hidden && !mine && !withdrawn,
                row.reported(), children);
    }

    /** 비밀 댓글의 내용은 작성자, 블로그 주인, (답글이면) 원댓글 작성자만 본다 (BR-16) */
    static boolean canRead(boolean secret, Long authorId, Long parentAuthorId, Blog blog, Long viewerId) {
        if (!secret) {
            return true;
        }
        if (viewerId == null) {
            return false;
        }
        return viewerId.equals(authorId) || blog.isOwnedBy(viewerId) || viewerId.equals(parentAuthorId);
    }

    /** 댓글을 읽을 수 있을 때만 돌려준다. 글을 볼 수 없거나 가려진 비밀 댓글이면 "존재하지 않는 댓글입니다" (NF-02) */
    @Transactional(readOnly = true)
    public Readable getReadable(long commentId, Long viewerId) {
        Comment comment = comments.findById(commentId).orElseThrow(CommentService::notFound);
        Post post;
        try {
            post = postService.getVisible(comment.getPostId(), viewerId);
        } catch (ApiException e) {
            throw notFound();
        }
        Blog blog = blogService.get(post.getBlogId());
        Long parentAuthorId = comment.getParentId() == null ? null
                : comments.findById(comment.getParentId()).map(Comment::getAuthorId).orElse(null);
        if (!canRead(comment.isSecret(), comment.getAuthorId(), parentAuthorId, blog, viewerId)) {
            throw notFound();
        }
        return new Readable(comment, post, blog);
    }

    /**
     * 1~500자, 공백만은 안 되고, 같은 사람은 5초 안에 다시 등록할 수 없다(답글 포함) (CF-18-2, 7).
     * parentId가 있으면 답글: 답글에는 답글을 달 수 없고, 비밀 댓글의 답글은 항상 비밀이다 (BR-14, BR-16).
     */
    @Transactional
    public long add(long postId, long memberId, String rawContent, boolean rawSecret, Long parentId) {
        Post post = postService.getVisible(postId, memberId);
        boolean secret = rawSecret;
        if (parentId != null) {
            Comment parent = getReadable(parentId, memberId).comment();
            if (!parent.getPostId().equals(post.getId())) {
                throw notFound();
            }
            if (parent.getParentId() != null) {
                throw ApiException.field("parentId", Messages.REPLY_TO_REPLY);
            }
            secret = secret || parent.isSecret();
        }
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
        return comments.save(new Comment(post.getId(), memberId, parentId, secret, content, clock.instant())).getId();
    }

    /** 남의 댓글이면 존재 자체를 숨긴다 (CF-18-4, NF-02). 답글은 표의 ON DELETE CASCADE로 함께 지워진다 (BR-15) */
    @Transactional
    public void delete(long commentId, long memberId) {
        Readable readable = getReadable(commentId, memberId);
        if (!readable.comment().isAuthoredBy(memberId) && !readable.blog().isOwnedBy(memberId)) {
            throw notFound();
        }
        comments.delete(readable.comment());
    }

    private static ApiException notFound() {
        return ApiException.notFound(Messages.COMMENT_NOT_FOUND);
    }

    private record Row(long id, Long parentId, Long authorId, String nickname, String color, String content,
                       boolean secret, Instant createdAt, Long parentAuthorId, boolean reported,
                       boolean authorWithdrawn) {
    }
}
