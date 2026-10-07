package com.myblog.blog.service;

import com.myblog.blog.domain.Blog;
import com.myblog.blog.domain.BlogRepository;
import com.myblog.blog.domain.Category;
import com.myblog.blog.domain.CategoryRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입이 끝나면 블로그와 "미분류" 분류를 함께 만든다 (CF-03-1~3).
 */
@Service
public class BlogCreationService {

    private final BlogRepository blogs;
    private final CategoryRepository categories;

    public BlogCreationService(BlogRepository blogs, CategoryRepository categories) {
        this.blogs = blogs;
        this.categories = categories;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Blog createFor(long memberId, String nickname, Instant now) {
        if (blogs.existsByOwnerId(memberId)) {
            throw new IllegalStateException("회원당 블로그는 1개입니다");
        }
        Blog blog = blogs.save(new Blog(memberId, nickname + "의 블로그", now));
        categories.save(new Category(blog.getId(), Category.DEFAULT_NAME, 1, 0, true, now));
        return blog;
    }
}
