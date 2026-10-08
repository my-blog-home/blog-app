package com.myblog.search;

import com.myblog.common.config.BlogLimits;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실시간 인기 검색어 (FR-073, BR-29, BR-31).
 * - 전체 검색의 검색어를 앞뒤 공백을 빼고 30자까지 기록한다. 같은 사람(회원 또는 브라우저 세션)의 같은 검색어는 24시간에 한 번만 센다.
 * - 순위: 최근 24시간 검색 횟수가 많은 순서 10개. 같으면 최근에 검색된 것이 위. 대소문자만 다르면 하나로 합치고 처음 쓰인 표기를 보인다.
 * - 변동: 1시간 전에 같은 방식(그때부터 거꾸로 24시간)으로 계산한 순위와 비교한다. 그때 10위 안에 없던 단어는 NEW.
 * - 이틀이 지난 기록은 매일 지운다.
 */
@Service
public class PopularKeywordService {

    public record Keyword(int rank, String keyword, int change, boolean isNew) {
    }

    /** asOf: 순위를 계산한 한국 시각 "HH:mm" */
    public record PopularKeywords(String asOf, List<Keyword> items) {
    }

    private record Ranked(String key, String keyword) {
    }

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final NamedParameterJdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final BlogLimits limits;
    private final Clock clock;
    private final ZoneId zone;

    public PopularKeywordService(NamedParameterJdbcTemplate jdbc, StringRedisTemplate redis, BlogLimits limits,
                                 Clock clock, @Value("${blog.zone}") String zone) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.limits = limits;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    /** searcherKey는 같은 사람을 알아보는 값(회원 id 또는 세션 쿠키). 비어 있으면 매번 센다 */
    @Transactional
    public void record(String rawQuery, String searcherKey) {
        String trimmed = rawQuery == null ? "" : rawQuery.strip();
        if (trimmed.isEmpty()) {
            return;
        }
        int max = limits.searchLogKeywordMax();
        String keyword = trimmed.codePointCount(0, trimmed.length()) > max
                ? trimmed.substring(0, trimmed.offsetByCodePoints(0, max)).strip()
                : trimmed;
        String key = keyword.toLowerCase(Locale.ROOT);
        if (searcherKey != null) {
            Boolean first = redis.opsForValue().setIfAbsent("search:seen:" + searcherKey + ":" + key, "1",
                    limits.popularKeywordWindow());
            if (!Boolean.TRUE.equals(first)) {
                return;
            }
        }
        jdbc.update("insert into search_log (keyword, keyword_key, searched_at) values (:keyword, :key, :now)",
                Map.of("keyword", keyword, "key", key, "now", Timestamp.from(clock.instant())));
    }

    @Transactional(readOnly = true)
    public PopularKeywords popular() {
        Instant now = clock.instant();
        List<Ranked> current = rank(now);
        List<Ranked> before = rank(now.minus(limits.popularKeywordCompare()));
        Map<String, Integer> beforeRank = new HashMap<>();
        for (int i = 0; i < before.size(); i++) {
            beforeRank.put(before.get(i).key(), i + 1);
        }
        List<Keyword> items = new java.util.ArrayList<>();
        for (int i = 0; i < current.size(); i++) {
            Ranked r = current.get(i);
            Integer prev = beforeRank.get(r.key());
            int rank = i + 1;
            items.add(new Keyword(rank, r.keyword(), prev == null ? 0 : prev - rank, prev == null));
        }
        return new PopularKeywords(HH_MM.format(now.atZone(zone)), items);
    }

    /** asOf 시점에서 거꾸로 24시간 동안의 순위 */
    private List<Ranked> rank(Instant asOf) {
        Duration window = limits.popularKeywordWindow();
        Map<String, Object> params = Map.of("from", Timestamp.from(asOf.minus(window)), "to", Timestamp.from(asOf),
                "limit", limits.popularKeywordCount());
        return jdbc.query("""
                select keyword_key,
                       (array_agg(keyword order by searched_at, id))[1] as keyword,
                       count(*) as cnt, max(searched_at) as last_at
                from search_log
                where searched_at > :from and searched_at <= :to
                group by keyword_key
                order by cnt desc, last_at desc, keyword_key
                limit :limit
                """, params, (rs, row) -> new Ranked(rs.getString("keyword_key"), rs.getString("keyword")));
    }

    /** 매일 새벽: 보관 기간(이틀)이 지난 검색 기록을 지운다 */
    @Scheduled(cron = "0 40 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public int cleanUp() {
        return jdbc.update("delete from search_log where searched_at < :cutoff",
                Map.of("cutoff", Timestamp.from(clock.instant().minus(limits.searchLogRetention()))));
    }
}
