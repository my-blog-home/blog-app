package com.myblog.search;

import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.Messages;
import com.myblog.post.service.PostQueryService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 글의 제목·본문에서 모든 단어가 들어 있는 글을 찾는다 (CF-11, research R-11).
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
        String query = rawQuery == null ? "" : rawQuery.strip();
        int length = query.codePointCount(0, query.length());
        if (length < limits.searchMin()) {
            throw ApiException.field("q", Messages.SEARCH_TOO_SHORT);
        }
        if (length > limits.searchMax()) {
            throw ApiException.field("q", "검색어는 " + limits.searchMax() + "자 이하로 입력해 주세요");
        }
        StringBuilder where = new StringBuilder("where p.visibility = 'PUBLIC'\n");
        Map<String, Object> params = new HashMap<>();
        String[] words = query.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String name = "w" + i;
            params.put(name, "%" + escapeLike(words[i]) + "%");
            where.append("  and (p.title ilike :").append(name).append(" escape '\\'")
                    .append(" or p.body ilike :").append(name).append(" escape '\\')\n");
        }
        return postQuery.searchPage(where.toString(), params, page);
    }

    /** 검색어의 특수문자는 일반 글자로 취급한다 (CF-11-9) */
    static String escapeLike(String word) {
        return word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
