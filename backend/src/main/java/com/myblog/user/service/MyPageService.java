package com.myblog.user.service;

import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import com.myblog.common.security.SessionTerminator;
import com.myblog.user.domain.Member;
import com.myblog.user.domain.MemberRepository;
import com.myblog.user.validation.InputRules;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지: 내 정보 수정, 비밀번호 변경, 회원 탈퇴 (CF-15).
 */
@Service
public class MyPageService {

    public record MyInfo(String email, String nickname, String bio, String profileColor, Instant joinedAt, Long blogId) {
    }

    private final MemberRepository members;
    private final InputRules rules;
    private final PasswordCheck passwordCheck;
    private final PasswordEncoder passwordEncoder;
    private final SessionTerminator sessions;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public MyPageService(MemberRepository members, InputRules rules, PasswordCheck passwordCheck,
                         PasswordEncoder passwordEncoder, SessionTerminator sessions, NamedParameterJdbcTemplate jdbc,
                         Clock clock) {
        this.members = members;
        this.rules = rules;
        this.passwordCheck = passwordCheck;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MyInfo info(long memberId) {
        Member member = get(memberId);
        Long blogId = jdbc.queryForList("select id from blog where owner_id = :id order by id limit 1",
                Map.of("id", memberId), Long.class).stream().findFirst().orElse(null);
        return new MyInfo(member.getEmail(), member.getNickname(), member.getBio(), member.getProfileColor(),
                member.getCreatedAt(), blogId);
    }

    /**
     * 닉네임은 가입 규칙을 따르고(내 닉네임 그대로는 중복 아님), 소개는 0~100자 (CF-15-5).
     * 프로필 색은 정해진 6가지 중 하나이고, 보내지 않으면 그대로 둔다 (FR-05)
     */
    @Transactional
    public MyInfo updateProfile(long memberId, String rawNickname, String rawBio, String rawProfileColor) {
        Member member = get(memberId);
        String nickname = rules.checkNickname(rawNickname);
        if (members.existsByNicknameExcept(nickname, memberId)) {
            throw ApiException.field("nickname", Messages.NICKNAME_DUPLICATE);
        }
        String bio = rawBio == null || rawBio.isBlank() ? null : rawBio.strip();
        if (bio != null && bio.codePointCount(0, bio.length()) > 100) {
            throw ApiException.field("bio", Messages.BIO_TOO_LONG);
        }
        String profileColor = member.getProfileColor();
        if (rawProfileColor != null) {
            profileColor = rawProfileColor.strip().toLowerCase(java.util.Locale.ROOT);
            if (!Member.PROFILE_COLORS.contains(profileColor)) {
                throw ApiException.field("profileColor", Messages.PROFILE_COLOR_INVALID);
            }
        }
        member.updateProfile(nickname, bio, profileColor, clock.instant());
        return info(memberId);
    }

    /** 바꾸면 지금 기기만 남기고 다른 기기의 로그인을 끊는다 (CF-15-10~15) */
    @Transactional(noRollbackFor = ApiException.class)
    public void changePassword(long memberId, String current, String next, String confirm, String currentSessionId) {
        Member member = get(memberId);
        passwordCheck.verify(member, current);
        rules.checkPassword("newPassword", next, confirm);
        if (passwordEncoder.matches(next, member.getPasswordHash())) {
            throw ApiException.field("newPassword", Messages.NEW_PASSWORD_SAME);
        }
        member.changePasswordHash(passwordEncoder.encode(next), clock.instant());
        sessions.terminateAllExcept(memberId, currentSessionId);
    }

    /**
     * 탈퇴: 내 블로그·글(→ 댓글·좋아요 등)·분류를 지우고, 남의 글에 단 댓글은 작성자를 비워 남긴다 (CF-15-17~21, research R-14).
     * 글은 분류를 RESTRICT로 참조하므로 먼저 지운다. 나머지는 표의 ON DELETE 규칙이 처리한다.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void withdraw(long memberId, String password, boolean agreed) {
        Member member = get(memberId);
        passwordCheck.verify(member, password);
        if (!agreed) {
            throw ApiException.field("agreed", Messages.WITHDRAW_AGREE);
        }
        Map<String, Object> params = Map.of("id", memberId);
        jdbc.update("delete from post where blog_id in (select id from blog where owner_id = :id)", params);
        members.delete(member);
        members.flush();
        sessions.terminateAll(memberId);
    }

    private Member get(long memberId) {
        return members.findById(memberId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, Messages.LOGIN_REQUIRED));
    }
}
