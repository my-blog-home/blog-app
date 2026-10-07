package com.myblog.common.mail;

/**
 * 인증번호 메일 발송. 개발은 로그, 운영은 SMTP (research R-06, 헌법 V).
 */
public interface VerificationMailer {

    enum Purpose { SIGNUP, PASSWORD_RESET }

    /** 보내지 못하면 MailDeliveryException */
    void sendCode(String email, String code, Purpose purpose);

    class MailDeliveryException extends RuntimeException {
        public MailDeliveryException(Throwable cause) {
            super(cause);
        }
    }
}
