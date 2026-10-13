package com.myblog.user.social;

import com.myblog.user.web.SessionLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 제공자에서 돌아온 뒤: 우리 규칙(SocialLoginService)으로 판단해 로그인하거나, 처음 가입 화면으로 보내거나, 안내와 함께 로그인 화면으로 보낸다.
 * 제공자 인증 정보(OAuth2 토큰)는 세션에 남기지 않고, 이메일 로그인과 같은 우리 세션으로 바꾼다.
 */
@Component
public class SocialAuthHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    /** 처음 가입하는 사람의 소셜 정보를 닉네임·약관 동의를 받는 동안 담아 두는 세션 이름 */
    public static final String PENDING = "socialSignupPending";
    public static final String PENDING_NICKNAME = "socialSignupNickname";

    private static final Logger log = LoggerFactory.getLogger(SocialAuthHandlers.class);

    private final SocialLoginService social;
    private final SessionLogin sessionLogin;

    public SocialAuthHandlers(SocialLoginService social, SessionLogin sessionLogin) {
        this.social = social;
        this.sessionLogin = sessionLogin;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        SocialProfile profile = (SocialProfile) ((OAuth2User) authentication.getPrincipal())
                .getAttributes().get(SocialUserService.PROFILE);
        SecurityContextHolder.clearContext();
        var session = request.getSession();
        session.removeAttribute(PENDING);
        session.removeAttribute(PENDING_NICKNAME);
        switch (social.login(profile)) {
            case SocialLoginService.LoggedIn in -> {
                sessionLogin.start(in.memberId(), request, response);
                response.sendRedirect(in.linkedNow() ? "/?social=linked" : "/");
            }
            case SocialLoginService.NeedsSignup needs -> {
                // 닉네임·약관 동의를 받기 전이라 로그인은 아직 아니다
                request.getSession().setAttribute(PENDING, needs.profile());
                request.getSession().setAttribute(PENDING_NICKNAME, needs.suggestedNickname());
                response.sendRedirect("/signup/social");
            }
            case SocialLoginService.Rejected rejected -> response.sendRedirect("/login?socialError=" + rejected.reason());
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("소셜 로그인 실패: {}", exception.getMessage());
        response.sendRedirect("/login?socialError=failed");
    }
}
