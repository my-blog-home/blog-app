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

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PostStatus status;

    /** 처음 작성완료한 시각. 화면의 "작성 시각"은 이 값이다 (임시저장 글은 비어 있음) */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Post() {
    }

    public Post(long blogId, long categoryId, long topicId, String title, String body, Visibility visibility,
                boolean draft, Instant now) {
        this.blogId = blogId;
        this.categoryId = categoryId;
        this.topicId = topicId;
        this.title = title;
        this.body = body;
        this.visibility = visibility;
        this.status = draft ? PostStatus.DRAFT : PostStatus.PUBLISHED;
        this.publishedAt = draft ? null : now;
        this.createdAt = now;
    }

    /** 내용이 바뀌었을 때만 수정 시각을 갱신한다 (CF-05-13) */
    public boolean edit(long categoryId, long topicId, String title, String body, Visibility visibility, Instant now) {
        boolean changed = !Objects.equals(this.categoryId, categoryId) || !Objects.equals(this.topicId, topicId)
                || !this.title.equals(title) || !this.body.equals(body) || this.visibility != visibility;
        if (changed) {
            this.categoryId = categoryId;
            this.topicId = topicId;
            this.title = title;
            this.body = body;
            this.visibility = visibility;
            this.updatedAt = now;
        }
        return changed;
    }

    /**
     * 임시저장 글을 처음 작성완료한다. 작성 시각은 이 순간으로 한 번만 정하고,
     * 임시저장 중 고친 기록은 "수정"으로 보이지 않게 비운다 (FR-09 작성·수정 시각)
     */
    public void publish(Instant now) {
        if (status == PostStatus.DRAFT) {
            status = PostStatus.PUBLISHED;
            publishedAt = now;
            updatedAt = null;
        }
    }

    public boolean isPublished() {
        return status == PostStatus.PUBLISHED;
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

    public Long getTopicId() {
        return topicId;
    }

    public PostStatus getStatus() {
        return status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
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
