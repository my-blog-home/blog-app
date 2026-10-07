package com.myblog.common.security;

import java.io.Serializable;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * 세션에 저장되는 로그인 회원. 닉네임은 바뀔 수 있으므로 id만 둔다.
 * getName()이 회원 id라서 Spring Session이 회원별로 세션을 찾을 수 있다.
 */
public record MemberPrincipal(long memberId) implements AuthenticatedPrincipal, Serializable {

    @Override
    public String getName() {
        return String.valueOf(memberId);
    }
}
