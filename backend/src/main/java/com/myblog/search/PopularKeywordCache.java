package com.myblog.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myblog.search.PopularKeywordService.PopularKeywords;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 실시간 인기 검색어 순위를 Redis에 잠깐 담아 둔다. 순위는 몇 분에 한 번 바뀌어도 되는데,
 * 첫 화면을 열 때마다 최근 24시간 검색 기록을 두 번 집계하면 DB가 같은 일을 계속 반복하기 때문이다.
 *
 * - 순위는 ttl 동안 "새 값"으로 쓰고, 그 두 배까지는 "조금 지난 값"으로 남겨 둔다.
 * - 새 값이 끝나면 한 요청만 잠금을 잡고 다시 계산한다. 그동안 다른 요청은 조금 지난 값을 받아
 *   여러 요청이 한꺼번에 DB로 몰리지 않게 한다(캐시 스탬피드 방지).
 * - Redis가 멈추면 캐시 없이 DB에서 바로 계산한다. ttl이 0이면 캐시를 쓰지 않는다(테스트).
 */
@Component
public class PopularKeywordCache {

    private static final Logger log = LoggerFactory.getLogger(PopularKeywordCache.class);

    static final String KEY = "cache:popular-keywords";
    static final String FRESH = KEY + ":fresh";
    static final String LOCK = KEY + ":lock";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration ttl;

    public PopularKeywordCache(StringRedisTemplate redis, ObjectMapper json,
                               @Value("${blog.cache.popular-keywords-ttl:10m}") Duration ttl) {
        this.redis = redis;
        this.json = json;
        this.ttl = ttl;
    }

    public PopularKeywords get(Supplier<PopularKeywords> compute) {
        if (ttl.isZero()) {
            return compute.get();
        }
        String cached;
        boolean fresh;
        boolean locked;
        try {
            cached = redis.opsForValue().get(KEY);
            fresh = Boolean.TRUE.equals(redis.hasKey(FRESH));
            PopularKeywords value = read(cached);
            if (value != null && fresh) {
                return value;
            }
            locked = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(LOCK, "1", LOCK_TTL));
            if (!locked && value != null) {
                return value;
            }
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.warn("인기 검색어 캐시를 읽지 못해 DB에서 계산합니다: {}", e.getMessage());
            return compute.get();
        }

        PopularKeywords computed = compute.get();
        try {
            redis.opsForValue().set(KEY, json.writeValueAsString(computed), ttl.multipliedBy(2));
            redis.opsForValue().set(FRESH, "1", ttl);
            if (locked) {
                redis.delete(LOCK);
            }
        } catch (RedisConnectionFailureException | RedisSystemException | JsonProcessingException e) {
            log.warn("인기 검색어 캐시를 저장하지 못했습니다: {}", e.getMessage());
        }
        return computed;
    }

    private PopularKeywords read(String cached) {
        if (cached == null) {
            return null;
        }
        try {
            return json.readValue(cached, PopularKeywords.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
