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

    /** 탈퇴하지 않은 회원의 이메일인지. 비밀번호 찾기는 탈퇴한 이메일을 가입되지 않은 이메일과 같게 본다 (FR-086) */
    @Query("select count(m) > 0 from Member m where lower(m.email) = lower(:email) and m.withdrawnAt is null")
    boolean existsActiveByEmail(String email);

    @Query("select count(m) > 0 from Member m where lower(m.nickname) = lower(:nickname)")
    boolean existsByNickname(String nickname);

    @Query("select count(m) > 0 from Member m where lower(m.nickname) = lower(:nickname) and m.id <> :excludeId")
    boolean existsByNicknameExcept(String nickname, Long excludeId);
}
