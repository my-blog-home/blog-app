package com.myblog.post.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "post")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blog_id", nullable = false)
    private Long blogId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Visibility visibility;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Post() {
    }

    public Post(long blogId, long categoryId, String title, String body, Visibility visibility, Instant now) {
        this.blogId = blogId;
        this.categoryId = categoryId;
        this.title = title;
        this.body = body;
        this.visibility = visibility;
        this.createdAt = now;
    }

    /** 내용이 바뀌었을 때만 수정 시각을 갱신한다 (CF-05-13) */
    public boolean edit(long categoryId, String title, String body, Visibility visibility, Instant now) {
        boolean changed = !Objects.equals(this.categoryId, categoryId) || !this.title.equals(title)
                || !this.body.equals(body) || this.visibility != visibility;
        if (changed) {
            this.categoryId = categoryId;
            this.title = title;
            this.body = body;
            this.visibility = visibility;
            this.updatedAt = now;
        }
        return changed;
    }

    public boolean isPublic() {
        return visibility == Visibility.PUBLIC;
    }

    public Long getId() {
        return id;
    }

    public Long getBlogId() {
        return blogId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public long getViewCount() {
        return viewCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
