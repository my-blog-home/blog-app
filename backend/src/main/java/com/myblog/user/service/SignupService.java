package com.myblog.user.service;

import com.myblog.blog.service.BlogCreationService;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import com.myblog.user.domain.Member;
import com.myblog.user.domain.MemberRepository;
import com.myblog.user.validation.InputRules;
import com.myblog.user.verification.VerificationPurpose;
import com.myblog.user.verification.VerificationService;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 인증을 먼저 마친 뒤 계정을 한 번에 만든다 (CF-01, CF-14, research R-04).
 */
@Service
public class SignupService {

    private final MemberRepository members;
    private final VerificationService verification;
    private final BlogCreationService blogCreation;
    private final InputRules rules;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public SignupService(MemberRepository members, VerificationService verification, BlogCreationService blogCreation,
                         InputRules rules, PasswordEncoder passwordEncoder, Clock clock) {
        this.members = members;
        this.verification = verification;
        this.blogCreation = blogCreation;
        this.rules = rules;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** 이메일 중복은 메일을 보내기 전에 확인한다 (CF-14-1) */
    public void requestCode(String rawNickname, String rawEmail) {
        rules.checkNickname(rawNickname);
        String email = rules.normalizeEmail(rawEmail);
        if (members.existsByEmail(email)) {
            throw new ApiException(ErrorCode.CONFLICT, Messages.EMAIL_DUPLICATE);
        }
        verification.issue(VerificationPurpose.SIGNUP, email, true);
    }

    public void confirmCode(String rawEmail, String code) {
        verification.confirm(VerificationPurpose.SIGNUP, rules.normalizeEmail(rawEmail), code);
    }

    public boolean isNicknameAvailable(String nickname) {
        return !members.existsByNickname(nickname);
    }

    @Transactional
    public long signup(String rawNickname, String rawEmail, String password, String passwordConfirm) {
        String nickname = rules.checkNickname(rawNickname);
        String email = rules.normalizeEmail(rawEmail);
        rules.checkPassword("password", password, passwordConfirm);
        verification.requireVerified(VerificationPurpose.SIGNUP, email);

        if (members.existsByEmail(email)) {
            throw new ApiException(ErrorCode.CONFLICT, Messages.EMAIL_DUPLICATE);
        }
        if (members.existsByNickname(nickname)) {
            throw ApiException.field("nickname", Messages.NICKNAME_DUPLICATE);
        }

        Instant now = clock.instant();
        Member member;
        try {
            member = members.saveAndFlush(new Member(email, nickname, passwordEncoder.encode(password), now));
        } catch (DataIntegrityViolationException e) {
            // 인증하는 사이에 다른 사람이 먼저 가입한 경우 (CF-01-21)
            throw new ApiException(ErrorCode.CONFLICT, Messages.EMAIL_DUPLICATE);
        }
        blogCreation.createFor(member.getId(), nickname, now);
        verification.clearVerified(VerificationPurpose.SIGNUP, email);
        return member.getId();
    }
}
