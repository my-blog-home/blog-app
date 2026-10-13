package com.myblog.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myblog.stats.HomeRankingService.HotPost;
import com.myblog.support.IntegrationTestBase;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 짧은 캐시: 목록도 그대로 되살리고, 키(주제)마다 따로 담는다 */
class ShortCacheIT extends IntegrationTestBase {

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void 주제마다_따로_담고_목록을_그대로_되살린다() {
        ShortCache cache = new ShortCache(redis, objectMapper);
        AtomicInteger computed = new AtomicInteger();
        TypeReference<List<HotPost>> type = new TypeReference<>() {
        };
        HotPost post = new HotPost(1, "글", 2, "블로그", "분류", "작가", "#c9dcfb", 3, 4, 5, "여행");

        List<HotPost> all = cache.get("cache:hot-posts:all:3", Duration.ofMinutes(1), type, () -> {
            computed.incrementAndGet();
            return List.of(post);
        });
        List<HotPost> again = cache.get("cache:hot-posts:all:3", Duration.ofMinutes(1), type, () -> {
            computed.incrementAndGet();
            return List.of();
        });
        List<HotPost> otherTopic = cache.get("cache:hot-posts:5:3", Duration.ofMinutes(1), type, () -> {
            computed.incrementAndGet();
            return List.of();
        });

        assertThat(again).isEqualTo(all).containsExactly(post);
        assertThat(otherTopic).isEmpty();
        assertThat(computed).hasValue(2);
    }
}
