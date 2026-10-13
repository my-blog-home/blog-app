package com.myblog.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myblog.common.cache.ShortCache;
import com.myblog.search.PopularKeywordService.Keyword;
import com.myblog.search.PopularKeywordService.PopularKeywords;
import com.myblog.support.IntegrationTestBase;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 인기 검색어 순위 캐시: 새 값이면 DB 집계를 건너뛰고, 끝나면 한 번만 다시 계산한다 */
class PopularKeywordCacheIT extends IntegrationTestBase {

    @Autowired
    ObjectMapper objectMapper;

    private final AtomicInteger computed = new AtomicInteger();

    private Supplier<PopularKeywords> compute(String keyword) {
        return () -> {
            computed.incrementAndGet();
            return new PopularKeywords("09:00", List.of(new Keyword(1, keyword, 0, true)));
        };
    }

    @Test
    void 새_값이_있으면_DB에서_다시_집계하지_않는다() {
        PopularKeywordCache cache = new PopularKeywordCache(new ShortCache(redis, objectMapper), Duration.ofMinutes(10));
        PopularKeywords first = cache.get(compute("자바"));
        PopularKeywords second = cache.get(compute("다른 값"));
        assertThat(computed).hasValue(1);
        assertThat(second).isEqualTo(first);
        assertThat(second.items().get(0).keyword()).isEqualTo("자바");
    }

    @Test
    void 새_값이_끝나면_다시_계산하고_계산_중에는_조금_지난_값을_준다() {
        PopularKeywordCache cache = new PopularKeywordCache(new ShortCache(redis, objectMapper), Duration.ofMinutes(10));
        cache.get(compute("옛 순위"));
        redis.delete(PopularKeywordCache.FRESH);

        // 다른 요청이 다시 계산하는 중(잠금이 있음)이면 조금 지난 값을 바로 준다
        redis.opsForValue().set(PopularKeywordCache.LOCK, "1");
        assertThat(cache.get(compute("새 순위")).items().get(0).keyword()).isEqualTo("옛 순위");
        assertThat(computed).hasValue(1);

        // 잠금이 풀리면 한 요청이 다시 계산해 새 값으로 바꾼다
        redis.delete(PopularKeywordCache.LOCK);
        assertThat(cache.get(compute("새 순위")).items().get(0).keyword()).isEqualTo("새 순위");
        assertThat(computed).hasValue(2);
        assertThat(redis.hasKey(PopularKeywordCache.LOCK)).isFalse();
    }

    @Test
    void ttl이_0이면_캐시를_쓰지_않는다() {
        PopularKeywordCache cache = new PopularKeywordCache(new ShortCache(redis, objectMapper), Duration.ZERO);
        cache.get(compute("a"));
        cache.get(compute("b"));
        assertThat(computed).hasValue(2);
        assertThat(redis.hasKey(PopularKeywordCache.KEY)).isFalse();
    }
}
