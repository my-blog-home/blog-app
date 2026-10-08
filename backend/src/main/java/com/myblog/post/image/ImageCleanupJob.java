package com.myblog.post.image;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 하루 한 번: 24시간이 지나도 글에 연결되지 않은 이미지와, DB 기록이 없는 파일(글 삭제·탈퇴 뒤 남은 것)을 지운다.
 */
@Component
public class ImageCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ImageCleanupJob.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final ImageStorage storage;
    private final Clock clock;

    public ImageCleanupJob(NamedParameterJdbcTemplate jdbc, ImageStorage storage, Clock clock) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void run() {
        Timestamp cutoff = Timestamp.from(clock.instant().minus(Duration.ofHours(24)));
        List<String> stale = jdbc.queryForList(
                "delete from post_image where post_id is null and created_at < :cutoff returning storage_key",
                Map.of("cutoff", cutoff), String.class);
        stale.forEach(storage::delete);

        Set<String> known = new HashSet<>(jdbc.queryForList("select storage_key from post_image", Map.of(), String.class));
        long orphans = storage.keys().filter(key -> !known.contains(key)).peek(storage::delete).count();
        log.info("이미지 정리: 연결 안 된 이미지 {}개, 기록 없는 파일 {}개 삭제", stale.size(), orphans);
    }
}
