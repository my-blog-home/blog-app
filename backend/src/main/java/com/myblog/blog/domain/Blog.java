package com.myblog.blog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "blog")
public class Blog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(name = "comments_last_viewed_at")
    private Instant commentsLastViewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Blog() {
    }

    public Blog(long ownerId, String name, long topicId, Instant now) {
        this.ownerId = ownerId;
        this.name = name;
        this.topicId = topicId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isOwnedBy(Long memberId) {
        return memberId != null && ownerId.equals(memberId);
    }

    public void update(String name, String description, long topicId, Instant now) {
        this.name = name;
        this.description = description;
        this.topicId = topicId;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Long getTopicId() {
        return topicId;
    }
}
