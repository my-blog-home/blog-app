package com.myblog.common.security;

import com.myblog.common.sql.SuspensionSql;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로그인해 둔 요청마다 회원이 아직 쓸 수 있는 상태인지 확인한다 (FR-081, BR-49).
 * 탈퇴했거나 지금 정지 중이면 세션을 지우고 로그인하지 않은 요청으로 이어 간다. 그래서 로그인이 필요한 API는 401이 된다.
 * 정지할 때도 세션을 지우지만(SessionTerminator), 다른 경로로 남은 세션도 다음 요청에서 끊기도록 여기서 한 번 더 막는다.
 * 보안 필터(로그인 정보 읽기) 뒤에 돈다.
 */
@Component
@Order(0)
public class AccountStatusFilter extends OncePerRequestFilter {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public AccountStatusFilter(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Long> memberId = CurrentMember.idIfPresent();
        if (memberId.isPresent() && !usable(memberId.get())) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                try {
                    session.invalidate();
                } catch (IllegalStateException alreadyGone) {
                    // 이미 지워진 세션
                }
            }
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }

    private boolean usable(long memberId) {
        Long count = jdbc.queryForObject("select count(*) from member m where m.id = :id and m.withdrawn_at is null"
                        + " and not " + SuspensionSql.memberSuspended("m.id", "now"),
                Map.of("id", memberId, "now", Timestamp.from(clock.instant())), Long.class);
        return count != null && count > 0;
    }
}
