package com.myblog.user.service;

import com.myblog.common.error.ApiException;
import com.myblog.user.domain.Member;
import com.myblog.user.domain.MemberRepository;
import com.myblog.user.domain.MemberRole;
import com.myblog.user.validation.InputRules;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 관리자 계정 준비 (FR-078, BR-47, CR-65). 관리자는 가입으로 만들 수 없으므로, 서버를 켤 때
 * blog.admin.email·blog.admin.password가 설정되어 있고 그 이메일의 회원이 없으면 관리자를 하나 만든다.
 * 닉네임은 "운영자"이고 블로그는 만들지 않는다. 값은 환경변수 ADMIN_EMAIL·ADMIN_PASSWORD로 받고(기본은 빈 값),
 * 비밀번호는 저장소에 두지 않는다. 이미 있는 회원은 바꾸지 않는다.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    static final String NICKNAME = "운영자";
    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final MemberRepository members;
    private final InputRules rules;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String email;
    private final String password;

    public AdminAccountInitializer(MemberRepository members, InputRules rules, PasswordEncoder passwordEncoder,
                                   Clock clock, @Value("${blog.admin.email:}") String email,
                                   @Value("${blog.admin.password:}") String password) {
        this.members = members;
        this.rules = rules;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        ensureAdmin(email, password);
    }

    /** 만들었으면 참. 이미 같은 이메일의 회원이 있거나 값이 규칙에 맞지 않으면 만들지 않는다 */
    public boolean ensureAdmin(String rawEmail, String rawPassword) {
        String normalized;
        try {
            normalized = rules.normalizeEmail(rawEmail);
            rules.checkPassword("password", rawPassword, rawPassword);
        } catch (ApiException e) {
            log.warn("관리자 계정 설정이 규칙에 맞지 않아 만들지 않습니다: {}", e.getMessage());
            return false;
        }
        if (members.existsByEmail(normalized)) {
            return false;
        }
        String nickname = NICKNAME;
        for (int i = 1; members.existsByNickname(nickname); i++) {
            nickname = NICKNAME + i;
        }
        members.saveAndFlush(new Member(normalized, nickname, passwordEncoder.encode(rawPassword), MemberRole.ADMIN,
                clock.instant()));
        log.info("관리자 계정을 만들었습니다: {}", normalized);
        return true;
    }
}
