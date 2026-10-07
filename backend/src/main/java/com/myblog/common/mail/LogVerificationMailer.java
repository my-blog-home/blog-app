package com.myblog.common.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "blog.mail.mode", havingValue = "log", matchIfMissing = true)
public class LogVerificationMailer implements VerificationMailer {

    private static final Logger log = LoggerFactory.getLogger(LogVerificationMailer.class);

    @Override
    public void sendCode(String email, String code, Purpose purpose) {
        log.info("[개발용 메일] {} 인증번호 {} → {}", purpose, code, email);
    }
}
