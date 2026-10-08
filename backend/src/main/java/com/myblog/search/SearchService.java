package com.myblog.search;

import com.myblog.common.config.BlogLimits;
import com.myblog.common.sql.PostVisibilitySql;
import com.myblog.post.service.PostQueryService;
import com.myblog.post.service.SearchWords;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방문자에게 보이는 글(작성완료·공개·공개 분류)의 제목·본문에서 모든 단어가 들어 있는 글을 찾는다 (CF-11, research R-11, BR-03).
 * '#'으로 시작하는 검색어는 그 태그가 정확히 붙은 공개 글만 찾는다(대소문자 무시) (FR-070, BR-10).
 */
@Service
public class SearchService {

    private final PostQueryService postQuery;
    private final BlogLimits limits;

    public SearchService(PostQueryService postQuery, BlogLimits limits) {
        this.postQuery = postQuery;
        this.limits = limits;
    }

    @Transactional(readOnly = true)
    public PostQueryService.PageResult search(String rawQuery, int page) {
        String query = SearchWords.check(rawQuery, limits);
        if (query.startsWith("#")) {
            String tag = query.replaceFirst("^#+", "").strip();
            if (!tag.isEmpty()) {
                return postQuery.tagPosts(tag, page);
            }
        }
        Map<String, Object> params = new HashMap<>();
        String where = "where " + PostVisibilitySql.PUBLIC + "\n" + SearchWords.conditions(query, "p", false, params);
        return postQuery.searchPage(where, params, page);
    }
}
