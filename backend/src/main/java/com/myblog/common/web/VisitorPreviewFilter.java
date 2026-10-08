package com.myblog.common.web;

import com.myblog.common.security.CurrentMember;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 방문자 화면 미리보기 (FR-083, BR-44). 화면이 "X-Ylog-View: visitor" 헤더를 붙여 부르면
 * 그 요청 하나만 로그인하지 않은 방문자로 처리한다(공개 글만, 주인 버튼 없음, 비공개 글은 "존재하지 않는 글입니다").
 * 이 요청으로 연 글은 조회수·방문자에 세지 않도록 표시(ATTRIBUTE)를 남긴다.
 * 세션의 로그인 정보는 그대로 두고, 요청이 끝나면 원래대로 되돌린다.
 * 로그인하지 않은 요청에 붙은 헤더는 무시한다(원래 방문자이므로 조회수·방문자에 그대로 센다).
 * 보안 필터(로그인 정보 읽기)와 AccountStatusFilter 뒤에 돈다.
 */
@Component
@Order(1)
public class VisitorPreviewFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Ylog-View";
    public static final String ATTRIBUTE = VisitorPreviewFilter.class.getName() + ".PREVIEW";

    /** 지금 요청이 방문자 화면 미리보기인지 */
    public static boolean isPreview(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(ATTRIBUTE));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"visitor".equalsIgnoreCase(request.getHeader(HEADER));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (CurrentMember.idIfPresent().isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        request.setAttribute(ATTRIBUTE, Boolean.TRUE);
        SecurityContext original = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.setContext(original);
        }
    }
}
