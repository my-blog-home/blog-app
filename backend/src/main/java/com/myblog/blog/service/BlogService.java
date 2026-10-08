package com.myblog.blog.service;

import com.myblog.blog.domain.Blog;
import com.myblog.blog.domain.BlogRepository;
import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.Messages;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlogService {

    private final BlogRepository blogs;
    private final BlogLimits limits;
    private final TopicService topics;
    private final Clock clock;

    public BlogService(BlogRepository blogs, BlogLimits limits, TopicService topics, Clock clock) {
        this.blogs = blogs;
        this.limits = limits;
        this.topics = topics;
        this.clock = clock;
    }

    public Blog get(long blogId) {
        return blogs.findById(blogId).orElseThrow(() -> ApiException.notFound(Messages.NOT_FOUND));
    }

    /** 블로그 주인이 아니면 존재 자체를 숨긴다 (NF-02, contracts/api.md) */
    public Blog getOwned(long blogId, long memberId) {
        Blog blog = get(blogId);
        if (!blog.isOwnedBy(memberId)) {
            throw ApiException.notFound(Messages.NOT_FOUND);
        }
        return blog;
    }

    /** 블로그 이름 1~30자, 소개 0~200자, 대표 주제는 10개 중 하나(보내지 않으면 그대로) (CF-04-1~3, FR-07) */
    @Transactional
    public Blog update(long blogId, long memberId, String rawName, String rawDescription, Long topicId) {
        Blog blog = getOwned(blogId, memberId);
        String name = rawName == null ? "" : rawName.strip();
        if (name.isEmpty()) {
            throw ApiException.field("name", Messages.BLOG_NAME_REQUIRED);
        }
        if (name.codePointCount(0, name.length()) > limits.blogNameMax()) {
            throw ApiException.field("name", "블로그 이름은 " + limits.blogNameMax() + "자 이하로 입력해 주세요");
        }
        String description = Optional.ofNullable(rawDescription).map(String::strip).filter(s -> !s.isEmpty()).orElse(null);
        if (description != null && description.codePointCount(0, description.length()) > limits.blogDescriptionMax()) {
            throw ApiException.field("description", "소개는 " + limits.blogDescriptionMax() + "자 이하로 입력해 주세요");
        }
        long topic = topicId == null ? blog.getTopicId() : topics.check(topicId);
        blog.update(name, description, topic, clock.instant());
        return blog;
    }
}
