package com.myblog.blog.service;

import com.myblog.blog.domain.Blog;
import com.myblog.blog.domain.Category;
import com.myblog.blog.domain.CategoryRepository;
import com.myblog.blog.domain.CategoryVisibility;
import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분류 관리는 블로그 주인만 한다 (CF-07, CF-08, research: 분류 순서).
 */
@Service
public class CategoryService {

    /** 정해진 색 목록의 개수. 화면이 같은 순서의 색을 갖고 있다 (BM-04-3) */
    static final int COLOR_COUNT = 8;

    private final CategoryRepository categories;
    private final BlogService blogService;
    private final BlogLimits limits;
    private final Clock clock;

    public CategoryService(CategoryRepository categories, BlogService blogService,
                           BlogLimits limits, Clock clock) {
        this.categories = categories;
        this.blogService = blogService;
        this.limits = limits;
        this.clock = clock;
    }

    /** 새 분류의 색은 아직 쓰지 않은 첫 번째 색, 모두 쓰였으면 차례대로 (BR-34, BM-04-3) */
    @Transactional
    public Category add(long blogId, long memberId, String rawName, String rawDescription) {
        Blog blog = blogService.getOwned(blogId, memberId);
        String name = checkName(rawName, false);
        String description = checkDescription(rawDescription);
        if (categories.existsNameInBlog(blog.getId(), name, -1L)) {
            throw new ApiException(ErrorCode.CONFLICT, Messages.CATEGORY_DUPLICATE);
        }
        try {
            return categories.saveAndFlush(new Category(blog.getId(), name, description,
                    categories.maxSortOrder(blog.getId()) + 1, nextColor(blog.getId()), false, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.CONFLICT, Messages.CATEGORY_DUPLICATE);
        }
    }

    /** 이름과 소개글을 함께 고친다. 소개글을 보내지 않으면(null) 그대로 둔다 (FR-17, CR-58) */
    @Transactional
    public Category edit(long categoryId, long memberId, String rawName, String rawDescription) {
        Category category = getOwned(categoryId, memberId);
        String name = checkName(rawName, category.isDefault());
        String description = rawDescription == null ? category.getDescription() : checkDescription(rawDescription);
        if (categories.existsNameInBlog(category.getBlogId(), name, category.getId())) {
            throw new ApiException(ErrorCode.CONFLICT, Messages.CATEGORY_DUPLICATE);
        }
        category.edit(name, description);
        return category;
    }

    /** 분류 공개 범위. 글마다의 공개 설정은 그대로 두고, 분류가 비공개면 그 글은 주인만 본다 (BR-46) */
    @Transactional
    public Category changeVisibility(long categoryId, long memberId, String rawVisibility) {
        Category category = getOwned(categoryId, memberId);
        CategoryVisibility visibility;
        try {
            visibility = CategoryVisibility.valueOf(rawVisibility == null ? "" : rawVisibility.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.field("visibility", Messages.CATEGORY_VISIBILITY);
        }
        category.changeVisibility(visibility);
        return category;
    }

    /** 직접 만든 분류끼리 이웃과 순서를 맞바꾼다. 끝에 있으면 그대로 두고, "미분류"는 맨 뒤에서 움직이지 않는다 (BR-34) */
    @Transactional
    public void move(long categoryId, long memberId, boolean up) {
        Category category = getOwned(categoryId, memberId);
        if (category.isDefault()) {
            throw new ApiException(ErrorCode.CONFLICT, Messages.CATEGORY_DEFAULT_UNMOVABLE);
        }
        List<Category> ordered = categories.findOrdered(category.getBlogId()).stream()
                .filter(c -> !c.isDefault())
                .toList();
        int index = ordered.indexOf(category);
        int target = up ? index - 1 : index + 1;
        if (target >= 0 && target < ordered.size()) {
            category.swapOrderWith(ordered.get(target));
        }
    }

    /**
     * 끌어서 놓은 순서대로 직접 만든 분류의 순서를 정한다 (FR-17, CR-31, CR-56).
     * 블로그의 "미분류"가 아닌 분류 id를 빠짐없이, 겹치지 않게 보내야 한다. "미분류"는 늘 맨 뒤다.
     */
    @Transactional
    public void reorder(long blogId, long memberId, List<Long> categoryIds) {
        Blog blog = blogService.getOwned(blogId, memberId);
        List<Category> movable = categories.findOrdered(blog.getId()).stream()
                .filter(c -> !c.isDefault())
                .toList();
        if (categoryIds == null || categoryIds.size() != movable.size()
                || new HashSet<>(categoryIds).size() != categoryIds.size()) {
            throw ApiException.field("categoryIds", Messages.CATEGORY_ORDER_INVALID);
        }
        Map<Long, Category> byId = new HashMap<>();
        movable.forEach(c -> byId.put(c.getId(), c));
        for (int i = 0; i < categoryIds.size(); i++) {
            Category category = byId.get(categoryIds.get(i));
            if (category == null) {
                throw ApiException.field("categoryIds", Messages.CATEGORY_ORDER_INVALID);
            }
            category.changeSortOrder(i + 1);
        }
    }

    /** 글이 있거나 "미분류"이면 지울 수 없다 (CF-08-6, 8) */
    @Transactional
    public void delete(long categoryId, long memberId) {
        Category category = getOwned(categoryId, memberId);
        if (category.isDefault()) {
            throw new ApiException(ErrorCode.CONFLICT, Messages.CATEGORY_DEFAULT_UNDELETABLE);
        }
        long postCount = categories.countPosts(category.getId());
        if (postCount > 0) {
            throw new ApiException(ErrorCode.CONFLICT, Messages.CATEGORY_HAS_POSTS.formatted(postCount),
                    List.of(), Map.of("postCount", postCount));
        }
        categories.delete(category);
    }

    private Category getOwned(long categoryId, long memberId) {
        Category category = categories.findById(categoryId)
                .orElseThrow(() -> ApiException.notFound(Messages.CATEGORY_NOT_FOUND));
        blogService.getOwned(category.getBlogId(), memberId);
        return category;
    }

    /** 1~20자, 앞뒤 공백 제거. 기본 분류가 아니면 "미분류"(대소문자 무시)는 쓸 수 없다 (BR-34) */
    private String checkName(String raw, boolean isDefault) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty()) {
            throw ApiException.field("name", Messages.CATEGORY_NAME_REQUIRED);
        }
        if (name.codePointCount(0, name.length()) > limits.categoryNameMax()) {
            throw ApiException.field("name", "분류 이름은 " + limits.categoryNameMax() + "자 이하로 입력해 주세요");
        }
        if (!isDefault && name.equalsIgnoreCase(Category.DEFAULT_NAME)) {
            throw ApiException.field("name", Messages.CATEGORY_RESERVED_NAME);
        }
        return name;
    }

    /** 소개글은 선택, 0~100자, 앞뒤 공백 제거 (CR-58) */
    private String checkDescription(String raw) {
        String description = raw == null ? "" : raw.strip();
        if (description.isEmpty()) {
            return null;
        }
        if (description.codePointCount(0, description.length()) > limits.categoryDescriptionMax()) {
            throw ApiException.field("description",
                    "소개글은 " + limits.categoryDescriptionMax() + "자 이하로 입력해 주세요");
        }
        return description;
    }

    private int nextColor(long blogId) {
        Set<Integer> used = new HashSet<>();
        categories.colorIndexes(blogId).forEach(c -> used.add((int) c));
        for (int i = 0; i < COLOR_COUNT; i++) {
            if (!used.contains(i)) {
                return i;
            }
        }
        return (int) (categories.countByBlogId(blogId) % COLOR_COUNT);
    }
}
