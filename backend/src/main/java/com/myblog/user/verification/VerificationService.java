package com.myblog.user.verification;

import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import com.myblog.common.mail.VerificationMailer;
import java.time.Duration;
import java.util.Locale;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 이메일 인증번호를 Redis에 두고 만료·1회용·횟수 제한을 지킨다 (research R-04, CF-01-11~20, CF-25-4).
 */
@Service
public class VerificationService {

    private final StringRedisTemplate redis;
    private final CodeGenerator codes;
    private final VerificationMailer mailer;
    private final BlogLimits limits;

    public VerificationService(StringRedisTemplate redis, CodeGenerator codes, VerificationMailer mailer, BlogLimits limits) {
        this.redis = redis;
        this.codes = codes;
        this.mailer = mailer;
        this.limits = limits;
    }

    /**
     * 인증번호를 새로 만든다. {@code deliver}가 false이면 (가입되지 않은 이메일의 비밀번호 찾기)
     * 메일은 보내지 않지만, 제한과 화면 흐름은 똑같이 만들려고 아무도 모르는 번호를 저장한다 (CF-25-3).
     */
    public void issue(VerificationPurpose purpose, String email, boolean deliver) {
        try {
            if (Boolean.TRUE.equals(redis.hasKey(key(purpose, email, "cooldown")))) {
                throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, Messages.RESEND_TOO_SOON);
            }
            String dailyKey = key(purpose, email, "daily");
            String daily = redis.opsForValue().get(dailyKey);
            if (daily != null && Integer.parseInt(daily) >= limits.verificationDailyMax()) {
                throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, Messages.RESEND_DAILY_LIMIT);
            }

            redis.opsForValue().set(key(purpose, email, "cooldown"), "1", limits.verificationResendInterval());
            Long count = redis.opsForValue().increment(dailyKey);
            if (count != null && count == 1) {
                redis.expire(dailyKey, Duration.ofDays(1));
            }

            String code = codes.next();
            redis.opsForValue().set(key(purpose, email, "code"), code, limits.verificationCodeTtl());
            redis.delete(key(purpose, email, "fail"));
            redis.delete(key(purpose, email, "verified"));

            if (deliver) {
                try {
                    mailer.sendCode(email, code, purpose.mailPurpose());
                } catch (VerificationMailer.MailDeliveryException e) {
                    redis.delete(key(purpose, email, "code"));
                    redis.delete(key(purpose, email, "cooldown"));
                    throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, Messages.MAIL_FAILED);
                }
            }
        } catch (DataAccessException e) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, Messages.TRY_LATER);
        }
    }

    /** 맞으면 번호를 지우고 "인증됨" 표시를 30분 남긴다 */
    public void confirm(VerificationPurpose purpose, String email, String input) {
        String codeKey = key(purpose, email, "code");
        String failKey = key(purpose, email, "fail");
        String expected = redis.opsForValue().get(codeKey);
        if (expected == null) {
            throw new ApiException(ErrorCode.CODE_GONE, Messages.CODE_EXPIRED);
        }
        String normalized = input == null ? "" : input.strip().toUpperCase(Locale.ROOT);
        if (!expected.equals(normalized)) {
            Long failures = redis.opsForValue().increment(failKey);
            Long ttl = redis.getExpire(codeKey);
            if (ttl != null && ttl > 0) {
                redis.expire(failKey, Duration.ofSeconds(ttl));
            }
            if (failures != null && failures >= limits.verificationMaxFailures()) {
                redis.delete(codeKey);
                redis.delete(failKey);
                throw new ApiException(ErrorCode.CODE_GONE, Messages.CODE_TOO_MANY_FAILURES);
            }
            throw ApiException.field("code", Messages.CODE_WRONG);
        }
        redis.delete(codeKey);
        redis.delete(failKey);
        redis.opsForValue().set(key(purpose, email, "verified"), "1", limits.verifiedTtl());
    }

    /** 가입·변경 직전에 서버가 인증을 다시 확인한다 (CF-01-20) */
    public void requireVerified(VerificationPurpose purpose, String email) {
        if (!Boolean.TRUE.equals(redis.hasKey(key(purpose, email, "verified")))) {
            throw new ApiException(ErrorCode.NOT_VERIFIED, Messages.VERIFY_FIRST);
        }
    }

    public void clearVerified(VerificationPurpose purpose, String email) {
        redis.delete(key(purpose, email, "verified"));
    }

    static String key(VerificationPurpose purpose, String email, String suffix) {
        return "verify:" + purpose.key() + ":" + email + ":" + suffix;
    }
}
