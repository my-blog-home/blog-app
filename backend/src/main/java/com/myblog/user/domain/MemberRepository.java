package com.myblog.user.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /** 이메일은 소문자로 저장하지만 비교도 대소문자 없이 한다 */
    @Query("select m from Member m where lower(m.email) = lower(:email)")
    Optional<Member> findByEmail(String email);

    @Query("select count(m) > 0 from Member m where lower(m.email) = lower(:email)")
    boolean existsByEmail(String email);

    @Query("select count(m) > 0 from Member m where lower(m.nickname) = lower(:nickname)")
    boolean existsByNickname(String nickname);
}
