package com.myblog.blog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "category")
public class Category {

    public static final String DEFAULT_NAME = "미분류";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blog_id", nullable = false)
    private Long blogId;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(length = 100)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CategoryVisibility visibility = CategoryVisibility.PUBLIC;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "color_index", nullable = false)
    private short colorIndex;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Category() {
    }

    public Category(long blogId, String name, String description, int sortOrder, int colorIndex, boolean isDefault,
                    Instant now) {
        this.blogId = blogId;
        this.name = name;
        this.description = description;
        this.sortOrder = sortOrder;
        this.colorIndex = (short) colorIndex;
        this.isDefault = isDefault;
        this.createdAt = now;
    }

    public void edit(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void changeVisibility(CategoryVisibility visibility) {
        this.visibility = visibility;
    }

    public boolean isPublic() {
        return visibility == CategoryVisibility.PUBLIC;
    }

    public void swapOrderWith(Category other) {
        int mine = this.sortOrder;
        this.sortOrder = other.sortOrder;
        other.sortOrder = mine;
    }

    public Long getId() {
        return id;
    }

    public Long getBlogId() {
        return blogId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public CategoryVisibility getVisibility() {
        return visibility;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public int getColorIndex() {
        return colorIndex;
    }

    public boolean isDefault() {
        return isDefault;
    }
}
