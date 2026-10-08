package com.myblog.user.service;

import com.myblog.common.error.Messages;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 정지된 회원이 로그인할 때 보는 안내 (FR-081, BR-49).
 * "정지된 계정입니다. {yyyy. M. d. HH:mm}까지 정지입니다. 사유: {사유}. 운영자에게 문의해 주세요"
 * 영구면 "영구 정지입니다.", 사유가 비어 있으면 사유 부분을 뺀다. 시각은 한국 시간이다.
 */
public final class SuspensionNotice {

    private static final DateTimeFormatter UNTIL = DateTimeFormatter.ofPattern("yyyy. M. d. HH:mm");

    private SuspensionNotice() {
    }

    public static String message(Instant until, String reason, ZoneId zone) {
        StringBuilder text = new StringBuilder(Messages.SUSPENDED_PREFIX).append(' ');
        if (until == null) {
            text.append(Messages.SUSPENDED_PERMANENT);
        } else {
            text.append(Messages.SUSPENDED_UNTIL.formatted(UNTIL.format(until.atZone(zone))));
        }
        if (reason != null && !reason.isBlank()) {
            text.append(' ').append(Messages.SUSPENDED_REASON.formatted(reason.strip()));
        }
        return text.append(' ').append(Messages.SUSPENDED_SUFFIX).toString();
    }
}
