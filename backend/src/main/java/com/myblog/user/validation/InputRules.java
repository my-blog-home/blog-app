package com.myblog.user.validation;

import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.Messages;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 가입 입력 규칙. 화면도 같은 규칙으로 안내하지만 최종 판단은 서버가 한다 (CF-01-2~5, NF-02).
 */
@Component
public class InputRules {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern NICKNAME_CHARS = Pattern.compile("^[가-힣A-Za-z0-9]+$");
    private static final String SPECIALS = "!@#$%^&*()_+\\-=";
    /** 탈퇴한 회원의 닉네임(탈퇴{번호})과 겹치지 않도록 가입·수정에서 쓰지 못하게 한다 (FR-086) */
    private static final Pattern WITHDRAWN_NICKNAME = Pattern.compile("^탈퇴\\d+$");

    private final BlogLimits limits;
    private final Pattern password;

    public InputRules(BlogLimits limits) {
        this.limits = limits;
        this.password = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[" + SPECIALS + "])[A-Za-z\\d" + SPECIALS + "]{"
                + limits.passwordMin() + "," + limits.passwordMax() + "}$");
    }

    /** 앞뒤 공백을 지우고 소문자로 바꾼 이메일을 돌려준다 */
    public String normalizeEmail(String raw) {
        String email = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw ApiException.field("email", Messages.EMAIL_FORMAT);
        }
        return email;
    }

    public String checkNickname(String raw) {
        String nickname = raw == null ? "" : raw;
        int length = nickname.codePointCount(0, nickname.length());
        if (length < limits.nicknameMin() || length > limits.nicknameMax() || !NICKNAME_CHARS.matcher(nickname).matches()) {
            throw ApiException.field("nickname", Messages.NICKNAME_RULE);
        }
        if (WITHDRAWN_NICKNAME.matcher(nickname).matches()) {
            throw ApiException.field("nickname", Messages.NICKNAME_RESERVED);
        }
        return nickname;
    }

    public void checkPassword(String field, String password, String confirm) {
        if (password == null || !this.password.matcher(password).matches()) {
            throw ApiException.field(field, Messages.PASSWORD_RULE);
        }
        if (!password.equals(confirm)) {
            throw ApiException.field(field + "Confirm", Messages.PASSWORD_MISMATCH);
        }
    }
}
