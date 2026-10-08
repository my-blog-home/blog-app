package com.myblog.user.service;

import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import com.myblog.user.domain.Member;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 변경·탈퇴 때 현재 비밀번호를 확인한다. 틀린 횟수는 로그인 실패와 합산한다 (CF-15-14, 17).
 * 실패 기록이 저장되도록 호출하는 트랜잭션은 ApiException에 롤백하지 않아야 한다.
 */
@Component
public class PasswordCheck {

    private final PasswordEncoder passwordEncoder;
    private final BlogLimits limits;
    private final Clock clock;

    public PasswordCheck(PasswordEncoder passwordEncoder, BlogLimits limits, Clock clock) {
        this.passwordEncoder = passwordEncoder;
        this.limits = limits;
        this.clock = clock;
    }

    public void verify(Member member, String rawPassword) {
        Instant now = clock.instant();
        member.clearExpiredLock(now);
        if (member.isLocked(now)) {
            throw locked(member, now);
        }
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, member.getPasswordHash())) {
            member.recordFailure(limits.loginMaxFailures(), limits.loginLockDuration(), now);
            if (member.isLocked(now)) {
                throw locked(member, now);
            }
            throw ApiException.field("currentPassword", Messages.CURRENT_PASSWORD_WRONG);
        }
        member.resetFailures(now);
    }

    private static ApiException locked(Member member, Instant now) {
        long minutes = Math.max(1, (Duration.between(now, member.getLockedUntil()).toSeconds() + 59) / 60);
        return new ApiException(ErrorCode.ACCOUNT_LOCKED, Messages.ACCOUNT_LOCKED.formatted(minutes), List.of(),
                Map.of("retryAfterMinutes", minutes));
    }
}
