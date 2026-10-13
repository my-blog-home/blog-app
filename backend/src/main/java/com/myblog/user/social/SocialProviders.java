package com.myblog.user.social;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;

/**
 * 쓸 수 있는 소셜 로그인 제공자. 클라이언트 ID·시크릿이 설정된 제공자만 켠다.
 * 아무것도 설정되지 않으면 소셜 로그인이 꺼지고 화면에도 버튼이 나오지 않는다.
 *
 * - GitHub: http 주소와 IP 주소도 콜백으로 등록할 수 있어 지금 서버에서 바로 쓸 수 있다.
 * - 카카오: 국내 사용자가 많다. 이메일을 받으려면 비즈 앱 전환이 필요해서 이메일 없이 닉네임만 받는다(profile_nickname).
 *   그래서 카카오 회원은 카카오 회원번호로만 알아보고, 이메일로 기존 계정에 연결하지 않는다.
 * - 구글: 콜백 주소가 https 도메인이어야 한다(IP·http 불가). 공개 주소 https://ylog.java21.net으로 등록하면 쓸 수 있다.
 */
@Component
public class SocialProviders {

    public static final String GITHUB = "github";
    public static final String KAKAO = "kakao";
    public static final String GOOGLE = "google";
    private static final String CALLBACK = "/login/oauth2/code/{registrationId}";

    private final List<ClientRegistration> registrations = new ArrayList<>();

    public SocialProviders(@Value("${blog.public-url:}") String publicUrl,
                           @Value("${blog.social.github.client-id:}") String githubId,
                           @Value("${blog.social.github.client-secret:}") String githubSecret,
                           @Value("${blog.social.kakao.client-id:}") String kakaoId,
                           @Value("${blog.social.kakao.client-secret:}") String kakaoSecret,
                           @Value("${blog.social.google.client-id:}") String googleId,
                           @Value("${blog.social.google.client-secret:}") String googleSecret) {
        // 공개 주소(예: https://ylog.java21.net)가 정해져 있으면 콜백을 그 주소로 고정한다. 앞단 프록시가 원래 주소를
        // 전달하지 않아도 제공자에 등록한 콜백과 정확히 맞게 하기 위해서다. 없으면 요청 주소로 만든다
        String redirect = (publicUrl.isBlank() ? "{baseUrl}" : publicUrl.replaceAll("/+$", "")) + CALLBACK;
        if (!githubId.isBlank() && !githubSecret.isBlank()) {
            registrations.add(CommonOAuth2Provider.GITHUB.getBuilder(GITHUB)
                    .clientId(githubId).clientSecret(githubSecret)
                    .scope("read:user", "user:email")
                    .redirectUri(redirect)
                    .build());
        }
        // 카카오는 "클라이언트 시크릿"을 켜고 그 값을 함께 넣어야 한다
        if (!kakaoId.isBlank() && !kakaoSecret.isBlank()) {
            ClientRegistration.Builder kakao = ClientRegistration.withRegistrationId(KAKAO)
                    .clientId(kakaoId)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(redirect)
                    .scope("profile_nickname")
                    .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                    .tokenUri("https://kauth.kakao.com/oauth/token")
                    .userInfoUri("https://kapi.kakao.com/v2/user/me")
                    .userNameAttributeName("id")
                    .clientName("카카오")
                    .clientSecret(kakaoSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
            registrations.add(kakao.build());
        }
        if (!googleId.isBlank() && !googleSecret.isBlank()) {
            // openid를 빼고 OAuth2 사용자 정보(이메일·인증 여부·이름)만 받아 다른 제공자와 같은 흐름으로 처리한다
            registrations.add(CommonOAuth2Provider.GOOGLE.getBuilder(GOOGLE)
                    .clientId(googleId).clientSecret(googleSecret)
                    .scope("profile", "email")
                    .redirectUri(redirect)
                    .build());
        }
    }

    /** 이메일 없이 가입할 수 있는 제공자(제공자 회원번호로만 알아본다) */
    public static boolean allowsNoEmail(String provider) {
        return KAKAO.equals(provider);
    }

    /** 켜진 제공자 이름 (화면이 버튼을 그릴 때 쓴다) */
    public List<String> enabled() {
        return registrations.stream().map(ClientRegistration::getRegistrationId).toList();
    }

    public Optional<ClientRegistrationRepository> repository() {
        return registrations.isEmpty() ? Optional.empty()
                : Optional.of(new InMemoryClientRegistrationRepository(registrations));
    }
}
