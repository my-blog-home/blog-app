package com.myblog.blog.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** "미분류"는 항상 맨 뒤, 나머지는 정한 순서 (BR-34) */
    @Query("select c from Category c where c.blogId = :blogId order by c.isDefault asc, c.sortOrder asc, c.id asc")
    List<Category> findOrdered(Long blogId);

    Optional<Category> findFirstByBlogIdAndIsDefaultTrue(Long blogId);

    @Query("select count(c) > 0 from Category c where c.blogId = :blogId and lower(c.name) = lower(:name) and c.id <> :excludeId")
    boolean existsNameInBlog(Long blogId, String name, Long excludeId);

    @Query("select coalesce(max(c.sortOrder), 0) from Category c where c.blogId = :blogId")
    int maxSortOrder(Long blogId);

    long countByBlogId(Long blogId);

    @Query("select c.colorIndex from Category c where c.blogId = :blogId")
    List<Short> colorIndexes(Long blogId);

    /** blog 모듈이 post 모듈을 부르지 않도록 글 수는 표에서 직접 센다 (모듈 방향: post → blog) */
    @Query(value = "select count(*) from post where category_id = :categoryId", nativeQuery = true)
    long countPosts(Long categoryId);
}
