package com.myblog.comment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "comment")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    /** 작성자가 탈퇴하면 비어 있다 ("탈퇴한 사용자", CF-18-6) */
    @Column(name = "author_id")
    private Long authorId;

    /** 답글이면 원댓글 id. 답글은 한 단계까지다 (FR-065, BR-14) */
    @Column(name = "parent_id")
    private Long parentId;

    /** 비밀 댓글 (FR-066, BR-16) */
    @Column(name = "is_secret", nullable = false)
    private boolean secret;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Comment() {
    }

    public Comment(long postId, long authorId, Long parentId, boolean secret, String content, Instant now) {
        this.postId = postId;
        this.authorId = authorId;
        this.parentId = parentId;
        this.secret = secret;
        this.content = content;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public Long getParentId() {
        return parentId;
    }

    public boolean isSecret() {
        return secret;
    }

    public String getContent() {
        return content;
    }

    public boolean isAuthoredBy(Long memberId) {
        return memberId != null && memberId.equals(authorId);
    }
}
