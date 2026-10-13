package com.myblog.user.web;

import com.myblog.common.security.MemberPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/** 로그인 세션 시작. 이메일 로그인과 소셜 로그인이 같이 쓴다 */
@Component
public class SessionLogin {

    private final SecurityContextRepository contextRepository;

    public SessionLogin(SecurityContextRepository contextRepository) {
        this.contextRepository = contextRepository;
    }

    /** 로그인할 때마다 세션 ID를 새로 발급한다 (NF-12) */
    public void start(long memberId, HttpServletRequest http, HttpServletResponse response) {
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
