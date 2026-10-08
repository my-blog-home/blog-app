package com.myblog.search;

import com.myblog.common.security.CurrentMember;
import com.myblog.post.service.PostQueryService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

    /** 브라우저를 닫으면 사라지는 쿠키. 같은 세션의 같은 검색어를 한 번만 세는 데 쓴다 (BR-29) */
    static final String SEARCH_SESSION_COOKIE = "ssid";
    private static final Pattern UUID_FORMAT = Pattern.compile("^[0-9a-f\\-]{36}$");

    private final SearchService searchService;
    private final PopularKeywordService popularKeywords;

    public SearchController(SearchService searchService, PopularKeywordService popularKeywords) {
        this.searchService = searchService;
        this.popularKeywords = popularKeywords;
    }

    /** 규칙에 맞는 검색만 인기 검색어에 기록한다 (FR-073) */
    @GetMapping("/api/search")
    public PostQueryService.PageResult search(@RequestParam(defaultValue = "") String q,
                                              @RequestParam(defaultValue = "1") int page,
                                              HttpServletRequest request, HttpServletResponse response) {
        PostQueryService.PageResult result = searchService.search(q, page);
        popularKeywords.record(q, searcherKey(request, response));
        return result;
    }

    @GetMapping("/api/search/popular-keywords")
    public PopularKeywordService.PopularKeywords popular() {
        return popularKeywords.popular();
    }

    /** 회원은 회원 id, 비회원은 세션 쿠키로 같은 사람을 알아본다 */
    private static String searcherKey(HttpServletRequest request, HttpServletResponse response) {
        Optional<Long> member = CurrentMember.idIfPresent();
        if (member.isPresent()) {
            return "m:" + member.get();
        }
        Optional<String> existing = Optional.ofNullable(request.getCookies()).stream().flatMap(Arrays::stream)
                .filter(c -> SEARCH_SESSION_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> UUID_FORMAT.matcher(v).matches())
                .findFirst();
        String id = existing.orElseGet(() -> {
            String created = UUID.randomUUID().toString();
            response.addHeader("Set-Cookie", ResponseCookie.from(SEARCH_SESSION_COOKIE, created).path("/")
                    .httpOnly(true).sameSite("Lax").build().toString());
            return created;
        });
        return "s:" + id;
    }
}
