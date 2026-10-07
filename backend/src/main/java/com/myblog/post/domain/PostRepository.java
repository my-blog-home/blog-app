package com.myblog.post.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

    long countByCategoryId(Long categoryId);

    Optional<Post> findFirstByBlogIdOrderByCreatedAtDescIdDesc(Long blogId);
}
