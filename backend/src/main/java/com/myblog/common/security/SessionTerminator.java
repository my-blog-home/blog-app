package com.myblog.common.security;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * 회원의 로그인 세션을 찾아 지운다 (다른 기기 끊기, 비밀번호 찾기, 탈퇴).
 */
@Component
public class SessionTerminator {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionTerminator(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    public void terminateAll(long memberId) {
        terminateAllExcept(memberId, null);
    }

    public void terminateAllExcept(long memberId, String keepSessionId) {
        sessions.findByPrincipalName(String.valueOf(memberId)).keySet().stream()
                .filter(id -> !id.equals(keepSessionId))
                .forEach(sessions::deleteById);
    }
}
