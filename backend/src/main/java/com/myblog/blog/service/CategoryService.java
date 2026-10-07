package com.myblog.blog.service;

import com.myblog.blog.domain.Blog;
import com.myblog.blog.domain.Category;
import com.myblog.blog.domain.CategoryRepository;
import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import java.time.Clock;
import java.util.List;
import java.util.Map;
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

    @Transactional
    public Category add(long blogId, long memberId, String rawName) {
        Blog blog = blogService.getOwned(blogId, memberId);
        String name = checkName(rawName);
        if (categories.existsNameInBlog(blog.getId(), name, -1L)) {
            throw new ApiException(ErrorCode.CONFLICT, Messages.CATEGORY_DUPLICATE);
        }
        int color = (int) (categories.countByBlogId(blog.getId()) % COLOR_COUNT);
        try {
            return categories.saveAndFlush(new Category(blog.getId(), name,
                    categories.maxSortOrder(blog.getId()) + 1, color, false, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.CONFLICT, Messages.CATEGORY_DUPLICATE);
        }
    }

    @Transactional
    public Category rename(long categoryId, long memberId, String rawName) {
        Category category = getOwned(categoryId, memberId);
        String name = checkName(rawName);
        if (categories.existsNameInBlog(category.getBlogId(), name, category.getId())) {
            throw new ApiException(ErrorCode.CONFLICT, Messages.CATEGORY_DUPLICATE);
        }
        category.rename(name);
        return category;
    }

    /** 이웃한 분류와 순서를 맞바꾼다. 끝에 있으면 그대로 둔다 */
    @Transactional
    public void move(long categoryId, long memberId, boolean up) {
        Category category = getOwned(categoryId, memberId);
        List<Category> ordered = categories.findByBlogIdOrderBySortOrderAsc(category.getBlogId());
        int index = ordered.indexOf(category);
        int target = up ? index - 1 : index + 1;
        if (target >= 0 && target < ordered.size()) {
            category.swapOrderWith(ordered.get(target));
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
        Category category = categories.findById(categoryId).orElseThrow(() -> ApiException.notFound(Messages.NOT_FOUND));
        blogService.getOwned(category.getBlogId(), memberId);
        return category;
    }

    private String checkName(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty()) {
            throw ApiException.field("name", Messages.CATEGORY_NAME_REQUIRED);
        }
        if (name.codePointCount(0, name.length()) > limits.categoryNameMax()) {
            throw ApiException.field("name", "분류 이름은 " + limits.categoryNameMax() + "자 이하로 입력해 주세요");
        }
        return name;
    }
}
