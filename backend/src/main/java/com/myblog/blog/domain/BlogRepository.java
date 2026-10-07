package com.myblog.blog.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogRepository extends JpaRepository<Blog, Long> {

    Optional<Blog> findFirstByOwnerIdOrderByIdAsc(Long ownerId);

    boolean existsByOwnerId(Long ownerId);
}
