package com.myblog.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.myblog.common.cache.ShortCache;
import com.myblog.search.PopularKeywordService.PopularKeywords;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 실시간 인기 검색어 순위를 Redis에 잠깐 담아 둔다(ShortCache). 순위는 몇 분에 한 번 바뀌어도 되는데,
 * 첫 화면을 열 때마다 최근 24시간 검색 기록을 두 번 집계하면 DB가 같은 일을 계속 반복하기 때문이다.
 */
@Component
public class PopularKeywordCache {

    static final String KEY = "cache:popular-keywords";
    static final String FRESH = ShortCache.fresh(KEY);
    static final String LOCK = ShortCache.lock(KEY);

    private final ShortCache cache;
    private final Duration ttl;

    public PopularKeywordCache(ShortCache cache, @Value("${blog.cache.popular-keywords-ttl:10m}") Duration ttl) {
        this.cache = cache;
        this.ttl = ttl;
    }

    public PopularKeywords get(Supplier<PopularKeywords> compute) {
        return cache.get(KEY, ttl, new TypeReference<>() {
        }, compute);
    }
}
