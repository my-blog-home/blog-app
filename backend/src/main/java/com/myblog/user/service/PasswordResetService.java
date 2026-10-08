package com.myblog.user.service;

import com.myblog.common.security.SessionTerminator;
import com.myblog.user.domain.Member;
import com.myblog.user.domain.MemberRepository;
import com.myblog.user.validation.InputRules;
import com.myblog.user.verification.VerificationPurpose;
import com.myblog.user.verification.VerificationService;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 찾기. 가입 여부와 관계없이 화면 안내와 제한 동작을 똑같이 하고, 메일은 가입된 이메일에만 보낸다 (CF-25, NF-06).
 */
@Service
public class PasswordResetService {

    private final MemberRepository members;
    private final VerificationService verification;
    private final InputRules rules;
    private final PasswordEncoder passwordEncoder;
    private final SessionTerminator sessions;
    private final Clock clock;

    public PasswordResetService(MemberRepository members, VerificationService verification, InputRules rules,
                                PasswordEncoder passwordEncoder, SessionTerminator sessions, Clock clock) {
        this.members = members;
        this.verification = verification;
        this.rules = rules;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.clock = clock;
    }

    public void requestCode(String rawEmail) {
        String email = rules.normalizeEmail(rawEmail);
        verification.issue(VerificationPurpose.RESET, email, members.existsByEmail(email));
    }

    public void confirmCode(String rawEmail, String code) {
        verification.confirm(VerificationPurpose.RESET, rules.normalizeEmail(rawEmail), code);
    }

    /** 자동 로그인 없이 모든 기기를 끊고 잠금을 푼다 (CF-25-7~9) */
    @Transactional
    public void reset(String rawEmail, String newPassword, String confirm) {
        String email = rules.normalizeEmail(rawEmail);
        rules.checkPassword("newPassword", newPassword, confirm);
        verification.requireVerified(VerificationPurpose.RESET, email);
        verification.clearVerified(VerificationPurpose.RESET, email);
        members.findByEmail(email).ifPresent((Member member) -> {
            Instant now = clock.instant();
            member.changePasswordHash(passwordEncoder.encode(newPassword), now);
            member.resetFailures(now);
            sessions.terminateAll(member.getId());
        });
    }
}
