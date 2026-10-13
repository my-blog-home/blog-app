package com.myblog.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 몇 분에 한 번 바뀌어도 되는 계산 결과(순위 등)를 Redis에 JSON으로 잠깐 담아 둔다.
 *
 * - ttl 동안은 "새 값"으로 쓰고, 그 두 배까지는 "조금 지난 값"으로 남겨 둔다.
 * - 새 값이 끝나면 한 요청만 잠금(SET NX)을 잡고 다시 계산한다. 그동안 다른 요청은 조금 지난 값을 받아
 *   여러 요청이 한꺼번에 DB로 몰리지 않게 한다(캐시 스탬피드 방지).
 * - Redis가 멈추면 캐시 없이 바로 계산한다. ttl이 0이면 캐시를 쓰지 않는다(테스트).
 */
@Component
public class ShortCache {

    private static final Logger log = LoggerFactory.getLogger(ShortCache.class);
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public ShortCache(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public static String fresh(String key) {
        return key + ":fresh";
    }

    public static String lock(String key) {
        return key + ":lock";
    }

    public <T> T get(String key, Duration ttl, TypeReference<T> type, Supplier<T> compute) {
        if (ttl.isZero()) {
            return compute.get();
        }
        boolean locked;
        try {
            T value = read(redis.opsForValue().get(key), type);
            if (value != null && Boolean.TRUE.equals(redis.hasKey(fresh(key)))) {
                return value;
            }
            locked = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lock(key), "1", LOCK_TTL));
            if (!locked && value != null) {
                return value;
            }
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.warn("캐시({})를 읽지 못해 바로 계산합니다: {}", key, e.getMessage());
            return compute.get();
        }

        T computed = compute.get();
        try {
            redis.opsForValue().set(key, json.writeValueAsString(computed), ttl.multipliedBy(2));
            redis.opsForValue().set(fresh(key), "1", ttl);
            if (locked) {
                redis.delete(lock(key));
            }
        } catch (RedisConnectionFailureException | RedisSystemException | JsonProcessingException e) {
            log.warn("캐시({})를 저장하지 못했습니다: {}", key, e.getMessage());
        }
        return computed;
    }

    private <T> T read(String cached, TypeReference<T> type) {
        if (cached == null) {
            return null;
        }
        try {
            return json.readValue(cached, type);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
