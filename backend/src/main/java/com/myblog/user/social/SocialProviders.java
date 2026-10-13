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
 * - 카카오: 국내 사용자가 많다. 이메일을 받으려면 카카오 앱을 "개인 개발자 비즈 앱"으로 바꾸고 이메일 동의항목을 켜야 한다.
 * - 구글: 콜백 주소가 https여야 하고 IP 주소는 받지 않아서, 도메인과 HTTPS를 붙인 뒤 여기에 더한다.
 */
@Component
public class SocialProviders {

    public static final String GITHUB = "github";
    public static final String KAKAO = "kakao";
    private static final String REDIRECT = "{baseUrl}/login/oauth2/code/{registrationId}";

    private final List<ClientRegistration> registrations = new ArrayList<>();

    public SocialProviders(@Value("${blog.social.github.client-id:}") String githubId,
                           @Value("${blog.social.github.client-secret:}") String githubSecret,
                           @Value("${blog.social.kakao.client-id:}") String kakaoId,
                           @Value("${blog.social.kakao.client-secret:}") String kakaoSecret) {
        if (!githubId.isBlank() && !githubSecret.isBlank()) {
            registrations.add(CommonOAuth2Provider.GITHUB.getBuilder(GITHUB)
                    .clientId(githubId).clientSecret(githubSecret)
                    .scope("read:user", "user:email")
                    .redirectUri(REDIRECT)
                    .build());
        }
        // 카카오는 "클라이언트 시크릿"을 켜고 그 값을 함께 넣어야 한다
        if (!kakaoId.isBlank() && !kakaoSecret.isBlank()) {
            ClientRegistration.Builder kakao = ClientRegistration.withRegistrationId(KAKAO)
                    .clientId(kakaoId)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(REDIRECT)
                    .scope("profile_nickname", "account_email")
                    .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                    .tokenUri("https://kauth.kakao.com/oauth/token")
                    .userInfoUri("https://kapi.kakao.com/v2/user/me")
                    .userNameAttributeName("id")
                    .clientName("카카오")
                    .clientSecret(kakaoSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
            registrations.add(kakao.build());
        }
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
