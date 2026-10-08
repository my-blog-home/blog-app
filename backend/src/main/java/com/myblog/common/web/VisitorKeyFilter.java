package com.myblog.common.web;

import com.myblog.common.security.CurrentMember;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 같은 사람을 알아보는 구분값. 로그인 회원은 회원 id, 비회원은 1년짜리 쿠키 (research R-12).
 * 글 상세 요청에서만 쓴다.
 */
@Component
public class VisitorKeyFilter extends OncePerRequestFilter {

    public static final String ATTRIBUTE = VisitorKeyFilter.class.getName() + ".KEY";
    private static final String COOKIE = "vid";
    private static final Pattern UUID_FORMAT = Pattern.compile("^[0-9a-f\\-]{36}$");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().matches("^/api/posts/\\d+$") || !"GET".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = CurrentMember.idIfPresent().map(id -> "m:" + id).orElseGet(() -> "v:" + visitorId(request, response));
        request.setAttribute(ATTRIBUTE, key);
        chain.doFilter(request, response);
    }

    private static String visitorId(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> existing = Optional.ofNullable(request.getCookies()).stream().flatMap(Arrays::stream)
                .filter(c -> COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> UUID_FORMAT.matcher(v).matches())
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        String id = UUID.randomUUID().toString();
        response.addHeader("Set-Cookie", ResponseCookie.from(COOKIE, id).path("/").httpOnly(true).sameSite("Lax")
                .maxAge(Duration.ofDays(365)).build().toString());
        return id;
    }
}
