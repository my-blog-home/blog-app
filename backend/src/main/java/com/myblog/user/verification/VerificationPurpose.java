package com.myblog.user.verification;

import com.myblog.common.mail.VerificationMailer;

public enum VerificationPurpose {
    SIGNUP("signup", VerificationMailer.Purpose.SIGNUP),
    RESET("reset", VerificationMailer.Purpose.PASSWORD_RESET);

    private final String key;
    private final VerificationMailer.Purpose mailPurpose;

    VerificationPurpose(String key, VerificationMailer.Purpose mailPurpose) {
        this.key = key;
        this.mailPurpose = mailPurpose;
    }

    public String key() {
        return key;
    }

    public VerificationMailer.Purpose mailPurpose() {
        return mailPurpose;
    }
}
