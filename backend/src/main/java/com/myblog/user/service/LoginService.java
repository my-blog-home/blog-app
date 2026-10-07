package com.myblog.user.service;

import com.myblog.common.config.BlogLimits;
import com.myblog.user.domain.Member;
import com.myblog.user.domain.MemberRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 확인과 연속 실패 잠금 (CF-02-3~9, research R-07).
 * 실패 기록이 저장되어야 하므로 예외를 던지지 않고 결과를 돌려준다.
 */
@Service
public class LoginService {

    public sealed interface Result permits Success, Failed, Locked {
    }

    public record Success(long memberId) implements Result {
    }

    public record Failed() implements Result {
    }

    public record Locked(long minutesLeft) implements Result {
    }

    /** 없는 이메일에도 같은 시간이 걸리도록 비교에 쓰는 값 */
    private final String dummyHash;

    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final BlogLimits limits;
    private final Clock clock;

    public LoginService(MemberRepository members, PasswordEncoder passwordEncoder, BlogLimits limits, Clock clock) {
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.limits = limits;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public Result login(String rawEmail, String password) {
        String email = rawEmail == null ? "" : rawEmail.strip().toLowerCase(Locale.ROOT);
        String input = password == null ? "" : password;
        Optional<Member> found = members.findByEmail(email);
        if (found.isEmpty()) {
            passwordEncoder.matches(input, dummyHash);
            return new Failed();
        }

        Member member = found.get();
        Instant now = clock.instant();
        member.clearExpiredLock(now);
        if (member.isLocked(now)) {
            return locked(member, now);
        }
        if (!passwordEncoder.matches(input, member.getPasswordHash())) {
            member.recordFailure(limits.loginMaxFailures(), limits.loginLockDuration(), now);
            return member.isLocked(now) ? locked(member, now) : new Failed();
        }
        member.resetFailures(now);
        return new Success(member.getId());
    }

    private static Locked locked(Member member, Instant now) {
        long seconds = Duration.between(now, member.getLockedUntil()).toSeconds();
        return new Locked(Math.max(1, (seconds + 59) / 60));
    }
}
