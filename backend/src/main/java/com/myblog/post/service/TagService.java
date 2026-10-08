package com.myblog.post.service;

import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.Messages;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 태그: 글마다 0~5개, 1~15자, 공백·쉼표 불가, 앞의 #은 지우고 대소문자는 구분하지 않는다 (CF-20).
 */
@Service
public class TagService {

    private final NamedParameterJdbcTemplate jdbc;
    private final BlogLimits limits;

    public TagService(NamedParameterJdbcTemplate jdbc, BlogLimits limits) {
        this.jdbc = jdbc;
        this.limits = limits;
    }

    /** 규칙을 검사하고 같은 글 안의 겹침을 없앤 태그 목록 */
    public List<String> normalize(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String value : raw) {
            String tag = value == null ? "" : value.strip().replaceFirst("^#+", "");
            if (tag.isEmpty() && (value == null || value.isBlank())) {
                continue;
            }
            int length = tag.codePointCount(0, tag.length());
            if (length < 1 || length > limits.tagMax() || tag.matches(".*[\\s,].*")) {
                throw ApiException.field("tags", Messages.TAG_RULE);
            }
            unique.putIfAbsent(tag.toLowerCase(Locale.ROOT), tag);
        }
        if (unique.size() > limits.tagsPerPost()) {
            throw ApiException.field("tags", Messages.TAG_TOO_MANY);
        }
        return new ArrayList<>(unique.values());
    }

    /** 글의 태그 연결을 통째로 바꾼다 (CF-20-4) */
    public void replace(long postId, List<String> tags) {
        jdbc.update("delete from post_tag where post_id = :postId", Map.of("postId", postId));
        for (String tag : tags) {
            jdbc.update("insert into tag (name) values (:name) on conflict (lower(name)) do nothing", Map.of("name", tag));
            jdbc.update("""
                    insert into post_tag (post_id, tag_id)
                    select :postId, id from tag where lower(name) = lower(:name)
                    on conflict do nothing
                    """, Map.of("postId", postId, "name", tag));
        }
    }

    public List<String> tagsOf(long postId) {
        return jdbc.queryForList("""
                select t.name from post_tag pt join tag t on t.id = pt.tag_id
                where pt.post_id = :postId order by t.name
                """, Map.of("postId", postId), String.class);
    }
}
