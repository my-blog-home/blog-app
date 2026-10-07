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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
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

    public record Me(long id, String nickname, Long blogId) {
    }

    private final LoginService loginService;
    private final MemberRepository members;
    private final BlogRepository blogs;
    private final SecurityContextRepository contextRepository;

    public AuthController(LoginService loginService, MemberRepository members, BlogRepository blogs,
                          SecurityContextRepository contextRepository) {
        this.loginService = loginService;
        this.members = members;
        this.blogs = blogs;
        this.contextRepository = contextRepository;
    }

    @PostMapping("/api/auth/login")
    public Me login(@RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        LoginService.Result result = loginService.login(request.email(), request.password());
        return switch (result) {
            case LoginService.Failed failed -> throw new ApiException(ErrorCode.LOGIN_FAILED, Messages.LOGIN_FAILED);
            case LoginService.Locked locked -> throw new ApiException(ErrorCode.ACCOUNT_LOCKED,
                    Messages.ACCOUNT_LOCKED.formatted(locked.minutesLeft()), List.of(),
                    Map.of("retryAfterMinutes", locked.minutesLeft()));
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
        return new Me(member.getId(), member.getNickname(), blogId);
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
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, http, response);
    }
}
