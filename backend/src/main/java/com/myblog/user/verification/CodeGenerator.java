package com.myblog.user.verification;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 헷갈리는 O, 0, I, 1을 뺀 영문 대문자·숫자 6자리 (CF-01-12, NF-05).
 */
@Component
public class CodeGenerator {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
