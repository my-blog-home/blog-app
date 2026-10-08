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
        int imagesPerPost,
        /** 햄버거 메뉴에 보여 줄 구독한 블로그 수 (FR-068) */
        int drawerSubscriptionMax,
        /** 지금 핫한 글: 가장 최근 공개 글의 날부터 거꾸로 며칠, 몇 개 (FR-072, BR-33) */
        int hotPostDays,
        int hotPostCount,
        /** 이번 주 인기 블로거: 가장 최근 공개 글의 날부터 거꾸로 며칠, 몇 개 (FR-074, BR-45) */
        int hotBloggerDays,
        int hotBloggerCount,
        /** 핫한 글·인기 블로거 점수에서 댓글 하나의 무게 (좋아요 + 댓글 × 5) */
        int hotCommentWeight,
        /** 실시간 인기 검색어: 순위 수, 집계 기간, 비교 시점, 기록하는 검색어 길이, 기록 보관 기간 (FR-073, BR-29, BR-31) */
        int popularKeywordCount,
        Duration popularKeywordWindow,
        Duration popularKeywordCompare,
        int searchLogKeywordMax,
        Duration searchLogRetention,
        /** 공지 상세의 같은 종류 다른 안내 수 (FR-077, BR-30) */
        int noticeRelatedCount) {
}
