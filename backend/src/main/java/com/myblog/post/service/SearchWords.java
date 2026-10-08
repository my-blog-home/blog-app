package com.myblog.post.service;

import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.Messages;
import java.util.Map;

/**
 * 검색어 규칙: 앞뒤 공백을 빼고 2~50자, 공백으로 나눈 모든 단어가 들어 있어야 하며 대소문자는 무시하고
 * 특수문자는 일반 글자로 본다 (CF-11, BR-10). 전체 검색(search 모듈)과 블로그 안 검색이 함께 쓴다.
 */
public final class SearchWords {

    private SearchWords() {
    }

    /** 규칙에 맞는지 확인하고 앞뒤 공백을 뺀 검색어를 돌려준다 */
    public static String check(String rawQuery, BlogLimits limits) {
        String query = rawQuery == null ? "" : rawQuery.strip();
        int length = query.codePointCount(0, query.length());
        if (length < limits.searchMin()) {
            throw ApiException.field("q", Messages.SEARCH_TOO_SHORT);
        }
        if (length > limits.searchMax()) {
            throw ApiException.field("q", "검색어는 " + limits.searchMax() + "자 이하로 입력해 주세요");
        }
        return query;
    }

    /**
     * 단어마다 "and (… ilike …)" 조건을 만든다. withTags면 글에 붙은 태그 이름도 본다(블로그 안 검색, BR-10).
     * alias는 post 표 별칭, params에 단어 파라미터(w0, w1 …)를 넣는다.
     */
    public static String conditions(String query, String alias, boolean withTags, Map<String, Object> params) {
        StringBuilder where = new StringBuilder();
        String[] words = query.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String name = "w" + i;
            params.put(name, "%" + escapeLike(words[i]) + "%");
            where.append("  and (").append(alias).append(".title ilike :").append(name).append(" escape '\\'")
                    .append(" or ").append(alias).append(".body ilike :").append(name).append(" escape '\\'");
            if (withTags) {
                where.append(" or exists (select 1 from post_tag swt join tag sw on sw.id = swt.tag_id")
                        .append(" where swt.post_id = ").append(alias).append(".id and sw.name ilike :")
                        .append(name).append(" escape '\\')");
            }
            where.append(")\n");
        }
        return where.toString();
    }

    /** 검색어의 특수문자는 일반 글자로 취급한다 (CF-11-9) */
    public static String escapeLike(String word) {
        return word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
