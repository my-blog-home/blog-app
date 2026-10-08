package com.myblog.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 원천 문서의 '기본값' 표를 한곳에 모은 값 (NF-08, research R-09).
 */
@ConfigurationProperties(prefix = "blog.limits")
public record BlogLimits(
        int nicknameMin,
        int nicknameMax,
        int passwordMin,
        int passwordMax,
        Duration verificationCodeTtl,
        Duration verificationResendInterval,
        int verificationDailyMax,
        int verificationMaxFailures,
        Duration verifiedTtl,
        int loginMaxFailures,
        Duration loginLockDuration,
        int blogNameMax,
        int blogDescriptionMax,
        int categoryNameMax,
        int categoryDescriptionMax,
        int postTitleMax,
        int postBodyMax,
        int pageSize,
        int excerptLength,
        int searchMin,
        int searchMax,
        int commentMax,
        Duration commentInterval,
        int tagsPerPost,
        int tagMax,
        int reportDetailMax,
        long imageMaxBytes,
        int imagesPerPost) {
}
