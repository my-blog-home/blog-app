package com.myblog.user.social;

import java.io.Serializable;

/**
 * 소셜 로그인 제공자에게서 받은 사용자 정보를 제공자와 상관없이 같은 모양으로 맞춘 것.
 * 처음 가입하는 사람은 닉네임·약관 동의를 받는 동안 세션에 담아 두므로 Serializable이다.
 */
public record SocialProfile(String provider, String providerUserId, String email, boolean emailVerified,
                            String nickname) implements Serializable {
}
