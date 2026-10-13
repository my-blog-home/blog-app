package com.myblog.user.social;

import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import com.myblog.user.web.SessionLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 소셜 로그인: 켜진 제공자 목록, 처음 가입 마무리(닉네임·약관 동의) */
@RestController
public class SocialController {

    public record Pending(String provider, String email, String suggestedNickname) {
    }

    public record CompleteRequest(String nickname, boolean agreed) {
    }

    private final SocialProviders providers;
    private final SocialLoginService social;
    private final SessionLogin sessionLogin;

    public SocialController(SocialProviders providers, SocialLoginService social, SessionLogin sessionLogin) {
        this.providers = providers;
        this.social = social;
        this.sessionLogin = sessionLogin;
    }

    /** 화면은 이 목록에 있는 제공자만 버튼으로 보인다. 설정이 없으면 빈 목록 */
    @GetMapping("/api/auth/social/providers")
    public Map<String, List<String>> providers() {
        return Map.of("providers", providers.enabled());
    }

    @GetMapping("/api/auth/social/pending")
    public Pending pending(HttpServletRequest request) {
        SocialProfile profile = pendingProfile(request.getSession(false));
        String nickname = (String) request.getSession().getAttribute(SocialAuthHandlers.PENDING_NICKNAME);
        return new Pending(profile.provider(), profile.email(), nickname == null ? "" : nickname);
    }

    @PostMapping("/api/auth/social/complete")
    public Map<String, Long> complete(@RequestBody CompleteRequest body, HttpServletRequest request,
                                      HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        SocialProfile profile = pendingProfile(session);
        long memberId = social.completeSignup(profile, body.nickname(), body.agreed());
        session.removeAttribute(SocialAuthHandlers.PENDING);
        session.removeAttribute(SocialAuthHandlers.PENDING_NICKNAME);
        sessionLogin.start(memberId, request, response);
        return Map.of("memberId", memberId);
    }

    private static SocialProfile pendingProfile(HttpSession session) {
        Object pending = session == null ? null : session.getAttribute(SocialAuthHandlers.PENDING);
        if (!(pending instanceof SocialProfile profile)) {
            throw new ApiException(ErrorCode.NOT_FOUND, Messages.SOCIAL_SIGNUP_EXPIRED);
        }
        return profile;
    }
}
