package com.myblog.common.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "blog.mail.mode", havingValue = "smtp")
public class SmtpVerificationMailer implements VerificationMailer {

    private final JavaMailSender sender;
    private final String from;

    public SmtpVerificationMailer(JavaMailSender sender, @Value("${blog.mail.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void sendCode(String email, String code, Purpose purpose) {
        String usage = purpose == Purpose.SIGNUP ? "회원가입" : "비밀번호 찾기";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("[내 블로그] " + usage + " 인증번호");
        message.setText("""
                내 블로그 %s 인증번호입니다.

                인증번호: %s
                유효 시간: 10분

                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(usage, code));
        try {
            sender.send(message);
        } catch (MailException e) {
            throw new MailDeliveryException(e);
        }
    }
}
