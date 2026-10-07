package com.myblog.common.security;

import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentMember {

    private CurrentMember() {
    }

    /** 로그인했으면 회원 id, 아니면 비어 있음 */
    public static Optional<Long> idIfPresent() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof MemberPrincipal principal) {
            return Optional.of(principal.memberId());
        }
        return Optional.empty();
    }

    /** 로그인하지 않았으면 401 */
    public static long id() {
        return idIfPresent().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, Messages.LOGIN_REQUIRED));
    }
}
