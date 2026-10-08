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

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Comment() {
    }

    public Comment(long postId, long authorId, String content, Instant now) {
        this.postId = postId;
        this.authorId = authorId;
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
}
