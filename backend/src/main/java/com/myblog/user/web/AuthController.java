package com.myblog.user.web;

import com.myblog.blog.domain.Blog;
import com.myblog.blog.domain.BlogRepository;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import com.myblog.common.security.CurrentMember;
import com.myblog.common.security.MemberPrincipal;
import com.myblog.user.domain.Member;
import com.myblog.user.domain.MemberRepository;
import com.myblog.user.service.LoginService;
import com.myblog.user.service.SuspensionNotice;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인·로그아웃·내 상태 (CF-02, NF-12).
 */
@RestController
public class AuthController {

    public record LoginRequest(String email, String password) {
    }

    /**
     * role: MEMBER 또는 ADMIN. 관리자는 블로그가 없을 수 있다(blogId 비어 있음).
     * pendingReportCount는 관리자에게만 채운다(처리 대기 신고 건수, 메뉴의 "관리자" 옆 숫자) (FR-078, FR-079)
     */
    public record Me(long id, String nickname, Long blogId, String profileColor, String role,
                     Long pendingReportCount) {
    }

    private final LoginService loginService;
    private final MemberRepository members;
    private final BlogRepository blogs;
    private final SecurityContextRepository contextRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final ZoneId zone;

    public AuthController(LoginService loginService, MemberRepository members, BlogRepository blogs,
                          SecurityContextRepository contextRepository, NamedParameterJdbcTemplate jdbc,
                          @Value("${blog.zone}") String zone) {
        this.loginService = loginService;
        this.members = members;
        this.blogs = blogs;
        this.contextRepository = contextRepository;
        this.jdbc = jdbc;
        this.zone = ZoneId.of(zone);
    }

    @PostMapping("/api/auth/login")
    public Me login(@RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        LoginService.Result result = loginService.login(request.email(), request.password());
        return switch (result) {
            case LoginService.Failed failed -> throw new ApiException(ErrorCode.LOGIN_FAILED, Messages.LOGIN_FAILED);
            case LoginService.Locked locked -> throw new ApiException(ErrorCode.ACCOUNT_LOCKED,
                    Messages.ACCOUNT_LOCKED.formatted(locked.minutesLeft()), List.of(),
                    Map.of("retryAfterMinutes", locked.minutesLeft()));
            case LoginService.Suspended suspended -> {
                // 비밀번호가 맞은 사람에게만 정지 안내를 보여 준다 (FR-081)
                Map<String, Object> details = new HashMap<>();
                details.put("until", suspended.until() == null ? "PERMANENT" : suspended.until().toString());
                details.put("reason", suspended.reason() == null ? "" : suspended.reason());
                throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED,
                        SuspensionNotice.message(suspended.until(), suspended.reason(), zone), List.of(), details);
            }
            case LoginService.Success success -> {
                startSession(success.memberId(), http, response);
                yield me(success.memberId());
            }
        };
    }

    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @GetMapping("/api/auth/me")
    public Me currentMember() {
        return me(CurrentMember.id());
    }

    private Me me(long memberId) {
        Member member = members.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, Messages.LOGIN_REQUIRED));
        Long blogId = blogs.findFirstByOwnerIdOrderByIdAsc(memberId).map(Blog::getId).orElse(null);
        Long pendingReports = member.isAdmin()
                ? jdbc.queryForObject("select count(*) from report where status = 'PENDING'", Map.of(), Long.class)
                : null;
        return new Me(member.getId(), member.getNickname(), blogId, member.getProfileColor(), member.getRole().name(),
                pendingReports);
    }

    /** 로그인할 때마다 세션 ID를 새로 발급한다 (NF-12) */
    private void startSession(long memberId, HttpServletRequest http, HttpServletResponse response) {
        if (http.getSession(false) != null) {
            http.changeSessionId();
        } else {
            http.getSession(true);
        }
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                new MemberPrincipal(memberId), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
        // 관리자 권한은 세션에 담지 않고 관리자 API가 요청마다 회원 표의 role로 확인한다 (FR-078)
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, http, response);
    }
}
