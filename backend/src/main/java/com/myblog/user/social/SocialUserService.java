package com.myblog.user.social;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 제공자마다 다른 사용자 정보를 SocialProfile 모양(속성 "profile")으로 맞춘다.
 * - GitHub: 이메일을 공개하지 않은 사람이 많아 /user/emails에서 인증된 대표 이메일을 따로 받는다.
 * - 카카오: kakao_account.email과 이메일 인증 여부(is_email_verified, is_email_valid)를 쓴다.
 * - 구글: email과 email_verified를 쓴다.
 */
@Component
public class SocialUserService extends DefaultOAuth2UserService {

    static final String PROFILE = "profile";

    private final RestClient github = RestClient.builder().baseUrl("https://api.github.com").build();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(request);
        String provider = request.getClientRegistration().getRegistrationId();
        SocialProfile profile = switch (provider) {
            case SocialProviders.GITHUB -> github(user, request.getAccessToken().getTokenValue());
            case SocialProviders.KAKAO -> kakao(user);
            case SocialProviders.GOOGLE -> google(user);
            default -> throw new IllegalStateException("모르는 제공자: " + provider);
        };
        Map<String, Object> attributes = new HashMap<>(user.getAttributes());
        attributes.put(PROFILE, profile);
        String nameKey = request.getClientRegistration().getProviderDetails().getUserInfoEndpoint()
                .getUserNameAttributeName();
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_SOCIAL")), attributes, nameKey);
    }

    private SocialProfile github(OAuth2User user, String token) {
        String id = String.valueOf(user.getAttributes().get("id"));
        String nickname = (String) user.getAttributes().get("login");
        String email = null;
        boolean verified = false;
        try {
            List<Map<String, Object>> emails = github.get().uri("/user/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve().body(new ParameterizedTypeReference<>() {
                    });
            if (emails != null) {
                for (Map<String, Object> e : emails) {
                    if (Boolean.TRUE.equals(e.get("primary")) && Boolean.TRUE.equals(e.get("verified"))) {
                        email = (String) e.get("email");
                        verified = true;
                    }
                }
            }
        } catch (RestClientException e) {
            // 이메일을 못 받으면 가입할 수 없다고 안내한다 (SocialLoginService)
        }
        return new SocialProfile(SocialProviders.GITHUB, id, email, verified, nickname);
    }

    private SocialProfile google(OAuth2User user) {
        Object verified = user.getAttributes().get("email_verified");
        return new SocialProfile(SocialProviders.GOOGLE, String.valueOf(user.getAttributes().get("sub")),
                (String) user.getAttributes().get("email"),
                Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified)),
                (String) user.getAttributes().get("name"));
    }

    @SuppressWarnings("unchecked")
    private SocialProfile kakao(OAuth2User user) {
        String id = String.valueOf(user.getAttributes().get("id"));
        Map<String, Object> account = (Map<String, Object>) user.getAttributes().getOrDefault("kakao_account", Map.of());
        Map<String, Object> profile = (Map<String, Object>) account.getOrDefault("profile", Map.of());
        String email = (String) account.get("email");
        boolean verified = Boolean.TRUE.equals(account.get("is_email_verified"))
                && Boolean.TRUE.equals(account.get("is_email_valid"));
        return new SocialProfile(SocialProviders.KAKAO, id, email, verified, (String) profile.get("nickname"));
    }
}
