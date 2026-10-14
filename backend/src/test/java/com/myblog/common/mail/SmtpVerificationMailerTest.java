package com.myblog.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpVerificationMailerTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final SmtpVerificationMailer mailer = new SmtpVerificationMailer(sender, "Ylog <ylog@example.com>");

    @Test
    void 인증번호를_메일로_보낸다() {
        mailer.sendCode("user@example.com", "123456", VerificationMailer.Purpose.SIGNUP);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        SimpleMailMessage message = sent.getValue();
        assertThat(message.getFrom()).isEqualTo("Ylog <ylog@example.com>");
        assertThat(message.getTo()).containsExactly("user@example.com");
        assertThat(message.getSubject()).isEqualTo("[Ylog] 회원가입 인증번호");
        assertThat(message.getText()).contains("123456");
    }

    @Test
    void 보내지_못하면_발송_실패로_알린다() {
        doThrow(new MailSendException("connect failed")).when(sender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> mailer.sendCode("user@example.com", "123456", VerificationMailer.Purpose.PASSWORD_RESET))
                .isInstanceOf(VerificationMailer.MailDeliveryException.class);
    }
}
