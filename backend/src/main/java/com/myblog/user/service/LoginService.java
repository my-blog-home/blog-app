package com.myblog.user.service;

import com.myblog.common.config.BlogLimits;
import com.myblog.common.sql.SuspensionSql;
import com.myblog.user.domain.Member;
import com.myblog.user.domain.MemberRepository;
import java.time.Clock;
import java.time.Duration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 확인과 연속 실패 잠금 (CF-02-3~9, research R-07).
 * 실패 기록이 저장되어야 하므로 예외를 던지지 않고 결과를 돌려준다.
 * 탈퇴한 회원은 없는 이메일과 같은 실패이고(FR-086), 정지 중인 회원은 비밀번호가 맞을 때만 정지 안내를 받는다 (FR-081).
 */
@Service
public class LoginService {

    public sealed interface Result permits Success, Failed, Locked, Suspended {
    }

    public record Success(long memberId) implements Result {
    }

    public record Failed() implements Result {
    }

    public record Locked(long minutesLeft) implements Result {
    }

    /** until이 비어 있으면 영구 정지 */
    public record Suspended(Instant until, String reason) implements Result {
    }

    /** 없는 이메일에도 같은 시간이 걸리도록 비교에 쓰는 값 */
    private final String dummyHash;

    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final BlogLimits limits;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public LoginService(MemberRepository members, PasswordEncoder passwordEncoder, BlogLimits limits,
                        NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.limits = limits;
        this.jdbc = jdbc;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public Result login(String rawEmail, String password) {
        String email = rawEmail == null ? "" : rawEmail.strip().toLowerCase(Locale.ROOT);
        String input = password == null ? "" : password;
        Optional<Member> found = members.findByEmail(email).filter(m -> !m.isWithdrawn());
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
        Optional<Suspended> suspended = activeSuspension(member.getId(), now);
        if (suspended.isPresent()) {
            return suspended.get();
        }
        return new Success(member.getId());
    }

    /** 효력 있는 정지 중 가장 오래 가는 것 (영구가 먼저, BR-49) */
    private Optional<Suspended> activeSuspension(long memberId, Instant now) {
        List<Suspended> found = jdbc.query("select s.ends_at, s.reason from suspension s where s.member_id = :id and "
                        + SuspensionSql.active("s", "now") + " order by s." + SuspensionSql.LONGEST_FIRST + " limit 1",
                Map.of("id", memberId, "now", Timestamp.from(now)), (rs, row) -> {
                    Timestamp ends = rs.getTimestamp("ends_at");
                    return new Suspended(ends == null ? null : ends.toInstant(), rs.getString("reason"));
                });
        return found.stream().findFirst();
    }

    private static Locked locked(Member member, Instant now) {
        long seconds = Duration.between(now, member.getLockedUntil()).toSeconds();
        return new Locked(Math.max(1, (seconds + 59) / 60));
    }
}
