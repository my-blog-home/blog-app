package com.myblog.user.service;

import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재가입 대기 (FR-087, BR-23). 같은 이메일은 탈퇴한 날(한국 날짜)부터 30일(blog.limits.rejoin-wait-days)이 지나야 다시 가입할 수 있다.
 * 탈퇴한 회원의 원래 이메일(original_email)은 대기 기간에만 보관하고, 매일 한 번 기간이 지난 것을 비운다(개인정보 파기).
 */
@Component
public class RejoinPolicy {

    private static final Logger log = LoggerFactory.getLogger(RejoinPolicy.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy. M. d.");

    private final NamedParameterJdbcTemplate jdbc;
    private final BlogLimits limits;
    private final Clock clock;
    private final ZoneId zone;

    public RejoinPolicy(NamedParameterJdbcTemplate jdbc, BlogLimits limits, Clock clock, @Value("${blog.zone}") String zone) {
        this.jdbc = jdbc;
        this.limits = limits;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    /** 대기 기간 안이면 409 REJOIN_WAIT과 가입할 수 있는 날을 알린다. 인증번호를 보내기 전과 가입할 때 둘 다 부른다 */
    public void check(String email) {
        List<Timestamp> withdrawn = jdbc.queryForList("""
                select max(withdrawn_at) from member
                where original_email is not null and lower(original_email) = lower(:email) and withdrawn_at is not null
                """, Map.of("email", email), Timestamp.class);
        if (withdrawn.isEmpty() || withdrawn.get(0) == null) {
            return;
        }
        LocalDate availableOn = withdrawn.get(0).toInstant().atZone(zone).toLocalDate().plusDays(limits.rejoinWaitDays());
        LocalDate today = clock.instant().atZone(zone).toLocalDate();
        if (today.isBefore(availableOn)) {
            throw new ApiException(ErrorCode.REJOIN_WAIT,
                    Messages.REJOIN_WAIT.formatted(limits.rejoinWaitDays(), DATE.format(availableOn)), List.of(),
                    Map.of("availableOn", availableOn.toString()));
        }
    }

    /** 매일 새벽: 대기 기간이 지난 탈퇴 회원의 원래 이메일을 지운다 */
    @Scheduled(cron = "0 50 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public int clearExpiredOriginalEmails() {
        LocalDate today = clock.instant().atZone(zone).toLocalDate();
        // 탈퇴한 날 + 대기 일수 <= 오늘 ⇔ 탈퇴 시각 < (오늘 - 대기 일수 + 1일)의 0시
        Timestamp cutoff = Timestamp.from(today.minusDays(limits.rejoinWaitDays() - 1L).atStartOfDay(zone).toInstant());
        int cleared = jdbc.update("""
                update member set original_email = null
                where original_email is not null and withdrawn_at is not null and withdrawn_at < :cutoff
                """, Map.of("cutoff", cutoff));
        log.info("재가입 대기가 끝난 탈퇴 회원의 원래 이메일 {}개 삭제", cleared);
        return cleared;
    }
}
